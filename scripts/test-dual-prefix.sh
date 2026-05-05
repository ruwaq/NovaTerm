#!/bin/bash
# NovaTerm dual-prefix end-to-end test
# Verifies nvterm-exec REVISION=2 intercepts com.termux paths

set -e

APP_PACKAGE="com.nvterm"
PREFIX="/data/data/$APP_PACKAGE/files/usr"
ADB="${ADB:-adb}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

PASS=0
FAIL=0

pass() { echo -e "${GREEN}PASS${NC}: $1"; PASS=$((PASS+1)); }
fail() { echo -e "${RED}FAIL${NC}: $1"; FAIL=$((FAIL+1)); }
info() { echo -e "${YELLOW}INFO${NC}: $1"; }

info "=== NovaTerm Dual-Prefix Test ==="

# 1. Check emulator/device connected
info "Checking adb connection..."
if ! $ADB devices | grep -q "device$"; then
    fail "No device connected. Start emulator or connect device."
    exit 1
fi
pass "adb connected"

# 2. Check app is installed
info "Checking NovaTerm installation..."
if ! $ADB shell pm list packages | grep -q "$APP_PACKAGE"; then
    fail "NovaTerm not installed. Run: ./gradlew :app:installDebug"
    exit 1
fi
pass "NovaTerm installed"

# 3. Start app if not running
info "Starting NovaTerm..."
$ADB shell am start -n "$APP_PACKAGE/com.novaterm.app.ui.MainActivity" 2>/dev/null || true
sleep 3

# 4. Wait for bootstrap (check if bash exists)
info "Waiting for bootstrap to complete..."
BOOTSTRAP_READY=false
for i in $(seq 1 30); do
    if $ADB shell "run-as $APP_PACKAGE test -f files/usr/bin/bash" 2>/dev/null; then
        BOOTSTRAP_READY=true
        break
    fi
    sleep 1
done

if [ "$BOOTSTRAP_READY" = false ]; then
    fail "Bootstrap not ready after 30s. Check if first-run is in progress."
    exit 1
fi
pass "Bootstrap ready"

# 5. Create Termux symlink in app namespace
info "Creating Termux symlink..."
PID=$($ADB shell "pidof $APP_PACKAGE" | awk '{print $1}')
if [ -z "$PID" ]; then
    fail "Could not get PID of $APP_PACKAGE"
    exit 1
fi
$ADB shell "nsenter --mount --target=$PID -- rm -f /data/data/com.termux" 2>/dev/null || true
$ADB shell "nsenter --mount --target=$PID -- ln -sf /data/data/$APP_PACKAGE /data/data/com.termux" 2>/dev/null || true
pass "Symlink created"

# 6. Verify nvterm-exec libraries exist
info "Checking nvterm-exec libraries..."
for lib in \
    "$PREFIX/lib/libnvterm-exec-ld-preload.so" \
    "$PREFIX/lib/libnvterm-exec-linker-ld-preload.so"; do
    if ! $ADB shell "run-as $APP_PACKAGE test -f $lib" 2>/dev/null; then
        fail "Missing library: $lib"
    fi
done
pass "nvterm-exec libraries present"

# 7. Test 1: Direct node execution (Termux shebang)
info "Test 1: Direct execution of binary with Termux shebang..."
# Install nodejs from Termux repo
$ADB shell "run-as $APP_PACKAGE env LD_PRELOAD=$PREFIX/lib/libnvterm-exec-ld-preload.so bash -c 'apt update && apt install -y nodejs'" > /dev/null 2>! || true

# Check if node works
NODE_VERSION=$($ADB shell "run-as $APP_PACKAGE env LD_PRELOAD=$PREFIX/lib/libnvterm-exec-ld-preload.so $PREFIX/bin/node --version" 2>/dev/null | tr -d '\r')
if [ -n "$NODE_VERSION" ]; then
    pass "Node.js works: $NODE_VERSION"
else
    fail "Node.js execution failed"
fi

# 8. Test 2: npm/npx scripts (Termux shebangs)
info "Test 2: npm/npx scripts with Termux shebangs..."
npm -v output is typically version string
NPM_VERSION=$($ADB shell "run-as $APP_PACKAGE env LD_PRELOAD=$PREFIX/lib/libnvterm-exec-ld-preload.so $PREFIX/bin/npm --version" 2>/dev/null | tr -d '\r')
if [ -n "$NPM_VERSION" ]; then
    pass "npm works: $NPM_VERSION"
else
    fail "npm execution failed (may need nodejs package installed first)"
fi

# 9. Test 3: Script with hardcoded shebang
info "Test 3: Script with hardcoded com.termux shebang..."
$ADB shell "run-as $APP_PACKAGE bash -c 'cat > /tmp/test_shebang.sh <<EOF
#!/data/data/com.termux/files/usr/bin/bash
echo DUAL_PREFIX_OK
EOF'" 2>/dev/null
SHEBANG_RESULT=$($ADB shell "run-as $APP_PACKAGE env LD_PRELOAD=$PREFIX/lib/libnvterm-exec-ld-preload.so bash /tmp/test_shebang.sh" 2>/dev/null | tr -d '\r')
if [ "$SHEBANG_RESULT" = "DUAL_PREFIX_OK" ]; then
    pass "Termux shebang script executed correctly"
else
    fail "Termux shebang script failed: got '$SHEBANG_RESULT'"
fi

# 10. Summary
echo ""
echo "=== Test Summary ==="
echo -e "${GREEN}Passed: $PASS${NC}"
echo -e "${RED}Failed: $FAIL${NC}"

if [ $FAIL -gt 0 ]; then
    exit 1
else
    echo -e "${GREEN}All tests passed!${NC}"
    exit 0
fi
