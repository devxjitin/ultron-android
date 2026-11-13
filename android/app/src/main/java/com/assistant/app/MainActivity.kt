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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.assistant.app.ui.AssistantActions
import com.assistant.app.ui.AssistantScreen
import com.assistant.app.ui.theme.AssistantTheme
import com.assistant.app.ui.theme.ActivePalette
import com.assistant.app.ui.theme.ThemeMode
import com.assistant.app.ui.theme.ThemePrefs
import com.assistant.app.ui.theme.applyWindowColors
import com.assistant.app.ui.theme.paletteFor
import com.assistant.app.viewmodel.AssistantViewModel
import com.assistant.app.ui.AccessCheckingScreen
import com.assistant.app.ui.ActivationScreen
import com.assistant.app.ui.LicenseSummary
import com.assistant.app.viewmodel.AccessState
import com.assistant.app.viewmodel.AccessViewModel

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
    private val access: AccessViewModel by viewModels()
    /** Skips the key re-check on the very first start; the access view model already runs one. */
    private var startedOnce = false

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
        if (startedOnce) access.refresh(quiet = true)
        startedOnce = true
    }

    private fun openStore() {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.STORE_URL))) }
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAssistIntent(intent)
    }

    /** Starts talking when Ultron is opened as the phone's default assistant (hold home or power). */
    private fun handleAssistIntent(intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_ASSIST && action != Intent.ACTION_VOICE_COMMAND) return
        intent.action = null // so a recreated activity does not start a second session
        // The view model holds the request until the key check is done and the app is on screen.
        if (hasMicPermission()) viewModel.onAssistInvoked()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        UpdatedReceiver.clearNotification(this)
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

            val imagePickerLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.GetContent()
            ) { uri -> viewModel.onImagePicked(uri) }

            val pdfPickerLauncher = rememberLauncherForActivityResult(
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
                        viewModel.onPdfFileUploadFailed()
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
                    val accessState by access.state.collectAsState()
                    val unlocked = accessState is AccessState.Active
                    LaunchedEffect(unlocked) { viewModel.onAccessChanged(unlocked) }
                    when (val gate = accessState) {
                        AccessState.Checking -> { AccessCheckingScreen(); return@Surface }
                        is AccessState.NeedsKey -> {
                            ActivationScreen(gate, storeUrl = BuildConfig.STORE_URL, onActivate = access::activate, onOpenStore = ::openStore)
                            return@Surface
                        }
                        is AccessState.Active -> Unit
                    }
                    val active = accessState as AccessState.Active

                    val uiState by viewModel.uiState.collectAsState()
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

                    val voiceLevel = viewModel.voiceLevel.collectAsState()
                    AssistantScreen(
                        uiState = uiState,
                        voiceLevel = { voiceLevel.value },
                        hasMicPermission = micGranted,
                        micPermissionPermanentlyDenied = micPermissionPermanentlyDenied,
                        themeMode = themeMode,
                        license = LicenseSummary(active.key, active.status),
                        actions = AssistantActions(
                            onMicTapped = { viewModel.onMicTapped() },
                            onMuteToggled = { viewModel.onMuteToggled() },
                            onThemeModeChange = { mode ->
                                themeMode = mode
                                ThemePrefs.save(this@MainActivity, mode)
                                val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                                    android.content.res.Configuration.UI_MODE_NIGHT_YES
                                ActivePalette.current = paletteFor(mode, night)
                            },
                            onKnowledgeTapped = { viewModel.onKnowledgeTapped() },
                            onHomeTapped = { viewModel.onHomeTapped() },
                            onConnectorsTapped = { viewModel.onConnectorsTapped() },
                            onInstallUpdate = { viewModel.onInstallUpdate() },
                            onDismissUpdate = { viewModel.onDismissUpdate() },
                            onDismissWhatsNew = { viewModel.onDismissWhatsNew() },
                            onCheckForUpdates = { viewModel.checkForUpdate(manual = true) },
                            onUpdateNow = { viewModel.onUpdateNow() },
                            onUsageTapped = { viewModel.onUsageTapped() },
                            onStorageTapped = { viewModel.onStorageTapped() },
                            onDownloadsTapped = { viewModel.onDownloadsTapped() },
                            onSkillsTapped = { viewModel.onSkillsTapped() },
                            onSkillToggled = { name, on -> viewModel.onSkillToggled(name, on) },
                            onSkillSaved = { name, description, body -> viewModel.onSkillSaved(name, description, body) },
                            onSkillDeleted = { viewModel.onSkillDeleted(it) },
                            onClearCache = { viewModel.onClearCache() },
                            onResetUsage = { viewModel.onResetUsage() },
                            onMemoryTapped = { viewModel.onMemoryTapped() },
                            onMemoryEnabledToggled = { viewModel.onMemoryEnabledToggled(it) },
                            onAddMemory = { viewModel.onAddMemory(it) },
                            onUpdateMemory = { id, text -> viewModel.onUpdateMemory(id, text) },
                            onDeleteMemory = { viewModel.onDeleteMemory(it) },
                            onClearMemory = { viewModel.onClearMemory() },
                            onRestoreMemory = { viewModel.onRestoreMemory(it) },
                            onHistoryTapped = { viewModel.onHistoryTapped() },
                            onHelpTapped = { viewModel.onHelpTapped() },
                            onPickImage = { imagePickerLauncher.launch("image/*") },
                            onSaveImage = { title, description -> viewModel.onSaveImage(title, description) },
                            onCancelPendingImage = { viewModel.onCancelPendingImage() },
                            onUpdateImage = { id, title, description -> viewModel.onUpdateImage(id, title, description) },
                            onDeleteImage = { viewModel.onDeleteImage(it) },
                            onClearImages = { viewModel.onClearImages() },
                            onDismissShownImage = { viewModel.onDismissShownImage() },
                            onSendFeatureRequest = { form, done ->
                                viewModel.onSendFeatureRequest(form, done)
                            },
                            onDeletePastSession = { viewModel.onDeletePastSession(it) },
                            onClearPastConversations = { viewModel.onClearPastConversations() },
                            onShowOlderConversations = { viewModel.onShowOlderConversations() },
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
                            onProfileTapped = { viewModel.onProfileTapped() },
                            onRefreshLicense = { access.refresh(quiet = true) },
                            onRenewLicense = if (BuildConfig.STORE_URL.isNotBlank()) ::openStore else null,
                            onDismissSettings = { viewModel.onDismissSettings() },
                            onClearApiKey = { viewModel.onClearApiKey() },
                            onClearEmbeddingApiKey = { viewModel.onClearEmbeddingApiKey() },
                            onLiveModelSelected = { id -> viewModel.onLiveModelSelected(id) },
                            onVoiceSelected = { voice -> viewModel.onVoiceSelected(voice) },
                            onLanguageSelected = { code -> viewModel.onLanguageSelected(code) },
                            onMicSensitivitySelected = { sensitivity -> viewModel.onMicSensitivitySelected(sensitivity) },
                            onBargeInToggled = { enabled -> viewModel.onBargeInToggled(enabled) },
                            onDecisionsRerankToggled = { enabled -> viewModel.onDecisionsRerankToggled(enabled) },
                            onDecisionsMemoryToggled = { enabled -> viewModel.onDecisionsMemoryToggled(enabled) },
                            onDecisionsActionCheckToggled = { enabled -> viewModel.onDecisionsActionCheckToggled(enabled) },
                            onDecisionsScreenCheckToggled = { enabled -> viewModel.onDecisionsScreenCheckToggled(enabled) },
                            onDecisionsSkillToggled = { enabled -> viewModel.onDecisionsSkillToggled(enabled) },
                            onAnnounceNotificationsToggled = { enabled -> viewModel.onAnnounceNotificationsToggled(enabled) },
                            onPersonalitySelected = { id -> viewModel.onPersonalitySelected(id) },
                            onOwnerNameChanged = { name -> viewModel.onOwnerNameChanged(name) },
                            onSaveDeveloperChanges = { apiKey, embeddingApiKey, retrievalLimit ->
                                viewModel.onSaveDeveloperChanges(
                                    apiKey,
                                    embeddingApiKey,
                                    retrievalLimit
                                )
                            },
                            onAddKnowledgeDocument = { title, text -> viewModel.onAddKnowledgeDocument(title, text) },
                            onUpdateKnowledgeDocument = { id, title, text -> viewModel.onUpdateKnowledgeDocument(id, title, text) },
                            onDeleteKnowledgeDocument = { id -> viewModel.onDeleteKnowledgeDocument(id) },
                            onResetKnowledge = { viewModel.onResetKnowledge() },
                            onUploadPdfFile = {
                                pdfPickerLauncher.launch(arrayOf("application/pdf"))
                            },
                            onDismissKnowledgeDocumentError = { viewModel.onDismissKnowledgeDocumentError() },
                            onDismissToast = { viewModel.onDismissToast() },
                            onRemoveLicense = { access.removeKey() },
                        ),
                    )
                }
            }
        }
    }
}
