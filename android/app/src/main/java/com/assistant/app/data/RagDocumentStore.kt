package com.assistant.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.assistant.app.network.GeminiEmbeddingClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import com.assistant.app.util.LatencyTool
import com.assistant.app.util.UserInputException
import io.objectbox.BoxStore

private val Context.ragDataStore by preferencesDataStore(name = "assistant_rag_documents")
private val headingRegex = Regex("^(#{1,6})\\s+(.+)$")
private const val MAX_EMBEDDING_PARAGRAPH_CHARS = 900

/** Packs complete pipe-delimited records; the size target is soft for indivisible rows. */
private fun splitStructuredParagraph(paragraph: String): List<String> {
    val lines = paragraph.lines()
    val firstRecord = lines.indexOfFirst { it.contains('|') }
    val prefix = lines.take(firstRecord).toMutableList()
    var recordStart = firstRecord
    // Markdown tables have a header followed by a delimiter row. Repeat both on continuations.
    if (firstRecord + 1 < lines.size &&
        lines[firstRecord + 1].trim().trim('|').split('|').all {
            it.trim().matches(Regex(":?-{3,}:?"))
        }
    ) {
        prefix += lines.subList(firstRecord, firstRecord + 2)
        recordStart += 2
    }
    val pieces = mutableListOf<String>()
    val records = mutableListOf<String>()
    fun content() = (prefix + records).joinToString("\n")
    for (line in lines.drop(recordStart)) {
        if (records.isNotEmpty() && content().length + line.length + 1 > MAX_EMBEDDING_PARAGRAPH_CHARS) {
            pieces += content()
            records.clear()
        }
        records += line
    }
    if (records.isNotEmpty() || pieces.isEmpty()) pieces += content()
    return pieces
}

/** Breaks model output that ignored the paragraph contract without dropping content. */
private fun splitLongParagraphForEmbedding(paragraph: String): List<String> {
    if (paragraph.contains('|')) return splitStructuredParagraph(paragraph)
    if (paragraph.length <= MAX_EMBEDDING_PARAGRAPH_CHARS) return listOf(paragraph)
    val pieces = mutableListOf<String>()
    var current = StringBuilder()
    val sentences = paragraph.split(Regex("(?<=[.!?。！？])\\s+"))
    sentences.forEach { sentence ->
        val normalized = sentence.trim()
        if (normalized.isEmpty()) return@forEach
        if (current.isNotEmpty() && current.length + normalized.length + 1 > MAX_EMBEDDING_PARAGRAPH_CHARS) {
            pieces += current.toString()
            current = StringBuilder()
        }
        if (normalized.length > MAX_EMBEDDING_PARAGRAPH_CHARS) {
            if (current.isNotEmpty()) {
                pieces += current.toString()
                current = StringBuilder()
            }
            normalized.chunked(MAX_EMBEDDING_PARAGRAPH_CHARS).forEach { pieces += it }
        } else {
            if (current.isNotEmpty()) current.append(' ')
            current.append(normalized)
        }
    }
    if (current.isNotEmpty()) pieces += current.toString()
    return pieces
}

/** Parses inline dataset chunks and legacy Markdown, preserving their heading context. */
internal fun chunkMarkdownText(text: String): List<Pair<String?, String>> {
    val headingStack = mutableListOf<Pair<Int, String>>()
    return buildList {
        val bodyLines = mutableListOf<String>()
        var sectionContext: String? = null
        fun flushBody() {
            val body = bodyLines.joinToString("\n").trim()
            if (body.isNotEmpty()) {
                val path = headingStack.takeIf { it.isNotEmpty() }?.joinToString(" > ") { it.second }
                val contextualBody = if (body.contains('|') && sectionContext != null) {
                    "$sectionContext\n$body"
                } else body
                splitLongParagraphForEmbedding(contextualBody).forEach { add(path to it) }
                if (!body.contains('|')) sectionContext = body
            }
            bodyLines.clear()
        }

        text.lineSequence().forEach { line ->
            val heading = headingRegex.find(line.trim())
            if (heading != null) {
                flushBody()
                sectionContext = null
                val level = heading.groupValues[1].length
                val parts = if (level == 1) {
                    heading.groupValues[2].split(Regex("\\s+\\|(?:\\s+|$)"), limit = 2)
                } else {
                    listOf(heading.groupValues[2])
                }
                val headingText = parts.first().trim()
                while (headingStack.isNotEmpty() && headingStack.last().first >= level) {
                    headingStack.removeAt(headingStack.lastIndex)
                }
                headingStack.add(level to headingText)
                if (parts.size == 2) bodyLines.add(parts[1])
            } else if (line.isBlank()) {
                flushBody()
            } else {
                bodyLines.add(line)
            }
        }
        flushBody()
    }
}

