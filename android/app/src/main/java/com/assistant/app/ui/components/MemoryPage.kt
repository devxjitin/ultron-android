package com.assistant.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
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
import com.assistant.app.data.MAX_MEMORY_TEXT_CHARS
import com.assistant.app.data.MemoryItem
import com.assistant.app.ui.theme.AssistantAccent
import com.assistant.app.ui.theme.AssistantAccentFill
import com.assistant.app.ui.theme.AssistantAccentPink
import com.assistant.app.ui.theme.AssistantError
import com.assistant.app.ui.theme.AssistantFieldBackground
import com.assistant.app.ui.theme.AssistantOutline
import com.assistant.app.ui.theme.AssistantSurface
import com.assistant.app.ui.theme.AssistantTextMuted
import com.assistant.app.ui.theme.AssistantTextPrimary
import com.assistant.app.ui.theme.AssistantTextSecondary

/** What the assistant remembers about the user: switch it on or off, add, edit and delete facts. */
@Composable
fun MemoryPage(
    memories: List<MemoryItem>,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onAdd: (String) -> Unit,
    onUpdate: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    var draft by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<MemoryItem?>(null) }
    var confirmingClear by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(22.dp)

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth().clip(cardShape).background(AssistantSurface)
                .border(BorderStroke(1.dp, AssistantOutline), cardShape).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Remember things about me", color = AssistantTextPrimary, fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Spacer(Modifier.size(12.dp))
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = AssistantAccentFill, checkedThumbColor = Color.White,
                    uncheckedTrackColor = AssistantFieldBackground, uncheckedBorderColor = AssistantOutline,
                    uncheckedThumbColor = AssistantTextMuted
                )
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = draft,
                onValueChange = { draft = it.take(MAX_MEMORY_TEXT_CHARS) },
                placeholder = { Text("Add something to remember", color = AssistantTextMuted, fontSize = 14.sp, maxLines = 1) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = fieldColors(),
                modifier = Modifier.weight(1f).border(BorderStroke(1.dp, AssistantOutline), RoundedCornerShape(18.dp))
            )
            Spacer(Modifier.size(10.dp))
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(AssistantAccentFill, AssistantAccentPink)))
                    .clickable(enabled = draft.isNotBlank()) { onAdd(draft); draft = "" },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add memory", tint = Color.White)
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "SAVED · ${memories.size}", color = AssistantTextMuted, fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
            if (memories.isNotEmpty()) {
                TextButton(onClick = { confirmingClear = true }) { Text("Clear all", color = AssistantError, fontSize = 13.sp) }
            }
        }
        if (memories.isEmpty()) {
            Text(
                "Nothing saved yet.",
                color = AssistantTextMuted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 16.dp, horizontal = 4.dp)
            )
        }
        memories.asReversed().forEach { item ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(16.dp)).background(AssistantSurface)
                    .border(BorderStroke(1.dp, AssistantOutline), RoundedCornerShape(16.dp))
                    .clickable { editing = item }.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(item.text, color = AssistantTextPrimary, fontSize = 14.sp, lineHeight = 20.sp,
                    modifier = Modifier.weight(1f).padding(vertical = 10.dp))
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).clickable { onDelete(item.id) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, contentDescription = "Forget this", tint = AssistantTextMuted, modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    editing?.let { item ->
        var text by remember(item.id) { mutableStateOf(item.text) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit memory") },
            text = {
                TextField(
                    value = text, onValueChange = { text = it.take(MAX_MEMORY_TEXT_CHARS) },
                    shape = RoundedCornerShape(16.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = { onUpdate(item.id, text); editing = null }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
        )
    }

    if (confirmingClear) {
        AlertDialog(
            onDismissRequest = { confirmingClear = false },
            title = { Text("Clear all memory?") },
            text = { Text("Your assistant will forget everything it saved about you.") },
            confirmButton = { TextButton(onClick = { onClearAll(); confirmingClear = false }) { Text("Clear all", color = AssistantError) } },
            dismissButton = { TextButton(onClick = { confirmingClear = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun fieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = AssistantFieldBackground,
    unfocusedContainerColor = AssistantFieldBackground,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    cursorColor = AssistantAccent,
    focusedTextColor = AssistantTextPrimary,
    unfocusedTextColor = AssistantTextPrimary
)
