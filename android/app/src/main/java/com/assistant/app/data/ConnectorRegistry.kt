package com.assistant.app.data

import android.Manifest
import android.os.Build

/**
 * The groups the Connectors page is split into, in the order they appear. A group tells the user how sensitive a
 * connector is; [advanced] groups carry a warning and are never switched on by default.
 */
enum class ConnectorCategory(val title: String, val summary: String, val advanced: Boolean = false) {
    ESSENTIALS("Essentials", "Everyday phone info and controls. Nothing here reads your personal data."),
    SEARCH_MEDIA("Search & media", "Web search, web pages and YouTube."),
    PERSONAL("Personal data", "Reads private data from your phone, such as contacts, calendar, location, files and notifications. What you ask about is sent to Gemini."),
    PHONE_CONTROL("Phone control", "Lets the assistant operate other apps through Android accessibility. It can tap, type and read the screen, so turn it on only if you understand that.", advanced = true),
}

/** Extra Android access (a special settings screen, not a normal permission) a connector needs before its tools work. */
enum class SpecialAccess { NONE, NOTIFICATION_ACCESS, ACCESSIBILITY, USAGE_ACCESS, ALL_FILES }

enum class ConnectorKind {
    /** Tools that run on this phone. */
    PHONE,
    /** An online service that needs no sign-in: connecting just switches its tools on. */
    ONLINE
}

/** The long-form facts shown on a connector's own page. */
class ConnectorInfo(val about: String, val needs: String, val privacy: String, val runsOn: String = "Your phone")

/** Everything the app knows about one connector, in one place. */
class ConnectorSpec(
    val id: String,
    val name: String,
    val description: String,
    val subtitle: String,
    val category: ConnectorCategory,
    val kind: ConnectorKind,
    /** Android runtime permissions that must be granted. */
    val permissions: List<String>,
    val access: SpecialAccess,
    /** Switched on for a fresh install. */
    val defaultOn: Boolean,
    val tools: List<DeviceTool>
)

private fun deviceInfoPermissions(): List<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
    if (Build.VERSION.SDK_INT >= 29) add(Manifest.permission.ACTIVITY_RECOGNITION)
}

/** Before Android 11 the ordinary storage permission already reaches every file; from 11 the special All files screen is used. */
private fun allFilesPermissions(): List<String> =
    if (Build.VERSION.SDK_INT >= 30) emptyList() else listOf(Manifest.permission.READ_EXTERNAL_STORAGE)

/**
 * The single list of connectors. The Connectors page, the tool list sent to the model, the fresh-install defaults and
 * the permission requests all read from here, so adding or changing a connector means editing this file only.
 * A tool belongs to exactly one connector.
 */
