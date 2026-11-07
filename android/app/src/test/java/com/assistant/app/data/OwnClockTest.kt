package com.assistant.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnClockTest {
    @Test fun timeLeftIsSpokenInPlainWords() {
        assertEquals("1 second", OwnClock.spoken(1))
        assertEquals("5 minutes", OwnClock.spoken(300))
        assertEquals("1 hour 1 minute 5 seconds", OwnClock.spoken(3665))
        assertEquals("0 seconds", OwnClock.spoken(0))
    }

    @Test fun timerAndAlarmToolsAreInTheAlarmsConnector() {
        val tools = ConnectorRegistry.get("alarms").tools
        listOf(
            DeviceTool.SET_ALARM, DeviceTool.LIST_ALARMS, DeviceTool.CANCEL_ALARM, DeviceTool.MODIFY_ALARM,
            DeviceTool.SET_TIMER, DeviceTool.LIST_TIMERS, DeviceTool.CANCEL_TIMER
        ).forEach { assertTrue("$it", it in tools) }
    }
}
