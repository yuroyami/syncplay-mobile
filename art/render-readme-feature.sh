#!/usr/bin/env bash
# Build device frames around proportionally resized original screenshots.
set -euo pipefail

art_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
render_tmp="$(mktemp -d "${TMPDIR:-/tmp}/synkplay-hero.XXXXXX")"
trap 'rm -rf -- "$render_tmp"' EXIT

canvas_size=1774x887
bash "$art_dir/render-readme-availability.sh"
# Keep the official logo's original colors and shape; only resize its actual pixels.
magick "$art_dir/third-party/syncplay/logo.png" -filter Lanczos -resize 44x44 \
  "$render_tmp/syncplay-logo.png"
# Reuse the README's store artwork at equal sizes, preserving its 10:3 proportions.
for badge in google-play app-store; do
  magick "$art_dir/badges/$badge.png" -filter Lanczos -resize 240x \
    "$render_tmp/$badge.png"
done
magick "$art_dir/readme-feature-background.webp" "$art_dir/readme-feature-availability.webp" \
  -geometry +72+596 -compose Over -composite \
  "$render_tmp/google-play.png" -geometry +84+654 -compose Over -composite \
  "$render_tmp/app-store.png" -geometry +348+654 -compose Over -composite \
  "$art_dir/readme-feature-footer.webp" \
  -geometry +129+752 -compose Over -composite \
  "$render_tmp/syncplay-logo.png" -geometry +84+764 -compose Over -composite \
  "$render_tmp/banner.png"
cp "$render_tmp/banner.png" "$render_tmp/frame.png"

# Sizes and positions below describe the screen, not an independently sized box.
# ImageMagick computes the height from the source; the bezel follows that result.
prepare_device() {
  local name="$1" image_name="$2" width="$3" x="$4" y="$5" bezel="$6" radius="$7"
  local screen_width screen_height outer_width outer_height outer_radius
  magick "$art_dir/screenshots/$image_name" -filter Lanczos -resize "${width}x" \
    "$render_tmp/$name-screen.png"
  read -r screen_width screen_height < <(magick identify -format '%w %h\n' "$render_tmp/$name-screen.png")
  outer_width=$((screen_width + 2 * bezel))
  outer_height=$((screen_height + 2 * bezel))
  outer_radius=$((radius + bezel))
  if ((x < bezel || y < bezel || x + screen_width + bezel > 1774 || y + screen_height + bezel > 887)); then
    printf 'Device %s does not fit the banner. Adjust its width or position.\n' "$name" >&2
    exit 1
  fi

  # A thin plum metal rim and dark inner lip, with consistent thickness all around.
  magick -size "${outer_width}x${outer_height}" xc:black -fill white \
    -draw "roundrectangle 0,0 $((outer_width - 1)),$((outer_height - 1)) $outer_radius,$outer_radius" \
    "$render_tmp/$name-body-mask.png"
  magick -size "${outer_width}x${outer_height}" 'gradient:#483357-#241a30' \
    "$render_tmp/$name-body-mask.png" -alpha off -compose CopyOpacity -composite \
    -fill none -stroke '#9679a7' -strokewidth 1 \
    -draw "roundrectangle 0.5,0.5 $((outer_width - 2)).5,$((outer_height - 2)).5 $outer_radius,$outer_radius" \
    -stroke '#19121f' -strokewidth 2 \
    -draw "roundrectangle 3,3 $((outer_width - 4)),$((outer_height - 4)) $((outer_radius - 3)),$((outer_radius - 3))" \
    -fill '#070609' -stroke '#09070d' -strokewidth 1 \
    -draw "roundrectangle $((bezel - 1)),$((bezel - 1)) $((bezel + screen_width)),$((bezel + screen_height)) $((radius + 1)),$((radius + 1))" \
    "$render_tmp/$name-body.png"

  # Clip only the physical screen corners. Never add padding, crop to fill, or stretch.
  magick -size "${screen_width}x${screen_height}" xc:black -fill white \
    -draw "roundrectangle 0,0 $((screen_width - 1)),$((screen_height - 1)) $radius,$radius" \
    "$render_tmp/$name-screen-mask.png"
  magick "$render_tmp/$name-screen.png" "$render_tmp/$name-screen-mask.png" \
    -alpha off -compose CopyOpacity -composite "$render_tmp/$name-clipped.png"
  magick "$render_tmp/$name-body.png" "$render_tmp/$name-clipped.png" \
    -geometry "+$bezel+$bezel" -compose Over -composite "$render_tmp/$name-device.png"

  # Layer each device's shadow with its body so overlapping devices stay in order.
  magick -size "$canvas_size" xc:none "$render_tmp/$name-body.png" \
    -geometry "+$((x - bezel))+$((y - bezel + 10))" -compose Over -composite \
    -channel A -blur 0x12 -evaluate multiply 0.65 +channel \
    -fill black -colorize 100 "$render_tmp/$name-shadow.png"
  printf '%s %s %s %s\n' "$x" "$y" "$bezel" "$((y + screen_height + bezel))" > "$render_tmp/$name-geometry.txt"
  printf '%s: screen %sx%s, frame %sx%s\n' "$name" "$screen_width" "$screen_height" "$outer_width" "$outer_height"
}

