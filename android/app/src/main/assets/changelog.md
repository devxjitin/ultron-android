# Changelog

The newest version goes first. The first "## x.y.z" heading below is the app's version: the build, the update
notes and the About page all read it from here. A date after the version is optional.

## 1.25.0 - 2026-11-13

### Changed
- Alarms and timers are now fully Ultron's own and ring like a real alarm: the sound plays on the alarm volume until you stop it, a Stop and Snooze screen shows over the lock screen, and the phone wakes for them even in deep sleep. They no longer depend on the clock app or on notification sounds.
- Say "stop the alarm" or "stop the timer" while one is ringing and it goes quiet. Snooze rings again in 10 minutes. An alarm nobody answers stops after 5 minutes and leaves a note.

### Fixed
- Setting an alarm or timer while Ultron's notifications are off no longer says "set" and then stays silent. Ultron asks for notifications first.
- Reminders, alarms and timers no longer mix: the reminder list shows only reminders, and cancelling or changing a reminder can no longer remove an alarm or timer.
- The Alarms & timers connector page no longer says they are set in your clock app.

## 1.24.1 - 2026-11-13

### Changed
- Long voice sessions now tidy their memory earlier, at 60% of the model's context instead of 90%, so replies stay fast and accurate. The ring around the mic button turns amber and red earlier to match.
- The About page and Ultron's own answer to "who made you" now name Nandivex Technologies, with Chandra Prakash as founder, Jitin as co-founder and Sonia as co-builder.

## 1.24.0 - 2026-11-13

### Added
- Five optional checks in Settings, all off by default, that use Gemini's Decisions API with your own key: smarter document search (picks the passages that really answer you), tidy memory notes (removes older notes a new one repeats or replaces; you can restore them), check screen actions (stops a tap or typing step that does not match what you asked), check the screen after each step (Android 11 or newer), and match skills to requests.
- Each check's hint says what it sends to Gemini. Screen checks are skipped for password fields and for apps that block screenshots.

## 1.23.8 - 2026-11-10

### Changed
- The voice wave now reacts clearly to your voice and to the assistant's: it rises quickly with every word and settles back smoothly. Quiet speech already lifts it, and the assistant's wave is timed to what you hear instead of running ahead of it.

## 1.23.7 - 2026-11-10

### Fixed
- Admin dashboard: the main button keeps readable white text on red in dark mode, and the notice toast stands out in both themes.

## 1.23.6 - 2026-11-10

### Fixed
- Toasts now tell success, information, warning and error apart: green for success, blue-grey for information, and a dark icon on the amber warning so it can be read.
- Light theme: errors use a plum shade that no longer looks like the red brand colour, the home background stays warm instead of turning blue-grey, and the voice ribbon is stronger on the pale page.
- Dark theme: default Material buttons, checkboxes and progress bars use the deeper red so white on them is readable.
- Switches show a clear thumb and outline when off, on every page.
- Cards, small items and dialogs share the same corner sizes, and section captions share one style.

## 1.23.5 - 2026-11-10

### Fixed
- Adding a picture or uploading a skill file no longer freezes or crashes the app when the file is very large or slow to open. Pictures over 25 MB and skill files over 256 KB are refused up front.

## 1.23.4 - 2026-11-09

### Changed
- A running voice session stays quietly in the background when you open Settings, Skills or any other page. The status bar with Mute and Stop is gone from those pages; Stop it from the notification or on Home.

## 1.23.3 - 2026-11-09

### Fixed
- Opening Settings, Skills or any other page no longer ends a running voice session. A slim bar under the top bar shows its state with Mute and Stop.
- Sending a file on WhatsApp now presses Send for you and only says "sent" once the preview has closed. If Send did not go through, it tells you instead of claiming success.
- Tapping a button by its text now prefers the real button over a heading or hint that only contains the word.

## 1.23.2 - 2026-11-09

