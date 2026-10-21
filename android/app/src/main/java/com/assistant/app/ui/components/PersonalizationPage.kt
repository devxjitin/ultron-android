package com.assistant.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.app.data.BaseStyle
import com.assistant.app.data.MAX_ABOUT_FIELD_CHARS
import com.assistant.app.data.MAX_CUSTOM_INSTRUCTIONS_CHARS
import com.assistant.app.data.Personalization
import com.assistant.app.data.TraitLevel
import com.assistant.app.ui.theme.AssistantAccent
import com.assistant.app.ui.theme.AssistantAccentFill
import com.assistant.app.ui.theme.AssistantError
import com.assistant.app.ui.theme.AssistantOutline
import com.assistant.app.ui.theme.AssistantTextMuted
import com.assistant.app.ui.theme.AssistantTextPrimary
import com.assistant.app.ui.theme.AssistantTextSecondary

/**
 * Personalization: how the assistant talks to you. Dropdown and level choices apply at once; the
 * text fields are saved with the Save button so a half-typed instruction is never used.
 */
@Composable
fun PersonalizationPage(
    personalization: Personalization,
    assistantName: String,
    onChange: (Personalization) -> Unit,
    modifier: Modifier = Modifier
) {
    var custom by remember(personalization.customInstructions) { mutableStateOf(personalization.customInstructions) }
    var nickname by remember(personalization.nickname) { mutableStateOf(personalization.nickname) }
    var occupation by remember(personalization.occupation) { mutableStateOf(personalization.occupation) }
    var aboutMore by remember(personalization.aboutMore) { mutableStateOf(personalization.aboutMore) }
    var confirmingReset by remember { mutableStateOf(false) }
    val textChanged = custom != personalization.customInstructions || nickname != personalization.nickname ||
        occupation != personalization.occupation || aboutMore != personalization.aboutMore

    PageColumn(modifier) {
        PageLabel("Base style and tone")
        SettingsDropdown(
            value = personalization.baseStyle.label + if (personalization.baseStyle == BaseStyle.DEFAULT) "" else " · ${personalization.baseStyle.hint}",
            options = BaseStyle.entries.map { it.name to "${it.label} · ${it.hint}" },
            selectedId = personalization.baseStyle.name,
            onSelect = { onChange(personalization.copy(baseStyle = BaseStyle.fromStored(it))) },
            hint = "Sets the overall personality of $assistantName's replies. The options below fine-tune it."
        )

        PageLabel("Characteristics")
        PageCard {
            TraitRow("Warmth", "How friendly and personal replies feel", personalization.warmth) {
                onChange(personalization.copy(warmth = it))
            }
            Divider()
            TraitRow("Enthusiasm", "How much energy replies have", personalization.enthusiasm) {
                onChange(personalization.copy(enthusiasm = it))
            }
            Divider()
            TraitRow("Headers and lists", "Structure in typed chat answers", personalization.headersAndLists) {
                onChange(personalization.copy(headersAndLists = it))
            }
            Divider()
            TraitRow("Emoji", "How often typed chat uses emoji", personalization.emoji) {
                onChange(personalization.copy(emoji = it))
            }
        }

        PageLabel("Custom instructions")
        OutlinedTextField(
            value = custom,
            onValueChange = { custom = it.take(MAX_CUSTOM_INSTRUCTIONS_CHARS) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            maxLines = 8,
            placeholder = { Text("Anything you want $assistantName to always keep in mind", color = AssistantTextMuted) },
            supportingText = { Text("${custom.length}/$MAX_CUSTOM_INSTRUCTIONS_CHARS", color = AssistantTextMuted) },
            colors = assistantTextFieldColors()
        )

        PageLabel("About you")
        OutlinedTextField(
            value = nickname, onValueChange = { nickname = it.take(MAX_ABOUT_FIELD_CHARS) },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("What should $assistantName call you?") }, colors = assistantTextFieldColors()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = occupation, onValueChange = { occupation = it.take(MAX_ABOUT_FIELD_CHARS) },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Occupation") }, colors = assistantTextFieldColors()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = aboutMore, onValueChange = { aboutMore = it.take(MAX_ABOUT_FIELD_CHARS) },
            modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 6,
            label = { Text("Anything else about you") }, colors = assistantTextFieldColors()
        )

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                onChange(personalization.copy(customInstructions = custom, nickname = nickname, occupation = occupation, aboutMore = aboutMore))
            },
            enabled = textChanged,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AssistantAccentFill, contentColor = Color.White)
        ) { Text("Save") }
        if (!personalization.isDefault) {
            TextButton(onClick = { confirmingReset = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Reset everything to default", color = AssistantError)
            }
        }
        Text(
            "Your choices apply to typed chat and voice. Headers, lists and emoji only apply to typed chat.",
            color = AssistantTextMuted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp, start = 4.dp)
        )
    }

    if (confirmingReset) {
        AlertDialog(
            onDismissRequest = { confirmingReset = false },
            title = { Text("Reset personalization?") },
            text = { Text("Style, tone, custom instructions and the details about you will go back to default.") },
            confirmButton = {
                TextButton(onClick = { confirmingReset = false; onChange(Personalization()) }) { Text("Reset", color = AssistantError) }
            },
            dismissButton = { TextButton(onClick = { confirmingReset = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Divider() {
    Spacer(Modifier.height(12.dp))
    HorizontalDivider(color = AssistantOutline)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun TraitRow(title: String, hint: String, level: TraitLevel, onLevel: (TraitLevel) -> Unit) {
    Column {
        Text(title, color = AssistantTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text(hint, color = AssistantTextSecondary, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            TraitLevel.entries.forEach { ChoiceChip(it.label, it == level, onClick = { onLevel(it) }) }
        }
    }
}
