package com.assistant.app.viewmodel

import com.assistant.app.network.AppUpdater
import com.assistant.app.network.UpdateInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UpdateManagerTest {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Unconfined)
    private val state = MutableStateFlow(AssistantUiState())
    private val toasts = mutableListOf<Pair<String, AppNotificationType>>()
    private var clock = 1_000_000L
    private var sessionActive = false
    private val source = FakeSource()

    private fun manager() = UpdateManager(
        scope, state, source,
        showToast = { m, t -> toasts += m to t },
        isVoiceSessionActive = { sessionActive },
        now = { clock }
    )

    @After fun tearDown() = job.cancel()

    private fun info(code: Int = 5) = UpdateInfo(code, "1.0.$code", "", "sha", 10, "https://x")

    private class FakeSource : com.assistant.app.viewmodel.UpdateSource {
        override var isConfigured = true
        var unmetered = true
        var checkResult: Result<UpdateInfo?> = Result.success(null)
        var downloadGate: CompletableDeferred<Unit>? = null
        var downloadResult: Result<File> = Result.success(File.createTempFile("upd", ".apk").also { it.deleteOnExit() })
        var checks = 0
        var downloads = 0
        var installs = 0
        var installResult = AppUpdater.InstallStart.OPENED
        override suspend fun check(): Result<UpdateInfo?> { checks++; return checkResult }
        override suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): Result<File> {
            downloads++
            downloadGate?.await()
            return downloadResult
        }
        override fun install(file: File): AppUpdater.InstallStart { installs++; return installResult }
        override fun isUnmetered() = unmetered
    }

    @Test fun `manual check without update server shows a failure`() {
        source.isConfigured = false
        manager().checkForUpdate(manual = true)
        assertEquals(UpdateCheckState.FAILED, state.value.updateCheckState)
        assertEquals(0, source.checks)
    }

    @Test fun `automatic check without update server stays silent`() {
        source.isConfigured = false
        manager().checkForUpdate(manual = false)
        assertEquals(UpdateCheckState.IDLE, state.value.updateCheckState)
    }

    @Test fun `automatic checks are spaced by the interval but manual ones are not`() {
        val m = manager()
        m.checkForUpdate(manual = false)
        clock += UpdateManager.CHECK_INTERVAL_MS - 1
        m.checkForUpdate(manual = false)
        assertEquals(1, source.checks)
        m.checkForUpdate(manual = true)
        assertEquals(2, source.checks)
        clock += 2 * UpdateManager.CHECK_INTERVAL_MS
        m.checkForUpdate(manual = false)
        assertEquals(3, source.checks)
    }

    @Test fun `failed check does not block the next try`() {
        source.checkResult = Result.failure(java.io.IOException("offline"))
        val m = manager()
        m.checkForUpdate(manual = false)
        source.checkResult = Result.success(null)
        m.checkForUpdate(manual = false)
        assertEquals(2, source.checks)
        assertEquals(UpdateCheckState.UP_TO_DATE, state.value.updateCheckState)
    }

    @Test fun `failed quiet check shows nothing but a manual one shows the error`() {
        source.checkResult = Result.failure(java.io.IOException("offline"))
        val m = manager()
        m.checkForUpdate(manual = false)
        assertEquals(UpdateCheckState.IDLE, state.value.updateCheckState)
        m.checkForUpdate(manual = true)
        assertEquals(UpdateCheckState.FAILED, state.value.updateCheckState)
        assertNotNull(state.value.updateCheckError)
    }

    @Test fun `found build is offered in the dialog on a normal check`() {
        source.checkResult = Result.success(info())
        manager().checkForUpdate(manual = false)
        assertEquals(UpdateCheckState.AVAILABLE, state.value.updateCheckState)
        assertEquals(5, state.value.updateInfo?.versionCode)
        assertEquals(5, state.value.availableUpdate?.versionCode)
    }

    @Test fun `quiet check during a voice session keeps the dialog closed`() {
        sessionActive = true
        source.checkResult = Result.success(info())
        manager().checkForUpdate(manual = false)
        assertNull(state.value.updateInfo)
        assertEquals(5, state.value.availableUpdate?.versionCode)
    }

    @Test fun `manual check opens the dialog even during a voice session`() {
        sessionActive = true
        source.checkResult = Result.success(info())
        manager().checkForUpdate(manual = true)
        assertEquals(5, state.value.updateInfo?.versionCode)
    }

    @Test fun `declined build is not offered again by a quiet check`() {
        source.checkResult = Result.success(info())
        val m = manager()
        m.checkForUpdate(manual = false)
        m.onDismissUpdate()
        assertNull(state.value.updateInfo)
        clock += 2 * UpdateManager.CHECK_INTERVAL_MS
        m.checkForUpdate(manual = false)
        assertNull(state.value.updateInfo)
        assertEquals(5, state.value.availableUpdate?.versionCode)
    }

    @Test fun `prefetch happens on unmetered networks only`() {
        source.unmetered = false
        source.checkResult = Result.success(info())
        manager().checkForUpdate(manual = false)
        assertEquals(0, source.downloads)

        val other = FakeSource().also { it.checkResult = Result.success(info()) }
        UpdateManager(scope, MutableStateFlow(AssistantUiState()), other, { _, _ -> }, { false }, { clock })
            .checkForUpdate(manual = false)
        assertEquals(1, other.downloads)
    }

    @Test fun `install reuses a finished prefetch instead of downloading again`() {
        source.checkResult = Result.success(info())
        val m = manager()
        m.checkForUpdate(manual = false)
        assertEquals(1, source.downloads)
        m.onInstallUpdate()
        assertEquals(1, source.downloads)
        assertEquals(1, source.installs)
        assertNull(state.value.updateInfo)
    }

    @Test fun `install waits for a running prefetch and shows its progress`() {
        source.downloadGate = CompletableDeferred()
        source.checkResult = Result.success(info())
        val m = manager()
        m.checkForUpdate(manual = false)
        assertTrue(m.isDownloading)
        m.onInstallUpdate()
        assertEquals(0f, state.value.updateDownloadProgress)
        assertEquals(0, source.installs)
        source.downloadGate!!.complete(Unit)
        assertFalse(m.isDownloading)
        assertNull(state.value.updateDownloadProgress)
        assertEquals(1, source.downloads)
        assertEquals(1, source.installs)
    }

    @Test fun `failed download shows an error and asks for a fresh link`() {
        source.unmetered = false
        source.checkResult = Result.success(info())
        source.downloadResult = Result.failure(java.io.IOException("link expired"))
        val m = manager()
        m.checkForUpdate(manual = false)
        m.onInstallUpdate()
        assertEquals(listOf("link expired" to AppNotificationType.ERROR), toasts)
        assertEquals(2, source.checks)
        assertNull(state.value.updateDownloadProgress)
        assertEquals(0, source.installs)
    }

    @Test fun `missing install permission keeps the dialog and tells the user`() {
        source.installResult = AppUpdater.InstallStart.NEEDS_PERMISSION
        source.checkResult = Result.success(info())
        val m = manager()
        m.checkForUpdate(manual = false)
        m.onInstallUpdate()
        assertEquals(1, toasts.size)
        assertNotNull(state.value.updateInfo)
    }

    @Test fun `update now offers the found build and installs it`() {
        source.checkResult = Result.success(info())
        sessionActive = true
        val m = manager()
        m.checkForUpdate(manual = false)
        assertNull(state.value.updateInfo)
        m.onUpdateNow()
        assertEquals(1, source.installs)
    }

    @Test fun `forgetting the download makes install fetch the file again`() {
        source.checkResult = Result.success(info())
        val m = manager()
        m.checkForUpdate(manual = false)
        m.forgetDownload()
        m.onInstallUpdate()
        assertEquals(2, source.downloads)
    }

    @Test fun `no check starts while a download is in progress`() {
        state.value = state.value.copy(updateDownloadProgress = 0.3f)
        manager().checkForUpdate(manual = true)
        assertEquals(0, source.checks)
    }
}
