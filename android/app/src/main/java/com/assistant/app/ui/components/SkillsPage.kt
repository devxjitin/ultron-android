package com.assistant.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.app.data.Skill
import com.assistant.app.data.SkillEntry
import com.assistant.app.ui.theme.AlertDialog
import com.assistant.app.ui.theme.AssistantAccentFill
import com.assistant.app.ui.theme.AssistantFieldBackground
import com.assistant.app.ui.theme.AssistantOutline
import com.assistant.app.ui.theme.AssistantSurface
import com.assistant.app.ui.theme.AssistantTextMuted
import com.assistant.app.ui.theme.AssistantTextPrimary
import com.assistant.app.ui.theme.AssistantTextSecondary

/**
 * The assistant's built-in skills: instruction sets it loads only when a request needs one. Skills cannot be added or edited
 * here; each one can be switched off, and each task inside it allowed or disallowed. Tap a task to read its instructions.
 */
@Composable
fun SkillsPage(
    skills: List<SkillEntry>,
    onToggle: (skill: String, task: String?, enabled: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var openName by rememberSaveable { mutableStateOf<String?>(null) }
    var viewing by remember { mutableStateOf<Skill?>(null) }

    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        Text(
            "Skills are built-in instructions for specific tasks. The assistant only sees a skill's name until a request needs it, " +
                "then only the one task it needs. Switch a skill off, or open it to turn off single tasks, for example keep WhatsApp but not Send message.",
            color = AssistantTextSecondary, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(horizontal = 4.dp)
        )
        PageLabel("Skills (${skills.size})")
        if (skills.isEmpty()) {
            Text("No skills.", color = AssistantTextMuted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
        }
        skills.forEach { entry ->
            SkillCard(
                entry = entry,
                open = openName == entry.group.name,
                onOpen = {
                    val group = entry.group
                    // A skill without tasks has nothing to expand: show its instructions instead.
                    if (group.tasks.isEmpty()) viewing = Skill(group.name, group.description, group.body)
                    else openName = if (openName == group.name) null else group.name
                },
                onToggle = onToggle,
                onView = { viewing = it }
            )
        }
        Spacer(Modifier.height(24.dp))
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
}

@Composable
private fun SkillCard(
    entry: SkillEntry,
    open: Boolean,
    onOpen: () -> Unit,
    onToggle: (String, String?, Boolean) -> Unit,
    onView: (Skill) -> Unit
) {
    val group = entry.group
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(shape).background(AssistantSurface)
            .border(BorderStroke(1.dp, AssistantOutline), shape)
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    group.name + if (entry.locked) "  ·  locked" else "",
                    color = AssistantTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                )
                Text(group.description, color = AssistantTextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                if (group.tasks.isNotEmpty()) {
                    val allowed = group.tasks.count { it.name !in entry.disabledTasks }
                    Text(
                        if (entry.enabled) "$allowed of ${group.tasks.size} tasks allowed" else "Off",
                        color = AssistantTextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            if (!entry.locked) {
                Spacer(Modifier.size(10.dp))
                SkillSwitch(entry.enabled, enabled = true) { onToggle(group.name, null, it) }
            }
        }
        if (open) {
            group.tasks.forEach { task ->
                Row(
                    Modifier.fillMaxWidth().background(AssistantFieldBackground).clickable { onView(task) }
                        .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(task.name, color = AssistantTextPrimary, fontSize = 14.sp)
                        Text(task.description, color = AssistantTextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                    Spacer(Modifier.size(10.dp))
                    // Tasks of a switched-off skill stay hidden from the assistant, so their switches wait for the skill.
                    SkillSwitch(task.name !in entry.disabledTasks, enabled = entry.enabled) { onToggle(group.name, task.name, it) }
                }
            }
        }
    }
}

@Composable
private fun SkillSwitch(checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedTrackColor = AssistantAccentFill, checkedThumbColor = Color.White,
            uncheckedTrackColor = AssistantFieldBackground, uncheckedBorderColor = AssistantOutline,
            uncheckedThumbColor = AssistantTextMuted
        )
    )
}
