package com.assistant.app.ui.components

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.IconButton
import androidx.compose.ui.unit.Dp
import com.assistant.app.data.OWN_SKILLS_CATEGORY
import com.assistant.app.data.displaySummary
import com.assistant.app.ui.theme.AssistantSelectedBg
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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
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
import com.assistant.app.data.MAX_SKILL_FILE_BYTES
import com.assistant.app.data.readAtMost
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
 * The assistant's skills: instruction sets it loads only when a request needs one. Each skill has its own details page,
 * which shows its instructions as formatted Markdown. Every skill can be switched off except the one the assistant needs to
 * describe itself; only your own skills can be added, edited and deleted. Skills are filed under category buttons.
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
    var detailName by rememberSaveable { mutableStateOf<String?>(null) }
    var categoryFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<SkillEntry?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SkillEntry?>(null) }

    val detail = detailName?.let { name -> skills.firstOrNull { it.group.name == name } }
    BackHandler(enabled = detail != null) { detailName = null }

    if (detail != null) {
        SkillDetail(
            entry = detail,
            onToggle = { onToggle(detail.group.name, it) },
            onEdit = { editing = detail },
            onBack = { detailName = null },
            modifier = modifier
        )
    } else {
        val categories = skills.map { it.category }.distinct().sortedWith(compareBy({ it == OWN_SKILLS_CATEGORY }, { it }))
        Column(modifier.fillMaxWidth().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(50))
                    .background(com.assistant.app.ui.theme.AssistantButtonFill)
                    .clickable { adding = true }.padding(vertical = 14.dp, horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = com.assistant.app.ui.theme.AssistantOnButtonFill, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text("Add a skill", color = com.assistant.app.ui.theme.AssistantOnButtonFill, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(14.dp))
            CategoryChips(categories, categoryFilter, { categoryFilter = it })
            if (skills.isEmpty()) {
                Text("No skills yet.", color = AssistantTextMuted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
            }
            categories.filter { categoryFilter == null || it == categoryFilter }.forEach { category ->
                PageLabel(category)
                skills.filter { it.category == category }.sortedBy { it.group.name }.forEach { entry ->
                    SkillCard(
                        entry = entry,
                        onToggle = { onToggle(entry.group.name, it) },
                        onEdit = { editing = entry },
                        onOpen = { detailName = entry.group.name }
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (adding) {
        SkillEditor(null, onSave, onDismiss = { adding = false }, onDelete = null)
    }
    editing?.let { entry ->
        SkillEditor(entry, onSave, onDismiss = { editing = null }, onDelete = { deleting = entry; editing = null })
    }
    deleting?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this skill?") },
            text = { Text("\"${entry.group.name}\" will be removed for good.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(entry.group.name); deleting = null
                    if (detailName == entry.group.name) detailName = null
                }) { Text("Delete", color = AssistantError) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SkillSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = assistantSwitchColors()
    )
}

/** One skill in the list: name, description, a switch (not for the one that always stays on) and Edit for your own. */
@Composable
private fun SkillCard(entry: SkillEntry, onToggle: (Boolean) -> Unit, onEdit: () -> Unit, onOpen: () -> Unit) {
    val shape = com.assistant.app.ui.theme.CardShape
    Column(
        Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(shape).clickable(onClick = onOpen)
            .background(AssistantSurface).border(BorderStroke(1.dp, AssistantOutline), shape).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SkillIcon(entry, 44.dp, 22.dp)
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.group.name, color = AssistantTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp)
                Text(entry.category, color = AssistantTextMuted, fontSize = 13.sp)
            }
            if (!entry.locked) {
                Spacer(Modifier.size(8.dp))
                SkillSwitch(entry.enabled, onToggle)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(entry.group.displaySummary, color = AssistantTextSecondary, fontSize = 15.sp, lineHeight = 21.sp)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!entry.builtIn) {
                TextButton(onClick = onEdit) { Text("Edit", color = AssistantAccent, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
            }
            Spacer(Modifier.weight(1f))
            Text("View details", color = AssistantTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = AssistantTextSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SkillIcon(entry: SkillEntry, box: Dp, icon: Dp) {
    NeutralIcon(skillIcon(entry.group.name, entry.category, entry.group.description), box = box, size = icon)
}

/** A skill's own page: what it is for, its instructions shown as formatted Markdown, and what it can do. Only your own skills can be edited. */
@Composable
private fun SkillDetail(entry: SkillEntry, onToggle: (Boolean) -> Unit, onEdit: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val group = entry.group
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AssistantTextPrimary)
            }
            Text("Skill details", color = AssistantTextSecondary, fontSize = 14.sp)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SkillIcon(entry, 56.dp, 28.dp)
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(group.name, color = AssistantTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp)
                    Text(entry.category, color = AssistantTextMuted, fontSize = 13.sp)
                }
                if (!entry.locked) SkillSwitch(entry.enabled, onToggle)
            }
            if (!entry.builtIn) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onEdit) { Text("Edit", color = AssistantAccent, fontWeight = FontWeight.Medium) }
                }
            }
            PageLabel("About")
            Text(group.displaySummary, color = AssistantTextSecondary, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
            if (group.body.isNotBlank()) {
                PageLabel("Instructions")
                PageCard { MarkdownText(group.body) }
            }
            if (group.tasks.isNotEmpty()) {
                PageLabel("What it can do (${group.tasks.size})")
                group.tasks.forEach { task -> SkillTaskCard(task) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** One task of a skill: its name and summary, tap to read its instructions as formatted Markdown. */
@Composable
private fun SkillTaskCard(task: com.assistant.app.data.Skill) {
    var open by rememberSaveable(task.name) { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(shape).background(AssistantSurface)
            .border(BorderStroke(1.dp, AssistantOutline), shape).clickable { open = !open }.padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(task.name.replace('_', ' ').replaceFirstChar { it.uppercase() }, color = AssistantTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(task.displaySummary(), color = AssistantTextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
            }
            if (task.body.isNotBlank()) {
                Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (open) "Hide" else "Show instructions", tint = AssistantTextMuted)
            }
        }
        if (open && task.body.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            MarkdownText(task.body)
        }
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
    val scope = rememberCoroutineScope()
    var reading by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        reading = true
        error = null
        scope.launch {
            // The provider may be a slow cloud stream, so the read stays off the UI thread and is size-capped.
            val read = withContext(Dispatchers.IO) {
                runCatching {
                    val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                        val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (c.moveToFirst() && i >= 0) c.getString(i) else null
                    }.orEmpty()
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readAtMost(MAX_SKILL_FILE_BYTES) }
                    bytes?.let { fileName to it.toString(Charsets.UTF_8) }
                }.getOrNull()
            }
            reading = false
            if (read == null || read.second.isBlank()) {
                error = "Could not read that file. Use a .md or .txt file under 256 KB."
            } else {
                val draft = SkillParser.draftFromMarkdown(read.second, read.first)
                name = draft.name; description = draft.description; body = draft.instruction
                error = null
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (entry == null) "New skill" else "Edit skill") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(
                    enabled = !reading,
                    onClick = { picker.launch(arrayOf("text/markdown", "text/plain", "text/x-markdown", "application/octet-stream")) }
                ) {
                    Text(if (reading) "Reading file…" else "Upload .md file")
                }
                SkillField(name, { name = it.take(48) }, "Name", "e.g. trip-planner", enabled = entry == null, singleLine = true)
                SkillField(description, { description = it.take(300) }, "Description", "What it is for")
                SkillField(body, { body = it.take(MAX_SKILL_BODY_CHARS) }, "Instruction", "What the assistant should do", minLines = 6)
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
