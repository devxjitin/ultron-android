package com.assistant.app.viewmodel

import com.assistant.app.network.GeminiLiveClient
import com.assistant.app.network.LiveClient
import com.assistant.app.data.KnowledgeBaseProvider
import com.assistant.app.util.LatencySession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolDispatcherTest {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Unconfined)
    private val state = MutableStateFlow(AssistantUiState())
    private val client = FakeClient()
    private var currentClient: LiveClient? = client
    private var timeoutStarts = 0
    private val gates = mutableMapOf<String, CompletableDeferred<String>>()
    private val executed = mutableListOf<String>()

    private class FakeClient : LiveClient {
        override val latency = LatencySession("t", { 0L }, { }, enabled = false)
        val sent = mutableListOf<List<GeminiLiveClient.ToolResult>>()
        var accept = true
        override fun connect(
            systemInstruction: String,
            settings: com.assistant.app.data.AssistantSettings,
            history: List<KnowledgeBaseProvider.HistoryTurn>,
            listener: GeminiLiveClient.Listener,
            resumeHandle: String?
        ) = Unit
        override fun sendAudioChunk(pcm16: ByteArray) = Unit
        override fun sendToolResponses(results: List<GeminiLiveClient.ToolResult>): Boolean { sent.add(results); return accept }
        override fun sendText(text: String) = true
        override fun close() = Unit
    }

    private fun dispatcher() = ToolDispatcher(
        scope = scope,
        state = state,
        isCurrent = { it === currentClient },
        startTurnTimeout = { timeoutStarts++ },
        displayName = { "Tool $it" },
        execute = { call, _ ->
            executed += call.id
            gates.getOrPut(call.id) { CompletableDeferred() }.await()
        },
        newToolScope = { CoroutineScope(SupervisorJob() + Dispatchers.Unconfined) }
    ).also { it.openScope() }

    @After fun tearDown() = job.cancel()

    private fun call(id: String, name: String = "set_alarm") = GeminiLiveClient.FunctionCall(id, name, JSONObject())
    private fun finish(id: String, text: String = "done $id") { gates.getOrPut(id) { CompletableDeferred() }.complete(text) }

    @Test fun `results of one message go back together in one response`() {
        val d = dispatcher()
        d.onToolCalls(client, listOf(call("a"), call("b")))
        assertTrue(d.hasPending)
        finish("a")
        assertEquals(0, client.sent.size)
        finish("b")
        assertEquals(1, client.sent.size)
        assertEquals(listOf("a", "b"), client.sent[0].map { it.id })
        assertFalse(d.hasPending)
        assertEquals(1, timeoutStarts)
    }

    @Test fun `a call delivered twice runs once`() {
        val d = dispatcher()
        d.onToolCalls(client, listOf(call("a")))
        d.onToolCalls(client, listOf(call("a")))
        assertEquals(listOf("a"), executed)
        finish("a")
        assertEquals(1, client.sent.size)
    }

    @Test fun `a cancelled call is not answered but the others still are`() {
        val d = dispatcher()
        d.onToolCalls(client, listOf(call("a"), call("b")))
        d.onCancellation(client, listOf("a"))
        finish("a")
        finish("b")
        assertEquals(1, client.sent.size)
        assertEquals(listOf("b"), client.sent[0].map { it.id })
    }

    @Test fun `cancelling every call sends nothing and clears the label`() {
        val d = dispatcher()
        d.onToolCalls(client, listOf(call("a")))
        assertEquals("Tool set_alarm", state.value.activeTool)
        d.onCancellation(client, listOf("a"))
        finish("a")
        assertEquals(0, client.sent.size)
        assertNull(state.value.activeTool)
        assertFalse(d.hasPending)
        assertEquals(0, timeoutStarts)
    }

    @Test fun `a result from a replaced session is dropped`() {
        val d = dispatcher()
        d.onToolCalls(client, listOf(call("a")))
        currentClient = null
        finish("a")
        assertEquals(0, client.sent.size)
    }

    @Test fun `a rejected response does not start the reply watchdog`() {
        client.accept = false
        val d = dispatcher()
        d.onToolCalls(client, listOf(call("a")))
        finish("a")
        assertEquals(1, client.sent.size)
        assertEquals(0, timeoutStarts)
        assertFalse(d.hasPending)
    }

    @Test fun `the label shows while a tool runs and goes away after the response`() {
        val d = dispatcher()
        d.onToolCalls(client, listOf(call("a")))
        assertEquals("Tool set_alarm", state.value.activeTool)
        finish("a")
        assertNull(state.value.activeTool)
    }

    @Test fun `shutdown forgets everything that is waiting`() {
        val d = dispatcher()
        d.onToolCalls(client, listOf(call("a")))
        d.shutdown()
        assertFalse(d.hasPending)
        assertNull(state.value.activeTool)
        finish("a")
        assertEquals(0, client.sent.size)
    }

    @Test fun `without an open scope the call fails at once and the response still goes out`() {
        val d = ToolDispatcher(scope, state, { it === currentClient }, { timeoutStarts++ }, { "x" }, { _, _ -> "never" })
        d.onToolCalls(client, listOf(call("a")))
        assertFalse(d.hasPending)
        assertEquals(0, client.sent.size)
    }
}
