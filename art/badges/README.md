# Download badges

This folder holds one family of download buttons for GitHub, IzzyOnDroid, Google Play, the App
Store, AltSource and the direct IPA download. They are custom Synkplay buttons that use the brand
mark of each destination, with one shared layout and type system.

| GitHub | IzzyOnDroid | Google Play |
|---|---|---|
| ![Get it on GitHub](get-it-on-github.png) | ![Get it on IzzyOnDroid](IzzyOnDroid.png) | ![Get it on Google Play](google-play.png) |
| **App Store** | **AltSource** | **IPA** |
| ![Download on the App Store](app-store.png) | ![Add AltSource](AltSource_Blue.png) | ![Download IPA](Download_Blue.png) |

Keep the file names. The README and the release notes link to them.

## Shared geometry

Every SVG has `viewBox="0 0 240 72"` and an intrinsic size of **480 × 144**. Every PNG is also
**480 × 144**, which covers a 160 × 48 display at 3× density.

- The README shows the badges 140 pixels wide, and the release notes show them 150 pixels wide.
  Set the width, and keep the aspect ratio.
- The release notes load each image through the commit that the release workflow checks out. So
  an old release page keeps its images when the artwork changes later.
- A badge has no transparent padding. Only the rounded corners are transparent. Put the space
  between buttons in the surrounding layout.

All geometry below uses SVG viewBox units:

| Property | Shared value |
|---|---|
| Plate | x/y 0.5; width 239; height 71; fill `#080808` |
| Border | 1; `#91959C` |
| Corner radius | 8 |
| Icon slot | x 14; y 16; 40 × 40; fitted with the proportions kept, centered |
| Text left ink edge | 68 |
| Header | Lexend 400; size 9; tracking 0.5; baseline 25 |
| Title | Lexend 450; size 22; baseline 51 |
| Right text limit | 226 |

The plates and the PNG alpha channels match exactly. Each brand mark keeps its natural proportions
inside the shared slot. All titles use one font size, whatever their length.

## Rebuild

Edit `generate.py` to change the layout or the text. Edit `marks/*.svg` to change a mark.

The script needs `uv` and `rsvg-convert` (from librsvg). On macOS, you can install librsvg with
Homebrew:

```bash
brew install librsvg
```

Rebuild the badges from the repository root:

```bash
uv run art/badges/generate.py
```

- The script pins FontTools through its inline dependency metadata.
- It reads `shared/src/commonMain/composeResources/font/Lexend_variable.ttf` and turns the text
  into outlines in each SVG. Then librsvg renders each PNG.
- The SVGs contain no font dependency, no script, no remote image and no external stylesheet.
- The rebuild does not contact any brand website.

Commit the generator, the marks and the regenerated SVG and PNG pairs together.

## Mark sources

- GitHub: `GitHub Logos/SVG/GitHub_Invertocat_White.svg` from the
  [official logo archive](https://brand.github.com/GitHub_Logos.zip). The visible path is kept.
- Apple: the two Apple-mark paths from Apple's
  [official App Store badge](https://developer.apple.com/assets/elements/icons/download-on-the-app-store/download-on-the-app-store.svg).
- IzzyOnDroid: the [official logo](https://codeberg.org/IzzyOnDroid/assets/raw/branch/main/IzzyOnDroidLogo.svg),
  with its [source and usage terms](https://codeberg.org/IzzyOnDroid/assets/raw/branch/main/README.md).
  The upstream logo combines vector shapes with a raster Android mascot. This set keeps those
  shapes and embeds the mascot, reduced to 128 × 128. That resolution is enough for a mascot of
  about 50 pixels in the PNG. **This mark is not entirely vector.** The SVG is still
  self-contained.
- Google Play, IPA and AltSource: compact vector redraws of the marks in older versions of
  `google-play.png`, `Download_Blue.png` and `AltSource_Blue.png`. Git history keeps those older
  files.
