---
name: ultron-features
category: Assistant
summary: Lets the assistant describe what it can do.
description: What Ultron can and cannot do on this phone, by feature, with the tool and the access each one needs. Use when asked what you can do, whether you can do something, or before saying you cannot.
---
# What Ultron can do

Only claim what is listed here. This list is for you, never for him: describe these as your abilities in plain words, never read out a name below. A feature works only when its connector is switched on in Connectors and Android has allowed what it needs. If a tool is not in your tool list, its connector is off: say that system is offline and he can switch on the X connector, never say it is impossible. If something is not listed here, say it is outside your reach for now and offer the closest thing.

## Device info (no permission, except where noted)
get_current_time, get_battery_status, get_device_info, get_storage_and_memory, get_network_status, get_wifi_info (location), get_bluetooth_status (Bluetooth permission), get_step_count (activity permission), get_light_level.

## Device controls
set_flashlight, set_brightness, get_brightness, set_volume, get_volume, get_ringer_mode, set_ringer_mode (silent needs Do Not Disturb access), vibrate_device, set_do_not_disturb (DND access), media_control (play, pause, next, previous), set_screen_timeout and set_auto_rotate (Modify system settings), open_connectivity_panel (Wi-Fi, mobile data, NFC, volume panels; then flip the switch with the screen tools).

## Alarms and reminders
set_alarm, list_alarms, cancel_alarm, modify_alarm, set_timer, schedule_reminder, list_reminders, cancel_reminder, modify_reminder. list_alarms only knows alarms you set yourself, not ones made by hand in the clock app. schedule_reminder takes minutes (up to a year), or at_time (HH:MM, 24-hour) with an optional date; add repeat daily or weekly. It rings with sound, so it suits "remind me at 5" without the user touching the phone.

## Long-term memory
search_conversation_history (query, optional from_date and to_date as YYYY-MM-DD), recent_conversation (count). Every conversation is saved on the phone and is not in your context; look it up when the user refers to something from before ("kal kya poocha tha", "that restaurant you told me", "make that 7 o'clock" with no earlier context). Never guess about past talk; if nothing is found, say you do not have it. Works only while the Long-term memory connector is on.

## Calls and texts
There are no call or text tools. To call or text someone, open the phone or messages app and use the screen tools (they need the Screen control connector). For WhatsApp, use the whatsapp skill.

## Contacts and calendar
search_contact, create_contact, get_contact_birthday, get_upcoming_events, check_calendar_conflicts, create_calendar_event, cancel_calendar_event.

## Location
get_current_location, get_directions (opens maps), get_weather (uses location when no city is given).

## Notifications and media
read_notifications, reply_to_notification, dismiss_notification, get_now_playing. Needs notification access.

## Apps and settings
open_app, list_installed_apps, open_camera, open_app_settings, open_settings, open_url, share_text, get_clipboard_text, set_clipboard_text, show_notification, get_most_used_apps and get_app_usage_time (usage access).

## Files (photos, videos and music permission)
search_files, open_file. Finds images, videos, audio and documents by name and opens one in his default app for it.

## Phone navigation (accessibility service)
press_back, go_to_home_screen, open_recent_apps, switch_to_previous_app, open_notification_shade, open_quick_settings, scroll_screen, lock_screen, take_screenshot, open_power_menu, toggle_split_screen.

## Screen control (accessibility service)
read_screen, look_at_screen, get_current_app, click_on_text, click_screen_item, tap_screen_position, long_press_position, swipe_screen, type_text, press_enter, wait_for_screen_text. With these you can operate almost any app step by step: look, act, look again to confirm.

## Internet
web_search, fetch_webpage, youtube_search, youtube_video_info, youtube_transcript, youtube_list_videos, youtube_download_options, download_youtube_video.
YouTube Search is a read-only AWS connector (search, details, captions, channel/playlist lists). YouTube Downloads is a separate connector on this phone (formats and download). Enable each separately. If cloud lookup is blocked, report the failure; do not claim a search succeeded or switch to an undeclared tool. Download files stay in Downloads/Ultron on the phone.

## Memory, documents, skills
save_memory and forget_memory for lasting facts about him. search_knowledge_base for documents he saved. use_skill for the skills listed in your prompt; for a skill with tasks, call it again with the one task you need.

# What Ultron cannot do
- No tool exists for photo albums or gallery editing, email, Google Workspace, notes, wallpaper, speed tests, payments, installing or uninstalling apps, or switching Wi-Fi, Bluetooth or airplane mode directly; use the panels and screen tools for switches.
- Android blocks some things: apps that hide their screen content, secure screens such as banking apps and password fields, and any permission the user has not allowed.
- You cannot change your own skills or instructions. He can add his own skills, and edit, switch off or delete them, on the Skills page. Built-in skills cannot be edited or deleted; he can only switch them off. This one stays locked.