object ConnectorRegistry {
    val all: List<ConnectorSpec> = listOf(
        ConnectorSpec(
            id = "device_info", name = "Device info", description = "Battery, storage, network, time and more.",
            subtitle = "On your phone", category = ConnectorCategory.ESSENTIALS, kind = ConnectorKind.PHONE,
            permissions = deviceInfoPermissions(), access = SpecialAccess.NONE, defaultOn = true,
            tools = listOf(DeviceTool.TIME, DeviceTool.BATTERY, DeviceTool.DEVICE_INFO, DeviceTool.STORAGE_MEMORY, DeviceTool.NETWORK, DeviceTool.WIFI_INFO, DeviceTool.BLUETOOTH, DeviceTool.STEPS, DeviceTool.LIGHT)
        ),
        ConnectorSpec(
            id = "device_controls", name = "Device controls", description = "Flashlight, volume, brightness and phone modes.",
            subtitle = "On your phone", category = ConnectorCategory.ESSENTIALS, kind = ConnectorKind.PHONE,
            permissions = emptyList(), access = SpecialAccess.NONE, defaultOn = true,
            tools = listOf(DeviceTool.FLASHLIGHT, DeviceTool.BRIGHTNESS, DeviceTool.GET_BRIGHTNESS, DeviceTool.VOLUME, DeviceTool.GET_VOLUME, DeviceTool.GET_RINGER, DeviceTool.SET_RINGER, DeviceTool.VIBRATE, DeviceTool.SET_DND, DeviceTool.MEDIA_CONTROL, DeviceTool.SCREEN_TIMEOUT, DeviceTool.AUTO_ROTATE, DeviceTool.OPEN_PANEL)
        ),
        ConnectorSpec(
            id = "alarms", name = "Alarms & timers", description = "Set alarms and timers.",
            subtitle = "On your phone", category = ConnectorCategory.ESSENTIALS, kind = ConnectorKind.PHONE,
            permissions = emptyList(), access = SpecialAccess.NONE, defaultOn = true,
            tools = listOf(DeviceTool.SET_ALARM, DeviceTool.LIST_ALARMS, DeviceTool.CANCEL_ALARM, DeviceTool.MODIFY_ALARM, DeviceTool.SET_TIMER, DeviceTool.LIST_TIMERS, DeviceTool.CANCEL_TIMER)
        ),
        ConnectorSpec(
            id = "reminders", name = "Reminders", description = "Get reminded at a time, with a sound.",
            subtitle = "On your phone", category = ConnectorCategory.ESSENTIALS, kind = ConnectorKind.PHONE,
            permissions = emptyList(), access = SpecialAccess.NONE, defaultOn = true,
            tools = listOf(DeviceTool.SCHEDULE_REMINDER, DeviceTool.LIST_REMINDERS, DeviceTool.CANCEL_REMINDER, DeviceTool.MODIFY_REMINDER)
        ),
        ConnectorSpec(
            id = "apps_settings", name = "Apps & settings", description = "Open apps and settings, use the clipboard.",
            subtitle = "On your phone", category = ConnectorCategory.ESSENTIALS, kind = ConnectorKind.PHONE,
            permissions = emptyList(), access = SpecialAccess.NONE, defaultOn = true,
            tools = listOf(DeviceTool.OPEN_APP, DeviceTool.OPEN_CAMERA, DeviceTool.OPEN_APP_SETTINGS, DeviceTool.OPEN_SETTINGS, DeviceTool.GET_CLIPBOARD, DeviceTool.SET_CLIPBOARD, DeviceTool.SHOW_NOTIFICATION, DeviceTool.LIST_APPS, DeviceTool.OPEN_URL, DeviceTool.SHARE_TEXT)
        ),
        ConnectorSpec(
            id = "weather", name = "Weather", description = "Weather now and for the next few days.",
            subtitle = "Free, no sign-in", category = ConnectorCategory.ESSENTIALS, kind = ConnectorKind.ONLINE,
            permissions = emptyList(), access = SpecialAccess.NONE, defaultOn = true,
            tools = listOf(DeviceTool.GET_WEATHER)
        ),
        ConnectorSpec(
            id = "duckduckgo", name = "Web search", description = "Search the web.",
            subtitle = "No sign-in", category = ConnectorCategory.SEARCH_MEDIA, kind = ConnectorKind.ONLINE,
            permissions = emptyList(), access = SpecialAccess.NONE, defaultOn = true,
            tools = listOf(DeviceTool.WEB_SEARCH)
        ),
        ConnectorSpec(
            id = "web_reader", name = "Web pages", description = "Read any web page or link.",
            subtitle = "Free, no sign-in", category = ConnectorCategory.SEARCH_MEDIA, kind = ConnectorKind.ONLINE,
            permissions = emptyList(), access = SpecialAccess.NONE, defaultOn = true,
            tools = listOf(DeviceTool.FETCH_WEBPAGE)
        ),
        ConnectorSpec(
            id = "youtube", name = "YouTube Search", description = "Search YouTube videos and playlists.",
            subtitle = "On AWS", category = ConnectorCategory.SEARCH_MEDIA, kind = ConnectorKind.ONLINE,
            permissions = emptyList(), access = SpecialAccess.NONE, defaultOn = false,
            tools = listOf(DeviceTool.YOUTUBE_SEARCH, DeviceTool.YOUTUBE_INFO, DeviceTool.YOUTUBE_TRANSCRIPT, DeviceTool.YOUTUBE_CHANNEL)
        ),
        ConnectorSpec(
            id = "youtube_downloads", name = "YouTube Downloads", description = "Save YouTube videos or audio to your phone.",
            subtitle = "On your phone", category = ConnectorCategory.SEARCH_MEDIA, kind = ConnectorKind.PHONE,
            permissions = emptyList(), access = SpecialAccess.NONE, defaultOn = false,
            tools = listOf(DeviceTool.YOUTUBE_DOWNLOAD_OPTIONS, DeviceTool.DOWNLOAD_YOUTUBE)
        ),
        ConnectorSpec(
            id = "calendar", name = "Calendar", description = "Check your schedule and add events.",
            subtitle = "On your phone", category = ConnectorCategory.PERSONAL, kind = ConnectorKind.PHONE,
            permissions = listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR), access = SpecialAccess.NONE, defaultOn = false,
            tools = listOf(DeviceTool.UPCOMING_EVENTS, DeviceTool.CALENDAR_CONFLICTS, DeviceTool.CREATE_EVENT, DeviceTool.CANCEL_EVENT)
        ),
        ConnectorSpec(
            id = "contacts", name = "Contacts", description = "Find contacts, birthdays and save new contacts.",
            subtitle = "On your phone", category = ConnectorCategory.PERSONAL, kind = ConnectorKind.PHONE,
            permissions = listOf(Manifest.permission.READ_CONTACTS), access = SpecialAccess.NONE, defaultOn = false,
            tools = listOf(DeviceTool.SEARCH_CONTACT, DeviceTool.CONTACT_BIRTHDAY, DeviceTool.CREATE_CONTACT)
        ),
        ConnectorSpec(
            id = "calls", name = "Calls", description = "Call a contact by name.",
            subtitle = "On your phone", category = ConnectorCategory.PERSONAL, kind = ConnectorKind.PHONE,
            permissions = listOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS), access = SpecialAccess.NONE, defaultOn = false,
            tools = listOf(DeviceTool.MAKE_CALL)
        ),
        ConnectorSpec(
            id = "location", name = "Location", description = "Know where you are and get directions.",
            subtitle = "On your phone", category = ConnectorCategory.PERSONAL, kind = ConnectorKind.PHONE,
            permissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), access = SpecialAccess.NONE, defaultOn = false,
            tools = listOf(DeviceTool.LOCATION, DeviceTool.DIRECTIONS)
        ),
        ConnectorSpec(
            id = "files", name = "Files", description = "Browse folders, find any file and open it.",
            subtitle = "On your phone", category = ConnectorCategory.PERSONAL, kind = ConnectorKind.PHONE,
            permissions = allFilesPermissions(), access = SpecialAccess.ALL_FILES, defaultOn = false,
            tools = listOf(DeviceTool.SEARCH_FILES, DeviceTool.OPEN_FILE, DeviceTool.LIST_FOLDER, DeviceTool.SHARE_FILE)
        ),
        ConnectorSpec(
            id = "images", name = "Pictures", description = "Show pictures you saved in Ultron, or photos from your phone.",
            subtitle = "On your phone", category = ConnectorCategory.SEARCH_MEDIA, kind = ConnectorKind.PHONE,
            permissions = emptyList(), access = SpecialAccess.NONE, defaultOn = false,
            tools = listOf(DeviceTool.SHOW_IMAGE)
        ),
        ConnectorSpec(
            id = "app_usage", name = "App usage", description = "See which apps you use most.",
            subtitle = "On your phone", category = ConnectorCategory.PERSONAL, kind = ConnectorKind.PHONE,
            permissions = emptyList(), access = SpecialAccess.USAGE_ACCESS, defaultOn = false,
            tools = listOf(DeviceTool.MOST_USED_APPS, DeviceTool.APP_USAGE)
        ),
        ConnectorSpec(
            id = "notifications", name = "Notifications", description = "Read and reply to notifications.",
            subtitle = "On your phone", category = ConnectorCategory.PERSONAL, kind = ConnectorKind.PHONE,
            permissions = emptyList(), access = SpecialAccess.NOTIFICATION_ACCESS, defaultOn = false,
            tools = listOf(DeviceTool.READ_NOTIFICATIONS, DeviceTool.REPLY_NOTIFICATION, DeviceTool.DISMISS_NOTIFICATION, DeviceTool.NOW_PLAYING)
        ),
        ConnectorSpec(
            id = "navigation", name = "Phone navigation", description = "Go back, go home, scroll and take screenshots.",
            subtitle = "On your phone", category = ConnectorCategory.PHONE_CONTROL, kind = ConnectorKind.PHONE,
            permissions = emptyList(), access = SpecialAccess.ACCESSIBILITY, defaultOn = false,
            tools = listOf(DeviceTool.PRESS_BACK, DeviceTool.RECENT_APPS, DeviceTool.PREVIOUS_APP, DeviceTool.NOTIFICATION_SHADE, DeviceTool.QUICK_SETTINGS, DeviceTool.SCROLL, DeviceTool.LOCK_SCREEN, DeviceTool.SCREENSHOT, DeviceTool.HOME_SCREEN, DeviceTool.POWER_MENU, DeviceTool.SPLIT_SCREEN)
        ),
        ConnectorSpec(
            id = "screen", name = "Screen control", description = "Read the screen, tap and type.",
            subtitle = "On your phone", category = ConnectorCategory.PHONE_CONTROL, kind = ConnectorKind.PHONE,
            permissions = emptyList(), access = SpecialAccess.ACCESSIBILITY, defaultOn = false,
            tools = listOf(DeviceTool.READ_SCREEN, DeviceTool.LOOK_AT_SCREEN, DeviceTool.CURRENT_APP, DeviceTool.CLICK_TEXT, DeviceTool.CLICK_ITEM, DeviceTool.TAP_POSITION, DeviceTool.TYPE_TEXT, DeviceTool.PRESS_ENTER, DeviceTool.WAIT_FOR_TEXT, DeviceTool.SWIPE, DeviceTool.LONG_PRESS)
        )
    )

    private val byId = all.associateBy { it.id }

    fun get(id: String): ConnectorSpec = byId.getValue(id)

    fun info(id: String): ConnectorInfo? = infos[id]

    /** Ids switched on for a fresh install. */
    val defaultIds: List<String> get() = all.filter { it.defaultOn }.map { it.id }

    private val infos: Map<String, ConnectorInfo> = mapOf(
        "device_info" to ConnectorInfo(
            "Tells you the time, battery, storage, network, Bluetooth, steps and light. It only reads.",
            "Nothing to allow.",
            "Shared only when you ask."
        ),
        "device_controls" to ConnectorInfo(
            "Changes simple settings for you: flashlight, brightness, volume, ringer, Do Not Disturb, media keys and auto-rotate.",
            "Android asks the first time for settings and Do Not Disturb.",
            "Nothing leaves your phone."
        ),
        "alarms" to ConnectorInfo(
            "Ultron's own alarms and timers. They ring on the alarm volume until you stop or snooze them, and you can list, change and cancel them by voice. They do not use the clock app.",
            "Keep notifications on for Ultron. On Xiaomi, Oppo, Vivo and Realme phones also allow Autostart for Ultron, or the phone may stop it from ringing.",
            "Nothing leaves your phone."
        ),
        "reminders" to ConnectorInfo(
            "Ultron's own reminders: a message at a time or after a while, once or every day or week, with a sound and vibration.",
            "Keep notifications on for Ultron. On Xiaomi, Oppo, Vivo and Realme phones also allow Autostart for Ultron.",
            "Nothing leaves your phone."
        ),
        "calendar" to ConnectorInfo(
            "Checks your schedule, finds free time and adds or cancels events.",
            "Calendar access.",
            "Event names and times are shared when you ask. Changes happen in your real calendar."
        ),
        "contacts" to ConnectorInfo(
            "Finds a contact's number and birthday, and opens a form to save a new contact.",
            "Contacts access.",
            "Shared only when you ask."
        ),
        "calls" to ConnectorInfo(
            "Calls the contact you name straight away, even when it is spelled differently on your phone. If two contacts fit, it asks which.",
            "Phone and Contacts access.",
            "The call is placed on your phone and the name is matched on your phone. Nothing is sent except what you say to Gemini."
        ),
        "location" to ConnectorInfo(
            "Tells you where you are and starts directions in your maps app.",
            "Location access.",
            "Your location is shared when you ask where you are."
        ),
        "notifications" to ConnectorInfo(
            "Reads your notifications so the assistant can tell you what you missed.",
            "Notification access.",
            "Notifications can be private. They are shared whenever you ask."
        ),
        "app_usage" to ConnectorInfo(
            "Shows which apps you use most and for how long.",
            "Usage access.",
            "App names and times are shared when you ask."
        ),
        "duckduckgo" to ConnectorInfo(
            "Searches the web and answers from the results.",
            "Nothing to allow.",
            "Your search words go to Ultron's server, which asks search engines. If it is down, your phone asks DuckDuckGo itself.",
            "Ultron's server"
        ),
        "web_reader" to ConnectorInfo(
            "Reads a web page and gives you the main text.",
            "Nothing to allow.",
            "The link you ask about goes to Ultron's server. Nothing is stored.",
            "Ultron's server"
        ),
        "weather" to ConnectorInfo(
            "Tells you the weather now and for the next few days, for where you are or any city.",
            "Location access, only for where you are.",
            "A city name or your location goes to a free weather service.",
            "Your phone"
        ),
        "youtube" to ConnectorInfo(
            "Searches videos and shows details, captions and playlists. YouTube sometimes blocks this.",
            "Internet only.",
            "Your search goes to Ultron's server, which asks YouTube.",
            "Ultron's server"
        ),
        "youtube_downloads" to ConnectorInfo(
            "Lets you pick a quality, then saves the video or audio to Downloads/Ultron.",
            "Internet and free space.",
            "Files stay on your phone. Download only what you may keep.",
            "Your phone"
        ),
        "apps_settings" to ConnectorInfo(
            "Opens apps, the camera and settings, uses the clipboard and shows notifications.",
            "Nothing to allow.",
            "Clipboard text is shared only when you ask."
        ),
        "files" to ConnectorInfo(
            "Looks inside any folder on the phone, finds photos, videos, music, documents and any other file by name, and opens them in your own apps.",
            "All files access. A settings screen opens when you add it.",
            "Names and folders are read on your phone. File contents are not sent anywhere unless you ask Ultron to read a file aloud."
        ),
        "images" to ConnectorInfo(
            "Shows a picture on the screen when you ask, from the pictures you added on the Images page or from a photo found with Files.",
            "Nothing. Add pictures on the Images page.",
            "Pictures stay on your phone. They are not sent anywhere.",
        ),
        "navigation" to ConnectorInfo(
            "Goes back and home, opens recents, scrolls, locks the screen and takes screenshots.",
            "Accessibility access. A settings screen opens when you add it.",
            "It does not read the screen."
        ),
        "screen" to ConnectorInfo(
            "Reads the screen and taps and types for you, so the assistant can use other apps.",
            "Accessibility access. A settings screen opens when you add it.",
            "Screen text is shared with Gemini and can be private. Use with care."
        )
    )
}
