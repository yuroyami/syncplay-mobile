# Theming overhaul

Assumes [FOUNDATION](../FOUNDATION/README.md).

Files: `theme/ThemePicker.kt` (367), `theme/ThemeCreatorScreen.kt` (598),
`theme/BuiltinThemes.kt` (99), `theme/SaveableTheme.kt` (45), `theme/Theming.kt` (63),
`theme/AppTypography.kt` (45), and the theme methods of `SyncplayViewmodel.kt`.

## What it is

Six built in themes (Trinity, Daylight, Silver Lake, PyncSlay, GrayOLED, Alley Lamp) plus a full
custom theme creator. A theme is a set of seeds fed to MaterialKolor, which generates the whole
scheme. This is a genuine feature and a good one; almost nothing about the model changes.

## What is right already

- Seeds, not hand painted palettes. `SaveableTheme` carries primary, secondary, tertiary,
  neutral, neutral variant, contrast, dark, AMOLED and palette style. That is the right level of
  control.
- `Theming.flexibleGradient` reads the active theme's raw seeds rather than scheme roles, so
  identity moments stay vivid instead of being muted into chrome. Twenty one files read it; its
  shape does not change.
- The creator already previews live: the whole screen is re-themed on every edit by
  re-providing the theme and the colour scheme. What it lacks is a sample of the surfaces the
  theme will actually paint, not liveness.
- Custom themes are stored as a set of JSON strings, and saving a theme also applies it.

## What is wrong

**`ThemeCreatorScreen.kt` carries seventeen Material imports**, including `Scaffold`,
`TopAppBar`, `Surface`, `Checkbox`, `Button`, `SplitButtonDefaults`, `ExposedDropdownMenuBox`
and the menu types. It is the most Material file in the app after the room control panel, and
its "Preview" section is dead: a read-only field, a split button and a room tab with empty
click handlers.

**The picker previews a theme as a three colour gradient card** (`primary`,
`tertiaryContainer`, `background`) in two horizontal rows inside a `BasicAlertDialog`. Three
colours out of a generated scheme do not tell you what the theme looks like in use, so choosing
is guesswork followed by trying it. Names auto-shrink between 9 and 14sp.

**Editing and deleting are fragile.** Edit and delete are reachable only by long pressing a
custom theme. Delete has no confirmation. Deleting the active theme compares a `StateFlow` to
a theme, which is never equal, so the deleted theme stays live as an orphan the picker cannot
show. Saving an edit deletes the old copy and saves the new one in two separate coroutines,
so a fast save can lose the theme entirely.

**The creator recomputes the palette per keystroke.** Its derived state is not remembered and
the scheme is lazy per instance, so every colour drag frame generates a full MaterialKolor
palette on the composition thread. Inside the creator the app's type and shapes fall back to
Material defaults, because the preview wraps only the colour scheme. There is no light or dark
switch, the contrast slider resets the theme's contrast to zero on open, and one specific
colour (22, 22, 22) cannot be picked because the picker uses it as a sentinel.

## The design

### Preview what the theme actually does

Replace the three colour card with a **miniature of the app**: one composable that draws a
transport bar with a filled scrub track, two chat lines, and a panel edge, at whatever size it
is given. The picker composes it at 72 x 40dp per row; the creator composes it as the live
canvas at the size of its pane. Themes then compare on the thing they change.

The miniature takes a static sample model and a resolved scheme. It never subscribes to real
state (no player, no room), it draws no glass and no blur, and the picker resolves each theme's
scheme once through the existing lazy `dynamicScheme`. Its scrub fill is the theme's gradient,
which is the one gradient in the miniature and does not count against the viewport budget any
more than a logo does; the picker is a list of brand samples.

### The picker is a list, not a grid of cards

A `panel` modal from [POPUPS](../POPUPS/README.md). Each theme is a 54dp row: the miniature on
the left, name in `label`, and a `value` column saying `dark`, `light` or `amoled`. The active
theme takes a 2dp `accent` left edge. Custom themes carry edit and delete glyphs on their own
48dp targets; delete asks first. Built in themes come first, custom themes after a group
heading, newest first as today. A list makes the names readable, which a 72dp square did not.

### The creator gets a live canvas

Split the screen: controls on one side, the miniature at full pane size on the other, updating
as you drag. On compact widths the miniature pins to the top and the controls scroll under it.

That single change is what makes the creator usable, because a generated scheme is not
predictable from its seeds. You cannot reason your way from a hex value to what MaterialKolor
will produce; you have to see it.

The scheme is computed once per edit, remembered on the theme value, and colour drags are
rate limited to one recomputation per frame at most. The preview provides the theme through the
token layer, so type, radii and palette all follow the edit, not only the colour scheme.

### Controls come from FOUNDATION

- `Checkbox` and switches become rockers. A dark or light rocker is added; it is the one seed
  the creator cannot set today.
- The palette style menu becomes a stepper, since there are a fixed handful of styles.
- Colour rows become the settings `ColorRow`: hex in the value column, swatch after it, opening
  the shared colour modal. The (22, 22, 22) sentinel goes with the third party picker's
  dropdown.
- The contrast row is a scrub row that starts at the theme's stored contrast.
- `Scaffold` and `TopAppBar` are replaced by the screen frame from
  [NAVIGATION](../NAVIGATION/README.md).
- Save is the one Primary action; Save as new is a Secondary action. Editing a theme replaces
  the old copy and stores the new one in a single datastore edit.

### Deleting works

Deleting the active theme switches to the first remaining custom theme or the default, by
comparing themes, not a flow to a theme. Delete asks first.

### Gradients stay reserved

`syncplayGradients` is kept on the model for serialisation and read nowhere; identity moments
draw from the seeds. The FOUNDATION rule applies: one gradient per viewport outside the picker.

## Invariants

- `defaultTheme` is what the stored current theme defaults to, so changing Trinity changes the
  stored default.
- Custom themes de-duplicate on exact JSON equality. Two themes with the same name and different
  colours both persist; saving an unchanged theme is refused.
- `BLANK_THEME` is dead and goes; a new theme seeds from the current theme, as today.
- The theme picker dialog is opened from the home top bar only.

## Phases

1. The miniature component, used by both the picker and the creator.
2. Picker rebuilt as a list in the modal frame, with the delete confirmation and the delete fix.
3. Creator split into controls and a live canvas, with memoised scheme generation.
4. Material components inside the creator replaced; the dark rocker; the single-edit save.
   Delete `Scaffold`, `TopAppBar`, `Checkbox`, `ExposedDropdownMenuBox`, `SplitButtonDefaults`,
   `Surface` and the dead preview section.

## Risks

- The miniature composes real components, so it must not subscribe to real state, or it pulls
  the room graph into the theme screen.
- Six built in themes plus custom ones means the miniature renders many times in a list. It
  needs to be cheap: no glass, no blur, static content, the scheme resolved once per theme.
- The single-edit save changes the persisted set in one write. The migration is nil, because
  the format does not change, but the old two-step code must go entirely or the race returns.
