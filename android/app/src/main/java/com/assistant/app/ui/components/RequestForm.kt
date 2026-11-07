package com.assistant.app.ui.components

import android.util.Patterns
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.app.ui.theme.AssistantAccent
import com.assistant.app.ui.theme.AssistantAccentFill
import com.assistant.app.ui.theme.AssistantBackground
import com.assistant.app.ui.theme.AssistantError
import com.assistant.app.ui.theme.AssistantErrorFill
import com.assistant.app.ui.theme.AssistantFieldBackground
import com.assistant.app.ui.theme.AssistantOutline
import com.assistant.app.ui.theme.AssistantSurface
import com.assistant.app.ui.theme.AssistantTextMuted
import com.assistant.app.ui.theme.AssistantTextPrimary
import com.assistant.app.ui.theme.AssistantTextSecondary

/** What the request form sends. [type] is "skill" or "connector". */
data class FeatureRequest(val type: String, val name: String, val details: String, val examples: String, val email: String)

private const val MAX_NAME = 80
private const val MAX_DETAILS = 1300
private const val MAX_LINK = 150
private const val MAX_EXAMPLES = 600
private const val MAX_EMAIL = 120
private const val MIN_NAME = 2
private const val MIN_DETAILS = 10

/** Words that change between the skill form and the connector form. */
private class FormText(
    val icon: ImageVector,
    val title: String,
    val intro: String,
    val nameLabel: String,
    val nameHint: String,
    val detailsLabel: String,
    val detailsHint: String,
    val examplesHint: String
)

private val SkillText = FormText(
    icon = Icons.Outlined.Bolt,
    title = "Request a skill",
    intro = "A skill teaches Ultron how to do something, such as a routine or a way of answering.",
    nameLabel = "Skill name", nameHint = "For example: Daily briefing",
    detailsLabel = "What should it do?", detailsHint = "Describe the steps, when it should run and what you expect back. The more detail, the better.",
    examplesHint = "For example: give me my morning briefing"
)

private val ConnectorText = FormText(
    icon = Icons.Outlined.Extension,
    title = "Request a connector",
    intro = "A connector lets Ultron use another app or service, such as music, notes or a smart home.",
    nameLabel = "App or service", nameHint = "For example: Spotify",
    detailsLabel = "What should Ultron do with it?", detailsHint = "Say what you want to do in that app: play, read, add, control. The more detail, the better.",
    examplesHint = "For example: play my liked songs on Spotify"
)

/**
 * A full screen form to ask the developer for a skill or a connector. Each kind has its own screen with its own wording.
 * [onSend] starts sending; the caller keeps [sending] and [error] up to date and closes the form with [onBack] when it worked.
 */
@Composable
fun FeatureRequestPage(
    type: String,
    sending: Boolean,
    error: String?,
    onSend: (FeatureRequest) -> Unit,
    onBack: () -> Unit
) {
    val isConnector = type == "connector"
    val text = if (isConnector) ConnectorText else SkillText
    var name by remember(type) { mutableStateOf("") }
    var details by remember(type) { mutableStateOf("") }
    var link by remember(type) { mutableStateOf("") }
    var examples by remember(type) { mutableStateOf("") }
    var email by remember(type) { mutableStateOf("") }

    val nameOk = name.trim().length >= MIN_NAME
    val detailsOk = details.trim().length >= MIN_DETAILS
    val emailOk = email.isBlank() || Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val ready = nameOk && detailsOk && emailOk && !sending

    BackHandler(enabled = !sending, onBack = onBack)
    Box(Modifier.fillMaxSize().background(AssistantBackground)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, enabled = !sending) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = AssistantTextPrimary)
                }
                Text(text.title, color = AssistantTextPrimary, fontSize = 18.sp, fontFamily = com.assistant.app.ui.theme.AssistantSerif)
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Intro(text)
                FormField(
                    value = name, onChange = { name = it.take(MAX_NAME) }, max = MAX_NAME,
                    label = text.nameLabel, hint = text.nameHint, required = true,
                    problem = "Use at least $MIN_NAME letters".takeIf { !nameOk },
                    capitalization = KeyboardCapitalization.Words
                )
                if (isConnector) {
                    FormField(
                        value = link, onChange = { link = it.take(MAX_LINK) }, max = MAX_LINK,
                        label = "Website or app link", hint = "Optional. Where to find it, for example spotify.com",
                        required = false, keyboardType = KeyboardType.Uri
                    )
                }
                FormField(
                    value = details, onChange = { details = it.take(MAX_DETAILS) }, max = MAX_DETAILS,
                    label = text.detailsLabel, hint = text.detailsHint, required = true,
                    problem = "Say a little more, at least $MIN_DETAILS characters".takeIf { !detailsOk },
                    minLines = 4, maxLines = 8, capitalization = KeyboardCapitalization.Sentences
                )
                FormField(
                    value = examples, onChange = { examples = it.take(MAX_EXAMPLES) }, max = MAX_EXAMPLES,
                    label = "What would you say to it?", hint = "Optional. ${text.examplesHint}",
                    required = false, minLines = 2, maxLines = 5, capitalization = KeyboardCapitalization.Sentences
                )
                FormField(
                    value = email, onChange = { email = it.take(MAX_EMAIL) }, max = MAX_EMAIL,
                    label = "Your email", hint = "Optional. So the developer can reply to you",
                    required = false, keyboardType = KeyboardType.Email, last = true,
                    problem = "That email does not look right".takeIf { !emailOk }
                )
                if (error != null) ErrorBox(error)
                Spacer(Modifier.height(2.dp))
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                SendButton(sending = sending, enabled = ready) {
                    val extra = link.trim().takeIf { isConnector && it.isNotEmpty() }?.let { "\n\nWebsite or app: $it" }.orEmpty()
                    onSend(FeatureRequest(type, name.trim(), details.trim() + extra, examples.trim(), email.trim()))
                }
            }
        }
    }
}

