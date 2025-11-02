#!/bin/bash
set -eu


# Args
SDK_PATH="$1"           # Android SDK path
NDK_PATH="$2"           # Android NDK path
OS_NAME="$3"            # mac, linux, or windows

if [ $# -lt 3 ]; then
  echo "❌ Usage: $0 <SDK_PATH> <NDK_PATH> <OS_NAME>"
  exit 1
fi

echo "▶️  Starting FFmpeg build for ExoPlayer"

ANDROID_ABI=21

# Paths
FFMPEG_MODULE_PATH="$(pwd)/media3/libraries/decoder_ffmpeg/src/main"
FFMPEG_DIR="$(pwd)/ffmpeg"

JNI_LIBS_DIR="$(pwd)/media3/libraries/decoder_ffmpeg/src/main/jniLibs"
mkdir -p "$JNI_LIBS_DIR"

# Host platform detection (case-insensitive)
OS_NAME_LOWER=$(echo "$OS_NAME" | tr '[:upper:]' '[:lower:]')
case "$OS_NAME_LOWER" in
  mac|macos|darwin)
    HOST_PLATFORM="darwin-x86_64"
    echo "ℹ️  Detected macOS platform"
    ;;
  linux)
    HOST_PLATFORM="linux-x86_64"
    echo "ℹ️  Detected Linux platform"
    ;;
  windows)
    echo "❌ Windows is not supported for native builds"
    echo "ℹ️  This is expected - Windows builds should be skipped in Gradle"
    exit 1
    ;;
  *)
    echo "❌ Unsupported/Unknown OS: $OS_NAME"
    echo "ℹ️  Expected: mac, macos, darwin, or linux"
    echo "ℹ️  Detected from system: $(uname -s)"
    exit 1
    ;;
esac

ANDROID_API=23

# ---- Clone FFmpeg locally if not present ----
if [ ! -d "$FFMPEG_DIR" ]; then
  echo "🔁 Cloning FFmpeg..."
  git clone git://source.ffmpeg.org/ffmpeg "$FFMPEG_DIR"
  cd "$FFMPEG_DIR"
  git checkout release/6.0
  cd ..
fi

cd "${FFMPEG_MODULE_PATH}/jni" && \
[ ! -L ffmpeg ] && ln -s "$FFMPEG_DIR" ffmpeg

cd ../../../../../..

echo "🛠️  Preparing FFmpeg audio libs build..."

ENABLED_DECODERS=(vorbis opus flac alac pcm_mulaw pcm_alaw mp3 amrnb amrwb aac ac3 eac3 dca mlp truehd)

JOBS="$(nproc 2> /dev/null || sysctl -n hw.ncpu 2> /dev/null || echo 4)"
TOOLCHAIN_PREFIX="${NDK_PATH}/toolchains/llvm/prebuilt/${HOST_PLATFORM}/bin"

echo "🔍 Checking toolchain at $TOOLCHAIN_PREFIX"
if [[ ! -d "${TOOLCHAIN_PREFIX}" ]]; then
  echo "❌ NDK not found under: $NDK_PATH"
  exit 1
fi

COMMON_OPTIONS="
    --target-os=android
    --enable-static
    --disable-shared
    --disable-doc
    --disable-programs
    --disable-everything
    --disable-avdevice
    --disable-avformat
    --disable-swscale
    --disable-postproc
    --disable-avfilter
    --disable-symver
    --enable-swresample
    --extra-ldexeflags=-pie
    --disable-v4l2-m2m
    --disable-vulkan
"

for DEC in "${ENABLED_DECODERS[@]}"; do
  COMMON_OPTIONS="${COMMON_OPTIONS} --enable-decoder=${DEC}"
done

# Ensure output directory exists
mkdir -p "$JNI_LIBS_DIR"

cd "$FFMPEG_DIR"

ANDROID_ABI_64BIT="$ANDROID_ABI"
if [[ "$ANDROID_ABI_64BIT" -lt 21 ]]
then
    echo "Using ANDROID_ABI 21 for 64-bit architectures"
    ANDROID_ABI_64BIT=21
fi

echo "Building FFmpeg: armeabi-v7a"
./configure \
    --libdir=android-libs/armeabi-v7a \
    --arch=arm \
    --cpu=armv7-a \
    --cross-prefix="${TOOLCHAIN_PREFIX}/armv7a-linux-androideabi${ANDROID_ABI}-" \
    --nm="${TOOLCHAIN_PREFIX}/llvm-nm" \
    --ar="${TOOLCHAIN_PREFIX}/llvm-ar" \
    --ranlib="${TOOLCHAIN_PREFIX}/llvm-ranlib" \
    --strip="${TOOLCHAIN_PREFIX}/llvm-strip" \
    --extra-cflags="-march=armv7-a -mfloat-abi=softfp" \
    --extra-ldflags="-Wl,--fix-cortex-a8" \
    ${COMMON_OPTIONS}
echo "🔧 Compiling for armeabi-v7a..."
make -j$JOBS
make install-libs
make clean
rm -f config.h config.mak

./configure \
    --libdir=android-libs/arm64-v8a \
    --arch=aarch64 \
    --cpu=armv8-a \
    --cross-prefix="${TOOLCHAIN_PREFIX}/aarch64-linux-android${ANDROID_ABI_64BIT}-" \
    --nm="${TOOLCHAIN_PREFIX}/llvm-nm" \
    --ar="${TOOLCHAIN_PREFIX}/llvm-ar" \
    --ranlib="${TOOLCHAIN_PREFIX}/llvm-ranlib" \
    --strip="${TOOLCHAIN_PREFIX}/llvm-strip" \
    ${COMMON_OPTIONS}
echo "🔧 Compiling for arm64-v8a..."
make -j$JOBS
make install-libs
make clean
rm -f config.h config.mak

./configure \
    --libdir=android-libs/x86 \
    --arch=x86 \
    --cpu=i686 \
    --cross-prefix="${TOOLCHAIN_PREFIX}/i686-linux-android${ANDROID_ABI}-" \
    --nm="${TOOLCHAIN_PREFIX}/llvm-nm" \
    --ar="${TOOLCHAIN_PREFIX}/llvm-ar" \
    --ranlib="${TOOLCHAIN_PREFIX}/llvm-ranlib" \
    --strip="${TOOLCHAIN_PREFIX}/llvm-strip" \
    --disable-asm \
    ${COMMON_OPTIONS}
echo "🔧 Compiling for x86..."
make -j$JOBS
make install-libs
make clean
rm -f config.h config.mak

echo "Building FFmpeg: x86_64"
./configure \
    --libdir=android-libs/x86_64 \
    --arch=x86_64 \
    --cpu=x86-64 \
    --cross-prefix="${TOOLCHAIN_PREFIX}/x86_64-linux-android${ANDROID_ABI_64BIT}-" \
    --nm="${TOOLCHAIN_PREFIX}/llvm-nm" \
    --ar="${TOOLCHAIN_PREFIX}/llvm-ar" \
    --ranlib="${TOOLCHAIN_PREFIX}/llvm-ranlib" \
    --strip="${TOOLCHAIN_PREFIX}/llvm-strip" \
    --disable-asm \
    ${COMMON_OPTIONS}
echo "🔧 Compiling for x86_64..."
make -j$JOBS
make install-libs
make clean
rm -f config.h config.mak

echo "✅ FFmpeg build complete. Output at: $JNI_LIBS_DIR"
