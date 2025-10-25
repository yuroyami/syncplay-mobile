#!/bin/bash -e

. ../../include/path.sh

build=_build$ndk_suffix

if [ "$1" == "build" ]; then
	true
elif [ "$1" == "clean" ]; then
	rm -rf $build
	exit 0
else
	exit 255
fi

unset CC CXX # meson wants these unset

meson setup $build --cross-file "$prefix_dir"/crossfile.txt \
	-Denable_tests=false -Db_lto=true -Dstack_alignment=16

ninja -C $build -j$cores
DESTDIR="$prefix_dir" ninja -C $build install
