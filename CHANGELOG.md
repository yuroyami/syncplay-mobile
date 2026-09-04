# Changelog

Written for people who use the app. The full engineering history is in the commit log.

## 0.24.0

### Sync and playback

- Fixed the phantom pause: an engine stopping on its own no longer pauses the whole room.
- The room shows when it is waiting for the video instead of looking frozen.
- Audio and subtitle picks survive a reload on every engine, and follow your preferred languages.
- Tracks marked as accessibility captions, audio description or forced now say so in the picker.
- The end of a file moves the playlist on by one, not once per person watching.
- A file that is still opening no longer drags the whole room back to the start.

### Room and chat

- Chat colours follow the theme, so they stay readable on a light one.
- Chat timestamps follow your device's clock format.
- Volume and brightness are reachable without a swipe.
- You can mute someone, and peer image links stay hidden until you tap them.
- Picture in picture morphs out of the video instead of appearing from nowhere.
- A television keeps the room's controls inside the visible area.
- The locked screen tells you how to unlock it, instead of leaving you guessing.
- The room shows whether your connection is encrypted.
- Panels look right on a light theme, not bruised at the bottom and edgeless at the top.

### Connection

- Encrypted connections now check the server's certificate against the name you dialled.
- A dropped connection reconnects with a growing wait instead of hammering the server.
- A handshake that never finishes gives up instead of hanging.
- Leaving the server address empty joins the official server, as it always looked like it would.
- The app's own services can no longer be pushed onto plain HTTP by the network you are on.

### Hosting

- The hosting screen and its notification speak the app's language.
- A silent client is dropped after the timeout the server already advertised.
- An operator password works only for the room it belongs to.
- Creating a managed room really does put the password on your clipboard, which it has claimed
  to do for a long time.

### Elsewhere

- Invite links: share a room with a link, and open one to join.
- Audio and subtitle language names are shown in your own language.
- About lists what the app is built from, and can check for a newer release.
- The GIF service can be told to stop recognising you between sessions.
- Startup no longer reads preferences on the drawing thread, and neither does logging.
