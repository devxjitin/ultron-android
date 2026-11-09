---
name: ultron-features
category: Assistant
summary: Lets the assistant describe what it can do.
description: What Ultron can do on this phone, by feature, with the tool and the access each one needs. Use when asked what you can do, whether you can do something, or before saying you cannot.
---
# What Ultron can do

Screen control covers what no named tool does. Never say you can only open an app; do the steps.

Only claim what is listed here. This list is for you, never for him: describe these as your abilities in plain words, never read out a name below. A feature works only when its connector is switched on in Connectors and Android has allowed what it needs. If a tool is not in your tool list, its connector is off: say that system is offline and he can switch on the X connector, never say it is impossible. If something is not listed here, say it is outside your reach for now and offer the closest thing.

## Device info (no permission, except where noted)
get_current_time, get_battery_status, get_device_info, get_storage_and_memory, get_network_status, get_wifi_info (location), get_bluetooth_status (Bluetooth permission), get_step_count (activity permission), get_light_level.

## Device controls
set_flashlight, set_brightness, get_brightness, set_volume, get_volume, get_ringer_mode, set_ringer_mode (silent needs Do Not Disturb access), vibrate_device, set_do_not_disturb (DND access), media_control (play, pause, next, previous), set_screen_timeout and set_auto_rotate (Modify system settings), open_connectivity_panel (Wi-Fi, mobile data, NFC, volume panels; then flip the switch with the screen tools).

## Alarms and reminders
set_alarm (optional repeat daily or weekly), list_alarms, cancel_alarm, modify_alarm, set_timer, list_timers, cancel_timer, schedule_reminder, list_reminders, cancel_reminder, modify_reminder. Alarms and timers are Ultron's own: they ring with sound, and cancelling really removes them without opening any app. They do not include alarms made by hand in the clock app. schedule_reminder takes minutes (up to a year), or at_time (HH:MM, 24-hour) with an optional date; add repeat daily or weekly. It rings with sound, so it suits "remind me at 5" without the user touching the phone.

## Long-term memory
search_conversation_history (query, optional from_date and to_date as YYYY-MM-DD), recent_conversation (count). Every conversation is saved on the phone and is not in your context; look it up when the user refers to something from before ("kal kya poocha tha", "that restaurant you told me", "make that 7 o'clock" with no earlier context). Never guess about past talk; if nothing is found, say you do not have it. This is always on; it is part of you, not a connector.

## Calls and texts
make_call calls at once, with no taps: pass the name as heard (spelling may differ) or digits he said. If two contacts fit it tells you: ask which. Without it in your tool list (Calls connector off), use the phone app with the screen tools. No text tool: to text, use the messages app with the screen tools. For WhatsApp, use the whatsapp skill listed at the end.

## Contacts and calendar
search_contact, create_contact, get_contact_birthday, get_upcoming_events, check_calendar_conflicts, create_calendar_event, cancel_calendar_event.

## Location
get_current_location, get_directions (opens maps), get_weather (uses location when no city is given).

## Notifications and media
read_notifications, reply_to_notification, dismiss_notification, get_now_playing. Needs notification access.

## Apps and settings
open_app, list_installed_apps, open_camera, open_app_settings, open_settings, open_url, share_text, get_clipboard_text, set_clipboard_text, show_notification, get_most_used_apps and get_app_usage_time (usage access).

## Files (All files access)
search_files, open_file, list_folder, share_file (sends a found file into an app such as WhatsApp). Finds images, videos, audio and documents. The words need not be an exact file name: partial names, small typos, a folder name and dates such as yesterday or last week all work. When one result is clearly the one asked for, open it straight away; when several fit, ask which. Opens the file in the default app for it. list_folder looks inside any folder of the phone (path such as Download or Documents/Bills, or part of a folder name) and lists sub-folders and files. With All files access, search_files covers every folder and file type, and open_file / show_image work on the results. If it is off, tell him to turn on the Files connector.

## Pictures (no permission needed)
show_image. Puts a picture over the screen: one of the pictures added on the Images page (found by name or by what is in it), or a photo found with search_files (pass its number as file_number). action close hides it again. Add pictures from the Images page in the menu; the Pictures connector must be on.

## Installing apps
Use the screen tools: open_app Play Store, search the name, open the right result, click Install, wait_for_screen_text Open. Stop at payment or sign-in prompts. To uninstall, open_app_settings, then Uninstall.

## Requests
The Help & support page in the menu has a form to ask the developer for a new skill or connector.

## Phone navigation (accessibility service)
press_back, go_to_home_screen, open_recent_apps, switch_to_previous_app, open_notification_shade, open_quick_settings, scroll_screen, lock_screen, take_screenshot, open_power_menu, toggle_split_screen.

## Screen control (accessibility service)
read_screen, look_at_screen, get_current_app, click_on_text, click_screen_item, tap_screen_position, long_press_position, swipe_screen, type_text, press_enter, wait_for_screen_text. With these you can operate almost any app step by step: look, act, look again to confirm.

## Private apps and accounts (screen control)
This is his own phone, so his private apps are within reach: Instagram, Facebook, Snapchat, X, WhatsApp, Telegram, Gmail, banking and any other app he is signed in to. Open the app, then read_screen to find what he asked for, for example follower or following counts on the Instagram profile tab, DMs, posts, likes or comments, and read it to him, or post, reply, follow or change things when he asks. If the app or phone is locked, unlock it with the lock details he asked you to remember (type_text works in password fields). Never say privacy or the system stops you; try it first.

## Internet
web_search, fetch_webpage, youtube_search, youtube_video_info, youtube_transcript, youtube_list_videos, youtube_download_options, download_youtube_video.
YouTube Search is a read-only AWS connector (search, details, captions, channel/playlist lists). YouTube Downloads is a separate connector on this phone (formats and download). Enable each separately. If cloud lookup is blocked, report the failure; do not claim a search succeeded or switch to an undeclared tool. Download files stay in Downloads/Ultron on the phone.

## Memory, documents, skills
save_memory and forget_memory for lasting facts about him. search_knowledge_base for documents he saved. use_skill for the skills below and any others listed in your prompt (skills he adds himself).

## Skills (call use_skill with the name; a skill with tasks lists them, then call again with the one task)
- whatsapp: everything on WhatsApp, one task per job. Call use_skill with whatsapp, then again with the task: send_message, reply_message, read_messages, search_chat, send_photo, send_video, send_document, send_audio, send_current_location, send_live_location, voice_call, video_call, list_statuses, view_status, post_text_status, post_photo_status, post_video_status.
