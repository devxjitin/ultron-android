package com.assistant.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.assistant.app.ui.theme.AssistantSelectedBg
import com.assistant.app.ui.theme.AssistantTextMuted
import com.assistant.app.ui.theme.AssistantTextPrimary
import com.assistant.app.ui.theme.AssistantTextSecondary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private enum class UsageRange(val label: String, val days: Int) {
    TODAY("Today", 1), WEEK("7 days", 7), MONTH("30 days", 30), ALL("All", Int.MAX_VALUE)
}

private enum class UsageMetric(val label: String) { TALK("Talk time"), ACTIONS("Actions") }

private fun UsageDay.value(metric: UsageMetric): Double = when (metric) {
    UsageMetric.TALK -> voiceSeconds / 60.0
    UsageMetric.ACTIONS -> toolCalls.toDouble()
}

/** Plain words for what the assistant's thinking was spent on. */
private fun purposeLabel(purpose: String): String = when (purpose) {
    UsageStore.PURPOSE_CHAT -> "Typed chat"
    UsageStore.PURPOSE_PDF -> "Documents"
    UsageStore.PURPOSE_SCREEN -> "Reading the screen"
    UsageStore.PURPOSE_PERSONA -> "Setup"
    else -> "Other"
}

/** Usage: how much the assistant was used, from counters kept on this phone. */
@Composable
fun UsagePage(
    days: List<UsageDay>,
    modifier: Modifier = Modifier
) {
    var rangeIndex by rememberSaveable { mutableIntStateOf(UsageRange.WEEK.ordinal) }
    var metricIndex by rememberSaveable { mutableIntStateOf(UsageMetric.TALK.ordinal) }
    var picked by remember(rangeIndex, metricIndex) { mutableStateOf<Int?>(null) }
    val range = UsageRange.entries[rangeIndex]
    val metric = UsageMetric.entries[metricIndex.coerceIn(0, UsageMetric.entries.lastIndex)]
    val today = remember { LocalDate.now() }

    fun within(from: LocalDate, to: LocalDate): List<UsageDay> {
        val a = from.toString()
        val b = to.toString()
        return days.filter { it.day in a..b }
    }

    val current = remember(days, range) {
        if (range == UsageRange.ALL) days else within(today.minusDays(range.days - 1L), today)
    }
    val total = remember(current) { current.fold(UsageDay("")) { sum, d -> sum + d } }
    // The period right before this one, to show whether use went up or down. Not shown for "All".
    val previous = remember(days, range) {
        if (range == UsageRange.ALL) null
        else within(today.minusDays(range.days * 2L - 1L), today.minusDays(range.days.toLong()))
            .fold(UsageDay("")) { sum, d -> sum + d }
    }

    PageColumn(modifier) {
        ChoiceChips(
            options = UsageRange.entries.map { it.name to it.label },
            selectedId = range.name,
            onSelect = { id -> rangeIndex = UsageRange.entries.first { it.name == id }.ordinal }
        )
        Spacer(Modifier.height(16.dp))

        if (total.voiceSeconds == 0L && total.voiceSessions == 0 && total.toolCalls == 0) {
            PageCard {
                Text("Nothing yet for this period.", color = AssistantTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text("Talk to the assistant and your usage will show up here.", color = AssistantTextMuted, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp))
            }
            return@PageColumn
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Talk time", durationText(total.voiceSeconds), Modifier.weight(1f))
            StatTile("Conversations", total.voiceSessions.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        StatTile("Actions", total.toolCalls.toString(), Modifier.fillMaxWidth())

        if (range != UsageRange.TODAY) {
            PageLabel("Over time")
            ChoiceChips(
                options = UsageMetric.entries.map { it.name to it.label },
                selectedId = metric.name,
                onSelect = { id -> metricIndex = UsageMetric.entries.first { it.name == id }.ordinal }
            )
            Spacer(Modifier.height(12.dp))
            PageCard {
                val shownDays = if (range == UsageRange.ALL) 30 else range.days
                val byDay = remember(days) { days.associateBy { it.day } }
                val dates = remember(shownDays) { (shownDays - 1 downTo 0).map { today.minusDays(it.toLong()) } }
                val values = dates.map { byDay[it.toString()]?.value(metric) ?: 0.0 }
                val format = DateTimeFormatter.ofPattern("d MMM", androidx.compose.ui.platform.LocalConfiguration.current.locales[0])

                val index = picked?.takeIf { it in values.indices }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (index != null) "${dates[index].format(format)}: ${metricText(metric, values[index])}"
                        else metricText(metric, current.sumOf { it.value(metric) }) + if (range == UsageRange.ALL) " in 30 days" else " in total",
                        color = AssistantTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f)
                    )
                    Text("Most: " + metricText(metric, values.maxOrNull() ?: 0.0), color = AssistantTextMuted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(10.dp))
                BarChart(values, index) { picked = if (picked == it) null else it }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text(dates.first().format(format), color = AssistantTextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text(dates.last().format(format), color = AssistantTextMuted, fontSize = 12.sp)
                }
                if (previous != null) {
                    val now = current.sumOf { it.value(metric) }
                    val before = previous.value(metric)
                    comparison(now, before, if (range.days == 1) "yesterday" else "the ${range.days} days before")?.let {
                        Text(it, color = AssistantTextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                    }
                }
            }
        }

        val purposes = total.purposes.filterValues { it > 0 }
        if (purposes.isNotEmpty()) {
            PageLabel("What it was used for")
            val sum = purposes.values.sum().toDouble()
            PageCard {
                purposes.entries.sortedByDescending { it.value }.forEachIndexed { i, (purpose, tokens) ->
                    if (i > 0) Spacer(Modifier.height(12.dp))
                    val share = tokens / sum
                    Row(Modifier.fillMaxWidth()) {
                        Text(purposeLabel(purpose), color = AssistantTextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text("${(share * 100).roundToInt()}%", color = AssistantTextSecondary, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(AssistantFieldBackground)) {
                        Box(Modifier.fillMaxWidth(share.toFloat().coerceIn(0.02f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(AssistantAccent))
                    }
                }
            }
        }
    }
}

/** "Up 20% from yesterday", or null when there is nothing to compare. */
private fun comparison(now: Double, before: Double, label: String): String? = when {
    before <= 0.0 && now <= 0.0 -> null
    before <= 0.0 -> "New compared with $label"
    else -> {
        val change = ((now - before) / before * 100).roundToInt()
        when {
            change > 0 -> "Up $change% from $label"
            change < 0 -> "Down ${-change}% from $label"
            else -> "Same as $label"
        }
    }
}

private fun metricText(metric: UsageMetric, value: Double): String = when (metric) {
    UsageMetric.TALK -> durationText((value * 60).roundToInt().toLong())
    UsageMetric.ACTIONS -> "${value.roundToInt()} " + if (value.roundToInt() == 1) "action" else "actions"
}

@Composable
private fun BarChart(values: List<Double>, picked: Int?, onPick: (Int) -> Unit) {
    val max = (values.maxOrNull() ?: 0.0).coerceAtLeast(1e-9)
    Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, value ->
            Box(Modifier.weight(1f).fillMaxWidth().height(120.dp).clickable { onPick(i) }, contentAlignment = Alignment.BottomCenter) {
                val fraction = (value / max).toFloat()
                Box(
                    Modifier.fillMaxWidth().height((4 + 116 * fraction).dp)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(
                            when {
                                i == picked -> AssistantTextPrimary
                                value > 0 -> AssistantAccent
                                else -> AssistantFieldBackground
                            }
                        )
                )
            }
        }
    }
}

@Composable
private fun StatTile(title: String, value: String, modifier: Modifier) {
    PageCard(modifier) {
        Text(title, color = AssistantTextSecondary, fontSize = 13.sp)
        Text(value, color = AssistantTextPrimary, fontSize = if (value.length > 7) 22.sp else 26.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 4.dp))
    }
}

private fun durationText(seconds: Long): String = when {
    seconds < 60 -> "${seconds}s"
    seconds < 3600 -> "${seconds / 60} min"
    else -> "${seconds / 3600} h ${seconds % 3600 / 60} min"
}
