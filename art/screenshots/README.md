# App screenshots

These are real captures of the app. The PNG originals are unchanged. The README loads smaller WebP
copies and links to the originals.

| View | Original PNG | README WebP | WebP bytes |
|---|---|---|---:|
| Android home, portrait | [1080 × 2400](android-portrait.png) | [720 × 1600](android-portrait.webp) | 33,614 |
| Android room, landscape | [2400 × 1080](android-landscape.png) | [1920 × 864](android-landscape.webp) | 93,292 |
| macOS room | [3420 × 1962](macos.png) | [1920 × 1101](macos.webp) | 116,408 |

The Android captures come from a physical Android phone. The macOS capture is a window capture.
The captures contain no generated or retouched screen content.

## Refresh the WebP copies

Run these commands from the repository root. They need ImageMagick.

```bash
magick art/screenshots/android-portrait.png -resize 720x1600 -strip -quality 90 -define webp:method=6 art/screenshots/android-portrait.webp
```

```bash
magick art/screenshots/android-landscape.png -resize 1920x864 -strip -quality 90 -define webp:method=6 art/screenshots/android-landscape.webp
```

```bash
magick art/screenshots/macos.png -resize 1920x -strip -quality 90 -define webp:method=6 art/screenshots/macos.webp
```

These copies keep the full image and its proportions. ImageMagick rounds the macOS height to the
nearest pixel. Together, the three copies are 243,314 bytes.

## Play Store copies

The Play Store copies are in `fastlane/metadata/android/en-US/images/phoneScreenshots/`. They are
24-bit RGB PNGs without transparency. Plain `#141119` padding makes the portrait copy
1350 × 2400 (9:16) and the landscape copy 2400 × 1350 (16:9). Every original pixel stays, centered,
with no crop and no scaling.

```bash
magick art/screenshots/android-portrait.png -background '#141119' -gravity center -extent 1350x2400 -alpha remove -alpha off PNG24:fastlane/metadata/android/en-US/images/phoneScreenshots/android-portrait.png
```

```bash
magick art/screenshots/android-landscape.png -background '#141119' -gravity center -extent 2400x1350 -alpha remove -alpha off PNG24:fastlane/metadata/android/en-US/images/phoneScreenshots/android-landscape.png
```

## AltStore feed

The AltStore feed, `altstore_yuroyami.json`, has no screenshots. Its
[`screenshots` field is optional](https://faq.altstore.io/developers/make-a-source). Add iOS
captures there when they exist.
