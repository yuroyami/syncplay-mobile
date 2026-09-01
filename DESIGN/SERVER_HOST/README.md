# Server host screen overhaul

Assumes [FOUNDATION](../FOUNDATION/README.md).

Files: `server/ui/ServerHostScreen.kt` (417), `server/ServerViewmodel.kt` (39),
`server/ServerHostSession.kt` (162), and the Android `SyncplayServerService`.

## What it is

Hosting a Syncplay server from the device: pick a port, start it, and hand out an address.
Shows status, the device and public IP, a client count, and a log.

## What is right already

- The server lives in `ServerHostSession`, a process-lifetime singleton with its own scope, and
  the viewmodel deliberately has no `onCleared`, so leaving the screen cannot kill the server.
- The public IP is fetched fire and forget after a successful bind; the screen renders with the
  LAN address alone and fills the public row in when it arrives.
- Both addresses are already selectable text, so long press and copy works.
- Status is already a coloured dot plus a word, in four states: stopped, starting, running,
  error.
- Config is disabled while the server runs; a port outside 1 to 65535 is refused.
- `ExitRoomMode()` is the screen's first call, as on home.

## What is wrong

**It is a stock M3 settings screen**: `Scaffold`, `TopAppBar`, `Card`, `Button`, `Switch`,
`IconButton`, `BottomAppBarDefaults`, with 11, 12, 14, 18 and 20sp literals and hardcoded
amber and green for the status dot.

**The most important thing on the screen is the hardest to use.** Hosting exists so other people
can join you, so the one job is to get an address to them. Today the address is text among
other text, copyable only by long pressing and selecting, with no copy control and no share.

**Status is a word and a dot with no evidence.** Running with zero clients and running with
three read the same; a port already in use is a generic "Failed to start" line in the log.

**Config is six Material controls** (port, password, message of the day, isolate rooms,
disable chat, disable ready) that look like no other setting in the app, and none of it is
persisted: it lives in the singleton's state and dies with the process.

**The log is a scrolling text block** with no severity and no time, so a connection and an
error look identical. It is unbounded, it auto-scrolls even when the reader has scrolled up,
and its merge logic drops server lines after a restart.

**The iOS caveat is one 12sp line** placed after the toggles, and the running state shows a
note saying the server keeps running in the background, which on iOS is the opposite of the
caveat two lines above.

## The design

### The address is the screen

The top of the screen is one block: the joinable address in `display` type, with copy and
share as the two actions beside it on 48dp targets. Below it, in `note` type, which address
this is and one line on what that means.

If both a LAN and a public address exist, they are two rows in the same block, each with its own
copy action, labelled `on this network` and `over the internet`. That is the actual decision a
host has to make, so it is presented as two options rather than two facts. The public row shows
the Progress bar while fetching and disappears quietly if the fetch fails; the LAN row is
always there.

Copy uses the clipboard API the app already has (`LocalClipboard`). Share is one new
`PlatformCallback` method: the system share sheet on Android and iOS, a copy on desktop.

### Status is a state, with evidence

A single status row: a 6dp square (`inkFaint` stopped, `accent` starting, `ok` running, `bad`
error), the state in `label`, and the client count in the `value` column while running. A port
in use gets its own message: "Port 8999 is taken", not a generic failure.

### Config is the console list

Port, password and message of the day become `OpenRow`s with their value in the column; the
three switches become `ToggleRow`s. Same rows as everywhere else, and the six values persist in
the datastore so a host does not retype them.

The start and stop control is the screen's one Primary action while stopped and a Secondary
action with its label in `bad` while running. It is disabled while starting, which also closes
the double start hole below.

### The log is a severity list

Each line is a row: time in the left gutter in `value` `inkFaint`, message in `note`, and a 2dp
left stub coloured by severity (`ok` for a join, `inkFaint` for a leave or info, `bad` for an
error). The list is capped at 500 lines, auto-scroll pauses while the reader has scrolled up,
and a Tag at the top filters to errors only, because when hosting goes wrong the log is the
only diagnostic.

### The iOS caveat becomes a state, not a paragraph

On iOS the status row gains a persistent `warn` line while running: hosting pauses when the app
is in the background. The contradictory "keeps running" note is removed on iOS. Short, always
visible while hosting, rather than a paragraph read once at the top.

## Invariants

- The Android foreground service starts as the last step of a successful start and stops on
  stop; it is `START_NOT_STICKY` on purpose so a killed process does not resurrect a ghost
  notification with no server behind it.
- The collectors job is cancelled on stop so a dead instance's flows are never collected twice.
- The device address is read after a successful bind and is the first non-loopback IPv4, which
  can be a VPN or tethering interface. The screen says which interface it is when it can.

## Bugs fixed on the way

- Start is guarded only against Running, so a second tap during Starting creates a second
  server and engine and orphans the first.
- Session log lines and server log lines share one list, and the merge drops entries by the
  list's total size, so after a restart the fresh server's log is swallowed.
- A failed start leaves the server, engine and collectors assigned; the error state is only
  cleared by a later success.
- The Android notification always says zero clients because its clients extra is never passed.
- The log entry's timestamp is discarded at render.

## Phases

1. The address block with copy and share. Highest value, self contained.
2. Status row, the client count, the port-in-use message, the start guard.
3. Config rows on the settings console components, persisted.
4. Log as a severity list with the cap, the scroll rule and the error filter; the merge fix.
5. Replace `Scaffold` and `TopAppBar` with the screen frame; delete the remaining Material
   components; the iOS state line.

## Risks

- Share is a platform call that does not exist in `PlatformCallback` yet. It needs Android, iOS
  and desktop actuals; desktop share is a copy.
- Public IP comes from an external service and can be absent or slow. The block must never
  block on it, which it does not today and must not start to.
- Persisting the password means it sits in the datastore in plain text, like the join
  password does today. That matches the app's existing decision and is stated in the row's
  detail.
