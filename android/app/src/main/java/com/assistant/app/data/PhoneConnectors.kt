package com.assistant.app.data

/** The phone-side connectors and the device tools each one owns. A tool belongs to exactly one connector. */
class PhoneConnector(val id: String, val tools: List<DeviceTool>)

object PhoneConnectors {
    val all: List<PhoneConnector> = listOf(
        PhoneConnector("device_info", listOf(
            DeviceTool.TIME, DeviceTool.BATTERY, DeviceTool.DEVICE_INFO, DeviceTool.STORAGE_MEMORY, DeviceTool.NETWORK,
            DeviceTool.WIFI_INFO, DeviceTool.BLUETOOTH, DeviceTool.STEPS, DeviceTool.LIGHT, DeviceTool.SPEED_TEST
        )),
        PhoneConnector("device_controls", listOf(
            DeviceTool.FLASHLIGHT, DeviceTool.BRIGHTNESS, DeviceTool.GET_BRIGHTNESS, DeviceTool.VOLUME, DeviceTool.GET_VOLUME,
            DeviceTool.GET_RINGER, DeviceTool.SET_RINGER, DeviceTool.VIBRATE, DeviceTool.SET_DND, DeviceTool.MEDIA_CONTROL,
            DeviceTool.SCREEN_TIMEOUT, DeviceTool.AUTO_ROTATE
        )),
        PhoneConnector("alarms_notes", listOf(
            DeviceTool.SET_ALARM, DeviceTool.CANCEL_ALARM, DeviceTool.SET_TIMER, DeviceTool.SCHEDULE_REMINDER, DeviceTool.LIST_REMINDERS, DeviceTool.CANCEL_REMINDER,
            DeviceTool.CREATE_NOTE, DeviceTool.SEARCH_NOTES, DeviceTool.DELETE_NOTE
        )),
        PhoneConnector("calendar", listOf(DeviceTool.UPCOMING_EVENTS, DeviceTool.CALENDAR_CONFLICTS, DeviceTool.CREATE_EVENT, DeviceTool.CANCEL_EVENT)),
        PhoneConnector("contacts", listOf(DeviceTool.SEARCH_CONTACT, DeviceTool.CONTACT_BIRTHDAY)),
        PhoneConnector("location", listOf(DeviceTool.LOCATION, DeviceTool.DIRECTIONS)),
        PhoneConnector("photos", listOf(DeviceTool.RECENT_PHOTOS)),
        PhoneConnector("notifications", listOf(DeviceTool.READ_NOTIFICATIONS)),
        PhoneConnector("app_usage", listOf(DeviceTool.MOST_USED_APPS, DeviceTool.APP_USAGE)),
        PhoneConnector("duckduckgo", listOf(DeviceTool.WEB_SEARCH)),
        PhoneConnector("web_reader", listOf(DeviceTool.FETCH_WEBPAGE)),
        PhoneConnector("weather", listOf(DeviceTool.GET_WEATHER)),
        PhoneConnector("youtube", listOf(
            DeviceTool.YOUTUBE_SEARCH, DeviceTool.YOUTUBE_INFO, DeviceTool.YOUTUBE_TRANSCRIPT, DeviceTool.YOUTUBE_CHANNEL,
            DeviceTool.YOUTUBE_DOWNLOAD_OPTIONS, DeviceTool.DOWNLOAD_YOUTUBE
        )),
        PhoneConnector("calls", listOf(DeviceTool.DIAL, DeviceTool.CALL_CONTACT, DeviceTool.CREATE_CONTACT)),
        PhoneConnector("messages", listOf(DeviceTool.SEND_SMS, DeviceTool.SEND_SMS_CONTACT)),
        PhoneConnector("apps_settings", listOf(
            DeviceTool.OPEN_APP, DeviceTool.OPEN_CAMERA, DeviceTool.OPEN_APP_SETTINGS, DeviceTool.OPEN_SETTINGS,
            DeviceTool.SET_WALLPAPER, DeviceTool.GET_CLIPBOARD, DeviceTool.SET_CLIPBOARD, DeviceTool.SHOW_NOTIFICATION
        )),
        PhoneConnector("navigation", listOf(
            DeviceTool.PRESS_BACK, DeviceTool.RECENT_APPS, DeviceTool.PREVIOUS_APP, DeviceTool.NOTIFICATION_SHADE,
            DeviceTool.QUICK_SETTINGS, DeviceTool.SCROLL, DeviceTool.LOCK_SCREEN, DeviceTool.SCREENSHOT, DeviceTool.HOME_SCREEN
        )),
        PhoneConnector("screen", listOf(
            DeviceTool.READ_SCREEN, DeviceTool.LOOK_AT_SCREEN, DeviceTool.CURRENT_APP, DeviceTool.CLICK_TEXT, DeviceTool.CLICK_ITEM,
            DeviceTool.TAP_POSITION, DeviceTool.TYPE_TEXT, DeviceTool.PRESS_ENTER, DeviceTool.WAIT_FOR_TEXT
        ))
    )

    private val byId = all.associateBy { it.id }

    fun tools(id: String): List<DeviceTool> = byId.getValue(id).tools
}
