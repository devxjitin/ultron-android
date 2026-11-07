package com.assistant.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.app.ui.theme.AlertDialog
import com.assistant.app.ui.theme.AssistantAccent
import com.assistant.app.ui.theme.AssistantFieldBackground
import com.assistant.app.ui.theme.AssistantOutline
import com.assistant.app.ui.theme.AssistantSurface
import com.assistant.app.ui.theme.AssistantTextMuted
import com.assistant.app.ui.theme.AssistantTextPrimary
import com.assistant.app.ui.theme.AssistantTextSecondary

/** What the request form sends. [type] is "skill" or "connector". */
data class FeatureRequest(val type: String, val name: String, val details: String, val examples: String, val email: String)

/** The form for asking the developer to add a skill or a connector. [onSend] must call back with whether it was sent. */
@Composable
fun FeatureRequestDialog(
    initialType: String,
    sending: Boolean,
    error: String?,
    onSend: (FeatureRequest) -> Unit,
    onDismiss: () -> Unit
) {
    var type by remember { mutableStateOf(initialType) }
    var name by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var examples by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    val colors = TextFieldDefaults.colors(
        focusedContainerColor = AssistantFieldBackground, unfocusedContainerColor = AssistantFieldBackground,
        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
        cursorColor = AssistantAccent, focusedTextColor = AssistantTextPrimary, unfocusedTextColor = AssistantTextPrimary
    )
    val ready = name.trim().length >= 2 && details.trim().length >= 10 && !sending
    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text("Request a skill or connector") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = type == "skill", onClick = { type = "skill" }, label = { Text("Skill") })
                    FilterChip(selected = type == "connector", onClick = { type = "connector" }, label = { Text("Connector") })
                }
                Text(
                    if (type == "skill") "A skill teaches Ultron how to do something, such as a routine or a way of answering."
                    else "A connector lets Ultron use another app or service, such as music, notes or a smart home.",
                    color = AssistantTextSecondary, fontSize = 13.sp, lineHeight = 18.sp
                )
                TextField(
                    value = name, onValueChange = { name = it.take(80) }, singleLine = true,
                    placeholder = { Text(if (type == "skill") "Name, for example Daily briefing" else "Name, for example Spotify", color = AssistantTextMuted, fontSize = 14.sp, maxLines = 1) },
                    shape = RoundedCornerShape(14.dp), colors = colors, modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = details, onValueChange = { details = it.take(1500) }, minLines = 3, maxLines = 6,
                    placeholder = { Text("What should it do? The more detail the better.", color = AssistantTextMuted, fontSize = 14.sp) },
                    shape = RoundedCornerShape(14.dp), colors = colors, modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = examples, onValueChange = { examples = it.take(600) }, minLines = 2, maxLines = 4,
                    placeholder = { Text("What would you say to it? (optional)", color = AssistantTextMuted, fontSize = 14.sp) },
                    shape = RoundedCornerShape(14.dp), colors = colors, modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = email, onValueChange = { email = it.take(120) }, singleLine = true,
                    placeholder = { Text("Your email, so the developer can reply (optional)", color = AssistantTextMuted, fontSize = 14.sp, maxLines = 1) },
                    shape = RoundedCornerShape(14.dp), colors = colors, modifier = Modifier.fillMaxWidth()
                )
                if (error != null) Text(error, color = com.assistant.app.ui.theme.AssistantError, fontSize = 13.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(2.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = { onSend(FeatureRequest(type, name.trim(), details.trim(), examples.trim(), email.trim())) }, enabled = ready) {
                Text(if (sending) "Sending" else "Send")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !sending) { Text("Cancel") } }
    )
}
