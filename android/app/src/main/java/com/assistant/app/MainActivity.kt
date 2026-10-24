package com.assistant.app

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.assistant.app.ui.AssistantScreen
import com.assistant.app.ui.theme.AssistantTheme
import com.assistant.app.ui.theme.ActivePalette
import com.assistant.app.ui.theme.ThemeMode
import com.assistant.app.ui.theme.ThemePrefs
import com.assistant.app.ui.theme.applyWindowColors
import com.assistant.app.ui.theme.paletteFor
import com.assistant.app.viewmodel.AssistantViewModel

/** A missing declared size passes here; the streaming read enforces the limit in that case. */
internal fun isPdfWithinLimit(declaredSizeBytes: Long?, maxBytes: Int = 10 * 1024 * 1024): Boolean =
    declaredSizeBytes == null || declaredSizeBytes <= maxBytes

class MainActivity : ComponentActivity() {

    companion object {
        /** Inline base64 PDF requests must stay well under Gemini's ~20 MB request limit. */
        const val MAX_PDF_BYTES = 10 * 1024 * 1024
    }

    private class PdfTooLargeException : IOException("PDF exceeds 10 MB")

    // Held at Activity scope (not just inside the Compose tree) so onStart/onStop below can
    // reach the same instance Compose is observing.
    private val viewModel: AssistantViewModel by viewModels()

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /** Recreates the task after a saved Developer configuration so the next session gets only
     *  the newly persisted settings and no old Live connection remains active. */
    private fun restartApp() {
        viewModel.onAppBackgrounded()
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(launchIntent)
        finishAffinity()
    }

