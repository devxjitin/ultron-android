package com.assistant.app.decisions

import com.assistant.app.network.HttpClients
import com.assistant.app.network.watchCancellation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Why a Decisions call gave no answer. Callers fall back to what the app did before the call existed. */
enum class DecisionFailure { TIMEOUT, RATE_LIMITED, SERVER, REJECTED, NETWORK, BAD_RESPONSE }

sealed class DecisionOutcome {
    class Answered(val result: DecisionResult, val latencyMs: Long, val fromCache: Boolean = false) : DecisionOutcome()
    class Failed(val reason: DecisionFailure, val detail: String = "") : DecisionOutcome()
}

/**
 * Calls the Gemini Decisions API (`POST /v1beta/decisions`) with the user's own key. Never throws for a failed call:
 * it returns [DecisionOutcome.Failed] so every feature can keep working exactly as it did without Decisions.
 * (Firebase AI Logic has no Decisions support, so this uses REST like the other Gemini clients.)
 */
class DecisionsClient(
    private val apiKey: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val model: String = MODEL,
    private val nowMs: () -> Long = System::currentTimeMillis,
    /** Receives one line per call for the local tuning log; it must never contain the input text. */
    private val log: ((String) -> Unit)? = null,
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com"
        const val MODEL = "gemini-decision-1"
        private const val CACHE_SIZE = 64
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }

    private val client = HttpClients.shared.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** Skips optional checks for the rest of the session once the key's quota says to slow down. */
    @Volatile var rateLimited: Boolean = false
        private set

    private val cache = object : LinkedHashMap<String, DecisionResult>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, DecisionResult>) = size > CACHE_SIZE
    }

    /**
     * Asks [questions] about [input]. [feature] names the caller in the log. Waits at most [timeoutMs] in total,
     * including the one retry after a dropped connection.
     */
    suspend fun decide(
        feature: String,
        input: List<DecisionInput>,
        questions: QuestionSet,
        timeoutMs: Long,
        useCache: Boolean = true,
    ): DecisionOutcome {
        val body = buildBody(input, questions).toString()
        val cacheKey = (model + body).hashCode().toString()
        if (useCache) synchronized(cache) { cache[cacheKey] }?.let {
            log?.invoke("$feature cache_hit")
            return DecisionOutcome.Answered(it, 0, fromCache = true)
        }
        val started = nowMs()
        val outcome = try {
            withTimeout(timeoutMs) { postWithRetry(body, questions) }
        } catch (_: TimeoutCancellationException) {
            DecisionOutcome.Failed(DecisionFailure.TIMEOUT, "no answer within ${timeoutMs}ms")
        }
        val elapsed = nowMs() - started
        when (outcome) {
            is DecisionOutcome.Answered -> {
                if (useCache) synchronized(cache) { cache[cacheKey] = outcome.result }
                log?.invoke("$feature ok ${elapsed}ms tokens=${outcome.result.inputTokens}")
                return DecisionOutcome.Answered(outcome.result, elapsed)
            }
            is DecisionOutcome.Failed -> {
                if (outcome.reason == DecisionFailure.RATE_LIMITED) rateLimited = true
                log?.invoke("$feature failed ${outcome.reason} ${elapsed}ms")
                return outcome
            }
        }
    }

    internal fun buildBody(input: List<DecisionInput>, questions: QuestionSet): JSONObject {
        require(input.isNotEmpty()) { "Decisions need an input" }
        val inputJson: Any = if (input.size == 1 && input[0] is DecisionInput.Text) {
            (input[0] as DecisionInput.Text).text
        } else {
            JSONArray().apply { input.forEach { put(it.toJson()) } }
        }
        return JSONObject().put("model", model).put("input", inputJson).put("questions", questions.toJson())
    }

    private suspend fun postWithRetry(body: String, questions: QuestionSet): DecisionOutcome {
        var outcome = post(body, questions)
        // One retry, only for a dropped connection (seen once in the Phase 0 probe), never for HTTP errors.
        if (outcome is DecisionOutcome.Failed && outcome.reason == DecisionFailure.NETWORK) outcome = post(body, questions)
        return outcome
    }

    private suspend fun post(body: String, questions: QuestionSet): DecisionOutcome = withContext(Dispatchers.IO) {
        // Key goes in a header, not the URL, so it can't leak through logged URLs.
        val request = Request.Builder().url("$baseUrl/v1beta/decisions")
            .header("x-goog-api-key", apiKey).post(body.toRequestBody(JSON_MEDIA_TYPE)).build()
        val call = client.newCall(request)
        val watcher = watchCancellation(call)
        try {
            call.execute().use { response ->
                val raw = response.body?.string().orEmpty()
                when {
                    response.code == 429 -> DecisionOutcome.Failed(DecisionFailure.RATE_LIMITED)
                    response.code in 500..599 -> DecisionOutcome.Failed(DecisionFailure.SERVER, "HTTP ${response.code}")
                    !response.isSuccessful ->
                        DecisionOutcome.Failed(DecisionFailure.REJECTED, "HTTP ${response.code}: ${raw.take(200)}")
                    else -> parse(raw, questions)
                }
            }
        } catch (e: IOException) {
            // A cancelled call fails with an IOException; report it as the cancellation it is.
            currentCoroutineContext().ensureActive()
            DecisionOutcome.Failed(DecisionFailure.NETWORK, e.javaClass.simpleName)
        } finally {
            watcher.cancel()
        }
    }

    internal fun parse(raw: String, questions: QuestionSet): DecisionOutcome = try {
        val root = JSONObject(raw)
        val answersJson = root.getJSONObject("answers")
        val answers = LinkedHashMap<String, Answer>()
        for ((key, question) in questions.questions) {
            val a = answersJson.getJSONObject(key)
            val confidence = a.getDouble("confidence")
            answers[key] = when (question) {
                is Question.YesNo -> Answer.YesNo(a.getDouble("noul"), confidence)
                is Question.Choice -> {
                    val probs = a.optJSONObject("probabilities")
                    Answer.Choice(
                        a.getString("choice"),
                        probs?.keys()?.asSequence()?.associateWith { probs.getDouble(it) }.orEmpty(),
                        confidence,
                    )
                }
                is Question.Score -> Answer.Score(a.getDouble("score"), confidence)
            }
        }
        DecisionOutcome.Answered(
            DecisionResult(answers, root.optJSONObject("usage")?.optInt("total_input_tokens") ?: 0), 0,
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        DecisionOutcome.Failed(DecisionFailure.BAD_RESPONSE, e.message.orEmpty().take(120))
    }
}
