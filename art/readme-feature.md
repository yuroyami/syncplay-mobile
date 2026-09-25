# README feature graphic

[readme-feature.webp](readme-feature.webp) is the 1600 × 800 banner at the top of the README. Its
three screens show the real Android portrait, Android landscape and macOS captures from
[screenshots/](screenshots/README.md).

## Rebuild

The scripts need ImageMagick and `rsvg-convert` (from librsvg). The typeface, Lexend, is in the
repository. Run the banner script from the repository root:

```bash
bash art/render-readme-feature.sh
```

The script does these steps:

1. It runs [render-readme-availability.sh](render-readme-availability.sh), which builds the
   availability line.
2. It starts from [readme-feature-background.webp](readme-feature-background.webp), a lossless
   WebP.
3. It resizes each original screenshot with its proportions kept, then builds a bezel around the
   resulting screen size. It adds no containing box and no padding, and it does not crop or
   stretch. Only the screen corners are rounded. The captures stay unchanged, including the
   macOS title bar and any black bars inside the captured player.
4. It draws the connector lines below the devices.
5. It saves the banner as a WebP at quality 92, resized from the 1774 × 887 working canvas to
   1600 × 800.
6. It also saves [readme-feature-frame.webp](readme-feature-frame.webp), a preview of the empty
   device frames. The preview is an output. Never use it as the source of the screen openings.
7. It updates the banner URL in the README with a version made from the pixels of the new image.
   Changed artwork then gets a new URL, so no cache can show the old image.

The script works with PNG intermediates, and the background is lossless. So a rebuild adds no
extra compression loss.

### Device layout

The working canvas is 1774 × 887. A position is the top-left corner of a screen.

| Capture | Screen position | Resized screen | Bezel per side | Outer frame |
|---|---|---|---|---|
| macOS | 845, 139 | 744 × 427 | 12 px | 768 × 451 |
| Android landscape | 650, 619 | 420 × 189 | 10 px | 440 × 209 |
| Android portrait | 1552, 369 | 189 × 420 | 10 px | 209 × 440 |

- Both phones use the same scale and the same 20:9 display proportions, in two orientations.
- The script rounds the desktop height to the nearest pixel.
- The `prepare_device` calls set the screen widths, the positions, the bezel widths and the corner
  radii. The heights always come from the screenshots.
- A bounds check stops the script when a device does not fit the canvas.

The README also shows the Android screenshots on their own. The
[Play Store artwork](play-store/README.md) has its own, simpler design.

## Availability line

The banner says **Available on Android and iOS**, with the Android head beside "Android" and the
Apple logo beside "iOS".

