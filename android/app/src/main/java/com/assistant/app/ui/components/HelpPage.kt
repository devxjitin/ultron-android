package com.assistant.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.app.ui.theme.AssistantAccent
import com.assistant.app.ui.theme.AssistantOutline
import com.assistant.app.ui.theme.AssistantSurface
import com.assistant.app.ui.theme.AssistantTextMuted
import com.assistant.app.ui.theme.AssistantTextPrimary
import com.assistant.app.ui.theme.AssistantTextSecondary

/** Help and support: ask the developer for a skill or a connector. [onRequest] gets "skill" or "connector". */
@Composable
fun HelpPage(onRequest: (String) -> Unit, modifier: Modifier = Modifier) {
    PageColumn(modifier) {
        PageLabel("Ask the developer")
        Text(
            "Need something Ultron cannot do yet? Tell the developer what you need. It is sent from inside the app, and you can add your email to get a reply.",
            color = AssistantTextSecondary, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(horizontal = 4.dp)
        )
        Spacer(Modifier.height(14.dp))
        HelpAction(
            Icons.Outlined.Bolt, "Request a skill",
            "A skill teaches Ultron how to do something, such as a routine or a way of answering."
        ) { onRequest("skill") }
        Spacer(Modifier.height(12.dp))
        HelpAction(
            Icons.Outlined.Extension, "Request a connector",
            "A connector lets Ultron use another app or service, such as music, notes or a smart home."
        ) { onRequest("connector") }
    }
}

/** A card that shrinks a little and lights its border while it is pressed. */
@Composable
private fun HelpAction(icon: ImageVector, title: String, text: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, label = "helpScale")
    val border by animateColorAsState(if (pressed) AssistantTextMuted else Color.Transparent, label = "helpBorder")
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().scale(scale).clip(shape).background(AssistantSurface).border(BorderStroke(1.dp, border), shape)
            .clickable(interactionSource = source, indication = LocalIndication.current, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(com.assistant.app.ui.theme.AssistantSurfaceElevated), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = AssistantTextPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = AssistantTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(text, color = AssistantTextSecondary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.size(8.dp))
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = AssistantTextMuted)
    }
}
