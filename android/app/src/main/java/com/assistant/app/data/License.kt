package com.assistant.app.data

import android.content.Context
import org.json.JSONObject

/** Activation keys sold on the website store look like ULTR-7K2Q-M9XD-4H8P-ZC3N (Crockford base32, no I, L, O or U). */
object LicenseKey {
    private const val PREFIX = "ULTR"
    private const val GROUPS = 4
    private const val GROUP_LENGTH = 4
    private val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toSet()

    /** Upper-cases, drops spaces and dashes and regroups, so a pasted or hand-typed key matches; null when it can't be one. */
    fun normalize(input: String): String? {
        val body = input.uppercase().filter { it.isLetterOrDigit() }.removePrefix(PREFIX)
            .replace('O', '0').replace('I', '1').replace('L', '1')
        if (body.length != GROUPS * GROUP_LENGTH || body.any { it !in ALPHABET }) return null
        return (listOf(PREFIX) + body.chunked(GROUP_LENGTH)).joinToString("-")
    }

    /** "ULTR-••••-••••-••••-ZC3N", for showing which key is in use without printing all of it. */
    fun masked(key: String): String {
        val parts = key.split("-")
        if (parts.size != GROUPS + 1) return key
        return (listOf(parts.first()) + List(GROUPS - 1) { "••••" } + parts.last()).joinToString("-")
    }
}

/**
 * What the license Lambda says about a key on this device. [expiresAt] and [activatedAt] are in epoch seconds, 0 for a key
 * that never expires or has no activation yet. [name], [email] and [phone] are the buyer details entered in Ultronapp and
 * are empty when none were set; [durationDays] is the length of the plan, 0 for a lifetime key.
 */
data class LicenseStatus(
    val active: Boolean,
    val plan: String,
    val expiresAt: Long,
    val reason: String,
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val activatedAt: Long = 0,
    val durationDays: Int = 0
) {
    companion object {
        fun fromJson(json: JSONObject) = LicenseStatus(
            active = json.optBoolean("active"),
            plan = json.optString("plan"),
            expiresAt = json.optLong("expiresAt"),
            reason = json.optString("reason"),
            name = json.optString("name"),
            email = json.optString("email"),
            phone = json.optString("phone"),
            activatedAt = json.optLong("activatedAt"),
            durationDays = json.optInt("durationDays")
        )

        fun toJson(status: LicenseStatus): String = JSONObject()
            .put("active", status.active)
            .put("plan", status.plan)
            .put("expiresAt", status.expiresAt)
            .put("reason", status.reason)
            .put("name", status.name)
            .put("email", status.email)
            .put("phone", status.phone)
            .put("activatedAt", status.activatedAt)
            .put("durationDays", status.durationDays)
            .toString()
    }
}

/**
 * The activated key and the last answer the server gave for it, so the app opens offline. Kept in plain preferences:
 * the key only works on the device it is bound to, and the server re-checks it whenever it can.
 */
class LicenseStore(context: Context) {
    private val prefs = context.getSharedPreferences("license", Context.MODE_PRIVATE)

    val key: String? get() = prefs.getString(KEY_KEY, null)?.takeIf { it.isNotBlank() }

    fun lastStatus(): LicenseStatus? =
        prefs.getString(KEY_STATUS, null)?.let { runCatching { LicenseStatus.fromJson(JSONObject(it)) }.getOrNull() }

    /** Seconds since 1970 of the last answer the server gave, 0 when there is none. */
    val checkedAt: Long get() = prefs.getLong(KEY_CHECKED_AT, 0)

    fun save(key: String, status: LicenseStatus, nowSeconds: Long) {
        prefs.edit()
            .putString(KEY_KEY, key)
            .putString(KEY_STATUS, LicenseStatus.toJson(status))
            .putLong(KEY_CHECKED_AT, nowSeconds)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_KEY = "key"
        const val KEY_STATUS = "status"
        const val KEY_CHECKED_AT = "checked_at"
    }
}

/** How long a key keeps working without reaching the server, so a revoked or moved key stops on a phone kept offline. */
const val LICENSE_OFFLINE_GRACE_SECONDS = 7 * 86_400L

/** True when the cached [status] still allows use at [nowSeconds] without asking the server, for example while offline. */
fun isUsableOffline(status: LicenseStatus?, checkedAt: Long, nowSeconds: Long): Boolean =
    status != null && status.active &&
        (status.expiresAt == 0L || status.expiresAt > nowSeconds) &&
        // A clock set back before the last check gives a negative age; that must not stretch the grace.
        nowSeconds - checkedAt in 0 until LICENSE_OFFLINE_GRACE_SECONDS

/**
 * Whether the assistant may run right now. The access screen opens it once the key is confirmed; the voice session code
 * checks it before it connects to Gemini, so a locked app never opens a session in the background.
 */
object AccessGate {
    @Volatile var unlocked: Boolean = false
}
