package com.assistant.app.data

import com.assistant.app.network.GeminiEmbeddingClient
import com.assistant.app.util.LatencyTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Stored chunks use an older embedding model and must be re-embedded before they can be searched. */
class EmbeddingMigrationRequiredException :
    IllegalStateException("Knowledge embeddings need to be updated before search can run.")

/** Extra characters of neighbouring chunks added around the top matches, so answers aren't cut mid-topic. */
private const val NEIGHBOR_CHAR_BUDGET = 3_000
/** Upper bound on chunks retrieved for "list everything" style questions. */
private const val MAX_LIST_CHUNK_LIMIT = 20
private const val RRF_K = 60
private val listQueryRegex = Regex(
    "\\b(all|every|list|complete|entire|full|schedule|timetable|remaining|saare|sabhi)\\b|सभी|सारे|पूरा|पूरी",
    RegexOption.IGNORE_CASE
)

internal fun isListQuery(query: String): Boolean = listQueryRegex.containsMatchIn(query)

/** Reciprocal Rank Fusion: merges ranked lists without needing comparable scores. */
internal fun fuseRankings(vararg rankings: List<ChunkKey>, limit: Int): List<ChunkKey> {
    val scores = LinkedHashMap<ChunkKey, Double>()
    rankings.forEach { ranking ->
        ranking.forEachIndexed { rank, key -> scores.merge(key, 1.0 / (RRF_K + rank + 1), Double::plus) }
    }
    return scores.entries.sortedByDescending { it.value }.take(limit).map { it.key }
}

/**
 * Formats the ranked chunks, plus the chunks directly before/after each one while the extra
 * [extraCharBudget] lasts. Chunks are grouped per document in reading order, documents ordered by
 * their best-ranked chunk.
 */
internal fun assembleContext(
    ranked: List<RagChunk>,
    allChunks: List<RagChunk>,
    titlesById: Map<String, String>,
    extraCharBudget: Int = NEIGHBOR_CHAR_BUDGET
): List<String> {
    val byKey = allChunks.associateBy { it.key() }
    val selected = LinkedHashMap<ChunkKey, RagChunk>()
    ranked.forEach { selected[it.key()] = it }
    var budget = extraCharBudget
    ranked.forEach { chunk ->
        for (neighbourIndex in intArrayOf(chunk.chunkIndex - 1, chunk.chunkIndex + 1)) {
            val neighbour = byKey[chunk.docId to neighbourIndex] ?: continue
            if (neighbour.key() in selected || neighbour.text.length > budget) continue
            selected[neighbour.key()] = neighbour
            budget -= neighbour.text.length
        }
    }
    return selected.values.groupBy { it.docId }.values.flatMap { group ->
        group.sortedBy { it.chunkIndex }.map { chunk ->
            // Attributes each result to its document/section -- two documents like "Delhi fees" and
            // "Mumbai fees" would otherwise both surface an isolated "the fee is ..." paragraph.
            val title = titlesById[chunk.docId] ?: "Unknown document"
            val source = chunk.heading?.let { "$title › $it" } ?: title
            "[Source: $source]\n${chunk.text}"
        }
    }
}

/**
 * Embeds a live query and returns the best-matching stored chunks: vector (cosine) and BM25
 * keyword rankings fused with RRF, with neighbouring chunks added for context.
 */
class RagSearchEngine(
    private val documentStore: RagDocumentStore,
    private val embeddingClient: GeminiEmbeddingClient,
    private val retrievalChunkLimit: Int = DEFAULT_RETRIEVAL_CHUNK_LIMIT
) {

    /** Embeds the query in the current model space; fails fast if stored chunks need migration first. */
    suspend fun search(query: String, trace: LatencyTool? = null): List<String> {
        val loadStartedAt = trace?.now()
        val chunks = documentStore.allChunks(trace)
        if (requiresEmbeddingMigration(chunks)) {
            // Re-embedding can take minutes, far longer than a live tool call may wait; the caller
            // reports "not ready" and starts the migration in the background.
            trace?.event("embedding_migration_required", mapOf("chunk_count" to chunks.size))
            throw EmbeddingMigrationRequiredException()
        }
        if (trace != null && loadStartedAt != null) trace.event("chunks_loaded", mapOf(
            "duration_ms" to (trace.now() - loadStartedAt), "chunk_count" to chunks.size
        ))
        if (chunks.isEmpty()) return emptyList()

        val queryEmbedding = embeddingClient.embed(query, GeminiEmbeddingClient.TaskType.QUERY, trace).embedding

        val rankingStartedAt = trace?.now()
        val limit = if (isListQuery(query)) {
            minOf(retrievalChunkLimit * 2, MAX_LIST_CHUNK_LIMIT)
        } else retrievalChunkLimit
        val matches = documentStore.nearestChunks(queryEmbedding, limit, trace)
        val keywordKeys = withContext(Dispatchers.Default) { Bm25Cache.forChunks(chunks).search(query, limit) }
        val fusedKeys = fuseRankings(matches.chunks.map { it.key() }, keywordKeys, limit = limit)
        if (trace != null && rankingStartedAt != null) trace.event("ranking_complete", mapOf(
            "duration_ms" to (trace.now() - rankingStartedAt), "match_count" to fusedKeys.size,
            "vector_count" to matches.chunks.size, "keyword_count" to keywordKeys.size,
            "index" to "objectbox_hnsw+bm25"
        ))
        if (fusedKeys.isEmpty()) return emptyList()

        val byKey = chunks.associateBy { it.key() }
        val topChunks = fusedKeys.mapNotNull { byKey[it] }
        return assembleContext(topChunks, chunks, matches.documents.associate { it.id to it.title })
    }

}

internal fun requiresEmbeddingMigration(chunks: List<RagChunk>): Boolean =
    chunks.any { it.embeddingModel != GeminiEmbeddingClient.MODEL }
