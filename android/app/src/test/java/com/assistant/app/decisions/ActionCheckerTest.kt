package com.assistant.app.decisions

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ActionCheckerTest {
    private lateinit var server: MockWebServer

    @Before fun start() { server = MockWebServer().also { it.start() } }
    @After fun stop() { server.shutdown() }

    private fun checker() = ActionChecker(DecisionsClient("k", baseUrl = server.url("/").toString().trimEnd('/')))

    private fun reply(match: Pair<Double, Double> = 0.95 to 0.95, vararg risks: Pair<String, Double>): String {
        val riskMap = risks.toMap()
        val keys = listOf("is_irreversible", "sends_to_other_person", "involves_money", "deletes_data")
        val rows = listOf("matches_user_request" to match) + keys.map { it to ((riskMap[it] ?: 0.05) to 0.9) }
        return rows.joinToString(",", """{"answers":{""", """},"usage":{"total_input_tokens":90}}""") { (k, v) ->
            """"$k":{"type":"noul","noul":${v.first},"confidence":${v.second}}"""
        }
    }

    private fun check(app: String = "com.whatsapp", action: String = "tap", target: String = "Send", request: String = "message Mom I'm home") =
        runBlocking { checker().check(app, action, target, request) }

    @Test fun normalStepProceeds() {
        server.enqueue(MockResponse().setBody(reply()))
        assertTrue(check() is ActionVerdict.Proceed)
    }

    @Test fun clearMismatchIsBlocked() {
        server.enqueue(MockResponse().setBody(reply(match = 0.05 to 0.95)))
        val verdict = check(target = "Install now", request = "what's the weather") as ActionVerdict.Block
        assertTrue(verdict.reason.contains("Install now"))
    }

    @Test fun lowMatchWithLowConfidenceIsNotBlocked() {
        server.enqueue(MockResponse().setBody(reply(match = 0.4 to 0.6)))
        assertTrue(check() is ActionVerdict.Proceed)
        server.enqueue(MockResponse().setBody(reply(match = 0.2 to 0.6)))
        assertTrue(check(target = "Other") is ActionVerdict.Proceed)
    }

    @Test fun riskyStepProceedsWithWarning() {
        server.enqueue(MockResponse().setBody(reply(risks = arrayOf("involves_money" to 0.9, "is_irreversible" to 0.8))))
        val verdict = check(app = "com.phonepe.app", target = "Pay 500") as ActionVerdict.Warn
        assertTrue(verdict.note.contains("money"))
        assertTrue(verdict.note.contains("cannot be undone"))
    }

    @Test fun riskBelowThresholdIsQuiet() {
        server.enqueue(MockResponse().setBody(reply(risks = arrayOf("deletes_data" to 0.55))))
        assertTrue(check() is ActionVerdict.Proceed)
    }

    @Test fun failedCallProceeds() {
        server.enqueue(MockResponse().setResponseCode(503))
        assertTrue(check() is ActionVerdict.Proceed)
        server.enqueue(MockResponse().setResponseCode(429))
        assertTrue(check(target = "Other") is ActionVerdict.Proceed)
    }

    @Test fun blankRequestOrTargetSkipsTheCall() {
        assertTrue(check(request = "") is ActionVerdict.Proceed)
        assertTrue(check(target = "  ") is ActionVerdict.Proceed)
        assertEquals(0, server.requestCount)
    }

    @Test fun sendsAppStepTargetAndRequestAsJson() {
        server.enqueue(MockResponse().setBody(reply()))
        check()
        val body = org.json.JSONObject(server.takeRequest().body.readUtf8())
        val input = org.json.JSONObject(body.getString("input"))
        assertEquals("com.whatsapp", input.getString("app_on_screen"))
        assertEquals("tap", input.getString("step"))
        assertEquals("Send", input.getString("text_of_target"))
        assertEquals("message Mom I'm home", input.getString("user_request"))
        assertEquals(5, body.getJSONObject("questions").length())
    }
}
