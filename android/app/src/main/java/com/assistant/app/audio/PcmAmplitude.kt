package com.assistant.app.audio

import kotlin.math.log10
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
fun rmsAmplitude(pcm16: ByteArray): Float = rmsAmplitude(pcm16, 0, pcm16.size)

/** RMS amplitude of the bytes in [from, to) of a PCM16 little-endian mono buffer, normalized to 0..1. */
fun rmsAmplitude(pcm16: ByteArray, from: Int, to: Int): Float {
    val end = minOf(to, pcm16.size)
    if (end - from < 2) return 0f
    var sum = 0.0
    var i = from
    while (i < end - 1) {
        val sample = ((pcm16[i + 1].toInt() shl 8) or (pcm16[i].toInt() and 0xFF)).toShort()
        sum += sample * sample
        i += 2
    }
    val rms = sqrt(sum / ((end - from) / 2))
    return (rms / Short.MAX_VALUE).toFloat().coerceIn(0f, 1f)
}

/** Quietest and loudest levels the wave distinguishes, in dB below full scale. Speech sits around -35 to -15 dB. */
private const val VISUAL_FLOOR_DB = -50f
private const val VISUAL_CEILING_DB = -12f

/**
 * Turns a raw RMS level into the 0..1 level the voice wave is drawn from. Raw RMS of speech is only about 0.02 to 0.15,
 * which barely moves a wave; ears and eyes follow loudness on a log scale, so this maps -50 dB..-12 dB linearly onto
 * 0..1. Anything under the floor (room noise) is 0. Turn-taking keeps using the raw level.
 */
fun visualLevel(rms: Float): Float {
    if (!rms.isFinite() || rms <= 0f) return 0f
    val db = 20f * log10(rms)
    return ((db - VISUAL_FLOOR_DB) / (VISUAL_CEILING_DB - VISUAL_FLOOR_DB)).coerceIn(0f, 1f)
}
