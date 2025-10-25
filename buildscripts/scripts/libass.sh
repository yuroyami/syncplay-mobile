#!/bin/bash -e

. ../../include/path.sh

if [ "$1" == "build" ]; then
	true
elif [ "$1" == "clean" ]; then
	rm -rf _build$ndk_suffix
	exit 0
else
	exit 255
fi

[ -f configure ] || ./autogen.sh

mkdir -p _build$ndk_suffix
cd _build$ndk_suffix

../configure \
	--host=$ndk_triple --with-pic \
	--enable-static --disable-shared \
	--enable-libunibreak --disable-require-system-font-provider

make -j$cores
make DESTDIR="$prefix_dir" install