### Changed
- WhatsApp is now one group with 17 small skills, one job each: send a message, photo, video, document, audio, current or live location; voice and video calls; read and reply; search a chat; see who posted a status, view one, and post a text, photo or video status.
- The assistant's own ability list now names every WhatsApp task, so it finds the right one when you ask.

## 1.23.1 - 2026-11-09

### Changed
- WhatsApp skills are now six separate skills under one WhatsApp heading on the Skills page, each with its own switch, instead of one skill with six tasks inside.
- The assistant's own ability list now names every skill, so it finds the right one when you ask.

## 1.23.0 - 2026-11-09

### Added
- After an update, Ultron opens by itself and shows a card with what changed.
- While a voice session runs, a ring around the mic button fills up to show how much of the model's 65k context is used. It turns amber, then red, as it nears full.

## 1.22.1 - 2026-11-09

### Changed
- WhatsApp: calls, photos, videos, documents, location, live location, replies and statuses are now separate skills, so Ultron picks the exact one you ask for.

## 1.22.0 - 2026-11-09

### Added
- WhatsApp: Ultron can now tell you who posted a status, show you their status, post a photo or text to your status, and search inside a chat for something like an address, a link or a file.

## 1.21.0 - 2026-11-09

### Added
- WhatsApp: Ultron can now read your new messages aloud and reply to them, start WhatsApp voice and video calls, and send a photo, video, document or your location to someone.
- A found photo or file can be sent straight into an app such as WhatsApp, opening the right chat when the person is in your contacts.

## 1.20.1 - 2026-11-09

### Improved
- WhatsApp messages are now faster and safer: when the person is in your contacts, Ultron opens their chat straight from the number instead of searching inside WhatsApp. People who are only in WhatsApp are still found through its search, and Ultron tells you if WhatsApp is not installed.

## 1.20.0 - 2026-11-09

### Added
- A new Calls connector: say who to call and Ultron dials them at once, with no taps. It is off until you add it in Connectors.

### Fixed
- Contacts are now found even when the name is spelled differently from how it was heard, such as Raahul for Rahul, Mummy for Mom, or a Hindi spelling. If two contacts fit, Ultron asks which one.
- Ultron no longer says it can only open the Play Store: it now searches and installs apps through the screen when Screen control is on.

## 1.19.12 - 2026-11-09

### Changed
- Every new voice session now starts fresh. Earlier conversations are no longer carried into it, although Ultron can still look them up if you ask about something from before.

## 1.19.11 - 2026-11-09

### Fixed
- The voice ribbon now keeps moving after you start Ultron from the lock screen, unlock the phone and open the app.

## 1.19.10 - 2026-11-09

### Improved
- Behind-the-scenes cleanup of how a voice session is opened, kept running, reconnected and ended, so future changes are safer. Nothing looks or works differently.

## 1.19.9 - 2026-11-09

### Improved
- Behind-the-scenes cleanup of the timers and the speech detection used during a voice session, so future changes are safer. Nothing looks or works differently.

## 1.19.8 - 2026-11-09

### Improved
- Behind-the-scenes cleanup of how the assistant's tools (alarms, memory, search and so on) are started and answered, so future changes are safer. Nothing looks or works differently.

## 1.19.7 - 2026-11-09

### Improved
- Behind-the-scenes cleanup of how Knowledge documents are added, edited, searched and kept up to date, so future changes are safer. Nothing looks or works differently.

## 1.19.6 - 2026-11-08

### Improved
- Behind-the-scenes cleanup of how the app takes and gives back the speaker and microphone, so future changes are safer. Nothing looks or works differently.

## 1.19.5 - 2026-11-08

### Improved
- Behind-the-scenes cleanup of how the app checks for and installs updates, so future changes are safer. Nothing looks or works differently.

## 1.19.4 - 2026-11-08

### Improved
- The app is now prepared for faster opening and smoother screens right after an update. The first start after the update may feel the same; the phone finishes the preparation in the background, so the gain shows from the next start.

## 1.19.3 - 2026-11-08

### Improved
- Behind-the-scenes cleanup of the main screen start-up so future changes are safer. Nothing looks or works differently.