prepare_device desktop macos.png 744 845 139 12 8
# Both phone screens use the same scale: 420x189 landscape and 189x420 portrait.
prepare_device landscape android-landscape.png 420 650 619 10 12
prepare_device portrait android-portrait.png 189 1552 369 10 12

connectors=()
for entry in 'landscape 860' 'desktop 1290' 'portrait 1646'; do
  read -r name center_x <<< "$entry"
  read -r _ _ _ bottom < "$render_tmp/$name-geometry.txt"
  connectors+=(-draw "line $center_x,$bottom $center_x,824")
done
magick -size "$canvas_size" xc:none -fill none -stroke '#c98ad8' -strokewidth 1.5 \
  -draw 'line 860,824 1646,824' "${connectors[@]}" \
  -fill '#e5a1e6' -stroke '#f3c7f0' -strokewidth 1 \
  -draw 'circle 860,824 868,824' -draw 'circle 1290,824 1301,824' \
  -draw 'circle 1646,824 1654,824' "$render_tmp/connectors.png"
magick "$render_tmp/connectors.png" -channel A -blur 0x7 +channel "$render_tmp/glow.png"
for layer in banner frame; do
  magick "$render_tmp/$layer.png" "$render_tmp/glow.png" -compose Over -composite \
    "$render_tmp/connectors.png" -compose Over -composite "$render_tmp/$layer.png"
done

for name in desktop landscape portrait; do
  read -r x y bezel _ < "$render_tmp/$name-geometry.txt"
  for layer in banner frame; do
    device_layer=device
    if [[ "$layer" == frame ]]; then device_layer=body; fi
    magick "$render_tmp/$layer.png" "$render_tmp/$name-shadow.png" -compose Over -composite \
      "$render_tmp/$name-$device_layer.png" -geometry "+$((x - bezel))+$((y - bezel))" \
      -compose Over -composite "$render_tmp/$layer.png"
  done
done

# The empty-frame preview is an output. It is never the source of the screen geometry.
magick "$render_tmp/frame.png" -colorspace sRGB -strip -quality 92 \
  -define webp:method=6 "$art_dir/readme-feature-frame.webp"
magick "$render_tmp/banner.png" -filter Lanczos -resize 1600x800 \
  -colorspace sRGB -strip -quality 92 -define webp:method=6 "$art_dir/readme-feature.webp"

# Give changed artwork a new README URL so cached images cannot hide an update.
banner_version="$(magick identify -format '%#' "$art_dir/readme-feature.webp")"
banner_version="${banner_version:0:12}"
awk -v version="$banner_version" '{
  sub(/src="art\/readme-feature\.webp(\?v=[^"]*)?"/,
      "src=\"art/readme-feature.webp?v=" version "\"")
  print
}' "$art_dir/../README.MD" > "$render_tmp/README.MD"
cat "$render_tmp/README.MD" > "$art_dir/../README.MD"
