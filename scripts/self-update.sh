#!/data/data/com.nvterm/files/usr/bin/bash
# NovaTerm Self-Update — build and install from within the terminal
#
# Usage: ./scripts/self-update.sh [--skip-build] [--rust] [--release]
#
# Builds the APK and opens Android's package installer.
# Safe: preserves all app data (bootstrap, sessions, preferences).

set -euo pipefail

# ── Colors ──────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
RESET='\033[0m'

# ── Project root ────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

# ── Parse args ──────────────────────────────────────────────────
SKIP_BUILD=false
BUILD_RUST=false
BUILD_TYPE="debug"
GRADLE_TASK="assembleDebug"

while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-build)
            SKIP_BUILD=true
            shift ;;
        --rust)
            BUILD_RUST=true
            shift ;;
        --release)
            BUILD_TYPE="release"
            GRADLE_TASK="assembleRelease"
            shift ;;
        -h|--help)
            echo "Usage: $0 [--skip-build] [--rust] [--release]"
            echo ""
            echo "  --skip-build  Install existing APK without rebuilding"
            echo "  --rust        Rebuild libnovaterm.so before APK"
            echo "  --release     Build release APK (needs keystore.properties)"
            exit 0 ;;
        *)
            echo -e "${RED}Unknown option: $1${RESET}"
            exit 1 ;;
    esac
done

APK_DIR="$PROJECT_DIR/app/build/outputs/apk/$BUILD_TYPE"

# ── Pre-flight checks ──────────────────────────────────────────
echo -e "${BOLD}${CYAN}NovaTerm Self-Update${RESET}"
echo ""

# Check we're in the right place
if [[ ! -f "$PROJECT_DIR/settings.gradle.kts" ]]; then
    echo -e "${RED}Error: not in NovaTerm project root${RESET}"
    exit 1
fi

# Check available disk space (need ~500MB for build)
AVAIL_MB=$(df -m "$PROJECT_DIR" | tail -1 | awk '{print $4}')
if [[ "$AVAIL_MB" -lt 500 ]]; then
    echo -e "${YELLOW}Warning: only ${AVAIL_MB}MB free (500MB+ recommended)${RESET}"
    echo -n "Continue? [y/N] "
    read -r REPLY
    [[ "$REPLY" =~ ^[Yy]$ ]] || exit 0
fi

# ── Step 1: Build Rust (optional) ──────────────────────────────
if $BUILD_RUST; then
    echo -e "${CYAN}[1/3] Building libnovaterm.so...${RESET}"
    ./build-android.sh --"$BUILD_TYPE"
    echo ""
fi

# ── Step 2: Build APK ──────────────────────────────────────────
if ! $SKIP_BUILD; then
        if $BUILD_RUST; then STEP="2/3"; else STEP="1/2"; fi
    echo -e "${CYAN}[$STEP] Building APK ($BUILD_TYPE)...${RESET}"

    # Save battery: use --no-daemon to free RAM after build
    if ! ./gradlew "$GRADLE_TASK" --no-daemon -q; then
        echo -e "${RED}Build failed${RESET}"
        exit 1
    fi
    echo -e "${GREEN}Build OK${RESET}"
    echo ""
fi

# ── Step 3: Find APK ───────────────────────────────────────────
APK_FILE=$(find "$APK_DIR" -name "*.apk" -type f 2>/dev/null | head -1)

if [[ -z "$APK_FILE" || ! -f "$APK_FILE" ]]; then
    echo -e "${RED}Error: APK not found in $APK_DIR${RESET}"
    echo "Run without --skip-build first"
    exit 1
fi

APK_SIZE=$(ls -lh "$APK_FILE" | awk '{print $5}')
echo -e "${BOLD}APK: ${APK_FILE##*/} ($APK_SIZE)${RESET}"

# ── Step 4: Show what's preserved ──────────────────────────────
echo ""
echo -e "${GREEN}Data preserved on update:${RESET}"
echo "  • Bootstrap (bash, git, apt, etc.)"
echo "  • Sessions & CWD"
echo "  • Preferences & color scheme"
echo "  • Command history & BlockStore"
echo "  • Home directory contents"
echo ""

# ── Step 5: Copy to shared storage for installer access ───────
# Android's package installer can't read app-private paths.
# Copy to /sdcard/ temporarily so the installer intent can access it.
SHARED_APK="/sdcard/Download/novaterm-update.apk"
cp "$APK_FILE" "$SHARED_APK" 2>/dev/null || {
    # Fallback: try termux shared storage
    SHARED_APK="/storage/emulated/0/Download/novaterm-update.apk"
    cp "$APK_FILE" "$SHARED_APK" 2>/dev/null || {
        echo -e "${RED}Can't copy APK to shared storage${RESET}"
        echo "Grant storage permission and retry"
        exit 1
    }
}

# ── Step 6: Install via Android package installer ─────────────
if $BUILD_RUST; then STEP="3/3"; elif $SKIP_BUILD; then STEP="1/1"; else STEP="2/2"; fi
echo -e "${CYAN}[$STEP] Opening installer...${RESET}"
echo -e "${YELLOW}NovaTerm will close during install.${RESET}"
echo -e "${YELLOW}Reopen it after — sessions restore automatically.${RESET}"
echo ""

# Give user a moment to read
sleep 2

# Use termux-open (works from both Termux and NovaTerm)
# This triggers Android's ACTION_VIEW intent for APK files
termux-open "$SHARED_APK" 2>/dev/null || {
    # Fallback: use am (activity manager) directly
    am start -a android.intent.action.VIEW \
        -t application/vnd.android.package-archive \
        -d "file://$SHARED_APK" \
        --ez return-result true 2>/dev/null || {
        # Last fallback: content URI via FileProvider
        echo -e "${YELLOW}Auto-install failed. Install manually:${RESET}"
        echo "  Open Files app → Downloads → novaterm-update.apk"
    }
}

# Clean up shared APK after a delay (in case installer needs it)
# The user will have already tapped "Install" by then
(sleep 60 && rm -f "$SHARED_APK" 2>/dev/null) &

echo ""
echo -e "${GREEN}Done. Tap 'Install' when prompted.${RESET}"
