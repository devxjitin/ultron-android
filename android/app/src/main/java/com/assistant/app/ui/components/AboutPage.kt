package com.assistant.app.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import com.assistant.app.data.Changelog
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.app.BuildConfig
import com.assistant.app.network.UpdateInfo
import com.assistant.app.ui.theme.AppButtonShape
import com.assistant.app.ui.theme.AssistantAccent
import com.assistant.app.ui.theme.AssistantAccentFill
import com.assistant.app.ui.theme.AssistantAccentAlt
import com.assistant.app.ui.theme.AssistantError
import com.assistant.app.ui.theme.AssistantTextMuted
import com.assistant.app.ui.theme.AssistantTextPrimary
import com.assistant.app.ui.theme.AssistantTextSecondary
import com.assistant.app.viewmodel.UpdateCheckState
import java.text.DateFormat
import java.util.Date

/** About: what this app is, which build is installed, and whether a newer one is waiting. */
@Composable
fun AboutPage(
    assistantName: String,
    updateState: UpdateCheckState,
    availableUpdate: UpdateInfo?,
    lastCheckedAt: Long,
    updateError: String?,
    downloadProgress: Float?,
    onCheckForUpdates: () -> Unit,
    onUpdateNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    PageColumn(modifier) {
        PageCard {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                AppIconTile(72.dp)
                Spacer(Modifier.height(14.dp))
                Text(assistantName, color = AssistantTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Your personal voice assistant", color = AssistantTextSecondary, fontSize = 14.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp)
                )
                Text("Version ${BuildConfig.VERSION_NAME}", color = AssistantTextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }

        PageLabel("Updates")
        PageCard {
            when (updateState) {
                UpdateCheckState.CHECKING -> StatusLine("Checking for updates…", spinner = true)
                UpdateCheckState.UP_TO_DATE -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = AssistantAccent, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.size(10.dp))
                        Column {
                            Text("You're on the latest version", color = AssistantTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            if (lastCheckedAt > 0) {
                                Text("Last checked at ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(lastCheckedAt))}", color = AssistantTextMuted, fontSize = 13.sp)
                            }
                        }
                    }
                }
                UpdateCheckState.AVAILABLE -> if (availableUpdate != null) {
                    Text("Version ${availableUpdate.versionName} is available", color = AssistantTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    if (availableUpdate.sizeBytes > 0) {
                        Text("Download size ${formatBytes(availableUpdate.sizeBytes)}", color = AssistantTextMuted, fontSize = 13.sp)
                    }
                    if (availableUpdate.notes.isNotBlank()) {
                        Text(availableUpdate.notes, color = AssistantTextSecondary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                UpdateCheckState.FAILED -> Text(updateError ?: "Could not check for updates.", color = AssistantError, fontSize = 14.sp, lineHeight = 20.sp)
                UpdateCheckState.IDLE -> Text("Check whether a newer version is available.", color = AssistantTextSecondary, fontSize = 14.sp)
            }
            Spacer(Modifier.height(14.dp))
            if (downloadProgress != null) {
                LinearProgressIndicator(progress = { downloadProgress }, modifier = Modifier.fillMaxWidth(), color = AssistantAccent)
                Text("Downloading ${(downloadProgress * 100).toInt()}%", color = AssistantTextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            } else if (updateState == UpdateCheckState.AVAILABLE && availableUpdate != null) {
                Button(
                    shape = AppButtonShape,
                    onClick = onUpdateNow, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = com.assistant.app.ui.theme.AssistantButtonFill, contentColor = com.assistant.app.ui.theme.AssistantOnButtonFill)
                ) { Text("Update now") }
            } else {
                OutlinedButton(
                    shape = AppButtonShape,
                    onClick = onCheckForUpdates, enabled = updateState != UpdateCheckState.CHECKING, modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        when (updateState) {
                            UpdateCheckState.CHECKING -> "Checking…"
                            UpdateCheckState.UP_TO_DATE -> "Check again"
                            UpdateCheckState.FAILED -> "Try again"
                            else -> "Check for updates"
                        },
                        color = AssistantAccent
                    )
                }
            }
        }

        PageLabel("This build")
        PageCard {
            PageRow("Version", BuildConfig.VERSION_NAME)
            PageRow("Build", BuildConfig.VERSION_CODE.toString())
            PageRow("Package", BuildConfig.APPLICATION_ID)
            PageRow("Device", Build.MODEL)
            PageRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        }

        WhatsNew()

        PageLabel("Developer")
        PageCard {
            Text("Nandivex Technologies", color = AssistantTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("Founder: Chandra Prakash", color = AssistantTextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
            Text("Co-founder: Jitin", color = AssistantTextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
            Text("Co-builder: Sonia", color = AssistantTextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
            Text("devxjitin@gmail.com", color = AssistantTextMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        }

        PageLabel("Privacy")
        PageCard {
            Text(
                "Your memories and settings stay on this phone. What you say goes to Gemini to reply. " +
                    "If you turn on the optional checks in Settings, the text, notes or small screen picture a check needs go to Gemini too.",
                color = AssistantTextSecondary, fontSize = 14.sp, lineHeight = 21.sp
            )
        }
    }
}

/** What changed in the newest version only, read from the changelog shipped inside the app. */
@Composable
private fun WhatsNew() {
    val context = LocalContext.current
    val latest = remember {
        runCatching { Changelog.parse(context.assets.open("changelog.md").bufferedReader().use { it.readText() }) }
            .getOrDefault(emptyList()).firstOrNull()
    }
    latest?.let { entry ->
        PageLabel("What's new")
        PageCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Version ${entry.version}", color = AssistantTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (entry.date.isNotBlank()) {
                    Text(entry.date, color = AssistantTextMuted, fontSize = 13.sp)
                }
            }
            entry.sections.forEach { section ->
                PageLabel(section.title, color = AssistantAccent, top = 14.dp, bottom = 2.dp)
                // Each change as its own bullet, so long lines don't run into each other.
                section.items.forEach { item ->
                    Row(Modifier.padding(top = 6.dp)) {
                        Text("•", color = AssistantTextMuted, fontSize = 14.sp, lineHeight = 20.sp,
                            modifier = Modifier.padding(end = 10.dp))
                        Text(item, color = AssistantTextSecondary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLine(text: String, spinner: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (spinner) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = AssistantAccent)
        Text(text, color = AssistantTextPrimary, fontSize = 15.sp)
    }
}
