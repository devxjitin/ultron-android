---
name: send_message
summary: Send a message to a contact.
description: Send a WhatsApp text message to a contact on an Android phone by operating the screen. Use when the user says things like "send a WhatsApp message to <name>", "WhatsApp <name> that ...", "message <name> on WhatsApp", or in Hindi/Hinglish "<name> ko WhatsApp pe bol do ...", "<name> ko WhatsApp karo ...", "<name> ko message bhej do WhatsApp par".
---

# Send a WhatsApp message (Android)

## Inputs
- **Contact**: the name the user said (often just a first name).
- **Message**: the exact text to send. Send the user's words as given; do not rewrite, translate, add greetings, or "improve" it. Only fix obvious speech-to-text errors (punctuation, capitalization).

If either input is missing or unclear, ask before touching the phone.

## Choose the route first

1. **Is WhatsApp installed?** open_app "WhatsApp" fails with not_found when it is missing: then say WhatsApp is not installed on this phone, offer to install it from the Play Store, and stop. Use regular WhatsApp unless the user said "business"; if a chooser appears, click_on_text WhatsApp.
2. **Route A, fast (number known).** Use it when the user said digits, or search_contact (give the name as heard; it matches spelling and sound) returns one clear contact. Several fit: ask which. Take the number with its country code (use the number's own +code; if it has none, add the code the user's other numbers use, such as +91 for Indian numbers; if unsure, ask). open_url "https://wa.me/<digits only>" with no text in the link, so Hindi and emoji are typed later and cannot break. read_screen: the header must show that contact's name or number, and WhatsApp must not say the number is not on WhatsApp (then tell the user). Then go to step 5.
3. **Route B (no number).** When the person is not in the phone contacts (a WhatsApp-only chat, an unsaved number shown as ~Name, a group member), or the Contacts connector is off, use steps 1 to 4 below: WhatsApp's own search finds chats and unsaved people.
4. If a message just arrived from this person and the user is answering it, reply_to_notification is quicker than either route.

## Steps (Route B)

1. **Open WhatsApp and read the screen.** open_app "WhatsApp", then read_screen. If an app-lock screen appears, follow **WhatsApp is locked** below. WhatsApp may restore the last open chat instead of the chat list.
   - **A chat is already open:** compare only its header with the contact. If it is a clear match, keep it and go to step 5. Otherwise press_back to the chat list and continue.
   - **Chat list is open:** go to step 2.
   - Dismiss popups, update prompts and backup banners with the least-committal option (Not now / Close). Never accept permissions or terms on the user's behalf.

2. **Find the contact.**
   - If a row in the Chats list matches, open it (see **Opening a chat row**).
   - Otherwise click_on_text "Search", type_text the **first name only** (if search_contact knew the saved name, use that spelling), then read_screen. Prefer a match under **Chats** over **Contacts**. Ignore groups unless the user asked for one.

3. **Resolve ambiguity before opening anything.**
   - One clear match: continue.
   - Several people with the same first name (for example "Rahul Sharma" and "Rahul Office"): stop and ask which one. Do not guess.
   - No match: retry once with another spelling or the last name; if still nothing, say the name is not in the phone or in WhatsApp and ask for the number, then use Route A. Never message a similar-looking name.

4. **Open and verify the chat.** Open the row, then read_screen and check that the **header shows the intended contact**. If not, press_back and fix it. If a profile photo or contact-info card opened instead, press_back once and open the row again.

5. **Type the message.** click_on_text "Message" (the text box), then type_text the message. read_screen and confirm the box holds exactly the intended text. Do not touch the emoji, attach, camera or mic controls.

6. **Send.** Try in this order, and stop at the first that works:
   1. click_on_text "Send".
   2. click_screen_item on the item numbered as Send in the latest read_screen.
   3. look_at_screen to find the green send arrow, then tap_screen_position on it.
   The send arrow appears only once text is typed. The mic icon on an empty box is NOT send; never hold it.

7. **Confirm.** read_screen. The message must be the newest bubble in the chat. Report success only when it is there. If you can see its status, a clock for a long time means no network: say so instead of claiming it was delivered. Do not claim ticks you cannot see.

8. **Report** in one short sentence, for example "Sent 'I'll be late' to Rahul Sharma on WhatsApp."

## Opening a chat row (never tap the profile picture)
Each row has a round profile picture at the far left; tapping it opens a photo or contact card, not the chat.
- Best: click_on_text with the contact's name, or click_screen_item on the row's name element.
- If you must use coordinates, tap_screen_position on the middle of the row at the name or last-message text, never the far-left circle or the far-right time and badge.
- After tapping, check the screen. If the chat did not open, press_back and retry once on the name.

## Rules and edge cases
- **The right person matters more than speed.** Always verify the header (step 4) before typing.
- **Send directly:** the user's request is the permission, so do not ask for confirmation. Ask only when the contact is ambiguous.
- **Never send** OTPs, passwords or card numbers seen on screen.
- **WhatsApp vs WhatsApp Business:** use regular WhatsApp unless the user says "business".
- **Contact has no WhatsApp:** if a Contacts result shows "Invite to WhatsApp", stop and tell the user.
- **WhatsApp is locked:**
  1. Tap a normal Unlock, Continue, Use fingerprint or Try again button once to start the official authentication.
  2. If Android asks for fingerprint, face, PIN, password or pattern, use the lock details the user asked you to remember and enter them with the PIN pad or pattern. If you have none, or it needs a fingerprint or face, ask the user to authenticate on the phone. Never guess, bypass or weaken the lock.
  3. After the user authenticates, read_screen and continue from step 1 without waiting for them to say "done". If the lock stays, retry once, then report that the message was not sent.
- **Login or verification screen (phone number, SMS code):** stop and ask the user to handle it. Do not enter codes.
- **Keyboard hides the send button:** press_back or scroll to reveal it; do not tap blindly.
- **Wrong text typed:** clear the box and retype before sending. If it was already sent, tell the user honestly.
- **Do not** read other chats or open other conversations beyond what is needed to find the contact.
