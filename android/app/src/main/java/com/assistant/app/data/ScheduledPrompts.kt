package com.assistant.app.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.AtomicFile
import com.assistant.app.ScheduledPromptReceiver
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** How often a scheduled prompt runs again after its first run. */
enum class PromptRepeat(val id: String, val label: String) {
    ONCE("once", "Once"),
    DAILY("daily", "Every day"),
    WEEKDAYS("weekdays", "Weekdays"),
    WEEKLY("weekly", "Every week");

    companion object {
        fun from(id: String?): PromptRepeat = entries.firstOrNull { it.id == id?.lowercase()?.trim() } ?: ONCE
    }
}

/**
 * Something the user asked Ultron to do at a set time, in their own words, for example "send Sonia hello on WhatsApp".
 * [atMillis] is the next run. [lastRunAt] is 0 until it has run once; [lastResult] is what Ultron reported then.
 */
data class ScheduledPrompt(
    val id: Int,
    val prompt: String,
    val atMillis: Long,
    val repeat: PromptRepeat = PromptRepeat.ONCE,
    val enabled: Boolean = true,
    val lastRunAt: Long = 0,
    val lastResult: String = "",
    val lastOk: Boolean = true
)

const val MAX_SCHEDULED_PROMPT_LENGTH = 500
const val MAX_SCHEDULED_PROMPTS = 50

/** Voice tool that saves a scheduled prompt. */
const val SCHEDULE_PROMPT_TOOL = "schedule_prompt"

/**
 * Saves scheduled prompts in a small JSON file in the app's private storage and arms one exact alarm for each enabled one.
 * [entries] mirrors the file for the Schedule page; call [load] once (the app does it at start) so it is filled.
 */
object ScheduledPrompts {
    private const val FILE_NAME = "scheduled_prompts.json"
    /** Alarm request codes start here so they never clash with the reminder alarms of AutomationEngine. */
    private const val REQUEST_CODE_BASE = 700_000
    private val LOCK = Any()

    private val _entries = MutableStateFlow<List<ScheduledPrompt>>(emptyList())
    val entries: StateFlow<List<ScheduledPrompt>> = _entries.asStateFlow()

    fun load(context: Context) {
        synchronized(LOCK) { _entries.value = read(context).sortedBy { it.atMillis } }
    }

    fun all(context: Context): List<ScheduledPrompt> = synchronized(LOCK) { read(context) }

    fun get(context: Context, id: Int): ScheduledPrompt? = all(context).firstOrNull { it.id == id }

    /**
     * Adds a new prompt, or replaces the one with the same id when [id] is given, and arms its alarm.
     * Throws [IllegalArgumentException] with a sentence fit to show or read out.
     */
    fun save(
        context: Context, prompt: String, atMillis: Long, repeat: PromptRepeat, id: Int? = null,
        now: Long = System.currentTimeMillis()
    ): ScheduledPrompt {
        val text = prompt.trim()
        require(text.length >= 3) { "Say what Ultron should do." }
        require(text.length <= MAX_SCHEDULED_PROMPT_LENGTH) { "Keep it under $MAX_SCHEDULED_PROMPT_LENGTH characters." }
        require(atMillis > now) { "That time has already passed. Pick a time in the future." }
        val saved = synchronized(LOCK) {
            val current = read(context)
            val old = id?.let { wanted -> current.firstOrNull { it.id == wanted } }
            require(old != null || current.size < MAX_SCHEDULED_PROMPTS) { "You can keep up to $MAX_SCHEDULED_PROMPTS schedules. Delete one first." }
            val item = ScheduledPrompt(
                id = old?.id ?: ((current.maxOfOrNull { it.id } ?: 0) + 1),
                prompt = text, atMillis = atMillis, repeat = repeat, enabled = true,
                lastRunAt = old?.lastRunAt ?: 0, lastResult = old?.lastResult.orEmpty(), lastOk = old?.lastOk ?: true
            )
            write(context, current.filterNot { it.id == item.id } + item)
            item
        }
        arm(context, saved)
        return saved
    }

