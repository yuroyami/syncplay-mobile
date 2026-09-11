# README feature graphic

[readme-feature.webp](readme-feature.webp) is the 1600 × 800 README banner.
Its three screens show the actual Android portrait, Android landscape and macOS captures
in [screenshots/](screenshots/README.md). The macOS image was supplied by the maintainer.

## Rebuild

```sh
bash art/render-readme-feature.sh
```

Requires ImageMagick and librsvg's `rsvg-convert`; the typeface is bundled in the repository.
The script starts with [readme-feature-background.webp](readme-feature-background.webp),
resizes each original PNG proportionally, then builds its bezel from the resulting screen
dimensions. There is no contain box, added padding, crop-to-fill, or stretching. Only the
screen corners are rounded. The original captures, including the macOS title bar and any
black bars already present in the captured player, remain unchanged.

The working canvas is 1774 × 887. Positions refer to the top-left of each screen:

| Capture | Screen position | Resized screen | Bezel per side | Outer frame |
|---|---|---|---|---|
| macOS | 845, 139 | 744 × 427 | 12 px | 768 × 451 |
| Android landscape | 650, 619 | 420 × 189 | 10 px | 440 × 209 |
| Android portrait | 1552, 369 | 189 × 420 | 10 px | 209 × 440 |

Both phones use the same scale and 20:9 display proportions, rotated between orientations.
The desktop height is rounded to the nearest pixel. Screen widths, positions, bezel widths,
and corner radii are set in the `prepare_device` calls; heights are always calculated from
the screenshots. A bounds check rejects any device that would extend beyond the canvas.

The script exports the finished banner at WebP quality 92 and also rebuilds
[readme-feature-frame.webp](readme-feature-frame.webp), an empty-frame preview. That preview
is an output, not a source with fixed screen openings. PNG intermediates and the lossless
WebP background avoid repeated compression during rebuilding.

The full screenshots remain available separately in the README. The separate
[Play Store artwork](play-store/README.md) uses a simpler design.

## Mobile availability, 2026-09-11

The banner says **Available on Android and iOS**, with the current Android head beside
"Android" and the Apple logo beside "iOS". The Android head and name use **#34A853**,
matching Google's current [flat Android asset](https://developer.android.com/static/images/brand/android-head_flat.svg).
The Apple logo and "iOS" use **#FFFFFF** against the dark background.
"Available on" and "and" stay neutral **#DDD2E5**.

