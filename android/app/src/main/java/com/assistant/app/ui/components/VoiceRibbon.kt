package com.assistant.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.assistant.app.viewmodel.VoiceState
import kotlin.math.PI
import kotlin.math.sin

/** The ribbon's gradient end colours for a state; the mic button uses the same so they match. */
fun ribbonColors(voiceState: VoiceState): Pair<Color, Color> = when (voiceState) {
    VoiceState.LISTENING -> Color(0xFFC8102E) to Color(0xFFFF7A3D)
    VoiceState.SPEAKING -> Color(0xFF8E0E1C) to Color(0xFFD7263D)
    VoiceState.ERROR -> Color(0xFFE5484D) to Color(0xFFFF8A5B)
    else -> Color(0xFFD7263D) to Color(0xFFFF5A36)
}

/** A tapered, luminous ribbon, drawn locally and driven by real microphone/playback levels. */
@Composable
fun VoiceRibbon(voiceState: VoiceState, amplitude: Float, modifier: Modifier = Modifier) {
    val live = voiceState == VoiceState.LISTENING || voiceState == VoiceState.SPEAKING
    val (leftTarget, rightTarget) = ribbonColors(voiceState)
    val left by animateColorAsState(leftTarget, tween(900), label = "ribbonLeft")
    val right by animateColorAsState(rightTarget, tween(900), label = "ribbonRight")
    val motion = rememberInfiniteTransition(label = "ribbon")
    // Read animated values in the draw phase, avoiding recomposition of the whole screen.
    val phase = motion.animateFloat(0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(6500, easing = LinearEasing)), label = "ribbonFlow")
    val level = animateFloatAsState(
        if (live && amplitude.isFinite()) amplitude.coerceIn(0f, 1f) else 0f,
        tween(160), label = "ribbonLevel"
    )
    val path = remember { Path() }
    Canvas(modifier) {
        val samples = 64
        val layers = 10
        val p = phase.value
        val strength = level.value
        val gradient = Brush.horizontalGradient(listOf(Color.Transparent, left, right, Color.Transparent))
        fun y(t: Float): Float = size.height * (0.5f +
            sin(t * 5.2f - p) * (0.16f + strength * 0.16f) +
            sin(t * 9f + p) * 0.025f)
        // Nested translucent ribbons create a soft bloom without bitmap assets or blur effects.
        for (layer in layers downTo 1) {
            val spread = layer / layers.toFloat()
            path.reset()
            for (i in 0..samples) {
                val t = i / samples.toFloat()
                val taper = sin(t * PI).toFloat()
                val width = size.height * (0.035f + strength * 0.055f) * spread * taper *
                    (1.2f + sin(t * 7f + p) * 0.65f)
                val py = y(t) - width * 3.0f
                if (i == 0) path.moveTo(0f, py) else path.lineTo(size.width * t, py)
            }
            for (i in samples downTo 0) {
                val t = i / samples.toFloat()
                val width = size.height * (0.035f + strength * 0.055f) * spread * sin(t * PI).toFloat()
                path.lineTo(size.width * t, y(t) + width * 0.65f)
            }
            path.close()
            drawPath(path, gradient, alpha = 0.08f + (1f - spread) * 0.11f)
        }
        path.reset()
        for (i in 0..samples) {
            val t = i / samples.toFloat()
            if (i == 0) path.moveTo(0f, y(t)) else path.lineTo(size.width * t, y(t))
        }
        drawPath(path, gradient, alpha = 0.8f, style = Stroke(width = 1.2f * density))
    }
}
