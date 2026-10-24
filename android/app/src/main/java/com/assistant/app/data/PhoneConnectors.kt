package com.assistant.app.data

/** Where a connector's tools may be used: voice only (phone control), or voice and the typed chat. */
enum class Surface { VOICE, BOTH }

/**
 * The phone-side connectors and the device tools each one owns. A tool belongs to exactly one connector, so
 * the connector decides whether the tool is offered to the chat. Voice-only connectors are the automation ones.
 */
class PhoneConnector(val id: String, val surface: Surface, val tools: List<DeviceTool>)

object PhoneConnectors {
    val all: List<PhoneConnector> = listOf(
        PhoneConnector("device_info", Surface.BOTH, listOf(
            DeviceTool.TIME, DeviceTool.BATTERY, DeviceTool.DEVICE_INFO, DeviceTool.STORAGE_MEMORY, DeviceTool.NETWORK,
            DeviceTool.WIFI_INFO, DeviceTool.BLUETOOTH, DeviceTool.STEPS, DeviceTool.LIGHT, DeviceTool.SPEED_TEST
        )),
        PhoneConnector("device_controls", Surface.BOTH, listOf(
            DeviceTool.FLASHLIGHT, DeviceTool.BRIGHTNESS, DeviceTool.GET_BRIGHTNESS, DeviceTool.VOLUME, DeviceTool.GET_VOLUME,
            DeviceTool.GET_RINGER, DeviceTool.SET_RINGER, DeviceTool.VIBRATE, DeviceTool.SET_DND, DeviceTool.MEDIA_CONTROL,
            DeviceTool.SCREEN_TIMEOUT, DeviceTool.AUTO_ROTATE
        )),
        PhoneConnector("alarms_notes", Surface.BOTH, listOf(
            DeviceTool.SET_ALARM, DeviceTool.SET_TIMER, DeviceTool.SCHEDULE_REMINDER, DeviceTool.LIST_REMINDERS, DeviceTool.CANCEL_REMINDER,
            DeviceTool.CREATE_NOTE, DeviceTool.SEARCH_NOTES, DeviceTool.DELETE_NOTE
        )),
        PhoneConnector("calendar", Surface.BOTH, listOf(DeviceTool.UPCOMING_EVENTS, DeviceTool.CALENDAR_CONFLICTS, DeviceTool.CREATE_EVENT, DeviceTool.CANCEL_EVENT)),
        PhoneConnector("contacts", Surface.BOTH, listOf(DeviceTool.SEARCH_CONTACT, DeviceTool.CONTACT_BIRTHDAY)),
        PhoneConnector("location", Surface.BOTH, listOf(DeviceTool.LOCATION, DeviceTool.DIRECTIONS)),
        PhoneConnector("photos", Surface.BOTH, listOf(DeviceTool.RECENT_PHOTOS)),
        PhoneConnector("notifications", Surface.BOTH, listOf(DeviceTool.READ_NOTIFICATIONS)),
        PhoneConnector("app_usage", Surface.BOTH, listOf(DeviceTool.MOST_USED_APPS, DeviceTool.APP_USAGE)),
        PhoneConnector("duckduckgo", Surface.BOTH, listOf(DeviceTool.WEB_SEARCH)),
        PhoneConnector("web_reader", Surface.BOTH, listOf(DeviceTool.FETCH_WEBPAGE)),
        PhoneConnector("weather", Surface.BOTH, listOf(DeviceTool.GET_WEATHER)),
        PhoneConnector("youtube", Surface.BOTH, listOf(
            DeviceTool.YOUTUBE_SEARCH, DeviceTool.YOUTUBE_INFO, DeviceTool.YOUTUBE_TRANSCRIPT, DeviceTool.YOUTUBE_CHANNEL,
            DeviceTool.YOUTUBE_DOWNLOAD_OPTIONS, DeviceTool.DOWNLOAD_YOUTUBE
        )),
        // Calls and messages go out right away, so the chat may use them as well as voice.
        PhoneConnector("calls", Surface.BOTH, listOf(DeviceTool.DIAL, DeviceTool.CALL_CONTACT, DeviceTool.CREATE_CONTACT)),
        PhoneConnector("messages", Surface.BOTH, listOf(DeviceTool.SEND_SMS, DeviceTool.SEND_SMS_CONTACT)),
        // Automation: these act on the phone itself, so they stay in the voice assistant.
        PhoneConnector("apps_settings", Surface.VOICE, listOf(
            DeviceTool.OPEN_APP, DeviceTool.OPEN_CAMERA, DeviceTool.OPEN_APP_SETTINGS, DeviceTool.OPEN_SETTINGS,
            DeviceTool.SET_WALLPAPER, DeviceTool.GET_CLIPBOARD, DeviceTool.SET_CLIPBOARD, DeviceTool.SHOW_NOTIFICATION
        )),
        PhoneConnector("navigation", Surface.VOICE, listOf(
            DeviceTool.PRESS_BACK, DeviceTool.RECENT_APPS, DeviceTool.PREVIOUS_APP, DeviceTool.NOTIFICATION_SHADE,
            DeviceTool.QUICK_SETTINGS, DeviceTool.SCROLL, DeviceTool.LOCK_SCREEN, DeviceTool.SCREENSHOT, DeviceTool.HOME_SCREEN
        )),
        PhoneConnector("screen", Surface.VOICE, listOf(
            DeviceTool.READ_SCREEN, DeviceTool.CURRENT_APP, DeviceTool.CLICK_TEXT, DeviceTool.CLICK_ITEM,
            DeviceTool.TAP_POSITION, DeviceTool.TYPE_TEXT, DeviceTool.PRESS_ENTER, DeviceTool.WAIT_FOR_TEXT
        ))
    )

    private val byId = all.associateBy { it.id }

    fun tools(id: String): List<DeviceTool> = byId.getValue(id).tools

    fun surface(id: String): Surface = byId.getValue(id).surface

    /** Tools the typed chat may use: everything owned by a connector that is not voice-only. */
    val chatTools: Set<DeviceTool> = all.filter { it.surface == Surface.BOTH }.flatMap { it.tools }.toSet()
}
