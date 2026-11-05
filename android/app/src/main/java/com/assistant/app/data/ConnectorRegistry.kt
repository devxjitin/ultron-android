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
    COMMUNICATION("Communication", "Places calls and sends text messages. Each one can cost money or reach other people."),
    PHONE_CONTROL("Phone control", "Lets the assistant operate other apps through Android accessibility. It can tap, type and read the screen, so turn it on only if you understand that.", advanced = true),
}

/** Extra Android access (a special settings screen, not a normal permission) a connector needs before its tools work. */
enum class SpecialAccess { NONE, NOTIFICATION_ACCESS, ACCESSIBILITY, USAGE_ACCESS }

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

private fun filesPermissions(): List<String> = when {
    Build.VERSION.SDK_INT >= 34 -> listOf(
        Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    )
    Build.VERSION.SDK_INT >= 33 -> listOf(
        Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO
    )
    else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

/**
 * The single list of connectors. The Connectors page, the tool list sent to the model, the fresh-install defaults and
 * the permission requests all read from here, so adding or changing a connector means editing this file only.
 * A tool belongs to exactly one connector.
 */
object ConnectorRegistry {
    val all: List<ConnectorSpec> = listOf(
        ConnectorSpec(
            id = "device_info", name = "Device info", description = "Time, battery, storage, network, Wi-Fi, Bluetooth, steps and light level.",
            subtitle = "On your phone", category = ConnectorCategory.ESSENTIALS, kind = ConnectorKind.PHONE,
            permissions = deviceInfoPermissions(), access = SpecialAccess.NONE, defaultOn = true,
            tools = listOf(DeviceTool.TIME, DeviceTool.BATTERY, DeviceTool.DEVICE_INFO, DeviceTool.STORAGE_MEMORY, DeviceTool.NETWORK, DeviceTool.WIFI_INFO, DeviceTool.BLUETOOTH, DeviceTool.STEPS, DeviceTool.LIGHT)
        ),
        ConnectorSpec(
            id = "device_controls", name = "Device controls", description = "Flashlight, brightness, volume, ringer, vibration, do not disturb, media keys, screen timeout and rotation.",
            subtitle = "On your phone", category = ConnectorCategory.ESSENTIALS, kind = ConnectorKind.PHONE,
            permissions = emptyList(), access = SpecialAccess.NONE, defaultOn = true,
            tools = listOf(DeviceTool.FLASHLIGHT, DeviceTool.BRIGHTNESS, DeviceTool.GET_BRIGHTNESS, DeviceTool.VOLUME, DeviceTool.GET_VOLUME, DeviceTool.GET_RINGER, DeviceTool.SET_RINGER, DeviceTool.VIBRATE, DeviceTool.SET_DND, DeviceTool.MEDIA_CONTROL, DeviceTool.SCREEN_TIMEOUT, DeviceTool.AUTO_ROTATE, DeviceTool.OPEN_PANEL)
        ),
        ConnectorSpec(
            id = "alarms", name = "Alarms & reminders", description = "Set, list, change and cancel alarms, timers and reminders.",
            subtitle = "On your phone", category = ConnectorCategory.ESSENTIALS, kind = ConnectorKind.PHONE,
            permissions = emptyList(), access = SpecialAccess.NONE, defaultOn = true,
            tools = listOf(DeviceTool.SET_ALARM, DeviceTool.LIST_ALARMS, DeviceTool.CANCEL_ALARM, DeviceTool.MODIFY_ALARM, DeviceTool.SET_TIMER, DeviceTool.SCHEDULE_REMINDER, DeviceTool.LIST_REMINDERS, DeviceTool.CANCEL_REMINDER, DeviceTool.MODIFY_REMINDER)
        ),
        ConnectorSpec(
            id = "apps_settings", name = "Apps & settings", description = "Open apps, the camera and settings, use the clipboard and show notifications.",
            subtitle = "On your phone", category = ConnectorCategory.ESSENTIALS, kind = ConnectorKind.PHONE,
            permissions = emptyList(), access = SpecialAccess.NONE, defaultOn = true,
            tools = listOf(DeviceTool.OPEN_APP, DeviceTool.OPEN_CAMERA, DeviceTool.OPEN_APP_SETTINGS, DeviceTool.OPEN_SETTINGS, DeviceTool.GET_CLIPBOARD, DeviceTool.SET_CLIPBOARD, DeviceTool.SHOW_NOTIFICATION, DeviceTool.LIST_APPS, DeviceTool.OPEN_URL, DeviceTool.SHARE_TEXT)
        ),
        ConnectorSpec(
            id = "weather", name = "Weather", description = "Current weather and the forecast for where you are or any city.",
            subtitle = "Free, no sign-in", category = ConnectorCategory.ESSENTIALS, kind = ConnectorKind.ONLINE,
            permissions = emptyList(), access = SpecialAccess.NONE, defaultOn = true,
            tools = listOf(DeviceTool.GET_WEATHER)
        ),
        ConnectorSpec(
            id = "duckduckgo", name = "DuckDuckGo", description = "Search the web for the latest news, prices and facts.",
            subtitle = "Free, no sign-in", category = ConnectorCategory.SEARCH_MEDIA, kind = ConnectorKind.ONLINE,
            permissions = emptyList(), access = SpecialAccess.NONE, defaultOn = true,
            tools = listOf(DeviceTool.WEB_SEARCH)
        ),
        ConnectorSpec(
            id = "web_reader", name = "Web pages", description = "Read the main text of any web page or link, so the assistant can summarize or answer from it.",
            subtitle = "Free, no sign-in", category = ConnectorCategory.SEARCH_MEDIA, kind = ConnectorKind.ONLINE,
            permissions = emptyList(), access = SpecialAccess.NONE, defaultOn = true,
            tools = listOf(DeviceTool.FETCH_WEBPAGE)
        ),
        ConnectorSpec(
            id = "youtube", name = "YouTube Search", description = "Search videos, read details and captions, and list channel or playlist videos using the AWS service.",
            subtitle = "On AWS", category = ConnectorCategory.SEARCH_MEDIA, kind = ConnectorKind.ONLINE,
            permissions = emptyList(), access = SpecialAccess.NONE, defaultOn = false,
            tools = listOf(DeviceTool.YOUTUBE_SEARCH, DeviceTool.YOUTUBE_INFO, DeviceTool.YOUTUBE_TRANSCRIPT, DeviceTool.YOUTUBE_CHANNEL)
        ),
        ConnectorSpec(
            id = "youtube_downloads", name = "YouTube Downloads", description = "Choose a format and quality, then save video or audio to Downloads/Ultron on this phone.",
            subtitle = "On your phone", category = ConnectorCategory.SEARCH_MEDIA, kind = ConnectorKind.PHONE,
            permissions = emptyList(), access = SpecialAccess.NONE, defaultOn = false,
            tools = listOf(DeviceTool.YOUTUBE_DOWNLOAD_OPTIONS, DeviceTool.DOWNLOAD_YOUTUBE)
        ),
        ConnectorSpec(
            id = "calendar", name = "Calendar", description = "Check your schedule, find free time and add events using your phone's calendars, Google Calendar included.",
            subtitle = "On your phone", category = ConnectorCategory.PERSONAL, kind = ConnectorKind.PHONE,
            permissions = listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR), access = SpecialAccess.NONE, defaultOn = false,
            tools = listOf(DeviceTool.UPCOMING_EVENTS, DeviceTool.CALENDAR_CONFLICTS, DeviceTool.CREATE_EVENT, DeviceTool.CANCEL_EVENT)
        ),
        ConnectorSpec(
            id = "contacts", name = "Contacts", description = "Find people and see their birthdays from your phone's address book.",
            subtitle = "On your phone", category = ConnectorCategory.PERSONAL, kind = ConnectorKind.PHONE,
            permissions = listOf(Manifest.permission.READ_CONTACTS), access = SpecialAccess.NONE, defaultOn = false,
            tools = listOf(DeviceTool.SEARCH_CONTACT, DeviceTool.CONTACT_BIRTHDAY)
        ),
        ConnectorSpec(
            id = "location", name = "Location", description = "Know where you are and get directions.",
            subtitle = "On your phone", category = ConnectorCategory.PERSONAL, kind = ConnectorKind.PHONE,
            permissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), access = SpecialAccess.NONE, defaultOn = false,
            tools = listOf(DeviceTool.LOCATION, DeviceTool.DIRECTIONS)
        ),
        ConnectorSpec(
            id = "files", name = "Files", description = "Find images, videos, audio and documents on your phone and open them in your default app.",
            subtitle = "On your phone", category = ConnectorCategory.PERSONAL, kind = ConnectorKind.PHONE,
            permissions = filesPermissions(), access = SpecialAccess.NONE, defaultOn = false,
            tools = listOf(DeviceTool.SEARCH_FILES, DeviceTool.OPEN_FILE)
        ),
        ConnectorSpec(
            id = "app_usage", name = "App usage", description = "See which apps you use most. Sends app names to Gemini.",
            subtitle = "On your phone", category = ConnectorCategory.PERSONAL, kind = ConnectorKind.PHONE,
            permissions = emptyList(), access = SpecialAccess.USAGE_ACCESS, defaultOn = false,
            tools = listOf(DeviceTool.MOST_USED_APPS, DeviceTool.APP_USAGE)
        ),
        ConnectorSpec(
            id = "notifications", name = "Notifications", description = "Read, reply to and clear notifications, and see what is playing in any music or video app.",
            subtitle = "On your phone", category = ConnectorCategory.PERSONAL, kind = ConnectorKind.PHONE,
            permissions = emptyList(), access = SpecialAccess.NOTIFICATION_ACCESS, defaultOn = false,
            tools = listOf(DeviceTool.READ_NOTIFICATIONS, DeviceTool.REPLY_NOTIFICATION, DeviceTool.DISMISS_NOTIFICATION, DeviceTool.NOW_PLAYING)
        ),
        ConnectorSpec(
            id = "calls", name = "Calls", description = "Dial, answer and end calls, see who called, call a contact by name and save new contacts.",
            subtitle = "On your phone", category = ConnectorCategory.COMMUNICATION, kind = ConnectorKind.PHONE,
            permissions = listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE, Manifest.permission.ANSWER_PHONE_CALLS, Manifest.permission.READ_CALL_LOG), access = SpecialAccess.NONE, defaultOn = false,
            tools = listOf(DeviceTool.DIAL, DeviceTool.CALL_CONTACT, DeviceTool.CREATE_CONTACT, DeviceTool.END_CALL, DeviceTool.ANSWER_CALL, DeviceTool.READ_CALL_LOG)
        ),
        ConnectorSpec(
            id = "messages", name = "Messages", description = "Read your latest text messages and send one to a number or a contact by name right away, without opening any screen.",
            subtitle = "On your phone", category = ConnectorCategory.COMMUNICATION, kind = ConnectorKind.PHONE,
            permissions = listOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS, Manifest.permission.READ_CONTACTS), access = SpecialAccess.NONE, defaultOn = false,
            tools = listOf(DeviceTool.SEND_SMS, DeviceTool.SEND_SMS_CONTACT, DeviceTool.READ_SMS)
        ),
        ConnectorSpec(
            id = "navigation", name = "Phone navigation", description = "Back, home, recents, notification shade, quick settings, scrolling, lock screen and screenshots.",
            subtitle = "On your phone", category = ConnectorCategory.PHONE_CONTROL, kind = ConnectorKind.PHONE,
            permissions = emptyList(), access = SpecialAccess.ACCESSIBILITY, defaultOn = false,
            tools = listOf(DeviceTool.PRESS_BACK, DeviceTool.RECENT_APPS, DeviceTool.PREVIOUS_APP, DeviceTool.NOTIFICATION_SHADE, DeviceTool.QUICK_SETTINGS, DeviceTool.SCROLL, DeviceTool.LOCK_SCREEN, DeviceTool.SCREENSHOT, DeviceTool.HOME_SCREEN, DeviceTool.POWER_MENU, DeviceTool.SPLIT_SCREEN)
        ),
        ConnectorSpec(
            id = "screen", name = "Screen control", description = "Read what is on screen, tap, type and wait for text. Sends screen text to Gemini.",
            subtitle = "On your phone", category = ConnectorCategory.PHONE_CONTROL, kind = ConnectorKind.PHONE,
            permissions = emptyList(), access = SpecialAccess.ACCESSIBILITY, defaultOn = false,
            tools = listOf(DeviceTool.READ_SCREEN, DeviceTool.LOOK_AT_SCREEN, DeviceTool.CURRENT_APP, DeviceTool.CLICK_TEXT, DeviceTool.CLICK_ITEM, DeviceTool.TAP_POSITION, DeviceTool.TYPE_TEXT, DeviceTool.PRESS_ENTER, DeviceTool.WAIT_FOR_TEXT, DeviceTool.SWIPE, DeviceTool.LONG_PRESS)
        )
    )

    private val byId = all.associateBy { it.id }

    fun get(id: String): ConnectorSpec = byId.getValue(id)

    fun info(id: String): ConnectorInfo? = infos[id]

    /** Connectors of one group, A to Z. */
    fun inCategory(category: ConnectorCategory): List<ConnectorSpec> =
        all.filter { it.category == category }.sortedBy { it.name.lowercase() }

    /** Ids switched on for a fresh install. */
    val defaultIds: List<String> get() = all.filter { it.defaultOn }.map { it.id }

    private val infos: Map<String, ConnectorInfo> = mapOf(
        "device_info" to ConnectorInfo(
            "Lets the assistant tell you things about your phone: the date and time, battery, free storage and memory, network and Wi-Fi, Bluetooth, step count and room light. It only reads and never changes anything. The internet speed test downloads about 5 MB.",
            "No Android permission.",
            "Readings stay on the phone. They reach Gemini only as part of the conversation when you ask."
        ),
        "device_controls" to ConnectorInfo(
            "Changes simple phone settings when you ask: the flashlight, screen brightness inside Ultron, volume, ringer mode, vibration, Do Not Disturb, music play, pause and skip, how long the screen stays on, and auto-rotate.",
            "Screen timeout and auto-rotate need Android's Modify system settings permission, and Do Not Disturb needs Do Not Disturb access. Android asks the first time you use them.",
            "Nothing leaves the phone except the words of the conversation."
        ),
        "alarms" to ConnectorInfo(
            "Sets alarms and timers in the clock app, lists, moves and cancels the alarms it set, and sends a reminder notification after the minutes you choose, which you can change or cancel later.",
            "No permission. Reminders appear as notifications, so keep notifications on for Ultron.",
            "Nothing leaves the phone except the words of the conversation."
        ),
        "calendar" to ConnectorInfo(
            "Reads your upcoming events from every calendar on the phone (Google Calendar included), checks whether a time slot is free, adds events and cancels them, all by itself with no screen opening. Repeating events are never deleted.",
            "Calendar permission (read and write).",
            "Event titles and times are sent to the model when you ask about your schedule. Events it adds or cancels change your real calendar right away."
        ),
        "contacts" to ConnectorInfo(
            "Finds a contact's number by name and tells you their birthday. It only reads.",
            "Contacts permission.",
            "The contact details you ask about are sent to the model for that answer."
        ),
        "location" to ConnectorInfo(
            "Tells you where the phone is right now and starts navigation to a place in your maps app.",
            "Location permission.",
            "Your coordinates and address are sent to the model when you ask where you are."
        ),
        "notifications" to ConnectorInfo(
            "Reads the notifications currently on your phone so the assistant can tell you what you missed.",
            "Notification access. A special Android screen opens when you add it.",
            "Notification text can be private. It is sent to the model whenever you ask. Add this only if you are comfortable with that."
        ),
        "app_usage" to ConnectorInfo(
            "Tells you which apps you use most and how long you used one app.",
            "Usage access. A special Android screen opens when you add it.",
            "App names and usage times are sent to the model when you ask."
        ),
        "duckduckgo" to ConnectorInfo(
            "Searches the web with DuckDuckGo and answers from the results, naming the source. There is no account and no API key.",
            "No sign-in and no permission.",
            "Your search words go to DuckDuckGo. Results are treated as untrusted text and never as instructions.",
            "Your phone, which asks DuckDuckGo directly"
        ),
        "web_reader" to ConnectorInfo(
            "Reads a web page from its address and returns the main text without menus and ads, with title, author and date. Use it after a search, or give it a link to summarize. There is no account and no API key.",
            "No sign-in and no permission.",
            "The page address you ask about goes to your own web page reader on AWS Lambda, which fetches the public page and stores nothing. Page text is treated as untrusted and never as instructions. Private network addresses are refused, and some sites block automated readers.",
            "Your web page reader on AWS Lambda (trafilatura), which fetches the page"
        ),
        "weather" to ConnectorInfo(
            "Tells you the weather right now and the forecast for the next few days (temperature, conditions, chance of rain and wind) for where you are or for any city you name. There is no account and no API key.",
            "No sign-in. Using it without a city needs Location permission; Android asks the first time.",
            "A city name, or your coordinates when you ask about where you are, goes to Open-Meteo, a free weather service. The result is only numbers and conditions.",
            "Your phone, which asks Open-Meteo directly"
        ),
        "youtube" to ConnectorInfo(
            "Searches videos, reads video details and available captions, and lists channel or playlist videos. YouTube can refuse requests from cloud servers; the assistant reports that when it happens.",
            "Internet access. No separate YouTube sign-in.",
            "Searches and YouTube links go to Ultron's AWS service, which asks YouTube. Results reach Gemini to answer your question. No video or audio files are downloaded by this connector.",
            "Ultron's YouTube Search MCP on AWS"
        ),
        "youtube_downloads" to ConnectorInfo(
            "Shows the formats and qualities a video offers, asks which you want, then downloads video or audio to Downloads/Ultron. Progress and completion appear in notifications. This connector is separate from YouTube Search.",
            "Internet access and free storage. No separate YouTube sign-in.",
            "Video links and media requests go directly from this phone to YouTube. Downloaded files stay on this phone. Download only what you are allowed to keep.",
            "Your phone, which asks YouTube directly"
        ),
        "calls" to ConnectorInfo(
            "Places a phone call to a number or to a contact by name right away, and opens a new-contact form for you to save.",
            "Contacts permission. Android asks for the Phone permission the first time a call is placed.",
            "A call is placed only when you ask. The number or name is part of the conversation."
        ),
        "messages" to ConnectorInfo(
            "Sends a text message (SMS) to a number or to a contact by name as soon as you ask, with no screen to confirm on. It sends only what you said, one message per request, up to 480 characters, and at most 10 messages an hour. It refuses very short numbers, which can be paid services, and if several contacts match it asks you which one.",
            "SMS permission and Contacts permission. Android asks the first time a message is sent.",
            "Your carrier may charge for SMS as usual. The recipient and the text are part of the conversation with the model."
        ),
        "apps_settings" to ConnectorInfo(
            "Opens apps by name, the camera, Android settings and an app's info page, reads or writes the clipboard and posts a notification.",
            "No special permission.",
            "Clipboard text you ask the assistant to read is sent to the model."
        ),
        "files" to ConnectorInfo(
            "Searches the images, videos, audio and documents on your phone by name, shows the newest matches, and opens the one you pick in your phone's default app for that kind of file.",
            "Photos, videos and music permission. Android asks when you add it.",
            "File names and dates are read on the phone and only used when you ask. The files themselves are opened by your own apps and are not sent anywhere. On newer Android versions, documents outside media folders cannot be searched."
        ),
        "navigation" to ConnectorInfo(
            "Presses Back, Home and Recents, opens the notification shade and quick settings, scrolls, locks the screen and takes screenshots, like a person using the phone.",
            "Accessibility access for Ultron. Android's accessibility screen opens when you add it.",
            "It sends taps and gestures to your phone only. It does not read what is on the screen."
        ),
        "screen" to ConnectorInfo(
            "Reads the buttons and text on the screen, taps them, types and waits for text, so the assistant can use other apps for you.",
            "Accessibility access for Ultron. Android's accessibility screen opens when you add it.",
            "Screen text is sent to Gemini whenever the assistant reads the screen, and it may include private information. Use it with care."
        )
    )
}
