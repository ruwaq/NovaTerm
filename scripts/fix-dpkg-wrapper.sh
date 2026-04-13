#!/data/data/com.nvterm/files/usr/bin/bash
# fix-dpkg-wrapper.sh — Fixes the dpkg wrapper for NovaTerm
#
# The dpkg wrapper intercepts .deb installs and patches com.nvterm -> com.nvterm
# paths. The original wrapper had issues:
#   1. Suppressed errors from dpkg-deb (2>/dev/null), hiding failures
#   2. No logging to diagnose issues
#   3. Missing com/termux (Java package path) patching
#
# Run this script, then retry: pkg install -y openjdk-21
#
set -euo pipefail

PREFIX="/data/data/com.nvterm/files"
DPKG_WRAPPER="$PREFIX/usr/bin/dpkg"
DPKG_REAL="$PREFIX/usr/bin/dpkg.real"

# Verify dpkg.real exists
if [ ! -x "$DPKG_REAL" ]; then
  echo "ERROR: dpkg.real not found at $DPKG_REAL"
  exit 1
fi

echo "=== Step 1: Clean up failed dpkg state ==="
"$DPKG_REAL" --configure -a 2>&1 || true
apt --fix-broken install -y 2>&1 || true

# Force-remove any half-installed packages
"$DPKG_REAL" --audit 2>&1 | grep -oP '^\S+' | while read -r pkg; do
  echo "Removing half-installed: $pkg"
  "$DPKG_REAL" --remove --force-remove-reinstreq "$pkg" 2>&1 || true
done

echo ""
echo "=== Step 2: Install improved dpkg wrapper ==="

cat > "$DPKG_WRAPPER" << 'WRAPPER_EOF'
#!/data/data/com.nvterm/files/usr/bin/sh
# NovaTerm dpkg wrapper — patches com.termux paths in .deb files before install
# Real dpkg is at dpkg.real
# com.termux (10 bytes) == com.nvterm (10 bytes) — safe for binary patching

DPKG_DEB="/data/data/com.nvterm/files/usr/bin/dpkg-deb"
DPKG_REAL="/data/data/com.nvterm/files/usr/bin/dpkg.real"
LOGFILE="/data/data/com.nvterm/files/usr/var/log/dpkg-patch.log"

log() {
  echo "[dpkg-wrapper] $(date '+%H:%M:%S') $*" >> "$LOGFILE" 2>/dev/null
}

patch_deb() {
  local deb="$1"
  [ -f "$deb" ] || return 1

  local tmp
  tmp="$(mktemp -d)" || { log "FAIL: mktemp for $deb"; return 1; }
  local basename="${deb##*/}"
  log "Patching: $basename"

  # Extract .deb using dpkg-deb
  if ! $DPKG_DEB --raw-extract "$deb" "$tmp/pkg" 2>>"$LOGFILE"; then
    log "FAIL: raw-extract failed for $basename"
    rm -rf "$tmp"
    return 1
  fi

  # Rename directory tree: com.termux → com.nvterm
  if [ -d "$tmp/pkg/data/data/com.termux" ]; then
    mkdir -p "$tmp/pkg/data/data/com.nvterm"
    cp -a "$tmp/pkg/data/data/com.termux/." "$tmp/pkg/data/data/com.nvterm/"
    rm -rf "$tmp/pkg/data/data/com.termux"
    log "  Moved directory tree com.termux → com.nvterm"
  fi

  # Patch file contents — both dotted and slashed path forms
  # com.nvterm and com.nvterm are same byte length (10), safe for binaries
  find "$tmp/pkg" -type f | while IFS= read -r f; do
    if LC_ALL=C grep -qm1 "com\.termux\|com/termux" "$f" 2>/dev/null; then
      LC_ALL=C sed -i -e 's|com\.termux|com.nvterm|g' -e 's|com/termux|com/nvterm|g' "$f" 2>/dev/null
    fi
  done

  # Rebuild .deb — this MUST succeed or we fall through with original
  if ! $DPKG_DEB -b "$tmp/pkg" "$deb" 2>>"$LOGFILE"; then
    log "FAIL: rebuild failed for $basename"
    rm -rf "$tmp"
    return 1
  fi

  log "  OK: $basename patched and rebuilt"
  rm -rf "$tmp"
  return 0
}

# Patch any .deb file arguments before passing to real dpkg
for arg in "$@"; do
  case "$arg" in
    *.deb)
      if [ -f "$arg" ]; then
        patch_deb "$arg" || log "WARN: could not patch $arg, passing original"
      fi
      ;;
  esac
done

# Pass all original args to real dpkg
exec "$DPKG_REAL" "$@"
WRAPPER_EOF

chmod +x "$DPKG_WRAPPER"
echo "Wrapper updated: $DPKG_WRAPPER"

echo ""
echo "=== Step 3: Create log directory ==="
mkdir -p "$PREFIX/usr/var/log"
touch "$PREFIX/usr/var/log/dpkg-patch.log"
echo "Log file: $PREFIX/usr/var/log/dpkg-patch.log"

echo ""
echo "=== Step 4: Clean apt cache and retry install ==="
apt clean 2>&1
apt update 2>&1

echo ""
echo "=== Step 5: Installing openjdk-21 ==="
pkg install -y openjdk-21 2>&1
INSTALL_RC=$?

echo ""
if [ $INSTALL_RC -eq 0 ]; then
  echo "=== SUCCESS: openjdk-21 installed ==="
  echo ""
  echo "=== Checking dpkg-patch log ==="
  tail -20 "$PREFIX/usr/var/log/dpkg-patch.log" 2>/dev/null || echo "(no log entries)"
  echo ""
  echo "=== Verifying Java ==="
  java -version 2>&1
  JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(which java)")")")"
  echo "JAVA_HOME=$JAVA_HOME"
  echo ""
  echo "Add to your profile:"
  echo "  export JAVA_HOME=$JAVA_HOME"
else
  echo "=== INSTALL FAILED (exit $INSTALL_RC) ==="
  echo ""
  echo "=== dpkg-patch log ==="
  cat "$PREFIX/usr/var/log/dpkg-patch.log" 2>/dev/null || echo "(no log)"
  echo ""
  echo "=== Trying force approach ==="
  dpkg --remove --force-remove-reinstreq openjdk-21 2>&1 || true
  dpkg --remove --force-remove-reinstreq openjdk-21-x 2>&1 || true
  apt clean 2>&1
  pkg install -y openjdk-21 2>&1
  RETRY_RC=$?
  if [ $RETRY_RC -ne 0 ]; then
    echo ""
    echo "=== STILL FAILING — check log ==="
    cat "$PREFIX/usr/var/log/dpkg-patch.log" 2>/dev/null
  fi
fi
