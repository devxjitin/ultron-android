package com.assistant.app.network

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.assistant.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** A newer build published to the user's AWS update server. */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val notes: String,
    val sha256: String,
    val sizeBytes: Long,
    /** Short-lived download link; only valid for a while after the check. */
    val apkUrl: String
)

/**
 * Self-update from the user's own AWS server: ask which build is newest, download it, check its SHA-256,
 * and hand it to Android's installer. Android itself refuses the file unless it is signed with the same key
 * as the installed app, so a tampered download cannot replace this app.
 */
object AppUpdater {
    private val http = HttpClients.shared.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val isConfigured: Boolean get() = ApiClient.isConfigured

    /** Success(null) means this build is already the newest one (or nothing has been published yet). */
    suspend fun check(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            var lastFailure: Throwable? = null
            for (request in latestRequests()) {
                val body = try {
                    http.newCall(request).execute().use { response ->
                        when {
                            response.code == 404 -> return@runCatching null
                            !response.isSuccessful -> error("The update server answered HTTP ${response.code}.")
                            else -> response.body?.string().orEmpty()
                        }
                    }
                } catch (e: Exception) {
                    lastFailure = e
                    continue
                }
                val json = JSONObject(body)
                val info = UpdateInfo(
                    versionCode = json.getInt("versionCode"),
                    versionName = json.optString("versionName"),
                    notes = json.optString("notes"),
                    sha256 = json.getString("sha256").lowercase(),
                    sizeBytes = json.optLong("sizeBytes"),
                    apkUrl = json.getString("apkUrl")
                )
                return@runCatching info.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
            }
            throw lastFailure ?: IllegalStateException("No update server is set up in this build.")
        }
    }

    /** The Ultron API, which needs the user's Firebase token; nothing is requested when they are not signed in. */
    private fun latestRequests(): List<Request> = buildList {
        if (ApiClient.isConfigured) {
            ApiClient.authHeader()?.let { auth ->
                add(Request.Builder().url(ApiClient.url("/v1/releases/latest")).header("Authorization", auth)
                    .apply { ApiClient.appCheckToken()?.let { header(ApiClient.APP_CHECK_HEADER, it) } }
                    .build())
            }
        }
    }

    /** Downloads the build to the app's cache and verifies it; [onProgress] gets 0..1. */
    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "ultron-${info.versionCode}.apk")
            val digest = MessageDigest.getInstance("SHA-256")
            http.newCall(Request.Builder().url(info.apkUrl).build()).execute().use { response ->
                if (!response.isSuccessful) error("The download failed (HTTP ${response.code}). Try again.")
                val body = response.body ?: error("The download was empty.")
                val total = body.contentLength().takeIf { it > 0 } ?: info.sizeBytes
                var done = 0L
                var shownPercent = -1
                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            done += read
                            // Report only whole-percent steps so the UI is not redrawn for every 64 KB chunk.
                            val percent = if (total > 0) (done * 100 / total).toInt().coerceIn(0, 100) else -1
                            if (percent > shownPercent) {
                                shownPercent = percent
                                onProgress(percent / 100f)
                            }
                        }
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (actual != info.sha256) {
                file.delete()
                error("The downloaded file is damaged. Try again.")
            }
            file
        }
    }

    /** Opens Android's installer for [file]; if installs from this app are not allowed yet, opens that setting instead. */
    fun install(context: Context, file: File): InstallStart {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return InstallStart.NEEDS_PERMISSION
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return InstallStart.OPENED
    }

    enum class InstallStart { OPENED, NEEDS_PERMISSION }
}