- The Android head and the word "Android" use **#34A853**. This is the color of Google's
  [flat Android asset](https://developer.android.com/static/images/brand/android-head_flat.svg).
- The Apple logo and the word "iOS" use **#FFFFFF** on the dark background.
- "Available on" and "and" use the neutral **#DDD2E5**.

[render-readme-availability.sh](render-readme-availability.sh) builds
[readme-feature-availability.webp](readme-feature-availability.webp), a transparent 520 × 56 image.
The script uses the bundled Lexend font and measures the text to set the spacing. It renders the
real SVG marks with librsvg:

- The [Android SVG](third-party/android/head.svg) is Google's download, unchanged.
- The [Apple SVG](badges/marks/apple.svg) is the Apple mark of the App Store button.

The logo shapes and colors are exactly those of the source assets.

The banner script places the availability image at (72, 596). It resizes the
[Google Play](badges/google-play.png) and [App Store](badges/app-store.png) badge PNGs to
240 × 72 each, with the proportions kept. It places them at (84, 654) and (348, 654), with a 24 px
gap. The badges sit above the compatibility footer and clear of the landscape phone.

The banner is an image, so its store badges are not links. The Download section of the README has
the links: the [Google Play listing](https://play.google.com/store/apps/details?id=com.yuroyami.syncplay)
and the [App Store listing](https://apps.apple.com/us/app/synkplay/id6760187432). The alt text of
the banner names both platforms and both stores.

Android is a trademark of Google LLC. The Android robot is reproduced or modified from work
created and shared by Google and used according to terms described in the
[Creative Commons 3.0 Attribution License](https://creativecommons.org/licenses/by/3.0/).
See Google's [brand asset source and attribution](https://developer.android.com/distribute/marketing-tools/brand-guidelines).

## Compatibility footer

The note at the lower left reads:

> Compatible with original official Syncplay for PC<br>
> syncplay.pl · github.com/Syncplay/syncplay

The addresses are those of the [official Syncplay website](https://syncplay.pl/) and the
[official repository](https://github.com/Syncplay/syncplay). The alt text of the banner in the
README includes the note and both addresses.

The banner script adds [readme-feature-footer.webp](readme-feature-footer.webp) at (129, 752). Then
it adds the [official Syncplay logo](third-party/syncplay/logo.png) at (84, 764), resized to
44 × 44 with the proportions kept. The colors and the shape of the logo are unchanged. The logo and
its [Apache 2.0 licence](third-party/syncplay/LICENSE) come from
[upstream revision 2af7ace347f437d44cd809a65c049e82bb4027fb](https://github.com/Syncplay/syncplay/blob/2af7ace347f437d44cd809a65c049e82bb4027fb/syncplay/resources/syncplay.png).

An AI image generator made the footer text from the prompt below. The footer uses only the
516 × 76 region at (80, 778) of the generated image, resized to 490 × 72 with the proportions
kept. The edges of that region are feathered for compositing. The footer is a lossless WebP with
transparency. The script adds the official logo separately, from its original PNG.

### Footer prompt

This is the exact prompt that produced the footer text:

```text
Use case: precise-object-edit.
Edit target: the existing Synkplay 1774 by 887 banner BACKGROUND in image 1.
Add only a discreet two-line compatibility footer at the bottom left. Keep every existing pixel, all text, branding, logo, abstract plum background, positions, colors and composition elsewhere unchanged. No screens or devices should be added to this background.
New text, verbatim:
Line 1: "Compatible with original official Syncplay for PC"
Line 2: "syncplay.pl  ·  github.com/Syncplay/syncplay"
Placement is critical: use the same 1774 by 887 coordinate system as the reference. Left-align BOTH text lines at x=138. Line 1 baseline y=779; line 2 baseline y=811. Keep all footer text inside x=138..622 and y=758..818. Line 1 is approximately 19 pixels high in a clean, readable regular humanist sans-serif that matches the existing subheading, colored muted warm ivory #ddd2e5. Line 2 is approximately 17 pixels high, colored soft lilac #b9a1ca. Keep both lines on a single line each, no wrapping. Subtle supporting-note hierarchy, much smaller than the main tagline. No bold box, panel, border, divider, glow or decorative marks.
Leave the space x=84..125, y=769..811 COMPLETELY EMPTY, retaining the original background there: the actual official Syncplay logo will be composited separately from its original asset.
Critical: exact spelling and capitalization, especially original Syncplay with a c, website syncplay.pl, and github.com/Syncplay/syncplay. Do not alter the existing Synkplay wordmark with a k. Do not move or change the main headline or subtitle. Preserve 2:1 aspect ratio and output at 1774 by 887 if possible. Output one finished background with the two footer text lines only added.
```

## Background

[readme-feature-background.webp](readme-feature-background.webp) holds the plum background, the
branding and the text on the left. Its right side is empty, for the devices. An AI image generator
made it from an earlier version of the banner, with the prompt below. The generator removed the
old device frames and their connecting lines, and rebuilt the background behind them.

The banner script supplies the final device geometry, the connectors and the real screenshot
pixels. The banner uses no generated screen content and no generated interface labels.

### Background prompt

This is the exact prompt that produced the background:

```text
Use case: precise-object-edit.
Asset type: existing Synkplay README banner, background repair for accurate device-frame compositing.
Input image 1 is the edit target, the existing 1774 by 887 banner with three EMPTY black device frames.
Primary request: Remove all three device frames and their black interiors, shadows, highlights, AND the thin luminous connecting lines and dots below them. Seamlessly reconstruct the existing dark plum abstract wing-texture background behind those removed objects. The result must be this same banner's background and existing left-hand branding/copy only, with a clean empty right-hand area ready for separately composited real screenshots.
Critical invariants: preserve the image's exact 2:1 aspect ratio, original composition, color palette, textured purple wing shapes, subtle existing lighting, logo, typography, left-hand copy and their positions. Do not shift or resize anything that is being retained.
Keep text verbatim: "Synkplay"; "Your video."; "Everyone in sync."; "Watch together on phones and computers."
Do not add any screens, bezels, frames, devices, stands, UI, symbols, connector lines, dots, text or decoration. Reconstruct only the background where the removed devices and lines were. Maintain the original visual identity and low-key dark plum look. Output one finished seamless 2:1 banner background at high resolution.
```
