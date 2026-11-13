package com.assistant.app.decisions

import com.assistant.app.data.RagChunk

/**
 * Scores knowledge chunks against the user's question with one Decisions call (one score question per chunk) and keeps
 * only the ones that answer it. Returns null when the call fails, so the caller keeps today's ranking.
 */
class ChunkReranker(
    private val client: DecisionsClient,
    private val timeoutMs: Long = 3_000,
) {
    companion object {
        const val CANDIDATES = 20
        const val MAX_KEEP = 4
        /** Score is the weighted level index (0 to 3); 1.5 is the midpoint between "mentions it" and "answers it". */
        const val MIN_SCORE = 1.5
        private const val CHUNK_CHARS = 800
        private val LEVELS = listOf(
            "Not relevant to the question",
            "Same topic but does not help answer it",
            "Contains part of the answer",
            "Directly answers the question",
        )
    }

    suspend fun rerank(query: String, chunks: List<RagChunk>): List<RagChunk>? {
        val candidates = chunks.take(CANDIDATES)
        if (candidates.size < 2) return null
        val set = questions {
            candidates.forEachIndexed { i, chunk ->
                score("c$i", "How well does this passage answer the question?\n\nPassage:\n${chunk.text.take(CHUNK_CHARS)}", LEVELS)
            }
        }
        val outcome = client.decide("rerank", listOf(DecisionInput.Text(query)), set, timeoutMs)
        val result = (outcome as? DecisionOutcome.Answered)?.result ?: return null
        return candidates.withIndex()
            .mapNotNull { (i, chunk) -> result.score("c$i")?.score?.takeIf { it >= MIN_SCORE }?.let { chunk to it } }
            .sortedByDescending { it.second }
            .take(MAX_KEEP)
            .map { it.first }
    }
}
