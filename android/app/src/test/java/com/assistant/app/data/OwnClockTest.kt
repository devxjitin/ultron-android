package com.assistant.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnClockTest {
    @Test fun timeLeftIsSpokenInPlainWords() {
        assertEquals("1 second", OwnClock.spoken(1))
        assertEquals("5 minutes", OwnClock.spoken(300))
        assertEquals("1 hour 1 minute 5 seconds", OwnClock.spoken(3665))
        assertEquals("0 seconds", OwnClock.spoken(0))
    }

    @Test fun onlyAlarmsAndTimersCountAsClockItems() {
        val notify = DeviceTool.SHOW_NOTIFICATION.functionName
        assertEquals("alarm", AutomationEngine.clockKind(notify, """{"title":"Alarm","kind":"alarm","label":"gym"}"""))
        assertEquals("timer", AutomationEngine.clockKind(notify, """{"title":"Timer","kind":"timer"}"""))
        // A reminder is saved the same way but has no kind, so the reminder tools keep it and the clock tools ignore it.
        assertNull(AutomationEngine.clockKind(notify, """{"title":"Reminder","message":"call mom","urgent":true}"""))
        assertNull(AutomationEngine.clockKind(notify, "not json"))
        assertNull(AutomationEngine.clockKind(DeviceTool.FLASHLIGHT.functionName, """{"kind":"alarm"}"""))
        assertTrue(AutomationEngine.isClock(ScheduledAction(7, 0L, notify, """{"kind":"alarm"}""", 0)))
    }

    @Test fun timerAndAlarmToolsAreInTheAlarmsConnector() {
        val tools = ConnectorRegistry.get("alarms").tools
        listOf(
            DeviceTool.SET_ALARM, DeviceTool.LIST_ALARMS, DeviceTool.CANCEL_ALARM, DeviceTool.MODIFY_ALARM,
            DeviceTool.SET_TIMER, DeviceTool.LIST_TIMERS, DeviceTool.CANCEL_TIMER
        ).forEach { assertTrue("$it", it in tools) }
    }
}
