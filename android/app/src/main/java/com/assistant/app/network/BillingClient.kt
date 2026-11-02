package com.assistant.app.network

import com.assistant.app.data.BillingPlan
import com.assistant.app.data.SubscriptionStatus
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A subscription created on Razorpay, ready to open in Checkout. */
data class PendingSubscription(val subscriptionId: String, val keyId: String)

/** The billing routes of the Ultron API (mcp/billing). Every call blocks, so run it off the main thread. */
class BillingClient {
    private val http = HttpClients.shared.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    fun status(): SubscriptionStatus = SubscriptionStatus.fromJson(call("GET", "/v1/billing/status", null))

    fun subscribe(plan: BillingPlan): PendingSubscription {
        val json = call("POST", "/v1/billing/subscribe", JSONObject().put("plan", plan.id))
        return PendingSubscription(json.getString("subscriptionId"), json.getString("keyId"))
    }

    /** Sends the Checkout result for the server to check against Razorpay; returns the status it stored. */
    fun verify(paymentId: String, subscriptionId: String, signature: String): SubscriptionStatus =
        SubscriptionStatus.fromJson(call("POST", "/v1/billing/verify", JSONObject()
            .put("paymentId", paymentId)
            .put("subscriptionId", subscriptionId)
            .put("signature", signature)))

    private fun call(method: String, path: String, body: JSONObject?): JSONObject {
        val auth = ApiClient.authHeader() ?: throw IOException("Not signed in")
        val request = Request.Builder()
            .url(ApiClient.url(path))
            .header("Authorization", auth)
            .method(method, body?.toString()?.toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
            if (!response.isSuccessful) throw BillingException(response.code, json.optString("error", "HTTP ${response.code}"))
            return json
        }
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}

class BillingException(val code: Int, message: String) : IOException(message)
