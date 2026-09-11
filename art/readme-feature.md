# README feature graphic

[readme-feature.webp](readme-feature.webp) is the 1600 × 800 README banner.
Its three screens show the actual Android portrait, Android landscape and macOS captures
in [screenshots/](screenshots/README.md). The macOS image was supplied by the maintainer.

## Rebuild

```sh
bash art/render-readme-feature.sh
```

Requires ImageMagick. The script starts with [readme-feature-background.webp](readme-feature-background.webp),
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
