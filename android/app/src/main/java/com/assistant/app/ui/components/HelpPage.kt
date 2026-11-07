package com.assistant.app.ui.components

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Email
import androidx.compose.ui.platform.LocalContext
import com.assistant.app.BuildConfig
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

/** Where support emails go, from the Contact card and the About page. */
const val DEVELOPER_EMAIL = "devxjitin@gmail.com"

/** "Pixel 9, Android 16 (API 36)": the phone details a bug report and a support email carry. */
fun deviceSummary(): String = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}, " +
    "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

/**
 * Help and support: email the developer, report a bug, or ask for a skill or a connector.
 * [onRequest] gets "bug", "skill" or "connector" and opens that form.
 */
@Composable
fun HelpPage(onRequest: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    PageColumn(modifier) {
        PageLabel("Get help")
        HelpAction(
            Icons.Outlined.Email, "Email the developer",
            "Write to $DEVELOPER_EMAIL. Your app version and phone are filled in for you."
        ) { emailDeveloper(context) }
        Spacer(Modifier.height(12.dp))
        HelpAction(
            Icons.Outlined.BugReport, "Report a bug",
            "Something broke or works the wrong way? Tell the developer what happened."
        ) { onRequest("bug") }
        Spacer(Modifier.height(24.dp))
        PageLabel("Request a feature")
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

/** Opens the phone's email app with a support email to the developer; copies the address when there is no email app. */
private fun emailDeveloper(context: Context) {
    val subject = "Ultron support (v${BuildConfig.VERSION_NAME})"
    val body = "\n\n\n---\nApp version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\nPhone: ${deviceSummary()}"
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(DEVELOPER_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Email", DEVELOPER_EMAIL))
        Toast.makeText(context, "No email app found. Address copied: $DEVELOPER_EMAIL", Toast.LENGTH_LONG).show()
    }
}
