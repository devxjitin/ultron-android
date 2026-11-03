package com.assistant.app.data

import com.assistant.app.network.GeminiEmbeddingClient
import java.io.IOException
import java.net.SocketTimeoutException

/** Why a knowledge search could not complete. Logged by name; never contains user content. */
enum class SearchFailureCategory(val transient: Boolean) {
    NETWORK(true),
    TIMEOUT(true),
    SERVICE(true),
    CONFIG(false),
    INDEX(false),
    REINDEX_REQUIRED(false),
    UNKNOWN(false)
}

fun classifySearchFailure(t: Throwable): SearchFailureCategory = when {
    t is GeminiEmbeddingClient.HttpFailure -> when (t.statusCode) {
        400, 401, 403 -> SearchFailureCategory.CONFIG
        429, in 500..599 -> SearchFailureCategory.SERVICE
        else -> SearchFailureCategory.UNKNOWN
    }
    t is EmbeddingMigrationRequiredException -> SearchFailureCategory.REINDEX_REQUIRED
    t is SocketTimeoutException -> SearchFailureCategory.TIMEOUT
    t is IOException -> SearchFailureCategory.NETWORK
    t is IllegalStateException -> SearchFailureCategory.INDEX
    else -> SearchFailureCategory.UNKNOWN
}

/** Builds the text returned to the voice model for each search_knowledge_base outcome. */
object KnowledgeToolResponse {

    fun results(chunks: List<String>): String =
        if (chunks.isEmpty()) noMatch() else "status=results\n" + chunks.joinToString("\n\n")

    fun noMatch(): String =
        "status=no_match\nNo matching information was found. This is not proof the information doesn't exist. " +
            "Ask a clarifying question, or say you can't confirm it."

    fun invalidQuery(): String =
        "status=invalid_query\nThe search query was empty. Ask the user to repeat their question, " +
            "then search again with it. Do not say the information doesn't exist."

    fun notConfigured(): String =
        "status=search_failed; reason=not_configured\nKnowledge search is not set up on this device. " +
            "Tell the user you can't verify this right now; do not say the information doesn't exist."

    fun failed(category: SearchFailureCategory): String {
        val advice = when (category) {
            SearchFailureCategory.CONFIG ->
                "Knowledge search needs its setup checked in Developer settings."
            SearchFailureCategory.INDEX ->
                "The knowledge index is not ready. It may need to be rebuilt from the Knowledge page."
            SearchFailureCategory.REINDEX_REQUIRED ->
                "Knowledge search is being updated and is not ready yet. Offer to try again in a few minutes."
            else ->
                "The lookup could not be completed right now. Offer to try again."
        }
        return "status=search_failed; reason=${category.name.lowercase()}\n$advice " +
            "Tell the user you could not check this right now. Do not say the information doesn't exist."
    }
}
