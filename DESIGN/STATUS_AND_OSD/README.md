# Status and notices overhaul

Assumes [FOUNDATION](../FOUNDATION/README.md).

Files: `room/ui/statinfo/RoomSectionStatusInfo.kt` (114),
`room/ui/misc/RoomGestureValueHud.kt` (261), `room/RoomViewmodel.kt` (the `OSDCategory` enum
and `dispatchOSD`), `uicomponents/DarkGlassPill.kt` (66).

## What it is

Everything the room says to you without being asked: connection state, room name, user count,
the SxxExx badge, sync messages ("X rewound", "X is behind"), and the value readout while you
drag for brightness or volume.

## What is right already

- The status block and the gesture readout already share one skin, `darkGlassPill`: a fixed
  near-black gradient body, a rim lit at the top, a soft shadow, and no blur so it is safe on
  chrome that stays composed while video plays. That skin becomes the `chrome` tier.
- The status block is already hidden in picture in picture.
- The user count falls back to one while connected but unpopulated, so it never flashes zero.
- Same-room notices are filtered for non-operators only when an operator password is set, and
  the file mismatch warning is suppressed when the file is entirely different or not different
  at all.
- Notice duration is a preference (`OSD_DURATION`, default 2 s) and zero disables notices.

## What is wrong

**There is one notice slot.** Every dispatch cancels the previous one, so a same-room "X paused"
silently kills a live warning. Twenty four call sites feed it, and eight files use the
uncategorised overload that no preference gates.

**Four categories look the same.** `SAME_ROOM`, `OTHER_ROOM`, `SLOWDOWN` and `WARNING` are
gated by different preferences and mean genuinely different things, but they render as the same
text in the same place. A slowdown notice and a warning are not the same event.

**Notices vanish in solo mode.** The whole status block, notices included, is inside
`if (!isSoloMode)`, so a solo user seeking to a position or changing an audio track is told
nothing, even though the control panel dispatches those notices.

**Connecting looks like disconnected.** Four connection states exist; the line distinguishes
connected from everything else, so reconnecting reads as failure.

**The badge and the notice fight.** The SxxExx badge renders only while no notice is showing.

**The gesture readout is a separate 261 line implementation** of what is conceptually the same
thing: a transient readout over video, and it is not suppressed in picture in picture.

## The design

### Two channels, clearly separated

| Channel | Content | Placement | Lifetime |
|---|---|---|---|
| Status | room name, connection, user count, badge | `top` dock, leading edge | persistent |
| Notice | sync events, warnings, gesture values, control panel confirmations | `float` dock, top centre, under the top dock | transient |

Persistent facts never move; passing events always appear in the same one place. The `float`
dock's top centre is where the gesture readout already lives, and it keeps clear of the
transport.

### Status is a line, not a block

Room name in `label`, a 6dp connection square (`ok` connected, `accent` connecting or waiting to
reconnect, `bad` disconnected), the user count in `value`, and the SxxExx badge as a Tag,
always, independent of notices. Disconnected adds one `note` line saying so; reconnection stays
automatic, as it is today, with no manual control. In solo mode the frame replaces the line
with the file name.

### Notices are one component with four severities

A single `Notice` composable on the `chrome` tier, differing only by its 2dp left stub:

| Severity | Stub | Used by |
|---|---|---|
| info | `accent` | same room events, control panel confirmations, the uncategorised overload |
| quiet | `inkFaint` | other room events |
| sync | `brandField` | slowdown, rewind, fastforward |
| warn | `bad` | file mismatch, errors |

Notices stack to a maximum of three, newest at the bottom, each living for the duration
preference. When a fourth arrives the oldest goes, except that a `warn` is never dropped to make
room for an `info`. The four preferences keep gating their categories; the zero duration
switch keeps disabling everything. Notices render in solo mode too, because the `float` dock is
not a networked thing.

The gesture readout becomes the same component with a value and a track instead of text: the
existing bar, boost mark and knob drawing moves into a `Notice` variant, updated by state reads
inside the component rather than by dispatching a new notice per drag frame, so brightness and
volume readouts sit where every other transient message sits.

### Picture in picture

The `float` dock as a whole is suppressed when `hasEnteredPipMode` is set, see
[ROOM_SHELL](../ROOM_SHELL/README.md). That covers the notices, the readout and the play key
together, rather than per component.

## Invariants

- The over-video chrome is fixed white on near black, never theme roles; that is now the
  pinned palette of the `chrome` tier.
- The SxxExx match requires a contiguous `s##e##` pattern.
- The chat's own "other room" switch is the same preference that gates `quiet` notices.

## Phases

1. The `Notice` component with four severities and the three deep queue, replacing the single
   slot; the uncategorised overload maps to `info`; solo mode rendering.
2. Status reduced to the single line with the connecting state and the always-on badge.
3. Gesture readout reimplemented as a `Notice` variant. Delete `RoomGestureValueHud.kt` once
   its drawing has moved; its `GestureValue` model moves with it.
4. Dock level suppression in picture in picture.

## Risks

- Notices are how sync problems surface, so losing one is worse than showing an ugly one. The
  queue rule above is what protects a warning, and it is new behaviour: today nothing protects
  anything.
- The gesture readout updates at drag frequency. As a `Notice` it must not allocate per frame;
  the value is a state read inside the component, not a new notice per update.
- Twenty four dispatch sites across the protocol, player and playlist layers keep their
  signatures. Only the renderer changes in phase 1.
