---
name: video_call
summary: Start a WhatsApp video call.
description: Start a WhatsApp video call. Use for "Mummy ko video call lagao", "video call <name>", "WhatsApp pe video call karo". For a normal phone call, use make_call instead.
---

# WhatsApp video call

1. Open the person's chat and verify the header (shared). If several people fit, ask which before calling.
2. In the chat header click_on_text "Video call". If that fails, click_screen_item from read_screen, or look_at_screen to find the camera icon at the top right and tap_screen_position on it.
3. If WhatsApp asks "Start video call?", the user asked for it, so click_on_text "Video call".
4. read_screen: the call screen shows "Calling" or "Ringing" with the contact's name. Only then say "Video calling Rahul on WhatsApp." If it failed, say so.

## Rules
- A group call rings everyone: call a group only when the user named the group and said group call.
- Camera or microphone permission: if WhatsApp asks Android for it, tell the user to allow it on the phone.
- Already in a call: ask before starting another.
