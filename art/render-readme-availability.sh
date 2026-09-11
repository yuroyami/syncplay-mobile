#!/usr/bin/env bash
# Render the platform line from the bundled font and original SVG brand marks.
set -euo pipefail

art_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
label_tmp="$(mktemp -d "${TMPDIR:-/tmp}/synkplay-platforms.XXXXXX")"
trap 'rm -rf -- "$label_tmp"' EXIT
font="$art_dir/../shared/src/commonMain/composeResources/font/Lexend_variable.ttf"

# Render SVGs above the final size, preserving their original colors and geometry.
rsvg-convert --width 160 --output "$label_tmp/android-large.png" "$art_dir/third-party/android/head.svg"
rsvg-convert --height 116 --output "$label_tmp/apple-large.png" "$art_dir/badges/marks/apple.svg"
magick "$label_tmp/android-large.png" -filter Lanczos -resize 40x "$label_tmp/android.png"
magick "$label_tmp/apple-large.png" -filter Lanczos -resize x29 "$label_tmp/apple.png"

text_width() {
  magick -background none -font "$font" -pointsize 24 "label:$1" -format '%w' info:
}

# Measure the text, then use consistent gaps between neutral copy and each brand.
android_mark_x=$((12 + $(text_width 'Available on') + 12))
android_text_x=$((android_mark_x + 40 + 8))
and_x=$((android_text_x + $(text_width 'Android') + 14))
apple_mark_x=$((and_x + $(text_width 'and') + 14))
ios_x=$((apple_mark_x + 23 + 8))
if ((ios_x + $(text_width 'iOS') > 508)); then
  printf 'Platform label exceeds the banner inset.\n' >&2
  exit 1
fi

magick -size 520x56 xc:none -font "$font" -pointsize 24 -fill '#DDD2E5' \
  -draw "text 12,34 'Available on'" -draw "text $and_x,34 'and'" \
  -fill '#34A853' -draw "text $android_text_x,34 'Android'" \
  -fill '#FFFFFF' -draw "text $ios_x,34 'iOS'" \
  "$label_tmp/android.png" -geometry "+$android_mark_x+11" -compose Over -composite \
  "$label_tmp/apple.png" -geometry "+$apple_mark_x+6" -compose Over -composite \
  -strip -define webp:lossless=true -define webp:method=6 "$art_dir/readme-feature-availability.webp"
