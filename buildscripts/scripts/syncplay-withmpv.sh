#!/bin/bash -e

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
BUILD="$DIR/.."
SYNCPLAY_WITHMPV="$DIR/../.."

. "$BUILD"/include/path.sh
. "$BUILD"/include/depinfo.sh

if [ "$1" == "clean" ]; then
    rm -rf "$SYNCPLAY_WITHMPV"/{shared,.}/build "$SYNCPLAY_WITHMPV"/shared/src/androidMain/{libs,obj}
    exit 0
fi

nativeprefix () {
    if [ -f "$BUILD"/prefix/$1/lib/libmpv.so ]; then
        echo $BUILD/prefix/$1
    else
        echo >&2 "Warning: libmpv.so not found for $1, skipping"
    fi
}

prefix32=$(nativeprefix "armv7l")
prefix64=$(nativeprefix "arm64")
prefix_x64=$(nativeprefix "x86_64")
prefix_x86=$(nativeprefix "x86")

if [[ -z "$prefix32" && -z "$prefix64" && -z "$prefix_x64" && -z "$prefix_x86" ]]; then
    echo >&2 "Error: no mpv libraries found in prefix/"
    exit 255
fi

PREFIX32=$prefix32 PREFIX64=$prefix64 PREFIX_X64=$prefix_x64 PREFIX_X86=$prefix_x86 \
ndk-build -C shared/src/androidMain -j${cores:-4}
