# Synkplay app badge

The app badge shows the Synkplay logo on the white-grey app background, framed as a rounded
macOS-style badge with a neutral edge and a soft shadow. The area outside the badge is
transparent, so the badge works on light and dark backgrounds.

- [synkplay_app_badge.png](synkplay_app_badge.png): 1024 × 1024 PNG.
- [synkplay_app_badge_512.png](synkplay_app_badge_512.png): 512 × 512 PNG. The README shows it at
  180 × 180, and the release notes show it 128 pixels wide.

The foreground comes directly from [synkplay_logo.svg](synkplay_logo.svg), with its original
paths, gradient stops, highlights and cutouts. The background is the app's own
[synkplay_bg.png](../shared/src/commonMain/composeResources/drawable/synkplay_bg.png), resized and
clipped to the rounded outline, with no change of color. The badge uses only these existing
assets. It contains no pixels from an AI image generator.

## Rebuild

The script needs `rsvg-convert` (from librsvg) and ImageMagick. Run it from the repository root:

```bash
bash art/render-app-badge.sh
```

- [app-badge-mask.svg](app-badge-mask.svg) defines the rounded outline.
- [app-badge-surround.svg](app-badge-surround.svg) adds the neutral edge and the shadows.
- The script scales the background to an 832-pixel tile. It fits the trimmed logo into a
  648-pixel square and places it at the center, 8 pixels higher.
- The final PNGs use 8-bit RGBA.

The badge is artwork for the README and the release notes. The launcher icons and the store icons
come from other sources.
