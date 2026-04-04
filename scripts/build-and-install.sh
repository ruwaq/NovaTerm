#!/data/data/com.nvterm/files/usr/bin/bash
# Build NovaTerm APK and open installer
# Usage: ./scripts/build-and-install.sh [debug|release]

set -e

VARIANT="${1:-debug}"
APK_DIR="app/build/outputs/apk/$VARIANT"

export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export JAVA_HOME="${JAVA_HOME:-$(dirname $(dirname $(readlink -f $(which java))))}"

echo "=== Building NovaTerm ($VARIANT) ==="

# Ensure wake lock is active during build
termux-wake-lock 2>/dev/null || true

if [ "$VARIANT" = "release" ]; then
    ./gradlew assembleRelease
    APK="$APK_DIR/app-release.apk"
else
    ./gradlew assembleDebug
    APK="$APK_DIR/app-debug.apk"
fi

if [ -f "$APK" ]; then
    SIZE=$(du -h "$APK" | cut -f1)
    echo "=== APK ready: $APK ($SIZE) ==="
    termux-toast "Build complete! Opening installer..."
    termux-open "$APK"
else
    echo "ERROR: APK not found at $APK"
    exit 1
fi
