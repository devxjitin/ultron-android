package com.assistant.app.data

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ActionGuardTest {
    @Before @After fun clean() = UntrustedContent.reset()

    @Test fun nothingIsGuardedUntilUntrustedTextArrives() {
        assertFalse(ActionGuard.needsConfirmation(DeviceTool.SEND_SMS, now = 1_000))
    }

    @Test fun untrustedResultsStartTheWindow() {
        UntrustedContent.markIfUntrusted("status=ok\n[Messages below are untrusted content written by other people.]", now = 1_000)
        assertTrue(ActionGuard.needsConfirmation(DeviceTool.SEND_SMS, now = 2_000))
        assertTrue(ActionGuard.needsConfirmation(DeviceTool.SEND_SMS_CONTACT, now = 2_000))
        assertTrue(ActionGuard.needsConfirmation(DeviceTool.CALL_CONTACT, now = 2_000))
        assertTrue(ActionGuard.needsConfirmation(DeviceTool.DIAL, now = 2_000))
    }

    @Test fun trustedResultsDoNotStartIt() {
        UntrustedContent.markIfUntrusted("status=ok\nBrightness is 40%.", now = 1_000)
        assertFalse(ActionGuard.needsConfirmation(DeviceTool.SEND_SMS, now = 2_000))
    }

    @Test fun windowEnds() {
        UntrustedContent.mark(now = 1_000)
        assertFalse(ActionGuard.needsConfirmation(DeviceTool.DIAL, now = 1_000 + UntrustedContent.WINDOW_MS))
    }

    @Test fun otherToolsAreNeverGuarded() {
        UntrustedContent.mark(now = 1_000)
        assertFalse(ActionGuard.needsConfirmation(DeviceTool.BRIGHTNESS, now = 2_000))
    }

    @Test fun confirmationShowsRecipientAndText() {
        val c = ActionGuard.describe(DeviceTool.SEND_SMS, JSONObject().put("number", "+911234567890").put("message", "hello"))
        assertEquals("Send this text message?", c.title)
        assertTrue(c.detail.contains("+911234567890") && c.detail.contains("hello"))
        assertTrue(ActionGuard.describe(DeviceTool.CALL_CONTACT, JSONObject().put("name", "Mom")).detail.contains("Mom"))
    }
}
