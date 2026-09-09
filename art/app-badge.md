# Synkplay app badge

The original Synkplay logo and white-grey app background, framed as a rounded
macOS-style badge with a neutral edge and soft shadow. The exterior is transparent
for use on light and dark backgrounds.

- [synkplay_app_badge.png](synkplay_app_badge.png): 1024 × 1024 PNG.
- [synkplay_app_badge_512.png](synkplay_app_badge_512.png): 512 × 512 PNG,
  displayed at 180 × 180 in the README.

The foreground comes directly from [synkplay_logo.svg](synkplay_logo.svg), with
its original paths, gradient stops, highlights and cutouts. The background is
the app's actual [synkplay_bg.png](../shared/src/commonMain/composeResources/drawable/synkplay_bg.png),
resized and clipped to the rounded outline without recoloring. The final badge
uses these existing assets, with no generated image pixels.

## Rebuild

Requires `rsvg-convert` and ImageMagick:

```sh
bash art/render-app-badge.sh
```

[app-badge-mask.svg](app-badge-mask.svg) defines the rounded silhouette, and
[app-badge-surround.svg](app-badge-surround.svg) supplies the subtle neutral edge
and shadows. The script scales the original background to the 832-pixel tile and
centres the visible logo at 648 pixels wide. Final PNGs use 8-bit RGBA.

This badge is GitHub/presentation artwork. Platform launcher and store assets
retain their existing packaging.
