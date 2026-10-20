package com.assistant.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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

/** Add/edit form for a single knowledge document. [existing] null means "new document". */
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
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        containerColor = AssistantSurfaceElevated,
        tonalElevation = 6.dp,
        title = { Text(if (existing == null) "Add document" else "Edit document") },
        text = {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())
            ) {
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
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                    enabled = !isSaving,
                    label = { Text("Content") },
                    placeholder = { Text("Separate different topics with a blank line so they are easier to find later.") }
                )
                if (isSaving) {
                    androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
                    CircularProgressIndicator()
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, text) },
                enabled = !isSaving && title.isNotBlank() && text.isNotBlank()
            ) {
                Text("Save", color = AssistantAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Cancel", color = AssistantTextSecondary)
            }
        }
    )
}
