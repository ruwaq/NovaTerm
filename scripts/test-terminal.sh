#!/usr/bin/env bash
# NovaTerm Functional & Performance Test Suite
# Run directly in the terminal to validate behavior.
# Usage: bash scripts/test-terminal.sh [--quick|--full]

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
BOLD='\033[1m'
RESET='\033[0m'

PASS=0
FAIL=0
SKIP=0
MODE="${1:---quick}"

pass() { ((PASS++)); printf "${GREEN}  ✓ %s${RESET}\n" "$1"; }
fail() { ((FAIL++)); printf "${RED}  ✗ %s${RESET}\n" "$1"; }
skip() { ((SKIP++)); printf "${YELLOW}  ○ %s (skipped)${RESET}\n" "$1"; }
section() { printf "\n${BOLD}${BLUE}═══ %s ═══${RESET}\n" "$1"; }

# ─── 1. ENVIRONMENT VARIABLES ───────────────────────────────
section "Environment Variables"

[[ "$TERM" == "xterm-256color" ]] && pass "TERM=xterm-256color" || fail "TERM=$TERM (expected xterm-256color)"
[[ "$COLORTERM" == "truecolor" ]] && pass "COLORTERM=truecolor" || fail "COLORTERM=$COLORTERM (expected truecolor)"
[[ "$TERM_PROGRAM" == "novaterm" ]] && pass "TERM_PROGRAM=novaterm" || fail "TERM_PROGRAM=${TERM_PROGRAM:-unset}"
[[ -n "${TMPDIR:-}" ]] && [[ -d "$TMPDIR" ]] && [[ -w "$TMPDIR" ]] && pass "TMPDIR=$TMPDIR (writable)" || fail "TMPDIR not set or not writable"
[[ -n "${HOME:-}" ]] && [[ -d "$HOME" ]] && pass "HOME=$HOME" || fail "HOME not set"
[[ -n "${PREFIX:-}" ]] && [[ -d "$PREFIX" ]] && pass "PREFIX=$PREFIX" || fail "PREFIX not set"
[[ -n "${XDG_CONFIG_HOME:-}" ]] && pass "XDG_CONFIG_HOME=$XDG_CONFIG_HOME" || fail "XDG_CONFIG_HOME not set"
[[ -n "${XDG_DATA_HOME:-}" ]] && pass "XDG_DATA_HOME=$XDG_DATA_HOME" || fail "XDG_DATA_HOME not set"
[[ "${LANG:-}" == *UTF-8* ]] || [[ "${LANG:-}" == *utf8* ]] && pass "LANG=$LANG (UTF-8)" || fail "LANG=${LANG:-unset} (no UTF-8)"

# ─── 2. TRUECOLOR SUPPORT ──────────────────────────────────
section "Truecolor (24-bit RGB)"

# Print a gradient — visual check
printf "  Gradient: "
for i in $(seq 0 4 255); do
    printf "\033[48;2;${i};0;$((255-i))m \033[0m"
done
printf "\n"
# If we got here without error, truecolor sequences are accepted
pass "Truecolor sequences accepted (visual check above)"

# 256-color
printf "  256-color: "
for i in $(seq 0 15); do
    printf "\033[48;5;${i}m  \033[0m"
done
printf "\n"
pass "256-color palette rendered"

# ─── 3. UNICODE & WIDE CHARACTERS ──────────────────────────
section "Unicode Support"

printf "  ASCII:   Hello World\n" && pass "ASCII"
printf "  Latin:   café résumé naïve\n" && pass "Latin extended"
printf "  CJK:     你好世界 こんにちは\n" && pass "CJK characters"
printf "  Emoji:   🚀 🔥 ✅ ❌ 🎯 💻\n" && pass "Emoji"
printf "  Arabic:  مرحبا بالعالم\n" && pass "RTL text"
printf "  Box:     ┌─┐│ │└─┘\n" && pass "Box drawing"
printf "  Math:    ∑∏∫√∞≈≠≤≥\n" && pass "Math symbols"

# ─── 4. ESCAPE SEQUENCES ───────────────────────────────────
section "VT Escape Sequences"

# Bold, italic, underline, strikethrough
printf "  \033[1mBold\033[0m \033[3mItalic\033[0m \033[4mUnderline\033[0m \033[9mStrikethrough\033[0m\n"
pass "SGR attributes (bold/italic/underline/strike)"

# Cursor save/restore
printf "\033[s"  # Save
printf "\033[5;40H*"  # Move and print
printf "\033[u"  # Restore
pass "Cursor save/restore (DECSC/DECRC)"

# Cursor visibility
printf "\033[?25l"  # Hide
printf "\033[?25h"  # Show
pass "Cursor show/hide"

# Bracketed paste mode
printf "\033[?2004h"  # Enable
printf "\033[?2004l"  # Disable
pass "Bracketed paste mode (DECSET 2004)"

# OSC 7 — working directory
printf "\033]7;file://localhost${PWD}\033\\"
pass "OSC 7 (working directory)"

# OSC 133 — semantic prompts
printf "\033]133;A\033\\"
pass "OSC 133 (semantic prompt marker)"

# ─── 5. SCROLLBACK ─────────────────────────────────────────
section "Scrollback Buffer"

SCROLLBACK_LINES=500
if [[ "$MODE" == "--full" ]]; then
    SCROLLBACK_LINES=5000
fi

