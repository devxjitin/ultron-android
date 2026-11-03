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
        assertTrue(isUsableOffline(active.copy(expiresAt = 0), checkedAt = 1_000, nowSeconds = 2_000))
        assertFalse(isUsableOffline(active.copy(active = false), checkedAt = 1_000, nowSeconds = 2_000))
        assertFalse(isUsableOffline(null, checkedAt = 1_000, nowSeconds = 2_000))
    }

    @Test fun `status survives the cache round trip`() {
        val back = LicenseStatus.fromJson(JSONObject(LicenseStatus.toJson(active.copy(reason = "moved"))))
        assertEquals(active.copy(reason = "moved"), back)
    }
}
