# Type and icons overhaul

Assumes [FOUNDATION](../FOUNDATION/README.md).

Files: `theme/AppTypography.kt` (45), `uicomponents/Fonts.kt` (25),
`uicomponents/FlexibleText.kt` (237), `uicomponents/FlexibleIcon.kt` (111),
`uicomponents/SynkplayLogo.kt` (65), `uicomponents/AnimatedImage.kt` (23).

## What is wrong

**The type scale is Material's.** `AppTypography.kt` says so in its own doc comment: "Material 3
metrics with one text family (Lexend)". Every one of the fifteen roles is
`base.<role>.copy(fontFamily = ...)`. So the sizes, weights, line heights and tracking are all
Material's defaults, and swapping the family does not change that. This is the one file that
matters most in the whole overhaul: it is where the Material look actually comes from.

**36 direct uses of Material roles** across the UI (`labelLarge` 10, `titleMedium` 7,
`titleSmall` 6, and so on), plus thirteen distinct literal sizes from 8sp to 20sp: 14sp in 28
places, 11sp in 14, 10sp in 13, 12sp in 12, 13sp in 9, 9sp in 5, 8sp in 2. So there are
effectively two type systems, the Material one and a pile of magic numbers, and the magic
numbers win on the surfaces that matter most. The smallest text of all is not a literal: the
chat font size preference defaults to 9sp.

**Five font families are loaded and all five are in use.** Lexend is the text face. Directive4
is the wordmark and the seek-to editor's digits. Jost appears in the colour picker, the managed
room popup, the media directories popup and the theme creator. Saira appears in the gesture
readout, the status block and the media directories popup. Helvetica appears in exactly one
place, the playlist's URL popup, and is a questionable thing to ship at all.

**The shape scale is Material's too.** `appShapes` is 8 / 12 / 16 / 24 / 28dp, which is why
everything in the app is noticeably rounded, and its `extraLarge` is the default shape of every
glass surface and of the alert dialog.

**Icons are 142 distinct Material glyphs** app-wide, 54 of them on preference rows, all at
Material's optical weight, so they read as Android system icons regardless of what surrounds
them.

## The design

### One scale, five roles, no Material

`appTypography` is deleted, not re-tuned. In its place, the FOUNDATION scale as a plain object:

| Role | Size | Weight | Tracking | Line |
|---|---:|---|---:|---:|
| `display` | 24sp | 700 | -0.5 | 28sp |
| `label` | 15sp | 500 | -0.1 | 19sp |
| `value` | 13sp | 500 | +0.3 tabular | 16sp |
| `group` | 11sp | 600 | +1.5 caps | 14sp |
| `note` | 13sp | 400 | 0 | 19sp |

Five roles, not fifteen. Anything that does not fit one of five roles is a design problem, not a
missing role.

`MaterialTheme.typography` is still populated, because some Compose internals read it, but every
role is pointed at the nearest Synkplay role so an accidental Material usage does not look
foreign. The lint rule is: no `MaterialTheme.typography.*` and no `.sp` literal outside
`Tokens.kt` in app code, with the chat size preference as the one dynamic exception.

### Two families

- **Lexend** for everything. It is already the text face, it is a variable font, and it holds up
  at 13sp over video, which is the app's hardest typographic condition.
- **Directive4** for the wordmark only. The seek-to editor's digits move to `value` type.

Jost, Saira and Helvetica are dropped, each when its last consumer moves: Helvetica with the
playlist panel, Saira with the status and readout work, Jost with the popups and the theme
creator. Three font files leave every platform binary.

### Shapes are near square

`appShapes` is replaced by the FOUNDATION radii: 0 / 2 / 3 / 8dp. This is the change that will
be felt most immediately, because everything in the app currently has a 12 to 28dp corner. It
lands **after** [GLASS_SURFACES](../GLASS_SURFACES/README.md) has moved glass onto the tier
API, since glass takes its default shape from this scale today.

### Icons: one drawn set

Target: 20dp box, 1.5dp stroke, square terminals, drawn as vectors rather than pulled from
`material-icons-extended`. Staged, because it is a large job:

1. Define the box and stroke, and apply a uniform optical treatment to the existing Material
   glyphs so they at least sit consistently.
2. Replace per surface, as each surface is overhauled. The transport and rail glyphs first,
   since they are the most seen and the most generic looking; the list is in FOUNDATION.
3. Drop the `material-icons-extended` dependency once the last one is replaced, which also cuts
   a large artifact from the build.

### `FlexibleText` and `FlexibleIcon`

Both are built from Material `Text` and `IconButton` plus gradient overlays, and their gradient
use is what breaks the budget: chat paints the gradient on its field label, its glyphs and every
typed character.

- `SyncplayishText`, the wordmark, stays as it is.
- The three layer text (shadow, stroke, fill) survives only as `OutlinedText`, the component the
  chat outline preference needs over video. Gradient body text is deleted.
- `FlexibleIcon` becomes the FOUNDATION Glyph button: a 20 or 24dp glyph in a 48dp target, no
  ripple, press feedback, TV focus and a required name for its spoken description. Its eight
  consumers move one surface at a time.

### The logo

`SynkplayLogo` draws the mark live from the active theme's seeds and matches the shipped artwork
exactly, including the artwork's own `#793695` dark anchor, tuned in Oklab because that is the
space Compose's `lerp` interpolates in. It is correct and does not change.

## Phases

1. Introduce the five roles and the near square radii alongside the existing scale (the radii
   used by new code only until the glass tiers land).
2. Migrate surfaces to the roles as each surface's own overhaul lands, deleting magic sp
   literals as they go.
3. Delete `appTypography`'s Material metrics and `appShapes`. Drop the three fonts as their
   consumers move.
4. Icon set, staged as above.

## Risks

- Changing the radius scale changes every screen at once. It should land with at least one
  surface already rebuilt, so the squarer corners read as intentional rather than as a
  regression, and only after glass no longer reads it.
- Dropping fonts is only safe once nothing references them. Each font has a named last
  consumer above; the build fails on a missing resource, which is the check.
- Resource fonts do not resolve in the render harness, so goldens measure layout and spacing
  with a fallback face. Letterform judgement stays a device check.