## 1.19.2 - 2026-11-08

### Improved
- The old copy of your Knowledge documents, kept as a safety net since 1.19.0, is now removed from the phone. This frees some space and makes the app a little lighter. Your documents and search work as before.

## 1.19.1 - 2026-11-08

### Improved
- Updates now reach your phone sooner after a change is made: the update is built while it is being checked instead of afterwards. Nothing in the app looks or works differently.

## 1.19.0 - 2026-11-08

### Improved
- Your Knowledge documents are now stored in one place on the phone instead of two, so adding, editing and deleting a document is quicker and the app opens a little lighter.
- On the first start after this update your existing documents are moved over automatically. Nothing for you to do, and your documents and search work as before.

## 1.18.6 - 2026-11-08

### Improved
- Behind-the-scenes cleanup of the main screen so future changes are safer. Nothing looks or works differently.

## 1.18.5 - 2026-11-08

### Improved
- Adding or re-indexing a big document in Knowledge is much faster: the text is prepared in batches instead of one piece at a time.

## 1.18.4 - 2026-11-08

### Improved
- Knowledge answers come back a little faster: Ultron now prepares your question while it loads your documents.
- When the server asks for a fresh connection mid-answer, Ultron finishes its sentence first instead of cutting off.
- Ultron opens its ready-to-talk connection only after the app has stayed open for a moment, so fewer unused connections are made.

## 1.18.3 - 2026-11-08

### Fixed
- The close button on a picture Ultron shows is no longer hidden behind the top bar.
- The voice ribbon on the home screen now stands still when no conversation is running, which saves battery.
- Messages at the bottom sit lower on pages that have no mic button.
- Ultron now warns you one minute before a conversation reaches the 15-minute limit.
- Adding or removing a connector now shows a short confirmation.
- Removing an API key no longer pauses the screen for a moment.

## 1.18.2 - 2026-11-08

### Fixed
- Ultron no longer keeps refusing private apps such as Instagram because it refused once before. Old privacy refusals are left out of a new session, and it now opens the app and reads the screen for things like follower counts.
- Ultron can type into password fields when you ask, so it can sign in to your own apps.

## 1.18.1 - 2026-11-08

### Changed
- Ultron now acts with your full authority on your own phone, in every personality. It no longer holds back from private apps and accounts such as Instagram, and it can unlock the phone or an app with the lock details you ask it to remember.
- Lock details, logins and card numbers are saved only when you ask Ultron to remember them. Text it reads on screen, in messages or on the web never counts as an order from you.

## 1.18.0 - 2026-11-08

### Removed
- The Schedule feature is gone: the Schedule page in the menu, scheduling a task by voice, and the background runs with their notifications. Saved schedules are cleared the first time the updated app opens. Alarms, timers and reminders are not affected.

## 1.17.0 - 2026-11-08

### Added
- Downloads, in the menu under Your data. It lists everything Ultron has downloaded, such as YouTube videos and audio. Tap a file to play it, or share or delete it. The files are saved in Downloads/Ultron, so they also show in your phone's Files app, and Open in Files takes you there.

## 1.16.2 - 2026-11-08

### Changed
- Storage is a clean list now. It shows one total for what Ultron uses on the phone, and each item has its size and a Clear button. The bars are gone, since nothing there has a limit.
- Icons across the app match the menu: thin and neutral instead of red or coloured. This covers Storage, Skills, Connectors and the Schedule pickers.
- Each skill gets an icon that fits what it does, such as a chat bubble for WhatsApp.

## 1.16.1 - 2026-11-08

### Changed
- The assistant's name is now fixed, and the Assistant name and Greeting fields are gone from Settings.
- The home screen shows a different greeting each session, picked from over a hundred.
- Ultron no longer says "checking" out loud before a slow task. It stays quiet and speaks when the result is ready.

## 1.16.0 - 2026-11-08

