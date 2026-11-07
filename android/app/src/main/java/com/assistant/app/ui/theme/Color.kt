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
    val textMuted: Color,
    /** Fill and text colour of the main button: near-black on a light page, Ultron red on a dark page. */
    val buttonFill: Color,
    val onButtonFill: Color
)

// Light: a warm paper page, flat soft-grey cards and near-black text. Ultron crimson is kept only for small accents.
val LightPalette = AssistantPalette(
    dark = false,
    background = Color(0xFFFAF9F5),
    surface = Color(0xFFF0EEE6),
    surfaceElevated = Color(0xFFE8E5DB),
    outline = Color(0xFFE0DDD2),
    fieldBackground = Color(0xFFFFFFFF),
    selectedBg = Color(0xFFEDEBE2),
    primary = Color(0xFF9B1B24),
    accent = Color(0xFFB3202A),
    accentFill = Color(0xFFB3202A),
    accentAlt = Color(0xFF6E1219),
    amber = Color(0xFF92400E),
    error = Color(0xFFA8350A),
    errorFill = Color(0xFFA8350A),
    textPrimary = Color(0xFF1F1E1D),
    textSecondary = Color(0xFF5A5852),
    textMuted = Color(0xFF6E6B64),
    buttonFill = Color(0xFF1F1E1D),
    onButtonFill = Color(0xFFFAF9F5)
)

// Dark: a near-black warm page, flat dark-grey cards and soft off-white text.
val DarkPalette = AssistantPalette(
    dark = true,
    background = Color(0xFF131313),
    surface = Color(0xFF1F1F1F),
    surfaceElevated = Color(0xFF292929),
    outline = Color(0xFF2F2F2F),
    fieldBackground = Color(0xFF1B1B1B),
    selectedBg = Color(0xFF262626),
    primary = Color(0xFFC4232C),
    accent = Color(0xFFFF5A4D),
    accentFill = Color(0xFFC4232C),
    accentAlt = Color(0xFF7A1218),
    amber = Color(0xFFE8B931),
    error = Color(0xFFFF9A3D),
    errorFill = Color(0xFFC2410C),
    textPrimary = Color(0xFFEDEDEA),
    textSecondary = Color(0xFFA9A7A1),
    textMuted = Color(0xFF8A8882),
    buttonFill = Color(0xFFC4232C),
    onButtonFill = Color(0xFFFFFFFF)
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

/** The main button: dark on a light page and Ultron red on a dark page, with matching text. */
val AssistantButtonFill: Color get() = ActivePalette.current.buttonFill
val AssistantOnButtonFill: Color get() = ActivePalette.current.onButtonFill
