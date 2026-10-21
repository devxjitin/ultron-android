package com.assistant.app.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.app.data.DocxText
import com.assistant.app.network.ChatAttachment
import com.assistant.app.ui.theme.AssistantAccent
import com.assistant.app.ui.theme.AssistantAccentFill
import com.assistant.app.ui.theme.AssistantError
import com.assistant.app.ui.theme.AssistantFieldBackground
import com.assistant.app.ui.theme.AssistantOutline
import com.assistant.app.ui.theme.AssistantSelectedBg
import com.assistant.app.ui.theme.AssistantSurfaceElevated
import com.assistant.app.ui.theme.AssistantTextMuted
import com.assistant.app.ui.theme.AssistantTextPrimary
import com.assistant.app.ui.theme.AssistantTextSecondary
import com.assistant.app.viewmodel.ChatMessage
import com.assistant.app.viewmodel.IncomingShare
import com.assistant.app.viewmodel.ChatToolCall
import com.assistant.app.viewmodel.ChatToolState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_FILES_PER_MESSAGE = 5
private const val MAX_FILE_BYTES = 10 * 1024 * 1024
private const val MAX_TOTAL_BYTES = 12 * 1024 * 1024
private val PICKER_TYPES = arrayOf("image/*", "video/*", "application/pdf", DocxText.MIME, "text/*", "application/json")
private val IMAGE_TYPES = setOf("image/png", "image/jpeg", "image/webp", "image/heic", "image/heif")

/**
 * The home screen while no voice session runs: a typed chat with the assistant. The composer's "+"
 * attaches images, PDFs and text files, and its voice button starts a voice session instead.
 */
