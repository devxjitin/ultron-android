package com.assistant.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.app.ui.theme.AssistantAccent
import com.assistant.app.ui.theme.AssistantOutline
import com.assistant.app.ui.theme.AssistantSelectedBg
import com.assistant.app.ui.theme.AssistantSurface
import com.assistant.app.ui.theme.AssistantTextMuted
import com.assistant.app.ui.theme.AssistantTextPrimary
import com.assistant.app.ui.theme.AssistantTextSecondary
import java.util.Locale

/** The scrolling column every full page of the menu is built on, with the same side margins as Memory. */
@Composable
fun PageColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        content()
        Spacer(Modifier.height(24.dp))
    }
}

/** A white rounded card with a soft border, the container used for each group on a page. */
@Composable
fun PageCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(AssistantSurface)
            .border(BorderStroke(1.dp, AssistantOutline), shape).padding(16.dp),
        content = content
    )
}

/** Small upper-case caption above a card. */
@Composable
fun PageLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), color = AssistantTextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp,
        modifier = modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp)
    )
}

/** A label on the left and its value on the right. */
@Composable
fun PageRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = AssistantTextSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = AssistantTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

/** A closed field that opens a menu of [options] (id to label); the pick is reported by id. */
@Composable
fun ChoiceDropdown(options: List<Pair<String, String>>, selectedId: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier) {
        SettingsDropdown(
            value = options.firstOrNull { it.first == selectedId }?.second ?: selectedId,
            options = options, selectedId = selectedId, onSelect = onSelect
        )
    }
}

/** "1.4 MB", "820 KB": a size in bytes in the unit people expect. */
fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
    else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
}
