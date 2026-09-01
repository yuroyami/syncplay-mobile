# Room shell overhaul

Assumes [FOUNDATION](../FOUNDATION/README.md).

Files: `room/RoomScreenUI.kt` (348), `room/RoomUiStateManager.kt` (118),
`room/ui/tabs/RoomSectionTabs.kt` (242), `room/ui/tabs/RoomTab.kt` (45),
`room/ui/tabs/RoomUnlockableLayout.kt` (100), `room/ui/misc/RoomBackgoundArtwork.kt` (67, the
misspelling is the file's), plus the platform `EnterRoomMode` / `ExitRoomMode` actuals and the
Android activity's picture in picture and D-pad handling.

## What it is

The frame around the video: which chrome exists, where it sits, when it appears, and how a wide
room and a tall room differ. Everything else in the room hangs off this.

## What is right already

`RoomScreenUI` has no Material imports of its own, and its hard won behaviours survive any
redesign:

- The video surface is composed as soon as the player is ready and held at alpha 0 while there
  is no video, so the engine is never torn down by a recomposition. The alpha modifier comes
  before the background modifier, or the no-video state paints black.
- The HUD is **always composed** and faded, never removed. The chat draft, the GIF panel and
  the seekbar's drag state live inside it. The gesture interceptor is stacked on top of it so
  taps on invisible chrome are swallowed instead of pressing hidden buttons.
- Background tap is two stage: with the keyboard open it only clears focus, otherwise it hides
  the HUD. The keyboard flag is read through `rememberUpdatedState` because the pointer
  coroutine never restarts.
- `EnterRoomMode(isPortrait)` is the single source for orientation and windowing. On Android it
  re-hides the system bars after every composition, because popups un-hide them.
- The room provides its own glass backdrop state over the video layer, because in-window glass
  cannot sample the app-wide capture it lives inside.
- Initial D-pad focus goes to the play key when there is video and to the add button when
  there is not, only in keyboard input mode, after a short delay, wrapped in `runCatching`
  because the requester is unbound during the hand-over. Focus is cleared when the HUD hides.
- Leaving the room without `ExitRoomMode` strands the orientation lock; home and the server
  host screen both call it first.

None of that changes. The redesign is about what the chrome looks like and how it is arranged.

## What is wrong

**Chrome is a pile of independent floating cards.** Tabs, sliding cards, status, bottom bar,
chat, play button and the gesture readout are each positioned separately with their own
paddings (0, 6, 8, 56, 58 and 74dp, plus width fractions of 0.44, 0.28, 0.38 and 0.37 in the
same file). Insets are solved four different ways in the landscape branch alone. There is no
layout contract, so on an unusual aspect ratio they collide, and adding anything means finding
a gap by hand.

**The portrait layout is dead code.** `roomOrientation` has exactly one writer, and it sits
inside an `if (false)` in the overflow menu. `RoomOrientation.PORTRAIT` is unreachable, so the
entire portrait branch and every `isPortrait` path in the sliding cards has never run.

**The card is the only container idea.** Every panel is a rounded glass card at 12dp, tabs at
10dp. With five of them on screen the video is framed by a scatter of rounded rectangles.

**Nothing hides on its own.** Every write to `visibleHUD` is an immediate assignment; there is
no idle timer anywhere. The controls stay up until tapped away, which every other player has
stopped doing.

**The overflow is a menu for four items**: picture in picture (when the engine supports it),
create a managed room and identify as operator (when the server supports them), and leave.

## The design

### One frame, four docks

Replace ad hoc positioning with a single layout that owns four docks. Everything in the room
goes into a dock; nothing positions itself.

| Dock | Wide | Tall |
|---|---|---|
| `top` | status line left, tab rail right | status line full width, rail becomes a bottom row |
| `side` | panels on the right edge, `clamp(320dp, 38%, 420dp)` wide | a sheet rising from the bottom |
| `bottom` | transport bar, above the underlay gradient | same |
| `float` | notices, gesture readout, play key | same |

One `RoomFrame` composable resolves the arrangement from the window's size, and every child
asks only which dock it is in. Phones stay locked to landscape, exactly as today, so the tall
arrangement exists for tablets and desktop windows, not for a phone toggle. The dead toggle, the
`RoomOrientation` enum and the unreachable branch are deleted.

Insets are the frame's job, once per dock: the top dock pads with
`statusBars.union(displayCutout)`, the side dock with the horizontal cutout, the bottom dock
with `safeGestures`. Children never touch insets.

### Panels are edge anchored, not floating cards

Over video, a panel that touches an edge reads as part of the app; a panel floating in the
middle reads as debris. So side and bottom panels lose their outer margin and their outer
corners: rounded only on the inner edge, `radiusPanel`, one hairline rim on that edge, glass
behind. The result is a console face that slides in from the frame, not a card dropped on the
picture.

### Tabs become a rail

`RoomSectionTabs` is a row of icon buttons plus an overflow menu. It becomes a rail: 42dp cells,
hairline separated, the active cell carrying a 2dp `accent` edge on the side facing the content.
Two groups with a rule between them: the panels (people, playlist, settings, lock) and the
actions (picture in picture, managed room, leave). Cells whose feature the engine or server does
not support are absent, not disabled, so the second group is one to four cells long. No menu.
The two managed room actions become one cell that opens the managed room modal, which already
offers both paths.

### Chrome timing is one policy

HUD visibility is written from six places today and never on a timer. One policy object owns
it:

- Auto hide after 3.2s idle, behind a new switch, Hide controls automatically, on by default.
  Any pointer, key or drag resets the clock.
- Never auto hide while a panel is open, while the keyboard is up, while scrubbing, while the
  composer holds unsent text, or while a pointer hovers over chrome on desktop.
- Show on any tap, any D-pad key and any media key, as today.
- Fade `quick`, with the bottom dock sliding 12dp and the side dock 18dp. Reduced motion
  collapses both to an opacity change.

The tab lock keeps its meaning: it replaces the whole HUD subtree, gesture interceptor
included, and its unlock control keeps its own 2.2s auto hide.

### Picture in picture

When `hasEnteredPipMode` is set, the frame renders the video and nothing else. Today the
status and the panels hide but the transport, the play key and the gesture readout do not, and
in a 200dp window they are most of the frame.

### Solo mode

In solo mode the frame drops the status line, the roster, the ready tag and the chat, and shows
the file name alone in the top dock. Today solo is ten separate `if` checks spread over seven
files; the frame makes it one decision.

### Background artwork

`RoomBackgoundArtwork` shows the logo on a gradient when there is no video. Keep the idea,
drop the gradient wash: a single centred mark at 12 percent opacity on `ground`, with the file
name and "Waiting for a file" in `note` type beneath it. The mark still shrinks in picture in
picture, as it does today. A full brand gradient behind an empty room competes with the one
gradient the design spends on the transport.

## Invariants

Beyond the list under "what is right already":

- `onLifecycleStop` pauses unless in picture in picture and marks the room backgrounded; the
  flag is volatile because it is read off the main thread.
- The Android activity handles its own configuration changes, so rotation restarts nothing; any
  inset or size read inside a long lived pointer coroutine must go through
  `rememberUpdatedState` or `size`, never a captured value.
- Entering picture in picture sets the flag before asking the system, and forces the HUD off.
- Room entry force-opens the roster after 600 ms and sets `hasEnteredRoomOnce`, which also
  opens the add media menu the first time. In solo mode the roster open must not happen.
- The three panels are mutually exclusive in the state manager, not in the UI.
- Panels sit at `zIndex(10f)` above the transport and the play key.
- `ROOM_UI_OPACITY` has one consumer, the control panel sheet, clamped to 55 percent.

## Phases

1. `RoomFrame` with the four docks, existing chrome moved into it unchanged. Delete the dead
   portrait branch, the toggle and `RoomOrientation`. Verified by screenshots before and after
   at 16:9, 20:9 and a tablet size.
2. Panel geometry: edge anchoring, inner-only radius, single rim, the width clamp.
3. Tab rail replaces the tab row and the overflow menu.
4. The HUD policy with auto hide and its switch.
5. Picture in picture and solo mode resolved in the frame; artwork simplification.

## Risks

- Phase 1 touches inset handling, which is where the subtle bugs live (issue #137 was a gesture
  and inset interaction). Every inset expression moves into the frame verbatim, and the
  before and after screenshots must include a cutout device.
- Edge anchored panels at 320dp minimum cover more video than 37 percent did on narrow
  phones. Check against the widest panel (shared playlist) on a 16:9 phone.
- Auto hide is new behaviour in an app where the HUD holds the chat. The exceptions above are
  what make it acceptable; the switch is the escape hatch.
