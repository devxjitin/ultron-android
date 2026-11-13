package com.assistant.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.assistant.app.data.MicSensitivity
import kotlinx.coroutines.delay
import com.assistant.app.ui.components.HomeBackground
import com.assistant.app.ui.components.VoiceRibbon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.assistant.app.ui.theme.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.app.ui.components.AboutPage
import com.assistant.app.ui.components.DownloadsPage
import com.assistant.app.ui.components.StoragePage
import com.assistant.app.ui.components.SkillsPage
import com.assistant.app.data.SkillRegistry
import com.assistant.app.ui.components.UsagePage
import com.assistant.app.ui.components.AssistantToastHost
import com.assistant.app.ui.components.MemoryPage
import com.assistant.app.ui.components.HistoryPage
import com.assistant.app.ui.components.FeatureRequest
import com.assistant.app.ui.components.FeatureRequestPage
import com.assistant.app.ui.components.HelpPage
import com.assistant.app.ui.components.ProfilePage
import com.assistant.app.ui.components.ImageOverlay
import com.assistant.app.ui.components.ImagesPage
import com.assistant.app.ui.components.AppDrawerContainerColor
import com.assistant.app.ui.components.AppDrawerContent
import com.assistant.app.ui.theme.ThemeMode
import com.assistant.app.ui.components.AppTopBar
import com.assistant.app.ui.components.AppTopBarHeight
import com.assistant.app.ui.components.ConnectorsPage
import com.assistant.app.ui.components.MicButton
import androidx.activity.compose.BackHandler
import com.assistant.app.ui.components.NavTab
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.assistant.app.ui.components.SettingsPage
import com.assistant.app.ui.theme.AssistantAccent
import com.assistant.app.ui.theme.AssistantError
import com.assistant.app.ui.theme.AssistantTextMuted
import com.assistant.app.ui.theme.AssistantTextSecondary
import com.assistant.app.viewmodel.AppPage
import com.assistant.app.viewmodel.AppNotificationType
import com.assistant.app.data.DeviceTool
import com.assistant.app.viewmodel.AssistantUiState
import com.assistant.app.viewmodel.VoiceState

private val SETTINGS_PAGES = setOf(AppPage.SETTINGS, AppPage.KNOWLEDGE, AppPage.DEVELOPER)

