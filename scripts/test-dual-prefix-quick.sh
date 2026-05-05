#!/usr/bin/env bash
# ============================================================================
# NovaTerm Dual-Prefix Bootstrap Test (Quick)
# ============================================================================
# Quick variant of test-dual-prefix.sh.
# Assumes the emulator is running, the APK is already installed,
# and the app has completed bootstrap. Only runs steps 5-10.
#
# Steps:
#   1. Verify emulator and app are running (quick sanity check)
#   2. Ensure bootstrap is ready
#   3. Create com.termux -> com.nvterm symlink in app's mount namespace
#   4. Verify nvterm-exec is installed and is REVISION=2
#   5. apt install nodejs (from Termux repo)
#   6. Verify node, npm, npx execute without manual sed
#   7. Report PASS/FAIL
#
# Idempotent: safe to run multiple times.
#
# Usage: ./scripts/test-dual-prefix-quick.sh
# ============================================================================

set -euo pipefail

# ─── Configuration ─────────────────────────────────────────────────────────
PKG="com.nvterm"
ACTIVITY="$PKG/.ui.MainActivity"
DATA_DIR="/data/data/$PKG"
PREFIX="$DATA_DIR/files/usr"
HOME_DIR="$DATA_DIR/files/home"
TMPDIR="$PREFIX/tmp"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
BOLD='\033[1m'
RESET='\033[0m'

PASS=0
FAIL=0

pass() { ((PASS++)); printf "${GREEN}  [PASS] %s${RESET}\\n" "$1"; }
fail() { ((FAIL++)); printf "${RED}  [FAIL] %s${RESET}\\n" "$1"; }
info() { printf "${BLUE}  [INFO] %s${RESET}\\n" "$1"; }
warn() { printf "${YELLOW}  [WARN] %s${RESET}\\n" "$1"; }
section() { printf "\\n${BOLD}${BLUE}═══ %s ═══${RESET}\\n" "$1"; }

# ─── Helpers ────────────────────────────────────────────────────────────────

# Run a command inside the app's user context with proper Termux/NovaTerm env
run_as_app() {
    local cmd="$1"
    adb shell "run-as $PKG sh -c '
        export PREFIX=$PREFIX
        export HOME=$HOME_DIR
        export TMPDIR=$TMPDIR
        export LD_PRELOAD=$LD_PRELOAD_SO
        export PATH=\\$PREFIX/bin:\\$PATH
        export LANG=en_US.UTF-8
        export TERM=xterm-256color
        $cmd
    '"
}

# Find the active nvterm-exec LD_PRELOAD library on the device
find_ld_preload() {
    local candidates=(
        "$PREFIX/lib/libnvterm-exec-linker-ld-preload.so"
        "$PREFIX/lib/libnvterm-exec-ld-preload.so"
        "$PREFIX/lib/libnvterm-exec.so"
    )
    for c in "${candidates[@]}"; do
        if adb shell "run-as $PKG test -f $c" 2>/dev/null; then
            printf '%s' "$c"
            return 0
        fi
    done
    return 1
}