@Composable
private fun Intro(text: FormText) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AssistantSurface).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(com.assistant.app.ui.theme.AssistantSurfaceElevated), contentAlignment = Alignment.Center) {
            Icon(text.icon, contentDescription = null, tint = AssistantTextPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.size(14.dp))
        Text(text.intro, color = AssistantTextSecondary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
    }
}

/** One labelled text box. The border turns to the accent colour while it has focus, and shows red when [problem] is set. */
@Composable
private fun FormField(
    value: String,
    onChange: (String) -> Unit,
    max: Int,
    label: String,
    hint: String,
    required: Boolean,
    problem: String? = null,
    minLines: Int = 1,
    maxLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    last: Boolean = false
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    // A problem is only shown after the person has left the box once, so an empty form is not covered in red.
    var touched by remember { mutableStateOf(false) }
    var hadFocus by remember { mutableStateOf(false) }
    val showProblem = problem != null && touched && !focused
    val border by animateColorAsState(
        when {
            showProblem -> AssistantError
            focused -> AssistantTextMuted
            else -> AssistantOutline
        },
        label = "fieldBorder"
    )
    val colors = TextFieldDefaults.colors(
        focusedContainerColor = AssistantFieldBackground, unfocusedContainerColor = AssistantFieldBackground,
        errorContainerColor = AssistantFieldBackground,
        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, errorIndicatorColor = Color.Transparent,
        cursorColor = AssistantTextPrimary, errorCursorColor = AssistantError,
        focusedTextColor = AssistantTextPrimary, unfocusedTextColor = AssistantTextPrimary, errorTextColor = AssistantTextPrimary,
        focusedLabelColor = AssistantTextSecondary, unfocusedLabelColor = AssistantTextSecondary, errorLabelColor = AssistantError,
        focusedPlaceholderColor = AssistantTextMuted, unfocusedPlaceholderColor = AssistantTextMuted
    )
    Column {
        TextField(
            value = value, onValueChange = onChange,
            label = { Text(if (required) "$label *" else label, maxLines = 1) },
            placeholder = { Text(hint, fontSize = 14.sp, lineHeight = 19.sp) },
            singleLine = maxLines == 1, minLines = minLines, maxLines = maxLines,
            isError = showProblem, interactionSource = source,
            keyboardOptions = KeyboardOptions(
                capitalization = capitalization, keyboardType = keyboardType,
                imeAction = if (last) ImeAction.Done else if (maxLines > 1) ImeAction.Default else ImeAction.Next
            ),
            shape = RoundedCornerShape(14.dp), colors = colors,
            modifier = Modifier.fillMaxWidth().border(BorderStroke(1.dp, border), RoundedCornerShape(14.dp))
                .onFocusChanged {
                    if (it.isFocused) hadFocus = true else if (hadFocus) touched = true
                }
        )
        Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 4.dp)) {
            Text(
                if (showProblem) problem.orEmpty() else "",
                color = AssistantError, fontSize = 12.sp, modifier = Modifier.weight(1f)
            )
            if (value.length >= max * 0.8) Text("${value.length}/$max", color = AssistantTextMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ErrorBox(message: String) {
    Text(
        message, color = AssistantError, fontSize = 13.sp, lineHeight = 18.sp,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AssistantErrorFill)
            .border(BorderStroke(1.dp, AssistantError.copy(alpha = 0.5f)), RoundedCornerShape(14.dp)).padding(12.dp)
    )
}

/** A wide button that shrinks a little while it is pressed and shows a spinner while the request is going out. */
@Composable
private fun SendButton(sending: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, label = "sendScale")
    Button(
        onClick = { if (!sending) onClick() }, enabled = enabled || sending, interactionSource = source,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = com.assistant.app.ui.theme.AssistantButtonFill, contentColor = com.assistant.app.ui.theme.AssistantOnButtonFill,
            disabledContainerColor = if (sending) com.assistant.app.ui.theme.AssistantButtonFill else com.assistant.app.ui.theme.AssistantSurfaceElevated,
            disabledContentColor = if (sending) com.assistant.app.ui.theme.AssistantOnButtonFill else AssistantTextMuted
        ),
        modifier = Modifier.fillMaxWidth().height(50.dp).scale(scale)
    ) {
        if (sending) {
            CircularProgressIndicator(color = com.assistant.app.ui.theme.AssistantOnButtonFill, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(10.dp))
            Text("Sending", fontSize = 15.sp, fontWeight = FontWeight.Medium)
        } else {
            Text("Send request", fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}
