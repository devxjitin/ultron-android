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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.app.data.MAX_SKILL_BODY_CHARS
import com.assistant.app.data.SkillEntry
import com.assistant.app.ui.theme.AlertDialog
import com.assistant.app.ui.theme.AssistantAccent
import com.assistant.app.ui.theme.AssistantAccentAlt
import com.assistant.app.ui.theme.AssistantAccentFill
import com.assistant.app.ui.theme.AssistantError
import com.assistant.app.ui.theme.AssistantFieldBackground
import com.assistant.app.ui.theme.AssistantOutline
import com.assistant.app.ui.theme.AssistantSurface
import com.assistant.app.ui.theme.AssistantTextMuted
import com.assistant.app.ui.theme.AssistantTextPrimary
import com.assistant.app.ui.theme.AssistantTextSecondary

/**
 * The assistant's skills: instruction sets it loads only when a request needs one.
 * Switch each on or off, and add, edit or delete your own. [onSave] returns an error to show, or null when saved.
 */
@Composable
fun SkillsPage(
    skills: List<SkillEntry>,
    onToggle: (String, Boolean) -> Unit,
    onSave: (name: String, description: String, body: String) -> String?,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf<SkillEntry?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SkillEntry?>(null) }

    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        Text(
            "A skill is a set of instructions for one kind of task. The assistant only sees its name and description, and opens the full instructions only when your request needs it.",
            color = AssistantTextSecondary, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(horizontal = 4.dp)
        )
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(AssistantAccentFill, AssistantAccentAlt)))
                .clickable { adding = true }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text("Add a skill", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        PageLabel("Skills (${skills.size})")
        if (skills.isEmpty()) {
            Text("No skills yet.", color = AssistantTextMuted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
        }
        skills.forEach { entry ->
            val shape = RoundedCornerShape(16.dp)
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(shape).background(AssistantSurface)
                    .border(BorderStroke(1.dp, AssistantOutline), shape)
                    .clickable { editing = entry }.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.skill.name + if (entry.builtIn) "  ·  built in" else "",
                        color = AssistantTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text(entry.skill.description, color = AssistantTextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                }
                Spacer(Modifier.size(10.dp))
                Switch(
                    checked = entry.enabled,
                    onCheckedChange = { onToggle(entry.skill.name, it) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = AssistantAccentFill, checkedThumbColor = Color.White,
                        uncheckedTrackColor = AssistantFieldBackground, uncheckedBorderColor = AssistantOutline,
                        uncheckedThumbColor = AssistantTextMuted
                    )
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (adding) {
        SkillEditor(null, onSave, onDismiss = { adding = false }, onDelete = null)
    }
    editing?.let { entry ->
        SkillEditor(entry, onSave, onDismiss = { editing = null }, onDelete = if (entry.builtIn) null else ({ deleting = entry; editing = null }))
    }
    deleting?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this skill?") },
            text = { Text("\"${entry.skill.name}\" will be removed for good.") },
            confirmButton = { TextButton(onClick = { onDelete(entry.skill.name); deleting = null }) { Text("Delete", color = AssistantError) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

/** Add a skill ([entry] null) or view/edit one. Built-in skills are shown read-only. */
@Composable
private fun SkillEditor(
    entry: SkillEntry?,
    onSave: (String, String, String) -> String?,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val readOnly = entry?.builtIn == true
    var name by remember(entry) { mutableStateOf(entry?.skill?.name.orEmpty()) }
    var description by remember(entry) { mutableStateOf(entry?.skill?.description.orEmpty()) }
    var body by remember(entry) { mutableStateOf(entry?.skill?.body.orEmpty()) }
    var error by remember(entry) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (entry == null) "New skill" else if (readOnly) "Built-in skill" else "Edit skill") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SkillField(name, { name = it.take(48) }, "Name (e.g. trip-planner)", enabled = entry == null, singleLine = true)
                SkillField(description, { description = it.take(300) }, "When to use it (one line)", enabled = !readOnly)
                SkillField(body, { body = it.take(MAX_SKILL_BODY_CHARS) }, "Instructions", enabled = !readOnly, minLines = 6)
                error?.let { Text(it, color = AssistantError, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            if (!readOnly) TextButton(onClick = {
                error = onSave(name, description, body)
                if (error == null) onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                onDelete?.let { TextButton(onClick = it) { Text("Delete", color = AssistantError) } }
                TextButton(onClick = onDismiss) { Text(if (readOnly) "Close" else "Cancel") }
            }
        }
    )
}

@Composable
private fun SkillField(value: String, onChange: (String) -> Unit, label: String, enabled: Boolean, singleLine: Boolean = false, minLines: Int = 1) {
    TextField(
        value = value, onValueChange = onChange, enabled = enabled, singleLine = singleLine, minLines = minLines,
        placeholder = { Text(label, color = AssistantTextMuted, fontSize = 14.sp) },
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = AssistantFieldBackground, unfocusedContainerColor = AssistantFieldBackground,
            disabledContainerColor = AssistantFieldBackground, focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent,
            cursorColor = AssistantAccent, focusedTextColor = AssistantTextPrimary, unfocusedTextColor = AssistantTextPrimary,
            disabledTextColor = AssistantTextSecondary
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
