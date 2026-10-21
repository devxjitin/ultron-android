package com.assistant.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog as MaterialAlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogProperties

/**
 * The app's dialog. It takes its colours straight from the active palette, the same source every page uses, so a
 * dialog always matches the page behind it in light and dark and its text can never fade into its background.
 * Same parameters as the Material dialog, so call sites only change their import.
 */
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    containerColor: Color = AssistantSurfaceElevated,
    iconContentColor: Color = AssistantAccent,
    titleContentColor: Color = AssistantTextPrimary,
    textContentColor: Color = AssistantTextSecondary,
    tonalElevation: Dp = Dp.Hairline,
    properties: DialogProperties = DialogProperties()
) {
    MaterialAlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = containerColor,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = tonalElevation,
        properties = properties
    )
}