@Composable
fun ChatHome(
    messages: List<ChatMessage>,
    isSending: Boolean,
    assistantName: String,
    greeting: String,
    errorMessage: String?,
    /** Returns true when the message was accepted; a rejected one (for example no API key) keeps the draft. */
    onSend: (String, List<ChatAttachment>) -> Boolean,
    onEdit: (Long, String) -> Unit,
    onNotice: (String) -> Unit,
    onVoiceClick: () -> Unit,
    /** Id of the open saved chat; switching to another chat (or to a new one) drops the unsent draft. */
    chatKey: String? = null,
    /** Message to bring into view once (a search result); null for the usual "stay at the bottom". */
    focusIndex: Int? = null,
    onFocusConsumed: () -> Unit = {},
    /** The unsent message and files from when this screen was last left, so a trip to another page loses nothing. */
    initialInput: String = "",
    initialFiles: List<ChatAttachment> = emptyList(),
    onDraftSaved: (String, List<ChatAttachment>) -> Unit = { _, _ -> },
    incomingShare: IncomingShare? = null,
    onShareConsumed: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var input by rememberSaveable { mutableStateOf(initialInput) }
    val pending = remember { mutableStateListOf<ChatAttachment>().apply { addAll(initialFiles) } }
    val latestDraftSaved by androidx.compose.runtime.rememberUpdatedState(onDraftSaved)
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { latestDraftSaved(input, pending.toList()) }
    }
    var previewFile by remember { mutableStateOf<ChatAttachment?>(null) }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // A chat's first save gives it an id (null -> id) and keeps the draft; leaving a saved chat clears it.
    var shownChatKey by remember { mutableStateOf(chatKey) }
    LaunchedEffect(chatKey) {
        if (shownChatKey != null && shownChatKey != chatKey) {
            input = ""
            pending.clear()
        }
        shownChatKey = chatKey
    }

    // Used by the "+" picker and by files shared from other apps.
    val addFiles: suspend (List<Uri>) -> Unit = { uris ->
        for (uri in uris) {
            if (pending.size >= MAX_FILES_PER_MESSAGE) {
                onNotice("You can attach up to $MAX_FILES_PER_MESSAGE files at a time.")
                break
            }
            val result = withContext(Dispatchers.IO) { readAttachment(context, uri) }
            when {
                result.file == null -> onNotice(result.problem ?: "Couldn't read that file.")
                pending.sumOf { it.size } + result.file.size > MAX_TOTAL_BYTES ->
                    onNotice("Those files are too big together. Attach fewer or smaller files.")
                else -> pending.add(result.file)
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch { addFiles(uris) }
    }

    // A link or files shared from another app: files are attached and text lands in the box, never sent on its own,
    // so the user can add a question first.
    LaunchedEffect(incomingShare?.id) {
        val share = incomingShare ?: return@LaunchedEffect
        addFiles(share.uris)
        share.text?.let { input = if (input.isBlank()) it else input + "\n" + it }
        onShareConsumed(share.id)
    }

    val lastLength = messages.lastOrNull()?.text?.length ?: 0
    // Follow the reply as it streams only while the reader is at the bottom; scrolling up stops following.
    var followBottom by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }.collect { (moving, more) ->
            if (moving) followBottom = !more else if (!more) followBottom = true
        }
    }
    LaunchedEffect(focusIndex, messages.size) {
        if (focusIndex != null && focusIndex in messages.indices) {
            followBottom = false
            listState.scrollToItem(focusIndex)
            onFocusConsumed()
        }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && focusIndex == null) {
            followBottom = true
            listState.scrollToItem(messages.lastIndex)
            listState.scrollBy(100_000f)
        }
    }
    LaunchedEffect(lastLength) {
        if (followBottom && messages.isNotEmpty() && !listState.isScrollInProgress) {
            // The growing reply is already on screen, so just keep its end in view.
            listState.scrollBy(100_000f)
        }
    }
    LaunchedEffect(isSending) {
        // The Copy/Share row appears under a reply only once it has finished; bring it into view too.
        if (!isSending && followBottom && messages.isNotEmpty()) {
            delay(50)
            listState.scrollBy(100_000f)
        }
    }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val send = {
        if ((input.isNotBlank() || pending.isNotEmpty()) && !isSending) {
            if (onSend(input, pending.toList())) {
                input = ""
                pending.clear()
                // Hide the keyboard so the reply is visible as it streams in.
                focusManager.clearFocus()
                keyboard?.hide()
            }
        }
    }

    previewFile?.let { AttachmentPreviewDialog(it) { previewFile = null } }

    Column(modifier.fillMaxSize().imePadding()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(assistantName, color = AssistantTextMuted, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = greeting,
                        color = AssistantTextPrimary,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light),
                        fontSize = 30.sp,
                        lineHeight = 40.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 620.dp)
                    )
                }
            } else {
                // Messages already shown stay still; only newly added ones animate in.
                val known = remember { mutableSetOf<Long>() }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        val isNew = remember(message.id) { known.add(message.id) }
                        Entrance(isNew, message.fromUser, Modifier) {
                            ChatBubble(
                                message, canEdit = !isSending, isStreaming = isSending && message.id == messages.last().id,
                                onEdit = onEdit, onPreview = { previewFile = it }
                            )
                        }
                    }
                }
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            Text(
                errorMessage, color = AssistantError, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp), textAlign = TextAlign.Center
            )
        }

        val fieldInteraction = remember { MutableInteractionSource() }
        val focused by fieldInteraction.collectIsFocusedAsState()
        val borderColor by animateColorAsState(
            if (focused) AssistantAccent.copy(alpha = 0.75f) else AssistantOutline.copy(alpha = 0.35f), tween(250), label = "composerBorder"
        )
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(28.dp)).background(AssistantFieldBackground)
                .border(1.dp, borderColor, RoundedCornerShape(28.dp))
                .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
        ) {
            AnimatedVisibility(pending.isNotEmpty(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pending.toList().forEach { file -> AttachmentChip(file, onOpen = { previewFile = file }, onRemove = { pending.remove(file) }) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { picker.launch(PICKER_TYPES) }, enabled = !isSending) {
                    Icon(Icons.Filled.Add, contentDescription = "Attach files", tint = AssistantTextSecondary)
                }
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    interactionSource = fieldInteraction,
                    placeholder = { Text("Ask $assistantName", color = AssistantTextMuted) },
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = AssistantTextPrimary,
                        unfocusedTextColor = AssistantTextPrimary,
                        cursorColor = AssistantAccent
                    )
                )
                val hasContent = input.isNotBlank() || pending.isNotEmpty()
                val buttonSource = remember { MutableInteractionSource() }
                val pressed by buttonSource.collectIsPressedAsState()
                val pressScale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "sendPress")
                Box(
                    Modifier.size(48.dp).scale(pressScale).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(AssistantAccentFill.copy(alpha = 0.85f), AssistantAccentFill)))
                        .clickable(interactionSource = buttonSource, indication = null, enabled = !isSending || !hasContent) {
                            if (hasContent) send() else onVoiceClick()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = hasContent, label = "sendIcon",
                        transitionSpec = { (scaleIn(tween(220), initialScale = 0.4f) + fadeIn(tween(220))) togetherWith (scaleOut(tween(150), targetScale = 0.4f) + fadeOut(tween(150))) }
                    ) { sendMode ->
                        if (sendMode) Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
                        else Icon(Icons.Filled.GraphicEq, contentDescription = "Talk to $assistantName", tint = Color.White)
                    }
                }
            }
        }
    }
}

