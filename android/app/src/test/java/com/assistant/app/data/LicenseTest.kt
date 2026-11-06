package com.assistant.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseTest {
    private val key = "ULTR-7K2Q-M9XD-4H8P-ZC3N"
    private val active = LicenseStatus(active = true, plan = "yearly", expiresAt = 10_000_000, reason = "")

    @Test fun `typed keys are normalized the same way as on the server`() {
        assertEquals(key, LicenseKey.normalize(" ultr 7k2q-m9xd 4h8p zc3n "))
        assertEquals(key, LicenseKey.normalize("7K2QM9XD4H8PZC3N"))
        assertEquals("ULTR-0000-1111-0000-1111", LicenseKey.normalize("ULTR-OOOO-IIII-oooo-llll"))
        assertNull(LicenseKey.normalize("ULTR-1234"))
        assertNull(LicenseKey.normalize("ULTR-UUUU-1111-2222-3333"))
    }

    @Test fun `masked key shows only the last group`() {
        assertEquals("ULTR-••••-••••-••••-ZC3N", LicenseKey.masked(key))
    }

    @Test fun `cached status works offline within the grace and before expiry`() {
        assertTrue(isUsableOffline(active, checkedAt = 1_000, nowSeconds = 2_000))
        assertFalse(isUsableOffline(active, checkedAt = 1_000, nowSeconds = 1_000 + LICENSE_OFFLINE_GRACE_SECONDS))
        assertFalse(isUsableOffline(active, checkedAt = 9_999_000, nowSeconds = 10_000_000))
        // A clock set back before the last check does not stretch the grace.
        assertFalse(isUsableOffline(active.copy(expiresAt = 0), checkedAt = 5_000, nowSeconds = 1_000))
        assertTrue(isUsableOffline(active.copy(expiresAt = 0), checkedAt = 1_000, nowSeconds = 1_000))
        assertTrue(isUsableOffline(active.copy(expiresAt = 0), checkedAt = 1_000, nowSeconds = 2_000))
        assertFalse(isUsableOffline(active.copy(active = false), checkedAt = 1_000, nowSeconds = 2_000))
        assertFalse(isUsableOffline(null, checkedAt = 1_000, nowSeconds = 2_000))
    }

    @Test fun `buyer details come from the server answer and survive the cache`() {
        val json = JSONObject()
            .put("active", true).put("plan", "yearly").put("expiresAt", 10_000_000).put("reason", "")
            .put("name", "Asha Verma").put("email", "asha@example.com").put("phone", "+91 98765 43210")
            .put("activatedAt", 5_000_000).put("durationDays", 365)
        val status = LicenseStatus.fromJson(json)
        assertEquals("Asha Verma", status.name)
        assertEquals("asha@example.com", status.email)
        assertEquals("+91 98765 43210", status.phone)
        assertEquals(5_000_000L, status.activatedAt)
        assertEquals(365, status.durationDays)
        assertEquals(status, LicenseStatus.fromJson(JSONObject(LicenseStatus.toJson(status))))
    }

    @Test fun `an older server answer without buyer details still parses`() {
        val status = LicenseStatus.fromJson(JSONObject("""{"active":true,"plan":"yearly","expiresAt":10000000,"reason":""}"""))
        assertEquals(active, status)
        assertEquals("", status.name)
    }

    @Test fun `status survives the cache round trip`() {
        val back = LicenseStatus.fromJson(JSONObject(LicenseStatus.toJson(active.copy(reason = "moved"))))
        assertEquals(active.copy(reason = "moved"), back)
    }
}
