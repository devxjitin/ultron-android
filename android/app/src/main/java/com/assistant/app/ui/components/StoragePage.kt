package com.assistant.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cached
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.app.ui.theme.AlertDialog
import com.assistant.app.ui.theme.AssistantError
import com.assistant.app.ui.theme.AssistantOutline
import com.assistant.app.ui.theme.AssistantTextMuted
import com.assistant.app.ui.theme.AssistantTextPrimary
import com.assistant.app.ui.theme.AssistantTextSecondary

/** One kind of data kept on the phone. [bytes] is null when a size makes no sense for it. */
private class StorageItem(
    val icon: ImageVector,
    val title: String,
    val value: String,
    val detail: String,
    val bytes: Long?,
    val clearTitle: String? = null,
    val clearText: String = "",
    val onClear: (() -> Unit)? = null
)

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
    onClearPastConversations: () -> Unit = {},
    memoryBytes: Long = 0L,
    knowledgeBytes: Long = 0L,
    imageCount: Int = 0,
    imageBytes: Long = 0L,
    onClearImages: () -> Unit = {}
) {
    var pending by remember { mutableStateOf<StorageItem?>(null) }
    pending?.let { item ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(item.clearTitle.orEmpty()) },
            text = { Text(item.clearText) },
            confirmButton = { TextButton(onClick = { pending = null; item.onClear?.invoke() }) { Text("Clear", color = AssistantError) } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } }
        )
    }

    val items = listOf(
        StorageItem(
            Icons.Outlined.Psychology, "Memory",
            "$memoryCount ${if (memoryCount == 1) "fact" else "facts"}", "What the assistant knows about you.", memoryBytes,
            "Clear memory?", "Everything it remembers about you will be forgotten.", if (memoryCount > 0) onClearMemory else null
        ),
        StorageItem(
            Icons.Outlined.History, "Past conversations",
            if (pastConversationCount == 0) "None" else "$pastConversationCount saved",
            "Everything you and the assistant said before.", pastConversationBytes,
            "Clear past conversations?", "The assistant will no longer be able to look up anything said before.",
            if (pastConversationCount > 0) onClearPastConversations else null
        ),
        StorageItem(
            AssistantIcons.Knowledge, "Knowledge documents",
            "$knowledgeCount ${if (knowledgeCount == 1) "document" else "documents"}",
            "Your own documents. Add or remove them in Knowledge.", knowledgeBytes
        ),
        StorageItem(
            Icons.Outlined.Image, "Images",
            if (imageCount == 0) "None" else "$imageCount ${if (imageCount == 1) "picture" else "pictures"}",
            "Pictures you added for the assistant to show.", imageBytes,
            "Clear all pictures?", "Every picture you added will be deleted.", if (imageCount > 0) onClearImages else null
        ),
        StorageItem(
            Icons.Outlined.Cached, "Cache",
            if (cacheBytes > 0) "Temporary files" else "Empty", "Temporary files the app can rebuild.", cacheBytes,
            "Clear cache?", "Temporary files will be deleted.", if (cacheBytes > 0) onClearCache else null
        ),
        StorageItem(
            Icons.Outlined.Insights, "Usage statistics",
            if (hasUsage) "Recorded" else "Empty", "The numbers on the Usage page.", null,
            "Reset usage statistics?", "Usage will start again from zero.", if (hasUsage) onResetUsage else null
        )
    )
    val total = items.sumOf { it.bytes ?: 0L }

    PageColumn(modifier) {
        // One number: how much room Ultron takes on this phone. No bars, since nothing here has a limit to fill.
        PageCard {
            Text("Used by Ultron", color = AssistantTextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(formatBytes(total), color = AssistantTextPrimary, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("Everything below is stored only on this phone.", color = AssistantTextMuted, fontSize = 13.sp, lineHeight = 18.sp)
        }
        PageLabel("On this phone")
        PageCard {
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp).height(1.dp).background(AssistantOutline))
                }
                StorageRow(item, onClear = { pending = item })
            }
        }
        Text(
            "Clearing cannot be undone.",
            color = AssistantTextMuted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 12.dp, start = 4.dp)
        )
    }
}

@Composable
private fun StorageRow(item: StorageItem, onClear: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        NeutralIcon(item.icon)
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, color = AssistantTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            val size = item.bytes?.takeIf { it > 0 }?.let { " · ${formatBytes(it)}" }.orEmpty()
            Text(item.value + size, color = AssistantTextSecondary, fontSize = 14.sp)
            Text(item.detail, color = AssistantTextMuted, fontSize = 12.sp, lineHeight = 16.sp)
        }
        if (item.clearTitle != null) {
            val enabled = item.onClear != null
            TextButton(onClick = onClear, enabled = enabled) {
                Text("Clear", color = if (enabled) AssistantError else AssistantTextMuted)
            }
        }
    }
}