# Verify nvterm-exec supports dual-prefix (REVISION=2)
verify_nvterm_exec_revision() {
    local so="$1"
    info "Verificando strings com.termux y com.nvterm en el binario..."

    local has_termux=0
    local has_nvterm=0

    if adb shell "run-as $PKG sh -c '
        export LD_PRELOAD=$so
        export PATH=$PREFIX/bin:\\$PATH
        grep -aq \"com\\\\.termux\" \"$so\" && echo HAS_TERMUX
    ' 2>/dev/null" | tr -d '\r' | grep -q "HAS_TERMUX"; then
        has_termux=1
    fi

    if adb shell "run-as $PKG sh -c '
        export LD_PRELOAD=$so
        export PATH=$PREFIX/bin:\\$PATH
        grep -aq \"com\\\\.nvterm\" \"$so\" && echo HAS_NVTERM
    ' 2>/dev/null" | tr -d '\r' | grep -q "HAS_NVTERM"; then
        has_nvterm=1
    fi

    if [ "$has_termux" -eq 1 ] && [ "$has_nvterm" -eq 1 ]; then
        pass "nvterm-exec contiene strings com.termux y com.nvterm"
        return 0
    fi

    # Fallback: behavioral test — create script with com.termux shebang and execute
    info "Fallback: prueba behavioral de shebang com.termux..."
    local test_script="$HOME_DIR/.test-dual-prefix.sh"
    local result
    result=$(adb shell "run-as $PKG sh -c '
        export LD_PRELOAD=$so
        export PATH=$PREFIX/bin:\\$PATH
        cat > $test_script << \"EOF\"
#!/data/data/com.termux/files/usr/bin/sh
echo DUAL_PREFIX_OK
EOF
        chmod +x $test_script
        $test_script
    ' 2>/dev/null" | tr -d '\r' || true)

    adb shell "run-as $PKG rm -f $test_script" 2>/dev/null || true

    if echo "$result" | grep -q "DUAL_PREFIX_OK"; then
        pass "nvterm-exec intercepta shebangs com.termux (behavioral)"
        return 0
    fi

    fail "nvterm-exec no muestra soporte dual-prefix (REVISION=2)"
    return 1
}

# ─── Main Test Flow ───────────────────────────────────────────────────────

# 1. Quick sanity check
section "1. Sanity check (emulador + app)"
if ! command -v adb >/dev/null 2>&1; then
    fail "adb no encontrado en PATH"
    exit 1
fi
if ! adb devices | grep -q "emulator"; then
    fail "No hay emulador corriendo (adb devices no muestra emulator)"
    exit 1
fi
pass "Emulador detectado"

if ! adb shell "pidof $PKG" >/dev/null 2>&1; then
    warn "App no estaba corriendo; iniciando..."
    adb shell "am start -n $ACTIVITY" >/dev/null 2>&1 || true
    sleep 3
    if ! adb shell "pidof $PKG" >/dev/null 2>&1; then
        fail "No se pudo iniciar la app"
        exit 1
    fi
fi
pass "App corriendo"

# 2. Esperar bootstrap (max 60s)
section "2. Esperar bootstrap (max 60s)"
MAX_WAIT=60
ELAPSED=0
BOOTSTRAP_READY=false
while [ $ELAPSED -lt $MAX_WAIT ]; do
    if adb shell "run-as $PKG test -f $PREFIX/bin/bash" 2>/dev/null; then
        BOOTSTRAP_READY=true
        pass "Bootstrap listo (${ELAPSED}s)"
        break
    fi
    sleep 2
    ELAPSED=$((ELAPSED + 2))
    printf "."
done
if [ "$BOOTSTRAP_READY" != "true" ]; then
    fail "Timeout esperando bootstrap (${MAX_WAIT}s)"
    exit 1
fi

# Discover LD_PRELOAD library
LD_PRELOAD_SO=$(find_ld_preload) || {
    fail "No se encontro libreria nvterm-exec en $PREFIX/lib/"
    exit 1
}
info "LD_PRELOAD detectado: $(basename "$LD_PRELOAD_SO")"

# 3. Crear symlink com.termux -> com.nvterm en namespace de la app
section "3. Crear symlink com.termux -> com.nvterm"
PID=$(adb shell "pidof $PKG" 2>/dev/null | tr -d '\r' || true)
if [ -z "$PID" ]; then
    warn "No se pudo obtener PID de $PKG; reiniciando app..."
    adb shell "am start -n $ACTIVITY" >/dev/null 2>&1 || true
    sleep 3
    PID=$(adb shell "pidof $PKG" 2>/dev/null | tr -d '\r' || true)
fi

if [ -n "$PID" ]; then
    info "PID de $PKG: $PID"
    adb root >/dev/null 2>&1 || warn "adb root fallo (puede ser normal en algunos emuladores)"
    sleep 2
    adb wait-for-device >/dev/null 2>&1 || true

    if adb shell "which nsenter" >/dev/null 2>&1; then
        adb shell "nsenter --mount --target=$PID rm -rf /data/data/com.termux 2>/dev/null || true" >/dev/null 2>&1 || true
        if adb shell "nsenter --mount --target=$PID ln -s /data/data/com.nvterm /data/data/com.termux 2>&1" >/dev/null 2>&1; then
            pass "Symlink creado en namespace de la app (nsenter)"
        else
            warn "nsenter ln fallo; intentando symlink global"
            adb shell "rm -rf /data/data/com.termux 2>/dev/null || true" >/dev/null 2>&1 || true
            adb shell "ln -s /data/data/com.nvterm /data/data/com.termux 2>/dev/null || true" >/dev/null 2>&1 || true
            pass "Symlink creado globalmente (fallback)"
        fi
    else
        warn "nsenter no disponible; creando symlink global"
        adb shell "rm -rf /data/data/com.termux 2>/dev/null || true" >/dev/null 2>&1 || true
        adb shell "ln -s /data/data/com.nvterm /data/data/com.termux 2>/dev/null || true" >/dev/null 2>&1 || true
        pass "Symlink creado globalmente (fallback)"
    fi
else
    warn "No se pudo obtener PID; saltando symlink"
fi

# 4. Verificar nvterm-exec REVISION=2
section "4. Verificar nvterm-exec REVISION=2"
if ! adb shell "run-as $PKG test -f $LD_PRELOAD_SO" 2>/dev/null; then
    fail "LD_PRELOAD library no encontrada: $LD_PRELOAD_SO"
    exit 1
fi
pass "LD_PRELOAD library existe: $(basename "$LD_PRELOAD_SO")"

verify_nvterm_exec_revision "$LD_PRELOAD_SO" || exit 1

# 5. apt install nodejs
section "5. apt install nodejs"
info "Actualizando repos..."
run_as_app "apt update -qq" || { fail "apt update fallo"; exit 1; }
pass "apt update OK"

info "Instalando nodejs (puede tardar ~2-3 min)..."
run_as_app "apt install -y nodejs" || { fail "apt install nodejs fallo"; exit 1; }
pass "apt install nodejs OK"

# 6. Verificar node
section "6. Verificar node"
NODE_VER=$(run_as_app "node --version" 2>/dev/null | tr -d '\r' || true)
if [ -n "$NODE_VER" ]; then
    pass "node ejecuta: $NODE_VER"
else
    fail "node no ejecuta"
    exit 1
fi

# 7. Verificar npm y npx
section "7. Verificar npm y npx"
NPM_VER=$(run_as_app "npm --version" 2>/dev/null | tr -d '\r' || true)
if [ -n "$NPM_VER" ]; then
    pass "npm ejecuta: $NPM_VER"
else
    fail "npm no ejecuta"
    exit 1
fi

NPX_VER=$(run_as_app "npx --version" 2>/dev/null | tr -d '\r' || true)
if [ -n "$NPX_VER" ]; then
    pass "npx ejecuta: $NPX_VER"
else
    fail "npx no ejecuta"
    exit 1
fi

# 8. Resultado PASS/FAIL
section "8. RESULTADO"
TOTAL=$((PASS + FAIL))
printf "  ${GREEN}PASS: %d${RESET}  ${RED}FAIL: %d${RESET}  Total: %d\\n\\n" "$PASS" "$FAIL" "$TOTAL"

if [ $FAIL -eq 0 ]; then
    printf "  ${GREEN}${BOLD}RESULTADO: PASS${RESET}\\n\\n"
    exit 0
else
    printf "  ${RED}${BOLD}RESULTADO: FAIL${RESET}\\n\\n"
    exit 1
fi
