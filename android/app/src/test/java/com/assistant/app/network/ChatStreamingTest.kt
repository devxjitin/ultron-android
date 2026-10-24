package com.assistant.app.network

import com.assistant.app.data.AzureConfig
import com.assistant.app.data.AzureMode
import com.assistant.app.data.NimConfig
import com.assistant.app.util.UserMessageException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ChatStreamingTest {
    private lateinit var server: MockWebServer

    @Before fun start() {
        GeminiChatClient.resetThinkingSupportForTest()
        server = MockWebServer().also { it.start() }
    }
    @After fun stop() { runCatching { server.shutdown() } }

    private class Recorder : ChatStreamListener {
        val text = StringBuilder()
        override suspend fun onText(delta: String) { text.append(delta) }
        override suspend fun onToolStart(index: Int, name: String) {}
        override suspend fun onToolEnd(index: Int, ok: Boolean) {}
    }

    private fun sse(vararg events: String) = MockResponse()
        .setHeader("Content-Type", "text/event-stream")
        .setBody(events.joinToString("") { "data: $it\n\n" })

    private fun azure() = OpenAiChatClient.azure(
        AzureConfig(mode = AzureMode.ENDPOINT, endpoint = server.url("/").toString(), apiKey = "key", model = "test-model"), ""
    )

    private fun nim() = OpenAiChatClient.nvidia(
        NimConfig(enabled = true, apiKey = "nvapi-key", model = "meta/test"), server.url("/v1").toString().trimEnd('/')
    )

    private fun gemini() = GeminiChatClient("key", baseUrl = server.url("/").toString().trimEnd('/'))

    private val question = listOf(ChatTurnMessage(true, "hi"))
    private val noTool: suspend (String, JSONObject) -> String = { _, _ -> "" }

    private fun chunk(content: String, finish: String? = null) = JSONObject().put(
        "choices", JSONArray().put(
            JSONObject()
                .put("delta", JSONObject().put("content", content))
                .put("finish_reason", finish ?: JSONObject.NULL)
        )
    ).toString()

    private fun geminiChunk(text: String, finish: String? = null) = JSONObject().put(
        "candidates", JSONArray().put(
            JSONObject()
                .put("content", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", text))))
                .apply { if (finish != null) put("finishReason", finish) }
        )
    ).toString()

    private fun azureReply(recorder: Recorder = Recorder()) =
        runBlocking { azure().stream("sys", question, JSONArray(), noTool, recorder) }

    private fun geminiReply(recorder: Recorder = Recorder()) =
        runBlocking { gemini().stream("sys", question, JSONArray(), noTool, recorder) }

    private fun rejected(block: () -> Unit): String {
        try {
            block()
        } catch (e: UserMessageException) {
            return e.message.orEmpty()
        }
        fail("expected the reply to be rejected")
        return ""
    }

    // ---- NVIDIA NIM

    @Test fun `nim sends a bearer key and max_tokens`() {
        server.enqueue(sse(chunk("Hi", "stop"), "[DONE]"))
        assertEquals("Hi", runBlocking { nim().stream("sys", question, JSONArray(), noTool, Recorder()) })
        val request = server.takeRequest()
        assertEquals("/v1/chat/completions", request.path)
        assertEquals("Bearer nvapi-key", request.getHeader("Authorization"))
        val body = JSONObject(request.body.readUtf8())
        assertEquals("meta/test", body.getString("model"))
        assertTrue(body.has("max_tokens"))
        assertTrue(!body.has("max_completion_tokens"))
    }

    @Test fun `nim answers without tools when the model rejects them`() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("{\"error\":\"tools are not supported\"}"))
        server.enqueue(sse(chunk("Plain answer", "stop"), "[DONE]"))
        assertEquals("Plain answer", runBlocking { nim().stream("sys", question, JSONArray().put(JSONObject().put("name", "get_time").put("description", "time")), noTool, Recorder()) })
        assertTrue(JSONObject(server.takeRequest().body.readUtf8()).has("tools"))
        assertTrue(!JSONObject(server.takeRequest().body.readUtf8()).has("tools"))
    }

    // ---- Azure

    @Test fun `azure reply that ends with stop is complete`() {
        server.enqueue(sse(chunk("Hel"), chunk("lo", "stop"), "[DONE]"))
        val recorder = Recorder()
        assertEquals("Hello", azureReply(recorder))
        assertEquals("Hello", recorder.text.toString())
    }

    @Test fun `azure stream cut off without a finish reason is not a finished reply`() {
        server.enqueue(sse(chunk("Hel")))
        val recorder = Recorder()
        rejected { azureReply(recorder) }
        assertEquals("what arrived is still shown", "Hel", recorder.text.toString())
    }

    @Test fun `azure length and content filter endings are rejected`() {
        server.enqueue(sse(chunk("a", "length"), "[DONE]"))
        assertTrue(rejected { azureReply() }.contains("length"))
        server.enqueue(sse(chunk("a", "content_filter"), "[DONE]"))
        assertTrue(rejected { azureReply() }.contains("filter"))
    }

    @Test fun `azure error event is reported with its message`() {
        server.enqueue(sse("{\"error\":{\"message\":\"quota exhausted\"}}"))
        assertTrue(rejected { azureReply() }.contains("quota"))
    }

    // ---- Gemini

    @Test fun `gemini reply that ends with STOP is complete`() {
        server.enqueue(sse(geminiChunk("Hi "), geminiChunk("there", "STOP")))
        assertEquals("Hi there", geminiReply())
    }

    @Test fun `gemini MAX_TOKENS and safety endings are rejected`() {
        server.enqueue(sse(geminiChunk("a", "MAX_TOKENS")))
        assertTrue(rejected { geminiReply() }.contains("length"))
        server.enqueue(sse(geminiChunk("a", "SAFETY")))
        assertTrue(rejected { geminiReply() }.contains("safety"))
    }

    @Test fun `gemini blocked prompt and missing finish reason are rejected`() {
        server.enqueue(sse("{\"promptFeedback\":{\"blockReason\":\"OTHER\"}}"))
        assertTrue(rejected { geminiReply() }.contains("blocked"))
        server.enqueue(sse(geminiChunk("partial")))
        assertTrue(rejected { geminiReply() }.contains("ended before"))
    }

    // ---- Cancellation

    @Test fun `cancelling while the server stays silent drops the request promptly`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        runBlocking {
            val job = launch(Dispatchers.Default) {
                azure().stream("sys", question, JSONArray(), noTool, Recorder())
            }
            delay(400) // the request has reached the server by now
            // The client's read timeout is 90 s; a working cancel must finish far sooner than this.
            withTimeout(5_000) { job.cancelAndJoin() }
            assertTrue(job.isCancelled)
        }
    }

    @Test fun geminiAsksForLowThinking() {
        server.enqueue(sse(geminiChunk("ok", "STOP")))
        geminiReply()
        val sent = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("low", sent.getJSONObject("generationConfig").getJSONObject("thinkingConfig").getString("thinkingLevel"))
    }

    private fun geminiCallChunk(text: String, tool: String) = JSONObject().put(
        "candidates", JSONArray().put(
            JSONObject().put("finishReason", "STOP").put(
                "content", JSONObject().put(
                    "parts", JSONArray()
                        .put(JSONObject().put("text", text))
                        .put(JSONObject().put("functionCall", JSONObject().put("name", tool).put("args", JSONObject())))
                )
            )
        )
    ).toString()

    private val oneTool = JSONArray().put(JSONObject().put("name", "get_weather").put("description", "Weather"))

    @Test fun geminiTextAfterAToolCallStartsANewParagraph() {
        server.enqueue(sse(geminiCallChunk("Let me check.", "get_weather")))
        server.enqueue(sse(geminiChunk("It is sunny.", "STOP")))
        val recorder = Recorder()
        val reply = runBlocking { gemini().stream("sys", question, oneTool, { _, _ -> "status=ok\nSunny" }, recorder) }
        assertEquals("Let me check.\n\nIt is sunny.", reply)
        assertEquals(reply, recorder.text.toString())
    }

    @Test fun geminiRetriesOnceAfterAMalformedToolCall() {
        server.enqueue(sse(geminiChunk("", "MALFORMED_FUNCTION_CALL")))
        server.enqueue(sse(geminiChunk("Done.", "STOP")))
        val reply = runBlocking { gemini().stream("sys", question, oneTool, noTool, Recorder()) }
        assertEquals("Done.", reply)
        assertEquals(2, server.requestCount)
    }

    @Test fun geminiLastRoundKeepsToolsButSwitchesCallingOff() {
        repeat(5) { server.enqueue(sse(geminiCallChunk("", "get_weather"))) }
        server.enqueue(sse(geminiChunk("Here is what I found.", "STOP")))
        runBlocking { gemini().stream("sys", question, oneTool, { _, _ -> "status=ok\nSunny" }, Recorder()) }
        repeat(5) { server.takeRequest() }
        val last = JSONObject(server.takeRequest().body.readUtf8())
        assertTrue(last.has("tools"))
        assertEquals("NONE", last.getJSONObject("toolConfig").getJSONObject("functionCallingConfig").getString("mode"))
    }

    @Test fun geminiRetriesWithoutThinkingWhenTheModelRejectsIt() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"message":"Thinking level low is not supported"}}"""))
        server.enqueue(sse(geminiChunk("hello", "STOP")))
        val recorder = Recorder()
        geminiReply(recorder)
        assertEquals("hello", recorder.text.toString())
        server.takeRequest()
        val retry = JSONObject(server.takeRequest().body.readUtf8())
        assertTrue(!retry.getJSONObject("generationConfig").has("thinkingConfig"))
    }
}
