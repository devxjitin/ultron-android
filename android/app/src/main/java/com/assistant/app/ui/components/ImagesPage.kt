package com.assistant.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.app.data.ImageLibrary
import com.assistant.app.data.StoredImage
import com.assistant.app.ui.theme.AlertDialog
import com.assistant.app.ui.theme.AssistantAccent
import com.assistant.app.ui.theme.AssistantError
import com.assistant.app.ui.theme.AssistantFieldBackground
import com.assistant.app.ui.theme.AssistantSurface
import com.assistant.app.ui.theme.AssistantTextMuted
import com.assistant.app.ui.theme.AssistantTextPrimary
import com.assistant.app.ui.theme.AssistantTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Reads a picture from a file or content address, scaled so its longest side is about [maxEdge], upright. Null if unreadable. */
private fun decodePicture(context: Context, address: String, maxEdge: Int): ImageBitmap? = try {
    val uri = Uri.parse(address)
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
    var bitmap: Bitmap? = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    }
    val rotation = try {
        resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
    } catch (e: Exception) {
        0f
    }
    if (bitmap != null && rotation != 0f) {
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation) }, true)
    }
    bitmap?.asImageBitmap()
} catch (e: Exception) {
    null
}

@Composable
private fun rememberPicture(address: String, maxEdge: Int): ImageBitmap? {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(null, address, maxEdge) {
        value = withContext(Dispatchers.IO) { decodePicture(context, address, maxEdge) }
    }.value
}

/** The pictures the assistant can show. Add one, give it a name and a few words about what is in it. */
@Composable
fun ImagesPage(
    images: List<StoredImage>,
    pendingUri: String?,
    isSaving: Boolean,
    imageFile: (StoredImage) -> File,
    onPick: () -> Unit,
    onSave: (title: String, description: String) -> Unit,
    onCancelPending: () -> Unit,
    onUpdate: (Long, String, String) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf<StoredImage?>(null) }
    PageColumn(modifier) {
        PageLabel("Pictures Ultron can show")
        Text(
            "Add a picture and say what it is. Later, say \"show me the picture of ...\" and Ultron puts it on the screen. " +
                "Turn on the Pictures connector in Connectors. Pictures stay on this phone.",
            color = AssistantTextSecondary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 4.dp)
        )
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onPick,
            colors = ButtonDefaults.buttonColors(containerColor = com.assistant.app.ui.theme.AssistantButtonFill, contentColor = com.assistant.app.ui.theme.AssistantOnButtonFill),
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text("Add a picture", fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(8.dp))
        if (images.isEmpty()) {
            Text(
                "No pictures yet.", color = AssistantTextMuted, fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 24.dp, horizontal = 4.dp)
            )
        }
        images.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { image ->
                    PictureCard(image, imageFile(image), Modifier.weight(1f)) { editing = image }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }

    if (pendingUri != null) {
        PictureDetailsDialog(
            heading = "New picture", previewAddress = pendingUri, initialTitle = "", initialDescription = "",
            confirmLabel = "Save", busy = isSaving, onConfirm = onSave, onDismiss = onCancelPending, onDelete = null
        )
    }
    editing?.let { image ->
        PictureDetailsDialog(
            heading = "Edit picture", previewAddress = Uri.fromFile(imageFile(image)).toString(),
            initialTitle = image.title, initialDescription = image.description, confirmLabel = "Save", busy = false,
            onConfirm = { title, description -> onUpdate(image.id, title, description); editing = null },
            onDismiss = { editing = null },
            onDelete = { onDelete(image.id); editing = null }
        )
    }
}

@Composable
private fun PictureCard(image: StoredImage, file: File, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val bitmap = rememberPicture(Uri.fromFile(file).toString(), 480)
    Column(modifier.clip(shape).background(AssistantSurface).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).background(AssistantFieldBackground)) {
            if (bitmap != null) Image(bitmap, contentDescription = image.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.padding(12.dp)) {
            Text(image.title, color = AssistantTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (image.description.isNotBlank()) {
                Text(image.description, color = AssistantTextSecondary, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PictureDetailsDialog(
    heading: String,
    previewAddress: String,
    initialTitle: String,
    initialDescription: String,
    confirmLabel: String,
    busy: Boolean,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?
) {
    var title by remember { mutableStateOf(initialTitle) }
    var description by remember { mutableStateOf(initialDescription) }
    val bitmap = rememberPicture(previewAddress, 720)
    val colors = TextFieldDefaults.colors(
        focusedContainerColor = AssistantFieldBackground, unfocusedContainerColor = AssistantFieldBackground,
        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
        cursorColor = AssistantAccent, focusedTextColor = AssistantTextPrimary, unfocusedTextColor = AssistantTextPrimary
    )
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(heading) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(14.dp)).background(AssistantFieldBackground)) {
                    if (bitmap != null) Image(bitmap, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
                TextField(
                    value = title, onValueChange = { title = it.take(ImageLibrary.MAX_TITLE) }, singleLine = true,
                    placeholder = { Text("Name, for example Mom's birthday", color = AssistantTextMuted, fontSize = 14.sp, maxLines = 1) },
                    shape = RoundedCornerShape(14.dp), colors = colors, modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = description, onValueChange = { description = it.take(ImageLibrary.MAX_DESCRIPTION) }, minLines = 2, maxLines = 4,
                    placeholder = { Text("What is in it? People, place, things. This is how Ultron finds it.", color = AssistantTextMuted, fontSize = 14.sp) },
                    shape = RoundedCornerShape(14.dp), colors = colors, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title, description) }, enabled = !busy) { Text(if (busy) "Saving" else confirmLabel) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = AssistantError) }
                TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
            }
        }
    )
}

/** A picture the assistant is showing, over the whole screen. Tap anywhere, press back or the close button to hide it. */
@Composable
fun ImageOverlay(address: String, title: String, onDismiss: () -> Unit) {
    val bitmap = rememberPicture(address, 2048)
    val alpha by animateFloatAsState(if (bitmap != null) 1f else 0f, tween(260), label = "image-fade")
    BackHandler(onBack = onDismiss)
    Box(
        Modifier.fillMaxSize().alpha(alpha).background(Color.Black.copy(alpha = 0.92f))
            .clickable(onClick = onDismiss).statusBarsPadding().navigationBarsPadding()
    ) {
        if (bitmap != null) {
            Image(
                bitmap, contentDescription = title, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(top = 56.dp, bottom = 64.dp, start = 12.dp, end = 12.dp)
            )
        }
        if (title.isNotBlank()) {
            Text(
                title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp, vertical = 20.dp)
            )
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(8.dp).size(44.dp).clip(RoundedCornerShape(22.dp)).clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Close picture", tint = Color.White)
        }
    }
}