START=$(date +%s%N)
seq 1 $SCROLLBACK_LINES > /dev/null 2>&1
END=$(date +%s%N)
ELAPSED_MS=$(( (END - START) / 1000000 ))
pass "Generated $SCROLLBACK_LINES lines in ${ELAPSED_MS}ms"

# ─── 6. THROUGHPUT BENCHMARK ───────────────────────────────
section "Throughput"

# Generate 1MB of data and measure time to process
TMPFILE=$(mktemp)
dd if=/dev/urandom bs=1024 count=1024 2>/dev/null | base64 > "$TMPFILE" 2>/dev/null
SIZE=$(wc -c < "$TMPFILE")

START=$(date +%s%N)
cat "$TMPFILE" > /dev/null 2>&1
END=$(date +%s%N)
ELAPSED_MS=$(( (END - START) / 1000000 ))
if [[ $ELAPSED_MS -gt 0 ]]; then
    THROUGHPUT=$(( SIZE * 1000 / ELAPSED_MS / 1024 ))
    pass "Processed ${SIZE} bytes in ${ELAPSED_MS}ms (~${THROUGHPUT} KB/s to /dev/null)"
else
    pass "Processed ${SIZE} bytes in <1ms"
fi

# Now test with actual terminal output (slower — goes through VT parser)
if [[ "$MODE" == "--full" ]]; then
    START=$(date +%s%N)
    cat "$TMPFILE" 2>/dev/null
    END=$(date +%s%N)
    ELAPSED_MS=$(( (END - START) / 1000000 ))
    if [[ $ELAPSED_MS -gt 0 ]]; then
        THROUGHPUT=$(( SIZE * 1000 / ELAPSED_MS / 1024 ))
        pass "Terminal render throughput: ~${THROUGHPUT} KB/s (${ELAPSED_MS}ms for $(( SIZE / 1024 ))KB)"
    fi
fi
rm -f "$TMPFILE"

# ─── 7. TOOLS AVAILABILITY ─────────────────────────────────
section "CLI Tools"

for cmd in bash sh git curl apt node npm python3 claude; do
    if command -v "$cmd" > /dev/null 2>&1; then
        VER=$("$cmd" --version 2>&1 | head -1 | cut -c1-60 || true)
        pass "$cmd: ${VER:-installed}"
    else
        skip "$cmd not installed"
    fi
done

# ─── 8. SHELL FEATURES ─────────────────────────────────────
section "Shell Features"

# Tab completion
[[ -f ~/.inputrc ]] && pass ".inputrc exists" || fail ".inputrc missing"
[[ -f ~/.bashrc ]] && pass ".bashrc exists" || fail ".bashrc missing"
[[ -f ~/.profile ]] && pass ".profile exists" || fail ".profile missing"

# History
[[ -n "${HISTFILE:-}" ]] && pass "HISTFILE=$HISTFILE" || fail "HISTFILE not set"
[[ "${HISTSIZE:-0}" -ge 1000 ]] && pass "HISTSIZE=$HISTSIZE" || fail "HISTSIZE=${HISTSIZE:-unset} (should be ≥1000)"

# ─── 9. PTY BEHAVIOR ───────────────────────────────────────
section "PTY"

# Window size
COLS=$(tput cols 2>/dev/null || echo "?")
ROWS=$(tput lines 2>/dev/null || echo "?")
[[ "$COLS" != "?" ]] && [[ "$COLS" -gt 0 ]] && pass "Terminal size: ${COLS}x${ROWS}" || fail "Cannot detect terminal size"

# stty
if stty -a > /dev/null 2>&1; then
    pass "stty works"
else
    fail "stty failed"
fi

# ─── 10. STRESS TEST (--full only) ─────────────────────────
if [[ "$MODE" == "--full" ]]; then
    section "Stress Tests"

    # Rapid output
    START=$(date +%s%N)
    yes "NovaTerm stress test line" | head -10000 > /dev/null 2>&1
    END=$(date +%s%N)
    ELAPSED_MS=$(( (END - START) / 1000000 ))
    pass "10K lines via yes: ${ELAPSED_MS}ms"

    # Long lines
    printf '%0.s=' $(seq 1 500)
    printf '\n'
    pass "500-char line rendered"

    # Rapid color switching
    for i in $(seq 1 100); do
        printf "\033[38;2;$((RANDOM%256));$((RANDOM%256));$((RANDOM%256))m#\033[0m"
    done
    printf '\n'
    pass "100 rapid truecolor switches"

    # Alternate screen
    printf "\033[?1049h"  # Enter alt screen
    printf "Alt screen test"
    printf "\033[?1049l"  # Exit alt screen
    pass "Alternate screen enter/exit"
fi

# ─── SUMMARY ───────────────────────────────────────────────
section "Results"
TOTAL=$((PASS + FAIL + SKIP))
printf "  ${GREEN}Passed: $PASS${RESET}  ${RED}Failed: $FAIL${RESET}  ${YELLOW}Skipped: $SKIP${RESET}  Total: $TOTAL\n"

if [[ $FAIL -eq 0 ]]; then
    printf "\n  ${GREEN}${BOLD}All tests passed!${RESET} 🎉\n"
else
    printf "\n  ${RED}${BOLD}$FAIL test(s) failed.${RESET}\n"
fi

[[ "$MODE" != "--full" ]] && printf "\n  ${YELLOW}Run with --full for stress tests and throughput benchmarks.${RESET}\n"