internal fun documentEmbeddingText(title: String, heading: String?, body: String): String =
    listOfNotNull(title.takeIf { it.isNotBlank() }, heading?.takeIf { it.isNotBlank() }, body).joinToString("\n")

/** Rejects empty or heading-only documents before embedding or persistent writes. */
internal fun searchableDocumentChunks(text: String): List<Pair<String?, String>> =
    chunkMarkdownText(text).also { chunks ->
        if (chunks.isEmpty()) throw UserInputException("Document must contain searchable content, not just headings.")
    }

/** Rejects model output that is not the requested `# Title | content` dataset (preface, prose, wrong format). */
internal fun validateFormattedDataset(text: String): String {
    val cleaned = text.lines().filterNot { it.trim().startsWith("```") }.joinToString("\n").trim()
    val lines = cleaned.lines().filter { it.isNotBlank() }
    val chunkLine = Regex("^#\\s+.+\\s\\|\\s+\\S")
    if (lines.isEmpty() || !lines.first().trim().startsWith("#") || lines.none { chunkLine.containsMatchIn(it.trim()) }) {
        throw UserInputException("We couldn't prepare this document in the expected format. Please try again.")
    }
    return cleaned
}

/**
 * Persists user-managed knowledge documents (title + full text) and their chunk embeddings,
 * so the RAG tool can search over whatever the kiosk admin has entered in Settings -- no
 * document ships in the app; the store starts empty.
 */
class RagDocumentStore(private val context: Context) {
    private val chunksMutex = Mutex()
    @Volatile private var cachedSnapshot: KnowledgeSnapshot? = null

    private val docsKey = stringPreferencesKey("documents")
    private val chunksKey = stringPreferencesKey("chunks")
    private val sourceRevisionKey = longPreferencesKey("knowledge_source_revision")
    private val indexedRevisionKey = longPreferencesKey("knowledge_indexed_revision")
    private val reconciliationVersionKey = intPreferencesKey("knowledge_index_reconciliation_version")
    private val objectBoxStore: BoxStore by lazy { sharedObjectBoxStore(context.applicationContext) }
    private val objectBoxIndex by lazy { ObjectBoxKnowledgeIndex(objectBoxStore) }

    private companion object {
        private var sharedStore: BoxStore? = null

        /** One store per process: a restarted ViewModel must reuse it, since ObjectBox rejects a
         *  second open store on the same directory and the old one is never closed. */
        @Synchronized
        fun sharedObjectBoxStore(appContext: Context): BoxStore =
            sharedStore?.takeUnless { it.isClosed } ?: MyObjectBox.builder()
                .androidContext(appContext)
                .name("knowledge-vector-store")
                .build()
                .also { sharedStore = it }
    }

    private val indexCoordinator = KnowledgeIndexCoordinator(
        readState = {
            val prefs = context.ragDataStore.data.first()
            val revision = prefs[sourceRevisionKey] ?: 0L
            val source = cachedSnapshot?.takeIf { it.revision == revision }
                ?: KnowledgeSnapshot(revision, decodeDocs(prefs[docsKey]), decodeChunks(prefs[chunksKey]))
                    .also { cachedSnapshot = it }
            KnowledgeIndexState(
                source,
                prefs[indexedRevisionKey], prefs[reconciliationVersionKey] ?: 0
            )
        },
        commitSource = { transform ->
            context.ragDataStore.edit { prefs ->
                val current = KnowledgeSnapshot(prefs[sourceRevisionKey] ?: 0L,
                    decodeDocs(prefs[docsKey]), decodeChunks(prefs[chunksKey]))
                val changed = transform(current)
                if (changed !== current) {
                    prefs[docsKey] = encodeDocs(changed.documents)
                    prefs[chunksKey] = encodeChunks(changed.chunks)
                    prefs[sourceRevisionKey] = changed.revision
                }
            }
        },
        replaceIndex = { source -> objectBoxIndex.replace(source) },
        markIndexed = { source ->
            context.ragDataStore.edit { prefs ->
                check((prefs[sourceRevisionKey] ?: 0L) == source.revision) {
                    "Knowledge source changed during index reconciliation."
                }
                prefs[indexedRevisionKey] = source.revision
                prefs[reconciliationVersionKey] = KNOWLEDGE_INDEX_RECONCILIATION_VERSION
            }
        },
        invalidateCache = { cachedSnapshot = null }
    )

