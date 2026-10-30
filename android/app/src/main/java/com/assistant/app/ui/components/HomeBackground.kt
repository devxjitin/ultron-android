package com.assistant.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.assistant.app.ui.theme.AssistantBackground

/** A plain vertical gradient behind the home page: gunmetal fading to a dark-red glow in dark mode, cold steel-grey in light mode. */
@Composable
fun HomeBackground(dark: Boolean, modifier: Modifier = Modifier.fillMaxSize()) {
    val spec = tween<Color>(700)
    val top by animateColorAsState(AssistantBackground, spec, label = "gradient-top")
    val middle by animateColorAsState(if (dark) Color(0xFF12151B) else Color(0xFFE9EDF1), spec, label = "gradient-middle")
    val bottom by animateColorAsState(if (dark) Color(0xFF2A1316) else Color(0xFFD5DCE4), spec, label = "gradient-bottom")
    Box(modifier.background(Brush.verticalGradient(listOf(top, middle, bottom))))
}
