package com.assistant.app.network

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** How the client behaves against servers that negotiate differently: plain JSON or event stream, sessions, protocol versions. */
class McpClientProtocolTest {
    private lateinit var server: MockWebServer

    @Before fun start() { server = MockWebServer().also { it.start() } }
    @After fun stop() { runCatching { server.shutdown() } }

    private fun client() = McpClient(server.url("/mcp").toString()) { emptyMap() }

    private fun json(result: JSONObject, id: Int = 1) =
        MockResponse().setHeader("Content-Type", "application/json").setBody(JSONObject().put("jsonrpc", "2.0").put("id", id).put("result", result).toString())

    private fun stream(result: JSONObject, id: Int) =
        MockResponse().setHeader("Content-Type", "text/event-stream")
            .setBody("data: " + JSONObject().put("jsonrpc", "2.0").put("id", id).put("result", result) + "\n\n")

    private fun initialize(version: String, id: Int = 1) =
        json(JSONObject().put("protocolVersion", version).put("capabilities", JSONObject()), id).setHeader("Mcp-Session-Id", "s1")

    private fun accepted() = MockResponse().setResponseCode(202)

    private fun textResult(text: String, isError: Boolean = false, kind: String? = null): JSONObject {
        val result = JSONObject().put("content", JSONArray().put(JSONObject().put("type", "text").put("text", text))).put("isError", isError)
        if (kind != null) result.put("structuredContent", JSONObject().put("error", JSONObject().put("kind", kind).put("status", 401)))
        return result
    }

    @Test fun `an older server version is accepted and its header is sent afterwards`() = runBlocking {
        server.enqueue(initialize("2024-11-05"))
        server.enqueue(accepted())
        server.enqueue(json(textResult("hi"), 2))
        val result = client().callTool("t", JSONObject())
        assertEquals("hi", result.getOrThrow())
        server.takeRequest() // initialize: nothing to echo yet
        server.takeRequest() // initialized notification
        val call = server.takeRequest()
        assertEquals("2024-11-05", call.getHeader("MCP-Protocol-Version"))
        assertEquals("s1", call.getHeader("Mcp-Session-Id"))
    }

    @Test fun `the first request carries no protocol header`() = runBlocking {
        server.enqueue(initialize("2025-06-18"))
        server.enqueue(accepted())
        server.enqueue(json(textResult("ok"), 2))
        client().callTool("t", JSONObject())
        assertNull(server.takeRequest().getHeader("MCP-Protocol-Version"))
    }

    @Test fun `an event stream answer works the same as a plain one`() = runBlocking {
        server.enqueue(initialize("2025-03-26"))
        server.enqueue(accepted())
        server.enqueue(stream(textResult("streamed"), 2))
        assertEquals("streamed", client().callTool("t", JSONObject()).getOrThrow())
    }

    @Test fun `a forgotten session is opened again and the call repeated`() = runBlocking {
        server.enqueue(initialize("2025-03-26"))
        server.enqueue(accepted())
        server.enqueue(MockResponse().setResponseCode(404).setBody("no session"))
        server.enqueue(initialize("2025-03-26", 3))
        server.enqueue(accepted())
        server.enqueue(json(textResult("after"), 2))
        assertEquals("after", client().callTool("t", JSONObject()).getOrThrow())
    }

    @Test fun `a failed tool keeps the kind the server gave it`() = runBlocking {
        server.enqueue(initialize("2025-03-26"))
        server.enqueue(accepted())
        server.enqueue(json(textResult("Google API error 401: expired", isError = true, kind = McpException.KIND_AUTH_REJECTED), 2))
        val failure = client().callTool("t", JSONObject()).exceptionOrNull() as McpException
        assertEquals(McpException.KIND_AUTH_REJECTED, failure.kind)
        assertTrue(failure.message!!.contains("expired"))
    }

    @Test fun `a failed tool without a kind still fails with its text`() = runBlocking {
        server.enqueue(initialize("2025-03-26"))
        server.enqueue(accepted())
        server.enqueue(json(textResult("boom", isError = true), 2))
        val failure = client().callTool("t", JSONObject()).exceptionOrNull() as McpException
        assertNull(failure.kind)
        assertEquals("boom", failure.message)
    }
}
