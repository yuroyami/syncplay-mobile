# Store declarations

This note records the privacy answers that Google Play and the App Store ask for, and where each
answer comes from. The answers follow [`PRIVACY_POLICY.md`](PRIVACY_POLICY.md) and the code.

When a change edits the privacy policy, update the matching answer here. Then copy the answer
into the store console with the next release.

## What leaves the device

Every answer below comes from this table. Each service also sees the IP address of the device.

| Service | What the app sends | When |
|---|---|---|
| The Syncplay server that you join | the username, the room name, the server password as an MD5 hash, the operator password of a managed room, chat messages, the ready state, the playback state and position, the name, size and duration of the file, and the shared playlist entries | while you are in a room |
| Klipy | the search text, the GIFs that you send, and a random ID | when you use the GIF panel, or when a Klipy GIF shows in chat |
| OpenSubtitles | the search text, the language filter, the season and episode numbers from the file name, the OpenSubtitles hash of a local file, and the ID of a subtitle that you download | when you search for subtitles or download one |
| api.ipify.org | the request only | when you start a server that you host |
| GitHub | the request only | when you tap **Check for updates** |
| The site of a video link, and the media resolver for it | the request for the link | when you or the room play a link |

The developer runs no server and keeps no user data. A Syncplay server belongs to whoever runs it.

## Google Play: Data safety

### Data collection and security

| Question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | Yes | The app sends the data in the table above off the device. |
| Is all of the user data collected by your app encrypted in transit? | No | A Syncplay server that cannot encrypt gets a plain connection, unless **Require encryption** is on. |
| Do you provide a way for users to request that their data is deleted? | No | The developer keeps no user data to delete. |

### Data types

| Data type | Collected | Shared | Processed ephemerally | Required | Purpose |
|---|---|---|---|---|---|
| Personal info: Name (the username) | Yes | No | Yes | Yes | App functionality |
| Messages: Other in-app messages (the room chat) | Yes | No | Yes | No | App functionality |
| Files and docs (the file name, size and duration, and the subtitle file hash) | Yes | No | Yes | No | App functionality |
| App activity: In-app search history (GIF and subtitle searches) | Yes | No | No | No | App functionality |
| App activity: App interactions (the GIFs that you send, for the Klipy recent list) | Yes | No | No | No | App functionality |
| Device or other IDs (the Klipy ID) | Yes | No | No | No | App functionality |

- "Shared" is No for every type. The app sends each item to the service that the person uses for
  that feature, as a direct result of an action of that person. Google Play does not count such a
  transfer as sharing.
- The room shows the username and the chat to the other people in the room. That is what a room
  is for.
- "Required" is Yes only for the username, because nobody can join a room without one. A file
  name can be sent as a hash or not at all, see the settings.
- The Klipy ID is random. While **Remember recent GIFs** is off, the app makes a new one at each
  launch.

### Photo and video permissions

The app declares `READ_MEDIA_VIDEO`, and `READ_EXTERNAL_STORAGE` up to Android 12L. Only an
Android TV asks for them. Answer the declaration with this text:

> Synkplay plays videos in sync with other people. On Android TV, the system file picker cannot
> be used with a remote. So the app lists the videos on the television, and the person picks one
> with the remote. A phone or a tablet never asks for this permission: it uses the system file
> picker.

## App Store: App Privacy

| Question | Answer |
|---|---|
| Do you or your third-party partners collect data from this app? | Yes |
| Data used to track you | None |

| Data type | Linked to the user | Used for tracking | Purpose | Why |
|---|---|---|---|---|
| Search History | No | No | App Functionality | Klipy and OpenSubtitles receive the search text. |
| Product Interaction | No | No | App Functionality | Klipy keeps the GIFs that you send, for your recent list. |
| Device ID | No | No | App Functionality | Klipy receives the random ID that the app makes. |

- The data for the Syncplay server is not in this table. Apple counts data as collected when the
  developer or a partner can read it for longer than it takes to serve the request. The server
  relays the data in real time, and it belongs to whoever runs it, not to the developer.
- The same three types are in `NSPrivacyCollectedDataTypes` in
  [`PrivacyInfo.xcprivacy`](iosApp/iosApp/PrivacyInfo.xcprivacy). Keep the two lists the same.
- The iOS purpose string for the local network covers a server that you host. The app has no other
  purpose string.

## Decisions for the owner

- Data that the app sends to a Syncplay server that the person picks may not count as collected
  by Google Play, because the developer never receives it. This note answers Yes, which is the
  careful choice. Answer No only after you read the current Play definition of collection.
