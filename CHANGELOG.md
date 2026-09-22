# Changelog

Written for people who use the app. The full engineering history is in the commit log.

## 0.25.0

- Audio files can now be played. (#168, requested by @gili-gili)
- Audio files show a visualizer. You can turn it off in the room's player settings.
- The visualizer changes patterns on its own, or you can pick one.
- Fixed the keyboard closing after one letter on Home. (#167, #170, reported by @gili-gili and @rkpsx7, fixed by @mvanhorn)
- Android TV: every control now works with a remote. (#159, contributed by @mukulhase)
- Android TV: the app lists your videos when no file picker is available. (#163, contributed by @mukulhase)
- Invite links are now https links, clickable in any messenger. The page opens the app or links to the stores.
- The room's side panels are merged into one.
- You can now set chat colours, and names in event messages are coloured.
- Long press a GIF to save it or copy its link. Long press a message to copy it.
- Chat messages over the video fade out, and new ones are marked.
- GIF and sticker controls share one row. Sending one clears the search text.
- The shared playlist can be reordered by dragging. After a disconnect, the app offers to restore it.
- Tracks show more detail, and video tracks can be selected.
- mpv on Android now uses libmpvKt, which fixes video track selection.
- iOS: the screen stays on in rooms, including after switching players.
- iOS VLC: fixed the seek bar freezing and the room jumping backward. (#158, reported by @yvchenko)
- Fixed rapid seeks losing the latest position, and an unfinished seek moving a new file.
- iOS VLC: fixed seeking in Picture-in-Picture, and the resume offer when watching alone.
- Home shows what each player supports.
- Invalid join links and port numbers are now rejected.
- Screen readers can now reach the join form.
- The ExoPlayer-only APK is now reproducible, so IzzyOnDroid can verify it. (#105, #172, requested by @IzzySoft)

## 0.24.0

### The new look

- The whole app was redrawn: one set of controls, five text sizes, colours that come from the theme, and frosted panels over the video on devices that can draw them.
- Home is a single form: your name, the server, the player, and Join. It fits a phone, a phone on its side, a tablet or a desktop window.
- The room has a rail for its actions and a status line. The controls hide while the video plays and come back on a tap.
- Settings have a search box, grouped rows, values in units people actually read, and colours you edit in place. Each player's settings sit inside the player category.
- Themes preview as a miniature of the app, and the theme creator shows what a seed colour will do before you save.
- Motion can be reduced from settings. A screen reader announces who is ready, connection changes and new messages.
- Watching alone has its own row in About: tap the logo on Home.

### Players

- KitePlayer, a new engine written for this app on top of FFmpeg, is available on Android, iOS and desktop as an experimental choice. It has chapters, external and styled subtitles, pitch-preserved speed from 0.25x to 4x, and plays links over https.
- Three engines per platform: ExoPlayer, mpv and KitePlayer on Android, and the system player, VLCKit and KitePlayer on iOS. VLC on Android and mpv on iOS are gone. VLCKit is the iOS default.
- Volume can go past 100 on players that can boost (mpv, ExoPlayer, VLCKit). The device volume fills first, then the player's own gain takes over.
- Double taps add up to one jump, and a long press shows where a seek will land before it commits.
- When you watch alone, the app remembers where you left a file and offers to continue from there.
- Seeking with KitePlayer lands on the exact frame in one step and is about twice as fast. Files with tens of thousands of subtitle lines no longer slow it down.
- YouTube links work again with the updated extractor, and a friend's YouTube link in the shared playlist is accepted without any setup. SoundCloud, PeerTube, Bandcamp and media.ccc.de links work the same way on Android and desktop. iOS handles YouTube only.

### Sync and playback

- Fixed the phantom pause: a player stopping on its own no longer pauses the whole room.
- The room shows when it is waiting for the video instead of looking frozen, and says who it is waiting for before playback starts.
- Audio and subtitle picks survive a reload on every engine, and follow your preferred languages.
- Tracks marked as accessibility captions, audio description or forced now say so in the picker.
- When a file ends, the playlist moves on once, not once per person in the room.
- A file that is still opening no longer drags the whole room back to the start.
- iOS with VLC: a video from a network share reported no length at first, and the app filled the gap with a placeholder. Dragging the seek bar then sent the room to a nonsense time, and everyone landed back at the start. The bar now waits until the real length arrives.
- If you load your file after the room has already started, you now sync to the room instead of sitting paused at the start.
- A per-user time offset lets two different rips of the same film be watched together.
- The three drift thresholds (rewind, slowdown, fast-forward) are now settings.
- A phone coming back from the background follows the room instead of dragging it back.

### Room and chat

- Chat colours follow the theme, so they stay readable on a light one.
- Chat timestamps follow your device's clock format.
- Slash commands in the chat box: /ready, /room, /seek, /op, /users and /help.
- Volume and brightness have their own controls, so a swipe is no longer the only way.
- You can mute someone, and image links from other people stay hidden until you tap them.
- The user list has a compact view and a detailed one. The detailed view shows each person's file, its length, its size and whether it matches yours.
- A playlist change can be undone.
- Picture in picture grows out of the video instead of popping up from nowhere.
- The room can follow the device's rotation or stay in landscape. There is a setting for it.
- Android: lock screen and headset controls work in the room and follow the same readiness rules as the play button.
- On a television, the room's controls stay inside the visible area.
- The locked screen tells you how to unlock it.
- The room shows whether your connection is encrypted.
- Panels look right on a light theme.
- Buffering no longer pushes the play button around. The button rounds into a circle and its colours move until the player catches up.
- The custom skip button sits between the two jump buttons.
- Chat that fades in while the controls are hidden now shows at the top centre, under the notices, at a readable size. It used to sit tiny at the left edge.
- Subtitle search and download work again, you can pick the subtitle language, and a failed search says why.
- A bug report sent from the app says which engine, which build and which device.

### Connection

- Encrypted connections now check the server's certificate against the name you typed.
- You can require encryption. A server that offers none is refused before anything is sent.
- A dropped connection reconnects with a growing wait instead of hammering the server.
- A handshake that never finishes gives up instead of hanging.
- Leaving the server address empty joins the official server, as it always looked like it would.
- The network you are on can no longer push the app's own services onto plain HTTP.
- A pasted link is checked against the site it really points to, not the text shown in front of it.

### Hosting

- Hosting lives on the home screen now, under the server choice, with the address first.
- The hosting screen and its notification use the app's language.
- A silent client is dropped after the timeout the server already advertised.
- An operator password works only for the room it belongs to.
- Creating a managed room now really puts the password on your clipboard, which it has claimed to do for a long time.

### Language

- Change the app's language from settings, on every platform, with no restart.
- All seven translations are complete: Arabic, German, Spanish, French, Polish, Russian and Chinese.
- Audio and subtitle language names are shown in your own language.
- A crash when opening settings, caused by a string defined twice, is fixed. Strings are now generated at build time and checked for duplicates.

### Elsewhere

- Invite links: share a room with a link, and open one to join.
- About lists what the app is built from, and can check for a newer release.
- Settings can be exported to a file and imported back.
- A damaged settings file no longer crashes the app at launch. It starts on defaults and tells you.
- Service keys are never written to the log, so a log you share carries none.
- You can tell the GIF service to forget you between sessions.
- Reading settings at startup no longer blocks drawing, and neither does logging.
- Android 13 and up can theme the launcher icon.
- Android downloads are two files now: the full universal APK and the smaller exo-only one. The per-CPU files are gone. Google Play serves each phone only what it needs.
- The app also runs on desktop (Windows, macOS, Linux) with KitePlayer. Installers are not part of this release yet.
- mpv now comes prebuilt from libmpvKt 0.1.0 (mpv 0.41.0, FFmpeg 9.0.1). Building the app no longer compiles mpv, and a clone builds without a native toolchain. The exoOnly build still ships no native player, and a build check now proves it on the APK.
