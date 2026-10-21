package com.assistant.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.produceState
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
import com.assistant.app.data.ChatSearchHit
import com.assistant.app.data.ChatSearchIndex
import com.assistant.app.data.ChatSession
import com.assistant.app.data.SearchScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.assistant.app.ui.theme.AssistantAccent
import com.assistant.app.ui.theme.AssistantFieldBackground
import com.assistant.app.ui.theme.AssistantOutline
import com.assistant.app.ui.theme.AssistantSelectedBg
import com.assistant.app.ui.theme.AssistantSurface
import com.assistant.app.ui.theme.AssistantTextMuted
import com.assistant.app.ui.theme.AssistantTextPrimary
import com.assistant.app.ui.theme.AssistantTextSecondary

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
fun ChatSearchPage(sessions: List<ChatSession>, onOpenChat: (String, Int?) -> Unit, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    var scopeIndex by rememberSaveable { mutableStateOf(SearchScope.ALL.ordinal) }
    val scope = SearchScope.entries[scopeIndex]
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    val tokens = remember(query) { query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() } }
    // The index is built once per list of chats, off the main thread; each search then only compares words.
    val index by produceState<ChatSearchIndex?>(null, sessions) { value = withContext(Dispatchers.Default) { ChatSearchIndex(sessions) } }
    // Searched off the main thread after a short pause, so typing stays smooth with a long history; null while searching.
    val hits by produceState<List<ChatSearchHit>?>(null, index, tokens, scope) {
        if (tokens.isEmpty()) {
            value = emptyList()
            return@produceState
        }
        val ready = index ?: return@produceState
        value = null
        delay(150)
        value = withContext(Dispatchers.Default) { ready.search(tokens, scope) }
    }
    val found = hits

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
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchScope.entries.forEach { ChoiceChip(it.label, it == scope, onClick = { scopeIndex = it.ordinal }) }
        }
        Spacer(Modifier.height(12.dp))
        val shown = if (tokens.isEmpty()) sessions.take(15).map { ChatSearchHit(it, null, null, 0) } else found.orEmpty()
        val caption = when {
            tokens.isEmpty() -> if (shown.isEmpty()) null else "Recent chats"
            found.isNullOrEmpty() -> null
            else -> "${found.size} ${if (found.size == 1) "chat" else "chats"} found"
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (caption != null) item { PageLabel(caption, Modifier.padding(top = 4.dp)) }
            if (shown.isEmpty() && (tokens.isEmpty() || found != null)) {
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
                        .clickable { onOpenChat(hit.session.id, hit.turnIndex) }.padding(horizontal = 16.dp, vertical = 12.dp)
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
