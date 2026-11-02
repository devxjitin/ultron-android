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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.assistant.app.ui.components.StoragePage
import com.assistant.app.ui.components.UsagePage
import com.assistant.app.ui.components.AssistantToastHost
import com.assistant.app.ui.components.MemoryPage
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

private val SETTINGS_PAGES = setOf(AppPage.SETTINGS, AppPage.KNOWLEDGE)

@Composable
fun AssistantScreen(
    uiState: AssistantUiState,
    hasMicPermission: Boolean,
    micPermissionPermanentlyDenied: Boolean = false,
    onMicTapped: () -> Unit,
    onMuteToggled: () -> Unit = {},
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit = {},
    onSettingsTapped: () -> Unit,
    onAboutTapped: () -> Unit = {},
    onKnowledgeTapped: () -> Unit = {},
    onHomeTapped: () -> Unit = {},
    onConnectorsTapped: () -> Unit = {},
    onInstallUpdate: () -> Unit = {},
    onDismissUpdate: () -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    onUpdateNow: () -> Unit = {},
    onUsageTapped: () -> Unit = {},
    onStorageTapped: () -> Unit = {},
    onClearCache: () -> Unit = {},
    onResetUsage: () -> Unit = {},
    onMemoryTapped: () -> Unit = {},
    onMemoryEnabledToggled: (Boolean) -> Unit = {},
    onAddMemory: (String) -> Unit = {},
    onUpdateMemory: (String, String) -> Unit = { _, _ -> },
    onDeleteMemory: (String) -> Unit = {},
    onClearMemory: () -> Unit = {},
    onRestoreMemory: (String) -> Unit = {},
    onStartGithubSignIn: () -> Unit = {},
    onCancelGithubSignIn: () -> Unit = {},
    onDisconnectGithub: () -> Unit = {},
    onConnectorToolsEnabled: (List<DeviceTool>, Boolean, String) -> Unit = { _, _, _ -> },
    onRequestConnectorPermissions: (List<String>) -> Unit = {},
    onDismissSettings: () -> Unit,
    onVoiceSelected: (String) -> Unit,
    onLanguageSelected: (String) -> Unit,
    onMicSensitivitySelected: (MicSensitivity) -> Unit,
    onBargeInToggled: (Boolean) -> Unit,
    onOwnerNameChanged: (String) -> Unit = {},
    onAddKnowledgeDocument: (String, String) -> Unit,
    onUpdateKnowledgeDocument: (String, String, String) -> Unit,
    onDeleteKnowledgeDocument: (String) -> Unit,
    onResetKnowledge: () -> Unit,
    onUploadMarkdownFile: () -> Unit,
    onDismissKnowledgeDocumentError: () -> Unit,
    onDismissToast: () -> Unit,
    voiceLevel: () -> Float = { 0f },
    account: AccountSummary? = null,
    onSignOut: () -> Unit = {},
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(com.assistant.app.ui.theme.AssistantBackground)) {
        val availableHeight = maxHeight
        val strings = visitorStrings()
        val isDarkTheme = themeMode == ThemeMode.DARK || (themeMode == ThemeMode.SYSTEM && androidx.compose.foundation.isSystemInDarkTheme())
        val compact = availableHeight < 480.dp
        // NavTab and AppPage share their names, so the menu row for a page is found by name.
        val currentTab = NavTab.valueOf(uiState.page.name)
        // Settings and Knowledge are one page. While it fades out the state already points elsewhere,
        // so it keeps showing the sub-page it was last on instead of jumping to another one mid-animation.
        val lastSettingsPage = remember { arrayOf(AppPage.SETTINGS) }
        if (uiState.page in SETTINGS_PAGES) lastSettingsPage[0] = uiState.page
        // Space the pages leave free at the top for the slim top bar.
        val topReserve = AppTopBarHeight
        val sessionActive = uiState.voiceState != VoiceState.IDLE && uiState.voiceState != VoiceState.ERROR
        var sessionStartMs by remember { mutableStateOf<Long?>(null) }
        LaunchedEffect(sessionActive) {
            sessionStartMs = if (sessionActive) System.currentTimeMillis() else null
        }
        val scope = rememberCoroutineScope()
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        // Back from any other page goes to the home screen.
        BackHandler(enabled = currentTab != NavTab.HOME && !drawerState.isOpen) { onHomeTapped() }
        // Back with the menu open closes the menu instead of leaving the app.
        BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
        // Inside Connectors, back from the browse view returns to the added connectors first.
        val wide = maxWidth >= 900.dp
        val drawerContent: @Composable (Modifier) -> Unit = { drawerModifier ->
            AppDrawerContent(
                selected = currentTab,
                assistantName = uiState.settings.assistantName,
                onSelect = { tab ->
                    scope.launch { drawerState.close() }
                    if (tab != currentTab) when (tab) {
                        NavTab.HOME -> onHomeTapped()
                        NavTab.USAGE -> onUsageTapped()
                        NavTab.STORAGE -> onStorageTapped()
                        NavTab.KNOWLEDGE -> onKnowledgeTapped()
                        NavTab.CONNECTORS -> onConnectorsTapped()
                        NavTab.MEMORY -> onMemoryTapped()
                        NavTab.SETTINGS -> onSettingsTapped()
                        NavTab.ABOUT -> onAboutTapped()
                    }
                },
                modifier = drawerModifier.windowInsetsPadding(WindowInsets.displayCutout)
            )
        }
        val main: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
        AnimatedContent(
            targetState = currentTab,
            // Settings and Knowledge share one page.
            contentKey = { if (it == NavTab.KNOWLEDGE) NavTab.SETTINGS else it },
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
                            text = uiState.settings.greeting,
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
                            IconButton(onClick = onMuteToggled) {
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
                    onEnabledChange = onMemoryEnabledToggled,
                    onAdd = onAddMemory,
                    onUpdate = onUpdateMemory,
                    onDelete = onDeleteMemory,
                    onClearAll = onClearMemory,
                    modifier = Modifier.padding(top = topReserve),
                    removedByAssistant = uiState.removedMemories,
                    onRestore = onRestoreMemory
                )
            }

            if (tab == NavTab.CONNECTORS) {
                ConnectorsPage(
                    issues = uiState.connectorIssues,
                    githubLogin = uiState.settings.githubLogin,
                    deviceCode = uiState.githubDeviceCode,
                    enabledTools = uiState.settings.enabledDeviceTools,
                    onStartGithubSignIn = onStartGithubSignIn,
                    onCancelGithubSignIn = onCancelGithubSignIn,
                    onDisconnectGithub = onDisconnectGithub,
                    githubTools = uiState.settings.githubTools,
                    onToolsEnabled = onConnectorToolsEnabled,
                    onRequestPermissions = onRequestConnectorPermissions,
                    modifier = Modifier.padding(top = topReserve)
                )
            }

            if (tab in listOf(NavTab.USAGE, NavTab.STORAGE, NavTab.ABOUT)) {
                val pageModifier = Modifier.padding(top = topReserve)
                when (tab) {
                    NavTab.USAGE -> UsagePage(uiState.usageDays, pageModifier)
                    NavTab.STORAGE -> StoragePage(
                        memoryCount = uiState.memories.size,
                        knowledgeCount = uiState.knowledgeDocuments.size,
                        cacheBytes = uiState.cacheBytes,
                        hasUsage = uiState.usageDays.isNotEmpty(),
                        onClearCache = onClearCache,
                        onClearMemory = onClearMemory,
                        onResetUsage = onResetUsage,
                        modifier = pageModifier
                    )
                    NavTab.ABOUT -> AboutPage(
                        assistantName = uiState.settings.assistantName,
                        updateState = uiState.updateCheckState,
                        availableUpdate = uiState.availableUpdate,
                        lastCheckedAt = uiState.lastUpdateCheckAt,
                        updateError = uiState.updateCheckError,
                        downloadProgress = uiState.updateDownloadProgress,
                        onCheckForUpdates = onCheckForUpdates,
                        onUpdateNow = onUpdateNow,
                        modifier = pageModifier
                    )
                    else -> Unit
                }
            }

            if (tab in listOf(NavTab.SETTINGS, NavTab.KNOWLEDGE)) {
                SettingsPage(
                    modifier = Modifier.padding(top = topReserve),
                    page = lastSettingsPage[0],
                    settings = uiState.settings,
                    onVoiceSelected = onVoiceSelected,
                    onLanguageSelected = onLanguageSelected,
                    onMicSensitivitySelected = onMicSensitivitySelected,
                    onBargeInToggled = onBargeInToggled,
                    onOwnerNameChanged = onOwnerNameChanged,
                    knowledgeDocuments = uiState.knowledgeDocuments,
                    isSavingKnowledgeDocument = uiState.isSavingKnowledgeDocument,
                    knowledgePreparationStage = uiState.knowledgePreparationStage,
                    savingKnowledgeDocumentTarget = uiState.savingKnowledgeDocumentTarget,
                    uploadedChunks = uiState.uploadedChunks,
                    totalChunks = uiState.totalChunks,
                    knowledgeDocumentError = uiState.knowledgeDocumentError,
                    onAddKnowledgeDocument = onAddKnowledgeDocument,
                    onUpdateKnowledgeDocument = onUpdateKnowledgeDocument,
                    onDeleteKnowledgeDocument = onDeleteKnowledgeDocument,
                    knowledgeStoreCorrupt = uiState.knowledgeStoreCorrupt,
                    onResetKnowledge = onResetKnowledge,
                    onUploadMarkdownFile = onUploadMarkdownFile,
                    onDismissKnowledgeDocumentError = onDismissKnowledgeDocumentError,
                    onDismiss = onDismissSettings,
                    account = account,
                    onSignOut = onSignOut
                )
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
                onClick = {
                    when {
                        hasMicPermission -> onMicTapped()
                        micPermissionPermanentlyDenied -> onOpenAppSettings()
                        else -> onRequestPermission()
                    }
                },
                accent = true,
                modifier = Modifier.padding(bottom = 28.dp)
            )
        }

        AppTopBar(
            title = if (currentTab == NavTab.HOME) "" else currentTab.label,
            showMenuButton = !wide,
            onMenuClick = { scope.launch { drawerState.open() } },
            onToggleTheme = if (currentTab == NavTab.HOME) {
                { onThemeModeChange(if (isDarkTheme) ThemeMode.LIGHT else ThemeMode.DARK) }
            } else null,
            isDarkTheme = isDarkTheme,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        uiState.updateInfo?.let { update ->
            val progress = uiState.updateDownloadProgress
            AlertDialog(
                onDismissRequest = { if (progress == null) onDismissUpdate() },
                title = { Text("Update available") },
                text = {
                    Text(
                        buildString {
                            append("Version ${update.versionName}")
                            if (update.sizeBytes > 0) append(" (${update.sizeBytes / (1024 * 1024)} MB)")
                            if (update.notes.isNotBlank()) append("\n\n").append(update.notes)
                            if (progress != null) append("\n\nDownloading ${(progress * 100).toInt()}%")
                        }
                    )
                },
                confirmButton = { TextButton(onClick = onInstallUpdate, enabled = progress == null) { Text("Update") } },
                dismissButton = { TextButton(onClick = onDismissUpdate, enabled = progress == null) { Text("Later") } }
            )
        }

        // Drawn last so it sits above every page without covering the page heading.
        AssistantToastHost(
            message = uiState.toastMessage,
            type = uiState.toastType,
            toastId = uiState.toastId,
            onDismiss = onDismissToast,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 116.dp)
        )

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
        !uiState.hasApiKey -> "This build is not set up to reach the assistant yet." to AssistantError
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
