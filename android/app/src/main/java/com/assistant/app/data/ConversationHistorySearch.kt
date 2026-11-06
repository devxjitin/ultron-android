package com.assistant.app.data

import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.ln

/** An optional span of days to search in; a null side is open-ended. Both ends are inclusive. */
data class DateRange(val fromMillis: Long?, val toMillis: Long?) {
    val isAll: Boolean get() = fromMillis == null && toMillis == null

    operator fun contains(timestampMillis: Long): Boolean =
        (fromMillis == null || timestampMillis >= fromMillis) && (toMillis == null || timestampMillis <= toMillis)

    companion object {
        val ALL = DateRange(null, null)

        /** Reads YYYY-MM-DD days in [zone]. Blank means open-ended. Throws [IllegalArgumentException] for a bad date. */
        fun parse(from: String?, to: String?, zone: ZoneId = ZoneId.systemDefault()): DateRange {
            fun day(text: String?, name: String): LocalDate? = text?.trim()?.takeIf { it.isNotEmpty() }?.let {
                try {
                    LocalDate.parse(it)
                } catch (e: DateTimeParseException) {
                    throw IllegalArgumentException("$name must look like YYYY-MM-DD, for example 2026-11-03.")
                }
            }
            val start = day(from, "from_date")
            val end = day(to, "to_date")
            if (start != null && end != null && start.isAfter(end)) {
                throw IllegalArgumentException("from_date must not be after to_date.")
            }
            return DateRange(
                start?.atStartOfDay(zone)?.toInstant()?.toEpochMilli(),
                end?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()?.minus(1)
            )
        }
    }
}

/**
 * Finds past exchanges for a question. Vector similarity and keyword matches are fused (Reciprocal Rank Fusion) so a
 * paraphrase and an exact name or number both work; newer exchanges win ties; the exchange just before and after each
 * match is added so an answer is not cut off. Keyword search alone still works when the query cannot be embedded.
 */
class ConversationHistorySearch(
    private val store: ConversationHistoryStore,
    private val embedQuery: suspend (String) -> FloatArray,
    private val zone: ZoneId = ZoneId.systemDefault()
) {
    companion object {
        const val DEFAULT_LIMIT = 5
        /** Most characters returned, so one search never floods the model's context. */
        const val MAX_RESULT_CHARS = 4_000
        private const val RRF_K = 60
        /** Per word, the most matching exchanges looked at. */
        private const val KEYWORD_CANDIDATES = 300
        /** With a date range the vector search looks wider first, because the range is applied afterwards. */
        private const val RANGE_VECTOR_FACTOR = 8
    }

    /** The matching exchanges, best session first and in order within it. Empty when nothing matches. */
    suspend fun search(query: String, range: DateRange = DateRange.ALL, limit: Int = DEFAULT_LIMIT): String {
        val ranked = rank(query, range, limit)
        if (ranked.isEmpty()) return ""
        val hits = store.getAll(ranked)
        val sessionOrder = hits.map { it.sessionId }.distinct()
        val hitIds = hits.map { it.id }.toSet()

        val out = StringBuilder()
        for (sessionId in sessionOrder) {
            val session = store.sessionTurns(sessionId)
            val wanted = session.indices.filter { index ->
                session[index].id in hitIds || (index + 1 < session.size && session[index + 1].id in hitIds) ||
                    (index > 0 && session[index - 1].id in hitIds)
            }
            for (index in wanted) {
                val block = render(session[index])
                if (out.length + block.length > MAX_RESULT_CHARS && out.isNotEmpty()) return out.toString().trimEnd()
                out.append(block).append('\n')
            }
        }
        return out.toString().trimEnd()
    }

    private suspend fun rank(query: String, range: DateRange, limit: Int): List<Long> {
        val keywordIds = keywordRanking(query, range, limit * 4)
        val vectorIds = vectorRanking(query, range, limit * 4)
        val candidates = (keywordIds + vectorIds).distinct()
        if (candidates.isEmpty()) return emptyList()

        val timestamps = store.getAll(candidates).associate { it.id to it.timestampMillis }
        val newest = timestamps.values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
        val scores = HashMap<Long, Double>()
        listOf(keywordIds, vectorIds).forEach { ranking ->
            ranking.forEachIndexed { rank, id -> scores.merge(id, 1.0 / (RRF_K + rank + 1), Double::plus) }
        }
        // Far smaller than one rank step, so recency only breaks ties.
        return candidates.sortedByDescending { id ->
            (scores[id] ?: 0.0) + 1e-7 * ((timestamps[id] ?: 0L).toDouble() / newest)
        }.take(limit)
    }

    private suspend fun vectorRanking(query: String, range: DateRange, limit: Int): List<Long> {
        val vector = try {
            embedQuery(query)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return emptyList()
        }
        val ids = store.nearest(vector, if (range.isAll) limit else limit * RANGE_VECTOR_FACTOR)
        if (range.isAll) return ids
        return store.getAll(ids).filter { it.timestampMillis in range }.map { it.id }.take(limit)
    }

    /** Exchanges ranked by how many of the query's rarer words they contain. */
    private fun keywordRanking(query: String, range: DateRange, limit: Int): List<Long> {
        val total = store.count()
        if (total == 0L) return emptyList()
        val weights = HashMap<Long, Double>()
        val timestamps = HashMap<Long, Long>()
        for (term in bm25Tokens(query).distinct()) {
            val matches = store.termCount(term, range.fromMillis, range.toMillis)
            if (matches == 0L) continue
            // A word in most exchanges ("kya", "the") says nothing about which one is meant.
            if (total >= 20 && matches * 10 > total * 3) continue
            val weight = ln(1.0 + total.toDouble() / (matches + 1))
            store.termMatches(term, range.fromMillis, range.toMillis, KEYWORD_CANDIDATES).forEach { id ->
                weights.merge(id, weight, Double::plus)
            }
        }
        if (weights.isEmpty()) return emptyList()
        store.getAll(weights.keys.toList()).forEach { timestamps[it.id] = it.timestampMillis }
        return weights.keys.sortedWith(
            compareByDescending<Long> { weights.getValue(it) }.thenByDescending { timestamps[it] ?: 0L }
        ).take(limit)
    }

    private fun render(turn: StoredTurn): String = renderTurn(turn, zone)
}

private val turnTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/** One exchange as the model reads it: both sides stamped with the local date and time. */
internal fun renderTurn(turn: StoredTurn, zone: ZoneId): String = buildString {
    val time = turnTimeFormatter.format(Instant.ofEpochMilli(turn.timestampMillis).atZone(zone))
    append('[').append(time).append("] You: ").append(turn.userText)
    if (turn.assistantText.isNotBlank()) append("\n[").append(time).append("] Ultron: ").append(turn.assistantText)
}
