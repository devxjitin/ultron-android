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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.app.data.SessionGroup
import com.assistant.app.data.StoredTurn
import com.assistant.app.data.filterSessions
import com.assistant.app.data.groupBySession
import com.assistant.app.ui.theme.CardShape
import com.assistant.app.ui.theme.AlertDialog
import com.assistant.app.ui.theme.AssistantAccent
import com.assistant.app.ui.theme.AssistantError
import com.assistant.app.ui.theme.AssistantFieldBackground
import com.assistant.app.ui.theme.AssistantOutline
import com.assistant.app.ui.theme.AssistantSurface
import com.assistant.app.ui.theme.AssistantTextMuted
import com.assistant.app.ui.theme.AssistantTextPrimary
import com.assistant.app.ui.theme.AssistantTextSecondary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

/**
 * Everything said to the assistant, one card per session and newest first. Tap a card to read the whole chat.
 * This is the same history the assistant searches when you ask what was said before.
 */
@Composable
fun HistoryPage(
    turns: List<StoredTurn>,
    total: Int,
    onDeleteSession: (List<Long>) -> Unit,
    onClearAll: () -> Unit,
    onShowOlder: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var openId by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<SessionGroup?>(null) }
    var confirmingClear by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val sessions = filterSessions(groupBySession(turns), query)

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        if (total == 0) {
            Text(
                "No conversations yet. What you say to the assistant will show up here.",
                color = AssistantTextMuted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(vertical = 24.dp, horizontal = 4.dp)
            )
            return@Column
        }
        TextField(
            value = query,
            onValueChange = { query = it.take(80) },
            placeholder = { Text("Search conversations", color = AssistantTextMuted, fontSize = 14.sp, maxLines = 1) },
            singleLine = true,
            shape = CardShape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = AssistantFieldBackground, unfocusedContainerColor = AssistantFieldBackground,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                cursorColor = AssistantAccent, focusedTextColor = AssistantTextPrimary, unfocusedTextColor = AssistantTextPrimary
            ),
            modifier = Modifier.fillMaxWidth().border(BorderStroke(1.dp, AssistantOutline), CardShape)
        )
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            PageLabel("Sessions (${sessions.size})", Modifier.weight(1f), top = 0.dp, bottom = 0.dp)
            TextButton(onClick = { confirmingClear = true }) { Text("Clear all", color = AssistantError, fontSize = 13.sp) }
        }
        if (sessions.isEmpty()) {
            Text("Nothing matches that.", color = AssistantTextMuted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 16.dp, horizontal = 4.dp))
        }
        var lastDay: java.time.LocalDate? = null
        sessions.forEach { session ->
            val day = Instant.ofEpochMilli(session.startMillis).atZone(zone).toLocalDate()
            if (day != lastDay) {
                lastDay = day
                Text(
                    dayFormatter.format(day), color = AssistantTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp)
                )
            }
            SessionCard(
                session = session,
                open = openId == session.sessionId || query.isNotBlank() && sessions.size == 1,
                onToggle = { openId = if (openId == session.sessionId) null else session.sessionId },
                onDelete = { deleting = session }
            )
        }
        if (total > turns.size) {
            TextButton(onClick = onShowOlder, modifier = Modifier.padding(top = 4.dp)) { Text("Show older") }
        }
        Spacer(Modifier.height(24.dp))
    }

    deleting?.let { session ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this session?") },
            text = { Text("The ${session.turns.size} exchanges in it will be deleted. The assistant can no longer look them up.") },
            confirmButton = {
                TextButton(onClick = { onDeleteSession(session.turns.map { it.id }); deleting = null }) { Text("Delete", color = AssistantError) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
    if (confirmingClear) {
        AlertDialog(
            onDismissRequest = { confirmingClear = false },
            title = { Text("Clear all history?") },
            text = { Text("The assistant will no longer be able to look up anything said before. This cannot be undone.") },
            confirmButton = { TextButton(onClick = { onClearAll(); confirmingClear = false }) { Text("Clear all", color = AssistantError) } },
            dismissButton = { TextButton(onClick = { confirmingClear = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SessionCard(session: SessionGroup, open: Boolean, onToggle: () -> Unit, onDelete: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val shape = CardShape
    val start = timeFormatter.format(Instant.ofEpochMilli(session.startMillis).atZone(zone))
    val end = timeFormatter.format(Instant.ofEpochMilli(session.endMillis).atZone(zone))
    val range = if (start == end) start else "$start – $end"
    val count = session.turns.size
    Column(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(shape).background(AssistantSurface)
            .border(BorderStroke(1.dp, AssistantOutline), shape).clickable(onClick = onToggle).padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("$range · $count ${if (count == 1) "exchange" else "exchanges"}", color = AssistantTextMuted, fontSize = 11.sp)
                Text(
                    session.title.ifBlank { "(no words)" }, color = AssistantTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    lineHeight = 20.sp, maxLines = if (open) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).clickable { onDelete() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete this session", tint = AssistantTextMuted, modifier = Modifier.size(18.dp))
            }
        }
        if (open) {
            Column(Modifier.padding(top = 10.dp, end = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                session.turns.forEach { turn ->
                    val time = timeFormatter.format(Instant.ofEpochMilli(turn.timestampMillis).atZone(zone))
                    Column {
                        Text(time, color = AssistantTextMuted, fontSize = 11.sp)
                        if (turn.userText.isNotBlank()) {
                            Text("You", color = AssistantAccent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
                            Text(turn.userText, color = AssistantTextPrimary, fontSize = 14.sp, lineHeight = 20.sp)
                        }
                        if (turn.assistantText.isNotBlank()) {
                            Text("Assistant", color = AssistantTextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                            Text(turn.assistantText, color = AssistantTextSecondary, fontSize = 14.sp, lineHeight = 20.sp)
                        }
                    }
                }
            }
        }
    }
}
