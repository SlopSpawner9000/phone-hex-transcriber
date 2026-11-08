#!/bin/sh
# Build HexTranscriber on Linux.
set -e
cd "$(dirname "$0")"

JDK_DIR="/usr/lib/jvm/java-17-openjdk"
SDK_DIR="$PWD/.android-sdk"

FAIL=0

if [ ! -d "$JDK_DIR" ]; then
    echo "  - JDK 17 not found at $JDK_DIR (install with: sudo pacman -S jdk17-openjdk)" >&2
    FAIL=1
fi

if ! command -v gradle >/dev/null 2>&1; then
    echo "  - 'gradle' not found on PATH (install with: sudo pacman -S gradle)" >&2
    FAIL=1
fi

if [ ! -d "$SDK_DIR/platforms" ] || [ ! -d "$SDK_DIR/build-tools" ]; then
    echo "  - Android SDK not found at $SDK_DIR (expected a platforms/ and build-tools/ subdirectory)" >&2
    FAIL=1
fi

if [ "$FAIL" = "1" ]; then
    echo "" >&2
    echo "Cannot build: missing required dependencies (see above)." >&2
    echo "Required programs:" >&2
    echo "  - JDK 17, at $JDK_DIR" >&2
    echo "  - Gradle, on PATH" >&2
    echo "  - Android SDK, at $SDK_DIR (with platforms/ and build-tools/)" >&2
    exit 1
fi

export JAVA_HOME="$JDK_DIR"
export ANDROID_SDK_ROOT="$SDK_DIR"
export ANDROID_HOME="$SDK_DIR"

gradle --no-daemon assembleDebug

mkdir -p builds
cp app/build/outputs/apk/debug/app-debug.apk builds/HexTranscriber-debug.apk
echo "APK written to builds/HexTranscriber-debug.apk"
