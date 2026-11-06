package com.assistant.app.data

import com.assistant.app.network.GeminiEmbeddingClient
import io.objectbox.BoxStore

/** A finished exchange as read back from long-term memory. */
data class StoredTurn(
    val id: Long,
    val sessionId: String,
    val userText: String,
    val assistantText: String,
    val timestampMillis: Long,
    val embedded: Boolean
)

/**
 * Every finished exchange, kept on the phone with no expiry (the user deletes them). This is the long-term memory the
 * assistant searches; [ConversationStore] only keeps the last few minutes for the notification line.
 */
class ConversationHistoryStore(store: BoxStore) {
    private val box = store.boxFor(ConversationTurnEntity::class.java)

    companion object {
        /** Exchanges whose user text is shorter than this are noise (a cough, "uh") and are not worth remembering. */
        const val MIN_USER_CHARS = 2
        /** Most characters of one exchange sent for embedding and kept for display. */
        const val MAX_TURN_CHARS = 2_000

        /** The text that is embedded and keyword-searched for an exchange. */
        fun embeddingText(userText: String, assistantText: String): String =
            buildString {
                append("You: ").append(userText.trim())
                if (assistantText.isNotBlank()) append("\nUltron: ").append(assistantText.trim())
            }.take(MAX_TURN_CHARS)
    }

    /** Saves one exchange. Returns its id, or null when it was noise (no real user text). */
    fun append(sessionId: String, userText: String, assistantText: String, timestampMillis: Long): Long? {
        val user = userText.trim()
        if (user.length < MIN_USER_CHARS) return null
        return box.put(
            ConversationTurnEntity(
                sessionId = sessionId,
                userText = user.take(MAX_TURN_CHARS),
                assistantText = assistantText.trim().take(MAX_TURN_CHARS),
                timestampMillis = timestampMillis
            )
        )
    }

    /** The newest [count] exchanges, oldest first, so they read as a conversation. */
    fun recent(count: Int): List<StoredTurn> {
        if (count <= 0) return emptyList()
        val query = box.query().orderDesc(ConversationTurnEntity_.timestampMillis).build()
        return try {
            query.find(0, count.toLong()).map { it.toStored() }.reversed()
        } finally {
            query.close()
        }
    }

    /** Exchanges that still need an embedding in the current model (never embedded, or embedded with an older one). */
    fun pendingEmbedding(limit: Int): List<StoredTurn> {
        val query = box.query(ConversationTurnEntity_.embeddingModel.notEqual(GeminiEmbeddingClient.MODEL))
            .order(ConversationTurnEntity_.timestampMillis).build()
        return try {
            query.find(0, limit.toLong()).map { it.toStored() }
        } finally {
            query.close()
        }
    }

    /** Stores the embedding for [id]. Returns false when the exchange was deleted in the meantime. */
    fun setEmbedding(id: Long, embedding: FloatArray): Boolean {
        val entity = box.get(id) ?: return false
        entity.embedding = embedding
        entity.embeddingModel = GeminiEmbeddingClient.MODEL
        box.put(entity)
        return true
    }

    fun count(): Long = box.count()

    fun get(id: Long): StoredTurn? = box.get(id)?.toStored()

    /** The turns with these ids, in the same order; ids that no longer exist are left out. */
    fun getAll(ids: List<Long>): List<StoredTurn> {
        if (ids.isEmpty()) return emptyList()
        val byId = box.get(ids).filterNotNull().associateBy { it.id }
        return ids.mapNotNull { byId[it]?.toStored() }
    }

    /** Every turn of one session, oldest first. */
    fun sessionTurns(sessionId: String): List<StoredTurn> {
        val query = box.query(ConversationTurnEntity_.sessionId.equal(sessionId))
            .order(ConversationTurnEntity_.timestampMillis).build()
        return try {
            query.find().map { it.toStored() }
        } finally {
            query.close()
        }
    }

    /** Ids of the embedded turns closest in meaning to [vector], closest first. Distant turns are dropped. */
    fun nearest(vector: FloatArray, limit: Int, maxDistance: Double = MAX_RETRIEVAL_DISTANCE): List<Long> {
        if (limit <= 0) return emptyList()
        val query = box.query(ConversationTurnEntity_.embedding.nearestNeighbors(vector, limit)).build()
        return try {
            query.findWithScores().filter { it.score <= maxDistance }.map { it.get().id }
        } finally {
            query.close()
        }
    }

    private fun termCondition(term: String, fromMillis: Long?, toMillis: Long?): io.objectbox.query.QueryCondition<ConversationTurnEntity> {
        val order = io.objectbox.query.QueryBuilder.StringOrder.CASE_INSENSITIVE
        var condition = ConversationTurnEntity_.userText.contains(term, order)
            .or(ConversationTurnEntity_.assistantText.contains(term, order))
        if (fromMillis != null || toMillis != null) {
            condition = condition.and(
                ConversationTurnEntity_.timestampMillis.between(fromMillis ?: 0L, toMillis ?: Long.MAX_VALUE)
            )
        }
        return condition
    }

    /** How many turns mention [term] (either side of the exchange) inside the date range. */
    fun termCount(term: String, fromMillis: Long?, toMillis: Long?): Long {
        val query = box.query(termCondition(term, fromMillis, toMillis)).build()
        return try {
            query.count()
        } finally {
            query.close()
        }
    }

    /** Ids of up to [limit] turns that mention [term] inside the date range, newest first. */
    fun termMatches(term: String, fromMillis: Long?, toMillis: Long?, limit: Int): List<Long> {
        val query = box.query(termCondition(term, fromMillis, toMillis))
            .orderDesc(ConversationTurnEntity_.timestampMillis).build()
        return try {
            query.findIds(0, limit.toLong()).toList()
        } finally {
            query.close()
        }
    }

    private fun ConversationTurnEntity.toStored() = StoredTurn(
        id = id,
        sessionId = sessionId,
        userText = userText,
        assistantText = assistantText,
        timestampMillis = timestampMillis,
        embedded = embeddingModel == GeminiEmbeddingClient.MODEL && embedding != null
    )
}

/**
 * Makes saved exchanges searchable by embedding them in the background. A failure (offline, quota, bad key) stops the
 * pass and leaves the rest pending for the next one, so it never blocks a conversation.
 */
class ConversationHistoryIndexer(
    private val store: ConversationHistoryStore,
    private val embed: suspend (String) -> FloatArray
) {
    /** Embeds up to [batchSize] pending exchanges. Returns how many were embedded. */
    suspend fun indexPending(batchSize: Int = DEFAULT_BATCH): Int {
        var done = 0
        for (turn in store.pendingEmbedding(batchSize)) {
            val vector = try {
                embed(ConversationHistoryStore.embeddingText(turn.userText, turn.assistantText))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                return done
            }
            if (store.setEmbedding(turn.id, vector)) done++
        }
        return done
    }

    private companion object {
        const val DEFAULT_BATCH = 20
    }
}