/** A file waiting to be sent: a thumbnail for images, an icon with the name for anything else. */
@Composable
private fun AttachmentChip(file: ChatAttachment, onOpen: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(14.dp)).background(AssistantSurfaceElevated).clickable(onClick = onOpen)
            .padding(start = 6.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AttachmentThumb(file, Modifier.size(36.dp))
        Text(
            file.name, color = AssistantTextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 140.dp).padding(horizontal = 8.dp)
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Remove ${file.name}", tint = AssistantTextSecondary, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun AttachmentThumb(file: ChatAttachment, modifier: Modifier) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, file) {
        value = if (file.isImage) withContext(Dispatchers.IO) { decodeThumbnail(file.data) } else null
    }
    val loaded = bitmap
    if (loaded != null) {
        Image(loaded.asImageBitmap(), contentDescription = file.name, contentScale = ContentScale.Crop, modifier = modifier.clip(RoundedCornerShape(10.dp)))
    } else {
        Box(modifier.clip(RoundedCornerShape(10.dp)).background(AssistantSelectedBg), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, tint = AssistantAccent, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage, canEdit: Boolean, isStreaming: Boolean, onEdit: (Long, String) -> Unit, onPreview: (ChatAttachment) -> Unit) {
    var editing by remember(message.id) { mutableStateOf(false) }
    var draft by remember(message.id) { mutableStateOf(message.text) }
    if (message.fromUser) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            message.attachments.forEach { file ->
                if (file.isImage) {
                    AttachmentThumb(file, Modifier.size(width = 160.dp, height = 120.dp).clickable { onPreview(file) })
                } else {
                    Row(
                        Modifier.clip(RoundedCornerShape(14.dp)).background(AssistantSurfaceElevated)
                            .clickable { onPreview(file) }.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = AssistantAccent, modifier = Modifier.size(18.dp))
                        Text(file.name, color = AssistantTextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 220.dp).padding(start = 8.dp))
                    }
                }
            }
            if (editing) {
                TextField(
                    value = draft, onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = AssistantFieldBackground, unfocusedContainerColor = AssistantFieldBackground,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = AssistantTextPrimary, unfocusedTextColor = AssistantTextPrimary
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { editing = false; draft = message.text }) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel edit", tint = AssistantTextSecondary)
                    }
                    IconButton(
                        onClick = { editing = false; onEdit(message.id, draft) },
                        enabled = draft.isNotBlank() || message.attachments.isNotEmpty()
                    ) { Icon(Icons.Filled.Check, contentDescription = "Resend", tint = AssistantAccent) }
                }
            } else if (message.text.isNotBlank()) {
                Text(
                    message.text,
                    color = AssistantTextPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.widthIn(max = 480.dp)
                        .clip(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp))
                        .background(AssistantSelectedBg)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
                if (canEdit) {
                    IconButton(onClick = { draft = message.text; editing = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit message", tint = AssistantTextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    } else {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // While a reply is on its way: three dots and what is happening, with no box or spinner.
            val runningTool = message.toolCalls.lastOrNull { it.state == ChatToolState.RUNNING }
            if (runningTool != null || message.text.isEmpty()) {
                ThinkingLine(
                    when {
                        runningTool != null -> "${runningTool.label}…"
                        message.status != null -> message.status
                        else -> "Thinking…"
                    }
                )
            }
            if (message.text.isNotEmpty()) {
                if (message.isError) {
                    Text(message.text, color = AssistantError, style = MaterialTheme.typography.bodyLarge, lineHeight = 24.sp)
                } else {
                    MarkdownText(message.text, Modifier.widthIn(max = 640.dp))
                }
            }
            val finished = !message.isError && message.text.isNotEmpty() && !isStreaming
            if (finished) LinkPreviewCards(message.text, Modifier.widthIn(max = 640.dp))
            AnimatedVisibility(finished, enter = fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 2 } + expandVertically()) {
                ResponseActions(message.text)
            }
        }
    }
}

/** Three bouncing dots plus a short pulsing label ("Thinking…", "Using Gmail: search messages…"). */
@Composable
private fun ThinkingLine(label: String) {
    val pulse by rememberInfiniteTransition(label = "thinkPulse").animateFloat(
        0.5f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "thinkAlpha"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        TypingDots()
        AnimatedContent(
            targetState = label, label = "thinkText", modifier = Modifier.padding(start = 6.dp),
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) }
        ) { text ->
            Text(text, color = AssistantTextSecondary, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.alpha(pulse))
        }
    }
}

