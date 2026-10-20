package com.assistant.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

// A fixed light look: white cards on a warm parchment page with a crimson devil accent.
private val AssistantColorScheme = lightColorScheme(
    primary = AssistantAccent,
    secondary = AssistantAccent,
    tertiary = AssistantAmber,
    background = AssistantBackground,
    surface = AssistantSurface,
    surfaceVariant = AssistantSurfaceElevated,
    surfaceContainer = AssistantSurface,
    surfaceContainerHigh = AssistantSurfaceElevated,
    surfaceContainerHighest = AssistantSurfaceElevated,
    primaryContainer = AssistantSelectedBg,
    secondaryContainer = AssistantSelectedBg,
    onPrimaryContainer = AssistantAccent,
    onSecondaryContainer = AssistantAccent,
    outline = AssistantOutline,
    outlineVariant = AssistantOutline,
    onSurfaceVariant = AssistantTextSecondary,
    error = AssistantError,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    onBackground = AssistantTextPrimary,
    onSurface = AssistantTextPrimary,
    onError = androidx.compose.ui.graphics.Color.White
)

// Softer, rounder corners everywhere: text fields, buttons, menus and dialogs all read these.
private val AssistantShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AssistantTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AssistantColorScheme,
        typography = AssistantTypography,
        shapes = AssistantShapes,
        content = content
    )
}
