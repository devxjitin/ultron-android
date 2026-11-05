# Changelog

The newest version goes first. The first "## x.y.z" heading below is the app's version: the build, the update
notes and the About page all read it from here. A date after the version is optional.

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
