package com.assistant.app.ui.theme

import android.app.Activity
import android.content.Context
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/** What the user picked in the menu: follow the phone, or always light, or always dark. */
enum class ThemeMode(val label: String) {
    SYSTEM("System"), LIGHT("Light"), DARK("Dark");

    companion object {
        fun fromName(name: String?) = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/** The choice is kept in a tiny preferences file so it is known before the first frame is drawn. */
object ThemePrefs {
    private const val FILE = "ui_prefs"
    private const val KEY = "theme_mode"

    fun load(context: Context): ThemeMode =
        ThemeMode.fromName(context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null))

    fun save(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, mode.name).apply()
    }
}

fun paletteFor(mode: ThemeMode, systemDark: Boolean): AssistantPalette =
    if (mode == ThemeMode.DARK || (mode == ThemeMode.SYSTEM && systemDark)) DarkPalette else LightPalette

private val White = Color.White

private fun colorSchemeFor(p: AssistantPalette) =
    if (p.dark) {
        darkColorScheme(
            primary = p.accent, onPrimary = White,
            secondary = p.accent, onSecondary = White,
            tertiary = p.amber, onTertiary = Color.Black,
            background = p.background, onBackground = p.textPrimary,
            surface = p.surface, onSurface = p.textPrimary,
            surfaceVariant = p.surfaceElevated, onSurfaceVariant = p.textSecondary,
            surfaceTint = p.accent,
            surfaceContainerLowest = p.background, surfaceContainerLow = p.surface, surfaceContainer = p.surface,
            surfaceContainerHigh = p.surfaceElevated, surfaceContainerHighest = p.surfaceElevated,
            primaryContainer = p.selectedBg, onPrimaryContainer = p.accent,
            secondaryContainer = p.selectedBg, onSecondaryContainer = p.accent,
            outline = p.outline, outlineVariant = p.outline,
            error = p.error, onError = Color.Black,
            inverseSurface = p.textPrimary, inverseOnSurface = p.background, inversePrimary = p.accentFill,
            scrim = Color.Black
        )
    } else {
        lightColorScheme(
            primary = p.accent, onPrimary = White,
            secondary = p.accent, onSecondary = White,
            tertiary = p.amber, onTertiary = Color.Black,
            background = p.background, onBackground = p.textPrimary,
            surface = p.surface, onSurface = p.textPrimary,
            surfaceVariant = p.surfaceElevated, onSurfaceVariant = p.textSecondary,
            surfaceTint = p.accent,
            surfaceContainerLowest = p.surface, surfaceContainerLow = p.surface, surfaceContainer = p.surface,
            surfaceContainerHigh = p.surfaceElevated, surfaceContainerHighest = p.surfaceElevated,
            primaryContainer = p.selectedBg, onPrimaryContainer = p.accent,
            secondaryContainer = p.selectedBg, onSecondaryContainer = p.accent,
            outline = p.outline, outlineVariant = p.outline,
            error = p.error, onError = White,
            inverseSurface = p.textPrimary, inverseOnSurface = p.background, inversePrimary = p.accentPink,
            scrim = Color.Black
        )
    }

// Softer, rounder corners everywhere: text fields, buttons, menus and dialogs all read these.
private val AssistantShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** Paints the window behind Compose and the system bars to match the page, so no edge flashes the other theme. */
fun applyWindowColors(activity: Activity, palette: AssistantPalette) {
    val window = activity.window
    window.setBackgroundDrawable(ColorDrawable(palette.background.toArgb()))
    @Suppress("DEPRECATION")
    window.statusBarColor = palette.background.toArgb()
    @Suppress("DEPRECATION")
    window.navigationBarColor = palette.background.toArgb()
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = !palette.dark
        isAppearanceLightNavigationBars = !palette.dark
    }
}

@Composable
fun AssistantTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val palette = paletteFor(mode, isSystemInDarkTheme())
    // Set before the content composes, so the first frame already has the right colours.
    if (ActivePalette.current !== palette) ActivePalette.current = palette
    val view = LocalView.current
    SideEffect { (view.context as? Activity)?.let { applyWindowColors(it, palette) } }
    MaterialTheme(
        colorScheme = colorSchemeFor(palette),
        typography = AssistantTypography,
        shapes = AssistantShapes,
        content = content
    )
}
