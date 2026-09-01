# Foundation

The shared design system every other `DESIGN/` folder builds on. Read this first. The other
folders assume it and only describe what is specific to their surface.

## Why this exists

Synkplay currently borrows Material 3's look, its dimensions, and its components. That is the
root of most of the interface problems: M3 is a design system for phone operating systems, and
Synkplay is a media player that people mostly use in a dark room, in landscape, over video,
with a remote or a thumb.

M3's defaults actively fight that:

- Its rows are 48 to 64dp tall because it assumes an OS settings list with plenty of screen.
  Synkplay has a 300dp panel floating over video.
- Its components (pill switches, fat slider thumbs, elevated cards, dialogs) carry a visual
  identity that is not Synkplay's, and that identity shows up in screenshots as "generic
  Android app".
- Its type roles bake in sizes tuned for that same OS context.

So: keep Compose, keep the `MaterialTheme` colour scheme as a **colour source** only (it is how
the user's own theme picker works, and that is a real feature), and replace everything else.

## The rule

No Material 3 component appears in Synkplay's UI. No `Switch`, `Slider`, `ListItem`, `Card`,
`AlertDialog`, `OutlinedTextField`, `TextField`, `Button`, `TextButton`, `IconButton`, `Chip`,
`FAB`, `NavigationBar`, `TopAppBar`, `Scaffold`, `ModalBottomSheet`, `DropdownMenu`,
`RadioButton`, `Checkbox`, `CircularProgressIndicator`, `HorizontalDivider`, `Tooltip`,
`Snackbar`. No `ripple`. No `MaterialTheme.typography` role used directly. No M3 dimension
(48, 56, 64, 8, 16, 24) chosen because M3 chose it.

What is allowed, because it carries no Material form:

- `MaterialTheme.colorScheme`, read through the palette below and nowhere else.
- `androidx.compose.material3.Text` and `Icon`. They are thin wrappers over `BasicText` and
  `Image` that pick up the current content colour; they draw nothing of their own.
- `Icons.*` glyphs from `material-icons-extended`, until the drawn set replaces them.

The census as it stands in `commonMain` (150 Kotlin files, 45 of them importing something from
`material3`): `Card` in 10 files, `Button` 9, `DropdownMenu` 8, `TextButton` 7, `AlertDialog`
5, `HorizontalDivider` 5, `IconButton` 5, `Surface` 5, `TextField` 4, `Switch` 3, `Scaffold` 3,
`Slider` 2, `ModalBottomSheet` 2, `TopAppBar` 2. 36 direct uses of `MaterialTheme.typography`
roles. 13 distinct hardcoded text sizes from 8sp to 20sp, the two most common being 14sp (28
places) and 11sp (14 places). 142 distinct Material icons, 54 of them on preference rows alone,
plus the category and engine icons.

## The reference world

A design system needs a place to take its cues from. Synkplay's is its own subject: a media
player and a mixing desk. Transport rows. Scrub tracks. Playheads. Tick marks. An inspector
panel where every parameter's current value lines up in a column you can read down.

That world gives real answers to real questions, instead of arbitrary difference for its own
sake:

| Question | Answer from the reference world |
|---|---|
| How does a boolean look? | A hardware rocker, hard edged, sitting left or right |
| How does a numeric value look? | A scrub track with a playhead, like the app's own seekbar |
| Where does the current value go? | A fixed right hand column, so values align down the panel |
| How are things separated? | Hairlines and gutters, like a console face. Not cards, not elevation |
| What does "selected" look like? | A filled bar or an accent rule, not a pill or a ripple |
| What does the one big button look like? | A square transport key, not a floating disc |

## Tokens

### Space: 6dp unit

M3 uses 8dp. Synkplay uses **6dp**. The reason is not novelty: the whole interface problem is
density, and a 6dp unit gives a finer ladder, so nothing lands on 48 / 56 / 64 by accident.

| Token | Value | Use |
|---|---:|---|
| `u` | 6dp | the unit |
| `rowCompact` | 36dp (6u) | dense read-only row, notice, tag row |
| `row` | 42dp (7u) | standard row: settings, roster, playlist, rail cell, panel header, composer |
| `rowTall` | 54dp (9u) | row carrying a track or two lines; the modal action row |
| `bar` | 54dp (9u) | the screen frame's title bar, plus the status inset |
| `hero` | 60dp (10u) | the one big control: play |
| `gutter` | 18dp (3u) | page and panel gutter |
| `gap` | 12dp (2u) | between related things |
| `gapTight` | 6dp (1u) | inside a control |
| `valueCol` | 90dp (15u) | the aligned value column |
| `groupHead` | 30dp (5u) | group heading block |
| `glyph` | 20dp | icon box inside rows and buttons |
| `glyphLarge` | 24dp | icon box in the transport row |
| `hair` | 1dp | every rule and border |
| `touchMin` | 48dp | minimum hit target for anything that stands on its own |

**Touch targets.** Visual size and touch size are separate, and the platform minimums are real:
Android's accessibility checker flags targets under 48dp and iOS asks for 44pt. The rules:

- A row's target is the row itself: full width, 42dp tall. A full-width strip is easy to hit,
  and rows are the one place the ladder stays at 42dp on purpose.
- A control standing on its own (a rocker, a swatch, a chevron, a stepper arrow, a glyph
  button) gets a target at least 48dp wide and as tall as its row. Outside a row, 48 x 48dp.
  The target is expanded past the visual bounds; adjacent targets may not overlap.
- TV density steps the whole ladder up one unit: `row` to 54dp, `rowTall` to 66dp, `bar` to
  60dp, every target to 54dp, so a plain row and a track row stay distinct. See
  [TV_AND_DPAD](../TV_AND_DPAD/README.md).

### Width classes

Three classes from available width, never from which host is drawing. Owned by
[NAVIGATION](../NAVIGATION/README.md) and exposed as one CompositionLocal.

| Class | Width | Meaning |
|---|---|---|
| compact | under 480dp | one column, sheets instead of dialogs, no row icons |
| medium | 480 to 839dp | one column capped at 560dp, centred |
| expanded | 840dp and up | two panes where a surface has two |

### Radius: near square

M3 rounds everything. Synkplay's shapes are close to square, because console faces are.

| Token | Value | Use |
|---|---:|---|
| `radiusNone` | 0dp | full bleed surfaces, rules |
| `radiusTight` | 2dp | swatches, knobs, small fills |
| `radiusControl` | 3dp | rockers, steppers, fields, buttons, tags |
| `radiusPanel` | 8dp | sheets, floating panels, notices, the play key |

Nothing is a capsule. A fully rounded pill reads as Material and is not used, which retires the
capsule shape of the current gesture readout and status pill (their body and rim survive, see
Surfaces below).

### Type: one scale, five roles

Not `titleMedium` and friends. Synkplay's own scale, with only two sizes ever appearing on a
single row so a row reads as one line of information.

| Role | Size | Weight | Tracking | Line | Use |
|---|---:|---|---:|---:|---|
| `display` | 24sp | 700 | -0.5 | 28sp | screen titles, the wordmark |
| `label` | 15sp | 500 | -0.1 | 19sp | the name of a thing: row labels, buttons, headers |
| `value` | 13sp | 500 | +0.3, tabular | 16sp | current values, counts, timecodes |
| `group` | 11sp | 600 | +1.5, uppercase | 14sp | group headings, eyebrows; never body content |
| `note` | 13sp | 400 | 0 | 19sp | explanations, help text, chat body |

- `value` is tabular everywhere. Values live in a column and must scan vertically, and
  timecodes must not jitter as digits change.
- Sizes are in sp, so the system font size scales them. Layouts use minimum heights, never
  fixed ones, so a 130 percent font scale wraps a row instead of clipping it.
- Nothing below 11sp exists, and 11sp is `group` only. The one dynamic size is the chat font
  size preference, which the user sets.
- Two families: Lexend for all five roles, Directive4 for the wordmark. See
  [TEXT_AND_ICONS](../TEXT_AND_ICONS/README.md).

### Colour

Colour still comes from the user's active theme, because the theme picker is a real feature.
What changes is that surfaces read theme colours through a **local semantic palette**, never
through M3 role names, so the source can be re-pointed without touching components.

| Token | Source | Use |
|---|---|---|
| `ground` | scheme background | the page |
| `panel` | scheme surfaceContainerHigh | panel body colour, the glass tint |
| `ink` / `inkDim` / `inkFaint` | scheme onSurface at 100 / 62 / 42 percent | text and glyphs |
| `rule` | scheme outlineVariant | every hairline |
| `trackOff` | `ink` at 12 percent | the unfilled part of a track, an off rocker |
| `accent` | the theme's first seed | selection, focus, the on state, links |
| `brandField` | the theme's three seeds, in order | the reserved gradient |
| `ok` | fixed `0xFF6ECB5A` | ready, connected, running |
| `warn` | the theme's third seed | caution, the iOS hosting notice |
| `bad` | fixed `0xFFE85455` | errors, disconnected, destructive actions |
| `disabled` | `ink` at 38 percent | any disabled control or label |

`ok` and `bad` are fixed because readiness green and error red must mean the same thing in
every theme; a theme whose accent is green cannot be allowed to make "not ready" look ready.

**Over video the palette is pinned.** Inside the room, `ground` becomes near black
(`0xFF0E0E12`), `ink` becomes white, `rule` becomes white at 10 percent, whatever the theme
says. Video is the ground there and it is usually dark; a light theme's white panels over a film
are unreadable and look like a different app. The theme still supplies `accent`, `brandField`,
`ok`, `warn` and `bad`, so the room is recognisably the user's theme without being lit by it.
This is the rule the current over-video chrome already follows; it is now a palette variant
instead of a convention.

**The gradient budget.** `brandField` marks the one primary action on a screen: Join on the
home screen, Play in the room. The scrub track's played fill is the same gradient because the
track and the play key are one instrument, the transport, and together they are the room's
single gradient moment. Two transient uses are allowed on top because only one of each exists
at a time and neither lasts: the focus ring, and the stub of a sync notice. The gradient never
decorates a container edge, never fills body text, and never sits on two resting controls at
once. The logo and wordmark draw from the same three seeds and do not count against the budget;
they are the brand, not chrome.

### Motion

Over video, motion competes with content. Rules:

- Two durations only: `quick` 120ms for state changes, `move` 220ms for things that travel.
- One easing for entrances and exits, the standard curve (0.2, 0, 0, 1). One spring for anything
  the finger is dragging: medium stiffness, no bounce.
- Three hold times, each named once: a notice holds for the notice duration preference
  (default 2s), the HUD hides after 3.2s idle, and a fading chat line holds for the fading
  duration preference (default 3s).
- No ripple. Press feedback is an immediate opacity drop to 70 percent and a 1dp inset, which
  reads faster and does not paint a Material circle over a dark panel.
- Everything respects reduced motion by collapsing to an instant state change or a crossfade.
  Nothing reads a system setting today; the design adds `expect fun reducedMotion(): Boolean`
  (iOS `isReduceMotionEnabled`, Android animator duration scale, desktop false) plus a Reduce
  motion switch in settings that forces it on.

### Feedback

The app has one haptic call today, `performHapticFeedback()`, and eight preferences gating room
events (joined, left, chat, paused, played, seeked, playlist, connection). Those stay. Controls
add a small vocabulary on top, mapped to that one call until the platforms expose strengths:

| Name | When |
|---|---|
| `tick` | a stepper step, a chapter mark under the playhead, an engine wheel detent (exists) |
| `light` | a rocker flip, a tag toggled |
| `medium` | a seek landed, ready toggled, a modal's confirming action |

Control haptics are on by default and gated by one new switch, Haptics on controls, separate
from the eight room event switches.

### Iconography

The app uses 142 distinct Material icons, 60 of them in settings alone, all at Material's
optical weight. They read as Android system icons.

Move to a single drawn set at a **1.5dp stroke, square terminals, 20dp box**, so the icons sit
with the hairlines and near square shapes rather than against them. Staged, because it is a
sizeable job:

1. Define the box and stroke, and draw every existing Material glyph at the same 20dp box so
   they at least sit consistently.
2. Replace per surface as each surface is overhauled. The first wave is the room's
   transport and rail, the glyphs seen most: play, pause, rewind, forward, skip, ready, add,
   close, back, chevron left and right and down, search, send, gif, settings, theme, playlist,
   people, lock, picture in picture, leave, chapters, subtitles, audio, aspect, undo, copy,
   share, and the connection square.
3. Drop the `material-icons-extended` dependency once the last one is replaced.

Icons are used where they discriminate. Inside a group where every row would take the same
icon, they are dropped: eight identical vibration icons down a haptics list carry no
information and cost 42dp of width per row.

## Controls

The control vocabulary, defined once here and reused everywhere. Each is drawn, not adopted,
and each carries its semantics and its focus handling as part of its definition (see
[ACCESSIBILITY](../ACCESSIBILITY/README.md) and [TV_AND_DPAD](../TV_AND_DPAD/README.md)).

| Control | Form |
|---|---|
| Rocker | 38 x 20dp, `radiusControl`, hairline border, 15 x 14dp knob sitting left or right. Filled `accent` at 28 percent when on, `trackOff` when off |
| Scrub track | 4dp track in `trackOff`, `brandField` fill, 3 x 16dp playhead bar in `ink`, optional 1dp tick marks. The seekbar is this control at 18dp tall with a 48dp target |
| Stepper | `‹ value ›` in place, in the value column, for option sets under five. Arrows are chevrons with 48dp targets |
| Swatch | 22 x 16dp rectangle with a hairline. A rectangle, because a circle is chip language |
| Chevron | two 1.4dp hairlines, drawn, not an icon font glyph |
| Field | a single hairline underline that thickens to 2dp and takes `accent` on focus. No box. Optional leading glyph in the gutter, optional trailing clear glyph with its own target |
| Rule | 1dp full bleed, `rule` colour |
| Row | full width, `row` tall, `gutter` padding, label left, value column right, control after it. Hover, focus, pressed and selected states drawn by the row, not by its children |
| Glyph button | a 20dp glyph in a 48dp target, no background at rest, press feedback only |
| Segmented | two to four hairline cells in one `radiusControl` frame, the active cell filled `accent` at 16 percent with an `accent` bottom edge |
| Tag | a hairline rectangle, `radiusControl`, text in `value` type, 22dp tall. Carries state words and badges: Experimental, Default, S01E02 |
| Progress | a 2dp bar along the top rim of its container, `accent` on `trackOff`; determinate or a 30 percent segment sweeping when indeterminate. No spinners |
| Handle | a 36 x 3dp `inkFaint` bar centred in a 24dp strip at the top of a draggable sheet |
| Primary action | 48dp tall, `radiusControl`, filled `brandField`, label in `ground`. Once per screen |
| Secondary action | 42dp tall, `radiusControl`, hairline border, label in `ink` |
| Destructive | a 2dp left edge stripe in `bad`, label in `bad`. Never a red filled button |
| Notice | the transient message component, defined in [STATUS_AND_OSD](../STATUS_AND_OSD/README.md). On screens that are not the room it is the toast: same component, bottom edge, `flat` colours |

### Control states

| State | Treatment |
|---|---|
| rest | as drawn above |
| hover (pointer only) | ground lifted by `ink` at 6 percent |
| pressed | opacity 70 percent, 1dp inset, no animation in |
| focused | 2dp `brandField` border inset by 1dp, ground lifted by `accent` at 12 percent, label from `inkDim` to `ink`. Nothing scales |
| selected | a 2dp `accent` edge on the side facing the content, ground lifted by `accent` at 8 percent |
| disabled | everything at `disabled`, no hover, no focus |
| loading | the Progress bar on the container's top rim; the control itself does not change |

## Surfaces and the glass system

The app already has a glass material (`GlassSurface.kt`, `GlassComponents.kt`,
`DarkGlassPill.kt`) with a `DISABLE_FROSTED_GLASS` escape hatch. Glass is the app's actual
visual identity and it stays, with rules. The rules that already exist in code are load bearing
and are restated in [GLASS_SURFACES](../GLASS_SURFACES/README.md); the short version:

| Tier | Where | Treatment |
|---|---|---|
| `flat` | screens that are not over video: home, settings, theme creator, server host | solid `ground`, hairline separation, no blur, no cost |
| `panel` | things that open over video: docks, sheets, menus, modals | real blur behind a low tint, `radiusPanel` on the edges facing content, one hairline rim lit along the top |
| `chrome` | things that are always composed over video: the status line, notices, the gesture readout, the transport underlay | no blur. A fixed near-black gradient body, the same lit rim, a soft shadow. This is the current gesture pill's skin with its capsule taken off |
| `scrim` | behind a modal | black at 28 percent with glass on, 55 percent with it off; no blur |

No elevation and no shadows, with one exception: `chrome` keeps its soft shadow, because it
floats over moving video with no edge to anchor to and the shadow is what seats it. `panel`
surfaces are edge anchored or scrimmed and need none. Depth everywhere else is the rim.

`chrome` is a separate tier for a performance reason, not a visual one: a blurred surface makes
the backdrop record itself every frame while it is on screen, and the status line is on screen
whenever the HUD is. Always-composed chrome must never attach the blur.

## Semantics

Every control above declares itself as part of its definition: a role, a state, a value and a
spoken value where the visual one would read badly (a timecode, not a float; a colour name and
hex, not an integer). Rows merge their descendants so a row is one announcement. This is
non-negotiable in the control definitions because it is the thing an overhaul silently breaks:
the Material components being replaced provided it for free.

## The code shape

So every folder builds against the same names:

```
app/theme/Tokens.kt                Space, Radius, Type, Motion, Palette, LocalPalette, Tier
app/uicomponents/controls/         one file per control in the table above
app/uicomponents/frames/           ScreenFrame, Modal, PanelFrame, Notice
app/room/RoomFrame.kt              the room's docks (ROOM_SHELL)
```

```kotlin
object Space { val u = 6.dp; val row = 42.dp; val rowTall = 54.dp; val bar = 54.dp; /* ... */ }
object Radius { val none = 0.dp; val tight = 2.dp; val control = 3.dp; val panel = 8.dp }
object Type { val display: TextStyle @Composable get; val label; val value; val group; val note }

@Immutable
data class Palette(
    val ground: Color, val panel: Color, val ink: Color, val inkDim: Color, val inkFaint: Color,
    val rule: Color, val trackOff: Color, val accent: Color, val brandField: List<Color>,
    val ok: Color, val warn: Color, val bad: Color, val disabled: Color,
) { fun overVideo(): Palette }

val LocalPalette: ProvidableCompositionLocal<Palette>
val palette: Palette @Composable get() = LocalPalette.current

enum class Tier { Flat, Panel, Chrome, Scrim }
fun Modifier.surface(tier: Tier, shape: Shape = RectangleShape): Modifier
```

`AdamScreen` provides `LocalPalette` from the active theme once; the room re-provides
`palette.overVideo()`. `MaterialTheme.typography` and `shapes` stay populated, each role pointed
at the nearest Synkplay role, so an accidental M3 usage does not look foreign, but new code never
reads them.

## Lint

Four checks, run as a desktopTest next to the render goldens, so a regression fails a build
rather than a review:

1. No `androidx.compose.material3.*` import in `commonMain` outside `MaterialTheme`, `Text`,
   `Icon`, and the two files that adapt the colour scheme.
2. No `.sp` literal outside `Tokens.kt`, except the chat font size preference.
3. No `MaterialTheme.typography` and no `ripple(` anywhere.
4. No text size below 11sp, and 11sp only in the `group` role.

## How the other folders use this

Every other `DESIGN/` folder assumes these tokens and this control vocabulary, and only
documents:

1. what that surface does today and what is wrong with it,
2. the redesign in terms of these tokens,
3. what has to change in code, in phases.

If a surface needs a control that is not in the table above, it gets designed there and
promoted back into this file. The Tag, the Segmented selector, the Progress bar and the toast
were promoted here that way.
