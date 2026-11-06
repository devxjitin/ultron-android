package com.assistant.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.assistant.app.ui.theme.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.app.ui.theme.AssistantError
import com.assistant.app.ui.theme.AssistantOutline
import com.assistant.app.ui.theme.AssistantTextMuted
import com.assistant.app.ui.theme.AssistantTextPrimary
import com.assistant.app.ui.theme.AssistantTextSecondary

/** What is stored on the phone, and the buttons that clear it. */
@Composable
fun StoragePage(
    memoryCount: Int,
    knowledgeCount: Int,
    cacheBytes: Long,
    hasUsage: Boolean,
    onClearCache: () -> Unit,
    onClearMemory: () -> Unit,
    onResetUsage: () -> Unit,
    modifier: Modifier = Modifier,
    pastConversationCount: Int = 0,
    pastConversationBytes: Long = 0L,
    onClearPastConversations: () -> Unit = {}
) {
    var pending by remember { mutableStateOf<Triple<String, String, () -> Unit>?>(null) }
    pending?.let { (title, text, action) ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(title) },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { pending = null; action() }) { Text("Clear", color = AssistantError) } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } }
        )
    }

    PageColumn(modifier) {
        PageLabel("On this phone")
        PageCard {
            PageRow("Memory", "$memoryCount ${if (memoryCount == 1) "fact" else "facts"}")
            PageRow(
                "Past conversations",
                if (pastConversationCount == 0) "None" else
                    "$pastConversationCount ${if (pastConversationCount == 1) "exchange" else "exchanges"}, about ${formatBytes(pastConversationBytes)}"
            )
            PageRow("Knowledge documents", knowledgeCount.toString())
            PageRow("Cache", formatBytes(cacheBytes))
        }

        PageLabel("Clear data")
        PageCard {
            ClearRow("Cache", "Temporary files.", enabled = cacheBytes > 0) {
                pending = Triple("Clear cache?", "Temporary files will be deleted.", onClearCache)
            }
            Separator()
            ClearRow("Memory", "What the assistant remembers.", enabled = memoryCount > 0) {
                pending = Triple("Clear memory?", "Everything it remembers will be forgotten.", onClearMemory)
            }
            Separator()
            ClearRow("Past conversations", "Everything you and the assistant said before.", enabled = pastConversationCount > 0) {
                pending = Triple(
                    "Clear past conversations?",
                    "The assistant will no longer be able to look up anything said before.",
                    onClearPastConversations
                )
            }
            Separator()
            ClearRow("Usage statistics", "The numbers on the Usage page.", enabled = hasUsage) {
                pending = Triple("Reset usage statistics?", "Usage will start again from zero.", onResetUsage)
            }
        }
        Text(
            "This cannot be undone.",
            color = AssistantTextMuted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 12.dp, start = 4.dp)
        )
    }
}

@Composable
private fun Separator() {
    Spacer(Modifier.height(10.dp))
    HorizontalDivider(color = AssistantOutline)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun ClearRow(title: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = AssistantTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(description, color = AssistantTextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Spacer(Modifier.size(8.dp))
        TextButton(onClick = onClick, enabled = enabled) { Text("Clear", color = if (enabled) AssistantError else AssistantTextMuted) }
    }
}
