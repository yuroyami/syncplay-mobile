# Security

## Reporting a vulnerability

Do not open a public issue for a security problem.

Use [GitHub's private vulnerability reporting](https://github.com/yuroyami/syncplay-mobile/security/advisories/new)
on this repository. That reaches the maintainer privately and keeps a record.

Tell us what an attacker can do, and how you got there. A rough report that names the real
mechanism is worth more than a polished one that does not. If you are not sure whether something
counts, report it.

You should get a reply within a week. This is a small project with one maintainer, so a fix may
take longer than the reply.

## What this app does with your data

Synkplay talks to a Syncplay server, and optionally to three services: a subtitle search, an
animated image search, and a service that reports your public address when you host a room. It
has no analytics, no crash reporting service and no advertising. See
[PRIVACY_POLICY.md](PRIVACY_POLICY.md).

## Where the risk actually is

Three surfaces take input from people who are not you.

**The server you connect to.** It sends the room's state, the user list, chat, and other people's
filenames. A hostile server can send anything. The client verifies the server's certificate
against the name you typed, refuses commands that arrive before the handshake, and skips a line it
cannot parse rather than dropping the connection.

**Other people in the room.** They supply filenames, chat messages and playlist entries. Playlist
entries can be links, and a link is only opened when its host matches the trusted-domain list,
which is deny by default. The playlist caps how many entries and how many characters it will
accept, and chat respects the server's advertised message limit.

**Anyone who can reach a room you host.** The built-in server is a full Syncplay server. If you
forward a port, anyone on the internet can reach it.

## Known limits, stated plainly

These are real and open. Each has an issue.

- **A hosted server has no encryption.** It always refuses an encryption request, so a room you
  host runs in plain text. Passwords cross as a digest, but usernames, filenames and chat do not.
- **A hosted server admits anyone who reaches the port.** There is no way to remove or block a
  person, and no pairing step.
- **Room passwords are stored in ordinary preferences** and copied into home-screen shortcuts.
  Anything that can read the app's data can read them.
- **Exported logs are not redacted.** A log you attach to a bug report carries the room names,
  usernames, filenames and server addresses from that session.
- **A chat message can make every device in the room fetch a URL the sender chose**, which tells
  the sender who saw the message.
- **The published builds carry a subtitle service key that is public in this repository.** It is
  already readable in the git history, so reporting it again adds nothing. Replacing it needs the
  key moved into a build secret, which has its own issue.

## Rules a change must keep

If you are contributing, these are not optional.

- **Verify the certificate against the name the user typed**, not the address it resolved to. The
  public server collapses its name to an address, so the two are kept apart on purpose. Both
  encrypted transports use the typed name for identity and for the server name indication.
- **Honour the encryption-required preference.** When it is off, a refused upgrade shows a notice
  and continues in plain text. When it is on, the connection aborts. Ignore an unsolicited
  encryption message.
- **There is no trust-all certificate handler anywhere.** Do not add one, not even behind a debug
  flag.
- **Encrypted connections need the Netty or SwiftNIO transport.** The fallback transport has no
  upgrade path, so it cannot be the default where encryption matters.
- **Refuse commands that arrive before the handshake.** This is stricter than the reference
  implementation, deliberately. Keep it when you add a message type.
- **Malformed input throws one exception type**, which is what the skip-a-bad-line path catches.
  Anything else drops the connection.
- **Keep the wire password handling as the reference defines it.** The client sends a digest and
  the server compares its own. Changing it unilaterally breaks interoperability.
- **Controlled-room authentication stays bounded.** The password must name the room the sender is
  in, three failures drop the connection, and a refusal goes only to the sender.
- **Privacy hashing is a wire contract.** The filename and file-size privacy modes send a
  truncated hash or a sentinel, and the comparison must stay byte-identical to the reference.
- **The trusted-domain check runs before any network work**, matches exact hosts, the `www`
  variant and single-label wildcards, and never arbitrary subdomains.
- **Sanitise downloaded subtitle filenames against path traversal** before writing them.
- **Keep the app free of analytics, crash and advertising components.** The privacy policy asserts
  this and the store declarations depend on it.
- **Signing secrets never enter the repository.** They load from `local.properties`, and a release
  build without a keystore fails on purpose.

## Supported versions

The latest release only. Fixes go into the next release rather than being backported.
