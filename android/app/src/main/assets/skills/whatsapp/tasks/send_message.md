---
name: send_message
description: Send a WhatsApp text message to a contact on an Android phone by operating the screen. Use when the user says things like "send a WhatsApp message to <name>", "WhatsApp <name> that ...", or "message <name> on WhatsApp".
---

# Send a WhatsApp message (Android)

## Inputs
- **Contact**: the name the user said (often just a first name).
- **Message**: the exact text to send. Send the user's words as given; do not rewrite, add greetings, or "improve" it. Only fix obvious speech-to-text errors (punctuation, capitalization).

If either input is missing or unclear, ask before touching the phone.

## Steps

1. **Open WhatsApp and inspect the current screen.** Launch the app from the home screen or app drawer and take a screenshot. If an app-lock screen appears, follow the **WhatsApp is locked** procedure below automatically. After unlocking, inspect the screen again; WhatsApp may restore the last open chat instead of the chat list.
   - **If a chat is already open:** read only its header and compare it with the requested contact. If it is a clear, unambiguous match, **keep that chat open and skip directly to step 5**. Do not tap Back, search again, or open another conversation.
   - **If the open chat belongs to someone else, or its identity is not clear:** do not type anything; tap Back to return to Chats and proceed to step 2. If the name is ambiguous, ask the user rather than guessing.
   - **If the chat list is open:** proceed to step 2. The main-screen tabs may include Chats / Updates / Communities / Calls, depending on the version.
   - If a popup, update prompt, or backup banner appears, dismiss it with the least-committal option (Not now / Close). Never accept permissions or terms on the user's behalf.

2. **Find the contact (only if the correct chat is not already open).**
   - First look at the **Chats** list (recent chats). If a row's name matches the contact, tap that row.
   - If not visible, tap the **Search** (magnifier) icon at the top, type the **first name only**, and read the results.
     - Prefer a match under **Chats** over one under **Contacts** (it is an existing conversation).
     - Ignore group chats unless the user asked for a group.

3. **Resolve ambiguity before opening anything.**
   - Exactly one clear match: continue.
   - Several people with the same first name (e.g. "Rahul Sharma", "Rahul Office"): stop and ask the user which one. Do not guess.
   - No match: retry once with a different spelling or the last name if known; if still nothing, tell the user the contact was not found. Do not message a similar-looking name.

4. **Open and verify the chat.** If the intended chat was already open and verified in step 1, skip this step. Otherwise tap the **row of the matching chat/contact** (on the name, not blindly the screen centre). Take a screenshot and **verify the header at the top shows the intended contact's name**. If it doesn't, go back and fix it.

5. **Type the message.** Tap the **"Message" text box** at the bottom, wait for the keyboard, and type the message text. Take a screenshot and verify the text in the box is exactly what the user asked for.
   - Do not tap the emoji, attach (paperclip), camera, or mic icons by mistake.

6. **Send.** Tap the green **Send** arrow at the right of the text box. (It only appears once text is typed; the mic icon shown on an empty box is NOT send. Never hold the mic.)

7. **Confirm delivery.** Take a screenshot and check that the message appears as the newest bubble with a clock → single tick → double tick. A clock icon for a long time means no network; report that rather than claiming success.

8. **Report back** in one short sentence: e.g. "Sent 'I'll be late' to Rahul Sharma on WhatsApp."

## Rules and edge cases
- **Right person is more important than speed.** Always verify the chat header (step 4) before typing.
- **Confirm first when risky:** ask the user for confirmation before sending if the message is long, sensitive (money, passwords, OTPs, bank details), or the contact match was uncertain. For short, routine messages to a clearly matched contact, send directly.
- **Never send** OTPs, passwords, or card numbers seen on screen to anyone.
- **WhatsApp vs WhatsApp Business:** if both are installed, use regular WhatsApp unless the user says "business".
- **Contact has no WhatsApp:** if tapping a Contacts result shows "Invite to WhatsApp", stop and tell the user.
- **WhatsApp is locked (app lock, fingerprint/face, PIN or password prompt):**
  1. **Handle the lock screen proactively.** Inspect the screen and, if there is a normal **Unlock**, **Continue**, **Use fingerprint**, or **Try again** button, tap it once to initiate the device's official authentication flow. Do not wait for the user to explicitly ask you to try.
  2. If the app opens without requesting authentication (for example, an already-approved session), take a screenshot, check whether the chat list **or a chat** is visible, and resume at step 1 to inspect the current screen. No extra user confirmation is needed.
  3. If Android requests a fingerprint, face scan, PIN, password, or pattern, **the phone owner must authenticate using the device**. Briefly ask: "WhatsApp needs your authentication. Please use your fingerprint, face, or unlock method on the phone." Never ask the user to share credentials or enter them on their behalf. Never guess, bypass, disable, or weaken the lock.
  4. After the user authenticates, inspect the screen again. If the chat list or an open chat is visible, resume at step 1 automatically **without waiting for the user to say 'done'**. If the lock prompt remains, offer the normal retry button once; if it still requires authentication, ask the user to complete it.
  5. If authentication is unavailable, fails repeatedly, or the app remains locked, stop and report that the message was not sent.
- **Login or verification screen (phone number, SMS code, "Verify"):** stop and ask the user to handle it. Do not enter codes.
- **Keyboard covers the screen:** scroll or hide the keyboard to see the send button; do not tap blindly.
- **Wrong message typed:** clear the box (select all, delete) and retype before sending. If already sent, tell the user honestly rather than hiding it.
- **Don't** read other chats or open other conversations beyond what's needed to find the contact.
