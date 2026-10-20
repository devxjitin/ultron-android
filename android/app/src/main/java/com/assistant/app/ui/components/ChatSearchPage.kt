package com.assistant.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.app.data.ChatSession
import com.assistant.app.ui.theme.AssistantAccent
import com.assistant.app.ui.theme.AssistantFieldBackground
import com.assistant.app.ui.theme.AssistantOutline
import com.assistant.app.ui.theme.AssistantSelectedBg
import com.assistant.app.ui.theme.AssistantSurface
import com.assistant.app.ui.theme.AssistantTextMuted
import com.assistant.app.ui.theme.AssistantTextPrimary
import com.assistant.app.ui.theme.AssistantTextSecondary

private class SearchHit(val session: ChatSession, val snippet: String?, val matches: Int)

private const val MAX_RESULTS = 50

/** Markdown the model writes (bold, code, headings) that would only look like stray symbols in a snippet. */
private val markdownMarks = Regex("\\*\\*|__|`|(?m)^#{1,6}\\s")

/** Finds the saved chats that contain every word of [query] (in the title, the messages or file names). */
private fun searchChats(sessions: List<ChatSession>, tokens: List<String>): List<SearchHit> =
    sessions.mapNotNull { session ->
        val body = session.turns.joinToString("\n") { turn ->
            turn.userText + "\n" + turn.assistantText + "\n" + turn.attachments.joinToString(" ") { it.name }
        }
        val haystack = (session.title + "\n" + body).lowercase()
        if (!tokens.all { haystack.contains(it) }) return@mapNotNull null
        val matches = tokens.sumOf { token -> haystack.windowed(token.length).count { it == token } }
        val at = body.lowercase().indexOf(tokens.first())
        val snippet = if (at >= 0) {
            val start = (at - 40).coerceIn(0, body.length)
            val end = (at + 110).coerceAtMost(body.length)
            (if (start > 0) "…" else "") + body.substring(start, end).replace(markdownMarks, "").replace(Regex("\\s+"), " ").trim() +
                (if (end < body.length) "…" else "")
        } else null
        SearchHit(session, snippet, matches)
    }.sortedByDescending { it.matches }.take(MAX_RESULTS)

private fun highlight(text: String, tokens: List<String>): AnnotatedString = buildAnnotatedString {
    append(text)
    val lower = text.lowercase()
    tokens.forEach { token ->
        var index = lower.indexOf(token)
        while (index >= 0 && index + token.length <= text.length) {
            addStyle(SpanStyle(color = AssistantAccent, fontWeight = FontWeight.Bold, background = AssistantSelectedBg), index, index + token.length)
            index = lower.indexOf(token, index + token.length)
        }
    }
}

/** Search chats: type a few words and open the conversation that has them. */
@Composable
fun ChatSearchPage(sessions: List<ChatSession>, onOpenChat: (String) -> Unit, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    val tokens = remember(query) { query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() } }
    val hits = remember(sessions, tokens) { if (tokens.isEmpty()) emptyList() else searchChats(sessions, tokens) }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        TextField(
            value = query,
            onValueChange = { query = it.take(100) },
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                .border(BorderStroke(1.dp, AssistantOutline), RoundedCornerShape(18.dp)),
            placeholder = { Text("Search your chats", color = AssistantTextMuted) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = AssistantTextMuted) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = AssistantTextMuted) }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = AssistantFieldBackground, unfocusedContainerColor = AssistantFieldBackground,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                cursorColor = AssistantAccent, focusedTextColor = AssistantTextPrimary, unfocusedTextColor = AssistantTextPrimary
            )
        )
        Spacer(Modifier.height(12.dp))
        val shown = if (tokens.isEmpty()) sessions.take(15).map { SearchHit(it, null, 0) } else hits
        val caption = when {
            tokens.isEmpty() -> if (shown.isEmpty()) null else "Recent chats"
            hits.isEmpty() -> null
            else -> "${hits.size} ${if (hits.size == 1) "chat" else "chats"} found"
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (caption != null) item { PageLabel(caption, Modifier.padding(top = 4.dp)) }
            if (shown.isEmpty()) {
                item {
                    Text(
                        if (tokens.isEmpty()) "You have no saved chats yet." else "No chat has all of those words.",
                        color = AssistantTextMuted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 20.dp, horizontal = 4.dp)
                    )
                }
            }
            items(shown, key = { it.session.id }) { hit ->
                val shape = RoundedCornerShape(16.dp)
                Column(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(shape).background(AssistantSurface)
                        .border(BorderStroke(1.dp, AssistantOutline), shape)
                        .clickable { onOpenChat(hit.session.id) }.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        highlight(hit.session.title, tokens), color = AssistantTextPrimary, fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    hit.snippet?.let {
                        Text(
                            highlight(it, tokens), color = AssistantTextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                            maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    val last = hit.session.turns.lastOrNull()?.timestampMillis ?: hit.session.startedAtMillis
                    Text(relativeDate(last), color = AssistantTextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}
