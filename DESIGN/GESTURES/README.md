# Gestures overhaul

Assumes [FOUNDATION](../FOUNDATION/README.md) and [TRANSPORT](../TRANSPORT/README.md).

File: `room/ui/misc/RoomGestureInterceptor.kt` (350).

## What it is

Double tap to seek, long press to seek continuously, swipe on the left half for brightness,
swipe on the right half for volume. The interceptor is always composed; its pointer handlers
attach only while the HUD is hidden, and while the HUD is visible it swallows nothing so taps
reach the chrome.

## What is right already

This file contains a real bug fix that must not be undone (issue #137):

- The drag start edge guard uses actual `systemGestures`, `displayCutout` and `waterfall`
  insets, with an 8 percent of height floor on the top and bottom edges. Left and right use the
  waterfall inset alone.
- All zone maths reads `PointerInputScope.size` and `rememberUpdatedState` insets, never
  values captured at composition time, because the activity handles `configChanges` itself so
  rotation restarts nothing.

Also kept:

- The brightness or volume decision is made per drag event from the live pointer x, at half the
  width.
- Volume spans half the view height; engines that report a maximum above 100 (VLCKit's 200)
  show their raw value with a mark at 100. Brightness snaps to 5 percent steps above 10
  percent and applies only when it moved.
- A tap that reveals the HUD also hides the keyboard.
- Three preferences gate the handlers: the master switch plus one per gesture family, and the
  two family switches live in the control panel so they can be flipped mid-playback.

## What is wrong

**The gestures are invisible.** Nothing on screen says double tap seeks, or that the left half
is brightness. A first time user finds them by accident or not at all.

**Zones are implicit.** Left half and right half is a reasonable convention, but with no
feedback the boundary is discovered by getting it wrong.

**Feedback is a separate readout.** Covered in [STATUS_AND_OSD](../STATUS_AND_OSD/README.md);
the readout moves into the shared notice channel.

**Double tap seeks one jump per tap, with one announcement per tap.** Four quick taps are four
engine seeks and four room announcements, and the readout never says "+40 s".

**Long press waits two seconds, then seeks five times a second, announcing each one.** Two
seconds is long enough to read as broken, and a burst of announcements is exactly what the sync
algorithm does not want.

## The design

### Zones become briefly visible

On the first drag of a session, and whenever a gesture preference changes, the active half gets
a 120 ms wash: a `ground` to transparent horizontal gradient at 18 percent peak from the edge
the finger is on, with the glyph (sun or speaker) at 30 percent opacity in the middle. It fades
as soon as the value readout appears. Enough to teach the zone once without decorating every
drag. The wash carries its own `!isHUDVisible` gate; it must never appear under the chrome.

### Seek accumulation reads as one value

Consecutive double taps inside 900 ms accumulate: the notice shows `+10 s`, `+20 s`, `+30 s`,
and the engine receives one seek when the chain ends, through the dispatcher's single seek
path. The origin is captured on the first tap and held for the chain, and one announcement
goes out at the end, which keeps the pending origin single use. Fewer engine seeks, one sync
announcement instead of four, and a clearer readout.

A ripple free directional mark is drawn at the tap point: two chevrons in `accent`, pointing
the way the seek goes, fading over `quick`.

### Continuous seek shows the track and commits once

Long press starts after 600 ms with a `light` haptic, then moves a ghost playhead along the
transport track at five jumps a second, using the [TRANSPORT](../TRANSPORT/README.md) scrub
bubble to show where release will land. The engine and the room get one seek, on release. No
announcement burst.

### Discoverability outside the gesture

Gestures are also listed in the room's help, and the first time a room is entered the tips
modal mentions the two zones. That is the honest fix for invisibility: a gesture that has no
affordance needs to be taught somewhere, once.

## Invariants

- The two pointer handlers are keyed on their own preference, so flipping one restarts only
  that handler.
- The edge rejected drag sentinel stays: a drag that starts inside the guard band does nothing
  for its whole life.
- Haptics: a tick on each accumulated tap, `light` when the long press engages, `medium` when
  a seek lands.

## Phases

1. Seek accumulation and the single engine seek, on the dispatcher's seek path. A behaviour
   improvement, independent of looks.
2. Continuous seek rebuilt as preview and commit, with the ghost playhead.
3. Directional mark and the first drag zone wash.
4. Readout moved into the shared notice channel.

## Risks

- Phases 1 and 2 change how many seek announcements the room sends. Both must send exactly one
  announcement per chain, through the one seek path, or the phantom seek regression comes
  back.
- The zone wash must be gated on the HUD explicitly. The interceptor is composed at all times;
  only its pointer handlers are conditional, so a wash without its own gate would draw under
  the visible chrome and look like a rendering fault.
