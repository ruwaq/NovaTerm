#!/bin/bash
# NovaTerm dual-prefix quick test (assumes APK already installed, emulator running)

APP_PACKAGE="com.nvterm"
PREFIX="/data/data/$APP_PACKAGE/files/usr"
ADB="${ADB:-adb}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

PASS=0
FAIL=0
pass() { echo -e "${GREEN}PASS${NC}: $1"; PASS=$((PASS+1)); }
fail() { echo -e "${RED}FAIL${NC}: $1"; FAIL=$((FAIL+1)); }
info() { echo -e "${YELLOW}INFO${NC}: $1"; }

info "=== NovaTerm Dual-Prefix Quick Test ==="

# 1. Verify app running
PID=$($ADB shell "pidof $APP_PACKAGE" | awk '{print $1}')
if [ -z "$PID" ]; then
    fail "App not running. Start NovaTerm first."
    exit 1
fi
pass "App running (pid=$PID)"

# 2. Ensure symlink exists
$ADB shell "nsenter --mount --target=$PID -- ln -sf /data/data/$APP_PACKAGE /data/data/com.termux" 2>/dev/null || true

# 3. Quick node test
info "Testing node with Termux shebang..."
NODE_VERSION=$($ADB shell "run-as $APP_PACKAGE env LD_PRELOAD=$PREFIX/lib/libnvterm-exec-ld-preload.so $PREFIX/bin/node --version" 2>/dev/null | tr -d '\r')
if [ -n "$NODE_VERSION" ]; then
    pass "Node.js works: $NODE_VERSION"
else
    fail "Node.js execution failed"
fi

# 4. Quick npm test
info "Testing npm..."
NPM_VERSION=$($ADB shell "run-as $APP_PACKAGE env LD_PRELOAD=$PREFIX/lib/libnvterm-exec-ld-preload.so $PREFIX/bin/npm --version" 2>/dev/null | tr -d '\r')
if [ -n "$NPM_VERSION" ]; then
    pass "npm works: $NPM_VERSION"
else
    fail "npm execution failed"
fi

# 5. Summary
echo ""
echo "=== Summary ==="
echo -e "${GREEN}Passed: $PASS${NC}"
echo -e "${RED}Failed: $FAIL${NC}"
[ $FAIL -eq 0 ] && echo -e "${GREEN}All quick tests passed!${NC}"