The listings were verified on [Google Play](https://play.google.com/store/apps/details?id=com.yuroyami.syncplay)
and the [App Store](https://apps.apple.com/us/app/synkplay/id6760187432). Their clickable
download links remain in the README's Download section; the banner alt text also names
both platforms and stores.

[render-readme-availability.sh](render-readme-availability.sh), invoked automatically by the
banner renderer, rebuilds [readme-feature-availability.webp](readme-feature-availability.webp)
as a transparent 520 × 56 image. It uses the bundled Lexend font, measures the text to set
the spacing, and renders the actual SVG marks with librsvg. The [Android SVG](third-party/android/head.svg)
is Google's unchanged download; the [Apple SVG](badges/marks/apple.svg) is the same official-badge
mark used by the existing App Store button. Logo shapes and colors remain exact source assets.

The availability image is placed at (72, 596). The existing
[Google Play](badges/google-play.png) and [App Store](badges/app-store.png) badge PNGs are each
resized proportionally to 240 × 72 and placed at (84, 654) and (348, 654), with a 24 px gap.
They sit above the compatibility footer and clear of the landscape phone.

Android is a trademark of Google LLC. The Android robot is reproduced or modified from
work created and shared by Google and used according to terms described in the
[Creative Commons 3.0 Attribution License](https://creativecommons.org/licenses/by/3.0/).
See Google's [brand asset source and attribution](https://developer.android.com/distribute/marketing-tools/brand-guidelines).

### Brand styling prompt

The **built-in image-generation tool** previewed this treatment using the current banner
and original brand marks as references. The final availability line is rendered from the
SVG sources and font by the script above, preserving exact shapes, colors, and spelling.

Use case: precise-object-edit.
Reference image 1 is the current finished Synkplay banner and is the edit target.
Reference image 2 is Google's current flat Android head logo. Reference image 3 is the white Apple logo. Use these exact modern brand shapes.
Change only the single availability line above the store badges. It currently reads "Available on Android & iOS".
Replace it with this inline arrangement, all on one line: neutral text "Available on", a small Android head logo, the word "Android", neutral text "and", a small Apple logo, the word "iOS".
Android logo and the entire word "Android" must both be Android green #34A853. Apple logo and the entire word "iOS" must both be white #FFFFFF. "Available on" and "and" stay muted warm ivory #DDD2E5.
No literal placeholder parentheses, quotation marks, emoji, or ampersand. Use actual logo shapes, optically aligned beside their text. Keep a compact professional baseline and comfortable small gaps.
Keep the line left aligned at the same location and retain a similar font size, fitting it into the available width above the two store badges. Do not alter, re-create, recolor, reposition or resize anything else: the existing screenshots, corrected device frames, all store badges, lower compatibility note, original Syncplay logo, background, headline, subtitle and main Synkplay wordmark remain unchanged.
Output one finished revised banner at its original 1600 by 800 aspect ratio.

## Compatibility footer, 2026-09-11

The lower-left note reads:

> Compatible with original official Syncplay for PC<br>
> syncplay.pl · github.com/Syncplay/syncplay

The addresses were verified against the [official Syncplay website](https://syncplay.pl/)
and [repository](https://github.com/Syncplay/syncplay). The banner's README alt text includes
the note and both addresses.

The renderer adds [readme-feature-footer.webp](readme-feature-footer.webp) at (129, 752),
then composites the actual [official logo](third-party/syncplay/logo.png) at (84, 764),
scaled proportionally to 44 × 44. Its orange-red colors and shape are unchanged.
The logo and accompanying [Apache 2.0 license](third-party/syncplay/LICENSE) were copied from
[upstream revision 2af7ace347f437d44cd809a65c049e82bb4027fb](https://github.com/Syncplay/syncplay/blob/2af7ace347f437d44cd809a65c049e82bb4027fb/syncplay/resources/syncplay.png).

The **built-in image-generation tool** supplied the footer text. Only its 516 × 76 region
at (80, 778) was retained, resized proportionally to 490 × 72, and given softly feathered
edges for compositing. The existing background and device layout remain the same sources.
The footer is saved as lossless WebP with transparency; the official logo is composited
separately from its original PNG.

### Footer prompt

Use case: precise-object-edit.
Edit target: the existing Synkplay 1774 by 887 banner BACKGROUND in image 1.
Add only a discreet two-line compatibility footer at the bottom left. Keep every existing pixel, all text, branding, logo, abstract plum background, positions, colors and composition elsewhere unchanged. No screens or devices should be added to this background.
New text, verbatim:
Line 1: "Compatible with original official Syncplay for PC"
Line 2: "syncplay.pl  ·  github.com/Syncplay/syncplay"
Placement is critical: use the same 1774 by 887 coordinate system as the reference. Left-align BOTH text lines at x=138. Line 1 baseline y=779; line 2 baseline y=811. Keep all footer text inside x=138..622 and y=758..818. Line 1 is approximately 19 pixels high in a clean, readable regular humanist sans-serif that matches the existing subheading, colored muted warm ivory #ddd2e5. Line 2 is approximately 17 pixels high, colored soft lilac #b9a1ca. Keep both lines on a single line each, no wrapping. Subtle supporting-note hierarchy, much smaller than the main tagline. No bold box, panel, border, divider, glow or decorative marks.
Leave the space x=84..125, y=769..811 COMPLETELY EMPTY, retaining the original background there: the actual official Syncplay logo will be composited separately from its original asset.
Critical: exact spelling and capitalization, especially original Syncplay with a c, website syncplay.pl, and github.com/Syncplay/syncplay. Do not alter the existing Synkplay wordmark with a k. Do not move or change the main headline or subtitle. Preserve 2:1 aspect ratio and output at 1774 by 887 if possible. Output one finished background with the two footer text lines only added.

## Background repair, 2026-09-11

The **built-in image-generation tool** removed the old mismatched device frames and connecting
lines from the previous empty-frame artwork, reconstructing the plum background while
preserving the branding and copy. The prompt below produced the background only. The rebuild
script supplies all final device geometry, connectors, and actual screenshot pixels; no
generated screen content or interface labels are used.

### Prompt

Use case: precise-object-edit.
Asset type: existing Synkplay README banner, background repair for accurate device-frame compositing.
Input image 1 is the edit target, the existing 1774 by 887 banner with three EMPTY black device frames.
Primary request: Remove all three device frames and their black interiors, shadows, highlights, AND the thin luminous connecting lines and dots below them. Seamlessly reconstruct the existing dark plum abstract wing-texture background behind those removed objects. The result must be this same banner's background and existing left-hand branding/copy only, with a clean empty right-hand area ready for separately composited real screenshots.
Critical invariants: preserve the image's exact 2:1 aspect ratio, original composition, color palette, textured purple wing shapes, subtle existing lighting, logo, typography, left-hand copy and their positions. Do not shift or resize anything that is being retained.
Keep text verbatim: "Synkplay"; "Your video."; "Everyone in sync."; "Watch together on phones and computers."
Do not add any screens, bezels, frames, devices, stands, UI, symbols, connector lines, dots, text or decoration. Reconstruct only the background where the removed devices and lines were. Maintain the original visual identity and low-key dark plum look. Output one finished seamless 2:1 banner background at high resolution.
