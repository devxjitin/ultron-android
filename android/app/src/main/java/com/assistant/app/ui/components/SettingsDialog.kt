package com.assistant.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import com.assistant.app.ui.theme.AppButtonShape
import com.assistant.app.ui.theme.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.app.data.AssistantOptions
import com.assistant.app.data.AssistantSettings
import com.assistant.app.data.DeviceTool
import com.assistant.app.data.MAX_RETRIEVAL_CHUNK_LIMIT
import com.assistant.app.data.MIN_RETRIEVAL_CHUNK_LIMIT
import com.assistant.app.data.MicSensitivity
import com.assistant.app.data.RagDocument
import com.assistant.app.viewmodel.ADD_DOCUMENT_TARGET_ID
import com.assistant.app.viewmodel.AppPage
import com.assistant.app.viewmodel.KnowledgePreparationStage
import com.assistant.app.ui.theme.AssistantAccent
import com.assistant.app.ui.theme.AssistantAccentFill
import com.assistant.app.ui.theme.AssistantError
import com.assistant.app.ui.theme.AssistantSurfaceElevated
import com.assistant.app.ui.theme.AssistantTextMuted
import com.assistant.app.ui.theme.AssistantTextSecondary

// Label to page-branch index (branch 2, Knowledge, has its own bottom-bar tab instead).


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsPage(
    currentApiKey: String,
    currentEmbeddingApiKey: String,
    settings: AssistantSettings,
    onClearApiKey: () -> Unit,
    onClearEmbeddingApiKey: () -> Unit,
    onLiveModelSelected: (String) -> Unit,
    onVoiceSelected: (String) -> Unit,
    onLanguageSelected: (String) -> Unit,
    onMicSensitivitySelected: (MicSensitivity) -> Unit,
    onBargeInToggled: (Boolean) -> Unit,
    onAnnounceNotificationsToggled: (Boolean) -> Unit,
    onDecisionsRerankToggled: (Boolean) -> Unit = {},
    onOwnerNameChanged: (String) -> Unit = {},
    onPersonalitySelected: (String) -> Unit = {},
    onSaveDeveloperChanges: (String, String, Int) -> Unit,
    knowledgeDocuments: List<RagDocument>,
    isSavingKnowledgeDocument: Boolean,
    knowledgePreparationStage: KnowledgePreparationStage?,
    /** Which add/edit operation [isSavingKnowledgeDocument]/[knowledgeDocumentError] belong to
     *  right now -- lets each dialog below react only to its own save, not an unrelated one that
     *  happens to be in flight (or finish) at the same time. */
    savingKnowledgeDocumentTarget: String?,
    uploadedChunks: Int,
    totalChunks: Int,
    knowledgeDocumentError: String?,
    onAddKnowledgeDocument: (String, String) -> Unit,
    onUpdateKnowledgeDocument: (String, String, String) -> Unit,
    onDeleteKnowledgeDocument: (String) -> Unit,
    knowledgeStoreCorrupt: Boolean,
    onResetKnowledge: () -> Unit,
    onUploadPdfFile: () -> Unit,
    onDismissKnowledgeDocumentError: () -> Unit,
    page: AppPage,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** Reports whether the Developer draft has unsaved edits, so navigation outside this page can guard it. */
    onDirtyChanged: (Boolean) -> Unit = {},
    /** An exit requested from outside this page (e.g. bottom nav); runs only after Save/Discard. */
    pendingNavigation: (() -> Unit)? = null,
    onPendingNavigationHandled: () -> Unit = {},
    /** True while a Developer Save is running; Save is disabled so it can't be submitted twice. */
    isSavingDeveloper: Boolean = false,
) {
    var keyInput by remember(currentApiKey) { mutableStateOf(currentApiKey) }
    var keyVisible by remember { mutableStateOf(false) }
    var embeddingKeyInput by remember(currentEmbeddingApiKey) { mutableStateOf(currentEmbeddingApiKey) }
    var embeddingKeyVisible by remember { mutableStateOf(false) }
    var confirmingRemove by remember { mutableStateOf(false) }
    var confirmingRemoveEmbeddingKey by remember { mutableStateOf(false) }
    var editingDocument by remember { mutableStateOf<RagDocument?>(null) }
    var showAddDocumentDialog by remember { mutableStateOf(false) }
    var confirmingDeleteDocId by remember { mutableStateOf<String?>(null) }
    var confirmingResetKnowledge by remember { mutableStateOf(false) }
    var confirmingUnsavedChanges by remember { mutableStateOf(false) }
    var retrievalChunkLimitInput by remember(settings.retrievalChunkLimit) {
        androidx.compose.runtime.mutableIntStateOf(settings.retrievalChunkLimit)
    }
    val developerChangesPending =
        keyInput != currentApiKey ||
            embeddingKeyInput != currentEmbeddingApiKey ||
            retrievalChunkLimitInput != settings.retrievalChunkLimit

    val requestDismiss = {
        if (developerChangesPending) {
            confirmingUnsavedChanges = true
        } else {
            onDismiss()
        }
    }
    BackHandler(onBack = requestDismiss)
    LaunchedEffect(developerChangesPending) { onDirtyChanged(developerChangesPending) }
    DisposableEffect(Unit) { onDispose { onDirtyChanged(false) } }
    // Leave via the outside destination if one was requested, otherwise just close the page.
    val leaveAfterConfirm = {
        confirmingUnsavedChanges = false
        val destination = pendingNavigation
        onPendingNavigationHandled()
        if (destination != null) destination() else onDismiss()
    }
    val activeBranch = when (page) {
        AppPage.SETTINGS, AppPage.DEVELOPER -> 0
        AppPage.KNOWLEDGE -> 2
        else -> 3
    }
    val developerContent: @Composable () -> Unit = {
        Column {
                    PageLabel("Gemini API key")
                    PageCard {
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("AIza...") },
                        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { keyVisible = !keyVisible }) {
                                Icon(
                                    imageVector = if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (keyVisible) "Hide key" else "Show key"
                                )
                            }
                        },
                        colors = assistantTextFieldColors()
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (currentApiKey.isNotBlank()) {
                            TextButton(onClick = { confirmingRemove = true }) {
                                Text("Remove", color = AssistantError)
                            }
                        }
                    }
                    }
                    PageLabel("Knowledge API key")
                    PageCard {
                    Text("Optional. Leave empty to use the Gemini key.", color = AssistantTextMuted, fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 8.dp))
                    OutlinedTextField(
                        value = embeddingKeyInput,
                        onValueChange = { embeddingKeyInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("AIza...") },
                        visualTransformation = if (embeddingKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { embeddingKeyVisible = !embeddingKeyVisible }) {
                                Icon(
                                    imageVector = if (embeddingKeyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (embeddingKeyVisible) "Hide key" else "Show key"
                                )
                            }
                        },
                        colors = assistantTextFieldColors()
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (currentEmbeddingApiKey.isNotBlank()) {
                            TextButton(onClick = { confirmingRemoveEmbeddingKey = true }) {
                                Text("Remove", color = AssistantError)
                            }
                        }
                    }
                    }
                    PageLabel("Knowledge search")
                    PageCard {
                    Text("Sections read per answer", color = AssistantTextMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                    var retrievalLimitExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = retrievalLimitExpanded,
                        onExpandedChange = { retrievalLimitExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = retrievalChunkLimitInput.toString(),
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = retrievalLimitExpanded) },
                            colors = assistantTextFieldColors()
                        )
                        DropdownMenu(
                            expanded = retrievalLimitExpanded,
                            onDismissRequest = { retrievalLimitExpanded = false },
                            modifier = Modifier.background(AssistantSurfaceElevated)
                        ) {
                            (MIN_RETRIEVAL_CHUNK_LIMIT..MAX_RETRIEVAL_CHUNK_LIMIT).forEach { limit ->
                                DropdownMenuItem(
                                    text = { Text("$limit chunks", color = AssistantTextSecondary) },
                                    onClick = {
                                        retrievalChunkLimitInput = limit
                                        retrievalLimitExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    }
                }
    }
    // Scoped to this page only: slightly smaller text than the app-wide typography.
    val base = androidx.compose.material3.MaterialTheme.typography
    val compactTypography = base.copy(
        titleLarge = base.titleLarge.copy(fontSize = 20.sp),
        bodyLarge = base.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp),
        labelLarge = base.labelLarge.copy(fontSize = 13.sp)
    )
    androidx.compose.material3.MaterialTheme(
        colorScheme = androidx.compose.material3.MaterialTheme.colorScheme,
        typography = compactTypography
    ) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
        key(activeBranch) {
        val contentScrollState = rememberScrollState()
        Column(modifier = Modifier.weight(1f).verticalScroll(contentScrollState)) {
            when (activeBranch) {
                0 -> Column {
                    GeneralSettingsContent(
                        settings, onLiveModelSelected, onVoiceSelected, onLanguageSelected,
                        onMicSensitivitySelected, onBargeInToggled, onAnnounceNotificationsToggled, onOwnerNameChanged, onPersonalitySelected,
                        onDecisionsRerankToggled
                    )
                    // The old Developer page now lives at the end of Settings.
                    Spacer(Modifier.height(24.dp))
                    developerContent()
                }
                2 -> Column {
                    Text(
                        "Documents the assistant can search when you ask about them.",
                        color = AssistantTextMuted, fontSize = 13.sp, lineHeight = 18.sp,
                        modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        androidx.compose.material3.Button(
                            shape = AppButtonShape,
                            modifier = Modifier.weight(1f).height(48.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = com.assistant.app.ui.theme.AssistantButtonFill,
                                contentColor = com.assistant.app.ui.theme.AssistantOnButtonFill
                            ),
                            onClick = { showAddDocumentDialog = true },
                            enabled = !isSavingKnowledgeDocument && !knowledgeStoreCorrupt
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add document", maxLines = 1, softWrap = false)
                        }
                        androidx.compose.material3.OutlinedButton(
                            shape = AppButtonShape,
                            modifier = Modifier.weight(1f).height(48.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, com.assistant.app.ui.theme.AssistantOutline),
                            onClick = onUploadPdfFile,
                            enabled = !isSavingKnowledgeDocument && !knowledgeStoreCorrupt
                        ) {
                            Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Upload PDF", maxLines = 1, softWrap = false)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    if (isSavingKnowledgeDocument &&
                        savingKnowledgeDocumentTarget == ADD_DOCUMENT_TARGET_ID &&
                        knowledgePreparationStage == KnowledgePreparationStage.ANALYZING
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = AssistantAccent
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Analyzing your document…",
                                        color = AssistantTextSecondary,
                                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "Finding and organizing the important information.",
                                        color = AssistantTextMuted,
                                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    } else if (isSavingKnowledgeDocument && totalChunks > 0) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Preparing your knowledge… $uploadedChunks of $totalChunks sections",
                                color = AssistantTextMuted,
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(6.dp))
                            if (uploadedChunks == 0) {
                                androidx.compose.material3.LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(), color = AssistantAccent
                                )
                            } else {
                                androidx.compose.material3.LinearProgressIndicator(
                                    progress = { uploadedChunks.toFloat() / totalChunks.toFloat() },
                                    modifier = Modifier.fillMaxWidth(), color = AssistantAccent
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    } else if (isSavingKnowledgeDocument) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Preparing your knowledge…",
                                color = AssistantTextMuted,
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(6.dp))
                            androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = AssistantAccent)
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    if (knowledgeStoreCorrupt) {
                        Text(
                            text = "The stored knowledge couldn't be read. Reset it to add documents again.",
                            color = AssistantError,
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                        )
                        androidx.compose.material3.OutlinedButton(shape = AppButtonShape, onClick = { confirmingResetKnowledge = true }) {
                            Text("Reset knowledge", color = AssistantError)
                        }
                    } else if (knowledgeDocuments.isEmpty()) {
                        PageCard {
                            Text("No documents yet", color = com.assistant.app.ui.theme.AssistantTextPrimary, fontSize = 15.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                            Text("Add notes or upload a PDF, then ask the assistant about them.",
                                color = AssistantTextMuted, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    } else {
                        PageLabel("Documents (${knowledgeDocuments.size})", Modifier.padding(top = 4.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            knowledgeDocuments.forEach { doc ->
                                KnowledgeDocumentRow(
                                    doc = doc,
                                    onEdit = { editingDocument = doc },
                                    onDelete = { confirmingDeleteDocId = doc.id }
                                )
                            }
                        }
                    }
                }
            }
        }
        if (activeBranch == 0 && developerChangesPending) {
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.Button(
                onClick = {
                    onSaveDeveloperChanges(
                        keyInput,
                        embeddingKeyInput,
                        retrievalChunkLimitInput
                    )
                },
                enabled = !isSavingDeveloper,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = com.assistant.app.ui.theme.AssistantButtonFill,
                    contentColor = com.assistant.app.ui.theme.AssistantOnButtonFill
                ),
                shape = AppButtonShape,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 14.dp)
            ) { Text("Save changes") }
        }
        }
    }
    }

    val cancelUnsavedPrompt = {
        confirmingUnsavedChanges = false
        onPendingNavigationHandled()
    }
    if (confirmingUnsavedChanges || pendingNavigation != null) {
        AlertDialog(
            onDismissRequest = cancelUnsavedPrompt,
            shape = com.assistant.app.ui.theme.SheetShape,
            containerColor = AssistantSurfaceElevated,
            tonalElevation = 6.dp,
            title = { Text("Save changes?") },
            text = { Text("You have unsaved settings. Save them before leaving?") },
            confirmButton = {
                TextButton(enabled = !isSavingDeveloper, onClick = {
                    onSaveDeveloperChanges(
                        keyInput,
                        embeddingKeyInput,
                        retrievalChunkLimitInput
                    )
                    // Stay on the page: success restarts the app, failure keeps the draft for a retry.
                    cancelUnsavedPrompt()
                }) {
                    Text("Save changes", color = AssistantAccent)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = leaveAfterConfirm) {
                        Text("Discard", color = AssistantError)
                    }
                    TextButton(onClick = cancelUnsavedPrompt) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    if (confirmingRemove) {
        AlertDialog(
            onDismissRequest = { confirmingRemove = false },
            shape = com.assistant.app.ui.theme.SheetShape,
            containerColor = AssistantSurfaceElevated,
            tonalElevation = 6.dp,
            title = { Text("Remove API key?") },
            text = {
                Text("The assistant stops working until you add a key again. The active conversation, if any, isn't affected.")
            },
            confirmButton = {
                TextButton(onClick = {
                    onClearApiKey()
                    confirmingRemove = false
                }) {
                    Text("Remove", color = AssistantError)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingRemove = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (confirmingRemoveEmbeddingKey) {
        AlertDialog(
            onDismissRequest = { confirmingRemoveEmbeddingKey = false },
            shape = com.assistant.app.ui.theme.SheetShape,
            containerColor = AssistantSurfaceElevated,
            tonalElevation = 6.dp,
            title = { Text("Remove knowledge API key?") },
            text = { Text("Knowledge features will use the main Gemini API key instead.") },
            confirmButton = {
                TextButton(onClick = {
                    onClearEmbeddingApiKey()
                    confirmingRemoveEmbeddingKey = false
                }) {
                    Text("Remove", color = AssistantError)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingRemoveEmbeddingKey = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddDocumentDialog) {
        KnowledgeDocEditDialog(
            existing = null,
            isSaving = isSavingKnowledgeDocument && savingKnowledgeDocumentTarget == ADD_DOCUMENT_TARGET_ID,
            error = if (savingKnowledgeDocumentTarget == ADD_DOCUMENT_TARGET_ID) knowledgeDocumentError else null,
            onSave = { title, text -> onAddKnowledgeDocument(title, text) },
            onDismiss = { showAddDocumentDialog = false },
        )
    }

    editingDocument?.let { doc ->
        KnowledgeDocEditDialog(
            existing = doc,
            isSaving = isSavingKnowledgeDocument && savingKnowledgeDocumentTarget == doc.id,
            error = if (savingKnowledgeDocumentTarget == doc.id) knowledgeDocumentError else null,
            onSave = { title, text -> onUpdateKnowledgeDocument(doc.id, title, text) },
            onDismiss = { editingDocument = null },
        )
    }

    if (confirmingResetKnowledge) {
        AlertDialog(
            onDismissRequest = { confirmingResetKnowledge = false },
            shape = com.assistant.app.ui.theme.SheetShape,
            containerColor = AssistantSurfaceElevated,
            tonalElevation = 6.dp,
            title = { Text("Reset knowledge?") },
            text = { Text("All knowledge documents and the search index will be deleted. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onResetKnowledge()
                    confirmingResetKnowledge = false
                }) {
                    Text("Reset", color = AssistantError)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingResetKnowledge = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    confirmingDeleteDocId?.let { docId ->
        val docTitle = knowledgeDocuments.firstOrNull { it.id == docId }?.title.orEmpty()
        AlertDialog(
            onDismissRequest = { confirmingDeleteDocId = null },
            shape = com.assistant.app.ui.theme.SheetShape,
            containerColor = AssistantSurfaceElevated,
            tonalElevation = 6.dp,
            title = { Text("Delete document?") },
            text = { Text("\"$docTitle\" will no longer be searchable by the assistant.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteKnowledgeDocument(docId)
                    confirmingDeleteDocId = null
                }) {
                    Text("Delete", color = AssistantError)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDeleteDocId = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/** Shared field styling so every input across Settings has the same accent-tinted border,
 *  matching the home screen's look instead of the flat default outline. */
@Composable
internal fun assistantTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = com.assistant.app.ui.theme.AssistantFieldBackground,
    unfocusedContainerColor = com.assistant.app.ui.theme.AssistantFieldBackground,
    focusedTextColor = com.assistant.app.ui.theme.AssistantTextPrimary,
    unfocusedTextColor = AssistantTextSecondary,
    focusedIndicatorColor = AssistantAccent,
    unfocusedIndicatorColor = com.assistant.app.ui.theme.AssistantOutline,
    cursorColor = AssistantAccent,
    focusedLabelColor = AssistantAccent,
    unfocusedLabelColor = AssistantAccent,
    focusedPlaceholderColor = AssistantTextMuted,
    unfocusedPlaceholderColor = AssistantTextMuted,
    focusedTrailingIconColor = AssistantTextSecondary,
    unfocusedTrailingIconColor = AssistantTextMuted
)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeneralSettingsContent(
    settings: AssistantSettings,
    onLiveModelSelected: (String) -> Unit,
    onVoiceSelected: (String) -> Unit,
    onLanguageSelected: (String) -> Unit,
    onMicSensitivitySelected: (MicSensitivity) -> Unit,
    onBargeInToggled: (Boolean) -> Unit,
    onAnnounceNotificationsToggled: (Boolean) -> Unit,
    onOwnerNameChanged: (String) -> Unit,
    onPersonalitySelected: (String) -> Unit,
    onDecisionsRerankToggled: (Boolean) -> Unit
) {
    val micHints = mapOf(
        MicSensitivity.LOW to "Cuts background noise",
        MicSensitivity.MEDIUM to "Best for normal rooms",
        MicSensitivity.HIGH to "Picks up quiet voices"
    )
    Column {
        SettingsHeading("Personality")
        PersonalityPicker(selectedId = settings.personalityId, onSelect = onPersonalitySelected)
        SettingsHeading("Model")
        SettingsDropdown(
            value = AssistantOptions.liveModels.firstOrNull { it.id == settings.liveModel }?.label ?: settings.liveModel,
            options = AssistantOptions.liveModels.map { it.id to it.label },
            selectedId = settings.liveModel,
            onSelect = onLiveModelSelected
        )
        SettingsHeading("Voice")
        SettingsDropdown(
            value = AssistantOptions.voices.firstOrNull { it.name == settings.voiceName }
                ?.let { "${it.name}, ${it.gender}, ${it.style}" } ?: settings.voiceName,
            options = AssistantOptions.voices.map { it.name to "${it.name}, ${it.gender}, ${it.style}" },
            selectedId = settings.voiceName,
            onSelect = onVoiceSelected
        )
        SettingsHeading("Language")
        SettingsDropdown(
            value = AssistantOptions.languageLabel(settings.languageCode),
            options = AssistantOptions.languages.map { it.code to it.label },
            selectedId = settings.languageCode,
            onSelect = onLanguageSelected
        )
        SettingsHeading("Your name")
        var ownerInput by remember(settings.ownerName) { mutableStateOf(settings.ownerName) }
        OutlinedTextField(
            value = ownerInput,
            onValueChange = { ownerInput = it.take(com.assistant.app.data.MAX_ASSISTANT_NAME_LENGTH) },
            singleLine = true,
            placeholder = { Text("How should the assistant address you?", color = AssistantTextMuted) },
            colors = assistantTextFieldColors(),
            keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                if (ownerInput.trim() != settings.ownerName) onOwnerNameChanged(ownerInput)
            }),
            modifier = Modifier.fillMaxWidth()
        )
        SettingsHeading("Microphone sensitivity")
        ChoiceDropdown(
            options = MicSensitivity.entries.map { it.name to "${it.label}: ${micHints.getValue(it)}" },
            selectedId = settings.micSensitivity.name,
            onSelect = { name -> onMicSensitivitySelected(MicSensitivity.fromStoredValue(name)) }
        )
        Spacer(Modifier.height(22.dp))
        ToggleCard(
            title = "Barge-in",
            hint = "Talk to interrupt. Turn off if it hears itself.",
            checked = settings.bargeInEnabled, onChange = onBargeInToggled
        )
        Spacer(Modifier.height(12.dp))
        ToggleCard(
            title = "Read out notifications",
            hint = "Tells you about new notifications while you talk. The text is shared with Gemini.",
            checked = settings.announceNotificationsEnabled, onChange = onAnnounceNotificationsToggled
        )
        Spacer(Modifier.height(12.dp))
        ToggleCard(
            title = "Smarter document search",
            hint = "Asks Gemini which passages really answer your question. Adds about a second to document searches. The passages are sent to Gemini, using your own key.",
            checked = settings.decisionsRerankEnabled, onChange = onDecisionsRerankToggled
        )
        DefaultAssistantSection()
    }
}

/** A labelled read-only field that opens a dropdown of [options] (id to label). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsDropdown(
    value: String,
    options: List<Pair<String, String>>,
    selectedId: String,
    onSelect: (String) -> Unit,
    hint: String? = null
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = assistantTextFieldColors()
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp).background(AssistantSurfaceElevated)
        ) {
            options.forEach { (id, text) ->
                DropdownMenuItem(
                    text = { Text(text, color = if (id == selectedId) AssistantAccent else AssistantTextSecondary) },
                    onClick = {
                        expanded = false
                        onSelect(id)
                    }
                )
            }
        }
    }
    if (hint != null) {
        Text(text = hint, color = AssistantTextMuted, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
    }
}

/** One knowledge document: icon tile, a one-line title with its size and date, and edit/delete actions. */
@Composable
private fun KnowledgeDocumentRow(doc: RagDocument, onEdit: () -> Unit, onDelete: () -> Unit) {
    val shape = com.assistant.app.ui.theme.CardShape
    val updated = remember(doc.updatedAtMillis) {
        java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(doc.updatedAtMillis))
    }
    val words = remember(doc.text) { doc.text.split(Regex("\\s+")).count { it.isNotBlank() } }
    Row(
        modifier = Modifier.fillMaxWidth().clip(shape).background(com.assistant.app.ui.theme.AssistantSurface)
            .border(androidx.compose.foundation.BorderStroke(1.dp, com.assistant.app.ui.theme.AssistantOutline), shape)
            .clickable(onClick = onEdit).padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .background(com.assistant.app.ui.theme.AssistantSelectedBg),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Icon(Icons.Outlined.Description, contentDescription = null, tint = com.assistant.app.ui.theme.AssistantTextPrimary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                doc.title.replace('_', ' '), color = com.assistant.app.ui.theme.AssistantTextPrimary, fontSize = 15.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text("$words words · $updated", color = AssistantTextMuted, fontSize = 12.sp, maxLines = 1)
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Outlined.Edit, contentDescription = "Edit ${doc.title}", tint = AssistantTextMuted, modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = "Delete ${doc.title}", tint = AssistantTextMuted, modifier = Modifier.size(20.dp))
        }
    }
}
