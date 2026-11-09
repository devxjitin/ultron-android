---
name: voice_call
summary: Start a WhatsApp voice call.
description: Start a WhatsApp voice call. Use for "WhatsApp call karo Rahul ko", "WhatsApp pe call lagao", "call <name> on WhatsApp". For a normal phone call (no WhatsApp named), use make_call instead.
---

# WhatsApp voice call

1. Open the person's chat and verify the header (shared). If several people fit, ask which before calling.
2. In the chat header click_on_text "Voice call" (sometimes just "Call"). If that fails, click_screen_item from read_screen, or look_at_screen to find the phone icon at the top right and tap_screen_position on it.
3. If WhatsApp asks "Start voice call?", the user asked for it, so click_on_text "Call".
4. read_screen: the call screen shows "Calling" or "Ringing" with the contact's name. Only then say "Calling Rahul on WhatsApp." If it says there is no internet or the call failed, say so.

## Rules
- A group call rings everyone: call a group only when the user named the group and said group call.
- Microphone permission: if WhatsApp asks Android for it, tell the user to allow it on the phone.
- Already in a call: ask before starting another.
- Never tap the phone icon next to a number inside the contact card: that places a normal call.
