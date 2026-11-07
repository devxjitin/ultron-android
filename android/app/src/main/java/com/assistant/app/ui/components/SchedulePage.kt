package com.assistant.app.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.app.ScheduledPromptService
import com.assistant.app.data.MAX_SCHEDULED_PROMPT_LENGTH
import com.assistant.app.data.PromptRepeat
import com.assistant.app.data.ScheduledPrompt
import com.assistant.app.data.ScheduledPrompts
import com.assistant.app.ui.theme.AppButtonShape
import com.assistant.app.ui.theme.AssistantAccent
import com.assistant.app.ui.theme.AssistantAmber
import com.assistant.app.ui.theme.AssistantBackground
import com.assistant.app.ui.theme.AssistantError
import com.assistant.app.ui.theme.AssistantFieldBackground
import com.assistant.app.ui.theme.AssistantOutline
import com.assistant.app.ui.theme.AssistantSurface
import com.assistant.app.ui.theme.AssistantTextMuted
import com.assistant.app.ui.theme.AssistantTextPrimary
import com.assistant.app.ui.theme.AssistantTextSecondary
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Ideas shown in the editor; a tap puts one in the box. */
private val EXAMPLES = listOf(
    "Send Sonia \"Hello\" on WhatsApp",
    "Tell me today's weather",
    "Put the phone on silent",
    "Remind me to drink water"
)

/**
 * Schedule: things Ultron does on its own at a set time, written the way you would say them. Each one can run once or
 * repeat, be paused, run now, edited or deleted. The list lives in [ScheduledPrompts].
 */
