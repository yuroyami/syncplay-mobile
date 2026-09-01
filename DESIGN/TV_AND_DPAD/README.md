# TV and D-pad overhaul

Assumes [FOUNDATION](../FOUNDATION/README.md) and shares its key map with
[DESKTOP_AND_KEYBOARD](../DESKTOP_AND_KEYBOARD/README.md).

Files: `uicomponents/TvFocus.kt` (67), thirteen `tvFocusable` call sites across twelve files
(every `FlexibleIcon` is one of them, so every room glyph is a focus target), the seekbar and
engine wheel key handlers, `LocalRoomInitialFocus` in `room/RoomScreenUI.kt`, and the
Android activity's D-pad interception.

## What it is

Android TV and any keyboard or remote driven use. `Modifier.tvFocusable` joins an element to the
focus tree and draws a focus indicator.

## What is right already

- The indicator uses the theme's gradient, so a focused element is marked with the app's own
  brand rather than a system highlight. That instinct is correct and stays.
- The seekbar turns left and right into real jump seeks and ignores key up; the engine wheel
  is one focus stop whose left and right step the selection.
- The Android activity intercepts D-pad keys only while the HUD is hidden and a video is loaded,
  and any D-pad key brings the HUD back; media keys work unconditionally.
- Initial room focus goes to the play key or the add button, only in keyboard input mode.
- `addFocusable = false` exists because most hosts are already focusable (an icon button, a
  clickable), and a second `focusable()` creates a duplicate stop the remote passes through
  twice.

## What is wrong

**The indicator scales the element**, by 1.04 by default and by 1.12 on every room glyph. In a
dense list or a tight panel a scaled element overlaps its neighbours, and inside a clipped
container it is cropped, so the focused item can look broken rather than focused. Scaling also
cannot be seen at all on an element that already fills its container.

**Focus order is implicit.** It follows composition order, which is right on the home form and
wrong in the room, where the visual arrangement is docks rather than a single column.

**Only some things are focusable.** `tvFocusable` is applied by hand, so whether a control can be
reached by remote depends on whether someone remembered. Settings rows, both category cards and
the playlist rows pass `addFocusable = false` without a focusable host, so they draw a ring if
something else focuses them and are otherwise unreachable. No popup has any focus handling.

**Disabling the modifier changes its slot shape.** `enabled = false` returns before the
modifier's `remember`, so flipping it at runtime loses focus state.

**There is no focus context.** On a 10 foot screen, one gradient border on a 42dp row is a small
signal.

## The design

### The indicator stops scaling and starts filling

Focus is shown by three things at once, none of which change layout:

- A 2dp `brandField` border inset by 1dp, so it draws inside the bounds and cannot be clipped.
- The row's ground lifting to `accent` at 12 percent.
- The label moving from `inkDim` to `ink`.

Nothing moves and nothing resizes, so a focused row in a scrolling list behaves. The modifier
keeps its `remember` in every branch and toggles behaviour with a flag, not an early return.

### Focus order follows the docks

The room declares an explicit focus order across docks: transport, then the rail, then the open
panel, then chat. Within a dock, order is visual. That is expressed as focus groups on the dock
containers rather than as per element ordering, so adding a control does not require rewiring.

### Focusable by default, not by memory

Every FOUNDATION control (rocker, stepper, scrub track, field, glyph button, row, tag) is
focusable as part of its own definition, with the correct key handling: left and right adjust a
stepper or a scrub track, centre activates, and long press maps to the menu key. Surfaces stop
calling `tvFocusable` by hand, which is what makes coverage complete rather than patchy, and
`addFocusable` disappears because the control is the host.

The key map is the one in [DESKTOP_AND_KEYBOARD](../DESKTOP_AND_KEYBOARD/README.md): centre is
Space, the arrows are the arrows, back is Escape. One map, two input devices.

### Modals keep focus and give it a start

A modal traps focus inside its window and gives initial focus to its first action in keyboard
input mode, so a remote can answer a question without hunting. See
[POPUPS](../POPUPS/README.md).

### A TV density, not a TV layout

On a 10 foot screen the app does not need a different layout, it needs a different density. The
width classes already exist; TV adds a flag that steps the whole ladder up one unit: `row` to
54dp, `rowTall` to 66dp, `bar` to 60dp, every target to 54dp, and type one step, without changing
any arrangement. The distinction between a plain row and a track row survives the step. One
flag, no second layout to maintain.

## Phases

1. Indicator rebuilt without scaling, with the ground lift and the label lift. This alone fixes
   the clipping and overlap.
2. Focusability moved into the FOUNDATION controls with their key handling; `addFocusable`
   retired as call sites move.
3. Dock focus groups in the room; modal focus.
4. TV density flag.

## Risks

- Removing the scale changes how obvious focus is at distance. The border plus ground lift plus
  label brightening has to be checked on an actual TV at distance, not on a monitor.
- Key handling on a scrub track competes with the room's existing D-pad seek. The seekbar
  already handles D-pad seek, so the scrub track's key handling must defer to its host when the
  host claims it.
