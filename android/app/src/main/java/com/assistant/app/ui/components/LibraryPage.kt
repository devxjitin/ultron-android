package com.assistant.app.ui.components

import android.graphics.Bitmap
import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import com.assistant.app.ui.theme.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.app.data.AttachmentRef
import com.assistant.app.data.LibraryCategory
import com.assistant.app.data.LibraryItem
import com.assistant.app.network.ChatAttachment
import com.assistant.app.ui.theme.AssistantAccent
import com.assistant.app.ui.theme.AssistantError
import com.assistant.app.ui.theme.AssistantOutline
import com.assistant.app.ui.theme.AssistantSelectedBg
import com.assistant.app.ui.theme.AssistantSurface
import com.assistant.app.ui.theme.AssistantTextMuted
import com.assistant.app.ui.theme.AssistantTextPrimary
import com.assistant.app.ui.theme.AssistantTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Library: every file you sent in a chat, sorted into Images, Videos, PDFs and Documents.
 * Tap to open, long-press for "open chat" or delete.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryPage(
    items: List<LibraryItem>,
    load: (AttachmentRef) -> ChatAttachment?,
    onOpenChat: (String) -> Unit,
    onDelete: (LibraryItem) -> Unit,
    onNotice: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var filterIndex by rememberSaveable { mutableIntStateOf(-1) }
    val filter = LibraryCategory.entries.getOrNull(filterIndex)
    var preview by remember { mutableStateOf<ChatAttachment?>(null) }
    var options by remember { mutableStateOf<LibraryItem?>(null) }
    var confirmDelete by remember { mutableStateOf<LibraryItem?>(null) }
    val scope = rememberCoroutineScope()
    val byCategory = remember(items) { items.groupBy { it.category } }

    val open: (LibraryItem) -> Unit = { item ->
        scope.launch {
            val file = withContext(Dispatchers.IO) { load(item.file) }
            if (file == null) onNotice("That file is no longer available.") else preview = file
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "filters") {
            ChoiceDropdown(
                options = listOf("all" to "All (${items.size})") +
                    LibraryCategory.entries.map { it.name to "${it.label} (${byCategory[it].orEmpty().size})" },
                selectedId = filter?.name ?: "all",
                onSelect = { id -> filterIndex = LibraryCategory.entries.firstOrNull { it.name == id }?.ordinal ?: -1 },
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        if (items.isEmpty() || (filter != null && byCategory[filter].isNullOrEmpty())) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                Text(
                    if (items.isEmpty()) "No files yet. Images, videos, PDFs and documents you send in a chat are kept here, sorted by type."
                    else "No ${filter?.label?.lowercase()} yet.",
                    color = AssistantTextMuted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(vertical = 24.dp, horizontal = 4.dp)
                )
            }
        }
        LibraryCategory.entries.filter { filter == null || it == filter }.forEach { category ->
            val list = byCategory[category].orEmpty()
            if (list.isEmpty()) return@forEach
            if (filter == null) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "header-${category.name}") {
                    PageLabel("${category.label} (${list.size})", Modifier.padding(top = 8.dp, bottom = 0.dp))
                }
            }
            if (category == LibraryCategory.IMAGES || category == LibraryCategory.VIDEOS) {
                items(list, key = { it.file.path }) { item ->
                    MediaTile(item, load, onClick = { open(item) }, onLongClick = { options = item })
                }
            } else {
                items(list, key = { it.file.path }, span = { GridItemSpan(maxLineSpan) }) { item ->
                    FileRow(item, onClick = { open(item) }, onLongClick = { options = item })
                }
            }
        }
    }

    preview?.let { AttachmentPreviewDialog(it) { preview = null } }

    options?.let { item ->
        AlertDialog(
            onDismissRequest = { options = null },
            title = { Text(item.file.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = { Text("Sent in \"${item.chatTitle}\", ${formatBytes(item.file.size.toLong())}") },
            confirmButton = {
                Row {
                    TextButton(onClick = { confirmDelete = item; options = null }) { Text("Delete", color = AssistantError) }
                    TextButton(onClick = { options = null; onOpenChat(item.sessionId) }) { Text("Open chat") }
                }
            },
            dismissButton = { TextButton(onClick = { options = null }) { Text("Cancel") } }
        )
    }

    confirmDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this file?") },
            text = { Text("\"${item.file.name}\" will be deleted from this phone. The chat text stays.") },
            confirmButton = { TextButton(onClick = { confirmDelete = null; onDelete(item) }) { Text("Delete", color = AssistantError) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaTile(item: LibraryItem, load: (AttachmentRef) -> ChatAttachment?, onClick: () -> Unit, onLongClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val isImage = item.category == LibraryCategory.IMAGES
    val thumbnail by produceState<Bitmap?>(null, item.file.path) {
        value = if (isImage) withContext(Dispatchers.IO) { load(item.file)?.let { decodeThumbnail(it.data) } } else null
    }
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(shape)
            .background(if (isImage) AssistantSelectedBg else Color(0xFF232738))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = thumbnail
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), contentDescription = item.file.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else if (!isImage) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Video", tint = Color.White, modifier = Modifier.size(36.dp))
            Text(
                item.file.name, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(item: LibraryItem, onClick: () -> Unit, onLongClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(AssistantSurface, shape)
            .border(BorderStroke(1.dp, AssistantOutline), shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (item.category == LibraryCategory.PDFS) Icons.Filled.PictureAsPdf else Icons.Filled.Description,
            contentDescription = null, tint = AssistantAccent, modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.file.name, color = AssistantTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${formatBytes(item.file.size.toLong())}, ${relativeDate(item.timestampMillis)}, ${item.chatTitle}",
                color = AssistantTextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

internal fun relativeDate(timestampMillis: Long): String =
    DateUtils.getRelativeTimeSpanString(timestampMillis, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS).toString()
