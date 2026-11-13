package com.assistant.app.decisions

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DecisionsClientTest {
    private lateinit var server: MockWebServer
    private val logs = mutableListOf<String>()

    @Before fun start() { server = MockWebServer().also { it.start() } }
    @After fun stop() { server.shutdown() }

    private fun client() = DecisionsClient("test-key", baseUrl = server.url("/").toString().trimEnd('/'), log = { logs += it })

    private val qs = questions {
        yesNo("needs_whatsapp", "Does it need WhatsApp?")
        choice("main", "Main connector?", mapOf("whatsapp" to "Messaging", "none" to "Chat"))
        score("importance", "How important?", listOf("low", "mid", "high"))
    }

    private val okBody = """{"answers":{
        "needs_whatsapp":{"type":"noul","noul":0.92,"confidence":0.92},
        "main":{"type":"choice","choice":"whatsapp","probabilities":{"whatsapp":0.9,"none":0.1},"confidence":0.9},
        "importance":{"type":"score","score":1.4,"legend":{"0":"low","1":"mid","2":"high"},"confidence":0.7}},
        "usage":{"total_input_tokens":272,"total_output_tokens":0,"total_tokens":272}}"""

    private fun ask(c: DecisionsClient, timeoutMs: Long = 3_000, useCache: Boolean = true) = runBlocking {
        c.decide("test", listOf(DecisionInput.Text("tell Rahul I'm late")), qs, timeoutMs, useCache)
    }

    @Test fun parsesAllThreeAnswerTypes() {
        server.enqueue(MockResponse().setBody(okBody))
        val out = ask(client()) as DecisionOutcome.Answered
        assertTrue(out.result.yesNo("needs_whatsapp")!!.isYes)
        assertEquals("whatsapp", out.result.choice("main")!!.choice)
        assertEquals(0.9, out.result.choice("main")!!.probabilities["whatsapp"]!!, 0.0001)
        assertEquals(1.4, out.result.score("importance")!!.score, 0.0001)
        assertEquals(272, out.result.inputTokens)
    }

    @Test fun sendsKeyInHeaderAndExpectedBody() {
        server.enqueue(MockResponse().setBody(okBody))
        ask(client())
        val request = server.takeRequest()
        assertEquals("/v1beta/decisions", request.path)
        assertEquals("test-key", request.getHeader("x-goog-api-key"))
        assertFalse(request.path!!.contains("test-key"))
        val body = org.json.JSONObject(request.body.readUtf8())
        assertEquals("gemini-decision-1", body.getString("model"))
        assertEquals("tell Rahul I'm late", body.getString("input"))
        assertEquals("noul", body.getJSONObject("questions").getJSONObject("needs_whatsapp").getString("type"))
        assertEquals(2, body.getJSONObject("questions").getJSONObject("main").getJSONObject("criteria").length())
    }

    @Test fun imageInputUsesBlocksWithResolution() {
        val body = client().buildBody(
            listOf(DecisionInput.Image(byteArrayOf(1, 2, 3), resolution = "low"), DecisionInput.Text("step")), qs,
        )
        val blocks = body.getJSONArray("input")
        assertEquals("image", blocks.getJSONObject(0).getString("type"))
        assertEquals("low", blocks.getJSONObject(0).getString("resolution"))
        assertEquals("text", blocks.getJSONObject(1).getString("type"))
    }

    @Test fun secondIdenticalCallComesFromCache() {
        server.enqueue(MockResponse().setBody(okBody))
        val c = client()
        ask(c)
        val second = ask(c) as DecisionOutcome.Answered
        assertTrue(second.fromCache)
        assertEquals(1, server.requestCount)
    }

    @Test fun rateLimitFailsAndRemembersIt() {
        server.enqueue(MockResponse().setResponseCode(429))
        val c = client()
        val out = ask(c) as DecisionOutcome.Failed
        assertEquals(DecisionFailure.RATE_LIMITED, out.reason)
        assertTrue(c.rateLimited)
    }

    @Test fun serverErrorFailsWithoutRetry() {
        server.enqueue(MockResponse().setResponseCode(503))
        assertEquals(DecisionFailure.SERVER, (ask(client()) as DecisionOutcome.Failed).reason)
        assertEquals(1, server.requestCount)
    }

    @Test fun rejectedRequestIsReported() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"code":400,"message":"blocked"}}"""))
        assertEquals(DecisionFailure.REJECTED, (ask(client()) as DecisionOutcome.Failed).reason)
    }

    @Test fun droppedConnectionIsRetriedOnce() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(MockResponse().setBody(okBody))
        assertNotNull(ask(client()) as? DecisionOutcome.Answered)
        assertEquals(2, server.requestCount)
    }

    @Test fun slowAnswerTimesOut() {
        server.enqueue(MockResponse().setBody(okBody).setBodyDelay(2, java.util.concurrent.TimeUnit.SECONDS))
        val out = ask(client(), timeoutMs = 300) as DecisionOutcome.Failed
        assertEquals(DecisionFailure.TIMEOUT, out.reason)
    }

    @Test fun malformedBodyIsBadResponse() {
        server.enqueue(MockResponse().setBody("""{"answers":{}}"""))
        assertEquals(DecisionFailure.BAD_RESPONSE, (ask(client()) as DecisionOutcome.Failed).reason)
    }

    @Test fun logNeverContainsInputText() {
        server.enqueue(MockResponse().setBody(okBody))
        ask(client())
        assertTrue(logs.isNotEmpty())
        assertTrue(logs.none { "Rahul" in it })
    }

    @Test fun builderChecksLimits() {
        assertRejects { questions { yesNo("bad key!", "x") } }
        assertRejects { questions { yesNo("a".repeat(65), "x") } }
        assertRejects { questions { yesNo("a", "x"); yesNo("a", "y") } }
        assertRejects { questions { choice("c", "x", mapOf("only" to "one")) } }
        assertRejects { questions { score("s", "x", List(256) { "l$it" }) } }
        assertRejects { questions { } }
        assertRejects { questions { repeat(1_001) { yesNo("q$it", "x") } } }
        assertEquals(1_000, questions { repeat(1_000) { yesNo("q$it", "x") } }.questions.size)
    }

    private fun assertRejects(block: () -> Unit) {
        try { block(); throw AssertionError("expected IllegalArgumentException") } catch (_: IllegalArgumentException) {}
    }
}
