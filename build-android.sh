#!/bin/bash
# NovaTerm - Build para Android desde Mac
# Uso: ./build-android.sh [--release|--debug] [--vulkan] [--apk]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUST_DIR="$SCRIPT_DIR/rust-core"
OUTPUT_DIR="$SCRIPT_DIR/core/terminal-emulator/src/main/jniLibs"

# Defaults
BUILD_TYPE="release"
FEATURES=""
TARGET="arm64-v8a"
API_LEVEL="30"
BUILD_APK=false

# Parse args
while [[ $# -gt 0 ]]; do
    case $1 in
        --debug)
            BUILD_TYPE="debug"
            shift
            ;;
        --release)
            BUILD_TYPE="release"
            shift
            ;;
        --vulkan|--gpu)
            FEATURES="--features vulkan"
            shift
            ;;
        --apk)
            BUILD_APK=true
            shift
            ;;
        *)
            echo "Unknown option: $1"
            echo "Usage: ./build-android.sh [--release|--debug] [--vulkan] [--apk]"
            exit 1
            ;;
    esac
done

# Setup Java (mise)
if [ -d "$HOME/.local/share/mise/installs/java" ]; then
    JAVA_DIR=$(ls -1d "$HOME/.local/share/mise/installs/java"/*/ 2>/dev/null | head -1)
    if [ -d "$JAVA_DIR/Contents/Home" ]; then
        export JAVA_HOME="$JAVA_DIR/Contents/Home"
        export PATH="$JAVA_HOME/bin:$PATH"
    fi
fi

# Setup Android SDK
if [ -z "$ANDROID_HOME" ]; then
    if [ -d "$HOME/Android/sdk" ]; then
        export ANDROID_HOME="$HOME/Android/sdk"
    elif [ -d "$HOME/Library/Android/sdk" ]; then
        export ANDROID_HOME="$HOME/Library/Android/sdk"
    fi
fi

# Detectar NDK
if [ -z "$ANDROID_NDK_HOME" ]; then
    # Ubicaciones comunes
    if [ -d "$HOME/Android/sdk/ndk" ]; then
        ANDROID_NDK_HOME="$HOME/Android/sdk/ndk/$(ls -1 "$HOME/Android/sdk/ndk" | sort -V | tail -1)"
    elif [ -d "$HOME/Library/Android/sdk/ndk" ]; then
        ANDROID_NDK_HOME="$HOME/Library/Android/sdk/ndk/$(ls -1 "$HOME/Library/Android/sdk/ndk" | sort -V | tail -1)"
    fi
fi

if [ -z "$ANDROID_NDK_HOME" ] || [ ! -d "$ANDROID_NDK_HOME" ]; then
    echo "ERROR: NDK no encontrado"
    echo "Instala NDK o configura ANDROID_NDK_HOME"
    exit 1
fi

export ANDROID_NDK_HOME
echo "NDK: $ANDROID_NDK_HOME"
echo "Build: $BUILD_TYPE"
echo "Target: $TARGET (API $API_LEVEL)"
echo "Features: ${FEATURES:-none}"
echo ""

cd "$RUST_DIR"

# Build con cargo-ndk
BUILD_FLAGS=""
if [ "$BUILD_TYPE" = "release" ]; then
    BUILD_FLAGS="--release"
fi

echo "Compilando libnovaterm.so..."
cargo ndk -t $TARGET -P $API_LEVEL build $BUILD_FLAGS $FEATURES

# Copiar .so al directorio jniLibs
mkdir -p "$OUTPUT_DIR/arm64-v8a"
cp "target/aarch64-linux-android/$BUILD_TYPE/libnovaterm.so" "$OUTPUT_DIR/arm64-v8a/"

# Verificar output
SO_FILE="$OUTPUT_DIR/arm64-v8a/libnovaterm.so"
if [ -f "$SO_FILE" ]; then
    SIZE=$(ls -lh "$SO_FILE" | awk '{print $5}')
    echo ""
    echo "RUST BUILD EXITOSO"
    echo "Output: $SO_FILE"
    echo "Size: $SIZE"
    file "$SO_FILE"
else
    echo "ERROR: No se genero libnovaterm.so"
    exit 1
fi

# Build APK si se solicita
if [ "$BUILD_APK" = true ]; then
    echo ""
    echo "Construyendo APK..."
    cd "$SCRIPT_DIR"

    GRADLE_BUILD_TYPE="assembleDebug"
    if [ "$BUILD_TYPE" = "release" ]; then
        GRADLE_BUILD_TYPE="assembleRelease"
    fi

    ./gradlew $GRADLE_BUILD_TYPE --no-daemon -q

    APK_DIR="$SCRIPT_DIR/app/build/outputs/apk/$BUILD_TYPE"
    APK_FILE=$(ls -1 "$APK_DIR"/*.apk 2>/dev/null | head -1)

    if [ -f "$APK_FILE" ]; then
        APK_SIZE=$(ls -lh "$APK_FILE" | awk '{print $5}')
        echo ""
        echo "APK BUILD EXITOSO"
        echo "Output: $APK_FILE"
        echo "Size: $APK_SIZE"
    fi
fi
