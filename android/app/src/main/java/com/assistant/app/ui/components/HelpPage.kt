package com.assistant.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.app.ui.theme.AssistantAccent
import com.assistant.app.ui.theme.AssistantTextSecondary
import com.assistant.app.ui.theme.AssistantTextPrimary

/** Help and support: ask the developer for a skill or a connector. [onRequest] gets "skill" or "connector". */
@Composable
fun HelpPage(onRequest: (String) -> Unit, modifier: Modifier = Modifier) {
    PageColumn(modifier) {
        PageLabel("Ask the developer")
        Text(
            "Need something Ultron cannot do yet? Tell the developer what you need. It is sent from inside the app, and you can add your email to get a reply.",
            color = AssistantTextSecondary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 4.dp)
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

@Composable
private fun HelpAction(icon: ImageVector, title: String, text: String, onClick: () -> Unit) {
    PageCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = AssistantAccent, modifier = Modifier.size(26.dp))
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = AssistantTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(text, color = AssistantTextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}
