package com.assistant.app.audio

import kotlin.math.sqrt
import kotlin.math.roundToInt

/** Applies software gain to PCM16 samples, saturating at the signed 16-bit limits. */
fun applyPcmGain(pcm16: ByteArray, gain: Float) {
    if (gain == 1f) return
    var i = 0
    while (i + 1 < pcm16.size) {
        val sample = ((pcm16[i + 1].toInt() shl 8) or (pcm16[i].toInt() and 0xFF)).toShort().toInt()
        val amplified = (sample * gain).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        pcm16[i] = (amplified and 0xFF).toByte()
        pcm16[i + 1] = (amplified shr 8).toByte()
        i += 2
    }
}

/** RMS amplitude of a PCM16 little-endian mono buffer, normalized to 0..1. */
fun rmsAmplitude(pcm16: ByteArray): Float {
    if (pcm16.size < 2) return 0f
    var sum = 0.0
    var i = 0
    while (i < pcm16.size - 1) {
        val sample = ((pcm16[i + 1].toInt() shl 8) or (pcm16[i].toInt() and 0xFF)).toShort()
        sum += sample * sample
        i += 2
    }
    val rms = sqrt(sum / (pcm16.size / 2))
    return (rms / Short.MAX_VALUE).toFloat().coerceIn(0f, 1f)
}
