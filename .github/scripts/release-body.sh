#!/usr/bin/env bash
# Writes the GitHub release notes for one version: a header with the logo and the store
# buttons, then the changelog, the dependencies, the translation status and the downloads.
#
#   release-body.sh <dir with the release files> <dependencies.md>
#
# Reads VERSION, GITHUB_REPOSITORY and IOS_MIN_VERSION from the environment, CHANGELOG.md and
# the git tags from the working tree. Writes release-body.md (the GitHub release) and
# release-notes.md (this version's changelog section alone, which the AltStore feed carries).
set -euo pipefail

FILES=$1
DEPS=$2
: "${VERSION:?VERSION is not set}" "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is not set}"
BASE="https://github.com/${GITHUB_REPOSITORY}/releases/download/v${VERSION}"
IOS_MIN=${IOS_MIN_VERSION:-15.0}
# minSdk 26 is Android 8.0. gradle.properties carries the number, this carries the name people know.
ANDROID_MIN="8.0"

# The previous release is the newest v* tag that is not this version, so a re-run of the same
# version still measures the changelog from the release before it.
PREV=$(git tag --list 'v*' --sort=-v:refname | grep -vx "v${VERSION}" | head -1 || true)
PREV=${PREV#v}

# This version's own section, for the AltStore feed and as a check that the section exists.
awk -v ver="## ${VERSION}" '
  $0 == ver { found = 1; next }
  found && /^## / { exit }
  found { print }
' CHANGELOG.md > release-notes.md
if [ ! -s release-notes.md ]; then
  echo "::error::CHANGELOG.md has no section for ${VERSION}" >&2
  exit 1
fi

# CHANGELOG.md is newest first, so everything above the previous release's heading is new.
# When that heading is absent (the previous release predates the file) the whole file is.
awk -v stop="## ${PREV}" '
  !started && /^## / { started = 1 }
  !started { next }
  $0 == stop { exit }
  { print }
' CHANGELOG.md > release-changelog-raw.md

# A release usually carries one version, and the summary line already names it, so repeating it
# as a heading inside the fold says the same thing twice. When a release carries more than one
# version (a section that never got its own tag), every heading stays and steps down two levels.
SECTIONS=$(grep -c '^## ' release-changelog-raw.md || true)
if [ "$SECTIONS" = "1" ]; then
  sed -e '/^## /d' release-changelog-raw.md | sed -e 's/^### /#### /' -e '/./,$!d' > release-changelog.md
else
  sed -e 's/^### /#### /' -e 's/^## /### /' release-changelog-raw.md > release-changelog.md
fi
rm -f release-changelog-raw.md

size_mb() {
  local bytes
  bytes=$(stat -c%s "$1" 2>/dev/null || stat -f%z "$1")
  echo "$(( (bytes + 524288) / 1048576 )) MB"
}

# The first file matching a pattern, or nothing. Matched by pattern rather than by a spelled-out
# name, so the page follows whatever the build names its outputs.
asset() {
  local f
  for f in "$FILES"/$1; do
    [ -f "$f" ] || continue
    basename "$f"
    return 0
  done
  return 0
}

# One download line: the file name links to the asset, the size follows it.
download_line() {
  local file=$1
  printf '**[`%s`](%s/%s)** (%s)\n' "$file" "$BASE" "$file" "$(size_mb "$FILES/$file")"
}

FULL=$(asset "*-full-universal.apk")
EXO=$(asset "*-exo-only.apk")
IPA=$(asset "*-ios.ipa")
DMG=$(asset "*.dmg")
MSI=$(asset "*.msi")
DEB=$(asset "*.deb")

# Pin images to this checkout's commit. A version tag may predate new artwork on a rerun.
# Old release pages still keep immutable image URLs if the assets later move.
ASSET_COMMIT=$(git rev-parse --verify HEAD)
RAW="https://raw.githubusercontent.com/${GITHUB_REPOSITORY}/${ASSET_COMMIT}"
ALTSTORE="https://celloserenity.github.io/altdirect/?url=https://raw.githubusercontent.com/${GITHUB_REPOSITORY}/refs/heads/master/altstore_yuroyami.json"
WEBLATE="https://hosted.weblate.org/engage/syncplay-mobile/"

{
  # The logo sits beside the four store buttons, two per row, in one centred block.
  echo '<table align="center"><tr>'
  echo "<td align=\"center\" rowspan=\"2\"><img src=\"${RAW}/art/synkplay_app_badge_512.png\" width=\"128\" alt=\"Synkplay\"></td>"
  echo "<td align=\"center\"><a href=\"https://play.google.com/store/apps/details?id=com.yuroyami.syncplay\"><img src=\"${RAW}/art/badges/google-play.png\" width=\"150\" alt=\"Get it on Google Play\"></a></td>"
  echo "<td align=\"center\"><a href=\"https://apps.apple.com/us/app/synkplay/id6760187432\"><img src=\"${RAW}/art/badges/app-store.png\" width=\"150\" alt=\"Download on the App Store\"></a></td>"
  echo '</tr><tr>'
  echo "<td align=\"center\"><a href=\"https://apt.izzysoft.de/fdroid/index/apk/com.reddnek.syncplay\"><img src=\"${RAW}/art/badges/IzzyOnDroid.png\" width=\"150\" alt=\"Get it on IzzyOnDroid\"></a></td>"
  echo "<td align=\"center\"><a href=\"${ALTSTORE}\"><img src=\"${RAW}/art/badges/AltSource_Blue.png\" width=\"150\" alt=\"Add the AltStore source\"></a></td>"
  echo '</tr></table>'
  echo
  # True on the day a release is published. Both stores review a build before it appears.
  echo "> This release is not on Google Play or the App Store yet. Both take a few days to review it."
  echo

  echo "## Changelog"
  echo
  if [ -n "$PREV" ]; then
    echo "<details open><summary><b>${VERSION}</b> (everything since v${PREV})</summary>"
  else
    echo "<details open><summary><b>${VERSION}</b></summary>"
  fi
  echo
  cat release-changelog.md
  echo
  echo "</details>"
  echo

  echo "## Dependencies"
  echo
  echo "<details><summary><b>The main ones</b>: toolchain, network stack, video engines. Click to unfold.</summary>"
  echo
  cat "$DEPS"
  echo
  echo "</details>"
  echo

  echo "## Translations"
  echo
  echo "[![Translation status](https://hosted.weblate.org/widget/syncplay-mobile/svg-badge.svg)](${WEBLATE})"
  echo
  echo "Volunteers translate Synkplay on Weblate. [Add or fix a language](${WEBLATE}), no account setup beyond Weblate itself."
  echo

  echo "## Downloads"
  echo

  if [ -n "$FULL" ] || [ -n "$EXO" ]; then
    echo "### Android &nbsp;<img src=\"https://img.shields.io/badge/Android-${ANDROID_MIN}%2B-3DDC84?logo=android&logoColor=white\" alt=\"Android ${ANDROID_MIN} and up\" height=\"20\">"
    echo
  fi

  if [ -n "$FULL" ]; then
    echo "**Full universal build**"
    echo
    download_line "$FULL"
    echo
    echo "- ✔️ All three engines (ExoPlayer, mpv and KitePlayer)"
    echo "- ✔️ Works on every phone"
    echo
  fi

  if [ -n "$EXO" ]; then
    echo "**Lite IzzyOnDroid build**"
    echo
    download_line "$EXO"
    echo
    echo "- ✔️ Small, and works on every phone"
    echo "- ❌ ExoPlayer only (mpv and KitePlayer are left out)"
    echo
  fi

  if [ -n "$IPA" ]; then
    echo "### iOS &nbsp;<img src=\"https://img.shields.io/badge/iOS-${IOS_MIN}%2B-000000?logo=apple&logoColor=white\" alt=\"iOS ${IOS_MIN} and up\" height=\"20\">"
    echo
    download_line "$IPA"
    echo
    echo "To sideload, follow the [install guide](https://github.com/${GITHUB_REPOSITORY}/wiki/How-to-install-the-app-on-iOS). Otherwise get it from the [App Store](https://apps.apple.com/us/app/synkplay/id6760187432)."
    echo
  fi

  if [ -n "$DMG" ] || [ -n "$MSI" ] || [ -n "$DEB" ]; then
    echo "### Desktop"
    echo
    [ -n "$DMG" ] && { printf 'macOS: '; download_line "$DMG"; echo; }
    [ -n "$MSI" ] && { printf 'Windows: '; download_line "$MSI"; echo; }
    [ -n "$DEB" ] && { printf 'Linux (Debian and Ubuntu): '; download_line "$DEB"; echo; }
  fi
} > release-body.md

echo "release-body.md written: $(wc -l < release-body.md) lines, changelog since ${PREV:-the beginning}"
