package com.assistant.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.app.ui.theme.AssistantAccent
import com.assistant.app.ui.theme.AssistantAmber
import com.assistant.app.ui.theme.AssistantError
import com.assistant.app.ui.theme.AssistantPrimaryLight
import com.assistant.app.ui.theme.AssistantSurfaceElevated
import com.assistant.app.ui.theme.AssistantTextPrimary
import com.assistant.app.viewmodel.AppNotificationType
import kotlinx.coroutines.delay

/** A short-lived typed notification that rises above the bottom navigation. */
@Composable
fun AssistantToastHost(
    message: String?,
    type: AppNotificationType,
    toastId: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(toastId) {
        if (message != null) {
            delay(2600)
            onDismiss()
        }
    }
    AnimatedVisibility(
        visible = message != null,
        modifier = modifier.padding(horizontal = 16.dp),
        enter = fadeIn(tween(180)) +
            slideInVertically(tween(280)) { it } +
            scaleIn(tween(280), initialScale = 0.92f),
        exit = fadeOut(tween(160)) +
            slideOutVertically(tween(220)) { it / 2 } +
            scaleOut(tween(220), targetScale = 0.96f)
    ) {
        val shape = RoundedCornerShape(50)
        val (accentColor, icon) = when (type) {
            AppNotificationType.SUCCESS -> AssistantPrimaryLight to Icons.Filled.Check
            AppNotificationType.ERROR -> AssistantError to Icons.Filled.Error
            AppNotificationType.WARNING -> AssistantAmber to Icons.Filled.Warning
            AppNotificationType.INFO -> AssistantAccent to Icons.Filled.Info
        }
        Row(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .shadow(16.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                .background(AssistantSurfaceElevated, shape)
                .padding(start = 10.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(accentColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = message.orEmpty(),
                color = AssistantTextPrimary,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
    }
}
