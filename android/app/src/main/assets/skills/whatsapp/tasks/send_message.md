---
name: send_message
description: Send a WhatsApp text message to one person or group by name.
---
# Send a WhatsApp message

1. You need the recipient and the exact message. If either is unclear, ask first.
2. Read the message and the recipient back in one short line and wait for a clear yes before sending.
3. open_app with WhatsApp, then wait_for_screen_text for "Chats".
4. Tap the search icon (click_on_text "Search" or the search item), type_text the recipient's name, and read_screen.
5. If no chat matches, or more than one could be the person, stop and ask which one. Never pick a guess.
6. Open the matching chat, confirm with read_screen that its title is the right person, then tap the "Message" box and type_text the message.
7. Tap "Send" (or press_enter if the send button is not found), then read_screen to confirm the message appears in the chat.
8. Tell him it was sent. If the last step did not show the message, say it may not have been sent.
