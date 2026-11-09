package com.assistant.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.assistant.app.data.ChangelogEntry
import com.assistant.app.ui.theme.AssistantAccent
import com.assistant.app.ui.theme.AssistantOnButtonFill
import com.assistant.app.ui.theme.AssistantSurfaceElevated
import com.assistant.app.ui.theme.AssistantTextMuted
import com.assistant.app.ui.theme.AssistantTextPrimary
import com.assistant.app.ui.theme.AssistantTextSecondary

/** Opens after an update and lists what changed in every version since the one the user last saw. */
@Composable
fun WhatsNewCard(entries: List<ChangelogEntry>, onDismiss: () -> Unit) {
    if (entries.isEmpty()) return
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = AssistantSurfaceElevated) {
            Column(Modifier.padding(24.dp)) {
                Text("Updated to ${entries.first().version}", color = AssistantTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("What's new", color = AssistantAccent, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.padding(top = 12.dp))
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    entries.forEachIndexed { index, entry ->
                        // Older versions get their own heading; the newest is already named in the title.
                        if (index > 0) {
                            Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Version ${entry.version}", color = AssistantTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                if (entry.date.isNotBlank()) Text(entry.date, color = AssistantTextMuted, fontSize = 13.sp)
                            }
                        }
                        entry.sections.forEach { section ->
                            Text(
                                section.title.uppercase(), color = AssistantAccent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
                            )
                            section.items.forEach { item ->
                                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.Start) {
                                    Text("•", color = AssistantTextMuted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(end = 10.dp))
                                    Text(item, color = AssistantTextSecondary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.padding(top = 20.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AssistantAccent, contentColor = AssistantOnButtonFill)
                ) { Text("Got it") }
            }
        }
    }
}
