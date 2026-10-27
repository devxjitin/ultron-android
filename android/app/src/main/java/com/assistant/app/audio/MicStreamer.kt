package com.assistant.app.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import com.assistant.app.network.GeminiLiveClient
import com.assistant.app.data.MicSensitivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Captures mono 16kHz PCM16 audio from the mic and hands fixed-size chunks to
 * [onChunk] (normally [GeminiLiveClient.sendAudioChunk]) until [stop] is called.
 *
 * Keeps reading from the mic continuously even while [muted], since stopping/restarting
 * AudioRecord has real latency -- muting just skips forwarding chunks, so unmuting is instant.
 */
class MicStreamer(
    private val onChunk: (ByteArray) -> Unit,
    private val onAmplitude: (Float) -> Unit = {},
    private val onError: (String) -> Unit = {},
    sensitivity: MicSensitivity = MicSensitivity.MEDIUM
) {
    private var audioRecord: AudioRecord? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val muted = AtomicBoolean(false)
    private val userMuted = AtomicBoolean(false)
    @Volatile private var gain = sensitivity.gain

    fun setSensitivity(sensitivity: MicSensitivity) {
        gain = sensitivity.gain
    }
    // [onAmplitude] and [onError] are consumed by the ViewModel, which mutates plain
    // (unsynchronized) fields on the assumption it's only ever called from the main thread. The
    // read loop below runs on an IO dispatcher, so those two callbacks are posted through here
    // instead of invoked inline -- [onChunk] stays on IO since it's just a network send that
    // touches no ViewModel state, and posting it too would add needless latency to every chunk.
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Closed by [stop]; events already queued on the main thread then run as no-ops instead of
     *  reaching a session that has since replaced this one. */
    private val callbackGate = CallbackGate()

    /** Mute while the assistant is speaking so the phone's own voice output isn't picked back up as
     *  user input -- without this, the mic hears the speaker and the model responds to itself
     *  in a loop. This also has the side effect of disabling barge-in, which is intended. */
    fun setMuted(value: Boolean) {
        muted.set(value)
    }

    /** User-controlled mute, kept separate from [setMuted] because that one is flipped back to
     *  false whenever assistant playback ends and must not undo the user's choice. */
    fun setUserMuted(value: Boolean) {
        userMuted.set(value)
    }

    /** Returns true only once the mic is actually capturing. The caller must not treat streaming
     *  as started (assign this streamer, move the UI to LISTENING, etc.) on a false return --
     *  [onError] has already been invoked with a user-facing message in that case. */
    @SuppressLint("MissingPermission") // caller is required to have checked RECORD_AUDIO first
    fun start(): Boolean {
        if (job?.isActive == true) return true

        val sampleRate = GeminiLiveClient.INPUT_SAMPLE_RATE_HZ
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBufferSize, sampleRate / 2) // ~500ms safety margin

        // AudioRecord's constructor is documented to leave the object in STATE_UNINITIALIZED on
        // failure, but some OEM drivers throw instead (IllegalArgumentException for a rejected
        // config, or other RuntimeExceptions) -- catch that too instead of letting it escape.
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION, // enables platform echo cancellation
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        } catch (e: Exception) {
            onError("Couldn't access the microphone. Try again in a moment.")
            return false
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            onError("Couldn't access the microphone. Try again in a moment.")
            return false
        }
        try {
            record.startRecording()
        } catch (e: Exception) {
            record.release()
            onError("Couldn't access the microphone. Try again in a moment.")
            return false
        }
        audioRecord = record

        // 40 ms chunks follow the Live API's low-latency streaming guidance.
        val chunkSize = sampleRate * 40 / 1000 * 2
        job = scope.launch {
            try {
                val buffer = ByteArray(chunkSize)
                while (isActive) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        if (!muted.get() && !userMuted.get()) {
                            val chunk = buffer.copyOf(read)
                            applyPcmGain(chunk, gain)
                            val amplitude = rmsAmplitude(chunk)
                            mainHandler.post { callbackGate.run { onAmplitude(amplitude) } }
                            // Keep silence and quiet/short words flowing to server VAD. A UI
                            // amplitude callback must never gate the next word's first chunk.
                            onChunk(chunk)
                        }
                    } else if (read < 0) {
                        // Negative return is a device-level error code (e.g. ERROR_DEAD_OBJECT
                        // if another app/call took the mic) -- without this check the loop would
                        // spin forever on a broken AudioRecord instead of surfacing the failure.
                        mainHandler.post {
                            callbackGate.run { onError("Lost access to the microphone. Please try again.") }
                        }
                        break
                    }
                }
            } finally {
                // Release on this same thread, after read() has actually returned, so a stop()
                // call from another thread can never release the AudioRecord out from under a
                // read() that's still in flight.
                try {
                    record.stop()
                } catch (_: IllegalStateException) {
                    // already stopped
                }
                record.release()
                if (audioRecord === record) audioRecord = null
            }
        }
        return true
    }

    fun stop() {
        callbackGate.close()
        job?.cancel()
        job = null
    }
}
