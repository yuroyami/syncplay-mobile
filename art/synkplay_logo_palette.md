# Synkplay logo palette

This file records the colors of the Synkplay logo. It covers color only. Do not change the logo
geometry, the cutouts, the gradient axes or the highlight geometry through this file.

## Linear color field

The primary gradient runs diagonally from `(250, 1200)` to `(1160, 100)` in the `1254 × 1254`
vector viewport.

| Offset | Color | Role |
|---:|:---:|---|
| 0% | `#793695` | softened deep violet |
| 25% | `#9879EF` | gentle ultraviolet |
| 55% | `#C331D8` | softened orchid-magenta |
| 88% | `#D86B75` | dusty coral |
| 100% | `#D86B75` | dusty coral hold |

## Diffuse lilac light

The soft radial light is centered at `(440, 527)` with a radius of `602`.

| Offset | Color | Opacity |
|---:|:---:|---:|
| 0% | `#F3ECFF` | 34% |
| 42% | `#F3ECFF` | 14% |
| 100% | `#F3ECFF` | 0% |

## Trinity: the three colors that the app uses

Trinity is the name of three brand colors. They are the logo stops that cover most of the visible
logo. The app themes and the launcher vector take three seed colors, not the five-stop field, so
they use these three. The values live in
[`buildSrc/src/main/kotlin/AppConfig.kt`](../buildSrc/src/main/kotlin/AppConfig.kt).

| Seed | Color | Logo stop |
|---|:---:|---|
| `TRINITY_1` | `#9879EF` | 25% |
| `TRINITY_2` | `#C331D8` | 55% |
| `TRINITY_3` | `#D86B75` | 88% |

The `#793695` stop at 0% is not a seed. The in-app logo computes that stop from the first two
seeds: it mixes them and darkens the mix in the Oklab color space. With the Trinity seeds, the
result is `#793695`.

From `AppConfig`, the three values go two ways:

- The root `build.gradle.kts` turns them into `KiteBuildConfig.TRINITY_COLOR_1`, `_2` and `_3`.
  These become `Theming.NeoSP1`, `NeoSP2` and `NeoSP3` in the app. They seed the built-in themes
  `TRINITY` (shown as "Violet") and `DAYLIGHT`. A theme without its own seed colors falls back to
  them.
- The `syncTrinityColors` task writes them into the three gradient stops of
  `shared/src/androidMain/res/drawable/ic_launcher_foreground.xml`. The small icon of the server
  notification and the Android TV banner use that vector.

The task does not run by itself. After you change a Trinity color in `AppConfig.kt`, run it:

```bash
./gradlew :shared:syncTrinityColors
```

Never edit the gradient stops of the launcher vector by hand.

## Source and generated assets

KiteConfig is a Gradle plugin. Its block in the root `build.gradle.kts` holds the app name, the
app ID, the version and the two logo images of the app icons.

- `art/synkplay_logo.svg` is the canonical vector artwork.
- `shared/src/commonMain/kotlin/app/uicomponents/SynkplayLogo.kt` draws the same shape in code, so
  that the logo takes the colors of the active theme.
- `shared/src/commonMain/composeResources/drawable/synkplay_fg.png` is the transparent
  1024 × 1024 foreground of the app icons.
- `shared/src/commonMain/composeResources/drawable/synkplay_bg.png` is the opaque background plate
  of the app icons. It is neutral gray, so a palette change does not affect it.
- KiteConfig generates and owns the Android launcher resources and
  `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset`.

Run the commands below from the repository root.

Render the foreground from the SVG:

```bash
rsvg-convert --width 1024 --height 1024 \
  --output shared/src/commonMain/composeResources/drawable/synkplay_fg.png \
  art/synkplay_logo.svg
```

Regenerate the platform icons:

```bash
./gradlew kiteApply
```