### Changed
- Profile is no longer in the menu list. At the bottom of the menu you now see a circle with the first letter of your name, and your name. Tap it to open Profile.
- Developer is part of Settings now. Scroll to the end of Settings for the assistant name, greeting and API keys.
- Images moved inside Knowledge. Open Knowledge and switch between Documents and Pictures.
- Settings now has its own row in the menu.

## 1.15.1 - 2026-11-07

### Changed
- The menu is in a clearer order, in groups: Home and Profile; Assistant (Skills, Connectors, Schedule); Your data (Knowledge, Memory, History, Images); Insights; App. The picture at the bottom is now the first letter of the assistant's name.

### Fixed
- In the dark theme, the main buttons (Update now, Send, Add a schedule, Add pictures and more) were pale. They are now Ultron red with white text. The light theme is unchanged.
- About no longer says "Checking for updates…" on its own. Ultron still looks for updates quietly in the background, and shows "Checking" only when you tap Check for updates.

## 1.15.0 - 2026-11-07

### Added
- New Schedule page in the menu. Tell Ultron what to do and when, in your own words, for example "Send Sonia 'Hello' on WhatsApp" at 9 pm. It can run once, every day, on weekdays or every week, and you can pause, edit, delete or run each one now.
- You can also schedule by voice: "kal subah 7 baje mujhe weather batana".
- After each run you get a notification saying what Ultron did, and the Schedule page shows the last result.
- If a task needs to open an app while the phone is locked, you get an "Unlock to run" notification. Unlock and tap it, and Ultron does it then.

## 1.14.0 - 2026-11-07

### Added
- Choose Ultron's personality in Settings: Destructive (the original cold, menacing Ultron), Friendly, Professional or Funny. Each card shows a sample line.
- You can also switch by voice, for example "be friendly" or "go back to destructive". The new style starts with the next reply.

## 1.13.0 - 2026-11-07

### Added
- New Profile page in the menu: your name, email and phone, your activation key and plan, how many days are left, and this phone's details. The activation details moved here from Settings.
- Help & support can now email the developer at devxjitin@gmail.com, with your app version and phone filled in.
- Report a bug from Help & support. You can include your phone model and Android version so the problem is easier to fix.

### Changed
- About now shows only what changed in the newest version.

## 1.12.4 - 2026-11-07

### Changed
- Internal tidy-up of notes in the code and a few internal names. Nothing you use changes.

## 1.12.3 - 2026-11-07

### Changed
- Internal clean-up: removed code and a library that nothing used any more (an old web-tool schema converter, a day-grouping helper for past conversations, unused text helpers and the unused serialization library). This makes the app a little smaller. Nothing you use changes.

## 1.12.2 - 2026-11-07

### Fixed
- Holding the power or home button while Ultron is closed now starts listening as soon as the app is open, instead of only opening it.
- After a phone call during a conversation, Ultron plays through the speaker again instead of the earpiece.
- Interrupting Ultron while it talks, or stopping a conversation at the same moment, can no longer crash the app.
- The Stop and Mute buttons in the notification keep working after the app restarts itself (for example after saving Developer settings).

## 1.12.1 - 2026-11-07

### Fixed
- Replying to a chat notification now needs the name of the person or group. Ultron answers only that chat, says who the reply went to, and asks you when several chats fit instead of replying to the newest one.

### Changed
- The Gemini key is now sent to the voice connection in a header instead of in the web address, so it cannot show up in logged addresses.

### Removed
- The Reconnect mark for online connectors, which never showed up. Local connectors still show Repair when Android access is taken away.

## 1.12.0 - 2026-11-07

### Changed
- Slow requests (web search, web pages, weather, YouTube, screen pictures, document search) no longer leave Ultron silent. It says a short "checking", keeps listening, and gives the answer once you stop speaking instead of cutting in.
- Earlier turns are now sent in the form Gemini 3.8 Live expects, so follow-ups keep working after a fresh connection.

## 1.11.1 - 2026-11-07

### Fixed
- Button text was hard to read on the Images, Knowledge, Skills, Memory and Connectors pages after the new look. Those buttons now use the same main button colours as the rest of the app, in both the dark and the light theme.

