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

/** The serif used for page titles and the menu heading (the phone's own serif, so nothing is bundled). */
val AssistantSerif: FontFamily = FontFamily.Serif

/** Built per palette: the text colours are read when this runs, so a switch between light and dark must build it again. */
fun assistantTypography() = Typography(
    displayLarge = TextStyle(fontFamily = AssistantSerif, fontWeight = FontWeight.Normal, fontSize = 34.sp, color = AssistantTextPrimary),
    headlineMedium = TextStyle(fontFamily = AssistantSerif, fontWeight = FontWeight.Normal, fontSize = 22.sp, color = AssistantTextPrimary),
    // AlertDialog titles (Settings, confirmation dialogs) use this style by default.
    headlineSmall = TextStyle(fontFamily = AssistantSerif, fontWeight = FontWeight.Normal, fontSize = 19.sp, color = AssistantTextPrimary),
    titleLarge = TextStyle(fontFamily = AssistantSerif, fontWeight = FontWeight.Normal, fontSize = 19.sp, color = AssistantTextPrimary),
    titleMedium = TextStyle(fontFamily = InterVariable, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = AssistantTextPrimary),
    titleSmall = TextStyle(fontFamily = InterVariable, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = AssistantTextPrimary),
    bodyLarge = TextStyle(fontFamily = InterVariable, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, color = AssistantTextPrimary),
    bodyMedium = TextStyle(fontFamily = InterVariable, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, color = AssistantTextSecondary),
    bodySmall = TextStyle(fontFamily = InterVariable, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp, color = AssistantTextSecondary),
    labelLarge = TextStyle(fontFamily = InterVariable, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = AssistantTextSecondary)
)
