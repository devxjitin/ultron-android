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
import com.assistant.app.network.LiveContext
import com.assistant.app.viewmodel.VoiceState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

@Composable
fun MicButton(
    voiceState: VoiceState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Tokens the live session has used so far; drawn as a ring that fills up, 0 hides it. */
    contextTokens: Int = 0
) {
    val active = voiceState != VoiceState.IDLE && voiceState != VoiceState.ERROR
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

    val contextFraction by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (active) LiveContext.fraction(contextTokens) else 0f,
        animationSpec = tween(400),
        label = "micContextFill"
    )
    val ringDescription = if (active && contextTokens > 0) {
        "Context ${(contextFraction * 100).toInt()} percent full, ${LiveContext.label(contextTokens)} tokens"
    } else null

    Box(
        modifier = modifier.size(72.dp).then(
            if (ringDescription != null) Modifier.semantics { stateDescription = ringDescription } else Modifier
        ),
        contentAlignment = Alignment.Center
    ) {
        if (voiceState == VoiceState.LISTENING) {
            PulseRing(color = gradientLeft)
        }

        if (active && contextTokens > 0) ContextRing(contextFraction)

        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier
                .size(60.dp)
                .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                .background(Brush.linearGradient(listOf(gradientLeft, gradientRight)), CircleShape),
            shape = CircleShape,
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = androidx.compose.ui.graphics.Color.White,
            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
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

/** A thin ring around the button that fills clockwise with the share of the context window in use. */
@Composable
private fun ContextRing(fraction: Float) {
    val color = when (LiveContext.level(fraction)) {
        LiveContext.Level.NORMAL -> AssistantAccent
        LiveContext.Level.WARNING -> AssistantAmber
        LiveContext.Level.CRITICAL -> AssistantError
    }
    val track = AssistantTextPrimary.copy(alpha = 0.15f)
    Canvas(modifier = Modifier.size(72.dp)) {
        val stroke = 3.dp.toPx()
        val inset = stroke / 2f
        val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(inset, inset)
        drawArc(color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = topLeft, size = arcSize,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke))
        drawArc(color = color, startAngle = -90f, sweepAngle = 360f * fraction, useCenter = false, topLeft = topLeft, size = arcSize,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
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
