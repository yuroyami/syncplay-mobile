# Home screen overhaul

Assumes [FOUNDATION](../FOUNDATION/README.md).

Files: `home/HomeScreen.kt` (667), `home/components/HomeTextField.kt` (231),
`home/components/HomeTopBar.kt` (195), `home/components/HomeEngineWheel.kt` (443),
`home/components/PopupAPropos.kt` (267), `home/components/PopupDidYaKnow.kt` (88),
`home/HomeViewmodel.kt` (50), `home/JoinConfig.kt` (61).

## What it is

The first thing anyone sees. A join form: username, room, server, port, password, an engine
picker, and a Join button. It is also, accidentally, where global settings live, and the only
way into watching alone is a button inside the About popup.

## What is right already

- `ExitRoomMode()` is the screen's first call. Without it the app stays locked to landscape
  after leaving a room.
- The form renders with defaults at once, then every field re-keys on the saved config when it
  arrives (250 ms timeout). The text field keeps its callbacks in `rememberUpdatedState` because
  of that re-key; a stale lambda would write into the orphaned state.
- Focus moves field to field through explicit `onNext` jumps, and the clear glyph is
  `canFocus = false`, because default traversal lands on the clear glyph and the buttons and
  flashes the keyboard. Initial focus is requested only in keyboard input mode, so touch users
  never get a keyboard on arrival.
- Switching the server to Official resets a non-official port and clears the password;
  switching to Custom blanks both. An old saved config still naming the official server's raw
  address is read as the official server.
- A saved engine this build no longer ships is replaced once with the platform default;
  unavailable engines are refused with a message and never written.
- The engine wheel: corrections are cancel-previous jobs, settle detection needs a real
  scrolling to not scrolling edge, a mouse wheel gets a hand rolled detent, unavailable engines
  are desaturated, one badge per engine, and the fade mask needs `CompositingStrategy.Offscreen`.
- Username and room are stripped of backslashes, trimmed and capped (149 and 34) before a join.
- The tips popup waits a second, and only shows when no room has been entered this session and
  the tips switch is on.
- A pending shortcut (iOS, desktop command line) joins once, on arrival.

## What is wrong

**It is a Material `Scaffold` wearing Synkplay's colours.** The top bar is a `Card` holding a
`ListItem`, with the global settings grid expanding inside it; the server picker is an
`ExposedDropdownMenu` with the crash cap; three of the four labels carry a `PlainTooltip`; the
Join row is two `SplitButtonDefaults` halves; errors are a `SnackbarHost`. Every structural
decision on this screen was made by Material, so the screen reads as a generic Android form
that happens to be purple.

**The form is a stack of 75% width columns with centred labels over centred text.** Centred
labels and centred input give the eye no left edge to run down, which is the one thing a form
needs. The 75% cap also means the form never adapts: it is 75% of a 360dp phone and 75% of a
1400dp desktop window, so on desktop it is a 1000dp wide text field for a six character room
name.

**Settings expand inside the top bar.** The bar reports its resting height so the page below
does not re-lay-out when the drawer opens, and paints a 96% wash because the bar is translucent
glass. That whole mechanism exists only because settings have no screen. Covered in
[PREF_SYSTEM](../PREF_SYSTEM/README.md); from here the change is that the gear navigates.

**Help hides behind a 15dp target** on the screen where a first time user most needs it.

**Watching alone is invisible.** Solo mode is a button inside About, which means the second
most useful thing the app does is undiscoverable.

**The server block is a menu of three rows.** Official, Custom and Host my own sit in a
dropdown, with the five official ports as a separate tap row under the field. The row of ports
is right; the menu around it is not, and Host my own is navigation to another screen dressed
as a choice.

**Type comes from Material roles plus 18sp on Join, 12sp on the port cells, 11sp in the menu,
and 26f, 13sp, 11sp and 10sp inside About.**

## The design

### A form with one left edge

Drop the centred labels, the centred text and the 75% cap. The form becomes a single column with
a real measure:

- Column width: `min(available - 2 * gutter, 420dp)`, left aligned, centred in the viewport.
- Label above field, both flush left, `label` type in `inkDim`.
- Field rows on the 6dp ladder: label block 18dp, field 42dp, `gap` between fields and
  `gutter` between groups. The engine wheel keeps its 96dp height as its own group.

