# Security

## Reporting a vulnerability

Do not open a public issue for a security problem.

Use [GitHub's private vulnerability reporting](https://github.com/yuroyami/syncplay-mobile/security/advisories/new)
for this repository. Your report reaches the maintainer privately, and GitHub keeps a record of it.

Tell us what an attacker can do and how you got there. A rough report that names the real
mechanism is worth more than a polished report that does not. If you are not sure whether
something counts, report it.

You should get a reply within a week. This is a small project with one maintainer, so a fix can
take longer than the reply.

## What this app does with your data

Synkplay talks to the Syncplay server that you join. It contacts other services only when you use
a feature that needs them:

- OpenSubtitles, when you search for subtitles.
- KLIPY, when you open the animated image (GIF) search. Chat also loads images from KLIPY's hosts
  without asking.
- api.ipify.org, when you host a room, to show your public address.
- GitHub, when you ask the app to check for a new version.
- The site of a shared link (YouTube and similar sites), when the app resolves that link to a
  video. Android and desktop use NewPipe Extractor, and iOS uses YouTubeKit.

The app has no analytics, no crash reporting service and no advertising. See
[PRIVACY_POLICY.md](PRIVACY_POLICY.md).

## Where the risk is

Three surfaces take input from people who are not you.

**The server that you connect to.** It sends the room's state, the user list, chat and other
people's filenames. A hostile server can send anything.

- On an encrypted connection, the client checks the server's certificate against the name that you
  typed.
- The client skips a line that it cannot parse, and it keeps the connection.
- The client cuts incoming usernames, chat messages and filenames to fixed maximum lengths.

**Other people in the room.** They send filenames, chat messages and playlist entries.

- A playlist entry can be a link. The app opens a link from someone else only when the link's host
  is on your trusted-domain list, or when you allow the link.
- The default trusted-domain list holds `youtube.com` and `youtu.be`. For a link to any other
  host, the app asks you to allow it once or always.
- A link that you add yourself needs no trust check.
- Chat loads an image by itself only from KLIPY's hosts (`klipy.com`, `klipy.co` and their
  subdomains). An image from any other host shows as a hidden line until you tap it.
- The app does not send a playlist over 250 entries or 10,000 characters. The hosted server refuses
  such a playlist, as the reference server does.
- The app keeps the chat messages that it sends within the length limit that the server
  advertises.

**Anyone who can reach a room that you host.** The hosted server is a Syncplay server that runs
inside the app. If you forward a port, anyone on the internet can reach it.

- The hosted server can ask for a server password, and it drops a client that sends a wrong one.
- The hosted server does not run a command that arrives before the handshake, and it drops that
  client.

## Known limits, stated plainly

These limits are real and open.

- **A hosted server has no encryption.** It refuses every encryption request, so a room that you
  host runs in plain text. The server password crosses as a digest. Usernames, filenames, chat and
  the operator password of a controlled room (a room that only its operators can control) cross in
  plain text.
  Tracked in #ISSUE(built-in-server-has-no-tls).
- **A hosted server cannot remove or block a person.** Anyone who reaches the port can join. A
  server password, when you set one, is the only check, and there is no pairing step.
  Tracked in #ISSUE(server-has-no-operator-controls).
- **A hosted server has no connection limits.** A connection that never finishes the handshake
  stays open, and nothing caps the number of clients.
  Tracked in #ISSUE(hosted-server-has-no-admission-limits).
- **Saved passwords are stored as plain text.** When the app remembers your join details, it keeps
  the server password and a controlled room's operator password in ordinary preferences. A
  home-screen shortcut carries the server password as well, and on iOS also the operator password.
  The hosted server's password is an ordinary preference too. Anything that can read the app's data
  can read them.
  Tracked in #ISSUE(passwords-stored-in-plain-preferences).
- **Exported logs are only partly redacted.** The export masks the two API keys. It keeps room
  names, usernames, filenames and server addresses. It also covers up to seven days of logs, not
  only the current session.
  Tracked in #ISSUE(exported-logs-are-not-redacted).

## API keys in this repository

Both API keys in this repository are public, so reporting either key adds nothing.

- The KLIPY key is public on purpose. It ships inside every app and travels in the address of every
  KLIPY request, so a device cannot keep it secret. Outside builders need it to rebuild the
  ExoPlayer-only APK.
- The OpenSubtitles key is in the git history, and published builds still use it. Moving it out of
  the repository is tracked in #ISSUE(opensubtitles-key-committed).

## Rules a change must keep

If you contribute, these rules are not optional.

- **Check the certificate against the name that the user typed**, not against the address that
  the name resolved to. The app can dial the official server at a fixed fallback address, so the
  address that it dials and the name that it checks can differ. Both encrypted transports use the
  typed name for the check and for the server name indication (SNI).
- **Honour the setting that requires encryption.** When the setting is off, a refused upgrade shows
  a notice and the connection continues in plain text. When the setting is on, the connection
  stops. Ignore an encryption message that the app did not ask for.
- **There is no trust-all certificate handler anywhere.** Do not add one, not even behind a debug
  flag.
- **Encrypted connections need the Netty or SwiftNIO transport.** The fallback Ktor transport has
  no upgrade path, so it cannot be the default where encryption matters.
- **The web build does not use the protocol's encryption upgrade.** It uses `wss` when the page
  loads over https, and plain `ws` otherwise.
- **The hosted server does not run a command that arrives before the handshake**, and it drops
  that client. The reference server still runs the command, so this is stricter on purpose. Keep it
  when you add a message type.
- **Malformed input throws `SerializationException` and nothing else.** The hosted server catches
  only that type, and then it drops the client. The client catches every exception and skips the
  line.
- **Keep the password handling on the wire as the reference defines it.** The client sends a
  digest of the server password, and the server compares it with its own digest. A change to one
  side alone breaks compatibility with other clients and servers.
- **Controlled-room authentication stays bounded.** The operator password must name the room that
  the sender is in. Three failures drop the connection, and a refusal goes only to the sender.
- **Privacy hashing is a wire contract.** The privacy modes for the filename and the file size send
  a truncated hash or a fixed placeholder. The comparison must stay byte-identical to the
  reference.
- **The trusted-domain check runs before any network work.** It matches exact hosts, the `www`
  variant and wildcards that stand for one label. It never matches other subdomains.
- **Sanitise the name of a downloaded subtitle file against path traversal** before you write the
  file.
- **Keep the app free of analytics, crash reporting and advertising components.** The privacy
  policy states this, and the store declarations depend on it.
- **Signing secrets never enter the repository.** They load from `local.properties`, and a release
  build without a keystore fails on purpose. One way past that exists for people who verify a
  published APK: `-PunsignedRelease=true` builds without signing and writes an APK whose name
  carries `unsigned`.

## Supported versions

Only the latest release is supported. Fixes go into the next release, and they are not backported.
