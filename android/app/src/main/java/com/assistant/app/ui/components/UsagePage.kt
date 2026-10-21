package com.assistant.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.app.data.UsageDay
import com.assistant.app.data.UsageStore
import com.assistant.app.ui.theme.AssistantAccent
import com.assistant.app.ui.theme.AssistantFieldBackground
import com.assistant.app.ui.theme.AssistantTextMuted
import com.assistant.app.ui.theme.AssistantTextPrimary
import com.assistant.app.ui.theme.AssistantTextSecondary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class UsageRange(val label: String, val days: Int) {
    TODAY("Today", 1), WEEK("7 days", 7), MONTH("30 days", 30), ALL("All time", Int.MAX_VALUE)
}

private enum class UsageMetric(val label: String) { MESSAGES("Messages"), TOKENS("Tokens"), VOICE("Voice"), TOOLS("Tools") }

private fun UsageDay.value(metric: UsageMetric): Double = when (metric) {
    UsageMetric.MESSAGES -> chatMessages.toDouble()
    UsageMetric.TOKENS -> tokens.toDouble()
    UsageMetric.VOICE -> voiceSeconds / 60.0
    UsageMetric.TOOLS -> toolCalls.toDouble()
}

/** Usage: how much the assistant was used, from counters kept on this phone. */
@Composable
fun UsagePage(
    days: List<UsageDay>,
    modifier: Modifier = Modifier,
    autoTitleChats: Boolean = false,
    onAutoTitleChange: (Boolean) -> Unit = {},
    autoPersonaUpdate: Boolean = false,
    onAutoPersonaChange: (Boolean) -> Unit = {},
    dailyTokenBudget: Long = 0L,
    onDailyBudgetChange: (Long) -> Unit = {}
) {
    var rangeIndex by rememberSaveable { mutableIntStateOf(UsageRange.WEEK.ordinal) }
    var metricIndex by rememberSaveable { mutableIntStateOf(UsageMetric.MESSAGES.ordinal) }
    val range = UsageRange.entries[rangeIndex]
    val metric = UsageMetric.entries[metricIndex]
    val today = remember { LocalDate.now() }

    val inRange = remember(days, range) {
        if (range == UsageRange.ALL) days
        else {
            val first = today.minusDays(range.days - 1L).toString()
            days.filter { it.day >= first }
        }
    }
    val total = remember(inRange) { inRange.fold(UsageDay("")) { sum, d -> sum + d } }

    PageColumn(modifier) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UsageRange.entries.forEach { ChoiceChip(it.label, it == range, onClick = { rangeIndex = it.ordinal }) }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Messages", total.chatMessages.toString(), "typed chat", Modifier.weight(1f))
            StatTile("Tokens", compactNumber(total.tokens), "typed chat", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Voice", durationText(total.voiceSeconds), "${total.voiceSessions} sessions", Modifier.weight(1f))
            StatTile("Tool calls", total.toolCalls.toString(), "all tools", Modifier.weight(1f))
        }

        if (range != UsageRange.TODAY) {
            PageLabel("Activity")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UsageMetric.entries.forEach { ChoiceChip(it.label, it == metric, onClick = { metricIndex = it.ordinal }) }
            }
            Spacer(Modifier.height(12.dp))
            PageCard {
                val shownDays = if (range == UsageRange.ALL) 30 else range.days
                val byDay = remember(days) { days.associateBy { it.day } }
                val dates = remember(shownDays) { (shownDays - 1 downTo 0).map { today.minusDays(it.toLong()) } }
                val values = dates.map { byDay[it.toString()]?.value(metric) ?: 0.0 }
                BarChart(values)
                Spacer(Modifier.height(8.dp))
                val format = DateTimeFormatter.ofPattern("d MMM", androidx.compose.ui.platform.LocalConfiguration.current.locales[0])
                Row(Modifier.fillMaxWidth()) {
                    Text(dates.first().format(format), color = AssistantTextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text(dates.last().format(format), color = AssistantTextMuted, fontSize = 12.sp)
                }
                if (values.all { it == 0.0 }) {
                    Text("No activity in this period.", color = AssistantTextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                }
            }
        }

        if (total.purposes.isNotEmpty()) {
            PageLabel("Where tokens went")
            PageCard {
                total.purposes.entries.sortedByDescending { it.value }.forEach { (name, tokens) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(purposeLabel(name), color = AssistantTextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text(compactNumber(tokens), color = AssistantTextSecondary, fontSize = 14.sp)
                    }
                }
            }
        }

        PageLabel("Limits and automatic extras")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Daily token limit", color = AssistantTextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, end = 4.dp))
            BUDGET_CHOICES.forEach { (label, tokens) -> ChoiceChip(label, dailyTokenBudget == tokens, onClick = { onDailyBudgetChange(tokens) }) }
        }
        Spacer(Modifier.height(10.dp))
        ToggleCard(
            "Name chats with AI", autoTitleChats, onAutoTitleChange,
            "Uses a few tokens after each chat. Off: a chat is named after its first question."
        )
        Spacer(Modifier.height(10.dp))
        ToggleCard(
            "Update assistant from documents", autoPersonaUpdate, onAutoPersonaChange,
            "Rewrites the assistant's instructions whenever you add, change or delete a knowledge document. Off by default; it costs tokens each time."
        )

        Text(
            "Counted on this phone only and never sent anywhere. Tokens are known for typed chat; voice is measured in time.",
            color = AssistantTextMuted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 16.dp, start = 4.dp)
        )
    }
}

@Composable
private fun BarChart(values: List<Double>) {
    val max = (values.maxOrNull() ?: 0.0).coerceAtLeast(1e-9)
    Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        values.forEach { value ->
            Box(Modifier.weight(1f).fillMaxWidth().height(120.dp), contentAlignment = Alignment.BottomCenter) {
                val fraction = (value / max).toFloat()
                Box(
                    Modifier.fillMaxWidth().height((4 + 116 * fraction).dp)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(if (value > 0) AssistantAccent else AssistantFieldBackground)
                )
            }
        }
    }
}

@Composable
private fun StatTile(title: String, value: String, caption: String, modifier: Modifier) {
    PageCard(modifier) {
        Text(title, color = AssistantTextSecondary, fontSize = 13.sp)
        Text(value, color = AssistantTextPrimary, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        Text(caption, color = AssistantTextMuted, fontSize = 12.sp, maxLines = 1)
    }
}

private val BUDGET_CHOICES = listOf("Off" to 0L, "50K" to 50_000L, "200K" to 200_000L, "1M" to 1_000_000L)

private fun purposeLabel(purpose: String) = when (purpose) {
    UsageStore.PURPOSE_CHAT -> "Chat replies"
    UsageStore.PURPOSE_TITLE -> "Chat names"
    UsageStore.PURPOSE_PERSONA -> "Assistant instructions"
    UsageStore.PURPOSE_PDF -> "PDF import"
    else -> purpose
}

private fun compactNumber(n: Long): String = when {
    n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0)
    n >= 10_000 -> String.format(Locale.US, "%.0fK", n / 1_000.0)
    n >= 1_000 -> String.format(Locale.US, "%.1fK", n / 1_000.0)
    else -> n.toString()
}

private fun durationText(seconds: Long): String = when {
    seconds < 60 -> "${seconds}s"
    seconds < 3600 -> "${seconds / 60} min"
    else -> "${seconds / 3600} h ${seconds % 3600 / 60} min"
}
