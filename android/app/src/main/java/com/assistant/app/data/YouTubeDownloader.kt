package com.assistant.app.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.assistant.app.network.HttpClients
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NpRequest
import org.schabi.newpipe.extractor.downloader.Response as NpResponse
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.sync.withPermit
import okhttp3.Request as HttpRequest

/**
 * Downloads a YouTube video (or just its audio) straight on the phone. YouTube blocks cloud IPs, but a phone on
 * its own connection is fine. Streams are found with NewPipe's extractor. Up to 1080p YouTube serves H.264 video
 * and AAC audio (joined into an mp4); above that it serves VP9 video and Opus audio (joined into a webm). Either
 * way the compressed data is copied as is, never re-encoded, and the file lands in Downloads/Ultron.
 */
class YouTubeDownloader(private val context: Context) {

    /** What this video offers, so the user can pick: audio, or a picture height with its container and size. */
    suspend fun options(video: String): String = withContext(Dispatchers.IO) {
        val url = toUrl(video) ?: return@withContext "status=invalid\nGive a YouTube link or video id."
        ensureInit()
        val info = StreamInfo.getInfo(url)
        val lines = mutableListOf<String>()
        val audioMp4 = pickAudio(info.audioStreams, webm = false)
        val audioWebm = pickAudio(info.audioStreams, webm = true)
        (audioMp4 ?: audioWebm)?.let { lines.add("- audio only: ${it.format?.suffix ?: "m4a"}, about ${it.averageBitrate} kbps${sizeText(it.itagItem?.contentLength ?: -1)}") }
        heights(info).forEach { height ->
            val stream = pickVideoOnly(info.videoOnlyStreams, height, exact = true) ?: return@forEach
            val audio = if (stream.format?.suffix == "mp4") audioMp4 else audioWebm
            if (audio == null) return@forEach
            val total = (stream.itagItem?.contentLength ?: -1).let { v -> if (v > 0 && (audio.itagItem?.contentLength ?: -1) > 0) v + audio.itagItem!!.contentLength else -1 }
            val fps = if (stream.itagItem?.fps ?: 0 > 30) ", ${stream.itagItem?.fps} fps" else ""
            lines.add("- video ${label(height)}: ${stream.format?.suffix ?: "mp4"}$fps${sizeText(total)}")
        }
        if (lines.isEmpty()) return@withContext "status=failed\nThis video has no downloadable streams."
        "status=ok\nAsk the user which one they want (audio or video, and which quality), then call download_youtube_video " +
            "with kind and, for video, height. Do not choose for them unless they already said.\n" +
            "Video: ${info.name} (${info.duration / 60} min ${info.duration % 60} s)\nAvailable:\n" + lines.joinToString("\n")
    }

    /** Starts the download in the background and returns at once; progress and the result show as notifications. */
    fun start(video: String, audioOnly: Boolean, height: Int): String {
        val url = toUrl(video) ?: return "status=invalid\nGive a YouTube link or video id."
        val id = notificationIds.getAndIncrement()
        scope.launch {
          gate.withPermit {
            val notice = Progress(context, id)
            notice.update("Preparing download", 0)
            try {
                val saved = download(url, audioOnly, height, notice)
                notice.done("Download finished", "${saved.name} is in Downloads/Ultron", saved.uri)
            } catch (e: Exception) {
                notice.done("Download failed", e.message?.take(120) ?: "Could not download this video.", null)
            }
          }
        }
        return "status=ok\nDownload started in the background. A notification shows progress, and the file will be in " +
            "Downloads/Ultron when it finishes."
    }

    private class Saved(val name: String, val uri: Uri?)

    /** The file being written in Downloads/Ultron: a MediaStore entry on Android 10+, a plain file before that. */
    private class Target(val name: String, val uri: Uri?, val file: File?)

