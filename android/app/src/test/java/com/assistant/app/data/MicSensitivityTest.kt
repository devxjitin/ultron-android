package com.assistant.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MicSensitivityTest {
    @Test
    fun `missing or unrecognized preference uses medium`() {
        assertEquals(MicSensitivity.MEDIUM, MicSensitivity.fromStoredValue(null))
        assertEquals(MicSensitivity.MEDIUM, MicSensitivity.fromStoredValue("unknown"))
    }

    @Test
    fun `saved choices round trip`() {
        MicSensitivity.values().forEach { choice ->
            assertEquals(choice, MicSensitivity.fromStoredValue(choice.name))
        }
    }
}
