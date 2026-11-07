package com.assistant.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cached
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.app.ui.theme.AlertDialog
import com.assistant.app.ui.theme.AssistantAccent
import com.assistant.app.ui.theme.AssistantAmber
import com.assistant.app.ui.theme.AssistantError
import com.assistant.app.ui.theme.AssistantFieldBackground
import com.assistant.app.ui.theme.AssistantPrimaryLight
import com.assistant.app.ui.theme.AssistantTextMuted
import com.assistant.app.ui.theme.AssistantTextPrimary
import com.assistant.app.ui.theme.AssistantTextSecondary

/** One kind of data kept on the phone, shown as its own card. [bytes] is null when a size makes no sense for it. */
private class StorageItem(
    val icon: ImageVector,
    val tint: Color,
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
    knowledgeBytes: Long = 0L
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
            Icons.Outlined.Psychology, AssistantAccent, "Memory",
            "$memoryCount ${if (memoryCount == 1) "fact" else "facts"}", "What the assistant knows about you.", memoryBytes,
            "Clear memory?", "Everything it remembers about you will be forgotten.", if (memoryCount > 0) onClearMemory else null
        ),
        StorageItem(
            Icons.Outlined.History, AssistantPrimaryLight, "Past conversations",
            if (pastConversationCount == 0) "None" else "$pastConversationCount saved",
            "Everything you and the assistant said before.", pastConversationBytes,
            "Clear past conversations?", "The assistant will no longer be able to look up anything said before.",
            if (pastConversationCount > 0) onClearPastConversations else null
        ),
        StorageItem(
            AssistantIcons.Knowledge, AssistantAmber, "Knowledge documents",
            "$knowledgeCount ${if (knowledgeCount == 1) "document" else "documents"}",
            "Your own documents. Add or remove them in Knowledge.", knowledgeBytes
        ),
        StorageItem(
            Icons.Outlined.Cached, AssistantTextSecondary, "Cache",
            formatBytes(cacheBytes), "Temporary files the app can rebuild.", cacheBytes,
            "Clear cache?", "Temporary files will be deleted.", if (cacheBytes > 0) onClearCache else null
        ),
        StorageItem(
            Icons.Outlined.Insights, AssistantError, "Usage statistics",
            if (hasUsage) "Recorded" else "Empty", "The numbers on the Usage page.", null,
            "Reset usage statistics?", "Usage will start again from zero.", if (hasUsage) onResetUsage else null
        )
    )
    val total = items.sumOf { it.bytes ?: 0L }.coerceAtLeast(1L)

    PageColumn(modifier) {
        PageLabel("On this phone")
        items.forEach { item ->
            StorageCard(item, share = item.bytes?.let { it.toFloat() / total }, onClear = { pending = item })
            Spacer(Modifier.height(12.dp))
        }
        Text(
            "Clearing cannot be undone.",
            color = AssistantTextMuted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp, start = 4.dp)
        )
    }
}

@Composable
private fun StorageCard(item: StorageItem, share: Float?, onClear: () -> Unit) {
    PageCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(item.tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(item.icon, contentDescription = null, tint = item.tint, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, color = AssistantTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(item.detail, color = AssistantTextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(item.value, color = AssistantTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            if (item.bytes != null && item.title != "Cache") {
                Text(formatBytes(item.bytes), color = AssistantTextSecondary, fontSize = 14.sp, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
        if (share != null) {
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(AssistantFieldBackground)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(share.coerceIn(0.02f, 1f)).background(item.tint, RoundedCornerShape(3.dp)))
            }
        }
        if (item.clearTitle != null) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
                val enabled = item.onClear != null
                TextButton(onClick = onClear, enabled = enabled) {
                    Text("Clear", color = if (enabled) AssistantError else AssistantTextMuted)
                }
            }
        }
    }
}
