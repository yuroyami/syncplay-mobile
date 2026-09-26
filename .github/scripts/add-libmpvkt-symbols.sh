#!/usr/bin/env bash
# Adds the symbol files of mpv and its FFmpeg to the native debug symbols of a full release build.
#
#   add-libmpvkt-symbols.sh <universal APK> <native-debug-symbols.zip>
#
# libmpvKt ships these libraries stripped, so the build has no symbols to extract from them. Each
# libmpvKt release attaches the unstripped copies, one zip per ABI. This script downloads the zips
# for the libmpvKt version in gradle/libs.versions.toml and checks each one against its .sha256
# file. A symbol file must have the same build id as the library in the APK, or the script fails.
# A symbol file for a library that the APK does not contain (a libmpvKt module that this app does
# not use) is skipped.
# The script then adds the file to <native-debug-symbols.zip> as <abi>/<library>.dbg, the name
# that the build gives its own symbol files. With these files, a crash report from mpv shows
# function names.
#
# Run it from the repository root. It reads the NDK version from gradle.properties and the SDK
# folder from ANDROID_HOME or local.properties. The NDK's llvm-readelf reads the build ids.
set -euo pipefail

if [ $# -ne 2 ]; then
  echo "usage: $0 <universal APK> <native-debug-symbols.zip>" >&2
  exit 2
fi
apk=$(cd "$(dirname "$1")" && pwd)/$(basename "$1")
symbols=$(cd "$(dirname "$2")" && pwd)/$(basename "$2")

version=$(sed -n 's/^libmpvkt = "\(.*\)"$/\1/p' gradle/libs.versions.toml)
[ -n "$version" ] || { echo "::error::gradle/libs.versions.toml has no libmpvkt version" >&2; exit 1; }
base="https://github.com/yuroyami/libmpvKt/releases/download/v$version"

ndk_version=$(sed -n 's/^android\.ndkVersion=//p' gradle.properties)
sdk=${ANDROID_HOME:-$(sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null || true)}
readelf=$(ls "$sdk/ndk/$ndk_version/toolchains/llvm/prebuilt/"*/bin/llvm-readelf 2>/dev/null | head -n 1 || true)
[ -x "$readelf" ] || { echo "::error::no llvm-readelf in NDK $ndk_version under '$sdk'" >&2; exit 1; }

# Prints the GNU build id of an ELF file, or nothing when it has none or is not an ELF file.
build_id() { { "$readelf" -n "$1" 2>/dev/null || true; } | sed -n 's/.*Build ID: *\([0-9a-fA-F]*\).*/\1/p' | head -n 1; }

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mkdir "$work/merged"
unzip -q "$symbols" -d "$work/merged"

abis=$(unzip -Z1 "$apk" 'lib/*/libmpv.so' 2>/dev/null | cut -d/ -f2 || true)
[ -n "$abis" ] || { echo "::error::$(basename "$apk") has no libmpv.so" >&2; exit 1; }

added=0
for abi in $abis; do
  zip="libmpvkt-symbols-$abi.zip"
  curl -fsSL --retry 3 -o "$work/$zip" "$base/$zip"
  curl -fsSL --retry 3 -o "$work/$zip.sha256" "$base/$zip.sha256"
  ( cd "$work" && shasum -a 256 -c "$zip.sha256" )
  unzip -q "$work/$zip" "$abi/*" -d "$work/unstripped"
  for file in "$work/unstripped/$abi/"*.so; do
    name=$(basename "$file")
    if ! unzip -p "$apk" "lib/$abi/$name" > "$work/packaged.so" 2>/dev/null; then
      echo "Skipped $abi/$name: the APK does not contain it."
      continue
    fi
    packaged_id=$(build_id "$work/packaged.so")
    symbol_id=$(build_id "$file")
    if [ -z "$symbol_id" ] || [ "$packaged_id" != "$symbol_id" ]; then
      echo "::error::$abi/$name: the APK has build id '${packaged_id:-none}' and the symbol file has '${symbol_id:-none}'" >&2
      exit 1
    fi
    mkdir -p "$work/merged/$abi"
    cp "$file" "$work/merged/$abi/$name.dbg"
    added=$((added + 1))
  done
done

[ "$added" -gt 0 ] || { echo "::error::no libmpvKt symbol file matches a library in the APK" >&2; exit 1; }
rm -f "$symbols"
( cd "$work/merged" && find . -type f | sed 's|^\./||' | sort | zip -X -q "$symbols" -@ )
echo "Added $added symbol files from libmpvKt $version to $(basename "$symbols")."
