#!/bin/bash
set -eu

# Args
SDK_PATH="$1"           # Android SDK path
NDK_PATH="$2"           # Android NDK path
OS_NAME="$3"            # mac, linux, or windows
MEDIA3_VER="$4"         # We get media3 via version catalog

if [ $# -lt 4 ]; then
  echo "❌ Usage: $0 <SDK_PATH> <NDK_PATH> <OS_NAME> <MEDIA3_VER>"
  exit 1
fi

echo "▶️  Starting FFmpeg build for ExoPlayer"

# Paths
FFMPEG_DIR="$(pwd)/ffmpeg"

JNI_LIBS_DIR="$(pwd)/decoder_ffmpeg/src/main/jniLibs"
mkdir -p "$JNI_LIBS_DIR"

JNI_LIBS_DIR="$(pwd)/decoder_ffmpeg/src/main/jniLibs"

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

ANDROID_API=21

# ---- Clone FFmpeg locally if not present ----
if [ ! -d "$FFMPEG_DIR" ]; then
  echo "🔁 Cloning FFmpeg..."
  git clone git://source.ffmpeg.org/ffmpeg "$FFMPEG_DIR"
  cd "$FFMPEG_DIR"
  git checkout release/6.0
  cd ..
fi

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

build_ffmpeg() {
  ARCH=$1
  CPU=$2
  ABI=$3
  PREFIX=$4

  echo "🔧 Building FFmpeg for $ABI..."
  ./configure \
    --libdir="${JNI_LIBS_DIR}/${ABI}" \
    --arch="$ARCH" \
    --cpu="$CPU" \
    --cross-prefix="${TOOLCHAIN_PREFIX}/${PREFIX}" \
    --nm="${TOOLCHAIN_PREFIX}/llvm-nm" \
    --ar="${TOOLCHAIN_PREFIX}/llvm-ar" \
    --ranlib="${TOOLCHAIN_PREFIX}/llvm-ranlib" \
    --strip="${TOOLCHAIN_PREFIX}/llvm-strip" \
    --extra-cflags="-O3" \
    --extra-ldflags="" \
    ${COMMON_OPTIONS}
  make -j"$JOBS"
  make install-libs
  make clean
}

build_ffmpeg "arm" "armv7-a" "armeabi-v7a" "armv7a-linux-androideabi${ANDROID_API}-"
build_ffmpeg "aarch64" "armv8-a" "arm64-v8a" "aarch64-linux-android${ANDROID_API}-"
build_ffmpeg "x86" "i686" "x86" "i686-linux-android${ANDROID_API}-"
build_ffmpeg "x86_64" "x86-64" "x86_64" "x86_64-linux-android${ANDROID_API}-"

echo "✅ FFmpeg build complete. Output at: $JNI_LIBS_DIR"