    /** Each download works in its own folder, so two running together never touch each other's temporary files. */
    private fun download(url: String, audioOnly: Boolean, height: Int, notice: Progress): Saved {
        val workDir = File(context.cacheDir, "yt-" + UUID.randomUUID()).apply { mkdirs() }
        try {
            return downloadIn(workDir, url, audioOnly, height, notice)
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun downloadIn(workDir: File, url: String, audioOnly: Boolean, height: Int, notice: Progress): Saved {
        ensureInit()
        val info = StreamInfo.getInfo(url)
        val title = safeName(info.name)
        if (audioOnly) {
            val stream = pickAudio(info.audioStreams, webm = false) ?: pickAudio(info.audioStreams, webm = true)
                ?: throw IOException("No audio stream found.")
            val suffix = stream.format?.suffix ?: "m4a"
            val cache = File(workDir, "audio-only.tmp")
            try {
                fetch(stream.content, cache, notice, "Downloading audio", 0, 100)
                return publish(cache, "$title.$suffix", mimeOf(stream.format?.mimeType, "audio/mp4"))
            } finally {
                cache.delete()
            }
        }
        val split = pickVideoOnly(info.videoOnlyStreams, height, exact = false)
        val webm = split?.format?.suffix == "webm"
        val audio = pickAudio(info.audioStreams, webm = webm)
        if (split != null && audio != null) {
            val total = (split.itagItem?.contentLength ?: -1) + (audio.itagItem?.contentLength ?: -1)
            if (total > 0) requireSpace(total)
            val videoFile = File(workDir, "video.tmp")
            val audioFile = File(workDir, "audio.tmp")
            try {
                fetch(split.content, videoFile, notice, "Downloading video", 0, 85)
                fetch(audio.content, audioFile, notice, "Downloading audio", 85, 95)
                notice.update("Joining video and audio", 96)
                val ext = if (webm) "webm" else "mp4"
                val target = openTarget("$title.$ext", if (webm) "video/webm" else "video/mp4")
                try {
                    mux(videoFile, audioFile, target, webm)
                } catch (e: Exception) {
                    abort(target)
                    throw e
                }
                return finish(target)
            } finally {
                videoFile.delete(); audioFile.delete()
            }
        }
        // Fallback: one ready-made file (video and sound together), usually 360p.
        val whole = info.videoStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.height in 1..maxOf(height, 360) }
            .maxByOrNull { it.height } ?: info.videoStreams.firstOrNull { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            ?: throw IOException("No downloadable stream found for this video.")
        val suffix = whole.format?.suffix ?: "mp4"
        val cache = File(workDir, "whole.tmp")
        try {
            fetch(whole.content, cache, notice, "Downloading video", 0, 100)
            return publish(cache, "$title.$suffix", mimeOf(whole.format?.mimeType, "video/mp4"))
        } finally {
            cache.delete()
        }
    }

    /** Picture heights the video offers as separate video streams, tallest first. */
    private fun heights(info: StreamInfo): List<Int> = info.videoOnlyStreams
        .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.height > 0 }
        .map { it.height }.distinct().sortedDescending()

    private fun pickAudio(streams: List<AudioStream>, webm: Boolean): AudioStream? =
        streams.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && (it.format?.suffix == "webm") == webm }
            .maxByOrNull { it.averageBitrate }

    /**
     * Best video-only stream for [height]: the exact height when [exact], otherwise the tallest not above it (or the
     * smallest there is). H.264 mp4 is preferred at a given height, because every phone can play it.
     */
    private fun pickVideoOnly(streams: List<VideoStream>, height: Int, exact: Boolean): VideoStream? {
        val usable = streams.filter {
            it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.height > 0 &&
                (it.format?.suffix == "mp4" || it.format?.suffix == "webm")
        }
        val sameHeight = usable.filter { it.height == height }
        val chosenHeight = when {
            sameHeight.isNotEmpty() -> height
            exact -> return null
            else -> usable.map { it.height }.filter { it <= height }.maxOrNull() ?: usable.minOfOrNull { it.height } ?: return null
        }
        return usable.filter { it.height == chosenHeight }
            .sortedWith(compareByDescending<VideoStream> { it.format?.suffix == "mp4" }.thenByDescending { it.bitrate }).firstOrNull()
    }