    /** Reads a bounded PDF intact so Gemini can process its visual document context directly. */
    private fun readKnowledgePdf(uri: Uri): Pair<String, ByteArray>? {
        var declaredSize: Long? = null
        val displayName = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) declaredSize = cursor.getLong(sizeIndex)
                if (nameIndex >= 0) cursor.getString(nameIndex) else null
            } else null
        }
        if (displayName?.substringAfterLast('.', "")?.lowercase() != "pdf") return null
        // Reject early from the declared size so an oversized file is never read into memory.
        if (!isPdfWithinLimit(declaredSize, MAX_PDF_BYTES)) throw PdfTooLargeException()
        val bytes = contentResolver.openInputStream(uri)?.use { stream ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                if (output.size() + count > MAX_PDF_BYTES) throw PdfTooLargeException()
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        val pdfBytes = bytes ?: return null
        if (pdfBytes.isEmpty()) return null
        val title = displayName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "Untitled document"
        return title to pdfBytes
    }

    /** Kiosk tablet: hide the status bar (clock/battery/network) and nav bar, swipe brings them
     *  back temporarily as a transient overlay. Re-applied on every focus change since Android
     *  restores system bars whenever the window regains focus (e.g. after a permission dialog). */
    private fun enableImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enableImmersiveMode()
    }

    override fun onStop() {
        super.onStop()
        // The Activity has actually left the foreground (not just a permission dialog, which is
        // onPause only) -- Android will block background mic use anyway, so stop cleanly rather
        // than leave a session stuck silently mid-conversation.
        viewModel.onAppBackgrounded()
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppForegrounded()
    }


    /** Takes a link, text or files another app shared to Ultron and gives them to the home chat. */
    private fun handleShareIntent(intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return
        val uris = if (action == Intent.ACTION_SEND_MULTIPLE) {
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        }
        viewModel.onShareReceived(intent.getStringExtra(Intent.EXTRA_TEXT), uris)
        intent.action = null // so a recreated activity does not take the same share twice
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
        handleAssistIntent(intent)
    }

    /** Starts talking when Ultron is opened as the phone's default assistant (hold home or power). */
    private fun handleAssistIntent(intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_ASSIST && action != Intent.ACTION_VOICE_COMMAND) return
        intent.action = null // so a recreated activity does not start a second session
        if (hasMicPermission()) viewModel.onAssistInvoked()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Know the theme before the first frame so the window never flashes the other one.
        val systemDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        paletteFor(ThemePrefs.load(this), systemDark).also {
            ActivePalette.current = it
            applyWindowColors(this, it)
        }
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableImmersiveMode()
        // Only saved automation rules keep the service (and its notification) up; a voice session starts it itself.
        if (com.assistant.app.data.AutomationEngine.hasRules(this)) AppForegroundService.start(this)
        handleAssistIntent(intent)

        setContent {
            var micGranted by remember { mutableStateOf(hasMicPermission()) }
            // True once a denial happens with the OS no longer willing to show its own rationale
            // dialog ("don't ask again", or some OEMs' second denial) -- relaunching
            // RequestPermission() at that point is a silent no-op, so the UI needs a different
            // recovery action (the app's own system Settings page).
            var micPermissionPermanentlyDenied by remember { mutableStateOf(false) }
            var notificationPermissionHandled by rememberSaveable {
                mutableStateOf(android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED)
            }

            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) {
                notificationPermissionHandled = true
            }

            val googleConsentLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartIntentSenderForResult()
            ) { result -> viewModel.onGoogleConsentResult(result.data) }

            val toolPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { }

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                micGranted = granted
                micPermissionPermanentlyDenied = if (granted) {
                    false
                } else {
                    !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
                }
            }

            // Ask up front on first launch instead of waiting for the first mic tap. Saved so a
            // rotation doesn't re-fire it; after a denial the on-screen button retries instead.
            var askedOnStart by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(notificationPermissionHandled) {
                if (!notificationPermissionHandled) {
                    // The literal avoids an API 33 field reference; before Android 13 the launcher answers at once.
                    notificationPermissionLauncher.launch("android.permission.POST_NOTIFICATIONS")
                    return@LaunchedEffect
                }
                if (!askedOnStart && !micGranted) {
                    askedOnStart = true
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }

            // Permission can also be granted/revoked from outside this Activity (the system
            // Settings app) while it stays alive in the background -- recheck whenever it comes
            // back to the foreground instead of only trusting the launcher callback.
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        viewModel.onAppResumed()
                        val granted = hasMicPermission()
                        micGranted = granted
                        if (granted) micPermissionPermanentlyDenied = false
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            val markdownPickerLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                lifecycleScope.launch {
                    var tooLarge = false
                    val parsed = withContext(Dispatchers.IO) {
                        try {
                            readKnowledgePdf(uri)
                        } catch (_: PdfTooLargeException) {
                            tooLarge = true
                            null
                        } catch (_: IOException) {
                            null
                        } catch (_: SecurityException) {
                            null
                        } catch (_: IllegalArgumentException) {
                            null
                        }
                    }
                    if (parsed != null) {
                        viewModel.onKnowledgePdfPicked(parsed.first, parsed.second)
                    } else if (tooLarge) {
                        viewModel.onKnowledgePdfTooLarge()
                    } else {
                        viewModel.onMarkdownFileUploadFailed()
                    }
                }
            }

            var themeMode by remember { mutableStateOf(ThemePrefs.load(this@MainActivity)) }
            AssistantTheme(themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = com.assistant.app.ui.theme.AssistantBackground,
                    contentColor = com.assistant.app.ui.theme.AssistantTextPrimary
                ) {
                    val uiState by viewModel.uiState.collectAsState()
                    LaunchedEffect(uiState.googleConsent) {
                        uiState.googleConsent?.let { pending ->
                            runCatching { googleConsentLauncher.launch(IntentSenderRequest.Builder(pending).build()) }
                                .onFailure { viewModel.onGoogleConsentResult(null) }
                        }
                    }
                    LaunchedEffect(uiState.permissionRequestId) {
                        if (uiState.permissionRequestId > 0 && uiState.requestedPermissions.isNotEmpty()) {
                            runCatching { toolPermissionLauncher.launch(uiState.requestedPermissions.toTypedArray()) }
                        }
                    }
                    LaunchedEffect(uiState.appBrightnessPercent) {
                        window.attributes = window.attributes.also {
                            it.screenBrightness = uiState.appBrightnessPercent?.let { pct -> pct / 100f }
                                ?: android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                        }
                    }
                    LaunchedEffect(uiState.restartRequestId) {
                        if (uiState.restartRequestId > 0) {
                            delay(900)
                            restartApp()
                        }
                    }

                    AssistantScreen(
                        uiState = uiState,
                        hasMicPermission = micGranted,
                        micPermissionPermanentlyDenied = micPermissionPermanentlyDenied,
                        onMicTapped = { viewModel.onMicTapped() },
                        onMuteToggled = { viewModel.onMuteToggled() },
                        onChatSend = { text, files -> viewModel.onChatSend(text, files) },
                        onChatDraftSaved = { text, files -> viewModel.onChatDraftSaved(text, files) },
                        onChatEdit = { id, text -> viewModel.onChatEdit(id, text) },
                        onChatNew = { viewModel.onChatNew() },
                        onChatNotice = { viewModel.onChatNotice(it) },
                        onShareConsumed = { viewModel.onShareConsumed(it) },
                        themeMode = themeMode,
                        onThemeModeChange = { mode ->
                            themeMode = mode
                            ThemePrefs.save(this@MainActivity, mode)
                            val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                                android.content.res.Configuration.UI_MODE_NIGHT_YES
                            ActivePalette.current = paletteFor(mode, night)
                        },
                        onOpenChat = { viewModel.onOpenChat(it) },
                        onKnowledgeTapped = { viewModel.onKnowledgeTapped() },
                        onHomeTapped = { viewModel.onHomeTapped() },
                        onConnectorsTapped = { viewModel.onConnectorsTapped() },
                        onInstallUpdate = { viewModel.onInstallUpdate() },
                        onDismissUpdate = { viewModel.onDismissUpdate() },
                        onCheckForUpdates = { viewModel.checkForUpdate(manual = true) },
                        onUpdateNow = { viewModel.onUpdateNow() },
                        onSearchTapped = { viewModel.onSearchTapped() },
                        onLibraryTapped = { viewModel.onLibraryTapped() },
                        onPersonalizationTapped = { viewModel.onPersonalizationTapped() },
                        onUsageTapped = { viewModel.onUsageTapped() },
                        onStorageTapped = { viewModel.onStorageTapped() },
                        onSkillsTapped = { viewModel.onSkillsTapped() },
                        onSkillToggled = { name, on -> viewModel.onSkillToggled(name, on) },
                        onSkillSaved = { name, description, body -> viewModel.onSkillSaved(name, description, body) },
                        onSkillDeleted = { viewModel.onSkillDeleted(it) },
                        onPersonalizationChanged = { viewModel.onPersonalizationChanged(it) },
                        onLoadAttachment = { viewModel.loadAttachment(it) },
                        onLibraryDelete = { viewModel.onLibraryDelete(it) },
                        onClearLibraryFiles = { viewModel.onClearLibraryFiles() },
                        onClearCache = { viewModel.onClearCache() },
                        onExportChats = { viewModel.onExportChats() },
                        onExportUnreadableChats = { viewModel.onExportUnreadableChats() },
                        onDiscardUnreadableChats = { viewModel.onDiscardUnreadableChats() },
                        onResetUsage = { viewModel.onResetUsage() },
                        onMemoryTapped = { viewModel.onMemoryTapped() },
                        onMemoryEnabledToggled = { viewModel.onMemoryEnabledToggled(it) },
                        onOpenChatAt = { id, turn -> viewModel.onOpenChat(id, turn) },
                        onChatFocusConsumed = { viewModel.onChatFocusConsumed() },
                        onDailyBudgetChanged = { viewModel.onDailyBudgetChanged(it) },
                        onDismissAgentPromptUpdate = { viewModel.onDismissAgentPromptUpdate() },
                        onAddMemory = { viewModel.onAddMemory(it) },
                        onUpdateMemory = { id, text -> viewModel.onUpdateMemory(id, text) },
                        onDeleteMemory = { viewModel.onDeleteMemory(it) },
                        onClearMemory = { viewModel.onClearMemory() },
                        onRestoreMemory = { viewModel.onRestoreMemory(it) },
                        onClearAllChats = { viewModel.onClearAllChats() },
                        onClearChatsAndMemory = { viewModel.onClearChatsAndMemory() },
                        onStartGithubSignIn = { viewModel.onStartGithubSignIn() },
                        onCancelGithubSignIn = { viewModel.onCancelGithubSignIn() },
                        onDisconnectGithub = { viewModel.onDisconnectGithub() },
                        onConnectGoogle = { service -> viewModel.onConnectGoogle(service) },
                        onDisconnectGoogle = { service -> viewModel.onDisconnectGoogle(service) },
                        onConnectorToolsEnabled = { tools, enabled, name -> viewModel.onConnectorToolsEnabled(tools, enabled, name) },
                        onRequestConnectorPermissions = { permissions -> viewModel.onRequestConnectorPermissions(permissions) },
                        onRequestPermission = {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        onOpenAppSettings = {
                            startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .setData(Uri.fromParts("package", packageName, null))
                            )
                        },
                        onSettingsTapped = { viewModel.onSettingsTapped() },
                        onDeveloperTapped = { viewModel.onDeveloperTapped() },
                        onAboutTapped = { viewModel.onAboutTapped() },
                        onDismissSettings = { viewModel.onDismissSettings() },
                        onClearApiKey = { viewModel.onClearApiKey() },
                        onClearEmbeddingApiKey = { viewModel.onClearEmbeddingApiKey() },
                        onLiveModelSelected = { id -> viewModel.onLiveModelSelected(id) },
                        onVoiceSelected = { voice -> viewModel.onVoiceSelected(voice) },
                        onLanguageSelected = { code -> viewModel.onLanguageSelected(code) },
                        onMicSensitivitySelected = { sensitivity -> viewModel.onMicSensitivitySelected(sensitivity) },
                        onBargeInToggled = { enabled -> viewModel.onBargeInToggled(enabled) },
                        onSaveDeveloperChanges = { name, greeting, apiKey, embeddingApiKey, systemPrompt, chatPrompt, documentPrompt, retrievalLimit ->
                            viewModel.onSaveDeveloperChanges(
                                name,
                                greeting,
                                apiKey,
                                embeddingApiKey,
                                systemPrompt,
                                chatPrompt,
                                documentPrompt,
                                retrievalLimit
                            )
                        },
                        onSaveAzure = { config -> viewModel.onSaveAzureConfig(config) },
                        onSaveNim = { config -> viewModel.onSaveNimConfig(config) },
                        onLoadNimModels = { viewModel.onLoadNimModels() },
                        onGeneratePromptFromKnowledgeBase = { viewModel.onGeneratePromptFromKnowledgeBase() },
                        onDismissPromptGenerationError = { viewModel.onDismissPromptGenerationError() },
                        onAddKnowledgeDocument = { title, text -> viewModel.onAddKnowledgeDocument(title, text) },
                        onUpdateKnowledgeDocument = { id, title, text -> viewModel.onUpdateKnowledgeDocument(id, title, text) },
                        onDeleteKnowledgeDocument = { id -> viewModel.onDeleteKnowledgeDocument(id) },
                        onResetKnowledge = { viewModel.onResetKnowledge() },
                        onUploadMarkdownFile = {
                            markdownPickerLauncher.launch(arrayOf("application/pdf"))
                        },
                        onDismissKnowledgeDocumentError = { viewModel.onDismissKnowledgeDocumentError() },
                        onDismissToast = { viewModel.onDismissToast() },
                        onDeleteChatSession = { id -> viewModel.onDeleteChatSession(id) },
                    )
                }
            }
        }
    }
}