@Composable
fun AssistantScreen(
    uiState: AssistantUiState,
    hasMicPermission: Boolean,
    micPermissionPermanentlyDenied: Boolean = false,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    voiceLevel: () -> Float = { 0f },
    license: LicenseSummary? = null,
    actions: AssistantActions,
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(com.assistant.app.ui.theme.AssistantBackground)) {
        val availableHeight = maxHeight
        val strings = visitorStrings()
        val isDarkTheme = themeMode == ThemeMode.DARK || (themeMode == ThemeMode.SYSTEM && androidx.compose.foundation.isSystemInDarkTheme())
        val compact = availableHeight < 480.dp
        // NavTab and AppPage share their names, so the menu row for a page is found by name.
        // Developer is part of Settings, so it highlights the Settings row.
        val currentTab = NavTab.valueOf(uiState.page.name).let {
            when (it) { NavTab.DEVELOPER -> NavTab.SETTINGS; else -> it }
        }
        // Knowledge has two parts: the documents and the pictures Ultron can show.
        var knowledgeSection by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
        // Settings, Knowledge and Developer are one page. While it fades out the state already points elsewhere,
        // so it keeps showing the sub-page it was last on instead of jumping to another one mid-animation.
        val lastSettingsPage = remember { arrayOf(AppPage.SETTINGS) }
        if (uiState.page in SETTINGS_PAGES) lastSettingsPage[0] = uiState.page
        // A live session keeps running behind the other pages without showing anything there.
        // Space the pages leave free at the top for the slim top bar.
        val topReserve = AppTopBarHeight
        // The request form (one screen for a skill, one for a connector) opens from the Help & support page.
        var requestType by remember { mutableStateOf<String?>(null) }
        var requestSending by remember { mutableStateOf(false) }
        var requestError by remember { mutableStateOf<String?>(null) }
        val openRequest: (String) -> Unit = { requestError = null; requestType = it }
        val sessionActive = uiState.voiceState != VoiceState.IDLE && uiState.voiceState != VoiceState.ERROR
        // A new line for every session: it changes whenever a session starts or ends.
        val greeting = remember(sessionActive) { com.assistant.app.data.randomHomeGreeting() }
        var sessionStartMs by remember { mutableStateOf<Long?>(null) }
        LaunchedEffect(sessionActive) {
            sessionStartMs = if (sessionActive) System.currentTimeMillis() else null
        }
        // Exits that would drop an unsaved Developer draft wait for Save/Discard first.
        // Settings <-> Knowledge keeps the same page (and draft), so it isn't guarded.
        var settingsDirty by remember { mutableStateOf(false) }
        var pendingNav by remember { mutableStateOf<(() -> Unit)?>(null) }
        val guardedNav: (() -> Unit) -> Unit = { action ->
            when {
                uiState.page in SETTINGS_PAGES && settingsDirty -> pendingNav = action
                else -> action()
            }
        }
        val scope = rememberCoroutineScope()
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        // Back from any other page goes to the home screen.
        BackHandler(enabled = currentTab != NavTab.HOME && !drawerState.isOpen) { guardedNav(actions.onHomeTapped) }
        // Back with the menu open closes the menu instead of leaving the app.
        BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
        // Inside Connectors, back from the browse view returns to the added connectors first.
        val wide = maxWidth >= 900.dp
        val drawerContent: @Composable (Modifier) -> Unit = { drawerModifier ->
            AppDrawerContent(
                selected = currentTab,
                assistantName = uiState.settings.assistantName,
                userName = license?.status?.name?.takeIf { it.isNotBlank() } ?: uiState.settings.ownerName,
                onClose = { scope.launch { drawerState.close() } },
                onSelect = { tab ->
                    scope.launch { drawerState.close() }
                    if (tab != currentTab) when (tab) {
                        NavTab.HOME -> guardedNav(actions.onHomeTapped)
                        NavTab.USAGE -> guardedNav(actions.onUsageTapped)
                        NavTab.STORAGE -> guardedNav(actions.onStorageTapped)
                        NavTab.DOWNLOADS -> guardedNav(actions.onDownloadsTapped)
                        NavTab.SKILLS -> guardedNav(actions.onSkillsTapped)
                        NavTab.KNOWLEDGE -> guardedNav(actions.onKnowledgeTapped)
                        NavTab.CONNECTORS -> guardedNav(actions.onConnectorsTapped)
                        NavTab.MEMORY -> guardedNav(actions.onMemoryTapped)
                        NavTab.HISTORY -> guardedNav(actions.onHistoryTapped)
                        NavTab.HELP -> guardedNav(actions.onHelpTapped)
                        NavTab.SETTINGS -> guardedNav(actions.onSettingsTapped)
                        NavTab.DEVELOPER -> guardedNav(actions.onDeveloperTapped)
                        NavTab.ABOUT -> guardedNav(actions.onAboutTapped)
                        NavTab.PROFILE -> guardedNav(actions.onProfileTapped)
                    }
                },
                modifier = drawerModifier.windowInsetsPadding(WindowInsets.displayCutout)
            )
        }
        val main: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
        AnimatedContent(
            targetState = currentTab,
            // Settings, Knowledge and Developer share one page, so switching between them keeps an unsaved draft.
            contentKey = { if (it == NavTab.KNOWLEDGE || it == NavTab.DEVELOPER) NavTab.SETTINGS else it },
            transitionSpec = {
                (fadeIn(tween(240, delayMillis = 70)) + slideInVertically(tween(280)) { it / 30 }) togetherWith fadeOut(tween(110))
            },
            modifier = Modifier.fillMaxSize(),
            label = "page"
        ) { tab ->
        Box(Modifier.fillMaxSize()) {
            if (tab == NavTab.HOME) HomeBackground(isDarkTheme, Modifier.fillMaxSize())
            if (tab == NavTab.HOME) {
                Column(Modifier.fillMaxSize().padding(top = topReserve, bottom = 112.dp)
                    .verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(if (compact) 0.dp else availableHeight * 0.13f))
                    Text(uiState.settings.assistantName, color = AssistantTextMuted, style = MaterialTheme.typography.labelLarge,
                        letterSpacing = 1.2.sp)
                    Spacer(Modifier.height(if (compact) 12.dp else 28.dp))
                    when (uiState.voiceState) {
                        VoiceState.IDLE, VoiceState.ERROR -> Text(
                            text = greeting,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light),
                            fontSize = if (compact) 24.sp else 30.sp,
                            lineHeight = if (compact) 32.sp else 40.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth().padding(horizontal = 28.dp)
                        )
                        VoiceState.CONNECTING, VoiceState.RESETTING -> Text(
                            text = strings.justAMoment,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light),
                            fontSize = if (compact) 24.sp else 30.sp,
                            lineHeight = if (compact) 32.sp else 40.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth().padding(horizontal = 28.dp)
                        )
                        VoiceState.LISTENING, VoiceState.PROCESSING, VoiceState.SPEAKING ->
                            SessionTimer(sessionStartTimeMs = sessionStartMs, compact = compact)
                    }
                    VoiceRibbon(uiState.voiceState, voiceLevel,
                        Modifier.widthIn(max = 900.dp).fillMaxWidth()
                            .height(if (compact) 80.dp else (availableHeight * 0.28f).coerceIn(160.dp, 270.dp)))
                    StatusLine(uiState, strings, hasMicPermission, micPermissionPermanentlyDenied)
                    uiState.activeTool?.takeIf { uiState.voiceState != VoiceState.SPEAKING }?.let { ToolChip(it) }
                    Spacer(Modifier.height(if (compact) 12.dp else 28.dp))
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        if (uiState.voiceState in setOf(VoiceState.LISTENING, VoiceState.PROCESSING, VoiceState.SPEAKING)) {
                            IconButton(onClick = actions.onMuteToggled) {
                                Icon(
                                    imageVector = if (uiState.isMicMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                                    contentDescription = if (uiState.isMicMuted) strings.unmute else strings.mute,
                                    tint = if (uiState.isMicMuted) AssistantError else AssistantTextSecondary
                                )
                            }
                        }
                    }
                }
            }

            if (tab == NavTab.MEMORY) {
                MemoryPage(
                    memories = uiState.memories,
                    enabled = uiState.settings.memoryEnabled,
                    onEnabledChange = actions.onMemoryEnabledToggled,
                    onAdd = actions.onAddMemory,
                    onUpdate = actions.onUpdateMemory,
                    onDelete = actions.onDeleteMemory,
                    onClearAll = actions.onClearMemory,
                    modifier = Modifier.padding(top = topReserve),
                    removedByAssistant = uiState.removedMemories,
                    onRestore = actions.onRestoreMemory
                )
            }

            if (tab == NavTab.HISTORY) {
                HistoryPage(
                    turns = uiState.pastConversations,
                    total = uiState.pastConversationCount,
                    onDeleteSession = actions.onDeletePastSession,
                    onClearAll = actions.onClearPastConversations,
                    onShowOlder = actions.onShowOlderConversations,
                    modifier = Modifier.padding(top = topReserve)
                )
            }

            if (tab == NavTab.PROFILE) {
                ProfilePage(
                    license = license,
                    ownerName = uiState.settings.ownerName,
                    onRefresh = actions.onRefreshLicense,
                    onRenew = actions.onRenewLicense,
                    onRemoveKey = actions.onRemoveLicense,
                    modifier = Modifier.padding(top = topReserve)
                )
            }

            if (tab == NavTab.HELP) {
                HelpPage(onRequest = openRequest, modifier = Modifier.padding(top = topReserve))
            }

            if (tab == NavTab.CONNECTORS) {
                ConnectorsPage(
                    enabledTools = uiState.settings.enabledDeviceTools,
                    onToolsEnabled = actions.onConnectorToolsEnabled,
                    onRequestPermissions = actions.onRequestConnectorPermissions,
                    modifier = Modifier.padding(top = topReserve)
                )
            }

            if (tab in listOf(NavTab.SKILLS, NavTab.USAGE, NavTab.STORAGE, NavTab.DOWNLOADS, NavTab.ABOUT)) {
                val pageModifier = Modifier.padding(top = topReserve)
                when (tab) {
                    NavTab.SKILLS -> {
                        val skills by SkillRegistry.entries.collectAsState()
                        SkillsPage(skills, actions.onSkillToggled, actions.onSkillSaved, actions.onSkillDeleted, pageModifier)
                    }
                    NavTab.USAGE -> UsagePage(uiState.usageDays, pageModifier)
                    NavTab.DOWNLOADS -> DownloadsPage(pageModifier)
                    NavTab.STORAGE -> StoragePage(
                        memoryCount = uiState.memories.size,
                        knowledgeCount = uiState.knowledgeDocuments.size,
                        cacheBytes = uiState.cacheBytes,
                        hasUsage = uiState.usageDays.isNotEmpty(),
                        onClearCache = actions.onClearCache,
                        onClearMemory = actions.onClearMemory,
                        onResetUsage = actions.onResetUsage,
                        modifier = pageModifier,
                        pastConversationCount = uiState.pastConversationCount,
                        pastConversationBytes = uiState.pastConversationBytes,
                        memoryBytes = uiState.memories.sumOf { it.text.length.toLong() },
                        knowledgeBytes = uiState.knowledgeDocuments.sumOf { it.text.length.toLong() },
                        onClearPastConversations = actions.onClearPastConversations,
                        imageCount = uiState.images.size,
                        imageBytes = uiState.imageBytes,
                        onClearImages = actions.onClearImages
                    )
                    NavTab.ABOUT -> AboutPage(
                        assistantName = uiState.settings.assistantName,
                        updateState = uiState.updateCheckState,
                        availableUpdate = uiState.availableUpdate,
                        lastCheckedAt = uiState.lastUpdateCheckAt,
                        updateError = uiState.updateCheckError,
                        downloadProgress = uiState.updateDownloadProgress,
                        onCheckForUpdates = actions.onCheckForUpdates,
                        onUpdateNow = actions.onUpdateNow,
                        modifier = pageModifier
                    )
                    else -> Unit
                }
            }

            if (tab in listOf(NavTab.SETTINGS, NavTab.KNOWLEDGE, NavTab.DEVELOPER)) {
              val onKnowledge = lastSettingsPage[0] == AppPage.KNOWLEDGE
              Column(Modifier.fillMaxSize().padding(top = topReserve)) {
                if (onKnowledge) KnowledgeSectionSwitch(knowledgeSection) { knowledgeSection = it }
                if (onKnowledge && knowledgeSection == 1) {
                val filesDir = androidx.compose.ui.platform.LocalContext.current.filesDir
                ImagesPage(
                    images = uiState.images,
                    pendingUri = uiState.pendingImageUri,
                    isSaving = uiState.isSavingImage,
                    imageFile = { com.assistant.app.data.ImageLibrary.fileIn(filesDir, it) },
                    onPick = actions.onPickImage,
                    onSave = actions.onSaveImage,
                    onCancelPending = actions.onCancelPendingImage,
                    onUpdate = actions.onUpdateImage,
                    onDelete = actions.onDeleteImage,
                    modifier = Modifier.weight(1f)
                )
                } else
                SettingsPage(
                    modifier = Modifier.weight(1f),
                    page = lastSettingsPage[0],
                    currentApiKey = uiState.currentApiKey,
                    currentEmbeddingApiKey = uiState.currentEmbeddingApiKey,
                    settings = uiState.settings,
                    onLiveModelSelected = actions.onLiveModelSelected,
                    onVoiceSelected = actions.onVoiceSelected,
                    onLanguageSelected = actions.onLanguageSelected,
                    onMicSensitivitySelected = actions.onMicSensitivitySelected,
                    onBargeInToggled = actions.onBargeInToggled,
                    onAnnounceNotificationsToggled = actions.onAnnounceNotificationsToggled,
                    onDecisionsRerankToggled = actions.onDecisionsRerankToggled,
                    onDecisionsMemoryToggled = actions.onDecisionsMemoryToggled,
                    onDecisionsActionCheckToggled = actions.onDecisionsActionCheckToggled,
                    onDecisionsScreenCheckToggled = actions.onDecisionsScreenCheckToggled,
                    onDecisionsSkillToggled = actions.onDecisionsSkillToggled,
                    onOwnerNameChanged = actions.onOwnerNameChanged,
                    onPersonalitySelected = actions.onPersonalitySelected,
                    onClearApiKey = actions.onClearApiKey,
                    onClearEmbeddingApiKey = actions.onClearEmbeddingApiKey,
                    onSaveDeveloperChanges = actions.onSaveDeveloperChanges,
                    isSavingDeveloper = uiState.isSavingDeveloperSettings,
                    knowledgeDocuments = uiState.knowledgeDocuments,
                    isSavingKnowledgeDocument = uiState.isSavingKnowledgeDocument,
                    knowledgePreparationStage = uiState.knowledgePreparationStage,
                    savingKnowledgeDocumentTarget = uiState.savingKnowledgeDocumentTarget,
                    uploadedChunks = uiState.uploadedChunks,
                    totalChunks = uiState.totalChunks,
                    knowledgeDocumentError = uiState.knowledgeDocumentError,
                    onAddKnowledgeDocument = actions.onAddKnowledgeDocument,
                    onUpdateKnowledgeDocument = actions.onUpdateKnowledgeDocument,
                    onDeleteKnowledgeDocument = actions.onDeleteKnowledgeDocument,
                    knowledgeStoreCorrupt = uiState.knowledgeStoreCorrupt,
                    onResetKnowledge = actions.onResetKnowledge,
                    onUploadPdfFile = actions.onUploadPdfFile,
                    onDismissKnowledgeDocumentError = actions.onDismissKnowledgeDocumentError,
                    onDismiss = actions.onDismissSettings,
                    onDirtyChanged = { settingsDirty = it },
                    pendingNavigation = pendingNav,
                    onPendingNavigationHandled = { pendingNav = null }
                )
              }
            }

        }
        }

        AnimatedVisibility(
            visible = currentTab == NavTab.HOME,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            MicButton(
                voiceState = uiState.voiceState,
                contextTokens = uiState.liveContextTokens,
                onClick = {
                    when {
                        hasMicPermission -> actions.onMicTapped()
                        micPermissionPermanentlyDenied -> actions.onOpenAppSettings()
                        else -> actions.onRequestPermission()
                    }
                },
                modifier = Modifier.padding(bottom = 28.dp)
            )
        }

        AppTopBar(
            title = if (currentTab == NavTab.HOME) "" else currentTab.label,
            showMenuButton = !wide,
            onMenuClick = { scope.launch { drawerState.open() } },
            onToggleTheme = if (currentTab == NavTab.HOME) {
                { actions.onThemeModeChange(if (isDarkTheme) ThemeMode.LIGHT else ThemeMode.DARK) }
            } else null,
            isDarkTheme = isDarkTheme,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Above the top bar, so the picture's close button is never covered by the bar's buttons.
        uiState.shownImage?.let { shown ->
            androidx.compose.runtime.key(shown.id) { ImageOverlay(shown.uri, shown.title, actions.onDismissShownImage) }
        }

        requestType?.let { kind ->
            FeatureRequestPage(
                type = kind, sending = requestSending, error = requestError,
                onSend = { form ->
                    requestSending = true
                    requestError = null
                    actions.onSendFeatureRequest(form) { error ->
                        requestSending = false
                        if (error == null) requestType = null else requestError = error
                    }
                },
                onBack = { requestType = null }
            )
        }

        com.assistant.app.ui.components.WhatsNewCard(uiState.whatsNew, actions.onDismissWhatsNew)

        uiState.updateInfo?.let { update ->
            val progress = uiState.updateDownloadProgress
            AlertDialog(
                onDismissRequest = { if (progress == null) actions.onDismissUpdate() },
                title = { Text("Update available") },
                text = {
                    // The version and the progress bar always stay on screen; the release notes open only on request.
                    var showNotes by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
                    Column {
                        Text(
                            buildString {
                                append("Version ${update.versionName}")
                                if (update.sizeBytes > 0) append(" (${update.sizeBytes / (1024 * 1024)} MB)")
                            }
                        )
                        if (progress != null) {
                            Spacer(Modifier.height(16.dp))
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { progress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(6.dp))
                            Text("Downloading ${(progress * 100).toInt()}%")
                        }
                        if (update.notes.isNotBlank()) {
                            TextButton(onClick = { showNotes = !showNotes }) { Text(if (showNotes) "Hide info" else "More info") }
                            if (showNotes) {
                                Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                                    Text(update.notes)
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = actions.onInstallUpdate, enabled = progress == null) { Text("Update") } },
                dismissButton = { TextButton(onClick = actions.onDismissUpdate, enabled = progress == null) { Text("Later") } }
            )
        }

        // Drawn last so it sits above every page without covering the page heading.
        AssistantToastHost(
            message = uiState.toastMessage,
            type = uiState.toastType,
            toastId = uiState.toastId,
            onDismiss = actions.onDismissToast,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (currentTab == NavTab.HOME) 116.dp else 24.dp)
        )

        if (uiState.restartRequestId > 0) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Restarting app") },
                text = { Text("Developer changes are saved. Applying them now…") },
                confirmButton = {},
                dismissButton = {}
            )
        }


        }
        }

        if (wide) {
            PermanentNavigationDrawer(drawerContent = {
                PermanentDrawerSheet(drawerContainerColor = AppDrawerContainerColor, windowInsets = WindowInsets(0), modifier = Modifier.width(280.dp)) {
                    drawerContent(Modifier.fillMaxSize())
                }
            }) { main() }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerShape = androidx.compose.foundation.shape.RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                        drawerContainerColor = AppDrawerContainerColor, windowInsets = WindowInsets(0), modifier = Modifier.width(300.dp)
                    ) {
                        drawerContent(Modifier.fillMaxSize())
                    }
                }
            ) { main() }
        }
    }
}

