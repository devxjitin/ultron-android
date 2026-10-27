package com.assistant.app.audio

/**
 * Decides which mic chunks reach the server while the assistant is speaking with barge-in on.
 *
 * The server's voice detection treats any loud sound as the user talking and cuts the reply off, so a
 * noisy room (or the assistant's own echo) made it interrupt itself. While gated, chunks are replaced
 * with silence until the sound has stayed clearly above the room's noise floor for a sustained moment.
 * Only then does the audio flow, starting with the last few chunks so the first word of a real
 * interruption is not clipped.
 *
 * Not thread safe: use it from the mic read loop only.
 */
class BargeInGate(
    private val ratio: Float = 3f,
    private val minRms: Float = 0.05f,
    private val openAfterChunks: Int = 8,
    private val closeAfterQuietChunks: Int = 25,
    private val floorSmoothing: Float = 0.02f
) {
    private var noiseFloor = 0.01f
    private var loudRun = 0
    private var quietRun = 0
    private var open = false
    private val preRoll = ArrayDeque<ByteArray>()

    /** Returns the chunks to forward for this input chunk: the chunk itself, silence, or buffered chunks to catch up. */
    fun process(chunk: ByteArray, rms: Float, gated: Boolean): List<ByteArray> {
        if (!gated) {
            // Learn the room's baseline while the assistant is quiet.
            noiseFloor += (rms - noiseFloor) * floorSmoothing
            reset()
            return listOf(chunk)
        }
        val loud = rms >= maxOf(minRms, noiseFloor * ratio)
        if (open) {
            quietRun = if (loud) 0 else quietRun + 1
            if (quietRun >= closeAfterQuietChunks) reset()
            return listOf(chunk)
        }
        preRoll.addLast(chunk)
        while (preRoll.size > openAfterChunks) preRoll.removeFirst()
        loudRun = if (loud) loudRun + 1 else 0
        if (loudRun >= openAfterChunks) {
            open = true
            quietRun = 0
            val buffered = preRoll.toList()
            preRoll.clear()
            return buffered
        }
        return listOf(ByteArray(chunk.size))
    }

    private fun reset() {
        loudRun = 0
        quietRun = 0
        open = false
        preRoll.clear()
    }
}
