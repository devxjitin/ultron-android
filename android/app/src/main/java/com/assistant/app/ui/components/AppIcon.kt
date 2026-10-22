package com.assistant.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.assistant.app.R

/** The launcher icon (mark on its midnight-to-indigo gradient) at any size, so the app shows one identity everywhere. */
@Composable
fun AppIconTile(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.24f))
            .background(Brush.radialGradient(listOf(Color(0xFF5B4BE8), Color(0xFF2A2490), Color(0xFF0A0B1E)))),
        contentAlignment = Alignment.Center
    ) {
        // The launcher art keeps a wide safe margin for adaptive-icon masks; zoom in so the mark fills the tile.
        Image(
            painterResource(R.drawable.ic_launcher_foreground), contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.45f; scaleY = 1.45f }
        )
    }
}
