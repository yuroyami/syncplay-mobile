# Privacy Policy

Synkplay is a Syncplay client for Android, Android TV and iOS, built by **yuroyami**. This policy
says what the app sends, who receives it, and what stays on your device.

There is no account system, no analytics SDK, no advertising, and no crash-reporting service.
Synkplay has no server of its own.

## What the app sends, and to whom

The app contacts only the services below, and only at the moments that this section names. Each
service sees the IP address of your device when the app contacts it.

### The Syncplay server that you join

The server receives:

- your username and the room name
- the server password as an MD5 hash, when the server has a password
- the operator password of a managed room (a room where only its operators control playback),
  when you create that room or identify as its operator. The app does not hash this password.
- your chat messages
- your ready state, your playback position, and whether you play or pause
- the name, the size and the duration of the file that you play
- the file names and the links that you add to the shared playlist (the list of files that
  everyone in the room follows)

The server passes most of this to the other people in the room.

In the settings, you can share the file name and the file size as they are (the default), as a
short hash, or not at all. This choice does not cover the shared playlist: a file that you add
there goes to the server by its name.

While **Secure connection** is on, which is the default, the app asks the server to encrypt the
connection. When the server cannot encrypt, the app joins in plain text and shows a notice.
**Require encryption** makes the app refuse such a server instead.

### A server that you host

With **Host mine** on Home, your device runs a Syncplay server. The people who join send the data
in the list above to your device. Each time you start the server, the app asks a public-IP lookup
service (api.ipify.org) for your public address, so that the screen can show it.

### Klipy (GIFs and stickers)

- When you open the GIF panel, the app loads GIFs from Klipy: the trending list or your recent
  list.
- When you search, the app sends the search text.
- When you send a GIF, the app tells Klipy, so that the GIF appears in your recent list.
- Each request carries an ID. While **Remember recent GIFs** is on (the default), the app creates
  the ID once and keeps it. This ID holds the time when the app created it, plus random digits.
  Klipy can then link your requests from different launches. The recent list needs that link.
  When the setting is off, the app uses a new random ID at each launch. Klipy then cannot link two
  launches, and the recent list stays empty.
- A Klipy GIF in the room chat loads from Klipy by itself, whoever posted it. An image from any
  other site waits until you tap it.

### OpenSubtitles

- When you open **Search subtitles** while a video is open, the search starts at once. The app
  sends a search text that it makes from the file name of the video, and your language filter.
- When you search again, the app sends your new search text.
- When you download a subtitle, the app sends the ID of that subtitle.

### Media resolvers

A media resolver turns a page link, such as a YouTube link, into a video stream. The resolver
contacts the site of the link directly. No other service takes part.

- On Android, the resolver is NewPipe Extractor. It handles YouTube, SoundCloud, PeerTube,
  Bandcamp and media.ccc.de.
- On iOS, the resolver is YouTubeKit. It handles YouTube only.

A resolver runs when you paste a link to one of these sites, and when the room plays such a link.
A link that someone else adds to the shared playlist plays by itself only when its site is on
your **Trusted domains** list (youtube.com and youtu.be by default). For any other site, the app
asks you first. **Resolve streaming URLs** in the settings turns the resolvers off.

### The site of a video link

When you or the room play a link, your device loads the video directly from the site that hosts
it.

### GitHub

- **Check for updates** in About asks GitHub for the newest release. The app checks only when you
  tap it, and it downloads nothing.
- **Report a bug** in About opens a GitHub issue form in your browser. The form already holds the
  app version, the platform, the system version, the device model and the engine names. GitHub
  receives nothing until you submit the form.

### Invite links

An invite link opens a page on yuroyami.github.io, which GitHub hosts. The room details, including
the server password, are in the part of the link after the `#` sign. Browsers do not send that
part to the server of the page. An invite link never holds the operator password.

## What stays on your device

- Your settings, your custom themes and your saved GIF favorites.
- Your last join details: name, room, server, port, server password and operator password. The
  app saves them while **Remember joining info** is on, which is the default. The app saves the
  passwords in plain text.
- The room shortcuts that you save. A shortcut holds the join details, including the server
  password. On iOS, a shortcut also holds the operator password when you pasted it with the room
  name. On Android, a shortcut pinned to the home screen stays on the launcher until you remove
  it.
- Where you stopped in up to 50 recent files: the file name, the position and the length. The app
  uses them for the offer to continue watching. **Continue watching** in the settings turns this
  off.
- The subtitles that you download.
- A local log for troubleshooting, kept for 7 days. It records what the app does, such as room
  events and web requests, so it can contain usernames, file names and search text. The app masks
  service keys in the log. The app never sends the log anywhere. You can export it or clear it in
  the settings.

On Android, the app turns off cloud backup and device-to-device transfer for its data.

## Permissions

None of these permissions sends data anywhere beyond what this policy lists.

- **Notifications** (Android 13 and newer): the app asks when it starts. It uses notifications for
  the playback controls and for a server that you host.
- **Vibration** (Android): the app gives short feedback when you touch a control.
- **Videos** (Android TV only): on a television, the app asks to read the video library, so that
  it can list your videos. Android 12 and older call this permission storage. A phone or a tablet
  never asks, because it uses the system file picker.
  The app declares this permission for every device, so the store listing shows it for phones too.
- **Local network** (iOS): the app uses it when you host a server, so that devices on your network
  can join that server.
- **Network access and background services** (Android, granted at install): the app uses them for
  the connection to the room, for playback, and for a server that you host.

## Changes to this policy

This policy changes when the app changes. The current version is always at this file's location
in the source repository.