    private fun label(height: Int) = when (height) {
        4320 -> "8K (4320p)"
        2160 -> "4K (2160p)"
        1440 -> "2K (1440p)"
        else -> "${height}p"
    }

    private fun sizeText(bytes: Long) = if (bytes > 0) ", about ${bytes / 1_000_000} MB" else ""

    private fun mimeOf(mime: String?, fallback: String) = mime?.takeIf { it.isNotBlank() } ?: fallback

    /** Fails early, with a clear message, when the phone cannot hold the temporary files plus the final one. */
    private fun requireSpace(bytes: Long) {
        val need = (bytes * 1.1).toLong()
        val cacheFree = context.cacheDir.usableSpace
        val sharedFree = Environment.getExternalStorageDirectory().usableSpace
        if (cacheFree < need || sharedFree < need) {
            throw IOException("Not enough free storage: this download needs about ${need / 1_000_000} MB.")
        }
    }

    /** Downloads [url] to [target] in 8 MB ranges, which YouTube serves at full speed, reporting progress between [from] and [to] percent. */
    private fun fetch(url: String, target: File, notice: Progress, label: String, from: Int, to: Int) {
        target.delete()
        var offset = 0L
        var total = -1L
        target.outputStream().buffered().use { out ->
            while (total < 0 || offset < total) {
                val end = offset + CHUNK_BYTES - 1
                val request = HttpRequest.Builder().url(url).header("User-Agent", USER_AGENT)
                    .header("Range", "bytes=$offset-$end").build()
                client.newCall(request).execute().use { response ->
                    if (response.code != 206 && response.code != 200) throw IOException("YouTube refused the download (HTTP ${response.code}).")
                    val range = response.header("Content-Range")
                    if (total < 0) total = range?.substringAfter('/')?.toLongOrNull() ?: response.body?.contentLength() ?: -1L
                    val body = response.body ?: throw IOException("Empty answer from YouTube.")
                    val written = copy(body.byteStream(), out)
                    if (written == 0L) throw IOException("YouTube sent no data.")
                    offset += written
                    if (response.code == 200) total = offset // server ignored the range and sent everything
                }
                if (total > 0) notice.update(label, from + ((to - from) * offset / total).toInt())
            }
        }
    }

