package com.assistant.app.ui.components

import androidx.activity.compose.BackHandler
import com.assistant.app.BuildConfig
import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
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
import com.assistant.app.data.AZURE_CHAT_MODELS
import com.assistant.app.data.AzureConfig
import com.assistant.app.data.AzureMode
import com.assistant.app.data.DEFAULT_CHAT_PROMPT
import com.assistant.app.data.DEFAULT_SYSTEM_PROMPT
import com.assistant.app.data.DeviceTool
import com.assistant.app.data.MAX_ASSISTANT_NAME_LENGTH
import com.assistant.app.data.MAX_GREETING_LENGTH
import com.assistant.app.data.MAX_RETRIEVAL_CHUNK_LIMIT
import com.assistant.app.data.MIN_RETRIEVAL_CHUNK_LIMIT
import com.assistant.app.data.MicSensitivity
import com.assistant.app.data.RagDocument
import com.assistant.app.viewmodel.ADD_DOCUMENT_TARGET_ID
import com.assistant.app.viewmodel.AdminPage
import com.assistant.app.viewmodel.KnowledgePreparationStage
import com.assistant.app.ui.theme.AssistantAccent
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
    onSaveDeveloperChanges: (String, String, String, String, String, String, String, Int) -> Unit,
    isGeneratingPrompt: Boolean,
    generatedPromptDraft: String?,
    promptGenerationError: String?,
    onGeneratePromptFromKnowledgeBase: () -> Unit,
    onDismissPromptGenerationError: () -> Unit,
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
    onUploadMarkdownFile: () -> Unit,
    onDismissKnowledgeDocumentError: () -> Unit,
    page: AdminPage,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** Reports whether the Developer draft has unsaved edits, so navigation outside this page can guard it. */
    onDirtyChanged: (Boolean) -> Unit = {},
    /** An exit requested from outside this page (e.g. bottom nav); runs only after Save/Discard. */
    pendingNavigation: (() -> Unit)? = null,
    onPendingNavigationHandled: () -> Unit = {},
    /** True while a Developer Save is running; Save is disabled so it can't be submitted twice. */
    isSavingDeveloper: Boolean = false,
    azure: AzureConfig = AzureConfig(),
    onSaveAzure: (AzureConfig) -> Unit = {}
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
    var nameInput by remember(settings.assistantName) { mutableStateOf(settings.assistantName) }
    var greetingInput by remember(settings.greeting) { mutableStateOf(settings.greeting) }
    var promptInput by remember(settings.systemPrompt) { mutableStateOf(settings.systemPrompt) }
    var chatPromptInput by remember(settings.chatPrompt) { mutableStateOf(settings.chatPrompt) }
    // A generated draft replaces the field directly only when there are no unsaved edits to lose.
    var pendingGeneratedDraft by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(generatedPromptDraft) {
        val draft = generatedPromptDraft ?: return@LaunchedEffect
        if (promptInput == settings.systemPrompt) promptInput = draft else pendingGeneratedDraft = draft
    }
    var documentFormatPromptInput by remember(settings.documentFormatPrompt) {
        mutableStateOf(settings.documentFormatPrompt)
    }
    var retrievalChunkLimitInput by remember(settings.retrievalChunkLimit) {
        mutableStateOf(settings.retrievalChunkLimit)
    }
    val developerChangesPending =
        nameInput != settings.assistantName ||
            greetingInput != settings.greeting ||
            keyInput != currentApiKey ||
            embeddingKeyInput != currentEmbeddingApiKey ||
            promptInput != settings.systemPrompt ||
            chatPromptInput != settings.chatPrompt ||
            documentFormatPromptInput != settings.documentFormatPrompt ||
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
        AdminPage.SETTINGS -> 0
        AdminPage.DEVELOPER -> 1
        AdminPage.KNOWLEDGE -> 2
        else -> 3
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
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        key(activeBranch) {
        val contentScrollState = rememberScrollState()
        Column(modifier = Modifier.weight(1f).verticalScroll(contentScrollState)) {
            when (activeBranch) {
                0 -> GeneralSettingsContent(
                    settings, onLiveModelSelected, onVoiceSelected, onLanguageSelected,
                    onMicSensitivitySelected, onBargeInToggled
                )
                1 -> Column {
                    SectionLabel("Identity")
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it.take(MAX_ASSISTANT_NAME_LENGTH) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = assistantTextFieldColors()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = greetingInput,
                        onValueChange = { greetingInput = it.take(MAX_GREETING_LENGTH) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = assistantTextFieldColors()
                    )
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = AssistantAccent.copy(alpha = 0.15f))
                    Spacer(Modifier.height(16.dp))
                    SectionLabel("Gemini API key")
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
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = AssistantAccent.copy(alpha = 0.15f))
                Spacer(Modifier.height(16.dp))
                    AzureChatSection(azure, onSaveAzure)
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = AssistantAccent.copy(alpha = 0.15f))
                Spacer(Modifier.height(16.dp))
                    SectionLabel("Knowledge API key (required)")
                    Spacer(Modifier.height(8.dp))
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
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = AssistantAccent.copy(alpha = 0.15f))
                Spacer(Modifier.height(16.dp))
                    SectionLabel("Voice agent prompt")
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedButton(
                        onClick = onGeneratePromptFromKnowledgeBase,
                        enabled = !isGeneratingPrompt,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isGeneratingPrompt) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = AssistantAccent
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Generating…")
                        } else {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Generate from knowledge base")
                        }
                    }
                    if (promptGenerationError != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = promptGenerationError,
                                color = AssistantError,
                                modifier = Modifier.weight(1f),
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                            )
                            TextButton(onClick = onDismissPromptGenerationError) {
                                Text("Dismiss", color = AssistantTextMuted)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = promptInput,
                        onValueChange = { promptInput = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                        colors = assistantTextFieldColors()
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            promptInput = DEFAULT_SYSTEM_PROMPT.trim()
                        }) {
                            Text("Reset to default", color = AssistantError)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = AssistantAccent.copy(alpha = 0.15f))
                    Spacer(Modifier.height(16.dp))
                    SectionLabel("Chat prompt")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = chatPromptInput,
                        onValueChange = { chatPromptInput = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                        colors = assistantTextFieldColors()
                    )
                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { chatPromptInput = DEFAULT_CHAT_PROMPT.trim() }) {
                            Text("Reset to default", color = AssistantError)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = AssistantAccent.copy(alpha = 0.15f))
                    Spacer(Modifier.height(16.dp))
                    SectionLabel("Retrieval chunks")
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
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = AssistantAccent.copy(alpha = 0.15f))
                    Spacer(Modifier.height(16.dp))
                    SectionLabel("Dataset preparation prompt")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = documentFormatPromptInput,
                        onValueChange = { documentFormatPromptInput = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                        colors = assistantTextFieldColors()
                    )
                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            onClick = { documentFormatPromptInput = "" }
                        ) { Text("Clear prompt", color = AssistantError) }
                    }
                }
                2 -> Column {
                    SectionLabel("Knowledge documents")
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        androidx.compose.material3.FilledTonalButton(
                            onClick = { showAddDocumentDialog = true },
                            enabled = !isSavingKnowledgeDocument && !knowledgeStoreCorrupt
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add document")
                        }
                        androidx.compose.material3.OutlinedButton(
                            onClick = onUploadMarkdownFile,
                            enabled = !isSavingKnowledgeDocument && !knowledgeStoreCorrupt
                        ) {
                            Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Upload PDF")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
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
                        androidx.compose.material3.OutlinedButton(onClick = { confirmingResetKnowledge = true }) {
                            Text("Reset knowledge", color = AssistantError)
                        }
                    } else if (knowledgeDocuments.isEmpty()) {
                        Text(
                            text = "No documents yet.",
                            color = AssistantTextMuted,
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            knowledgeDocuments.forEach { doc ->
                                androidx.compose.material3.Card(
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                                    colors = androidx.compose.material3.CardDefaults.cardColors(
                                        containerColor = AssistantSurfaceElevated
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AssistantAccent.copy(alpha = 0.38f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Filled.Description,
                                            contentDescription = null,
                                            tint = AssistantAccent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text = doc.title,
                                            color = AssistantTextSecondary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = { editingDocument = doc }) {
                                            Icon(Icons.Filled.Edit, contentDescription = "Edit ${doc.title}", tint = AssistantTextMuted)
                                        }
                                        IconButton(onClick = { confirmingDeleteDocId = doc.id }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete ${doc.title}", tint = AssistantError)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (activeBranch == 1 && developerChangesPending) {
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.Button(
                onClick = {
                    onSaveDeveloperChanges(
                        nameInput,
                        greetingInput,
                        keyInput,
                        embeddingKeyInput,
                        promptInput,
                        chatPromptInput,
                        documentFormatPromptInput,
                        retrievalChunkLimitInput
                    )
                },
                enabled = !isSavingDeveloper,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AssistantAccent,
                    contentColor = androidx.compose.ui.graphics.Color.White
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 14.dp)
            ) { Text("Save changes") }
        }
        }
    }
    }

    pendingGeneratedDraft?.let { draft ->
        AlertDialog(
            onDismissRequest = { pendingGeneratedDraft = null },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            containerColor = AssistantSurfaceElevated,
            tonalElevation = 6.dp,
            title = { Text("Replace your edits?") },
            text = { Text("The Agent prompt has unsaved edits. Replace them with the generated draft?") },
            confirmButton = {
                TextButton(onClick = {
                    promptInput = draft
                    pendingGeneratedDraft = null
                }) { Text("Replace", color = AssistantAccent) }
            },
            dismissButton = {
                TextButton(onClick = { pendingGeneratedDraft = null }) {
                    Text("Keep mine", color = AssistantTextMuted)
                }
            }
        )
    }

    val cancelUnsavedPrompt = {
        confirmingUnsavedChanges = false
        onPendingNavigationHandled()
    }
    if (confirmingUnsavedChanges || pendingNavigation != null) {
        AlertDialog(
            onDismissRequest = cancelUnsavedPrompt,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            containerColor = AssistantSurfaceElevated,
            tonalElevation = 6.dp,
            title = { Text("Save changes?") },
            text = { Text("You have unsaved Developer settings. Save them before leaving?") },
            confirmButton = {
                TextButton(enabled = !isSavingDeveloper, onClick = {
                    onSaveDeveloperChanges(
                        nameInput,
                        greetingInput,
                        keyInput,
                        embeddingKeyInput,
                        promptInput,
                        chatPromptInput,
                        documentFormatPromptInput,
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
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            containerColor = AssistantSurfaceElevated,
            tonalElevation = 6.dp,
            title = { Text("Remove API key?") },
            text = {
                Text(
                    if (BuildConfig.GEMINI_API_KEY.isNotBlank()) {
                        "The assistant will fall back to its built-in key. The active conversation, if any, isn't affected either way."
                    } else {
                        "The assistant won't be able to connect until a new key is entered. The active conversation, if any, isn't affected."
                    }
                )
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
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
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
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
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
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
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
    unfocusedLabelColor = AssistantAccent
)

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = com.assistant.app.ui.theme.AssistantTextPrimary,
        style = androidx.compose.material3.MaterialTheme.typography.labelLarge.copy(
            fontSize = 15.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
        )
    )
    Spacer(Modifier.height(8.dp))
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeneralSettingsContent(
    settings: AssistantSettings,
    onLiveModelSelected: (String) -> Unit,
    onVoiceSelected: (String) -> Unit,
    onLanguageSelected: (String) -> Unit,
    onMicSensitivitySelected: (MicSensitivity) -> Unit,
    onBargeInToggled: (Boolean) -> Unit
) {
    val micHints = mapOf(
        MicSensitivity.LOW to "Cuts background noise",
        MicSensitivity.MEDIUM to "Best for normal rooms",
        MicSensitivity.HIGH to "Picks up quiet voices"
    )
    Column {
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
                ?.let { "${it.name} · ${it.gender} · ${it.style}" } ?: settings.voiceName,
            options = AssistantOptions.voices.map { it.name to "${it.name} · ${it.gender} · ${it.style}" },
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
        SettingsHeading("Microphone sensitivity")
        CardChoiceRow(
            options = MicSensitivity.entries.map { CardOption(it.name, it.label, micHints.getValue(it)) },
            selectedId = settings.micSensitivity.name, cardWidth = 150.dp,
            onSelect = { name -> onMicSensitivitySelected(MicSensitivity.fromStoredValue(name)) }
        )
        Spacer(Modifier.height(22.dp))
        ToggleCard(
            title = "Barge-in",
            checked = settings.bargeInEnabled, onChange = onBargeInToggled
        )
        Spacer(Modifier.height(24.dp))
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

/** Chooses how the typed chat reaches Azure OpenAI: with just an API key, or with the resource's own endpoint. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AzureChatSection(azure: AzureConfig, onSave: (AzureConfig) -> Unit) {
    var mode by remember(azure) { mutableStateOf(azure.mode) }
    var endpoint by remember(azure) { mutableStateOf(azure.endpoint) }
    var key by remember(azure) { mutableStateOf(azure.apiKey) }
    var model by remember(azure) { mutableStateOf(azure.model) }
    var keyVisible by remember { mutableStateOf(false) }
    val changed = mode != azure.mode || endpoint != azure.endpoint || key != azure.apiKey || model != azure.model

    SectionLabel("Chat model (Azure)")
    Spacer(Modifier.height(8.dp))
    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = mode == AzureMode.OFF, onClick = { mode = AzureMode.OFF }, label = { Text("Gemini") })
        FilterChip(selected = mode == AzureMode.API_KEY, onClick = { mode = AzureMode.API_KEY }, label = { Text("Azure API key") })
        FilterChip(selected = mode == AzureMode.ENDPOINT, onClick = { mode = AzureMode.ENDPOINT }, label = { Text("Azure OpenAI endpoint") })
    }
    if (mode != AzureMode.OFF) {
        Spacer(Modifier.height(8.dp))
        if (mode == AzureMode.ENDPOINT) {
            OutlinedTextField(
                value = endpoint,
                onValueChange = { endpoint = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Endpoint") },
                placeholder = { Text("https://<resource>.openai.azure.com") },
                colors = assistantTextFieldColors()
            )
            Spacer(Modifier.height(8.dp))
        }
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Azure API key") },
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
        Spacer(Modifier.height(8.dp))
        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AZURE_CHAT_MODELS.forEach { (id, label) ->
                FilterChip(selected = model == id, onClick = { model = id }, label = { Text(label) })
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Deployment name") },
            colors = assistantTextFieldColors()
        )
    }
    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(
            enabled = changed && (mode == AzureMode.OFF || (key.isNotBlank() && model.isNotBlank() && (mode != AzureMode.ENDPOINT || endpoint.isNotBlank()))),
            onClick = { onSave(AzureConfig(mode, endpoint, key, model)) }
        ) { Text("Save chat model") }
    }
}


