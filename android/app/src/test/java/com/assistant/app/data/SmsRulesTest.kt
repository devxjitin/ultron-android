package com.assistant.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SmsRulesTest {
    @Before fun reset() = SmsRules.resetForTest()

    @Test fun keepsDigitsAndLeadingPlus() {
        assertEquals("+919876543210", SmsRules.cleanNumber("+91 98765-43210"))
        assertEquals("9876543210", SmsRules.cleanNumber(" (98765) 43210 "))
    }

    @Test fun refusesShortCodesAndNonNumbers() {
        assertNull(SmsRules.cleanNumber("56767"))
        assertNull(SmsRules.cleanNumber("mummy"))
        assertNull(SmsRules.cleanNumber("1234567890123456"))
    }

    @Test fun allowsTenAnHourThenRefuses() {
        repeat(SmsRules.MAX_PER_HOUR) { assertTrue(SmsRules.allowSend(1_000L + it)) }
        assertFalse(SmsRules.allowSend(2_000L))
        // An hour after the first one, a slot is free again.
        assertTrue(SmsRules.allowSend(1_000L + 3_600_000L))
    }

    @Test fun refundFreesTheSlot() {
        repeat(SmsRules.MAX_PER_HOUR) { assertTrue(SmsRules.allowSend(5_000L)) }
        SmsRules.refund()
        assertTrue(SmsRules.allowSend(5_000L))
    }
}
