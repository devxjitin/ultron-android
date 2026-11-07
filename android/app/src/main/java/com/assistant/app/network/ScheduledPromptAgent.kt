package com.assistant.app.network

import com.assistant.app.data.DeviceTool
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** How a scheduled run ended. [needsUnlock] is true when the task needs the screen and the phone was locked. */
data class AgentOutcome(val ok: Boolean, val summary: String, val needsUnlock: Boolean = false)

/**
 * Runs one scheduled prompt with nobody watching: Gemini plans, calls phone tools through [runTool], reads the results and
 * goes on until it answers in plain text or runs out of steps. Uses the user's own Gemini key, like the voice session.
 * [tools] are the device tools it may use; [phoneLocked] adds the needs_unlocked_phone escape so a task that needs the
 * screen stops cleanly instead of failing half way.
 */
class ScheduledPromptAgent(
    private val apiKey: String,
    private val tools: List<DeviceTool>,
    private val phoneLocked: Boolean,
    private val ownerName: String,
    private val runTool: suspend (DeviceTool, JSONObject) -> String,
    private val model: String = GeminiTextClient.FALLBACK_MODEL,
    /** Gemini's address; tests point it at a local server. */
    private val baseUrl: String = "https://generativelanguage.googleapis.com"
) {
    private val http = HttpClients.shared.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun run(prompt: String, nowText: String): AgentOutcome {
        val contents = JSONArray().put(
            JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt)))
        )
        repeat(MAX_STEPS) {
            val content = generate(systemInstruction(nowText), contents)
            contents.put(content)
            val parts = content.optJSONArray("parts") ?: JSONArray()
            val calls = (0 until parts.length()).mapNotNull { parts.getJSONObject(it).optJSONObject("functionCall") }
            if (calls.isEmpty()) {
                val text = (0 until parts.length()).joinToString(" ") { parts.getJSONObject(it).optString("text") }.trim()
                return AgentOutcome(ok = !text.startsWith(FAILED_MARK), summary = text.removePrefix(FAILED_MARK).trim().ifBlank { "Done." })
            }
            val responses = JSONArray()
            for (call in calls) {
                val name = call.optString("name")
                if (name == NEEDS_UNLOCK_TOOL) {
                    return AgentOutcome(ok = false, summary = "The phone was locked, so it could not use the screen.", needsUnlock = true)
                }
                val tool = tools.firstOrNull { it.functionName == name }
                val result = if (tool == null) {
                    "status=unknown_tool\nThere is no tool named $name here."
                } else {
                    runTool(tool, call.optJSONObject("args") ?: JSONObject())
                }
                val response = JSONObject().put("name", name).put("response", JSONObject().put("result", result))
                call.optString("id").takeIf { it.isNotBlank() }?.let { response.put("id", it) }
                responses.put(JSONObject().put("functionResponse", response))
            }
            contents.put(JSONObject().put("role", "user").put("parts", responses))
        }
        return AgentOutcome(ok = false, summary = "It took too many steps, so Ultron stopped.")
    }

    private fun systemInstruction(nowText: String) = """
You are Ultron, the voice assistant on ${ownerName.ifBlank { "the user" }}'s Android phone. Earlier they scheduled this task for now: do it on their behalf with the tools. Nobody is watching and nobody can answer questions.
- Do exactly the task, step by step, and nothing more. Never send, buy, delete or post anything the task does not ask for.
- To message someone in an app, find the contact, open the chat, type the message, press send, then check the screen that it was sent.
- If the task is unclear, impossible or a tool keeps failing, stop and explain why in one sentence that starts with "$FAILED_MARK".
${if (phoneLocked) "- The phone is locked. If the task needs to open an app, read or touch the screen, call $NEEDS_UNLOCK_TOOL at once instead of trying." else ""}
- When finished, answer with one short sentence of what you did, in the language of the task, with no markdown.
The time now is $nowText.
""".trim()

    private suspend fun generate(system: String, contents: JSONArray): JSONObject {
        val declarations = JSONArray()
        tools.forEach { declarations.put(ToolDeclarations.device(it).apply { remove("behavior") }) }
        if (phoneLocked) {
            declarations.put(
                JSONObject().put("name", NEEDS_UNLOCK_TOOL)
                    .put("description", "Call this when the task needs the screen (opening an app, reading or tapping the screen) while the phone is locked.")
            )
        }
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
            .put("tools", JSONArray().put(JSONObject().put("functionDeclarations", declarations)))
            .put("generationConfig", JSONObject().put("maxOutputTokens", 2048))
            .toString()
        var attempt = 0
        while (true) {
            try {
                return post(body)
            } catch (e: GeminiEmbeddingClient.HttpFailure) {
                // Rate limits and server hiccups are worth one more try; a bad key or request is not.
                if ((e.statusCode == 429 || e.statusCode >= 500) && attempt < 2) {
                    attempt++
                    delay(3_000L * attempt)
                } else throw e
            }
        }
    }

    private suspend fun post(body: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", apiKey)
            .post(body.toRequestBody(JSON))
            .build()
        val call = http.newCall(request)
        try {
            call.execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw GeminiEmbeddingClient.HttpFailure(response.code, "Scheduled run failed (${response.code}): ${raw.take(200)}")
                }
                val candidate = JSONObject(raw).optJSONArray("candidates")?.optJSONObject(0)
                    ?: throw IOException("Gemini sent no answer.")
                // The model's turn is sent back as it came, so its thought signatures stay intact for the next step.
                candidate.optJSONObject("content")?.put("role", "model")
                    ?: JSONObject().put("role", "model").put("parts", JSONArray().put(JSONObject().put("text", "$FAILED_MARK Gemini gave no answer.")))
            }
        } catch (e: IOException) {
            currentCoroutineContext().ensureActive()
            throw e
        }
    }

    companion object {
        const val MAX_STEPS = 15
        const val NEEDS_UNLOCK_TOOL = "needs_unlocked_phone"
        /** The agent starts its answer with this when it gave up; it is stripped before the user sees it. */
        const val FAILED_MARK = "FAILED:"
        private val JSON = "application/json".toMediaType()
    }
}
