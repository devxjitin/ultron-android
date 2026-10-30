package com.assistant.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.assistant.app.data.FileKind
import com.assistant.app.data.FileOpeners

private const val PHONE_DEFAULT = ""

/** Settings: which app opens images, PDFs and docx files when the assistant shows a file. */
@Composable
fun FileOpenersSection() {
    val context = LocalContext.current
    Column {
        SettingsHeading("Open files with")
        FileKind.entries.forEach { kind ->
            val apps = remember(kind) { FileOpeners.candidates(context, kind) }
            var selected by remember(kind) { mutableStateOf(FileOpeners.chosen(context, kind) ?: PHONE_DEFAULT) }
            SettingsHeading(kind.label)
            ChoiceDropdown(
                options = listOf(PHONE_DEFAULT to "Phone default") + apps,
                selectedId = selected,
                onSelect = { id ->
                    selected = id
                    FileOpeners.choose(context, kind, id.takeIf { it != PHONE_DEFAULT })
                }
            )
        }
    }
}
