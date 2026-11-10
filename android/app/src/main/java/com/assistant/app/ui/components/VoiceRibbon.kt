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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.assistant.app.ui.theme.ActivePalette
import com.assistant.app.viewmodel.VoiceState
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** The ribbon's gradient end colours for a state; the mic button uses the same so they match. */
fun ribbonColors(voiceState: VoiceState): Pair<Color, Color> =
    if (ActivePalette.current.dark) when (voiceState) {
        VoiceState.LISTENING -> Color(0xFFC4232C) to Color(0xFFFF5A4D)
        VoiceState.SPEAKING -> Color(0xFF5E7A99) to Color(0xFFA9B8C8)
        VoiceState.ERROR -> Color(0xFFD9650F) to Color(0xFFFFB347)
        else -> Color(0xFF7A1218) to Color(0xFFC4232C)
    } else when (voiceState) {
        // Deeper pairs for the pale page, where the dark-theme tints almost vanish.
        VoiceState.LISTENING -> Color(0xFF9B1B24) to Color(0xFFD63A3A)
        VoiceState.SPEAKING -> Color(0xFF2F4A6B) to Color(0xFF5C7FA6)
        VoiceState.ERROR -> Color(0xFFB04F00) to Color(0xFFE08A1E)
        else -> Color(0xFF6E1219) to Color(0xFFB3202A)
    }

/** How quickly the wave grows toward a louder level and settles back to a quieter one. */
private const val RISE_SECONDS = 0.04f
private const val FALL_SECONDS = 0.25f

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
    // The wave's own loudness, followed frame by frame: quick to rise with a word, slower to fall so it breathes.
    val level = remember { mutableFloatStateOf(0f) }
    val liveNow by rememberUpdatedState(live)
    val amplitudeNow by rememberUpdatedState(amplitude)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(flowing, lifecycle) {
        if (!flowing) {
            level.floatValue = 0f
            return@LaunchedEffect
        }
        // A session can be started from the lock screen and keep running while the app is stopped. A frame
        // request left waiting from then can stay stuck after unlock and freeze the ribbon, so the loop is
        // cancelled whenever the app leaves the screen and begins again with a fresh frame request on return.
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val dt = ((now - last) / 1e9f).coerceIn(0f, 0.1f)
                // Read here so only the ribbon redraws on each frame, not the whole screen.
                val target = if (liveNow) amplitudeNow().let { if (it.isFinite()) it.coerceIn(0f, 1f) else 0f } else 0f
                val tau = if (target > level.floatValue) RISE_SECONDS else FALL_SECONDS
                level.floatValue += (target - level.floatValue) * (1f - exp(-dt / tau))
                // The flow speeds up with the voice, so a loud moment visibly surges.
                phase.floatValue = (phase.floatValue + dt * (2 * PI).toFloat() / 6.5f * (1f + level.floatValue * 1.5f)) % (2 * PI).toFloat()
                last = now
            }
        }
    }
    val path = remember { Path() }
    val darkTheme = ActivePalette.current.dark
    Canvas(modifier) {
        val samples = 64
        val layers = 10
        val p = phase.floatValue
        val strength = level.floatValue
        // The pale page needs a stronger wash than the near-black one.
        val layerBoost = if (darkTheme) 1f else 1.7f
        val gradient = Brush.horizontalGradient(listOf(Color.Transparent, left, right, Color.Transparent))
        // Quiet, the wave only drifts; the voice supplies most of the height, so speech is clearly visible.
        fun y(t: Float): Float = size.height * (0.5f +
            sin(t * 5.2f - p) * (0.05f + strength * 0.20f) +
            sin(t * 9f + p) * (0.015f + strength * 0.03f))
        // Nested translucent ribbons create a soft bloom without bitmap assets or blur effects.
        for (layer in layers downTo 1) {
            val spread = layer / layers.toFloat()
            path.reset()
            for (i in 0..samples) {
                val t = i / samples.toFloat()
                val taper = sin(t * PI).toFloat()
                val width = size.height * (0.025f + strength * 0.03f) * spread * taper *
                    (1.2f + sin(t * 7f + p) * 0.65f)
                val py = y(t) - width * 2.0f
                if (i == 0) path.moveTo(0f, py) else path.lineTo(size.width * t, py)
            }
            for (i in samples downTo 0) {
                val t = i / samples.toFloat()
                val width = size.height * (0.025f + strength * 0.03f) * spread * sin(t * PI).toFloat()
                path.lineTo(size.width * t, y(t) + width * 0.65f)
            }
            path.close()
            drawPath(path, gradient, alpha = ((0.08f + (1f - spread) * 0.11f) * layerBoost).coerceAtMost(1f))
        }
        path.reset()
        for (i in 0..samples) {
            val t = i / samples.toFloat()
            if (i == 0) path.moveTo(0f, y(t)) else path.lineTo(size.width * t, y(t))
        }
        drawPath(path, gradient, alpha = 0.8f, style = Stroke(width = 1.2f * density))
    }
}
