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
# android.minSdk=26 in gradle.properties.
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

# One version: drop its heading, since the fold's title covers it.
# Several versions (an untagged section in between): keep every heading.
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

# The first file matching a pattern, or nothing.
asset() {
  local f
  for f in "$FILES"/$1; do
    [ -f "$f" ] || continue
    basename "$f"
    return 0
  done
  return 0
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
  # Logo on the left, store buttons in a 2x2 grid.
  echo '<table align="center"><tr>'
  echo "<td align=\"center\" rowspan=\"2\"><img src=\"${RAW}/art/synkplay_app_badge_512.png\" width=\"128\" alt=\"Synkplay\"></td>"
  echo "<td align=\"center\"><a href=\"https://play.google.com/store/apps/details?id=com.yuroyami.syncplay\"><img src=\"${RAW}/art/badges/google-play.png\" width=\"150\" alt=\"Get it on Google Play\"></a></td>"
  echo "<td align=\"center\"><a href=\"https://apps.apple.com/us/app/synkplay/id6760187432\"><img src=\"${RAW}/art/badges/app-store.png\" width=\"150\" alt=\"Download on the App Store\"></a></td>"
  echo '</tr><tr>'
  echo "<td align=\"center\"><a href=\"https://apt.izzysoft.de/fdroid/index/apk/com.reddnek.syncplay\"><img src=\"${RAW}/art/badges/IzzyOnDroid.png\" width=\"150\" alt=\"Get it on IzzyOnDroid\"></a></td>"
  echo "<td align=\"center\"><a href=\"${ALTSTORE}\"><img src=\"${RAW}/art/badges/AltSource_Blue.png\" width=\"150\" alt=\"Add the AltStore source\"></a></td>"
  echo '</tr></table>'
  echo
  # Store review lags the GitHub release by a few days.
  echo "> This release is not on Google Play or the App Store yet."
  echo

  if [ -n "$PREV" ]; then
    echo "<details open><summary><h2>Changelog <sub>since v${PREV}</sub></h2></summary>"
  else
    echo "<details open><summary><h2>Changelog</h2></summary>"
  fi
  echo
  cat release-changelog.md
  echo
  echo "</details>"
  echo

  echo "## Translations &nbsp;<a href=\"${WEBLATE}\"><img src=\"https://hosted.weblate.org/widget/syncplay-mobile/svg-badge.svg\" alt=\"Translation status\" height=\"20\"></a>"
  echo
  echo "Help translate Synkplay on [Weblate](${WEBLATE})."
  echo

  echo "<details><summary><h2>Downloads</h2></summary>"
  echo

  # Every table has two columns of the same fixed width, so all of them line up.
  COL=380

  if [ -n "$FULL" ] || [ -n "$EXO" ]; then
    echo "<table>"
    echo "<tr><th colspan=\"2\">Android &nbsp;<img src=\"https://img.shields.io/badge/${ANDROID_MIN}%2B-3DDC84?logo=android&logoColor=white&label=\" alt=\"Android ${ANDROID_MIN} and up\" height=\"20\"></th></tr>"
    printf '<tr><th width="%s">Full universal build</th><th width="%s">Lite IzzyOnDroid build</th></tr>\n' "$COL" "$COL"
    printf '<tr>'
    for f in "$FULL" "$EXO"; do
      if [ -n "$f" ]; then
        printf '<td align="center"><a href="%s/%s"><b>%s</b></a><br><sub>%s</sub></td>' \
          "$BASE" "$f" "$f" "$(size_mb "$FILES/$f")"
      else
        printf '<td align="center">Not in this release</td>'
      fi
    done
    printf '</tr>\n'
    echo "<tr><td>✔️ All three engines (ExoPlayer, mpv and KitePlayer)</td><td>❌ ExoPlayer only, no mpv or KitePlayer</td></tr>"
    echo "</table>"
    echo
  fi

  if [ -n "$IPA" ]; then
    echo "<table>"
    echo "<tr><th colspan=\"2\">iOS &nbsp;<img src=\"https://img.shields.io/badge/${IOS_MIN}%2B-000000?logo=apple&logoColor=white&label=\" alt=\"iOS ${IOS_MIN} and up\" height=\"20\"></th></tr>"
    printf '<tr><td width="%s" align="center"><a href="%s/%s"><b>%s</b></a><br><sub>%s</sub></td>' \
      "$COL" "$BASE" "$IPA" "$IPA" "$(size_mb "$FILES/$IPA")"
    printf '<td width="%s">To sideload, follow the <a href="https://github.com/%s/wiki/How-to-install-the-app-on-iOS">install guide</a>. Otherwise, get it from the <a href="https://apps.apple.com/us/app/synkplay/id6760187432">App Store</a>.</td></tr>\n' \
      "$COL" "$GITHUB_REPOSITORY"
    echo "</table>"
    echo
  fi

  if [ -n "$DMG" ] || [ -n "$MSI" ] || [ -n "$DEB" ]; then
    echo "<table>"
    echo "<tr><th colspan=\"2\">Desktop</th></tr>"
    for pair in "macOS:$DMG" "Windows:$MSI" "Linux:$DEB"; do
      label=${pair%%:*}
      file=${pair#*:}
      [ -n "$file" ] || continue
      printf '<tr><td width="%s">%s</td><td width="%s" align="center"><a href="%s/%s"><b>%s</b></a><br><sub>%s</sub></td></tr>\n' \
        "$COL" "$label" "$COL" "$BASE" "$file" "$file" "$(size_mb "$FILES/$file")"
    done
    echo "</table>"
    echo
  fi

  echo "</details>"
  echo

  echo "<details><summary><h2>Dependencies</h2></summary>"
  echo
  cat "$DEPS"
  echo
  echo "</details>"
} > release-body.md

echo "release-body.md written: $(wc -l < release-body.md) lines, changelog since ${PREV:-the beginning}"