## 1.11.0 - 2026-11-07

### Changed
- A cleaner, calmer look across the app. Warm near-black and paper-white colours, flat grey cards without borders, smaller text, and a serif for page titles and the menu heading.
- The menu is lighter: thin icons, smaller labels, a soft highlight for the open page, hairlines between groups, and a new thin menu button on every page. Page titles now sit in the middle of the top bar.
- Buttons are round pills, and the main button is light on a dark page and dark on a light page. Text boxes have a thin grey border.

## 1.10.2 - 2026-11-07

### Changed
- The request form was redesigned. Skill and connector now have their own full screens with their own wording, clear text boxes with a highlighted border while you type, a character count, an email check and a Send button that shows when it is sending. The cards on Help & support also react when you press them.

## 1.10.1 - 2026-11-07

### Changed
- Files and All files are now one Files connector. It needs All files access, and it finds, browses and opens files in every folder. If you had either one on, Files is on.

## 1.10.0 - 2026-11-07

### Added
- A new All files connector. Once you allow All files access, Ultron can look inside any folder on your phone, and file search covers every folder and every type of file, not only photos, videos and music. It can open what it finds.

## 1.9.2 - 2026-11-07

### Changed
- The menu rows sit closer together, so more pages fit on the screen without scrolling.

## 1.9.1 - 2026-11-07

### Changed
- The form to ask the developer for a skill or connector now lives on a new Help & support page in the menu.

## 1.9.0 - 2026-11-07

### Added
- Ultron can show you pictures. Add pictures on the new Images page with a name and a few words about what is in them, then say "show me the picture of ...". It can also show a photo it found on your phone. Tap, press back or the close button to hide it, or ask Ultron to close it.
- A new card at the bottom of Skills and Connectors lets you ask the developer for a skill or connector you need. It is sent from inside the app.
- A new History page in the menu keeps every chat session in one place. Each session shows when it happened and what you first asked. Tap one to read the whole chat, search across all of them, or delete a session.
- Storage now shows one card each for Memory, Past conversations, Knowledge documents, Cache and Usage, with an icon, size, a bar for its share of the space and its own Clear button.
- Knowledge documents open as neatly formatted text. Tap Edit to change the raw text.
- Ultron now runs its own alarms and timers. They ring with sound, and you can ask it to list, change or cancel them, including timers, without the Clock app opening.
- Alarms can repeat every day or week.

### Fixed
- "Cancel my alarm" no longer just opens the Clock app. The alarm is removed, and Ultron tells you which one.
- An alarm you already turned off or that already rang is no longer reported as still set.
- Ultron now looks up earlier conversations whenever you ask about them, and remembers the last few exchanges from the start of every chat.

### Changed
- Searching your files is much more forgiving. You can say part of a name, make a small typo, name the folder or say yesterday or last week, and Ultron still finds it, opens it, or asks which one you mean.
- Ultron now starts the job as soon as you ask, and tells you when it is done, instead of announcing what it is about to do first.
- The Memory page now shows only what Ultron remembers about you. Past conversations moved to History.
- Connector groups no longer have a line of text under their name, except the warning on Phone control.
- Long-term memory is no longer a connector. Remembering past conversations is part of Ultron itself and is always on. You can still clear it from Storage.

## 1.8.1 - 2026-11-06

### Fixed
- A page, message or notification that contains the word "untrusted" no longer switches off the safety warning Ultron adds to outside text.
- Removing your activation key, or typing a new one, now cancels any check still running, so an old answer can no longer bring the old key back.
- Setting the phone clock back no longer makes the offline grace period last longer.
- The licence server now stops an activation when the key is revoked or expires at that same moment, and no longer sends the buyer's name, email and phone to a phone whose key is not active.

## 1.8.0 - 2026-11-06

### Added
- Each skill's page now shows its instructions as neat, readable text. Tap a task to see its steps. Only your own skills can be edited.

