# Synkplay logo palette

Approved on 2026-08-31. This is the **26% softer** evolution of the original
Kotlin-inspired palette. The logo geometry, cutouts, gradient axes, and highlight
geometry are locked; this document records color only.

## Linear color field

The primary gradient runs diagonally from `(250, 1200)` to `(1160, 100)` in
the `1254 × 1254` vector viewport.

| Offset | Color | Role |
|---:|:---:|---|
| 0% | `#793695` | softened deep violet |
| 25% | `#9879EF` | gentle ultraviolet |
| 55% | `#C331D8` | softened orchid-magenta |
| 88% | `#D86B75` | dusty coral |
| 100% | `#D86B75` | dusty coral hold |

## Diffuse lilac light

The soft radial veil is centered at `(440, 527)` with radius `602`.

| Offset | Color | Opacity |
|---:|:---:|---:|
| 0% | `#F3ECFF` | 34% |
| 42% | `#F3ECFF` | 14% |
| 100% | `#F3ECFF` | 0% |

## Trinity: the three colors the app itself uses

The UI cannot draw a five stop field everywhere, so the theme and the launcher
vector run on three seeds. They are the stops that cover most of the logo's
visible sail area, and they live in `buildSrc/src/main/kotlin/AppConfig.kt`.

| Seed | Color | Logo stop |
|---|:---:|---|
| `TRINITY_1` | `#9879EF` | 25% |
| `TRINITY_2` | `#C331D8` | 55% |
| `TRINITY_3` | `#D86B75` | 88% |

The `#793695` stop at 0% is a shadow anchor for the artwork only. It is left out
of the Trinity because the app draws these three raw, and a near black violet
would kill the gradient on a dark screen.

From `AppConfig` the three values flow out in two directions:

- `KiteBuildConfig.TRINITY_COLOR_1/2/3` (declared in the root `build.gradle.kts`)
  become `Theming.NeoSP1/2/3` and `Theming.SP_GRADIENT`, which seed the Trinity
  and Daylight built in themes and every brand gradient in the UI.
- `propagateTrinityColors()` rewrites the three gradient stops in
  `shared/src/androidMain/res/drawable/ic_launcher_foreground.xml`, the legacy
  vector still used for notification small icons and the Android TV banner.

Change a color here and both paths follow. Never hand edit the launcher vector.

## Source and generated assets

- `art/synkplay_logo.svg` is the canonical vector artwork.
- `shared/src/commonMain/composeResources/drawable/synkplay_logo.xml` is the equivalent Compose VectorDrawable.
- `shared/src/commonMain/composeResources/drawable/synkplay_fg.png` is the transparent 1024 x 1024 KiteConfig foreground.
- `shared/src/commonMain/composeResources/drawable/synkplay_bg.png` is the opaque platform plate. It is neutral gray, so a palette change does not touch it.
- Android launcher resources and `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset` are generated and owned by KiteConfig.

Regenerate the raster and platform assets from the repository root:

```shell
rsvg-convert --width 1024 --height 1024 \
  --output shared/src/commonMain/composeResources/drawable/synkplay_fg.png \
  art/synkplay_logo.svg

./gradlew kiteRewriteLogo
```
