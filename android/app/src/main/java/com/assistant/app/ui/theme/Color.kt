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
 * for backgrounds that carry white text, because one red cannot be both on a dark page.
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

// Light: Ultron crimson on a cold steel-grey page, near-black text.
val LightPalette = AssistantPalette(
    dark = false,
    background = Color(0xFFEEF1F4),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFE4E8ED),
    outline = Color(0xFFCDD4DC),
    fieldBackground = Color(0xFFE8ECF0),
    selectedBg = Color(0xFFF9E1E3),
    primary = Color(0xFF9B1B24),
    accent = Color(0xFFB3202A),
    accentFill = Color(0xFFB3202A),
    accentAlt = Color(0xFF6E1219),
    amber = Color(0xFF92400E),
    error = Color(0xFFA8350A),
    errorFill = Color(0xFFA8350A),
    textPrimary = Color(0xFF12151A),
    textSecondary = Color(0xFF444C58),
    textMuted = Color(0xFF5F6876)
)

// Dark: the same crimson on a gunmetal page, soft off-white text.
val DarkPalette = AssistantPalette(
    dark = true,
    background = Color(0xFF0C0E12),
    surface = Color(0xFF14171D),
    surfaceElevated = Color(0xFF1C2028),
    outline = Color(0xFF2B313B),
    fieldBackground = Color(0xFF171B22),
    selectedBg = Color(0xFF2C1519),
    primary = Color(0xFFC4232C),
    accent = Color(0xFFFF5A4D),
    accentFill = Color(0xFFC4232C),
    accentAlt = Color(0xFF7A1218),
    amber = Color(0xFFE8B931),
    error = Color(0xFFFF9A3D),
    errorFill = Color(0xFFC2410C),
    textPrimary = Color(0xFFECEFF3),
    textSecondary = Color(0xFFB4BCC8),
    textMuted = Color(0xFF8A93A1)
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
val AssistantAccentFill: Color get() = ActivePalette.current.accentFill
val AssistantAccentAlt: Color get() = ActivePalette.current.accentAlt
val AssistantAmber: Color get() = ActivePalette.current.amber
val AssistantError: Color get() = ActivePalette.current.error
val AssistantErrorFill: Color get() = ActivePalette.current.errorFill

val AssistantTextPrimary: Color get() = ActivePalette.current.textPrimary
val AssistantTextSecondary: Color get() = ActivePalette.current.textSecondary
val AssistantTextMuted: Color get() = ActivePalette.current.textMuted
