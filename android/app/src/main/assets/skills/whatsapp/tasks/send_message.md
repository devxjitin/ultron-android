---
name: send_message
summary: Send a text message to a contact.
description: Send a WhatsApp text message. Use for "WhatsApp <name> that ...", "send a WhatsApp message to <name>", "<name> ko WhatsApp pe bol do ...", "<name> ko message bhej do WhatsApp par".
---

# Send a WhatsApp text message

## Inputs
- **Contact** and **Message**: the exact text. Send the user's words as given; do not rewrite, translate or add greetings. Only fix obvious speech-to-text errors (punctuation, capitalization). If either is missing, ask before touching the phone.

## Steps
1. Open the person's chat and verify the header (shared). If a message just arrived from this person and the user is answering it, use reply_message instead.
2. click_on_text "Message" (the text box), type_text the message, then read_screen and confirm the box holds exactly that text. Do not touch the emoji, attach, camera or mic controls.
3. Send, stopping at the first that works: click_on_text "Send"; click_screen_item on Send from read_screen; look_at_screen and tap_screen_position on the green arrow. The mic on an empty box is NOT send; never hold it.
4. read_screen: the message must be the newest bubble. Report success only when it is. A clock that stays means no network: say so. Do not claim ticks you cannot see.
5. Report in one short sentence, for example "Sent 'I'll be late' to Rahul on WhatsApp."

## Rules
- Never send OTPs, passwords or card numbers seen on screen.
- Wrong text typed: clear the box and retype before sending. If it was already sent, tell the user honestly.
- Keyboard hides the send button: press_back or scroll to reveal it; do not tap blindly.
