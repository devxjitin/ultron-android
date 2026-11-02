package com.assistant.app.data

import android.content.Context
import org.json.JSONObject

/** The plans sold in the app. [id] matches the billing Lambda; prices are in rupees and fixed with the Razorpay plans. */
enum class BillingPlan(val id: String, val label: String, val priceRupees: Int, val months: Int) {
    MONTHLY("monthly", "1 month", 199, 1),
    HALF_YEARLY("halfyearly", "6 months", 999, 6),
    YEARLY("yearly", "1 year", 1799, 12);

    /** What the plan costs per month, rounded, to compare the longer plans with the monthly one. */
    val perMonthRupees: Int get() = (priceRupees + months / 2) / months

    companion object {
        fun fromId(id: String): BillingPlan? = entries.firstOrNull { it.id == id }
    }
}

/** What the billing Lambda says about the signed-in user. [currentEnd] is in epoch seconds, 0 when unknown. */
data class SubscriptionStatus(val active: Boolean, val plan: String, val status: String, val currentEnd: Long) {
    val billingPlan: BillingPlan? get() = BillingPlan.fromId(plan)

    companion object {
        val NONE = SubscriptionStatus(active = false, plan = "", status = "none", currentEnd = 0)

        fun fromJson(json: JSONObject) = SubscriptionStatus(
            active = json.optBoolean("active"),
            plan = json.optString("plan"),
            status = json.optString("status"),
            currentEnd = json.optLong("currentEnd")
        )

        fun toJson(status: SubscriptionStatus): String = JSONObject()
            .put("active", status.active)
            .put("plan", status.plan)
            .put("status", status.status)
            .put("currentEnd", status.currentEnd)
            .toString()
    }
}

/**
 * The last status the server confirmed, per Google account, so a paid user can open the app offline. The cached answer
 * only counts until the paid cycle ends; after that the app needs the server again.
 */
class SubscriptionCache(context: Context) {
    private val prefs = context.getSharedPreferences("subscription", Context.MODE_PRIVATE)

    fun save(uid: String, status: SubscriptionStatus) {
        prefs.edit().putString(KEY_UID, uid).putString(KEY_STATUS, SubscriptionStatus.toJson(status)).apply()
    }

    fun load(uid: String): SubscriptionStatus? {
        if (prefs.getString(KEY_UID, null) != uid) return null
        return prefs.getString(KEY_STATUS, null)?.let { runCatching { SubscriptionStatus.fromJson(JSONObject(it)) }.getOrNull() }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_UID = "uid"
        const val KEY_STATUS = "status"
    }
}

/** True when [status] still allows use at [nowSeconds] without asking the server, for example while offline. */
fun isUsableOffline(status: SubscriptionStatus?, nowSeconds: Long): Boolean =
    status != null && status.active && status.currentEnd > nowSeconds

/**
 * Whether the assistant may run right now. The access screen opens it once the subscription is confirmed; the voice
 * session code checks it before it connects to Gemini, so a locked app never opens a session in the background.
 */
object AccessGate {
    @Volatile var unlocked: Boolean = false
}
