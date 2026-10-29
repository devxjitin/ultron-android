package com.assistant.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.app.data.SkillEntry
import com.assistant.app.ui.theme.AlertDialog
import com.assistant.app.ui.theme.AssistantOutline
import com.assistant.app.ui.theme.AssistantSurface
import com.assistant.app.ui.theme.AssistantTextMuted
import com.assistant.app.ui.theme.AssistantTextPrimary
import com.assistant.app.ui.theme.AssistantTextSecondary

/**
 * The assistant's skills: instruction sets it loads only when a request needs one. They ship with the app and are locked,
 * so this page only lets you read them.
 */
@Composable
fun SkillsPage(skills: List<SkillEntry>, modifier: Modifier = Modifier) {
    var viewing by remember { mutableStateOf<SkillEntry?>(null) }

    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        Text(
            "A skill is a set of instructions for one kind of task, including a full list of what the assistant can do. " +
                "The assistant only sees each name and description, and opens the full instructions only when your request needs it. " +
                "Skills are locked: you can read them here, but they cannot be changed.",
            color = AssistantTextSecondary, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(horizontal = 4.dp)
        )
        PageLabel("Skills (${skills.size})")
        if (skills.isEmpty()) {
            Text("No skills.", color = AssistantTextMuted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
        }
        skills.forEach { entry ->
            val shape = RoundedCornerShape(16.dp)
            Column(
                Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(shape).background(AssistantSurface)
                    .border(BorderStroke(1.dp, AssistantOutline), shape)
                    .clickable { viewing = entry }.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 16.dp)
            ) {
                Text(entry.skill.name + "  ·  locked", color = AssistantTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(entry.skill.description, color = AssistantTextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    viewing?.let { entry ->
        AlertDialog(
            onDismissRequest = { viewing = null },
            title = { Text(entry.skill.name) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(entry.skill.description, color = AssistantTextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(entry.skill.body, color = AssistantTextPrimary, fontSize = 13.sp, lineHeight = 19.sp)
                }
            },
            confirmButton = { TextButton(onClick = { viewing = null }) { Text("Close") } }
        )
    }
}
