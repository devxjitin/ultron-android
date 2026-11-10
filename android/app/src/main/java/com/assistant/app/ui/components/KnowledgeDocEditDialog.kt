package com.assistant.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.assistant.app.ui.theme.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableStateListOf
import com.assistant.app.data.joinKnowledgeBlocks
import com.assistant.app.data.splitKnowledgeBlocks
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.assistant.app.data.RagDocument
import com.assistant.app.ui.theme.AssistantAccent
import com.assistant.app.ui.theme.AssistantSurfaceElevated
import com.assistant.app.ui.theme.AssistantTextSecondary

/**
 * Add/edit form for a single knowledge document. [existing] null means "new document".
 * An existing document opens as a read-only preview of its chunks, rendered as Markdown; Edit switches to the raw text.
 */
@Composable
fun KnowledgeDocEditDialog(
    existing: RagDocument?,
    isSaving: Boolean,
    error: String? = null,
    onSave: (title: String, text: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var text by remember { mutableStateOf(existing?.text.orEmpty()) }
    // Existing documents are edited chunk by chunk; unchanged chunks keep their embeddings on save.
    val originalBlocks = remember(existing?.id) { existing?.let { splitKnowledgeBlocks(it.text) }.orEmpty() }
    val blocks = remember(existing?.id) { mutableStateListOf<String>().apply { addAll(originalBlocks) } }
    val chunkMode = existing != null
    var viewing by remember { mutableStateOf(existing != null) }
    val changedCount = if (chunkMode) blocks.count { it.trim() !in originalBlocks } else 0
    val savedText = if (chunkMode) joinKnowledgeBlocks(blocks) else text

    // [isSaving]/[error] are already scoped to this dialog's own save (the caller only passes a
    // true isSaving/non-null error when it's this document's add/update, not some other document
    // saving concurrently) -- so reacting to them here auto-closes this dialog on ITS OWN success
    // without ever being tripped by an unrelated concurrent save finishing first.
    var saveStarted by remember { mutableStateOf(false) }
    LaunchedEffect(isSaving, error) {
        if (isSaving) {
            saveStarted = true
        } else if (saveStarted) {
            saveStarted = false
            if (error == null) onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        shape = com.assistant.app.ui.theme.SheetShape,
        containerColor = AssistantSurfaceElevated,
        tonalElevation = 6.dp,
        title = { Text(if (existing == null) "Add document" else if (viewing) title.ifBlank { "Document" } else "Edit document") },
        text = {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())
            ) {
                if (viewing) {
                    Text(
                        text = "${blocks.size} ${if (blocks.size == 1) "chunk" else "chunks"}",
                        color = AssistantTextSecondary,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                    )
                    blocks.forEachIndexed { index, block ->
                        androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
                        Text(
                            "Chunk ${index + 1}", color = AssistantAccent,
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                        )
                        MarkdownText(block, Modifier.padding(top = 2.dp))
                    }
                    return@Column
                }
                // Frozen while isSaving so typing here can never be silently discarded by the
                // submitted snapshot's save completing and auto-closing the dialog underneath it.
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isSaving,
                    label = { Text("Title") }
                )
                androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
                if (chunkMode) {
                    Text(
                        text = "${blocks.size} sections, $changedCount changed.",
                        color = AssistantTextSecondary,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                    )
                    blocks.forEachIndexed { index, block ->
                        androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = block,
                            onValueChange = { blocks[index] = it },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isSaving,
                            label = {
                                Text("Chunk ${index + 1}" + if (block.trim() !in originalBlocks) " (edited)" else "")
                            },
                            trailingIcon = {
                                IconButton(onClick = { blocks.removeAt(index) }, enabled = !isSaving) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete chunk ${index + 1}")
                                }
                            }
                        )
                    }
                    TextButton(onClick = { blocks.add("") }, enabled = !isSaving) {
                        Text("Add chunk", color = AssistantAccent)
                    }
                } else {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                        enabled = !isSaving,
                        label = { Text("Content") },
                        placeholder = { Text("Leave a blank line between topics.") }
                    )
                }
                if (isSaving) {
                    androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
                    CircularProgressIndicator()
                }
            }
        },
        confirmButton = {
            if (viewing) {
                TextButton(onClick = { viewing = false }) { Text("Edit", color = AssistantAccent) }
            } else TextButton(
                onClick = { onSave(title, savedText) },
                enabled = !isSaving && title.isNotBlank() && savedText.isNotBlank()
            ) {
                Text("Save", color = AssistantAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(if (viewing) "Close" else "Cancel", color = AssistantTextSecondary)
            }
        }
    )
}