/** Three bouncing dots shown while the first words of a reply are still on their way. */
@Composable
private fun TypingDots() {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { index ->
            val phase by transition.animateFloat(
                0f, 1f,
                infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart, initialStartOffset = StartOffset(index * 160)),
                label = "dot$index"
            )
            // One smooth up-and-down bounce per cycle, then a short rest.
            val wave = if (phase < 0.5f) kotlin.math.sin(phase * 2f * Math.PI.toFloat()) else 0f
            Box(
                Modifier.size(8.dp).graphicsLayer { translationY = -6.dp.toPx() * wave; alpha = 0.45f + 0.55f * wave }
                    .clip(CircleShape).background(AssistantAccentFill)
            )
        }
    }
}

/** Copy and Share under a finished reply. */
@Composable
private fun ResponseActions(text: String) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        IconButton(onClick = { clipboard.setText(AnnotatedString(text)); copied = true }) {
            AnimatedContent(
                targetState = copied, label = "copyIcon",
                transitionSpec = { (scaleIn(tween(200), initialScale = 0.5f) + fadeIn(tween(200))) togetherWith fadeOut(tween(120)) }
            ) { done ->
                if (done) Icon(Icons.Filled.Check, contentDescription = "Copied", tint = AssistantAccent)
                else Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy reply", tint = AssistantTextSecondary)
            }
        }
        IconButton(onClick = {
            val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, text)
            }
            runCatching { context.startActivity(android.content.Intent.createChooser(share, null)) }
        }) { Icon(Icons.Outlined.Share, contentDescription = "Share reply", tint = AssistantTextSecondary) }
    }
}

/** Slides and fades a newly added message in; messages that were already there are shown as they are. */
@Composable
private fun Entrance(animate: Boolean, fromUser: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animate) progress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }
    Box(
        modifier.graphicsLayer {
            val p = progress.value
            alpha = p
            translationY = (1f - p) * 22.dp.toPx()
            if (fromUser) {
                scaleX = 0.94f + 0.06f * p
                scaleY = 0.94f + 0.06f * p
                transformOrigin = TransformOrigin(1f, 1f)
            }
        }
    ) { content() }
}

private class AttachmentRead(val file: ChatAttachment?, val problem: String? = null)

/** Reads a picked file if its type and size are supported; otherwise says why not. */
private fun readAttachment(context: android.content.Context, uri: Uri): AttachmentRead {
    val resolver = context.contentResolver
    var name = "file"
    runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) name = it.getString(0) ?: name
        }
    }
    var mime = resolver.getType(uri).orEmpty()
    if (mime.isEmpty() || mime == "application/octet-stream") mime = mimeFromName(name)
    val supported = mime in IMAGE_TYPES || mime.startsWith("video/") || mime == "application/pdf" || mime == DocxText.MIME ||
        mime.startsWith("text/") || mime == "application/json"
    if (!supported) return AttachmentRead(null, "$name: only images, videos, PDFs, Word (.docx) and text files can be attached.")
    return try {
        val bytes = resolver.openInputStream(uri)?.use { stream ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                if (out.size() + count > MAX_FILE_BYTES) return AttachmentRead(null, "$name is too large (limit 10 MB).")
                out.write(buffer, 0, count)
            }
            out.toByteArray()
        } ?: return AttachmentRead(null, "Couldn't open $name.")
        if (bytes.isEmpty()) AttachmentRead(null, "$name is empty.") else AttachmentRead(ChatAttachment(name, mime, bytes))
    } catch (e: Exception) {
        AttachmentRead(null, "Couldn't read $name.")
    }
}

private fun mimeFromName(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "webp" -> "image/webp"
    "heic" -> "image/heic"
    "heif" -> "image/heif"
    "pdf" -> "application/pdf"
    "docx" -> DocxText.MIME
    "mp4" -> "video/mp4"
    "mov" -> "video/quicktime"
    "webm" -> "video/webm"
    "3gp" -> "video/3gpp"
    "avi" -> "video/x-msvideo"
    "mpeg", "mpg" -> "video/mpeg"
    "json" -> "application/json"
    "txt", "md", "csv", "log", "html", "xml" -> "text/plain"
    else -> ""
}

private const val MAX_THUMBNAIL_PIXELS = 1_000_000L

/** A small preview of an image (about 200 px), so large photos are never decoded at full size for the UI. */
internal fun decodeThumbnail(data: ByteArray): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 200 && bounds.outHeight / (sample * 2) >= 200) sample *= 2
    // Very tall or wide images keep one side short, so also bound the number of decoded pixels.
    while (bounds.outWidth.toLong() / sample * (bounds.outHeight / sample) > MAX_THUMBNAIL_PIXELS) sample *= 2
    return try {
        BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (e: OutOfMemoryError) {
        null
    }
}
