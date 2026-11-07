# Changelog

The newest version goes first. The first "## x.y.z" heading below is the app's version: the build, the update
notes and the About page all read it from here. A date after the version is optional.

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
