package com.assistant.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.app.data.Personalities
import com.assistant.app.data.Personality
import com.assistant.app.ui.theme.CardShape
import com.assistant.app.ui.theme.AssistantAccent
import com.assistant.app.ui.theme.AssistantOutline
import com.assistant.app.ui.theme.AssistantSurface
import com.assistant.app.ui.theme.AssistantTextMuted
import com.assistant.app.ui.theme.AssistantTextPrimary
import com.assistant.app.ui.theme.AssistantTextSecondary

/** One card per built-in personality, with a sample line; the chosen one has an accent border. */
@Composable
fun PersonalityPicker(selectedId: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Personalities.all.forEach { personality ->
            PersonalityCard(personality, personality.id == selectedId) { onSelect(personality.id) }
        }
    }
}

@Composable
private fun PersonalityCard(personality: Personality, selected: Boolean, onClick: () -> Unit) {
    val shape = CardShape
    val border by animateColorAsState(if (selected) AssistantAccent else AssistantOutline, label = "personalityBorder")
    Row(
        Modifier.fillMaxWidth().clip(shape).background(AssistantSurface, shape)
            .border(BorderStroke(if (selected) 1.5.dp else 1.dp, border), shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(start = 4.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected, onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = AssistantAccent, unselectedColor = AssistantTextMuted),
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(personality.label, color = AssistantTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(personality.tagline, color = AssistantTextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                modifier = Modifier.padding(top = 2.dp))
            Text(personality.sample, color = AssistantTextMuted, fontSize = 13.sp, fontStyle = FontStyle.Italic,
                lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
