package com.assistant.app.audio

/**
 * Wave levels for audio that has been handed to the speaker but not yet heard. The speaker buffer holds up to about a
 * second, so a level taken when a chunk is written would move the wave long before the voice is audible, and a burst of
 * chunks would move it all at once. Each chunk is cut into [sliceFrames]-long slices with their loudness, and the level
 * is looked up by where playback actually is.
 */
class PlaybackLevels(private val sliceFrames: Int) {
    private class Slice(val startFrame: Long, val level: Float)

    private val slices = ArrayDeque<Slice>()
    private val lock = Any()

    /** Records [pcm16] (mono, 16-bit) as starting at absolute frame [startFrame] of the track. */
    fun add(startFrame: Long, pcm16: ByteArray) {
        val sliceBytes = sliceFrames * 2
        synchronized(lock) {
            var offset = 0
            var frame = startFrame
            while (offset < pcm16.size) {
                val end = minOf(offset + sliceBytes, pcm16.size)
                slices.addLast(Slice(frame, visualLevel(rmsAmplitude(pcm16, offset, end))))
                offset = end
                frame += sliceFrames
            }
        }
    }

    /** The level of what is playing at [headFrame], 0 once everything recorded has been played. Older slices are dropped. */
    fun levelAt(headFrame: Long): Float = synchronized(lock) {
        var level = 0f
        var current: Slice? = null
        while (slices.isNotEmpty() && slices.first().startFrame <= headFrame) current = slices.removeFirst()
        if (current != null) {
            // The slice stays "playing" until the next one starts; keep it in front so a repeat lookup still sees it.
            if (headFrame < current.startFrame + sliceFrames) {
                level = current.level
                slices.addFirst(current)
            }
        }
        level
    }

    fun clear() = synchronized(lock) { slices.clear() }
}
