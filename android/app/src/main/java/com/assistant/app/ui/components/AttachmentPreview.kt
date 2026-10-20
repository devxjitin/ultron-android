package com.assistant.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.assistant.app.network.ChatAttachment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

private val PreviewBackground = Color(0xFF121012)

/** Full-screen preview of a file the user attached: a zoomable image, the pages of a PDF, or the text of a file. */
@Composable
fun AttachmentPreviewDialog(file: ChatAttachment, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(PreviewBackground).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    file.name, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close preview", tint = Color.White) }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    file.isImage -> ImagePreview(file)
                    file.isPdf -> PdfPreview(file)
                    file.isVideo -> VideoPreview(file)
                    file.isDocx -> DocxPreview(file)
                    else -> TextPreview(file)
                }
            }
        }
    }
}

@Composable
private fun DocxPreview(file: ChatAttachment) {
    val text by produceState("", file) {
        value = withContext(Dispatchers.IO) {
            com.assistant.app.data.DocxText.extract(file.data, 100_000).ifBlank { "This document has no readable text." }
        }
    }
    Text(
        text, color = Color(0xFFEDEDED), fontSize = 14.sp, lineHeight = 21.sp,
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    )
}

/** Plays a video from a temporary copy of its bytes, with the system's play/seek controls. */
@Composable
private fun VideoPreview(file: ChatAttachment) {
    val context = LocalContext.current
    val temp by produceState<File?>(null, file) {
        value = withContext(Dispatchers.IO) {
            runCatching { File.createTempFile("preview", ".mp4", context.cacheDir).also { it.writeBytes(file.data) } }.getOrNull()
        }
    }
    DisposableEffect(temp) {
        val toDelete = temp
        onDispose { if (toDelete != null) CoroutineScope(Dispatchers.IO).launch { runCatching { toDelete.delete() } } }
    }
    val video = temp
    if (video == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
        return
    }
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            android.widget.VideoView(ctx).apply {
                val controls = android.widget.MediaController(ctx)
                controls.setAnchorView(this)
                setMediaController(controls)
                setVideoURI(android.net.Uri.fromFile(video))
                setOnPreparedListener { start() }
            }
        }
    )
}

@Composable
private fun TextPreview(file: ChatAttachment) {
    val text by produceState("", file) { value = withContext(Dispatchers.IO) { file.data.toString(Charsets.UTF_8).take(100_000) } }
    Text(
        text, color = Color(0xFFEDEDED),
        fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp,
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    )
}

@Composable
private fun ImagePreview(file: ChatAttachment) {
    val bitmap by produceState<Bitmap?>(null, file) { value = withContext(Dispatchers.IO) { decodeSampled(file.data, 2048) } }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val shown = bitmap
    if (shown == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
        return
    }
    Image(
        shown.asImageBitmap(), contentDescription = file.name, contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    if (scale == 1f) { offsetX = 0f; offsetY = 0f } else { offsetX += pan.x; offsetY += pan.y }
                }
            }
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY)
    )
}

@Composable
private fun PdfPreview(file: ChatAttachment) {
    val context = LocalContext.current
    val opened by produceState<OpenedPdf?>(null, file) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val temp = File.createTempFile("preview", ".pdf", context.cacheDir).also { it.writeBytes(file.data) }
                val descriptor = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
                OpenedPdf(temp, descriptor, PdfRenderer(descriptor))
            }.getOrNull()
        }
    }
    DisposableEffect(opened) {
        val toClose = opened
        // Not tied to this composition's scope, which is already cancelled when this runs.
        onDispose { if (toClose != null) CoroutineScope(Dispatchers.IO).launch { toClose.close() } }
    }
    val pdf = opened
    if (pdf == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp)) {
        items((0 until pdf.renderer.pageCount).toList()) { index ->
            val page by produceState<Bitmap?>(null, pdf, index) { value = pdf.render(index) }
            val bitmap = page
            if (bitmap == null) {
                Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                }
            } else {
                Image(bitmap.asImageBitmap(), contentDescription = "Page ${index + 1}", contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** A PDF opened for preview; pages are rendered one at a time because [PdfRenderer] allows only one open page. */
private class OpenedPdf(val temp: File, val descriptor: ParcelFileDescriptor, val renderer: PdfRenderer) {
    private val lock = Mutex()
    @Volatile private var closed = false

    suspend fun render(index: Int): Bitmap? = withContext(Dispatchers.IO) {
        lock.withLock {
            if (closed) return@withLock null
            runCatching {
                renderer.openPage(index).use { page ->
                    val width = 1080
                    val height = (width.toFloat() * page.height / page.width).toInt().coerceIn(1, 4096)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(AndroidColor.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }.getOrNull()
        }
    }

    /** Waits for a page that is being rendered, so the native renderer is never closed underneath it. */
    suspend fun close() = withContext(NonCancellable + Dispatchers.IO) {
        lock.withLock {
            closed = true
            runCatching { renderer.close() }
            runCatching { descriptor.close() }
            runCatching { temp.delete() }
        }
    }
}

/** Decodes an image no larger than about [maxEdge] pixels, so a large photo never exhausts memory. */
private fun decodeSampled(data: ByteArray, maxEdge: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
    return BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = sample })
}
