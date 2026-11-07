# Privacy plan: keep personal data on the phone

Status: planned, not started (written 2026-11-07).

## Goal

Personal data, screenshots included, should not reach the cloud model (Gemini). Ultron should still do the same work it does today.

## The limit

While the brain is Gemini in the cloud, "no personal data in the cloud" and "exactly the same abilities" cannot both be fully true, because the model cannot reason about what it never sees. The plan gets as close as possible. Gemini plans and sees placeholders. The real data stays in a vault on the phone, and the phone does the work.

## What reaches Gemini today

| Data | Where it comes from |
|---|---|
| Screenshots | `look_at_screen` uploads the whole screen as a JPEG (`DeviceToolRunner`, `GeminiTextClient`) |
| Screen text | `read_screen` sends the accessibility text: chats, names, OTPs, amounts |
| Notifications | `read_notifications`, `reply_to_notification` |
| Contacts, calendar, location, clipboard, file names, app usage | Tool results sent in `toolResponse` |
| Knowledge base documents | Embeddings are made by the Gemini API (`GeminiEmbeddingClient`) |
| Conversation memory and history | `search_conversation_history`, `recent_conversation` |
| The owner's voice | Raw audio goes to Gemini Live (`GeminiLiveClient`, `realtimeInput.audio`) |

## The design: split brain

- **Gemini (cloud):** understands the request, plans, and picks tools.
- **Privacy layer (phone):** every tool result passes through it before it goes to Gemini.
  - It labels each piece of data as public or personal.
  - It swaps personal values for placeholders such as `[CONTACT_1]`, `[MSG_2]`, `[PHONE_1]`, `[OTP]` or `[AMOUNT_1]`, and keeps the real values in a vault for the session.
  - When Gemini calls a tool with a placeholder (for example "call `[CONTACT_1]`"), the runner swaps the real value back in before it acts.
  - The vault is cleared when the session ends.

## Phase A: screen and personal data (biggest gain, nothing breaks)

- `look_at_screen`: never upload the screenshot. On the phone, run ML Kit text recognition (and later a local vision model) and send Gemini only the layout: buttons, labels and positions, with personal text redacted.
- `read_screen`: keep the UI structure and the button labels. Redact message bodies, names, numbers, emails, OTPs and amounts. Item numbers stay, so `click_screen_item` and taps work as before.
- Notifications: send the app name and placeholders. To read one aloud, the phone speaks the real text itself, and the tool result only says "read 3 notifications aloud".
- Contacts and calendar: return placeholders. Calling, messaging and creating events resolve them on the phone.
- Location: send only the city. Directions are opened by the phone from the real coordinates.
- Clipboard and file names: send placeholders, and open or share on the phone.
- Pieces to build: a `PrivacyFilter` (classify and redact), a `PrivacyVault` (placeholders to real values), a resolve step in `DeviceToolRunner`, and tests for redaction (Indian phone numbers, OTPs, ₹ amounts, emails, Hinglish names).

## Phase B: voice and answers

- Switch Gemini Live to text output and speak with Android TextToSpeech, so the phone can swap placeholders back to real names before speaking. With audio output, Gemini would say the placeholder itself.
- Optional: on-device speech-to-text, so recordings of the owner's voice never leave the phone and only text (with placeholders) is sent.
- Trade-off: the voice sounds more robotic and answers can come a little later.

## Phase C: on-device model for private content

- Tasks that need the real content, such as "summarize my WhatsApp messages" or "what does this PDF say", run on an on-device model (Gemma via LiteRT / MediaPipe, or Gemini Nano through AICore where the phone supports it).
- Knowledge base: on-device embeddings (for example EmbeddingGemma) and local search. Answers about private documents come from the local model.
- Memory and history: search on the phone, and send only redacted snippets.
- Trade-off: weaker answers than the cloud, and it depends on the phone's RAM and chip. Check the owner's phone before choosing a model.

## Still going to the cloud after all phases

- The words of commands until Phase B's on-device speech-to-text is added.
- Public data: web search, weather, YouTube, and the app layout with personal text removed.

## Rules to keep while building

- No new confirmation taps. The owner wants hands-free use, so privacy is on by default with no prompts.
- Every app change needs a version bump and a changelog entry.
- Keep the tool count under the cap (110), and keep every DeviceTool in exactly one connector.

## Suggested order

Phase A first. It covers the most sensitive data (screenshots and screen text), and no feature is lost.