    val documents: Flow<List<RagDocument>> =
        context.ragDataStore.data.map { prefs -> decodeDocs(prefs[docsKey]) }
            .flowOn(Dispatchers.Default)

    suspend fun allChunks(trace: LatencyTool? = null): List<RagChunk> = chunksWithRevision(trace).second

    /** The chunks together with the knowledge revision they belong to, read under one lock. */
    suspend fun chunksWithRevision(trace: LatencyTool? = null): Pair<Long, List<RagChunk>> = withContext(Dispatchers.Default) {
        chunksMutex.withLock {
            trace?.event("chunk_cache", mapOf("cache" to if (cachedSnapshot == null) "miss" else "hit"))
            val snapshot = indexCoordinator.snapshot()
            snapshot.revision to snapshot.chunks
        }
    }

    /** Reconciles the index before querying and captures source attribution under the same lock. */
    suspend fun nearestChunks(queryEmbedding: FloatArray, limit: Int, trace: LatencyTool? = null): RagSearchMatches =
        withContext(Dispatchers.Default) {
            chunksMutex.withLock {
                val source = indexCoordinator.snapshot()
                check(!requiresEmbeddingMigration(source.chunks)) {
                    "Knowledge embeddings still require model migration. Retry the search."
                }
                val indexedSource = indexCoordinator.reconcile()
                RagSearchMatches(objectBoxIndex.nearest(queryEmbedding, limit), indexedSource.documents, indexedSource.revision)
            }
        }

    /** Adds a new document, chunks it, and embeds every chunk. Throws if embedding fails --
     *  the caller should surface that rather than silently save a document with no searchable
     *  chunks. [onProgress] is called after each chunk finishes embedding with
     *  (completedChunks, totalChunks), so the UI can show an upload progress bar. */
    suspend fun addDocument(
        title: String,
        text: String,
        embeddingClient: GeminiEmbeddingClient,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): RagDocument {
        val doc = RagDocument(id = UUID.randomUUID().toString(), title = title.trim(), text = text.trim())
        val newChunks = embedChunks(doc, embeddingClient, onProgress)
        chunksMutex.withLock {
            indexCoordinator.mutate { current ->
                current.copy(documents = current.documents + doc, chunks = current.chunks + newChunks)
            }
        }
        return doc
    }

    /** Replaces an existing document's title/text and re-embeds its chunks from scratch.
     *  Returns null if [id] was deleted while embedding was in flight -- the update is then
     *  discarded (including any orphan chunks) instead of resurrecting the document's chunks
     *  under a title/text that no longer exists in the document list. */
    suspend fun updateDocument(
        id: String,
        title: String,
        text: String,
        embeddingClient: GeminiEmbeddingClient,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): RagDocument? {
        val updated = RagDocument(id = id, title = title.trim(), text = text.trim())
        val newChunks = embedChunks(updated, embeddingClient, onProgress)
        var applied = false
        chunksMutex.withLock {
            indexCoordinator.mutate { current ->
                if (current.documents.any { it.id == id }) {
                    applied = true
                    current.copy(
                        documents = current.documents.map { if (it.id == id) updated else it },
                        chunks = current.chunks.filterNot { it.docId == id } + newChunks
                    )
                } else {
                    current
                }
            }
        }
        return if (applied) updated else null
    }

    /** Recovery for an unreadable stored blob: clears documents, chunks and the search index
     *  without decoding the old data, which is exactly what cannot be done when it is corrupt. */
    suspend fun resetKnowledge() {
        chunksMutex.withLock {
            try {
                context.ragDataStore.edit { prefs ->
                    prefs[sourceRevisionKey] = Math.addExact(prefs[sourceRevisionKey] ?: 0L, 1L)
                    prefs.remove(docsKey)
                    prefs.remove(chunksKey)
                    prefs.remove(indexedRevisionKey)
                }
            } finally {
                cachedSnapshot = null
            }
            indexCoordinator.reconcile()
        }
    }

