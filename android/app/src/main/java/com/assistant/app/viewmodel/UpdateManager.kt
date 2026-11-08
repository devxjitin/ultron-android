package com.assistant.app.viewmodel

import android.app.Application
import android.net.ConnectivityManager
import android.util.Log
import com.assistant.app.network.AppUpdater
import com.assistant.app.network.UpdateInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** What [UpdateManager] needs from the update server and the phone; [AppUpdaterSource] is the real one. */
internal interface UpdateSource {
    val isConfigured: Boolean
    suspend fun check(): Result<UpdateInfo?>
    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): Result<File>
    fun install(file: File): AppUpdater.InstallStart
    /** False on mobile data or when the connection is unknown, so a background download never uses the user's data. */
    fun isUnmetered(): Boolean
}

internal class AppUpdaterSource(private val application: Application) : UpdateSource {
    override val isConfigured get() = AppUpdater.isConfigured
    override suspend fun check() = AppUpdater.check()
    override suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit) = AppUpdater.download(application, info, onProgress)
    override fun install(file: File) = AppUpdater.install(application, file)
    override fun isUnmetered(): Boolean {
        val connectivity = application.getSystemService(ConnectivityManager::class.java) ?: return false
        return !connectivity.isActiveNetworkMetered
    }
}

/**
 * Looks for a newer build, downloads it ahead of time and starts the install. It writes its progress into the
 * same [state] the screen reads. [isVoiceSessionActive] keeps an automatic check from interrupting a conversation.
 */
internal class UpdateManager(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<AssistantUiState>,
    private val source: UpdateSource,
    private val showToast: (String, AppNotificationType) -> Unit,
    private val isVoiceSessionActive: () -> Boolean,
    private val now: () -> Long = System::currentTimeMillis
) {
    companion object {
        /** How often the app asks the AWS update server for a newer build on its own, also while it stays open. */
        const val CHECK_INTERVAL_MS = 5 * 60_000L
    }

    private var lastCheckMs = 0L
    private var downloaded: Pair<Int, File>? = null
    /** Silent download started right after a check finds a build, so tapping Update can install at once. */
    private var prefetchJob: Job? = null
    @Volatile private var showPrefetchProgress = false
    /** True while a check is running, so two never overlap. The page only shows "Checking" for a check the user started. */
    private var checkInFlight = false
    private var dismissedVersion = 0

    /** True while a background download is running; clearing the cache then leaves the download folder alone. */
    val isDownloading: Boolean get() = prefetchJob?.isActive == true

    /** The cache was emptied, so a remembered download no longer exists. */
    fun forgetDownload() {
        downloaded = null
    }

    /** Asks the AWS update server for a newer build; the automatic check runs at most every [CHECK_INTERVAL_MS] (5 minutes). */
    fun checkForUpdate(manual: Boolean) {
        if (!source.isConfigured) {
            if (manual) state.update {
                it.copy(updateCheckState = UpdateCheckState.FAILED, updateCheckError = "Updates are not set up in this build.")
            }
            return
        }
        if (state.value.updateDownloadProgress != null || checkInFlight) return
        if (!manual && now() - lastCheckMs < CHECK_INTERVAL_MS) return
        lastCheckMs = now()
        checkInFlight = true
        // An automatic check runs quietly. "Checking" is only shown when the user taps Check for updates.
        if (manual) state.update { it.copy(updateCheckState = UpdateCheckState.CHECKING, updateCheckError = null) }
        scope.launch {
            val result = source.check()
            checkInFlight = false
            result.fold(
                onSuccess = { info ->
                    val checkedAt = now()
                    if (info != null) {
                        // A quiet check never interrupts a voice conversation, and a build the user already
                        // declined waits until the next launch (About still offers it).
                        val quiet = !manual && (isVoiceSessionActive() || info.versionCode == dismissedVersion)
                        state.update {
                            it.copy(
                                updateCheckState = UpdateCheckState.AVAILABLE, lastUpdateCheckAt = checkedAt, availableUpdate = info,
                                updateInfo = if (quiet) it.updateInfo else info
                            )
                        }
                        prefetch(info)
                    } else {
                        state.update {
                            it.copy(updateCheckState = UpdateCheckState.UP_TO_DATE, lastUpdateCheckAt = checkedAt, availableUpdate = null)
                        }
                    }
                },
                onFailure = { error ->
                    // A failed check (offline) must not block the next try for a whole interval.
                    lastCheckMs = 0L
                    // A quiet check that fails says nothing; only a check the user asked for shows the error.
                    if (manual) state.update {
                        it.copy(
                            updateCheckState = UpdateCheckState.FAILED,
                            updateCheckError = "Could not reach the update server. Check your connection and try again."
                        )
                    }
                    Log.w("AssistantUpdate", "Update check failed: ${error.javaClass.simpleName}")
                }
            )
        }
    }

    /** Offers the build that About found, the same way the automatic dialog does. */
    fun onUpdateNow() {
        val info = state.value.availableUpdate ?: return
        state.update { it.copy(updateInfo = info) }
        onInstallUpdate()
    }

    /** Downloads [info] in the background on an unmetered network (Wi-Fi), without any UI. */
    private fun prefetch(info: UpdateInfo) {
        if (prefetchJob?.isActive == true || downloaded?.first == info.versionCode) return
        if (!source.isUnmetered()) return
        showPrefetchProgress = false
        prefetchJob = scope.launch {
            source.download(info) { p ->
                if (showPrefetchProgress) state.update { it.copy(updateDownloadProgress = p) }
            }.onSuccess { downloaded = info.versionCode to it }
        }
    }

    /** Downloads the offered build (unless it is already downloaded) and opens Android's installer. */
    fun onInstallUpdate() {
        val info = state.value.updateInfo ?: return
        if (state.value.updateDownloadProgress != null) return
        scope.launch {
            // A background download already running: show its progress and wait for it instead of starting over.
            prefetchJob?.takeIf { it.isActive }?.let { job ->
                showPrefetchProgress = true
                state.update { it.copy(updateDownloadProgress = 0f) }
                job.join()
                state.update { it.copy(updateDownloadProgress = null) }
            }
            val ready = downloaded?.takeIf { it.first == info.versionCode && it.second.exists() }?.second
            val file = ready ?: run {
                state.update { it.copy(updateDownloadProgress = 0f) }
                val result = source.download(info) { p -> state.update { it.copy(updateDownloadProgress = p) } }
                state.update { it.copy(updateDownloadProgress = null) }
                result.getOrElse {
                    showToast(it.message ?: "The update could not be downloaded.", AppNotificationType.ERROR)
                    // The download link expires after a while; fetch a fresh one for the next try.
                    lastCheckMs = 0L
                    checkForUpdate(manual = false)
                    return@launch
                }
            }
            downloaded = info.versionCode to file
            when (source.install(file)) {
                AppUpdater.InstallStart.OPENED -> state.update { it.copy(updateInfo = null) }
                AppUpdater.InstallStart.NEEDS_PERMISSION -> showToast(
                    "Allow installs from this app, come back and tap Update again.", AppNotificationType.SUCCESS
                )
            }
        }
    }

    fun onDismissUpdate() {
        dismissedVersion = state.value.updateInfo?.versionCode ?: dismissedVersion
        state.update { it.copy(updateInfo = null) }
    }
}
