package com.assistant.app.data

import java.util.Locale
import kotlin.math.ln

/** Identifies a chunk independent of its (large) embedding payload. */
internal typealias ChunkKey = Pair<String, Int>

internal fun RagChunk.key(): ChunkKey = docId to chunkIndex

private val tokenSplitter = Regex("[^\\p{L}\\p{M}\\p{N}]+")

internal fun bm25Tokens(text: String): List<String> =
    text.lowercase(Locale.ROOT).split(tokenSplitter).filter { it.length > 1 || it.firstOrNull()?.isDigit() == true }

/**
 * In-memory BM25 keyword ranking over chunk heading + text. Complements the vector search, which
 * can miss exact codes, names and numbers. Chunks are already cached in memory, so no extra storage.
 */
internal class Bm25Index(chunks: List<RagChunk>) {
    private class Entry(val key: ChunkKey, val termCounts: Map<String, Int>, val length: Int)

    private val entries = chunks.map { chunk ->
        val tokens = bm25Tokens(listOfNotNull(chunk.heading, chunk.text).joinToString(" "))
        Entry(chunk.key(), tokens.groupingBy { it }.eachCount(), tokens.size)
    }
    private val averageLength = entries.map { it.length }.average().takeIf { !it.isNaN() && it > 0 } ?: 1.0
    private val documentFrequency: Map<String, Int> = HashMap<String, Int>().also { frequency ->
        entries.forEach { entry -> entry.termCounts.keys.forEach { term -> frequency.merge(term, 1, Int::plus) } }
    }

    /** Best-matching chunk keys first; empty when no query term is distinctive enough to match. */
    fun search(query: String, limit: Int): List<ChunkKey> {
        val total = entries.size
        if (total == 0 || limit <= 0) return emptyList()
        // Terms in most chunks (e.g. "the", "fee" in a fee-only corpus) carry no ranking signal.
        val terms = bm25Tokens(query).distinct().filter { term ->
            val frequency = documentFrequency[term] ?: 0
            frequency > 0 && (total < MIN_CHUNKS_FOR_COMMON_TERM_FILTER || frequency * 2 <= total)
        }
        if (terms.isEmpty()) return emptyList()
        return entries.mapNotNull { entry ->
            val score = terms.sumOf { term ->
                val count = entry.termCounts[term] ?: return@sumOf 0.0
                val frequency = documentFrequency.getValue(term)
                val idf = ln(1.0 + (total - frequency + 0.5) / (frequency + 0.5))
                idf * count * (K1 + 1) / (count + K1 * (1 - B + B * entry.length / averageLength))
            }
            if (score > 0.0) entry.key to score else null
        }.sortedByDescending { it.second }.take(limit).map { it.first }
    }

    private companion object {
        const val K1 = 1.2
        const val B = 0.75
        const val MIN_CHUNKS_FOR_COMMON_TERM_FILTER = 8
    }
}

/** Reuses one index while the cached chunk snapshot (same list instance) is unchanged. */
internal object Bm25Cache {
    private var last: Pair<List<RagChunk>, Bm25Index>? = null

    @Synchronized
    fun forChunks(chunks: List<RagChunk>): Bm25Index =
        last?.takeIf { it.first === chunks }?.second
            ?: Bm25Index(chunks).also { last = chunks to it }
}