@Composable
fun SchedulePage(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val items by ScheduledPrompts.entries.collectAsState()
    // null: no editor; an id of 0: a new schedule; otherwise the one being edited.
    var editing by remember { mutableStateOf<ScheduledPrompt?>(null) }
    var deleting by remember { mutableStateOf<ScheduledPrompt?>(null) }

    Box(modifier.fillMaxSize()) {
        PageColumn {
            Text(
                "Tell Ultron what to do and when, in your own words. It runs on its own at that time and tells you how it went.",
                color = AssistantTextSecondary, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { editing = ScheduledPrompt(0, "", 0) }, shape = AppButtonShape, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = com.assistant.app.ui.theme.AssistantButtonFill,
                    contentColor = com.assistant.app.ui.theme.AssistantOnButtonFill
                )
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text("New schedule")
            }

            val upcoming = items.filter { it.enabled }.sortedBy { it.atMillis }
            val paused = items.filterNot { it.enabled }.sortedByDescending { it.lastRunAt }
            if (items.isEmpty()) {
                Spacer(Modifier.height(24.dp))
                EmptyState()
            }
            if (upcoming.isNotEmpty()) {
                PageLabel("Upcoming")
                upcoming.forEachIndexed { index, item ->
                    if (index > 0) Spacer(Modifier.height(10.dp))
                    ScheduleCard(item, onEdit = { editing = item }, onDelete = { deleting = item })
                }
            }
            if (paused.isNotEmpty()) {
                PageLabel("Paused and finished")
                paused.forEachIndexed { index, item ->
                    if (index > 0) Spacer(Modifier.height(10.dp))
                    ScheduleCard(item, onEdit = { editing = item }, onDelete = { deleting = item })
                }
            }

            PageLabel("Good to know")
            PageCard {
                Text(
                    "Tasks that open apps, like sending a WhatsApp message, need the phone unlocked. If it is locked at that " +
                        "time, you get a notification: unlock and tap it, and Ultron does it then. Ultron can only use the " +
                        "connectors you added, and it uses your own Gemini key.",
                    color = AssistantTextSecondary, fontSize = 13.sp, lineHeight = 19.sp
                )
            }
        }

        editing?.let { item ->
            ScheduleEditor(item, onClose = { editing = null }, onSaved = {
                editing = null
                Toast.makeText(context, "Scheduled for ${ScheduledPrompts.describeTime(it.atMillis)}", Toast.LENGTH_SHORT).show()
            })
        }
    }

    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this schedule?") },
            text = { Text("\"${item.prompt}\" will not run again.") },
            confirmButton = {
                TextButton(onClick = { ScheduledPrompts.delete(context, item.id); deleting = null }) { Text("Delete", color = AssistantError) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun EmptyState() {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.Schedule, contentDescription = null, tint = AssistantTextMuted, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(10.dp))
        Text("Nothing scheduled yet", color = AssistantTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text(
            "Try \"Send Sonia hello on WhatsApp at 9 pm\", or just ask Ultron by voice.",
            color = AssistantTextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun ScheduleCard(item: ScheduledPrompt, onEdit: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    PageCard(Modifier.clickable(onClick = onEdit)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(item.prompt, color = AssistantTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 21.sp)
                Text(
                    if (item.enabled) "${ScheduledPrompts.describeTime(item.atMillis)} · ${item.repeat.label}"
                    else if (item.repeat == PromptRepeat.ONCE && item.lastRunAt > 0) "Done · ran once" else "Paused · ${item.repeat.label}",
                    color = if (item.enabled) AssistantAccent else AssistantTextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)
                )
            }
            Switch(
                checked = item.enabled,
                onCheckedChange = { on ->
                    ScheduledPrompts.setEnabled(context, item.id, on)
                    if (on && ScheduledPrompts.get(context, item.id)?.enabled == false) {
                        Toast.makeText(context, "Its time has passed. Edit it to pick a new time.", Toast.LENGTH_LONG).show()
                    }
                },
                colors = SwitchDefaults.colors(checkedTrackColor = AssistantAccent)
            )
        }
        if (item.lastRunAt > 0) {
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Top) {
                Icon(
                    if (item.lastOk) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, contentDescription = null,
                    tint = if (item.lastOk) AssistantAccent else AssistantAmber, modifier = Modifier.size(16.dp).padding(top = 2.dp)
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    "${ScheduledPrompts.describeTime(item.lastRunAt)}: ${item.lastResult.ifBlank { "Done." }}",
                    color = AssistantTextSecondary, fontSize = 13.sp, lineHeight = 18.sp
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = {
                ScheduledPromptService.start(context, item.id, advance = false)
                Toast.makeText(context, "Running it now", Toast.LENGTH_SHORT).show()
            }) { Text("Run now", color = AssistantAccent) }
            TextButton(onClick = onEdit) { Text("Edit", color = AssistantAccent) }
            TextButton(onClick = onDelete) { Text("Delete", color = AssistantError) }
        }
    }
}

/** A full-screen form for a new schedule ([item] id 0) or an existing one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleEditor(item: ScheduledPrompt, onClose: () -> Unit, onSaved: (ScheduledPrompt) -> Unit) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val isNew = item.id == 0
    val start = remember(item.id) {
        if (isNew) Instant.now().atZone(zone).plusHours(1).withMinute(0) else Instant.ofEpochMilli(item.atMillis).atZone(zone)
    }
    var prompt by remember(item.id) { mutableStateOf(item.prompt) }
    var time by remember(item.id) { mutableStateOf(start.toLocalTime().withSecond(0).withNano(0)) }
    var date by remember(item.id) { mutableStateOf<LocalDate>(start.toLocalDate()) }
    var repeat by remember(item.id) { mutableStateOf(if (isNew) PromptRepeat.ONCE else item.repeat) }
    var error by remember(item.id) { mutableStateOf<String?>(null) }
    val is24h = DateFormat.is24HourFormat(context)
    // Compose's own current locale, so the formats follow a language change while the form is open.
    val locale = androidx.compose.ui.text.intl.Locale.current.platformLocale

    // A full-screen dialog, so the form covers the top bar and the menu of the page behind it.
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
    Box(Modifier.fillMaxSize().background(AssistantBackground)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = AssistantTextPrimary)
                }
                Text(if (isNew) "New schedule" else "Edit schedule", color = AssistantTextPrimary, fontSize = 18.sp,
                    fontFamily = com.assistant.app.ui.theme.AssistantSerif)
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                TextField(
                    value = prompt, onValueChange = { prompt = it.take(MAX_SCHEDULED_PROMPT_LENGTH); error = null },
                    label = { Text("What should Ultron do? *") },
                    placeholder = { Text("For example: Send Sonia \"Hello\" on WhatsApp", fontSize = 14.sp) },
                    minLines = 3, maxLines = 6,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    shape = RoundedCornerShape(14.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = AssistantFieldBackground, unfocusedContainerColor = AssistantFieldBackground,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = AssistantTextPrimary, focusedTextColor = AssistantTextPrimary, unfocusedTextColor = AssistantTextPrimary,
                        focusedLabelColor = AssistantTextSecondary, unfocusedLabelColor = AssistantTextSecondary,
                        focusedPlaceholderColor = AssistantTextMuted, unfocusedPlaceholderColor = AssistantTextMuted
                    ),
                    modifier = Modifier.fillMaxWidth().border(BorderStroke(1.dp, AssistantOutline), RoundedCornerShape(14.dp))
                )
                if (prompt.isBlank()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        EXAMPLES.forEach { example ->
                            Text(
                                example, color = AssistantTextSecondary, fontSize = 13.sp,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(AssistantSurface)
                                    .border(BorderStroke(1.dp, AssistantOutline), RoundedCornerShape(50))
                                    .clickable { prompt = example }.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                PageLabel("When", Modifier.padding(top = 0.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PickerButton(
                        Icons.Outlined.Schedule,
                        time.format(DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm a", locale)),
                        Modifier.weight(1f)
                    ) {
                        TimePickerDialog(context, { _, h, m -> time = LocalTime.of(h, m); error = null }, time.hour, time.minute, is24h).show()
                    }
                    PickerButton(
                        Icons.Outlined.CalendarMonth,
                        date.format(DateTimeFormatter.ofPattern("EEE d MMM", locale)),
                        Modifier.weight(1f)
                    ) {
                        DatePickerDialog(context, { _, y, m, d -> date = LocalDate.of(y, m + 1, d); error = null },
                            date.year, date.monthValue - 1, date.dayOfMonth).apply {
                            datePicker.minDate = System.currentTimeMillis() - 1_000
                        }.show()
                    }
                }

                PageLabel("Repeat", Modifier.padding(top = 0.dp))
                ChoiceChips(
                    options = PromptRepeat.entries.map { it.id to it.label },
                    selectedId = repeat.id,
                    onSelect = { repeat = PromptRepeat.from(it); error = null }
                )

                error?.let {
                    Text(it, color = AssistantError, fontSize = 13.sp, lineHeight = 18.sp)
                }
                Spacer(Modifier.height(2.dp))
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Button(
                    onClick = {
                        try {
                            val at = ScheduledPrompts.firstRun(time, date, repeat)
                            onSaved(ScheduledPrompts.save(context, prompt, at, repeat, id = item.id.takeIf { !isNew }))
                        } catch (e: IllegalArgumentException) {
                            error = e.message
                        }
                    },
                    enabled = prompt.trim().length >= 3,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = com.assistant.app.ui.theme.AssistantButtonFill,
                        contentColor = com.assistant.app.ui.theme.AssistantOnButtonFill
                    ),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) { Text(if (isNew) "Schedule it" else "Save changes", fontSize = 15.sp, fontWeight = FontWeight.Medium) }
            }
        }
    }
    }
}

@Composable
private fun PickerButton(icon: ImageVector, text: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = AppButtonShape, modifier = modifier.height(48.dp)) {
        Icon(icon, contentDescription = null, tint = AssistantAccent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(text, color = AssistantTextPrimary, fontSize = 14.sp)
    }
}
