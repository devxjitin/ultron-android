package com.assistant.app.ui.components

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.app.data.MAX_SKILL_BODY_CHARS
import com.assistant.app.data.Skill
import com.assistant.app.data.SkillEntry
import com.assistant.app.data.SkillParser
import com.assistant.app.data.draftFromMarkdown
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
 * Built-in skills ship with the app: they can be switched off and read, but not edited or deleted, and the tasks inside a
 * built-in skill have no switches of their own. Your own skills can be added, switched off, edited and deleted.
 * [onSave] returns an error to show, or null when saved.
 */
@Composable
fun SkillsPage(
    skills: List<SkillEntry>,
    onToggle: (String, Boolean) -> Unit,
    onSave: (name: String, description: String, body: String) -> String?,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var openName by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<SkillEntry?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SkillEntry?>(null) }
    var viewing by remember { mutableStateOf<Skill?>(null) }

    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        Text(
            "A skill is a set of instructions for one kind of task. The assistant only sees a skill's name and description, and opens the full instructions only when your request needs it. " +
                "Built-in skills can be switched off but not edited or deleted; add your own skills below.",
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
            val group = entry.group
            val shape = RoundedCornerShape(16.dp)
            Column(
                Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(shape).background(AssistantSurface)
                    .border(BorderStroke(1.dp, AssistantOutline), shape)
            ) {
                Row(
                    Modifier.fillMaxWidth()
                        .clickable {
                            when {
                                group.tasks.isNotEmpty() -> openName = if (openName == group.name) null else group.name
                                entry.builtIn -> viewing = Skill(group.name, group.description, group.body)
                                else -> editing = entry
                            }
                        }
                        .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            group.name + if (entry.locked) "  ·  locked" else if (entry.builtIn) "  ·  built-in" else "",
                            color = AssistantTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                        )
                        Text(group.description, color = AssistantTextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                        if (group.tasks.isNotEmpty()) {
                            Text("${group.tasks.size} task" + if (group.tasks.size == 1) "" else "s", color = AssistantTextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                    if (!entry.locked) {
                        Spacer(Modifier.size(10.dp))
                        Switch(
                            checked = entry.enabled,
                            onCheckedChange = { onToggle(group.name, it) },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = AssistantAccentFill, checkedThumbColor = Color.White,
                                uncheckedTrackColor = AssistantFieldBackground, uncheckedBorderColor = AssistantOutline,
                                uncheckedThumbColor = AssistantTextMuted
                            )
                        )
                    }
                }
                if (openName == group.name) {
                    // Tasks are part of their built-in skill: tap one to read it, there is nothing to switch.
                    group.tasks.forEach { task ->
                        Column(
                            Modifier.fillMaxWidth().background(AssistantFieldBackground).clickable { viewing = task }
                                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 12.dp)
                        ) {
                            Text(task.name, color = AssistantTextPrimary, fontSize = 14.sp)
                            Text(task.description, color = AssistantTextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (adding) {
        SkillEditor(null, onSave, onDismiss = { adding = false }, onDelete = null)
    }
    editing?.let { entry ->
        SkillEditor(entry, onSave, onDismiss = { editing = null }, onDelete = { deleting = entry; editing = null })
    }
    viewing?.let { skill ->
        AlertDialog(
            onDismissRequest = { viewing = null },
            title = { Text(skill.name) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(skill.description, color = AssistantTextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(skill.body, color = AssistantTextPrimary, fontSize = 13.sp, lineHeight = 19.sp)
                }
            },
            confirmButton = { TextButton(onClick = { viewing = null }) { Text("Close") } }
        )
    }
    deleting?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this skill?") },
            text = { Text("\"${entry.group.name}\" will be removed for good.") },
            confirmButton = { TextButton(onClick = { onDelete(entry.group.name); deleting = null }) { Text("Delete", color = AssistantError) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

/** Add a skill ([entry] null) or edit one of your own. Built-in skills never reach this editor. */
@Composable
private fun SkillEditor(
    entry: SkillEntry?,
    onSave: (String, String, String) -> String?,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember(entry) { mutableStateOf(entry?.group?.name.orEmpty()) }
    var description by remember(entry) { mutableStateOf(entry?.group?.description.orEmpty()) }
    var body by remember(entry) { mutableStateOf(entry?.group?.body.orEmpty()) }
    var error by remember(entry) { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val read = runCatching {
            val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && i >= 0) c.getString(i) else null
            }.orEmpty()
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            fileName to text
        }.getOrNull()
        if (read == null || read.second.isBlank()) {
            error = "Could not read that file. Upload a .md or .txt file with text in it."
        } else {
            val draft = SkillParser.draftFromMarkdown(read.second, read.first)
            name = draft.name; description = draft.description; body = draft.instruction
            error = null
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (entry == null) "New skill" else "Edit skill") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = { picker.launch(arrayOf("text/markdown", "text/plain", "text/x-markdown", "application/octet-stream")) }) {
                    Text("Upload .md file")
                }
                SkillField(name, { name = it.take(48) }, "Name", "e.g. trip-planner", enabled = entry == null, singleLine = true)
                SkillField(description, { description = it.take(300) }, "Description", "when to use it, one line")
                SkillField(body, { body = it.take(MAX_SKILL_BODY_CHARS) }, "Instruction", "what to do, step by step", minLines = 6)
                error?.let { Text(it, color = AssistantError, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = onSave(name, description, body)
                if (error == null) onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                onDelete?.let { TextButton(onClick = it) { Text("Delete", color = AssistantError) } }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun SkillField(value: String, onChange: (String) -> Unit, label: String, hint: String, enabled: Boolean = true, singleLine: Boolean = false, minLines: Int = 1) {
    TextField(
        value = value, onValueChange = onChange, enabled = enabled, singleLine = singleLine, minLines = minLines,
        label = { Text(label, color = AssistantTextMuted, fontSize = 13.sp) },
        placeholder = { Text(hint, color = AssistantTextMuted, fontSize = 14.sp) },
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
