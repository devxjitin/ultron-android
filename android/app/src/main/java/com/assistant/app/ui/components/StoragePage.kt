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
    modifier: Modifier = Modifier
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
            PageRow("Knowledge documents", knowledgeCount.toString())
            PageRow("Cache", formatBytes(cacheBytes))
        }

        PageLabel("Clear data")
        PageCard {
            ClearRow("Cache", "Temporary files such as downloaded updates and previews.", enabled = cacheBytes > 0) {
                pending = Triple("Clear cache?", "Temporary files will be deleted. Nothing you saved is affected.", onClearCache)
            }
            Separator()
            ClearRow("Memory", "Everything the assistant remembers about you.", enabled = memoryCount > 0) {
                pending = Triple("Clear memory?", "Everything the assistant remembers about you will be forgotten.", onClearMemory)
            }
            Separator()
            ClearRow("Usage statistics", "The counters shown on the Usage page.", enabled = hasUsage) {
                pending = Triple("Reset usage statistics?", "The usage counters will start again from zero.", onResetUsage)
            }
        }
        Text(
            "Clearing cannot be undone. Settings, connectors and knowledge documents are never touched here.",
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
