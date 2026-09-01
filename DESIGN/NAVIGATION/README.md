# Navigation and screen frame overhaul

Assumes [FOUNDATION](../FOUNDATION/README.md).

Files: `AdamScreen.kt` (162), `Screen.kt` (38), `SyncplayViewmodel.kt` (the backstack),
`uicomponents/ScreenDimensions.kt` (34).

## What it is

Navigation3 `NavDisplay` over a `SnapshotStateList<Screen>` owned by `SyncplayViewmodel`. Four
destinations: `Home`, `Room(joinConfig?)`, `ThemeCreator(themeToEdit?)`, `ServerHost`.

## What is right already

The model is good. A serializable sealed `Screen`, a real backstack, per screen ViewModels
through `viewModelFactory`, and CompositionLocals for the graph. Nothing about that changes.

Two details are load bearing: the two entry decorators are applied in the order saveable state
holder then ViewModel store, and the room's ViewModel is reached from the activity through a
weak reference so the global ViewModel can never keep a room alive.

## What is wrong

**Settings are not a destination.** They are a drawer inside the home top bar and a card inside
the room. That is the root cause of the preferences layout problems and is fixed in
[PREF_SYSTEM](../PREF_SYSTEM/README.md); from here the change is one new `Screen` member.

**Every screen builds its own chrome.** The theme creator and the server host each construct a
`Scaffold` with a `TopAppBar`; home constructs a `Scaffold` with its own glass bar. Each has its
own paddings and insets, so a title bar is a slightly different height and a back arrow a
slightly different position on each.

**System back does nothing.** `NavDisplay` is given `onBack = {}`. The only place that pops the
backstack on its own is the server host screen's back arrow. A back press on Android is
consumed and ignored everywhere outside a dialog; iOS has no back gesture at all; desktop has
no Escape.

**Transitions are default.** `NavDisplay` moves between screens with whatever it does by
default, so entering a room feels the same as opening the theme creator, when one is a mode
change and the other is a push.

**Insets are solved per screen.** The room got this right the hard way (immersive, so
`statusBars` reports 0, top chrome pads with `statusBars.union(displayCutout)`). Other screens
each solved it separately.

**There are no width classes.** The global settings grid is a fixed two columns at every window
width; the room uses fractions of the width; nothing knows whether it is on a phone, a tablet or
a desktop window.

## The design

### One screen frame

A `ScreenFrame` composable that owns what every non room screen shares:

- A `bar` (54dp) plus the status inset: back glyph when the backstack is deeper than one, title
  in `display` type, and up to two trailing glyph buttons.
- One hairline under the bar, appearing only once content has scrolled beneath it.
- Content inset handling in one place, including cutout and keyboard.
- The `flat` surface tier, since these screens are not over video, and the desktop scrollbar
  on its scrolling body.

Home, settings, theme creator and server host all use it. The room does not, because it has its
own frame (see [ROOM_SHELL](../ROOM_SHELL/README.md)), and that difference is meaningful: the
room is a mode, everything else is a page. The code says so in a comment on `RoomFrame`, so
nobody later "fixes" the inconsistency and breaks the immersive inset handling.

### Back means back

`NavDisplay.onBack` pops the backstack when there is more than one entry. The room intercepts
it: a back press in the room opens the `ask` modal "Leave the room?", so a stray press cannot
drop a session. On desktop, Escape pops a page when no modal or panel is open; in the room it
follows the [DESKTOP_AND_KEYBOARD](../DESKTOP_AND_KEYBOARD/README.md) order. On iOS there is
no system back; the frame's back glyph is the way out and is always present on a pushed page.

### Transitions say what kind of move happened

| Move | Transition |
|---|---|
| Push (home to settings, settings to a category) | content slides 24dp from the trailing edge, fades in over `move` |
| Pop | the reverse, with the outgoing screen sliding back |
| Entering the room | crossfade to `ground` over `move`, then the room fades up. It is a mode change, not a page |
| Leaving the room | the reverse |

Reduced motion collapses all four to a crossfade.

### `Screen` gains one member

```kotlin
@Serializable data class Settings(val categoryKey: String? = null) : Screen
```

Which makes a settings category deep linkable, gives system back the correct behaviour, and lets
the in-room panel and the global screen share one content composable.

### Width classes live here

`ScreenDimensions` becomes the single source for the three width classes used across the app
(compact under 480dp, medium 480 to 839dp, expanded 840dp and up), exposed as a
CompositionLocal so no surface computes its own thresholds. Its dropdown height cap goes with
the dropdowns, see [POPUPS](../POPUPS/README.md).

## Invariants

- Seven CompositionLocals in `AdamScreen` have no default and throw outside the tree. Any
  composable rendered on its own, in the harness or a preview, must be given them; the harness
  has a helper for exactly that.
- ViewModels are keyed by fixed strings, so two entries of the same screen share one ViewModel.
  No screen is pushed twice today and the frame does not change that.

## Phases

1. `ScreenFrame`, with home moved onto it first.
2. `Screen.Settings` and the settings destination.
3. Back handling: the pop, the room's ask, desktop Escape.
4. Theme creator and server host moved onto the frame; their `Scaffold`s deleted.
5. Transitions.
6. Width classes centralised in `ScreenDimensions`.

## Risks

- Insets are where this kind of refactor breaks. Every screen must be checked on a cutout device
  in both orientations, with the keyboard open, before and after.
- Making back work is a behaviour change on Android: today a back press in the room is silently
  eaten. The ask modal is what keeps a stray press harmless.
