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
    val accentPink: Color,
    val amber: Color,
    val error: Color,
    val errorFill: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color
)

// Light: crimson and ember on a warm parchment page, near-black text.
val LightPalette = AssistantPalette(
    dark = false,
    background = Color(0xFFFFF4F0),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFFCE6E0),
    outline = Color(0xFFF1CFC8),
    fieldBackground = Color(0xFFFDEDE8),
    selectedBg = Color(0xFFFFDCD5),
    primary = Color(0xFF8E0E1C),
    accent = Color(0xFFC8102E),
    accentFill = Color(0xFFC8102E),
    accentPink = Color(0xFFFF5A36),
    amber = Color(0xFFF2A12B),
    error = Color(0xFFD93A40),
    errorFill = Color(0xFFD93A40),
    textPrimary = Color(0xFF1C0A0D),
    textSecondary = Color(0xFF5A3A3F),
    textMuted = Color(0xFF84666B)
)

// Dark: the same crimson on a warm near-black page, soft off-white text.
val DarkPalette = AssistantPalette(
    dark = true,
    background = Color(0xFF140C0E),
    surface = Color(0xFF1D1215),
    surfaceElevated = Color(0xFF2A1A1E),
    outline = Color(0xFF452F34),
    fieldBackground = Color(0xFF24161A),
    selectedBg = Color(0xFF3A1B22),
    primary = Color(0xFFB3202F),
    accent = Color(0xFFF2566A),
    accentFill = Color(0xFFD8283F),
    accentPink = Color(0xFFD9532F),
    amber = Color(0xFFF2A12B),
    error = Color(0xFFFF6B70),
    errorFill = Color(0xFFC9343A),
    textPrimary = Color(0xFFF6E9EB),
    textSecondary = Color(0xFFCDB3B8),
    textMuted = Color(0xFFA38A8F)
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
val AssistantAccentPink: Color get() = ActivePalette.current.accentPink
val AssistantAmber: Color get() = ActivePalette.current.amber
val AssistantError: Color get() = ActivePalette.current.error
val AssistantErrorFill: Color get() = ActivePalette.current.errorFill

val AssistantTextPrimary: Color get() = ActivePalette.current.textPrimary
val AssistantTextSecondary: Color get() = ActivePalette.current.textSecondary
val AssistantTextMuted: Color get() = ActivePalette.current.textMuted
