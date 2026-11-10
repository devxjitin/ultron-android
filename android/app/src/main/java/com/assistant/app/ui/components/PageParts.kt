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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
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
import com.assistant.app.ui.theme.AssistantFieldBackground
import com.assistant.app.ui.theme.AssistantSurface
import com.assistant.app.ui.theme.AssistantTextMuted
import com.assistant.app.ui.theme.AssistantTextPrimary
import com.assistant.app.ui.theme.AssistantTextSecondary
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.assistant.app.ui.theme.AssistantAccentFill
import com.assistant.app.ui.theme.CardShape
import java.util.Locale

/** The scrolling column every full page of the menu is built on, with the same side margins as Memory. */
@Composable
fun PageColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        content()
        Spacer(Modifier.height(24.dp))
    }
}

/** A flat rounded card, a shade off the page, the container used for each group on a page. */
@Composable
fun PageCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = CardShape
    Column(
        modifier.fillMaxWidth().clip(shape).background(AssistantSurface).padding(16.dp),
        content = content
    )
}

/** The one section caption used on every page: sentence case, 13sp, grey unless a page passes [color]. */
@Composable
fun PageLabel(
    text: String, modifier: Modifier = Modifier, color: Color = AssistantTextSecondary,
    top: Dp = 20.dp, bottom: Dp = 8.dp
) {
    Text(
        text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        modifier = modifier.padding(start = 4.dp, top = top, bottom = bottom)
    )
}

/** Shared on/off switch colours: a muted thumb and outlined track when off, so the off state shows on a light card. */
@Composable
fun assistantSwitchColors() = SwitchDefaults.colors(
    checkedTrackColor = AssistantAccentFill, checkedThumbColor = Color.White, checkedBorderColor = AssistantAccentFill,
    uncheckedTrackColor = AssistantFieldBackground, uncheckedBorderColor = AssistantTextMuted,
    uncheckedThumbColor = AssistantTextMuted
)

/** A label on the left and its value on the right. */
@Composable
fun PageRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = AssistantTextSecondary, fontSize = 14.sp, maxLines = 1)
        Spacer(Modifier.width(16.dp))
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

/**
 * A row of category buttons. [selected] null means "All". Tapping the chosen category again goes back to All.
 */
@Composable
fun CategoryChips(categories: List<String>, selected: String?, onSelect: (String?) -> Unit, modifier: Modifier = Modifier) {
    if (categories.size < 2) return
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        (listOf<String?>(null) + categories).forEach { category ->
            val on = category == selected
            FilterChip(
                selected = on,
                onClick = { onSelect(if (on) null else category) },
                label = { Text(category ?: "All", fontSize = 13.sp) },
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    labelColor = AssistantTextSecondary,
                    selectedContainerColor = AssistantSelectedBg,
                    selectedLabelColor = AssistantTextPrimary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true, selected = on,
                    borderColor = AssistantOutline, selectedBorderColor = AssistantTextMuted
                )
            )
        }
    }
}

/** A row of buttons where exactly one choice (by id) is picked; unlike [CategoryChips] there is no "All". */
@Composable
fun ChoiceChips(options: List<Pair<String, String>>, selectedId: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (id, label) ->
            val on = id == selectedId
            FilterChip(
                selected = on,
                onClick = { onSelect(id) },
                label = { Text(label, fontSize = 13.sp) },
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    labelColor = AssistantTextSecondary,
                    selectedContainerColor = AssistantSelectedBg,
                    selectedLabelColor = AssistantTextPrimary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true, selected = on,
                    borderColor = AssistantOutline, selectedBorderColor = AssistantTextMuted
                )
            )
        }
    }
}

/**
 * The icon style used across the app, the same quiet look as the menu: a thin outlined glyph in a neutral colour, never red
 * or tinted. With [boxed] it sits on a soft neutral square, for card headers.
 */
@Composable
fun NeutralIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    boxed: Boolean = true,
    box: androidx.compose.ui.unit.Dp = 44.dp,
    size: androidx.compose.ui.unit.Dp = 22.dp
) {
    if (boxed) {
        Box(
            modifier.size(box).clip(RoundedCornerShape(12.dp)).background(com.assistant.app.ui.theme.AssistantSelectedBg),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Icon(icon, contentDescription = null, tint = AssistantTextPrimary, modifier = Modifier.size(size))
        }
    } else {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = AssistantTextSecondary, modifier = modifier.size(size))
    }
}