    private fun copy(input: java.io.InputStream, out: OutputStream): Long {
        val buffer = ByteArray(64 * 1024)
        var count = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return count
            out.write(buffer, 0, read)
            count += read
        }
    }

    /** Copies the compressed samples of one video track and one audio track into the target, without re-encoding. */
    private fun mux(video: File, audio: File, target: Target, webm: Boolean) {
        val format = if (webm) MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        val descriptor = target.uri?.let { context.contentResolver.openFileDescriptor(it, "rw") }
        val muxer = if (descriptor != null) MediaMuxer(descriptor.fileDescriptor, format) else MediaMuxer(target.file!!.absolutePath, format)
        val extractors = mutableListOf<MediaExtractor>()
        try {
            val pairs = listOf(video to "video/", audio to "audio/").map { (file, prefix) ->
                val extractor = MediaExtractor().also { extractors.add(it); it.setDataSource(file.absolutePath) }
                val index = (0 until extractor.trackCount).firstOrNull {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith(prefix) == true
                } ?: throw IOException("The downloaded ${prefix.trimEnd('/')} had no usable track.")
                extractor.selectTrack(index)
                extractor to muxer.addTrack(extractor.getTrackFormat(index))
            }
            muxer.start()
            val buffer = ByteBuffer.allocate(8 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            pairs.forEach { (extractor, track) ->
                while (true) {
                    buffer.clear()
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    info.set(0, size, extractor.sampleTime, if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                    muxer.writeSampleData(track, buffer, info)
                    extractor.advance()
                }
            }
            muxer.stop()
        } finally {
            extractors.forEach { it.release() }
            runCatching { muxer.release() }
            runCatching { descriptor?.close() }
        }
    }

    /** Creates the empty file in Downloads/Ultron where the user's file apps and gallery can see it. */
    private fun openTarget(name: String, mime: String): Target {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Ultron")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Could not create the file in Downloads.")
            return Target(name, uri, null)
        }
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Ultron").apply { mkdirs() }
        return Target(name, null, File(dir, name))
    }

    private fun finish(target: Target): Saved {
        target.uri?.let {
            context.contentResolver.update(it, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        }
        return Saved(target.name, target.uri)
    }

    private fun abort(target: Target) {
        target.uri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
        target.file?.delete()
    }

    /** Puts a finished cache file into Downloads/Ultron. */
    private fun publish(file: File, name: String, mime: String): Saved {
        val target = openTarget(name, mime)
        try {
            val out = if (target.uri != null) context.contentResolver.openOutputStream(target.uri)!! else target.file!!.outputStream()
            out.use { stream -> file.inputStream().use { it.copyTo(stream) } }
        } catch (e: Exception) {
            abort(target)
            throw e
        }
        return finish(target)
    }

    private fun safeName(title: String?): String =
        (title ?: "video").replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "").replace(Regex("\\s+"), " ").trim().take(80).ifEmpty { "video" }

    private class Progress(private val context: Context, private val id: Int) {
        private val manager = context.getSystemService(NotificationManager::class.java)
        private var lastPercent = -1

        init {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW))
        }

        fun update(text: String, percent: Int) {
            if (percent == lastPercent || !manager.areNotificationsEnabled()) return
            lastPercent = percent
            manager.notify(
                id,
                Notification.Builder(context, CHANNEL_ID).setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle("YouTube download").setContentText(text).setOnlyAlertOnce(true).setOngoing(true)
                    .setProgress(100, percent.coerceIn(0, 100), false).build()
            )
        }

        fun done(title: String, text: String, uri: Uri?) {
            if (!manager.areNotificationsEnabled()) return
            val builder = Notification.Builder(context, CHANNEL_ID).setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title).setContentText(text).setAutoCancel(true)
            if (uri != null) {
                val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, context.contentResolver.getType(uri))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                builder.setContentIntent(PendingIntent.getActivity(context, id, view, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            }
            manager.notify(id, builder.build())
        }
    }

    internal companion object {
        const val CHANNEL_ID = "assistant_downloads"
        const val CHUNK_BYTES = 8L * 1024 * 1024
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val notificationIds = AtomicInteger(4000)
        /** At most two downloads run at once; the rest wait their turn. */
        val gate = kotlinx.coroutines.sync.Semaphore(2)
        val client: OkHttpClient by lazy {
            HttpClients.shared.newBuilder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
        }

        fun toUrl(input: String): String? {
            val value = input.trim()
            if (Regex("[A-Za-z0-9_-]{11}").matches(value)) return "https://www.youtube.com/watch?v=$value"
            val host = runCatching { Uri.parse(value).host?.lowercase() }.getOrNull() ?: return null
            return value.takeIf { host == "youtu.be" || host.endsWith("youtube.com") }
        }

        @Volatile private var initialised = false

        fun ensureInit() {
            if (initialised) return
            synchronized(this) {
                if (!initialised) {
                    NewPipe.init(NewPipeDownloader, Localization("en", "US"))
                    initialised = true
                }
            }
        }
    }
}

/** Lets the extractor make its web requests through the app's OkHttp client. */
private object NewPipeDownloader : Downloader() {
    private val client: OkHttpClient by lazy {
        HttpClients.shared.newBuilder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    }

    override fun execute(request: NpRequest): NpResponse {
        val body = request.dataToSend()?.toRequestBody(null)
        val builder = HttpRequest.Builder().url(request.url())
            .method(request.httpMethod(), body ?: if (request.httpMethod() == "POST") ByteArray(0).toRequestBody("application/json".toMediaTypeOrNull()) else null)
        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }
        if (request.headers().keys.none { it.equals("User-Agent", ignoreCase = true) }) {
            builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0")
        }
        client.newCall(builder.build()).execute().use { response ->
            return NpResponse(
                response.code, response.message, response.headers.toMultimap(),
                response.body?.string().orEmpty(), response.request.url.toString()
            )
        }
    }
}
