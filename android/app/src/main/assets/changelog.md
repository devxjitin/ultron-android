# Changelog

The newest version goes first. The first "## x.y.z" heading below is the app's version: the build, the update
notes and the About page all read it from here. A date after the version is optional.

## 1.1.0 - 2026-11-05

### Added
- About now lists what changed in every version.
- Settings has a new "Read out notifications" switch. When it is on, the assistant tells you about new phone notifications during a voice session. It is off by default.

### Changed
- Connectors are grouped into Essentials, Search & media, Personal data, Communication, Phone control and Accounts. Phone control carries a warning.
- Ultron always uses your own Gemini API key for voice, documents and search. Add it in Developer settings.
- The Knowledge API key is optional. Leave it empty and documents and search use your Gemini key.

### Removed
- The agent prompt and dataset preparation prompt are no longer shown or editable in Developer settings. The assistant uses its built-in prompts.
- Built-in Gemini access through Firebase. Without your own key the assistant now asks you to add one.

## 1.0.0

### Added
- Live voice conversations with Gemini, with interruption and 13 spoken languages.
- Phone tools for device settings, alarms and reminders, calendar, contacts, calls, messages and notifications.
- Memory, a private knowledge base, and connectors for web search, web pages, YouTube and GitHub.
- Activation keys, usage and storage pages, and one-tap updates.
