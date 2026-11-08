package com.assistant.app.ui.components

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.app.data.DownloadKind
import com.assistant.app.data.DownloadedFile
import com.assistant.app.data.DownloadedFiles
import com.assistant.app.ui.theme.AlertDialog
import com.assistant.app.ui.theme.AppButtonShape
import com.assistant.app.ui.theme.AssistantError
import com.assistant.app.ui.theme.AssistantTextMuted
import com.assistant.app.ui.theme.AssistantTextPrimary
import com.assistant.app.ui.theme.AssistantTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * Everything Ultron has downloaded: YouTube videos and audio, kept in the phone's Downloads/Ultron folder. Tap a file to
 * open it, share it or delete it. The same files show in the phone's own Files app.
 */
@Composable
fun DownloadsPage(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var files by remember { mutableStateOf<List<DownloadedFile>?>(null) }
    var filter by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<DownloadedFile?>(null) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(reload) { files = withContext(Dispatchers.IO) { DownloadedFiles.list(context) } }

    deleting?.let { file ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this file?") },
            text = { Text("\"${file.name}\" will be deleted from your phone. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    val removed = DownloadedFiles.delete(context, file)
                    if (!removed) Toast.makeText(context, "Could not delete it. Remove it in the Files app.", Toast.LENGTH_LONG).show()
                    reload++
                }) { Text("Delete", color = AssistantError) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }

    val all = files.orEmpty()
    val kinds = DownloadKind.entries.filter { kind -> all.any { it.kind == kind } }.map { it.label }
    val shown = all.filter { filter == null || it.kind.label == filter }

    PageColumn(modifier) {
        PageCard {
            Text("Downloaded by Ultron", color = AssistantTextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                if (files == null) "…" else "${all.size} ${if (all.size == 1) "file" else "files"} · ${formatBytes(all.sumOf { it.bytes })}",
                color = AssistantTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Saved in Downloads/Ultron, so you also find them in your phone's Files app.",
                color = AssistantTextMuted, fontSize = 13.sp, lineHeight = 18.sp
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { openDownloadsFolder(context) }, shape = AppButtonShape) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Open in Files")
            }
        }
        if (kinds.size > 1) {
            Spacer(Modifier.height(12.dp))
            CategoryChips(kinds, filter, { filter = it })
        }
        if (files != null && shown.isEmpty()) {
            Text(
                if (all.isEmpty()) "Nothing downloaded yet. Ask Ultron to download a YouTube video or its audio and it will show up here."
                else "Nothing in this group.",
                color = AssistantTextMuted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp)
            )
        }
        if (shown.isNotEmpty()) PageLabel("Files")
        shown.forEach { file ->
            DownloadRow(file, onDelete = { deleting = file })
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun DownloadRow(file: DownloadedFile, onDelete: () -> Unit) {
    val context = LocalContext.current
    val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(file.addedMillis))
    PageCard(Modifier.clickable { openFile(context, file) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeutralIcon(
                when (file.kind) {
                    DownloadKind.VIDEO -> Icons.Outlined.Videocam
                    DownloadKind.AUDIO -> Icons.Outlined.MusicNote
                    DownloadKind.OTHER -> Icons.Outlined.Description
                }
            )
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    file.name, color = AssistantTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Text("${file.kind.label} · ${formatBytes(file.bytes)} · $date", color = AssistantTextSecondary, fontSize = 13.sp)
            }
            Row(horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { shareFile(context, file) }) {
                    Icon(Icons.Outlined.Share, contentDescription = "Share", tint = AssistantTextSecondary, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = AssistantTextSecondary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

private fun openFile(context: Context, file: DownloadedFile) {
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(file.uri, file.mime.ifBlank { "*/*" })
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, "No app can open this file.", Toast.LENGTH_SHORT).show() }
}

private fun shareFile(context: Context, file: DownloadedFile) {
    val send = Intent(Intent.ACTION_SEND).setType(file.mime.ifBlank { "*/*" })
        .putExtra(Intent.EXTRA_STREAM, file.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching {
        context.startActivity(Intent.createChooser(send, file.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { Toast.makeText(context, "Could not share this file.", Toast.LENGTH_SHORT).show() }
}

/** Opens the system Downloads screen, where the Ultron folder sits. */
private fun openDownloadsFolder(context: Context) {
    runCatching {
        context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { Toast.makeText(context, "Open the Files app and look in Downloads/Ultron.", Toast.LENGTH_LONG).show() }
}
