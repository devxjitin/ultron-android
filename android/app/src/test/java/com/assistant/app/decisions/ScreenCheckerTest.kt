package com.assistant.app.decisions

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScreenCheckerTest {
    private lateinit var server: MockWebServer

    @Before fun start() { server = MockWebServer().also { it.start() } }
    @After fun stop() { server.shutdown() }

    private fun checker() = ScreenChecker(DecisionsClient("k", baseUrl = server.url("/").toString().trimEnd('/')))

    private fun reply(yes: Double, blocker: String, next: String) = """{"answers":{
        "step_succeeded":{"type":"noul","noul":$yes,"confidence":0.9},
        "blocker":{"type":"choice","choice":"$blocker","probabilities":{"$blocker":0.9},"confidence":0.9},
        "next":{"type":"choice","choice":"$next","probabilities":{"$next":0.9},"confidence":0.9}},
        "usage":{"total_input_tokens":500}}"""

    private fun check() = runBlocking {
        checker().check("click_on_text", "open the Rahul chat", DecisionInput.Image(byteArrayOf(1, 2, 3), resolution = "low"))
    }

    @Test fun successfulStepContinues() {
        server.enqueue(MockResponse().setBody(reply(0.95, "none", "continue")))
        val v = check()!!
        assertTrue(v.succeeded)
        assertEquals("screen_check: step_succeeded=yes; blocker=none; suggested_next=continue", v.toResultLine())
    }

    @Test fun loginWallAsksTheUser() {
        server.enqueue(MockResponse().setBody(reply(0.1, "login", "ask_user")))
        val v = check()!!
        assertFalse(v.succeeded)
        assertEquals("screen_check: step_succeeded=no; blocker=login; suggested_next=ask user", v.toResultLine())
    }

    @Test fun unknownOptionsFallBackSafely() {
        server.enqueue(MockResponse().setBody(reply(0.2, "weird", "dance")))
        val v = check()!!
        assertEquals("none", v.blocker)
        assertEquals("retry", v.next)
    }

    @Test fun failedCallGivesNoVerdict() {
        server.enqueue(MockResponse().setResponseCode(429))
        assertNull(check())
    }

    @Test fun sendsImageAtLowResolutionThenGoalText() {
        server.enqueue(MockResponse().setBody(reply(0.9, "none", "continue")))
        check()
        val body = org.json.JSONObject(server.takeRequest().body.readUtf8())
        val blocks = body.getJSONArray("input")
        assertEquals("image", blocks.getJSONObject(0).getString("type"))
        assertEquals("low", blocks.getJSONObject(0).getString("resolution"))
        assertTrue(blocks.getJSONObject(1).getString("text").contains("open the Rahul chat"))
        assertNotNull(body.getJSONObject("questions").getJSONObject("blocker").getJSONObject("criteria").getString("login"))
    }

    @Test fun thirdFailureInARowBecomesStop() {
        val failed = ScreenVerdict(false, "error", "retry")
        val c = checker()
        assertEquals("retry", c.limited(failed, 2).next)
        assertEquals("stop", c.limited(failed, 3).next)
        assertEquals("continue", c.limited(ScreenVerdict(true, "none", "continue"), 5).next)
    }
}
