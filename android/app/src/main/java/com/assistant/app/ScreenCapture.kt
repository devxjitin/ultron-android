package com.assistant.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream

/**
 * Lets the assistant look at the screen as a picture when an app gives it no readable text. Android always asks the
 * user first (its own "start recording or casting" dialog); the permission lasts until the voice session ends. Apps
 * that protect their window show up black even with that permission, and this never tries to get around that.
 */
object ScreenCapture {

    @Volatile private var projection: MediaProjection? = null
    @Volatile private var display: VirtualDisplay? = null
    @Volatile private var pending: CompletableDeferred<Boolean>? = null
    private val mutex = Mutex()
    private val handler = Handler(Looper.getMainLooper())

    val isReady: Boolean get() = projection != null && display != null

    /** True once the user has allowed capture; otherwise shows Android's dialog and waits for the answer. */
    suspend fun ensureAccess(context: Context, timeoutMs: Long = 45_000L): Boolean {
        if (isReady) return true
        val answer = CompletableDeferred<Boolean>()
        pending = answer
        context.startActivity(
            Intent(context, ScreenCaptureConsentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        val allowed = withTimeoutOrNull(timeoutMs) { answer.await() } == true
        if (pending === answer) pending = null
        return allowed
    }

    /** Called by the consent screen with the user's answer. */
    internal fun onConsentResult(context: Context, resultCode: Int, data: Intent?) {
        if (resultCode != android.app.Activity.RESULT_OK || data == null) {
            pending?.complete(false)
            return
        }
        // The projection must be created from a foreground service of the right type.
        androidx.core.content.ContextCompat.startForegroundService(
            context,
            Intent(context, ScreenCaptureService::class.java)
                .putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                .putExtra(ScreenCaptureService.EXTRA_DATA, data)
        )
    }

    internal fun attach(context: Context, granted: MediaProjection) {
        val (width, height, dpi) = screenSize(context)
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        granted.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() = release(context)
        }, handler)
        val virtual = granted.createVirtualDisplay(
            "ultron-look", width, height, dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, null
        )
        reader.close()
        projection = granted
        display = virtual
        pending?.complete(true)
    }

    internal fun failed() {
        pending?.complete(false)
    }

    /** One picture of what is on screen now, or null when capture is not allowed or no frame arrived in time. */
    suspend fun capture(context: Context): Bitmap? = mutex.withLock {
        val virtual = display ?: return@withLock null
        val (width, height, dpi) = screenSize(context)
        withContext(Dispatchers.Default) {
            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            try {
                val frame = CompletableDeferred<Unit>()
                reader.setOnImageAvailableListener({ frame.complete(Unit) }, handler)
                runCatching { virtual.resize(width, height, dpi) }
                // A new surface makes the display send a fresh frame even when the screen is not changing.
                virtual.surface = reader.surface
                withTimeoutOrNull(2_500L) { frame.await() }
                reader.acquireLatestImage()?.use { image ->
                    val plane = image.planes[0]
                    val padded = Bitmap.createBitmap(plane.rowStride / plane.pixelStride, height, Bitmap.Config.ARGB_8888)
                    padded.copyPixelsFromBuffer(plane.buffer)
                    if (padded.width == width) padded else Bitmap.createBitmap(padded, 0, 0, width, height)
                }
            } catch (e: Exception) {
                null
            } finally {
                runCatching { virtual.surface = null }
                reader.close()
            }
        }
    }

    /** True when the picture is (almost) all black, which is how an app that protects its window appears. */
    fun looksBlank(bitmap: Bitmap): Boolean {
        val steps = 24
        for (i in 0 until steps) {
            for (j in 0 until steps) {
                val pixel = bitmap.getPixel((bitmap.width - 1) * i / (steps - 1), (bitmap.height - 1) * j / (steps - 1))
                val brightest = maxOf((pixel shr 16) and 0xFF, (pixel shr 8) and 0xFF, pixel and 0xFF)
                if (brightest > 10) return false
            }
        }
        return true
    }

    /** A JPEG small enough to send to the model quickly. */
    fun toJpeg(bitmap: Bitmap, maxSide: Int = 1280): ByteArray {
        val scale = maxSide.toFloat() / maxOf(bitmap.width, bitmap.height)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else bitmap
        return ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
    }

    /** Ends the permission; the next look asks the user again. */
    fun release(context: Context) {
        val virtual = display
        val granted = projection
        display = null
        projection = null
        runCatching { virtual?.release() }
        runCatching { granted?.stop() }
        context.stopService(Intent(context, ScreenCaptureService::class.java))
    }

    private fun screenSize(context: Context): Triple<Int, Int, Int> {
        val bounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        return Triple(bounds.width(), bounds.height(), context.resources.displayMetrics.densityDpi)
    }
}
