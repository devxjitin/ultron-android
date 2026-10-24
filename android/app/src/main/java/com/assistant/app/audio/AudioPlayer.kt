package com.assistant.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import com.assistant.app.network.GeminiLiveClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import android.os.SystemClock
import com.assistant.app.util.LatencyTurn
import com.assistant.app.util.LatencySession
import com.assistant.app.util.playbackHasPassed
import com.assistant.app.util.playbackHasReached

/**
 * Plays back the mono 24kHz PCM16 audio chunks streamed from Gemini Live, in the
 * order they arrive. A small channel decouples network callbacks from the audio thread.
 */
class AudioPlayer(
    private val onPlaybackAmplitude: (Float) -> Unit = {},
    /** Fired once playback has actually finished (queue empty AND AudioTrack has caught up to
     *  everything written), not just when chunks stop arriving -- callers should treat this as
     *  the true "the assistant has stopped talking" signal, e.g. for unmuting the mic. */
    private val onPlaybackIdle: () -> Unit = {},
    /** A write failure or an undrained playback head is a failed reply, not normal idleness. */
    private val onPlaybackFailure: (String) -> Unit = {},
    private val latency: LatencySession? = null
) {
    private companion object {
        /** Cushion built up before (re-)starting playback of a burst. Starts low for the lowest
         *  possible latency and grows (see [PREBUFFER_STEP_MS]/[MAX_PREBUFFER_MS]) only if an
         *  actual AudioTrack underrun is observed, instead of always paying a fixed worst-case
         *  cushion on every single burst. */
        const val INITIAL_PREBUFFER_MS = 120
        const val PREBUFFER_STEP_MS = 80
        const val MAX_PREBUFFER_MS = 400
        /** No chunk for this long is treated as the burst ending (or a real gap) -- flush
         *  whatever's buffered immediately instead of holding the tail of a sentence forever. */
        const val IDLE_GAP_MS = 400L
        /** After an idle gap, only re-enter buffering mode if AudioTrack has actually
         *  (near-)drained. If there's still a real cushion of unplayed audio queued up, holding
         *  the next chunk for a fresh prebuffer just doubles every gap between slow-arriving
         *  chunks (gap + prebuffer-wait instead of just gap) -- write it straight through instead. */
        const val REBUFFER_UNPLAYED_THRESHOLD_MS = 60L
        /** Safety cap on how long to wait for AudioTrack to finish draining what's already been
         *  written before declaring playback idle anyway -- guards against the mic staying muted
         *  forever if playback-head tracking ever misbehaves on a given device/OEM. */
        const val DRAIN_TIMEOUT_MS = 3000L
        /** Absolute ceiling on a drain wait even if the playback head keeps creeping forward. */
        const val MAX_DRAIN_MS = 15000L
        const val BYTES_PER_FRAME = 2 // 16-bit mono PCM
    }

    private val sampleRate = GeminiLiveClient.OUTPUT_SAMPLE_RATE_HZ
    private val scope = CoroutineScope(Dispatchers.IO)
    // onUndeliveredElement re-queues a chunk that loses the receive()/timeout race in the player
    // loop below (withTimeoutOrNull cancelling receive() right as an element was being handed
    // over) -- without this that chunk is silently dropped instead of played.
    private data class QueuedAudio(val pcm: ByteArray, val trace: LatencyTurn? = null)
    private val probeGeneration = AtomicInteger()
    private val queue: Channel<QueuedAudio> = Channel(
        capacity = Channel.UNLIMITED,
        onUndeliveredElement = ::onUndeliveredChunk
    )
    private var job: Job? = null
    @Volatile private var track: AudioTrack? = null
    private val trackLock = Any()
    private val resetBufferingRequested = AtomicBoolean(false)
    /** Set by [notifyTurnComplete] -- the player loop only waits for a real drain-to-silence
     *  when the caller has told us the model's turn is actually over, not on every mid-sentence
     *  pause (the idle-gap check below fires on those too, for re-buffering purposes). */
    private val turnCompleteRequested = AtomicBoolean(false)
    private val playbackFailed = AtomicBoolean(false)
    @Volatile private var totalFramesWritten = 0L
    // [onPlaybackAmplitude]/[onPlaybackIdle] are consumed by the ViewModel, which mutates plain
    // (unsynchronized) fields on the assumption it's only ever called from the main thread. The
    // player loop below runs on an IO dispatcher, so callbacks fired from inside it are posted
    // through here instead of invoked inline. clearQueueForInterruption() also posts onPlaybackIdle() through it.
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun onUndeliveredChunk(chunk: QueuedAudio) {
        queue.trySend(chunk)
    }

    private fun failPlayback(reason: String) {
        if (!playbackFailed.compareAndSet(false, true)) return
        latency?.event("playback_failure", mapOf("reason" to reason))
        mainHandler.post { onPlaybackFailure(reason) }
    }

    /** Returns true only once playback has actually started. The caller must not treat this
     *  player as live (assign it, proceed to LISTENING, etc.) on a false return -- construction
     *  or `play()` failed and any partially-built native track has already been released. */
    fun start(): Boolean {
        if (job != null) return true

        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        // AudioTrack.Builder.build() can throw (e.g. UnsupportedOperationException for a
        // rejected config) and play() can throw IllegalStateException if the track didn't
        // actually initialize -- neither is caught by the framework, so catch both here instead
        // of letting them crash the app.
        val audioTrack = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBufferSize, sampleRate * 2)) // ~1s buffer (16-bit mono = 2 bytes/sample)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            return false
        }

        try {
            audioTrack.play()
        } catch (e: Exception) {
            audioTrack.release()
            return false
        }
        track = audioTrack

        var prebufferMs = INITIAL_PREBUFFER_MS
        var lastUnderrunCount = 0

        job = scope.launch {
            val pending = mutableListOf<QueuedAudio>()
            var pendingBytes = 0
            var buffering = true
            var nextChunk: QueuedAudio? = null
            // A completed model turn naturally drains AudioTrack to silence. That is not a
            // network starvation event and must never inflate the next reply's prebuffer.
            var receivingReplyAudio = false

            fun unplayedMs(): Long =
                (totalFramesWritten - audioTrack.playbackHeadPosition.toLong()) * 1000 / sampleRate

            fun writeChunk(packet: QueuedAudio): Boolean {
                val chunk = packet.pcm
                try {
                    synchronized(trackLock) {
                        var offset = 0
                        while (offset < chunk.size) {
                            val frameBeforeWrite = totalFramesWritten
                            val writeStartedAt = SystemClock.elapsedRealtime()
                            val written = audioTrack.write(chunk, offset, chunk.size - offset)
                            if (written <= 0) {
                                failPlayback("Audio output stopped accepting data.")
                                return false
                            }
                            if (offset == 0) packet.trace?.let { trace ->
                                trace.audioWritten(writeStartedAt, prebufferMs)
                                val generation = probeGeneration.get()
                                // Observe only the first packet of a turn. This does not gate playback.
                                scope.launch {
                                    val deadline = SystemClock.elapsedRealtime() + 5000L
                                    while (isActive && generation == probeGeneration.get() && !trace.finished) {
                                        val head = runCatching { audioTrack.playbackHeadPosition }.getOrNull()
                                        if (head == null) {
                                            trace.cancel("playback_unobserved")
                                            return@launch
                                        }
                                        if (playbackHasPassed(head, frameBeforeWrite)) {
                                            trace.playback(SystemClock.elapsedRealtime(),
                                                runCatching { audioTrack.underrunCount }.getOrDefault(0))
                                            return@launch
                                        }
                                        if (SystemClock.elapsedRealtime() >= deadline) {
                                            trace.cancel("playback_unobserved")
                                            return@launch
                                        }
                                        delay(5)
                                    }
                                }
                            }
                            offset += written
                            totalFramesWritten += written / BYTES_PER_FRAME
                        }
                    }
                } catch (_: Exception) {
                    failPlayback("Audio playback failed.")
                    return false
                }
                if (playbackFailed.get()) return false
                val amplitude = rmsAmplitude(chunk)
                mainHandler.post { onPlaybackAmplitude(amplitude) }
                return true
            }

            fun flushPending(): Boolean {
                for (packet in pending) {
                    if (!writeChunk(packet)) return false
                }
                pending.clear()
                pendingBytes = 0
                return true
            }

            /** If AudioTrack actually ran dry since the last check, grow the prebuffer cushion
             *  for future bursts -- this device/network combo needs more cushion than we started
             *  with. Checked once per burst restart rather than continuously.
             *
             *  [countAsRealUnderrun] is false right when a turn has just ended: AudioTrack
             *  naturally drains to empty at the end of every reply, which trips the same
             *  underrun counter as a genuine mid-reply starvation event but isn't one -- growing
             *  the cushion off that would keep inflating prebufferMs (and startup latency) on
             *  every single turn regardless of real network/model jitter. */
            fun adaptPrebufferIfUnderrun(countAsRealUnderrun: Boolean) {
                val underruns = audioTrack.underrunCount
                if (underruns > lastUnderrunCount) {
                    val delta = underruns - lastUnderrunCount
                    lastUnderrunCount = underruns
                    if (countAsRealUnderrun) {
                        prebufferMs = (prebufferMs + PREBUFFER_STEP_MS).coerceAtMost(MAX_PREBUFFER_MS)
                    }
                    latency?.event("audio_underruns", mapOf(
                        "underruns_delta" to delta, "underruns_total" to underruns,
                        "mid_reply" to countAsRealUnderrun, "prebuffer_ms" to prebufferMs
                    ))
                }
            }

            try {
                while (isActive && !playbackFailed.get()) {
                    if (resetBufferingRequested.getAndSet(false)) {
                        pending.clear()
                        pendingBytes = 0
                        buffering = true
                    }

                    val packet = nextChunk ?: withTimeoutOrNull(IDLE_GAP_MS) { queue.receive() }
                    nextChunk = null
                    if (packet == null) {
                        // Nothing arrived for a while -- don't hold the tail of a sentence
                        // forever, and flush whatever's still in the prebuffer stage.
                        if (!flushPending()) break
                        adaptPrebufferIfUnderrun(countAsRealUnderrun =
                            receivingReplyAudio && !turnCompleteRequested.get())
                        // Only re-buffer if AudioTrack has actually (near-)drained. If there's
                        // still a real cushion of unplayed audio queued up, the next chunk should
                        // go straight to AudioTrack instead of waiting out another prebuffer.
                        buffering = unplayedMs() <= REBUFFER_UNPLAYED_THRESHOLD_MS
                        if (turnCompleteRequested.getAndSet(false)) {
                            receivingReplyAudio = false
                            nextChunk = waitForDrainThenNotifyIdle(audioTrack)
                        }
                        continue
                    }

                    val chunk = packet.pcm

                    // A zero-length chunk wakes the player immediately when the server says
                    // the turn is complete. It sits after all earlier audio in the channel, so
                    // the final prebuffer can be flushed without paying another 400 ms gap.
                    if (chunk.isEmpty()) {
                        if (resetBufferingRequested.getAndSet(false)) {
                            pending.clear()
                            pendingBytes = 0
                            buffering = true
                            continue
                        }
                        if (!flushPending()) break
                        if (turnCompleteRequested.getAndSet(false)) {
                            receivingReplyAudio = false
                            nextChunk = waitForDrainThenNotifyIdle(audioTrack)
                        }
                        buffering = unplayedMs() <= REBUFFER_UNPLAYED_THRESHOLD_MS
                        continue
                    }

                    if (buffering) {
                        receivingReplyAudio = true
                        pending.add(packet)
                        pendingBytes += chunk.size
                        val prebufferBytes = (sampleRate * BYTES_PER_FRAME * prebufferMs) / 1000
                        if (pendingBytes >= prebufferBytes) {
                            buffering = false
                            if (!flushPending()) break
                        }
                    } else {
                        receivingReplyAudio = true
                        if (!writeChunk(packet)) break
                    }
                }
            } finally {
                // Release on this same thread, after the last write() has actually returned, so
                // a stop() call from another thread can never release the AudioTrack out from
                // under a write() that's still in flight.
                try {
                    audioTrack.stop()
                } catch (_: IllegalStateException) {
                    // already stopped
                }
                audioTrack.release()
                if (track === audioTrack) track = null
            }
        }
        return true
    }

    /** Waits (with a timeout safety cap) for AudioTrack to actually finish playing everything
     *  written so far, then fires [onPlaybackIdle]. Only called once the caller has confirmed
     *  via [notifyTurnComplete] that no more chunks are coming for this turn. */
    private suspend fun waitForDrainThenNotifyIdle(audioTrack: AudioTrack): QueuedAudio? {
        val targetFrames = totalFramesWritten
        // Allow for however much audio is genuinely still queued, and keep waiting while the
        // head is still advancing; only a head that stops moving (or the hard cap) ends the wait.
        val startedAt = System.currentTimeMillis()
        val hardDeadline = startedAt + MAX_DRAIN_MS
        var lastHead = audioTrack.playbackHeadPosition
        var lastProgressAt = startedAt
        while (!playbackHasReached(audioTrack.playbackHeadPosition, targetFrames)) {
            val now = System.currentTimeMillis()
            if (now >= hardDeadline || now - lastProgressAt >= DRAIN_TIMEOUT_MS) break
            if (resetBufferingRequested.get()) return null
            // Barge-in can produce the next turn before the old track drains. Process that audio
            // now instead of holding it behind a drain wait.
            queue.tryReceive().getOrNull()?.let { return it }
            val head = audioTrack.playbackHeadPosition
            if (head != lastHead) {
                lastHead = head
                lastProgressAt = now
            }
            delay(20)
        }
        if (resetBufferingRequested.get()) return null
        if (!playbackHasReached(audioTrack.playbackHeadPosition, targetFrames)) {
            // Not fatal: the audio was handed to the device, only the head counter stalled (audio
            // focus loss, route change, OEM quirk). Tearing down the live session over this is
            // what made Ultron Live stop working -- log it and carry on to idle instead.
            latency?.event("playback_drain_timeout", mapOf(
                "waited_ms" to (System.currentTimeMillis() - startedAt)))
        }
        queue.tryReceive().getOrNull()?.let { return it }
        mainHandler.post { onPlaybackIdle() }
        return null
    }

    /** Frames AudioTrack has actually played so far; used by the ViewModel to spot stalled playback. */
    fun playbackHeadFrames(): Long = runCatching { track?.playbackHeadPosition?.toLong() }.getOrNull() ?: 0L

    /** Tells the player the model's turn has ended -- once whatever's still queued/buffered has
     *  actually finished playing (not just been handed to AudioTrack), [onPlaybackIdle] fires. */
    fun notifyTurnComplete() {
        turnCompleteRequested.set(true)
        queue.trySend(QueuedAudio(ByteArray(0)))
    }

    /** Queues a chunk of PCM16 mono audio at 24kHz for playback. */
    fun enqueue(pcm16: ByteArray, trace: LatencyTurn? = null) {
        queue.trySend(QueuedAudio(pcm16, trace))
    }

    /** Drops any buffered audio immediately -- used when the user barges in mid-reply. */
    fun clearQueueForInterruption() {
        probeGeneration.incrementAndGet()
        while (queue.tryReceive().isSuccess) { /* drain */ }
        resetBufferingRequested.set(true)
        turnCompleteRequested.set(false)
        // pause/flush/play run under trackLock, which the player thread holds during a blocking
        // write() -- do them off the main thread so a barge-in can't freeze the UI, and only
        // report idle (unmute, back to LISTENING) once the track has really been flushed.
        scope.launch {
            track?.let {
                synchronized(trackLock) {
                    it.pause()
                    it.flush()
                    it.play()
                    // Discarded whatever was buffered -- resync the drain baseline to wherever
                    // playback actually is now, so a later notifyTurnComplete() doesn't wait on
                    // frames that were just thrown away.
                    totalFramesWritten = it.playbackHeadPosition.toLong()
                }
            }
            mainHandler.post { onPlaybackIdle() }
        }
    }

    fun stop() {
        probeGeneration.incrementAndGet()
        job?.cancel()
        job = null
        while (queue.tryReceive().isSuccess) { /* drain */ }
    }
}
