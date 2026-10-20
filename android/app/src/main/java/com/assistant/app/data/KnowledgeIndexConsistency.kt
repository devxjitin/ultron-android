package com.assistant.app.data

import io.objectbox.BoxStore

internal const val KNOWLEDGE_INDEX_RECONCILIATION_VERSION = 1

/** Cosine distance (1 - similarity); chunks farther than this are treated as not relevant. Tune per corpus. */
internal const val MAX_RETRIEVAL_DISTANCE = 0.5

/** DataStore owns this snapshot; ObjectBox is a recoverable search index of it. */
internal data class KnowledgeSnapshot(
    val revision: Long,
    val documents: List<RagDocument>,
    val chunks: List<RagChunk>
)

internal data class KnowledgeIndexState(
    val source: KnowledgeSnapshot,
    val indexedRevision: Long?,
    val reconciliationVersion: Int
)

/** The caller holds the store mutex, including across source writes and index queries. */
internal class KnowledgeIndexCoordinator(
    private val readState: suspend () -> KnowledgeIndexState,
    private val commitSource: suspend ((KnowledgeSnapshot) -> KnowledgeSnapshot) -> Unit,
    private val replaceIndex: (KnowledgeSnapshot) -> Unit,
    private val markIndexed: suspend (KnowledgeSnapshot) -> Unit,
    private val invalidateCache: () -> Unit
) {
    suspend fun snapshot(): KnowledgeSnapshot = readState().source

    suspend fun reconcile(): KnowledgeSnapshot {
        val state = readState()
        if (state.indexedRevision != state.source.revision ||
            state.reconciliationVersion != KNOWLEDGE_INDEX_RECONCILIATION_VERSION
        ) {
            invalidateCache()
            // Replacement and verification must share one native transaction. If this or
            // the marker write fails, the next attempt repeats the complete replacement.
            replaceIndex(state.source)
            markIndexed(state.source)
        }
        return state.source
    }

    suspend fun mutate(transform: (KnowledgeSnapshot) -> KnowledgeSnapshot) {
        try {
            commitSource { current ->
                val changed = transform(current)
                if (changed === current) current
                else changed.copy(revision = Math.addExact(current.revision, 1L))
            }
        } finally {
            // A cancelled DataStore edit may already have committed; never retain old data.
            invalidateCache()
        }
        reconcile()
    }
}

/** Rejects missing/duplicate/orphan records and verifies content, model and vector values. */
internal fun verifyKnowledgeIndex(
    source: KnowledgeSnapshot,
    indexedDocuments: List<RagDocument>,
    indexedChunks: List<RagChunk>
) {
    val documents = source.documents.associateBy { it.id }
    val chunks = source.chunks.associateBy { it.docId to it.chunkIndex }
    check(documents.size == source.documents.size && chunks.size == source.chunks.size) {
        "Knowledge source contains duplicate records."
    }
    check(source.chunks.all { it.docId in documents }) { "Knowledge source contains orphan chunks." }
    check(indexedDocuments.size == documents.size &&
        indexedDocuments.associateBy { it.id } == documents
    ) { "Knowledge index document verification failed." }
    val indexedByKey = indexedChunks.associateBy { it.docId to it.chunkIndex }
    check(indexedChunks.size == chunks.size && indexedByKey.keys == chunks.keys) {
        "Knowledge index chunk identity verification failed."
    }
    check(chunks.all { (key, expected) ->
        val actual = indexedByKey.getValue(key)
        actual.text == expected.text && actual.heading == expected.heading &&
            actual.embeddingModel == expected.embeddingModel &&
            actual.embedding.contentEquals(expected.embedding)
    }) { "Knowledge index chunk content verification failed." }
}

/** Apply prepared vectors only if the exact source document is still current. */
internal fun applyReindexedChunks(
    current: KnowledgeSnapshot,
    prepared: List<Pair<RagDocument, List<RagChunk>>>
): KnowledgeSnapshot {
    val currentById = current.documents.associateBy { it.id }
    val replacements = prepared.filter { (document, _) -> currentById[document.id] == document }
    if (replacements.isEmpty()) return current
    val replacedIds = replacements.map { it.first.id }.toSet()
    return current.copy(chunks = current.chunks.filterNot { it.docId in replacedIds } +
        replacements.flatMap { it.second })
}

data class RagSearchMatches(val chunks: List<RagChunk>, val documents: List<RagDocument>)

/** All replacement writes and full-content verification share one ObjectBox transaction. */
internal class ObjectBoxKnowledgeIndex(private val store: BoxStore) {
    private val documents = store.boxFor(KnowledgeDocumentEntity::class.java)
    private val chunks = store.boxFor(KnowledgeChunkEntity::class.java)

    fun replace(source: KnowledgeSnapshot) {
        store.runInTx {
            chunks.removeAll()
            documents.removeAll()
            documents.put(source.documents.map { document ->
                KnowledgeDocumentEntity(externalId = document.id, title = document.title,
                    text = document.text, updatedAtMillis = document.updatedAtMillis)
            })
            chunks.put(source.chunks.map { chunk ->
                KnowledgeChunkEntity(documentExternalId = chunk.docId, chunkIndex = chunk.chunkIndex,
                    text = chunk.text, heading = chunk.heading, embeddingModel = chunk.embeddingModel,
                    embedding = chunk.embedding)
            })
            verifyKnowledgeIndex(source, documents.all.map { document ->
                RagDocument(document.externalId, document.title, document.text, document.updatedAtMillis)
            }, chunks.all.map { it.toRagChunk() })
        }
    }

    fun nearest(
        queryEmbedding: FloatArray,
        limit: Int,
        maxDistance: Double = MAX_RETRIEVAL_DISTANCE
    ): List<RagChunk> {
        val query = chunks.query(
            KnowledgeChunkEntity_.embedding.nearestNeighbors(queryEmbedding, limit)
        ).build()
        return query.use {
            it.findWithScores().filter { result -> result.score <= maxDistance }
                .map { result -> result.get().toRagChunk() }
        }
    }

    private fun KnowledgeChunkEntity.toRagChunk() = RagChunk(
        docId = documentExternalId, chunkIndex = chunkIndex, text = text,
        embedding = requireNotNull(embedding), embeddingModel = embeddingModel, heading = heading
    )
}