/** Two pills at the top of Knowledge: the documents Ultron can search, and the pictures it can show. */
@Composable
private fun KnowledgeSectionSwitch(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Documents", "Pictures").forEachIndexed { index, label ->
            val on = index == selected
            Box(
                Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                    .background(if (on) com.assistant.app.ui.theme.AssistantSelectedBg else androidx.compose.ui.graphics.Color.Transparent)
                    .border(
                        androidx.compose.foundation.BorderStroke(1.dp, com.assistant.app.ui.theme.AssistantOutline),
                        androidx.compose.foundation.shape.RoundedCornerShape(50)
                    )
                    .clickable { onSelect(index) }
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text(
                    label, fontSize = 14.sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                    color = if (on) com.assistant.app.ui.theme.AssistantTextPrimary else AssistantTextSecondary
                )
            }
        }
    }
}

/** Shows which tool the assistant is running, so tool calls are visible and not only "thinking". */
@Composable
private fun ToolChip(name: String) {
    androidx.compose.foundation.layout.Row(
        Modifier.padding(top = 14.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
            .background(AssistantAccent.copy(alpha = 0.16f))
            .border(androidx.compose.foundation.BorderStroke(1.dp, AssistantAccent.copy(alpha = 0.5f)),
                androidx.compose.foundation.shape.RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.CircularProgressIndicator(
            modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = AssistantAccent
        )
        Spacer(Modifier.width(10.dp))
        Text("Using: $name", color = com.assistant.app.ui.theme.AssistantTextPrimary, fontSize = 13.sp,
            fontWeight = FontWeight.Medium, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

@Composable
private fun StatusLine(
    uiState: AssistantUiState,
    strings: VisitorStrings,
    hasMicPermission: Boolean,
    micPermissionPermanentlyDenied: Boolean = false
) {
    val (label, color) = when {
        !hasMicPermission && micPermissionPermanentlyDenied -> strings.micDenied to AssistantError
        !hasMicPermission -> strings.micRationale to AssistantError
        uiState.voiceState == VoiceState.ERROR ->
            (uiState.errorMessage ?: "Something went wrong") to AssistantError
        !uiState.hasApiKey -> "The assistant isn't set up yet. Add a Gemini API key in Settings." to AssistantError
        uiState.voiceState == VoiceState.CONNECTING -> strings.connectingCancel to AssistantTextSecondary
        uiState.voiceState == VoiceState.RESETTING -> strings.refreshingCancel to AssistantTextSecondary
        uiState.isMicMuted && uiState.voiceState == VoiceState.LISTENING -> strings.micMuted to AssistantError
        uiState.voiceState == VoiceState.LISTENING -> strings.listening to AssistantAccent
        uiState.voiceState == VoiceState.PROCESSING -> strings.thinking to AssistantTextSecondary
        uiState.voiceState == VoiceState.SPEAKING -> strings.speaking to AssistantAccent
        else -> strings.tapToSpeak to AssistantTextSecondary
    }

    Text(
        text = label,
        color = color,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth(0.85f)
    )
}

/** Elapsed time for the active session, shown as m:ss. */
@Composable
private fun SessionTimer(sessionStartTimeMs: Long?, compact: Boolean) {
    var elapsedSeconds by remember(sessionStartTimeMs) { mutableLongStateOf(0L) }
    LaunchedEffect(sessionStartTimeMs) {
        val start = sessionStartTimeMs ?: return@LaunchedEffect
        while (true) {
            elapsedSeconds = (System.currentTimeMillis() - start) / 1000
            delay(1000)
        }
    }
    Text(
        text = "%d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60),
        color = AssistantAccent,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light),
        fontSize = if (compact) 28.sp else 36.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 28.dp)
    )
}
