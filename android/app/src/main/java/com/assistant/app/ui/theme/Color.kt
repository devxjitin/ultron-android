package com.assistant.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Every colour the screens use, as one light set and one dark set. The Assistant* names below are read at use time from
 * the palette that is active, so a page never needs to know which theme it is in. Text pairs were checked for contrast
 * (4.5:1 or better for text on every surface it sits on), and [accent] is for text and outlines while [accentFill] is
 * for backgrounds that carry white text, because one indigo cannot be both on a dark page.
 */
@Immutable
class AssistantPalette(
    val dark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val outline: Color,
    val fieldBackground: Color,
    val selectedBg: Color,
    val primary: Color,
    val accent: Color,
    val accentFill: Color,
    val accentAlt: Color,
    val amber: Color,
    val error: Color,
    val errorFill: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color
)

// Light: indigo on a cool paper-white page, near-black text.
val LightPalette = AssistantPalette(
    dark = false,
    background = Color(0xFFF6F7FB),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFEDEFF6),
    outline = Color(0xFFD9DCE8),
    fieldBackground = Color(0xFFF0F2F8),
    selectedBg = Color(0xFFE3E7FF),
    primary = Color(0xFF3730A3),
    accent = Color(0xFF4338CA),
    accentFill = Color(0xFF4F46E5),
    accentAlt = Color(0xFF2563EB),
    amber = Color(0xFFF2A12B),
    error = Color(0xFFD93A40),
    errorFill = Color(0xFFD93A40),
    textPrimary = Color(0xFF12141F),
    textSecondary = Color(0xFF454B66),
    textMuted = Color(0xFF666D8A)
)

// Dark: the same indigo on a blue-black page, soft off-white text.
val DarkPalette = AssistantPalette(
    dark = true,
    background = Color(0xFF0E1018),
    surface = Color(0xFF161924),
    surfaceElevated = Color(0xFF1F2333),
    outline = Color(0xFF2E3348),
    fieldBackground = Color(0xFF1A1E2C),
    selectedBg = Color(0xFF242B52),
    primary = Color(0xFF4F46E5),
    accent = Color(0xFF8B9BFF),
    accentFill = Color(0xFF4F46E5),
    accentAlt = Color(0xFF2563EB),
    amber = Color(0xFFF2A12B),
    error = Color(0xFFFF6B70),
    errorFill = Color(0xFFC9343A),
    textPrimary = Color(0xFFEDEFF7),
    textSecondary = Color(0xFFB9BED3),
    textMuted = Color(0xFF8D93AD)
)

/** The palette in use. A snapshot state, so every screen that read a colour redraws when the theme changes. */
object ActivePalette {
    var current by mutableStateOf(LightPalette)
}

val AssistantBackground: Color get() = ActivePalette.current.background
val AssistantSurface: Color get() = ActivePalette.current.surface
val AssistantSurfaceElevated: Color get() = ActivePalette.current.surfaceElevated
// Hairline borders and filled-field backgrounds that give cards and inputs their soft edge.
val AssistantOutline: Color get() = ActivePalette.current.outline
val AssistantFieldBackground: Color get() = ActivePalette.current.fieldBackground
// Fill and accent text for the selected chip, card or menu row.
val AssistantSelectedBg: Color get() = ActivePalette.current.selectedBg

val AssistantPrimaryLight: Color get() = ActivePalette.current.primary
// Accent for text, icons and outlines. Use AssistantAccentFill for a background that carries white text.
val AssistantAccent: Color get() = ActivePalette.current.accent
/** Second stop of the accent gradient used on the greeting and the app icon tile. */
val AssistantGradientEnd: Color get() = if (ActivePalette.current.dark) Color(0xFF22D3EE) else Color(0xFF0891B2)
val AssistantAccentFill: Color get() = ActivePalette.current.accentFill
val AssistantAccentAlt: Color get() = ActivePalette.current.accentAlt
val AssistantAmber: Color get() = ActivePalette.current.amber
val AssistantError: Color get() = ActivePalette.current.error
val AssistantErrorFill: Color get() = ActivePalette.current.errorFill

val AssistantTextPrimary: Color get() = ActivePalette.current.textPrimary
val AssistantTextSecondary: Color get() = ActivePalette.current.textSecondary
val AssistantTextMuted: Color get() = ActivePalette.current.textMuted
