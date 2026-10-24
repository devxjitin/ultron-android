package com.assistant.app.ui.components

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.assistant.app.ui.theme.AppButtonShape
import com.assistant.app.ui.theme.AssistantTextMuted

/** Lets the user make Ultron the phone's digital assistant, so holding home or power opens it talking. */
@Composable
fun DefaultAssistantSection() {
    val context = LocalContext.current
    // Bumped on every resume so the status is right after coming back from system Settings.
    var resumes by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resumes++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val isDefault = remember(resumes) { holdsAssistantRole(context) }

    Column {
        SettingsHeading("Default assistant")
        Text(
            if (isDefault) "Ultron is your phone's assistant. Hold the home or power button to talk."
            else "Make Ultron open and start talking when you hold the home or power button. " +
                "Choose Ultron under “Digital assistant app”.",
            color = AssistantTextMuted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(start = 4.dp)
        )
        if (!isDefault) {
            OutlinedButton(
                shape = AppButtonShape,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                onClick = { openAssistantSettings(context) }
            ) { Text("Set as default assistant") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun holdsAssistantRole(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
        runCatching { context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_ASSISTANT) }.getOrDefault(false)

/** An app cannot request the assistant role itself, so this opens the system screen where the user picks it. */
private fun openAssistantSettings(context: Context) {
    val screens = listOf(Settings.ACTION_VOICE_INPUT_SETTINGS, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, Settings.ACTION_SETTINGS)
    for (action in screens) {
        if (runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    }
}
