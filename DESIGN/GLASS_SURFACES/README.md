# Surfaces and glass overhaul

Assumes [FOUNDATION](../FOUNDATION/README.md).

Files: `uicomponents/GlassSurface.kt` (292) and its three platform actuals,
`uicomponents/GlassComponents.kt` (150), `uicomponents/DarkGlassPill.kt` (66),
`uicomponents/Overlays.kt` (41), plus the `DISABLE_FROSTED_GLASS` preference.

## What is right already

This is the strongest part of the current design and the least Material. Every rule below was
paid for with a real failure and stays exactly as it is:

- The blur style keeps a **transparent background colour**. The library's material presets fill
  an opaque rectangle before blurring, which makes a panel a picture of glass rather than glass.
  Readability comes from a wide blur (40dp) and a low tint, never from a heavier tint.
- A 40 percent black dim is applied to the capture **before** the tint, because the scrim and
  the platform dialog dim live in the dialog window while the capture samples the undimmed app
  window beneath. Without it a panel over a bright scene is brighter inside than out.
- `HazePerformanceMode.Quality` on every surface. Adaptive mode halves the capture for a blur
  this wide, and the noise grain gets stamped at half resolution and upscaled into blobs.
- Default `Behind` source selection. `All` lets in-window glass sample the capture that
  contains itself, and the render thread walks that cycle until its stack overflows.
- The backdrop attaches its capture only while a glass surface is on screen (`GlassDemand`),
  because the capture records every frame whether or not anything consumes it.
- The room provides its own capture state over the video layer, because in-window glass cannot
  sample the app-wide capture it lives inside. Dialog windows have no such ancestor and sample
  everything.
- `DISABLE_FROSTED_GLASS` is the end to end off switch: no capture anywhere, a heavier scrim,
  solid panels, and the Android players back on `SurfaceView`. `glassEnabled()` and
  `glassEnabledNow()` are the only places anything asks.
- Below Android 12 there is no blur; a heavier fallback tint carries readability alone. On
  Android 12 and up, a popup window also asks the compositor to blur behind it, which is the
  only blur that reaches a platform video view. That flag must be set from inside the dialog's
  own window.
- Haze cannot blur a `SurfaceView` or a `UIKitView`, so video itself is never blurred in the
  window; ExoPlayer draws into a `TextureView` when glass is on so in-room glass does blur it.
- One scrim exists, `glassScrim`: black at 28 percent with glass on, 55 percent with it off.
- The rim is a 1dp gradient lit at the top (white 22 percent) fading to almost nothing, and
  full-bleed chrome draws only the edge that faces content.
- `darkGlassPill` is the deliberately **non-blurred** sibling for chrome that stays composed
  while video plays.

Glass is the app's actual visual identity. The overhaul keeps it and gives it rules.

## What is wrong

**Glass is applied per component, by hand.** `glassSurface(shape, material)` is called at
twelve sites with their own shape (12dp cards, 10dp tabs, `shapes.small` cards, and the
default, which is Material's 28dp `extraLarge`) and their own material, so the same conceptual
surface gets different treatment in different files.

**Rounded corners everywhere.** Every glass surface is a floating rounded card, which is what
makes the room read as scattered rather than composed.

**The Material overlays are glass wrappers over Material.** The dropdown, exposed dropdown,
bottom sheet and alert dialog wrappers make the component transparent and draw glass on its
modifier; the component underneath is still Material and still owns position, animation and
gestures.

**The pill is a capsule** (26dp radius by default), and the previous version of this document
said "no shadows anywhere" while the pill draws a 20dp shadow that reads well and that the
owner adopted for the whole app. The rule was wrong, not the pill.

## The design

### Four tiers, chosen by context not by call site

| Tier | When | Treatment |
|---|---|---|
| `flat` | not over video: home, settings, theme creator, server host | solid `ground`, hairline separation, no blur, no cost |
| `panel` | opened over video: docks, sheets, modals, lists | real blur, `Thin` tint in the room and `Regular` in a dialog window (which samples the undimmed window), `radiusPanel` on the edges facing content only, one lit rim |
| `chrome` | always composed over video: the status line, notices, the gesture readout, the scrub bubble, the transport underlay | no blur. The pill's body: near black gradient, the same lit rim, a soft 20dp shadow, `radiusPanel` instead of a capsule |
| `scrim` | behind a modal | `glassScrim`, no blur |

`glassSurface` stops taking a shape and a material. It becomes `Modifier.surface(tier, shape)`:
the material follows from the tier and from whether the surface is in a dialog window, the
shape follows from which dock the surface is in (which the [ROOM_SHELL](../ROOM_SHELL/README.md)
frame knows), and the rim edges follow from the shape. That removes the per call site decision
that caused the inconsistency.

### The rim carries the depth

No elevation and no shadows on `flat` and `panel`. A single 1dp rim at `rule`, brightened to 22
percent white on the edge facing the light, is what separates a panel from the video. That is
cheaper than a shadow and reads correctly on both light and dark video. `chrome` keeps its
shadow, because it floats over moving video with no edge to anchor to.

### The no-glass path is a first class design

With `DISABLE_FROSTED_GLASS` on, `panel` becomes `ground` at 92 percent with the same rim and
the same geometry. Today the fallback is a solid surface that looks like a different app; it
should look like the same app with the blur turned off, which it will once geometry and rim are
shared.

### `DarkGlassPill` folds in

Its body, rim and shadow become the `chrome` tier. Its capsule shape goes. The status line and
the gesture readout move onto the tier through [STATUS_AND_OSD](../STATUS_AND_OSD/README.md),
and the file is deleted.

### The Material wrappers go with their components

`GlassDropdownMenu`, `GlassExposedDropdownMenu`, `GlassModalBottomSheet` and
`GlassAlertDialog` are deleted as [POPUPS](../POPUPS/README.md) retires each underlying
component. The window blur call moves into the `Modal` frame, which owns the dialog window.

### Order matters with the radius change

`glassSurface`'s default shape is `MaterialTheme.shapes.extraLarge`, and the alert dialog's
too. [TEXT_AND_ICONS](../TEXT_AND_ICONS/README.md) replaces the shape scale with 0, 2, 3 and
8dp, which would silently reshape every glass surface and every dialog. So the tier API, which
takes its shape from the dock and never from `MaterialTheme.shapes`, lands first, and the shape
scale changes after.

## Phases

1. `Modifier.surface(tier, shape)` alongside the current API, with the materials and rims fixed
   per tier. No caller moves yet.
2. Migrate call sites tier by tier as each surface's overhaul lands.
3. Delete the shape and material parameters, `DarkGlassPill`, and the wrappers as their
   components retire.
4. Rim treatment and the no-glass parity pass, measured on a mid range Android phone with the
   full flavour.

## Risks

- Glass has a real performance cost and an HDR interaction, which is why the preference exists.
  Any change to how often the capture is attached must be measured on a mid range Android
  device, not only on desktop, and always-composed chrome must never attach it.
- Inner-only rounding means a panel's shape now depends on its dock. A panel shown outside a
  dock (a preview, the render harness) gets `radiusPanel` on every corner as the default, not a
  crash.
