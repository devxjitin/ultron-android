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
    VoiceState.LISTENING -> Color(0xFFC4232C) to Color(0xFFFF5A4D)
    VoiceState.SPEAKING -> Color(0xFF5E7A99) to Color(0xFFA9B8C8)
    VoiceState.ERROR -> Color(0xFFD9650F) to Color(0xFFFFB347)
    else -> Color(0xFF7A1218) to Color(0xFFC4232C)
}

/** A tapered, luminous ribbon, drawn locally and driven by real microphone/playback levels. */
@Composable
fun VoiceRibbon(voiceState: VoiceState, amplitude: () -> Float, modifier: Modifier = Modifier) {
    val live = voiceState == VoiceState.LISTENING || voiceState == VoiceState.SPEAKING
    val (leftTarget, rightTarget) = ribbonColors(voiceState)
    val left by animateColorAsState(leftTarget, tween(900), label = "ribbonLeft")
    val right by animateColorAsState(rightTarget, tween(900), label = "ribbonRight")
    // The flow only runs during a session; an idle or errored screen holds still instead of redrawing every frame.
    val flowing = voiceState != VoiceState.IDLE && voiceState != VoiceState.ERROR
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(flowing) {
        if (!flowing) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            phase.floatValue = (phase.floatValue + (now - last) / 1e9f * (2 * PI).toFloat() / 6.5f) % (2 * PI).toFloat()
            last = now
        }
    }
    val level = animateFloatAsState(
        // Read here so only the ribbon recomposes on each level update, not the whole screen.
        if (live) amplitude().let { if (it.isFinite()) it.coerceIn(0f, 1f) else 0f } else 0f,
        tween(160), label = "ribbonLevel"
    )
    val path = remember { Path() }
    Canvas(modifier) {
        val samples = 64
        val layers = 10
        val p = phase.floatValue
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
