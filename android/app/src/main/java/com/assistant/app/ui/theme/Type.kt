package com.assistant.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.assistant.app.R

/**
 * Inter (SIL Open Font License, see assets/inter_font_license.txt) -- a free, geometrically
 * close substitute for Helvetica Neue, which is commercially licensed and can't be bundled
 * without a purchased license. One variable font file covers every weight below.
 */
@OptIn(ExperimentalTextApi::class)
private val InterVariable = FontFamily(
    Font(R.font.inter_variable, weight = FontWeight.Light, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
    Font(R.font.inter_variable, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.inter_variable, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.inter_variable, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.inter_variable, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700)))
)

/** Built per palette: the text colours are read when this runs, so a switch between light and dark must build it again. */
fun assistantTypography() = Typography(
    displayLarge = TextStyle(
        fontFamily = InterVariable,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        color = AssistantTextPrimary
    ),
    headlineMedium = TextStyle(
        fontFamily = InterVariable,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        color = AssistantTextPrimary
    ),
    // AlertDialog titles (Settings, confirmation dialogs) use this style by default --
    // without it they'd silently fall back to Material3's default Roboto instead of Inter.
    headlineSmall = TextStyle(
        fontFamily = InterVariable,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        color = AssistantTextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = InterVariable,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        color = AssistantTextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = InterVariable,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = AssistantTextSecondary
    ),
    labelLarge = TextStyle(
        fontFamily = InterVariable,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        color = AssistantTextSecondary
    )
)
