package com.assistant.app.viewmodel

import android.os.SystemClock
import android.util.Log
import com.assistant.app.BuildConfig
import com.assistant.app.network.GeminiLiveClient
import com.assistant.app.network.LiveClient
import com.assistant.app.util.LatencyTool
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Starts the tools the voice model asks for and sends their results back. The calls of one `toolCall` message are
 * answered together in one `toolResponse`; a duplicate delivery of a call, or a call the server cancels, is never
 * answered. What each tool does is [execute]'s job.
 */
internal class ToolDispatcher(
    /** For the short-lived label on the home screen; outlives a single tool scope. */
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<AssistantUiState>,
    /** False once the session this client belongs to has been replaced or ended. */
    private val isCurrent: (LiveClient) -> Boolean,
    /** Starts the reply watchdog, once the last result of a message has been sent. */
    private val startTurnTimeout: () -> Unit,
    private val displayName: (String) -> String,
    private val execute: suspend (GeminiLiveClient.FunctionCall, LatencyTool?) -> String,
    private val newToolScope: () -> CoroutineScope = {
        CoroutineScope(
            SupervisorJob() + Dispatchers.Main +
                CoroutineExceptionHandler { _, e -> Log.e("AssistantError", "Uncaught tool error", e) }
        )
    }
) {
    companion object {
        /** A tool name never stays on screen longer than this, even if its session dies mid-tool. */
        const val LABEL_MAX_MS = 25_000L
    }

    /** The calls of one `toolCall` message. Their results go back to the model in one `toolResponse`. */
    private class Batch(val ids: List<String>) {
        val results = linkedMapOf<String, GeminiLiveClient.ToolResult>()
        val traces = mutableMapOf<String, Pair<LatencyTool?, Long>>()
    }

    @Volatile private var toolScope: CoroutineScope? = null
    /** Tool calls split one visitor interaction into a tool stage and a later response stage.
     *  Keep the buffers and reply watchdog attached to that one interaction until it genuinely
     *  finishes, rather than treating the protocol's tool boundary as a completed conversation. */
    private val pendingIds = mutableSetOf<String>()
    private val jobs = mutableMapOf<String, Job>()
    /** Finished results waiting for the rest of their `toolCall` message, by call id; see [flush]. */
    private val batches = mutableListOf<Batch>()

    /** True while a tool is running or its result has not been sent yet. */
    val hasPending: Boolean get() = pendingIds.isNotEmpty()

    /** A session started streaming: tools run in a fresh scope, and the previous one is cancelled. */
    fun openScope() {
        toolScope?.cancel()
        toolScope = newToolScope()
    }

    /** The session ended: stops running tools and forgets everything still waiting. */
    fun shutdown() {
        toolScope?.cancel()
        toolScope = null
        cancelAll()
    }

    /** Starts the calls of one `toolCall` message. */
    fun onToolCalls(client: LiveClient, calls: List<GeminiLiveClient.FunctionCall>) {
        // Duplicate delivery of a function-call frame must not execute retrieval
        // twice or produce two responses for the same protocol ID.
        val fresh = calls.filter { pendingIds.add(it.id) }
        if (fresh.isEmpty()) return
        val batch = Batch(fresh.map { it.id }).also { batches.add(it) }
        fresh.forEach { call ->
            val trace = client.latency.turn()?.tool()
            announce(displayName(call.name))
            val job = toolScope?.launch {
                val startedAt = SystemClock.elapsedRealtime()
                val resultText = execute(call, trace)
                if (BuildConfig.DEBUG) {
                    Log.d("AssistantLatency", "knowledge-search=${SystemClock.elapsedRealtime() - startedAt}ms")
                }
                // A cancellation, new session, or interruption may happen while an HTTP
                // embedding request is in flight. Never revive an abandoned turn by
                // sending its result after that point.
                if (isCurrent(client) && pendingIds.contains(call.id)) {
                    batch.results[call.id] = GeminiLiveClient.ToolResult(call.id, call.name, resultText)
                    batch.traces[call.id] = trace to startedAt
                    jobs.remove(call.id)
                    clearLabelLater(displayName(call.name))
                    flush(client, batch)
                }
            }
            if (job == null) {
                pendingIds.remove(call.id)
                trace?.failure()
                clearLabelLater(displayName(call.name))
                flush(client, batch)
            } else {
                jobs[call.id] = job
            }
        }
    }

    /** The server abandoned these calls: they are not run or answered any more. */
    fun onCancellation(client: LiveClient, ids: List<String>) {
        ids.forEach { id ->
            pendingIds.remove(id)
            jobs.remove(id)?.cancel()
        }
        // The calls left in a batch may all be done now that the cancelled ones no longer count.
        batches.toList().forEach { flush(client, it) }
        if (pendingIds.isEmpty()) {
            state.update { it.copy(activeTool = null) }
        }
    }

    private fun announce(label: String) {
        state.update { it.copy(activeTool = label) }
        // Safety net so a session that dies mid-tool never leaves the name on screen.
        clearLabelLater(label, LABEL_MAX_MS)
    }

    /** Keeps the tool name visible a moment after it finishes, then removes it if it is still the same one. */
    private fun clearLabelLater(label: String, afterMs: Long = 0L) {
        scope.launch {
            delay(afterMs)
            state.update { if (it.activeTool == label && pendingIds.isEmpty()) it.copy(activeTool = null) else it }
        }
    }

    /** Sends a batch's results once every call in it that is still wanted has one. Gemini waits for every response in a
     *  toolCall message, so the reply watchdog starts only after the last of them is actually sent. */
    private fun flush(client: LiveClient, batch: Batch) {
        val wanted = batch.ids.filter { it in pendingIds }
        if (wanted.any { it !in batch.results }) return
        batches.remove(batch)
        val results = wanted.mapNotNull { batch.results[it] }
        if (results.isEmpty()) return
        val accepted = isCurrent(client) && client.sendToolResponses(results)
        val now = SystemClock.elapsedRealtime()
        results.forEach { result ->
            val (trace, startedAt) = batch.traces[result.id] ?: (null to now)
            trace?.event("tool_response_sent", mapOf(
                "accepted" to accepted, "context_chars" to result.resultText.length, "duration_ms" to (now - startedAt)
            ))
            if (!accepted) trace?.failure()
            pendingIds.remove(result.id)
        }
        if (accepted && pendingIds.isEmpty()) startTurnTimeout()
        // The ids only leave pendingIds here, after clearLabelLater already looked, so the label is removed now.
        if (pendingIds.isEmpty()) state.update { it.copy(activeTool = null) }
    }

    private fun cancelAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        pendingIds.clear()
        batches.clear()
        state.update { it.copy(activeTool = null) }
    }
}