### Changed
- Knowledge, Developer and Settings now look like the rest of the app: tidy cards and small headings. Knowledge shows each document with its size and date.
- Connectors show a small "Connected" badge, so names fit on one line. The menu highlights the page you are on.
- Usage no longer has Messages, because Ultron has no text chat. It shows talk time, conversations and actions.
- About lists each version in its own card, with one bullet per change.

### Fixed
- Opening Connectors no longer closes the app.

## 1.7.0 - 2026-11-06

### Added
- Long-term memory: the assistant now remembers every past conversation and can look up what you said yesterday, last week or last month, and answer from it. It has its own switch in Connectors.
- Memory now lists your past conversations by day. Tap one to read it all, delete one, delete a whole day, or clear everything. Storage shows how much they take.

### Changed
- A new conversation no longer starts with your last few messages loaded. The assistant looks things up only when you refer to something from before, so it starts a little faster.
- If the assistant goes quiet after you ask something, the app now reconnects by itself and asks again, so you do not have to repeat yourself.

## 1.6.0 - 2026-11-05

### Changed
- Alarms and Reminders are now two separate connectors, each with its own switch.
- Reminders now pop up with a sound and vibration, and stay until you dismiss them.
- Reminders can be set for a clock time or a date, up to a year ahead, and can repeat daily or weekly.
- Reminders go off on the exact minute, with nothing to switch on.
- The reminder list reads in plain words, and the alarm list also shows the phone's next alarm.
- If Android would block the clock while Ultron is in the background, the assistant now says so instead of claiming the alarm was set.

## 1.5.0 - 2026-11-05

### Changed
- The Usage page is simpler: talk time, conversations, messages and actions at a glance, a chart you can tap, and how this period compares with the last one.
- Usage shows what the assistant was used for. Prices and token counts are gone.
- The Connectors page no longer repeats its title.

## 1.4.0 - 2026-11-05

### Changed
- Web search now runs through Ultron's server using DDGS, with more search engines behind it. If the server cannot answer, your phone searches DuckDuckGo itself.
- The DuckDuckGo connector is now called Web search.

## 1.3.0 - 2026-11-05

### Changed
- Saving a new contact now lives under Contacts.
- To call or text someone, the assistant uses your phone's own apps with Screen control.

### Removed
- The Calls and Messages connectors.

## 1.2.0 - 2026-11-05

### Added
- A new menu with a cleaner look, a close button and Settings at the bottom.
- Each skill has its own page, and skills are grouped by category.
- Connectors can be filtered by category.
- Your own skills have an Edit button.

### Changed
- Short, simple wording across the app.
- The update window now keeps the download progress in view. Release notes are behind More info.
- WhatsApp messages are sent more reliably.

### Removed
- The GitHub connector.

## 1.1.0 - 2026-11-05

### Added
- About now lists what changed in every version.
- Settings has a new "Read out notifications" switch. When it is on, the assistant tells you about new phone notifications during a voice session. It is off by default.

### Changed
- Connectors are grouped into Essentials, Search & media, Personal data, Communication, Phone control and Accounts. Phone control carries a warning.
- Ultron always uses your own Gemini API key for voice, documents and search. Add it in Developer settings.
- The Knowledge API key is optional. Leave it empty and documents and search use your Gemini key.

### Removed
- The tap-to-allow check that could appear before the assistant sent a message or placed a call after reading outside text. Ultron now acts hands-free, and still tells the model not to follow instructions found in messages, notifications and web pages.
- The agent prompt and dataset preparation prompt are no longer shown or editable in Developer settings. The assistant uses its built-in prompts.
- Built-in Gemini access through Firebase. Without your own key the assistant now asks you to add one.

## 1.0.0

### Added
- Live voice conversations with Gemini, with interruption and 13 spoken languages.
- Phone tools for device settings, alarms and reminders, calendar, contacts, calls, messages and notifications.
- Memory, a private knowledge base, and connectors for web search, web pages, YouTube and GitHub.
- Activation keys, usage and storage pages, and one-tap updates.
