package com.assistant.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.assistant.app.ui.theme.AssistantAccent
import com.assistant.app.ui.theme.AssistantAmber
import com.assistant.app.ui.theme.AssistantError
import com.assistant.app.ui.theme.AssistantTextPrimary
import com.assistant.app.viewmodel.VoiceState

@Composable
fun MicButton(
    voiceState: VoiceState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Violet-to-pink gradient (like the voice ribbon) instead of the default dark button, e.g.
     *  inside the nav bar. The gradient follows the ribbon's colours for the current state. */
    accent: Boolean = false
) {
    val active = voiceState != VoiceState.IDLE && voiceState != VoiceState.ERROR
    val accentIdle = accent
    val containerColor by animateColorAsState(
        targetValue = when (voiceState) {
            VoiceState.ERROR -> AssistantError
            VoiceState.CONNECTING, VoiceState.RESETTING -> AssistantAmber
            else -> if (accent) AssistantAccent else com.assistant.app.ui.theme.AssistantSurfaceElevated
        },
        label = "micButtonColor"
    )

    // Same colours as the voice ribbon for the current state, so the button follows the wave.
    val (ribbonLeft, ribbonRight) = ribbonColors(voiceState)
    val gradientLeft by animateColorAsState(ribbonLeft, tween(900), label = "micGradientLeft")
    val gradientRight by animateColorAsState(ribbonRight, tween(900), label = "micGradientRight")

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = tween(120),
        label = "micPressScale"
    )

    Box(modifier = modifier.size(72.dp), contentAlignment = Alignment.Center) {
        if (voiceState == VoiceState.LISTENING) {
            PulseRing(color = if (accentIdle) gradientLeft else containerColor)
        }

        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier
                .size(60.dp)
                .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                .then(
                    if (accentIdle) {
                        Modifier.background(
                            Brush.linearGradient(listOf(gradientLeft, gradientRight)),
                            CircleShape
                        )
                    } else {
                        Modifier
                    }
                ),
            shape = CircleShape,
            containerColor = if (accentIdle) androidx.compose.ui.graphics.Color.Transparent else containerColor,
            contentColor = androidx.compose.ui.graphics.Color.White,
            elevation = if (accentIdle) {
                FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
            } else {
                FloatingActionButtonDefaults.elevation()
            },
            interactionSource = interactionSource
        ) {
            if (voiceState == VoiceState.CONNECTING || voiceState == VoiceState.RESETTING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = androidx.compose.ui.graphics.Color.White,
                    strokeWidth = 3.dp
                )
            } else {
                Icon(
                    imageVector = if (active) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = if (active) "Stop talking to the assistant" else "Start talking to the assistant",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
private fun PulseRing(color: androidx.compose.ui.graphics.Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "micPulse")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Restart
        ),
        label = "micPulseProgress"
    )

    Canvas(modifier = Modifier.size(72.dp)) {
        val radius = (size.minDimension / 2f) * (0.72f + progress * 0.28f)
        drawCircle(
            color = color.copy(alpha = (1f - progress) * 0.5f),
            radius = radius,
            center = Offset(size.width / 2f, size.height / 2f)
        )
    }
}
