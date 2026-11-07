package com.assistant.app.network

import com.assistant.app.data.DeviceTool
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScheduledPromptAgentTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After fun tearDown() {
        server.shutdown()
    }

    private fun reply(vararg parts: String) = MockResponse().setBody(
        """{"candidates":[{"content":{"role":"model","parts":[${parts.joinToString(",")}]}}]}"""
    )

    private fun agent(locked: Boolean = false, ran: MutableList<String> = mutableListOf()) = ScheduledPromptAgent(
        apiKey = "test-key", tools = listOf(DeviceTool.SET_DND, DeviceTool.OPEN_APP), phoneLocked = locked, ownerName = "Asha",
        runTool = { tool, args -> ran += "${tool.functionName}:$args"; "status=ok\nDone." },
        baseUrl = server.url("/").toString().trimEnd('/')
    )

    @Test fun `runs the tool the model asks for and reports its final answer`() = runBlocking {
        server.enqueue(reply("""{"functionCall":{"name":"set_do_not_disturb","args":{"state":"on"}},"thoughtSignature":"sig"}"""))
        server.enqueue(reply("""{"text":"Do not disturb is on."}"""))
        val ran = mutableListOf<String>()
        val outcome = agent(ran = ran).run("Put the phone on silent", "Today, 9:00 pm")
        assertTrue(outcome.ok)
        assertEquals("Do not disturb is on.", outcome.summary)
        assertEquals(listOf("set_do_not_disturb:{\"state\":\"on\"}"), ran)

        val first = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("test-key", server.takeRequest().getHeader("x-goog-api-key"))
        assertTrue(first.getJSONObject("systemInstruction").toString().contains("Asha"))
        // Live-only fields are left out of the REST declarations.
        assertFalse(first.getJSONArray("tools").toString().contains("behavior"))
    }

    @Test fun `the model's own turn goes back unchanged so thought signatures survive`() = runBlocking {
        server.enqueue(reply("""{"functionCall":{"name":"set_do_not_disturb","args":{}},"thoughtSignature":"sig-123"}"""))
        server.enqueue(reply("""{"text":"Done."}"""))
        agent().run("Silent", "now")
        server.takeRequest()
        val second = server.takeRequest().body.readUtf8()
        assertTrue(second.contains("sig-123"))
        assertTrue(second.contains("functionResponse"))
    }

    @Test fun `a locked phone offers the unlock escape and stops when the model uses it`() = runBlocking {
        server.enqueue(reply("""{"functionCall":{"name":"needs_unlocked_phone","args":{}}}"""))
        val outcome = agent(locked = true).run("Send Sonia hello on WhatsApp", "now")
        assertFalse(outcome.ok)
        assertTrue(outcome.needsUnlock)
        assertTrue(server.takeRequest().body.readUtf8().contains("needs_unlocked_phone"))
    }

    @Test fun `an unlocked phone has no unlock escape`() = runBlocking {
        server.enqueue(reply("""{"text":"Done."}"""))
        agent(locked = false).run("Silent", "now")
        assertFalse(server.takeRequest().body.readUtf8().contains("needs_unlocked_phone"))
    }

    @Test fun `a refusal marked as failed is reported as not done`() = runBlocking {
        server.enqueue(reply("""{"text":"FAILED: There is no contact named Sonia."}"""))
        val outcome = agent().run("Send Sonia hello", "now")
        assertFalse(outcome.ok)
        assertEquals("There is no contact named Sonia.", outcome.summary)
    }

    @Test fun `tools that were not offered are refused instead of run`() = runBlocking {
        server.enqueue(reply("""{"functionCall":{"name":"lock_screen","args":{}}}"""))
        server.enqueue(reply("""{"text":"Done."}"""))
        val ran = mutableListOf<String>()
        agent(ran = ran).run("Lock", "now")
        assertTrue(ran.isEmpty())
        server.takeRequest()
        assertTrue(server.takeRequest().body.readUtf8().contains("unknown_tool"))
    }
}
