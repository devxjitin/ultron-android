package com.assistant.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.app.ui.theme.AssistantAccent
import com.assistant.app.ui.theme.AssistantOutline
import com.assistant.app.ui.theme.AssistantSelectedBg
import com.assistant.app.ui.theme.AssistantSurface
import com.assistant.app.ui.theme.AssistantTextMuted
import com.assistant.app.ui.theme.AssistantTextPrimary
import com.assistant.app.ui.theme.AssistantTextSecondary

/** One choice in a row of selectable cards: [id] is what gets reported, the rest is what the user sees. */
class CardOption(val id: String, val title: String, val subtitle: String = "")

/** A white rounded card that turns light blue with blue text when it is the selected choice. */
@Composable
fun SelectableCard(option: CardOption, selected: Boolean, width: Dp?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.then(if (width != null) Modifier.widthIn(min = width, max = width) else Modifier.fillMaxWidth())
            .clip(shape)
            .background(if (selected) AssistantSelectedBg else AssistantSurface, shape)
            .border(BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) AssistantAccent else AssistantOutline), shape)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            option.title, color = if (selected) AssistantAccent else AssistantTextPrimary, fontSize = 16.sp,
            fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        if (option.subtitle.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                option.subtitle, color = if (selected) AssistantAccent else AssistantTextMuted, fontSize = 13.sp,
                lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Selectable cards laid out in a horizontally scrolling row, like the voice picker in modern voice apps. */
@Composable
fun CardChoiceRow(options: List<CardOption>, selectedId: String, cardWidth: Dp, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        options.forEach { SelectableCard(it, it.id == selectedId, cardWidth) { onSelect(it.id) } }
    }
}

/** Heading above a group of settings. */
@Composable
fun SettingsHeading(text: String) {
    Text(text, color = AssistantTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 2.dp, top = 22.dp, bottom = 10.dp))
}

/** A full-width card with a title, a hint and an on/off switch. */
@Composable
fun ToggleCard(title: String, checked: Boolean, onChange: (Boolean) -> Unit, hint: String = "") {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(AssistantSurface, shape)
            .border(BorderStroke(1.dp, AssistantOutline), shape)
            .clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = AssistantTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (hint.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(hint, color = AssistantTextMuted, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
        Spacer(Modifier.size(12.dp))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = AssistantAccent, checkedThumbColor = Color.White,
                uncheckedTrackColor = AssistantOutline, uncheckedBorderColor = AssistantOutline,
                uncheckedThumbColor = Color.White
            )
        )
    }
}
