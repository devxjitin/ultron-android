package com.assistant.app.util

/** Thread-safe, content-free timing records. Clock and sink are injectable for JVM tests. */
class LatencySession(
    val id: String,
    private val clock: () -> Long,
    private val sink: (Map<String, Any>) -> Unit,
    private val enabled: Boolean = true
) {
    val createdAtMs = clock()
    private var sequence = 0
    private var current: LatencyTurn? = null
    private var closed = false
    private val pending = mutableListOf<LatencyTurn>()

    @Synchronized
    fun turn(): LatencyTurn? {
        if (!enabled || closed) return null
        pending.removeAll { it.finished }
        return current ?: LatencyTurn(this, ++sequence).also {
            current = it
            pending.add(it)
            it.event("turn_start")
        }
    }

    @Synchronized
    fun complete() {
        current?.complete()
        current = null
    }

    @Synchronized
    fun playbackIdle() { pending.forEach { it.drained() } }

    @Synchronized
    fun cancel(reason: String) {
        pending.forEach { it.cancel(reason) }
        pending.clear()
        current = null
    }

    @Synchronized
    fun close(reason: String) {
        if (closed) return
        cancel(reason)
        event("session_end", mapOf("outcome" to reason))
        closed = true
    }

    fun now(): Long = clock()

    fun event(name: String, fields: Map<String, Any> = emptyMap(), at: Long = now()) {
        if (!enabled) return
        // Telemetry must never break the audio/network path if its sink fails.
        runCatching { sink(fields + mapOf("session" to id, "event" to name, "at_ms" to at)) }
    }
}

class LatencyTurn internal constructor(private val session: LatencySession, val id: Int) {
    private var toolSequence = 0
    private var speechEnd: Long? = null
    private var speechSource = "unknown"
    private var firstAudio: Long? = null
    private var firstWrite: Long? = null
    private var firstPlayback: Long? = null
    private var serverComplete = false
    private var playbackDrained = false
    private var failed = false
    @Volatile var finished = false
        private set

    fun now(): Long = session.now()

    @Synchronized
    fun event(name: String, fields: Map<String, Any> = emptyMap(), at: Long = now()) {
        if (!finished) session.event(name, fields + ("turn" to id), at)
    }

    @Synchronized
    fun speech(at: Long, source: String) {
        if (finished || firstAudio != null || serverComplete) return
        // RMS is preferred; transcript arrival is only a fallback, not true acoustic speech end.
        if (source == "rms" || speechSource != "rms") {
            speechEnd = at
            speechSource = source
        }
    }

    @Synchronized
    fun tool(): LatencyTool = LatencyTool(this, ++toolSequence).also { it.event("tool_call") }

    @Synchronized
    fun audioReceived(at: Long): Boolean {
        if (finished || firstAudio != null) return false
        firstAudio = at
        speechEnd?.let { end -> event("speech_end_estimate", mapOf("source" to speechSource), end) }
        event("first_audio_received", at = at)
        return true
    }

    @Synchronized
    fun audioWritten(at: Long, prebufferMs: Int) {
        if (finished || firstWrite != null) return
        firstWrite = at
        event("first_audio_write", mapOf("prebuffer_ms" to prebufferMs), at)
    }

    @Synchronized
    fun playback(at: Long, underruns: Int) {
        if (finished || firstPlayback != null) return
        firstPlayback = at
        event("first_playback_progress", mapOf("underruns" to underruns), at)
        finishIfReady()
    }

    @Synchronized
    fun failure() { failed = true }

    @Synchronized
    fun complete() {
        if (finished) return
        serverComplete = true
        event("server_turn_complete")
        finishIfReady()
    }

    @Synchronized
    fun drained() {
        playbackDrained = true
        finishIfReady()
    }

    private fun finishIfReady() {
        if (!serverComplete || finished) return
        if (firstAudio == null) finish("no_audio")
        else if (firstPlayback != null && playbackDrained) finish(if (failed) "failed" else "completed")
    }

    @Synchronized
    fun cancel(reason: String) { if (!finished) finish(reason) }

    private fun finish(outcome: String) {
        val fields = mutableMapOf<String, Any>(
            "outcome" to outcome, "knowledge" to (toolSequence > 0),
            "tool_count" to toolSequence, "speech_source" to speechSource
        )
        speechEnd?.takeIf { end -> firstAudio == null || firstAudio!! >= end }?.let { end ->
            fields["speech_end_estimate_ms"] = end
            firstAudio?.takeIf { it >= end }?.let { fields["speech_to_audio_ms"] = it - end }
            firstPlayback?.takeIf { it >= end }?.let { fields["speech_to_playback_ms"] = it - end }
        }
        firstAudio?.let { audio ->
            firstWrite?.takeIf { it >= audio }?.let { fields["audio_to_write_ms"] = it - audio }
            firstPlayback?.takeIf { it >= audio }?.let { fields["audio_to_playback_ms"] = it - audio }
        }
        event("turn_summary", fields)
        finished = true
    }
}

/** AudioTrack exposes a wrapping unsigned 32-bit playback frame counter. */
fun playbackHasPassed(head: Int, frameBeforeWrite: Long): Boolean {
    val advanced = (head.toLong() - frameBeforeWrite) and 0xffffffffL
    return advanced in 1 until 0x80000000L
}

/** Same wrapping comparison as [playbackHasPassed], but equality counts for a drain target. */
fun playbackHasReached(head: Int, targetFrame: Long): Boolean {
    val advanced = (head.toLong() - targetFrame) and 0xffffffffL
    return advanced < 0x80000000L
}

class LatencyTool internal constructor(private val turn: LatencyTurn, val id: Int) {
    fun now(): Long = turn.now()
    fun event(name: String, fields: Map<String, Any> = emptyMap(), at: Long = now()) =
        turn.event(name, fields + ("tool" to id), at)
    fun failure() = turn.failure()
}