    suspend fun deleteDocument(id: String) {
        chunksMutex.withLock {
            indexCoordinator.mutate { current ->
                current.copy(documents = current.documents.filterNot { it.id == id },
                    chunks = current.chunks.filterNot { it.docId == id })
            }
            // Only report success once the committed source really has no trace of the document.
            val after = indexCoordinator.snapshot()
            check(after.documents.none { it.id == id } && after.chunks.none { it.docId == id }) {
                "Document was not removed from the knowledge store"
            }
        }
    }

    /** Prepares all vectors before committing, preserving source text/title/timestamps.
     * Concurrent edits/deletes are retained; failed embedding leaves the previous snapshot. */
    suspend fun reindexAll(
        embeddingClient: GeminiEmbeddingClient,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ) {
        val documentsToIndex = chunksMutex.withLock { indexCoordinator.snapshot().documents }
        val totalChunks = documentsToIndex.sumOf { chunkMarkdownText(it.text).size }
        var completedChunks = 0
        val prepared = documentsToIndex.map { document ->
            val newChunks = embedChunks(document, embeddingClient) { done, _ ->
                onProgress(completedChunks + done, totalChunks)
            }
            completedChunks += newChunks.size
            document to newChunks
        }
        chunksMutex.withLock {
            indexCoordinator.mutate { current -> applyReindexedChunks(current, prepared) }
        }
    }

    /** Prepares one paragraph at a time so the UI can show real, steadily advancing progress. */
    private suspend fun embedChunks(
        doc: RagDocument,
        embeddingClient: GeminiEmbeddingClient,
        onProgress: (Int, Int) -> Unit
    ): List<RagChunk> {
        val pieces = searchableDocumentChunks(doc.text)
        onProgress(0, pieces.size)
        return pieces.mapIndexed { index, piece ->
            val result = embeddingClient.embed(
                documentEmbeddingText(doc.title, piece.first, piece.second),
                GeminiEmbeddingClient.TaskType.DOCUMENT
            )
            onProgress(index + 1, pieces.size)
            RagChunk(
                docId = doc.id,
                chunkIndex = index,
                text = piece.second,
                heading = piece.first,
                embedding = result.embedding,
                embeddingModel = result.model
            )
        }
    }

    private fun encodeDocs(docs: List<RagDocument>): String {
        val array = JSONArray()
        docs.forEach { doc ->
            array.put(
                JSONObject()
                    .put("id", doc.id)
                    .put("title", doc.title)
                    .put("text", doc.text)
                    .put("updatedAt", doc.updatedAtMillis)
            )
        }
        return array.toString()
    }

    private fun decodeDocs(raw: String?): List<RagDocument> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                RagDocument(
                    id = obj.getString("id"),
                    title = obj.optString("title"),
                    text = obj.optString("text"),
                    updatedAtMillis = obj.optLong("updatedAt")
                )
            }
        } catch (e: Exception) {
            throw IllegalStateException("Stored knowledge documents could not be decoded.", e)
        }
    }

    private fun encodeChunks(chunks: List<RagChunk>): String {
        val array = JSONArray()
        chunks.forEach { chunk ->
            array.put(
                JSONObject()
                    .put("docId", chunk.docId)
                    .put("chunkIndex", chunk.chunkIndex)
                    .put("text", chunk.text)
                    .put("embeddingB64", EmbeddingCodec.encode(chunk.embedding))
                    .put("embeddingModel", chunk.embeddingModel)
                    .put("heading", chunk.heading ?: "")
            )
        }
        return array.toString()
    }

    private fun decodeChunks(raw: String?): List<RagChunk> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                RagChunk(
                    docId = obj.getString("docId"),
                    chunkIndex = obj.optInt("chunkIndex"),
                    text = obj.optString("text"),
                    embedding = if (obj.has("embeddingB64")) {
                        EmbeddingCodec.decode(obj.getString("embeddingB64"))
                    } else {
                        // Legacy format: JSON array of doubles.
                        val embeddingArray = obj.getJSONArray("embedding")
                        FloatArray(embeddingArray.length()) { j -> embeddingArray.getDouble(j).toFloat() }
                    },
                    embeddingModel = obj.optString("embeddingModel").takeIf { it.isNotBlank() }
                        ?: "gemini-embedding-001",
                    heading = obj.optString("heading").takeIf { it.isNotBlank() }
                )
            }
        } catch (e: Exception) {
            throw IllegalStateException("Stored knowledge chunks could not be decoded.", e)
        }
    }
}
