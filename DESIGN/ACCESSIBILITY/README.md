# Accessibility overhaul

Assumes [FOUNDATION](../FOUNDATION/README.md).

Not a single file. This is a cross cutting aspect, and it is the one an overhaul most easily
makes worse, because most of the changes elsewhere reduce visible text.

## Where it stands

Measured, not assumed:

- There is **no semantics code in the app**. Zero `Modifier.semantics`, zero `role`, zero
  `stateDescription`, zero live regions.
- 49 `contentDescription` arguments exist. 29 are `null`, 9 are the empty string, 3 carry real
  text ("Back" and two others). A screen reader gets the Material components' built in
  announcements and nothing else.
- Type is small and getting smaller. 8sp exists (the fading chat in picture in picture), the
  chat font size preference defaults to 9sp and goes down to 6, and 9, 10 and 11sp literals are
  spread across the room, the playlist, the theme picker and in-room settings. 9sp is below what
  any guidance considers legible at arm's length, and the room's is over moving video.
- Contrast is unverified. Panels are translucent glass over arbitrary video, so text contrast
  depends on the frame behind it. Nothing in the app measures or guarantees it.
- Colour alone carries meaning in several places: readiness green and red, connection state,
  message kinds, log severity.
- Gestures are undiscoverable and unreachable: brightness and volume swipes and double tap seek
  have no non gesture equivalent.
- Nothing reads a reduced motion setting on any platform.
- Text is in sp, so the system font size does scale it, but no layout has been checked at a
  large scale and the 8 and 9sp cases scale from an already illegible base.

## The design

### A minimum size, enforced

No text below 11sp anywhere, and 11sp only in the `group` role for short uppercase headings.
`value` and `note` are 13sp. The 8, 9 and 10sp literals are deleted with the surfaces that hold
them, the chat preference gets an 11sp floor with a 13sp default, and the lint in FOUNDATION
fails on any smaller literal.

Row heights are minimums, so a 130 percent system font scale wraps a row instead of clipping it,
and the render goldens run at font scale 1.0 and 1.3.

### Contrast is designed, not hoped for

Over video, text sits on the `panel` or `chrome` tier, whose pinned near black palette plus
the glass dim guarantee a floor regardless of the frame behind. For the one thing drawn directly
over video with no tier behind it, the fading chat, the outline preference is applied there (it
is not today) and stays on by default.

### Every drawn control declares itself

Each FOUNDATION control carries semantics as part of its definition, not per call site:

| Control | Semantics |
|---|---|
| Rocker | role Switch, state on or off, the row label as its name |
| Scrub track | role Slider, value, range, and a spoken value formatter (a timecode, not a float) |
| Stepper | role Slider with discrete steps, current option spoken by name |
| Swatch | role Button, value spoken as the colour name and hex |
| Glyph button | role Button, and a name parameter that is required, not optional, so a glyph without a spoken name cannot be written |
| Tag | role Button when toggleable, with its state; plain text otherwise |
| Notice | a live region, polite for info and quiet, assertive for warn |
| Rows | merged descendants, so a row is one announcement, not four |

This is the single most important item in this folder, because it is the thing an overhaul
breaks by default: there is nothing to preserve, so it has to be built in from the first
control.

### Meaning never rests on colour alone

- Readiness: the green square is filled, the red one is hollow.
- Connection: the status square is paired with a word.
- Chat: person and event lines differ in shape, not only in tint (see [CHAT](../CHAT/README.md)).
- Log: severity stub plus a severity word in the row.

### Gestures get equivalents

Everything reachable only by gesture is also reachable another way: brightness and volume
appear as scrub rows in the control panel, and double tap seek duplicates the jump buttons.
That also serves D-pad and TV users, who cannot swipe at all.

### Reduced motion is honoured everywhere

`expect fun reducedMotion(): Boolean` (iOS reduce motion, Android animator duration scale,
desktop false) plus a Reduce motion switch in settings that forces it on. Every transition
defined in FOUNDATION and in the surface folders collapses to an instant state change or a
crossfade. This is stated in each folder and verified once here.

## Phases

1. Semantics baked into the FOUNDATION controls, before any surface adopts them. Doing this after
   adoption means retrofitting every call site.
2. Minimum type size enforced, with the lint check and the chat preference floor.
3. Non colour differentiators for readiness, chat and the log; reduced motion.
4. Gesture equivalents in the control panel.

## Verification

The render harness cannot test screen readers, so this folder needs a second check: a manual pass
with TalkBack on Android and VoiceOver on iOS over the four main surfaces (home, room transport,
settings, playlist), recorded as a checklist per release. Automated checks cover what they can:
the minimum type size, the font scale goldens, and that every FOUNDATION control exposes a role
and a value.

## Risks

- This folder is the one most likely to be skipped under time pressure, and it is the one whose
  cost grows fastest when deferred. Phase 1 is therefore a hard prerequisite of the FOUNDATION
  work, not a follow up.
