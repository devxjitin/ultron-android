package com.assistant.app.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.app.BuildConfig
import com.assistant.app.ui.LicenseSummary
import com.assistant.app.ui.theme.AppButtonShape
import com.assistant.app.ui.theme.AssistantAccent
import com.assistant.app.ui.theme.AssistantAmber
import com.assistant.app.ui.theme.AssistantError
import com.assistant.app.ui.theme.AssistantOutline
import com.assistant.app.ui.theme.AssistantSurfaceElevated
import com.assistant.app.ui.theme.AssistantTextMuted
import com.assistant.app.ui.theme.AssistantTextPrimary
import com.assistant.app.ui.theme.AssistantTextSecondary
import java.text.DateFormat
import java.util.Date

/** A key counts as "expiring soon" this many days before it ends. */
private const val EXPIRING_SOON_DAYS = 7

/**
 * Profile: who the activation key belongs to, the key and its plan, and this phone. The buyer details are the ones the
 * admin entered in Ultronapp. [ownerName] (Settings > Your name) fills in when the admin left the name empty.
 * [onRenew] is null when this build has no store link.
 */
@Composable
fun ProfilePage(
    license: LicenseSummary?,
    ownerName: String,
    onRefresh: () -> Unit,
    onRenew: (() -> Unit)?,
    onRemoveKey: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmingRemove by remember { mutableStateOf(false) }
    val status = license?.status
    val name = status?.name?.takeIf { it.isNotBlank() } ?: ownerName.takeIf { it.isNotBlank() }
    val nowSeconds = System.currentTimeMillis() / 1000

    PageColumn(modifier) {
        PageCard {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(name)
                Spacer(Modifier.height(12.dp))
                Text(name ?: "Your profile", color = AssistantTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center)
                status?.email?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = AssistantTextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
                }
                if (status != null) {
                    Spacer(Modifier.height(10.dp))
                    StatusBadge(status.active, status.expiresAt, nowSeconds)
                }
            }
        }

        if (status != null) {
            PageLabel("Account")
            PageCard {
                PageRow("Name", status.name.ifBlank { "Not set" })
                PageRow("Email", status.email.ifBlank { "Not set" })
                PageRow("Phone", status.phone.ifBlank { "Not set" })
            }
        }

        PageLabel("Activation")
        PageCard {
            if (license == null || status == null) {
                Text("No activation key on this phone.", color = AssistantTextSecondary, fontSize = 14.sp)
            } else {
                Text(license.maskedKey, color = AssistantTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 8.dp))
                PageRow("Plan", status.plan.ifBlank { "Standard" }.replaceFirstChar { it.uppercase() })
                if (status.activatedAt > 0) PageRow("Activated on", formatDate(status.activatedAt))
                PageRow("Valid until", if (status.expiresAt > 0) formatDate(status.expiresAt) else "Never expires")
                if (status.expiresAt > 0) {
                    val daysLeft = ((status.expiresAt - nowSeconds) / 86_400).coerceAtLeast(0)
                    val total = (status.expiresAt - status.activatedAt).takeIf { status.activatedAt > 0 && it > 0 }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (daysLeft == 1L) "1 day left" else "$daysLeft days left",
                        color = if (daysLeft <= EXPIRING_SOON_DAYS) AssistantAmber else AssistantTextSecondary, fontSize = 13.sp
                    )
                    if (total != null) {
                        val left = ((status.expiresAt - nowSeconds).toFloat() / total).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { left },
                            color = if (daysLeft <= EXPIRING_SOON_DAYS) AssistantAmber else AssistantAccent,
                            trackColor = AssistantOutline,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(50))
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onRefresh, shape = AppButtonShape, modifier = Modifier.weight(1f)) {
                        Text("Refresh", color = AssistantAccent)
                    }
                    if (onRenew != null) {
                        OutlinedButton(onClick = onRenew, shape = AppButtonShape, modifier = Modifier.weight(1f)) {
                            Text("Renew", color = AssistantAccent)
                        }
                    }
                }
                TextButton(onClick = { confirmingRemove = true }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text("Remove key from this phone", color = AssistantError)
                }
            }
        }

        PageLabel("This phone")
        PageCard {
            PageRow("Device", "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}")
            PageRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            PageRow("App version", BuildConfig.VERSION_NAME)
        }
    }

    if (confirmingRemove) {
        AlertDialog(
            onDismissRequest = { confirmingRemove = false },
            title = { Text("Remove activation key?") },
            text = { Text("Ultron locks on this phone until a key is entered again. The key itself stays valid.") },
            confirmButton = {
                TextButton(onClick = { confirmingRemove = false; onRemoveKey() }) { Text("Remove", color = AssistantError) }
            },
            dismissButton = { TextButton(onClick = { confirmingRemove = false }) { Text("Cancel") } }
        )
    }
}

/** A round tile with the first letters of the name, or a plain person mark when there is no name. */
@Composable
private fun Avatar(name: String?) {
    val initials = name?.split(Regex("\\s+"))?.filter { it.isNotBlank() }?.take(2)
        ?.joinToString("") { it.first().uppercase() }.orEmpty()
    Box(Modifier.size(72.dp).clip(CircleShape).background(AssistantSurfaceElevated), contentAlignment = Alignment.Center) {
        if (initials.isNotEmpty()) {
            Text(initials, color = AssistantTextPrimary, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        } else {
            Icon(Icons.Outlined.Person, contentDescription = null, tint = AssistantTextSecondary, modifier = Modifier.size(34.dp))
        }
    }
}

@Composable
private fun StatusBadge(active: Boolean, expiresAt: Long, nowSeconds: Long) {
    val soon = expiresAt > 0 && expiresAt - nowSeconds <= EXPIRING_SOON_DAYS * 86_400L
    val (label, color) = when {
        !active || (expiresAt in 1..nowSeconds) -> "Expired" to AssistantError
        soon -> "Expiring soon" to AssistantAmber
        else -> "Active" to AssistantAccent
    }
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.size(7.dp))
        Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

private fun formatDate(epochSeconds: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochSeconds * 1000))
