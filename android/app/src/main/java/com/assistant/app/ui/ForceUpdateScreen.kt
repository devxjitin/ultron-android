package com.assistant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.app.ui.theme.AssistantTextPrimary

/**
 * Shown instead of the app when Remote Config raises `min_supported_version` above this build, for example after a
 * security fix. [updateAvailable] turns the button into a direct install once the update server has answered.
 */
@Composable
fun ForceUpdateScreen(
    updateAvailable: Boolean,
    checking: Boolean,
    onCheck: () -> Unit,
    onInstall: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Update required", color = AssistantTextPrimary, fontSize = 24.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            "This version of Ultron is no longer supported. Install the latest build to keep using the assistant.",
            color = AssistantTextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = if (updateAvailable) onInstall else onCheck, enabled = !checking) {
            Text(
                when {
                    checking -> "Checking..."
                    updateAvailable -> "Install update"
                    else -> "Check for update"
                }
            )
        }
    }
}
