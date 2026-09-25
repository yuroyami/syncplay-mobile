# Google Play artwork

Upload these two PNGs in Play Console, under Main store listing, then Graphics.

| Asset | File | Export |
|---|---|---|
| Feature graphic | [feature-graphic.png](feature-graphic.png) | 1024 × 500, 24-bit RGB PNG, no alpha |
| App icon | [icon.png](icon.png) | 512 × 512, 32-bit RGBA PNG, full square |

The feature graphic shows two linked play symbols and a short headline. The icon shows the
Synkplay logo on a solid plum plate. The editable master of the icon is [icon.svg](icon.svg).
These are store assets only. The README banner and the launcher icons have their own sources.

Suggested alt text for the feature graphic: **Synkplay. Watch together. Two connected play symbols
in violet and coral.**

## Sources and export

An AI image generator made the feature graphic from the prompt below. The export step resized the
generated image once to exactly 1024 × 500 with the Lanczos filter, and saved it without alpha.

[icon.svg](icon.svg) contains the paths and the gradients of the canonical
[Synkplay SVG](../synkplay_logo.svg) without change, so the icon keeps the geometry of the logo.
Google Play adds the outer corner mask and the shadow of the icon.

For the size and format rules, see the
[Google Play asset requirements](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en)
and the
[icon specifications](https://developer.android.com/distribute/google-play/resources/icon-design-specifications).

To export the icon again, run this command from the repository root. It needs `rsvg-convert` (from
librsvg) and ImageMagick.

```bash
rsvg-convert --width 512 --height 512 art/play-store/icon.svg |
  magick png:- -colorspace sRGB -alpha on -depth 8 -strip \
    -define png:compression-level=9 PNG32:art/play-store/icon.png
```

## Feature graphic prompt

This is the exact prompt that produced the feature graphic:

```text
Use case: ads-marketing.
Asset type: finished Google Play Store feature graphic for Synkplay, an app that synchronizes video playback between friends.
Primary request: a completely new, much simpler design than a device-mockup banner. Make a remarkably clean, flat graphic that reads instantly at thumbnail size. Landscape 1024 by 500 aspect ratio, preferably generate larger at exactly the same 2.048:1 proportion.
Scene/backdrop: uniform rich plum #2C1840, edge to edge, opaque.
Style/medium: precise flat editorial graphic design, immaculate simple geometry and confident modern humanist sans-serif typography. No photographic or 3D elements, no texture, no gradients in background, no shadows.
Composition: an airy horizontal composition with two balanced groups within the central safe area. On the left, very large warm-ivory text on two lines, exactly "Watch\ntogether." Above it, a small restrained brand label exactly "Synkplay". On the right, one very simple synchronization symbol: two equal circular discs side by side, one gentle ultraviolet #9879EF and one dusty coral #D86B75, each containing exactly the same small solid plum right-pointing play triangle. The discs are joined by one short straight thin lilac line between them, aligned through their centers. This is an abstract symbol of playing together, not product UI. Keep the symbol compact and clear with generous negative space around it. Keep ALL text and complete discs at least 85 pixels from left/right edges and 70 pixels from top/bottom edges relative to the final 1024x500 canvas. Inset everything comfortably.
Text verbatim: "Synkplay" and "Watch together." only. Spell brand S-y-n-k-p-l-a-y.
Color palette: plum #2C1840, gentle ultraviolet #9879EF, dusty coral #D86B75, warm ivory #F5F0EB. Use only these and antialiasing.
Constraints: exactly two discs and two identical play triangles; no wing logo in this banner because the store icon already provides it. No device frames, screenshots, fake UI, scenery, characters, film footage, ornaments, floating particles, lines other than the one connector, badges, CTA buttons, third-party logos, extra text, gradients, glossy effects, bevels, drop shadows or watermarks. Output one finished full-bleed banner, no presentation sheet.
```
