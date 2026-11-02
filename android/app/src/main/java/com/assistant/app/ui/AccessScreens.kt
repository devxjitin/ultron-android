package com.assistant.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.app.data.BillingPlan
import com.assistant.app.data.SubscriptionStatus
import com.assistant.app.ui.components.AppIconTile
import com.assistant.app.ui.theme.AppButtonShape
import com.assistant.app.ui.theme.AssistantAccent
import com.assistant.app.ui.theme.AssistantAccentFill
import com.assistant.app.ui.theme.AssistantBackground
import com.assistant.app.ui.theme.AssistantError
import com.assistant.app.ui.theme.AssistantSurfaceElevated
import com.assistant.app.ui.theme.AssistantTextMuted
import com.assistant.app.ui.theme.AssistantTextPrimary
import com.assistant.app.ui.theme.AssistantTextSecondary
import com.assistant.app.viewmodel.AccessState

/** The signed-in account as Settings shows it. */
data class AccountSummary(val email: String, val status: SubscriptionStatus) {
    /** "1 year plan, renews on 2 Nov 2027" or similar; empty when nothing is known. */
    val planLine: String get() {
        val plan = status.billingPlan?.label?.let { "$it plan" } ?: return ""
        if (status.currentEnd <= 0) return plan
        val date = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(status.currentEnd * 1000))
        return if (status.status == "cancelled") "$plan, ends on $date" else "$plan, renews on $date"
    }
}

/** Shown while the subscription is being checked, so the assistant never flashes before the lock. */
@Composable
fun AccessCheckingScreen() {
    AccessFrame {
        CircularProgressIndicator(color = AssistantAccent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
    }
}

@Composable
fun SignInScreen(state: AccessState.SignedOut, enabled: Boolean, onSignIn: () -> Unit) {
    AccessFrame {
        Heading("Welcome to Ultron", "Sign in with your Google account. Your plan stays with the account, even if you reinstall the app.")
        Spacer(Modifier.height(28.dp))
        PrimaryButton(
            text = if (state.busy) "Signing in…" else "Continue with Google",
            enabled = enabled && !state.busy,
            onClick = onSignIn
        )
        state.error?.let { ErrorLine(it) }
    }
}

@Composable
fun PaywallScreen(
    state: AccessState.Locked,
    onBuy: (BillingPlan) -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit
) {
    AccessFrame {
        Heading("Choose your plan", "Ultron works with an active plan. Pay with UPI, card or net banking; it renews on its own and you can cancel anytime.")
        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            BillingPlan.entries.forEach { plan ->
                PlanCard(plan, best = plan == BillingPlan.YEARLY, busy = state.busyPlan == plan,
                    enabled = state.busyPlan == null) { onBuy(plan) }
            }
        }
        state.error?.let { ErrorLine(it) }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onRefresh, enabled = state.busyPlan == null) { Text("Refresh", color = AssistantAccent) }
            TextButton(onClick = onSignOut, enabled = state.busyPlan == null) { Text("Sign out", color = AssistantTextMuted) }
        }
        if (state.email.isNotBlank()) {
            Text("Signed in as ${state.email}", color = AssistantTextMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PlanCard(plan: BillingPlan, best: Boolean, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(AssistantSurfaceElevated, shape)
            .border(BorderStroke(1.dp, AssistantAccent.copy(alpha = if (best) 0.7f else 0.25f)), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(plan.label, color = AssistantTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            val note = if (plan.months > 1) "Rs ${plan.perMonthRupees} a month" else "Billed every month"
            Text(if (best) "$note, best value" else note, color = AssistantTextMuted, fontSize = 12.sp)
        }
        if (busy) {
            CircularProgressIndicator(color = AssistantAccent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Text("Rs ${plan.priceRupees}", color = AssistantAccent, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AccessFrame(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(AssistantBackground), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 420.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) { content() }
    }
}

@Composable
private fun Heading(title: String, subtitle: String) {
    AppIconTile(56.dp)
    Spacer(Modifier.height(18.dp))
    Text(title, color = AssistantTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(subtitle, color = AssistantTextSecondary, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = AppButtonShape,
        colors = ButtonDefaults.buttonColors(containerColor = AssistantAccentFill, contentColor = Color.White),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 14.dp)
    ) { Text(text) }
}

@Composable
private fun ErrorLine(text: String) {
    Spacer(Modifier.height(14.dp))
    Text(text, color = AssistantError, fontSize = 13.sp, textAlign = TextAlign.Center)
}