    fun setEnabled(context: Context, id: Int, enabled: Boolean, now: Long = System.currentTimeMillis()) {
        val item = update(context, id) { old ->
            // A one-time prompt whose time passed while it was off cannot be switched back on as it was.
            val at = if (enabled && old.atMillis <= now) nextRun(old.atMillis, old.repeat, now) ?: return@update old.copy(enabled = false)
            else old.atMillis
            old.copy(enabled = enabled, atMillis = at)
        } ?: return
        if (item.enabled) arm(context, item) else disarm(context, id)
    }

    fun delete(context: Context, id: Int) {
        synchronized(LOCK) { write(context, read(context).filterNot { it.id == id }) }
        disarm(context, id)
    }

    /** Records what happened on a run and moves a repeating prompt to its next time; a one-time prompt is switched off. */
    fun finishRun(context: Context, id: Int, result: String, ok: Boolean, now: Long = System.currentTimeMillis()) {
        val item = update(context, id) { old ->
            val next = nextRun(old.atMillis, old.repeat, now)
            old.copy(
                lastRunAt = now, lastResult = result.take(300), lastOk = ok,
                atMillis = next ?: old.atMillis, enabled = next != null && old.enabled
            )
        } ?: return
        if (item.enabled) arm(context, item)
    }

    /** Records a run the user started by hand (Run now, or unlock to run), leaving the next planned time as it is. */
    fun recordRun(context: Context, id: Int, result: String, ok: Boolean, now: Long = System.currentTimeMillis()) {
        update(context, id) { it.copy(lastRunAt = now, lastResult = result.take(300), lastOk = ok) }
    }

    /** Re-arms every enabled prompt after a reboot or an app update; ones missed while the phone was off run shortly. */
    fun rescheduleAll(context: Context, now: Long = System.currentTimeMillis()) {
        all(context).filter { it.enabled }.forEach { item ->
            arm(context, if (item.atMillis < now) item.copy(atMillis = now + 30_000) else item)
        }
    }

    /**
     * The next run after [plannedAt] for [repeat], at the same clock time, strictly after [now]; null for a one-time
     * prompt. Counting from the planned time keeps a daily 9 am prompt at 9 am instead of drifting.
     */
    fun nextRun(plannedAt: Long, repeat: PromptRepeat, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (repeat == PromptRepeat.ONCE) return null
        var next = Instant.ofEpochMilli(plannedAt).atZone(zone)
        val step = if (repeat == PromptRepeat.WEEKLY) 7L else 1L
        // Bounded so a broken clock can never spin forever.
        repeat(800) {
            next = next.plusDays(step)
            if (next.toInstant().toEpochMilli() > now && (repeat != PromptRepeat.WEEKDAYS || next.isWeekday())) {
                return next.toInstant().toEpochMilli()
            }
        }
        return null
    }

    /**
     * The first run for a clock time picked in the editor or by voice: [time] today, or on [date] when given. Without a
     * date a time already past today means tomorrow. Weekday prompts skip to Monday when that lands on a weekend.
     */
    fun firstRun(
        time: LocalTime, date: LocalDate?, repeat: PromptRepeat, now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Long {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        var at = (date ?: today).atTime(time).atZone(zone)
        if (date == null && at.toInstant().toEpochMilli() <= now) at = at.plusDays(1)
        if (repeat == PromptRepeat.WEEKDAYS) while (!at.isWeekday()) at = at.plusDays(1)
        return at.toInstant().toEpochMilli()
    }

    /** "Today, 9:00 pm", "Tomorrow, 7:30 am" or "Mon 10 Nov, 9:00 pm". */
    fun describeTime(millis: Long, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
        val at = Instant.ofEpochMilli(millis).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val day = when (at.toLocalDate()) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            else -> at.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
        }
        return "$day, ${at.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())).lowercase()}"
    }

