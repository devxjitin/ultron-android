package com.assistant.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.assistant.app.network.GeminiEmbeddingClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

/** Splits document text into the editable blocks that become chunks: a block starts at a heading or after a
 *  blank line. Joining them with [joinKnowledgeBlocks] yields text that chunks exactly the same way. */
fun splitKnowledgeBlocks(text: String): List<String> {
    val blocks = mutableListOf<String>()
    val current = mutableListOf<String>()
    fun flush() {
        if (current.isNotEmpty()) blocks += current.joinToString("\n")
        current.clear()
    }
    text.lineSequence().forEach { line ->
        when {
            line.isBlank() -> flush()
            headingRegex.matches(line.trim()) -> { flush(); current += line }
            else -> current += line
        }
    }
    flush()
    return blocks
}

fun joinKnowledgeBlocks(blocks: List<String>): String =
    blocks.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n\n")

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

    /** Kept only to number knowledge changes; the documents and chunks themselves live in ObjectBox. */
    private val sourceRevisionKey = longPreferencesKey("knowledge_source_revision")
    /** Leftovers of the old DataStore-owned storage, removed the first time the store is opened. */
    private val retiredKeys = listOf(
        stringPreferencesKey("documents"), stringPreferencesKey("chunks"),
        longPreferencesKey("knowledge_indexed_revision"),
        intPreferencesKey("knowledge_index_reconciliation_version"),
        booleanPreferencesKey("knowledge_objectbox_is_source")
    )
    private var boxSource: ObjectBoxKnowledgeSource? = null
    private val documentsVersion = MutableStateFlow(0L)
    private val objectBoxStore: BoxStore by lazy { sharedObjectBoxStore(context.applicationContext) }
    private val objectBoxIndex by lazy { ObjectBoxKnowledgeIndex(objectBoxStore) }

    companion object {
        private var sharedStore: BoxStore? = null

        /** One store per process: a restarted ViewModel must reuse it, since ObjectBox rejects a
         *  second open store on the same directory and the old one is never closed. */
        @Synchronized
        internal fun sharedObjectBoxStore(appContext: Context): BoxStore =
            sharedStore?.takeUnless { it.isClosed } ?: MyObjectBox.builder()
                .androidContext(appContext)
                .name("knowledge-vector-store")
                .build()
                .also { sharedStore = it }
    }

    /** Opens the ObjectBox-backed knowledge source once. The caller holds [chunksMutex]. */
    private suspend fun coordinator(): KnowledgeIndexCoordinator {
        boxSource?.let { return it.coordinator() }
        var start = 0L
        context.ragDataStore.edit { prefs ->
            start = prefs[sourceRevisionKey] ?: 0L
            retiredKeys.forEach { prefs.remove(it) }
        }
        val source = ObjectBoxKnowledgeSource(objectBoxIndex, start) { revision ->
            context.ragDataStore.edit { it[sourceRevisionKey] = revision }
        }
        boxSource = source
        return source.coordinator()
    }

    val documents: Flow<List<RagDocument>> =
        documentsVersion.map { chunksMutex.withLock { coordinator().snapshot().documents } }
            .flowOn(Dispatchers.Default)

    suspend fun allChunks(trace: LatencyTool? = null): List<RagChunk> = chunksWithRevision(trace).second

    /** The chunks together with the knowledge revision they belong to, read under one lock. */
    suspend fun chunksWithRevision(trace: LatencyTool? = null): Pair<Long, List<RagChunk>> = withContext(Dispatchers.Default) {
        chunksMutex.withLock {
            val snapshot = coordinator().snapshot()
            snapshot.revision to snapshot.chunks
        }
    }

    /** Reconciles the index before querying and captures source attribution under the same lock. */
    suspend fun nearestChunks(queryEmbedding: FloatArray, limit: Int, trace: LatencyTool? = null): RagSearchMatches =
        withContext(Dispatchers.Default) {
            chunksMutex.withLock {
                val source = coordinator().snapshot()
                check(!requiresEmbeddingMigration(source.chunks)) {
                    "Knowledge embeddings still require model migration. Retry the search."
                }
                val indexedSource = coordinator().reconcile()
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
        val newChunks = embedChunks(doc, embeddingClient, onProgress = onProgress)
        chunksMutex.withLock {
            coordinator().mutate { current ->
                current.copy(documents = current.documents + doc, chunks = current.chunks + newChunks)
            }
        }
        documentsVersion.update { it + 1 }
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
        // Chunks whose embedding input is unchanged keep their stored vector; only edited ones are re-embedded.
        val (previousDoc, previousChunks) = chunksMutex.withLock {
            val source = coordinator().snapshot()
            source.documents.firstOrNull { it.id == id } to source.chunks.filter { it.docId == id }
        }
        val reusable = previousDoc?.let { old ->
            previousChunks.filter { it.embeddingModel == GeminiEmbeddingClient.MODEL }
                .associateBy { documentEmbeddingText(old.title, it.heading, it.text) }
        }.orEmpty()
        val newChunks = embedChunks(updated, embeddingClient, reusable, onProgress)
        var applied = false
        chunksMutex.withLock {
            coordinator().mutate { current ->
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
        documentsVersion.update { it + 1 }
        return if (applied) updated else null
    }

    /** Clears documents, chunks and the search index. */
    suspend fun resetKnowledge() {
        chunksMutex.withLock {
            coordinator().mutate { it.copy(documents = emptyList(), chunks = emptyList()) }
        }
        documentsVersion.update { it + 1 }
    }

    suspend fun deleteDocument(id: String) {
        chunksMutex.withLock {
            coordinator().mutate { current ->
                current.copy(documents = current.documents.filterNot { it.id == id },
                    chunks = current.chunks.filterNot { it.docId == id })
            }
            // Only report success once the committed source really has no trace of the document.
            val after = coordinator().snapshot()
            check(after.documents.none { it.id == id } && after.chunks.none { it.docId == id }) {
                "Document was not removed from the knowledge store"
            }
        }
        documentsVersion.update { it + 1 }
    }

    /** Prepares all vectors before committing, preserving source text/title/timestamps.
     * Concurrent edits/deletes are retained; failed embedding leaves the previous snapshot. */
    suspend fun reindexAll(
        embeddingClient: GeminiEmbeddingClient,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ) {
        val documentsToIndex = chunksMutex.withLock { coordinator().snapshot().documents }
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
            coordinator().mutate { current -> applyReindexedChunks(current, prepared) }
        }
    }

    /** Embeds the new paragraphs in batches (reusing unchanged ones); progress advances once per finished batch. */
    private suspend fun embedChunks(
        doc: RagDocument,
        embeddingClient: GeminiEmbeddingClient,
        reusable: Map<String, RagChunk> = emptyMap(),
        onProgress: (Int, Int) -> Unit
    ): List<RagChunk> {
        val pieces = searchableDocumentChunks(doc.text)
        val embeddingTexts = pieces.map { documentEmbeddingText(doc.title, it.first, it.second) }
        val toEmbed = embeddingTexts.count { it !in reusable }
        onProgress(0, toEmbed)
        val missing = embeddingTexts.indices.filter { embeddingTexts[it] !in reusable }
        val fresh = embeddingClient.embedDocuments(missing.map { embeddingTexts[it] }) { done -> onProgress(done, toEmbed) }
        val freshByIndex = missing.zip(fresh).toMap()
        return pieces.mapIndexed { index, piece ->
            val kept = reusable[embeddingTexts[index]]
            if (kept != null) {
                kept.copy(chunkIndex = index)
            } else {
                val result = freshByIndex.getValue(index)
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
    }
}
