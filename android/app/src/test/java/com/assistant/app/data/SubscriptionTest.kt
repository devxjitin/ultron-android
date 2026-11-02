package com.assistant.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionTest {
    private val paid = SubscriptionStatus(active = true, plan = "yearly", status = "active", currentEnd = 1_000)

    @Test fun `plans match the prices sold on Razorpay`() {
        assertEquals(listOf(199, 999, 1799), BillingPlan.entries.map { it.priceRupees })
        assertEquals(listOf("monthly", "halfyearly", "yearly"), BillingPlan.entries.map { it.id })
        assertEquals(167, BillingPlan.HALF_YEARLY.perMonthRupees)
        assertEquals(150, BillingPlan.YEARLY.perMonthRupees)
    }

    @Test fun `cached status works offline only until the paid cycle ends`() {
        assertTrue(isUsableOffline(paid, nowSeconds = 999))
        assertFalse(isUsableOffline(paid, nowSeconds = 1_000))
        assertFalse(isUsableOffline(paid.copy(active = false), nowSeconds = 1))
        assertFalse(isUsableOffline(null, nowSeconds = 1))
    }

    @Test fun `status survives the cache round trip`() {
        assertEquals(paid, SubscriptionStatus.fromJson(JSONObject(SubscriptionStatus.toJson(paid))))
        assertEquals(BillingPlan.YEARLY, paid.billingPlan)
    }
}