    /** schedule_prompt, called by the voice model: the task, a 24-hour time, an optional date and how often. */
    fun saveFromVoice(context: Context, args: JSONObject, now: Long = System.currentTimeMillis()): String {
        val prompt = args.optString("prompt").trim()
        val time = runCatching { LocalTime.parse(args.optString("at_time").trim()) }.getOrNull()
            ?: return "status=invalid\nGive at_time as HH:MM, 24-hour."
        val dateText = args.optString("date").trim()
        val date = if (dateText.isEmpty()) null else runCatching { LocalDate.parse(dateText) }.getOrNull()
            ?: return "status=invalid\nGive date as YYYY-MM-DD, or leave it out."
        val repeat = PromptRepeat.from(args.optString("repeat"))
        return try {
            val item = save(context, prompt, firstRun(time, date, repeat, now), repeat, now = now)
            load(context)
            "status=ok\nScheduled #${item.id}: \"${item.prompt}\" at ${describeTime(item.atMillis, now)}" +
                (if (repeat != PromptRepeat.ONCE) ", ${repeat.label.lowercase()}" else "") +
                ". It shows on the Schedule page. When it runs, the phone must be unlocked for anything that needs the screen."
        } catch (e: IllegalArgumentException) {
            "status=invalid\n${e.message}"
        }
    }

    // ---------------------------------------------------------------- alarms

    private fun arm(context: Context, item: ScheduledPrompt) {
        load(context)
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(context, item.id)
        val exact = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()
        if (exact) runCatching { manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.atMillis, pending) }
            .onFailure { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.atMillis, pending) }
        else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.atMillis, pending)
    }

    private fun disarm(context: Context, id: Int) {
        load(context)
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context, id))
    }

    private fun pendingIntent(context: Context, id: Int): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST_CODE_BASE + id,
        Intent(context, ScheduledPromptReceiver::class.java).putExtra(ScheduledPromptReceiver.EXTRA_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // ---------------------------------------------------------------- file

    private fun update(context: Context, id: Int, change: (ScheduledPrompt) -> ScheduledPrompt): ScheduledPrompt? = synchronized(LOCK) {
        val current = read(context)
        val old = current.firstOrNull { it.id == id } ?: return null
        val new = change(old)
        write(context, current.map { if (it.id == id) new else it })
        new
    }.also { load(context) }

    private fun file(context: Context) = AtomicFile(File(context.filesDir, FILE_NAME))

    private fun read(context: Context): List<ScheduledPrompt> = try {
        val atomic = file(context)
        val text = if (atomic.baseFile.exists()) atomic.readFully().toString(Charsets.UTF_8) else "[]"
        fromJson(text)
    } catch (e: Exception) {
        emptyList()
    }

    private fun write(context: Context, items: List<ScheduledPrompt>) {
        val atomic = file(context)
        val stream = atomic.startWrite()
        try {
            stream.write(toJson(items).toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (e: Exception) {
            atomic.failWrite(stream)
            throw e
        }
    }

    internal fun toJson(items: List<ScheduledPrompt>): String {
        val array = JSONArray()
        items.forEach {
            array.put(
                JSONObject().put("id", it.id).put("prompt", it.prompt).put("at", it.atMillis).put("repeat", it.repeat.id)
                    .put("enabled", it.enabled).put("lastRunAt", it.lastRunAt).put("lastResult", it.lastResult).put("lastOk", it.lastOk)
            )
        }
        return array.toString()
    }

    internal fun fromJson(text: String): List<ScheduledPrompt> {
        val array = JSONArray(text)
        return (0 until array.length()).mapNotNull { index ->
            runCatching {
                val o = array.getJSONObject(index)
                ScheduledPrompt(
                    id = o.getInt("id"), prompt = o.getString("prompt"), atMillis = o.getLong("at"),
                    repeat = PromptRepeat.from(o.optString("repeat")), enabled = o.optBoolean("enabled", true),
                    lastRunAt = o.optLong("lastRunAt"), lastResult = o.optString("lastResult"), lastOk = o.optBoolean("lastOk", true)
                )
            }.getOrNull()
        }
    }

    private fun ZonedDateTime.isWeekday() = dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY
}