A 420dp cap is the point where a text field stops looking like a mistake on a wide screen.

### Fields are hairline, not boxed

`HomeTextField` today is a boxed field with a solid animated border and centred text. Replace
with the FOUNDATION field: a single hairline underline that thickens to 2dp and takes `accent`
on focus, the icon in the left gutter at 20dp, text starting at the left edge, and the clear
glyph on its own 48dp target. The `onNext` chain, the non-focusable clear glyph and the
`rememberUpdatedState` callbacks move into the FOUNDATION field so every field in the app gets
them. The theme creator and the settings rows use the same field once their own overhauls land;
until then `HomeTextField` stays for them alone.

### Help sits in the form, not behind a tooltip

Kill the tooltips. Each field gets one line of `note` type beneath it, shown only when the field
is empty or focused. First time users see the guidance without hunting; returning users see a
clean form because their fields are filled. The same line turns `bad` and carries the message
when validation fails, which replaces the snackbar for field errors.

### Server picking is a segmented selector

`Official | Custom` as a two cell Segmented control. When Official is active, the five port
cells appear under it as a second Segmented row, which is the control that already exists and
is better than a stepper: one tap, every option visible. When Custom is active, host and port
fields appear. Host my own becomes a secondary action row under the block, because it navigates
to the server host screen and is not a server choice.

### Join and Watch alone

Join is the screen's one gradient: a Primary action, full form width, 48dp, with the
save-as-shortcut glyph inside its right edge on its own target. Below it, Watch alone as a
Secondary action. That is the whole fix for solo mode's discoverability, and About keeps its
link too.

### The engine wheel keeps its personality

`HomeEngineWheel` is the most characterful thing on the screen and stays. It picks up the
FOUNDATION Tag for its badges (Unavailable, Experimental, Default, System) and the two type
roles it reads from Material move onto `label` and `value`. Its mechanics do not change.

### The top bar becomes a bar

Logo and wordmark left, theme and settings glyphs right, one hairline underneath, `bar` tall
plus the status inset. The theme glyph opens the theme picker panel; the settings glyph
navigates to `Screen.Settings`. No `Scaffold`, no `Card`, no `ListItem`, no growing drawer, no
resting height, no wash. `SettingGridState` goes with the drawer, once the room's settings card
(its other consumer) has moved to the console panel.

### Feedback

The snackbar becomes the FOUNDATION Notice at the bottom edge for messages that are not about a
field (an unavailable engine, a saved shortcut). Field errors go inline as above.

## Invariants

Everything under "what is right already", plus:

- The whole form scrolls with the keyboard (`imePadding` then `verticalScroll`) and a tap on
  the background clears focus. The order of those modifiers matters.
- A join from the shortcut path must not crash on a blank or non-numeric port; the two
  validation paths become one function.
- `hasEnteredRoomOnce` is shared with the room, which sets it after 600 ms.

## Bugs fixed on the way

- The shortcut path strips and trims but skips the username and room length caps.
- The tips popup never advances its index, so it shows one random tip and nothing else.
- On iOS the shortcut is encoded as a joined string and decoded as JSON, so it never parses.
- The Android shortcut intent defaults a missing port to 80.
- The form's `SpaceAround` arrangement collapses once content exceeds the viewport.

## Phases

1. Form skeleton: measure, left edge, 6dp ladder, inline help, Watch alone. No component
   changes yet.
2. Field, segmented selector, Join and the port cells on FOUNDATION controls. Delete the
   dropdown, the tooltips and the split button halves.
3. Top bar rebuilt without `Scaffold`; settings glyph navigates. Delete `onRestingHeight`, the
   wash and, once the room card has moved, `SettingGridState`.
4. Snackbar replaced with the Notice; the four bugs above.

## Risks

- The 420dp cap changes the look on tablets most. Check it at 600dp and 840dp before
  committing.
- Removing tooltips moves help into the layout, which costs vertical space on short screens. The
  empty-or-focused rule is what keeps that cost near zero in practice; verify on a 640dp tall
  device with the keyboard open.
- `HomeTextField` has two other consumers with their own parameters (the theme creator's
  dropdown anchor, the settings row's height). It cannot be deleted until both have moved.
