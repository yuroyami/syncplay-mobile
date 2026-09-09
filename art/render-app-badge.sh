#!/usr/bin/env bash
# Frame the original app artwork without changing its logo or background colors.
set -euo pipefail

art_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
badge_tmp="$(mktemp -d "${TMPDIR:-/tmp}/synkplay-badge.XXXXXX")"
trap 'rm -rf -- "$badge_tmp"' EXIT

rsvg-convert --width 1024 --height 1024 \
  --output "$badge_tmp/mask.png" "$art_dir/app-badge-mask.svg"
rsvg-convert --width 1024 --height 1024 \
  --output "$badge_tmp/surround.png" "$art_dir/app-badge-surround.svg"
rsvg-convert --width 1254 --height 1254 \
  --output "$badge_tmp/logo.png" "$art_dir/synkplay_logo.svg"

# Use the actual white-grey app background; only resize and round its outline.
magick "$art_dir/../shared/src/commonMain/composeResources/drawable/synkplay_bg.png" \
  -filter Lanczos -resize 832x832 "$badge_tmp/background.png"
magick -size 1024x1024 xc:none "$badge_tmp/background.png" \
  -gravity northwest -geometry +96+88 -compose Over -composite \
  "$badge_tmp/mask.png" -geometry +0+0 -compose DstIn -composite "$badge_tmp/face.png"

# Trim transparent margins only, retaining the original logo's colors/cutouts.
magick "$badge_tmp/logo.png" -trim +repage -filter Lanczos -resize 648x648 \
  "$badge_tmp/mark.png"

magick "$badge_tmp/surround.png" "$badge_tmp/face.png" -compose Over -composite \
  "$badge_tmp/mark.png" -gravity center -geometry +0-8 -compose Over -composite \
  -colorspace sRGB -depth 8 -background black -alpha background -strip \
  "$badge_tmp/badge.png"
magick "$badge_tmp/badge.png" -filter Lanczos -resize 512x512 \
  -depth 8 -background black -alpha background -strip "$badge_tmp/badge-512.png"

cp "$badge_tmp/badge.png" "$art_dir/synkplay_app_badge.png"
cp "$badge_tmp/badge-512.png" "$art_dir/synkplay_app_badge_512.png"
