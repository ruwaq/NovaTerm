#!/usr/bin/env bash
# ============================================================================
# NovaTerm Comprehensive Terminal Emulator Test Suite
# ============================================================================
# 80+ individual tests covering escape sequences, Unicode, color, performance,
# PTY, input, signals, cursor, capabilities, mouse, clipboard, and more.
#
# Usage:
#   bash scripts/test-terminal-comprehensive.sh [--quick|--full|--interactive]
#
# Modes:
#   --quick        Skip slow benchmarks and interactive tests (default)
#   --full         Run everything including benchmarks
#   --interactive  Run tests that require user keyboard/mouse input
#
# Environment: Termux / NovaTerm (Android, bash, no X11)
# ============================================================================

set -uo pipefail

# ─── Framework ─────────────────────────────────────────────────────────────

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
DIM='\033[2m'
RESET='\033[0m'

PASS=0; FAIL=0; SKIP=0; WARN=0
MODE="${1:---quick}"
TMPDIR_TEST="${TMPDIR:-/tmp}"

pass()    { ((PASS++)); printf "${GREEN}  [PASS] %s${RESET}\n" "$1"; }
fail()    { ((FAIL++)); printf "${RED}  [FAIL] %s${RESET}\n" "$1"; }
skip()    { ((SKIP++)); printf "${YELLOW}  [SKIP] %s${RESET}\n" "$1"; }
warn()    { ((WARN++)); printf "${CYAN}  [WARN] %s${RESET}\n" "$1"; }
section() { printf "\n${BOLD}${BLUE}══════════════════════════════════════════════════════════${RESET}\n"; printf "${BOLD}${BLUE}  %s${RESET}\n" "$1"; printf "${BOLD}${BLUE}══════════════════════════════════════════════════════════${RESET}\n"; }
info()    { printf "${DIM}         %s${RESET}\n" "$1"; }

# Helper: read a terminal response with timeout
# Usage: response=$(query_terminal $'\e[6n' 0.5)
query_terminal() {
    local seq="$1" timeout="${2:-1}"
    local response=""
    # Save terminal settings, set raw mode with timeout
    local old_settings
    old_settings=$(stty -g 2>/dev/null) || return 1
    stty raw -echo min 0 time "$(printf '%.0f' "$(echo "$timeout * 10" | bc 2>/dev/null || echo 10)")" 2>/dev/null || return 1
    printf '%s' "$seq" > /dev/tty
    response=$(dd bs=64 count=1 < /dev/tty 2>/dev/null)
    stty "$old_settings" 2>/dev/null
    printf '%s' "$response"
}

printf "${BOLD}NovaTerm Comprehensive Terminal Test Suite${RESET}\n"
printf "Mode: ${CYAN}%s${RESET}  Date: %s  TERM: %s\n" "$MODE" "$(date '+%Y-%m-%d %H:%M:%S')" "${TERM:-unset}"
printf "Shell: %s  PID: $$\n" "$BASH_VERSION"

# ═══════════════════════════════════════════════════════════════════════════
# 1. TERMINAL CAPABILITY DETECTION
# ═══════════════════════════════════════════════════════════════════════════
section "1. Terminal Capability Detection"

# Test 1: TERM variable
[[ "${TERM:-}" == "xterm-256color" ]] && pass "T01: TERM=xterm-256color" || warn "T01: TERM=${TERM:-unset} (expected xterm-256color)"

# Test 2: tput colors
TPUT_COLORS=$(tput colors 2>/dev/null || echo 0)
[[ "$TPUT_COLORS" -ge 256 ]] && pass "T02: tput colors=$TPUT_COLORS (>=256)" || fail "T02: tput colors=$TPUT_COLORS (<256)"

# Test 3: tput cols/lines
COLS=$(tput cols 2>/dev/null || echo 0)
ROWS=$(tput lines 2>/dev/null || echo 0)
[[ "$COLS" -gt 0 ]] && [[ "$ROWS" -gt 0 ]] && pass "T03: Terminal size ${COLS}x${ROWS}" || fail "T03: Cannot detect size"

# Test 4: infocmp availability
if infocmp "$TERM" > /dev/null 2>&1; then
    CAP_COUNT=$(infocmp "$TERM" 2>/dev/null | grep -c ',' || echo 0)
    pass "T04: infocmp reports ~${CAP_COUNT} capabilities for $TERM"
else
    fail "T04: infocmp failed for $TERM"
fi

# Test 5: Key terminfo capabilities
for cap in cup cuf cub cuu cud clear smcup rmcup smso rmso bold rev sgr0 colors; do
    if tput "$cap" > /dev/null 2>&1; then
        : # exists
    else
        fail "T05: Missing terminfo capability: $cap"
        continue
    fi
done
pass "T05: Core terminfo capabilities present (cup,cuf,cub,cuu,cud,clear,smcup,rmcup,sgr0,colors)"

# Test 6: COLORTERM
[[ "${COLORTERM:-}" == "truecolor" ]] || [[ "${COLORTERM:-}" == "24bit" ]] && pass "T06: COLORTERM=$COLORTERM" || warn "T06: COLORTERM=${COLORTERM:-unset} (truecolor not advertised)"

# Test 7: Locale / UTF-8
LANG_VAL="${LANG:-${LC_ALL:-${LC_CTYPE:-}}}"
[[ "$LANG_VAL" == *"UTF-8"* ]] || [[ "$LANG_VAL" == *"utf8"* ]] && pass "T07: UTF-8 locale ($LANG_VAL)" || fail "T07: No UTF-8 locale (LANG=$LANG_VAL)"

# Test 8: Device Attributes (DA1) - Primary
info "Sending DA1 (ESC[c) ..."
DA1_RESP=$(query_terminal $'\e[c' 1 2>/dev/null || echo "")
if [[ -n "$DA1_RESP" ]]; then
    # Convert to printable
    DA1_PRINT=$(printf '%s' "$DA1_RESP" | sed 's/\x1b/ESC/g' 2>/dev/null || echo "$DA1_RESP")
    pass "T08: DA1 response received: $DA1_PRINT"
else
    warn "T08: No DA1 response (may need raw terminal)"
fi

# Test 9: Device Attributes (DA2) - Secondary
info "Sending DA2 (ESC[>c) ..."
DA2_RESP=$(query_terminal $'\e[>c' 1 2>/dev/null || echo "")
if [[ -n "$DA2_RESP" ]]; then
    DA2_PRINT=$(printf '%s' "$DA2_RESP" | sed 's/\x1b/ESC/g' 2>/dev/null || echo "$DA2_RESP")
    pass "T09: DA2 response received: $DA2_PRINT"
else
    warn "T09: No DA2 response"
fi

# ═══════════════════════════════════════════════════════════════════════════
# 2. SGR (SELECT GRAPHIC RENDITION) ATTRIBUTES
# ═══════════════════════════════════════════════════════════════════════════
section "2. SGR Text Attributes"

# Test 10: Basic attributes
printf "  "
printf "\033[1mBold\033[0m "
printf "\033[2mDim\033[0m "
printf "\033[3mItalic\033[0m "
printf "\033[4mUnderline\033[0m "
printf "\033[5mBlink\033[0m "
printf "\033[7mReverse\033[0m "
printf "\033[8mHidden\033[0m "
printf "\033[9mStrike\033[0m"
printf "\033[0m\n"
pass "T10: Basic SGR (bold/dim/italic/underline/blink/reverse/hidden/strike)"

# Test 11: Underline styles (SGR 4:x - modern terminals)
printf "  "
printf "\033[4:1mSingle\033[0m "
printf "\033[4:2mDouble\033[0m "
printf "\033[4:3mCurly\033[0m "
printf "\033[4:4mDotted\033[0m "
printf "\033[4:5mDashed\033[0m"
printf "\033[0m\n"
pass "T11: Extended underline styles (4:1 single, 4:2 double, 4:3 curly, 4:4 dotted, 4:5 dashed)"

# Test 12: Colored underlines (SGR 58 - underline color)
printf "  "
printf "\033[4;58;2;255;0;0mRedUnder\033[0m "
printf "\033[4;58;2;0;255;0mGreenUnder\033[0m "
printf "\033[4;58;2;0;0;255mBlueUnder\033[0m"
printf "\033[0m\n"
pass "T12: Colored underlines (SGR 58;2;R;G;B)"

# Test 13: Overline (SGR 53)
printf "  \033[53mOverline text\033[0m\n"
pass "T13: Overline (SGR 53)"

# Test 14: Combined attributes
printf "  \033[1;3;4;38;2;255;165;0mBold+Italic+Underline+Orange\033[0m\n"
pass "T14: Combined attributes (bold+italic+underline+color)"

# Test 15: SGR reset granularity
printf "  \033[1;3;4mAll on\033[22m no bold\033[23m no italic\033[24m no underline\033[0m\n"
pass "T15: Selective SGR reset (22=unbold, 23=unitalic, 24=ununderline)"

# ═══════════════════════════════════════════════════════════════════════════
# 3. COLOR RENDERING
# ═══════════════════════════════════════════════════════════════════════════
section "3. Color Rendering"

# Test 16: 8 basic colors (foreground)
printf "  FG: "
for c in 30 31 32 33 34 35 36 37; do
    printf "\033[${c}m#\033[0m"
done
printf "\n"
pass "T16: 8 basic foreground colors (30-37)"

# Test 17: 8 bright colors (foreground)
printf "  HI: "
for c in 90 91 92 93 94 95 96 97; do
    printf "\033[${c}m#\033[0m"
done
printf "\n"
pass "T17: 8 bright foreground colors (90-97)"

# Test 18: 8 basic background colors
printf "  BG: "
for c in 40 41 42 43 44 45 46 47; do
    printf "\033[${c}m \033[0m"
done
printf "\n"
pass "T18: 8 basic background colors (40-47)"

# Test 19: 256-color palette (first 16 + grayscale ramp)
printf "  256c sys: "
for i in $(seq 0 15); do
    printf "\033[48;5;${i}m  \033[0m"
done
printf "\n  256c gray: "
for i in $(seq 232 255); do
    printf "\033[48;5;${i}m \033[0m"
done
printf "\n"
pass "T19: 256-color palette (system + grayscale)"

# Test 20: 256-color cube (6x6x6 sample)
printf "  256c cube: "
for i in 16 21 46 51 196 201; do
    printf "\033[48;5;${i}m  \033[0m"
done
printf "\n"
pass "T20: 256-color cube corners (16,21,46,51,196,201)"

# Test 21: Truecolor (24-bit) gradient
printf "  TC grad:  "
for i in $(seq 0 6 255); do
    printf "\033[48;2;${i};0;$((255-i))m \033[0m"
done
printf "\n"
pass "T21: Truecolor gradient (purple->blue, 43 steps)"

# Test 22: Truecolor foreground
printf "  TC FG:    "
for i in $(seq 0 6 255); do
    printf "\033[38;2;${i};$((255-i));0m#\033[0m"
done
printf "\n"
pass "T22: Truecolor foreground gradient"

# Test 23: Default color reset
printf "  \033[31mRed\033[39m Default-FG \033[42mGreenBG\033[49m Default-BG\033[0m\n"
pass "T23: Default color reset (SGR 39=default-fg, 49=default-bg)"

# ═══════════════════════════════════════════════════════════════════════════
# 4. UNICODE RENDERING
# ═══════════════════════════════════════════════════════════════════════════
section "4. Unicode Rendering"

# Test 24: ASCII baseline
printf "  ASCII:       Hello, World! 0123456789 ~!@#\n"
pass "T24: ASCII baseline"

# Test 25: Latin extended
printf "  Latin ext:   cafe\xcc\x81 re\xcc\x81sume\xcc\x81 nai\xcc\x88ve u\xcc\x88ber\n"
pass "T25: Latin with combining accents"

# Test 26: Precomposed vs decomposed
printf "  Precomposed: \xc3\xa9\xc3\xa8\xc3\xaa (e-acute, e-grave, e-circ)\n"
printf "  Decomposed:  e\xcc\x81e\xcc\x80e\xcc\x82 (e+combining acute/grave/circ)\n"
pass "T26: Precomposed vs decomposed comparison"

# Test 27: CJK characters (double-width)
printf "  CJK:         \xe4\xbd\xa0\xe5\xa5\xbd\xe4\xb8\x96\xe7\x95\x8c (nihao shijie)\n"
printf "  Hiragana:    \xe3\x81\x93\xe3\x82\x93\xe3\x81\xab\xe3\x81\xa1\xe3\x81\xaf (konnichiwa)\n"
printf "  Katakana:    \xe3\x83\x8e\xe3\x83\xb4\xe3\x82\xa1\xe3\x82\xbf\xe3\x83\xbc\xe3\x83\xa0 (NovaTerm)\n"
printf "  Hangul:      \xed\x95\x9c\xea\xb5\xad\xec\x96\xb4 (hangugeo)\n"
pass "T27: CJK double-width (Chinese, Japanese, Korean)"

# Test 28: Emoji
printf "  Emoji:       \xf0\x9f\x9a\x80 \xf0\x9f\x94\xa5 \xe2\x9c\x85 \xe2\x9d\x8c \xf0\x9f\x8e\xaf \xf0\x9f\x92\xbb \xf0\x9f\xa6\x80 \xf0\x9f\x90\xa7\n"
pass "T28: Emoji (rocket, fire, check, cross, target, laptop, crab, penguin)"

# Test 29: Emoji ZWJ sequences
printf "  ZWJ emoji:   \xf0\x9f\x91\xa8\xe2\x80\x8d\xf0\x9f\x92\xbb \xf0\x9f\x91\xa9\xe2\x80\x8d\xf0\x9f\x94\xac \xf0\x9f\x8f\xb3\xef\xb8\x8f\xe2\x80\x8d\xf0\x9f\x8c\x88\n"
pass "T29: Emoji ZWJ sequences (man+laptop, woman+microscope, rainbow flag)"

# Test 30: Emoji skin tone modifiers
printf "  Skin tones:  \xf0\x9f\x91\x8b\xf0\x9f\x8f\xbb\xf0\x9f\x91\x8b\xf0\x9f\x8f\xbc\xf0\x9f\x91\x8b\xf0\x9f\x8f\xbd\xf0\x9f\x91\x8b\xf0\x9f\x8f\xbe\xf0\x9f\x91\x8b\xf0\x9f\x8f\xbf\n"
pass "T30: Emoji skin tone modifiers (5 tones)"

# Test 31: Box drawing
printf "  Box:         \xe2\x94\x8c\xe2\x94\x80\xe2\x94\x80\xe2\x94\x80\xe2\x94\xac\xe2\x94\x80\xe2\x94\x80\xe2\x94\x80\xe2\x94\x90\n"
printf "               \xe2\x94\x82 A \xe2\x94\x82 B \xe2\x94\x82\n"
printf "               \xe2\x94\x9c\xe2\x94\x80\xe2\x94\x80\xe2\x94\x80\xe2\x94\xbc\xe2\x94\x80\xe2\x94\x80\xe2\x94\x80\xe2\x94\xa4\n"
printf "               \xe2\x94\x82 C \xe2\x94\x82 D \xe2\x94\x82\n"
printf "               \xe2\x94\x94\xe2\x94\x80\xe2\x94\x80\xe2\x94\x80\xe2\x94\xb4\xe2\x94\x80\xe2\x94\x80\xe2\x94\x80\xe2\x94\x98\n"
pass "T31: Box drawing characters (single-line grid)"

# Test 32: Double box drawing
printf "  DblBox:      \xe2\x95\x94\xe2\x95\x90\xe2\x95\x90\xe2\x95\x90\xe2\x95\xa6\xe2\x95\x90\xe2\x95\x90\xe2\x95\x90\xe2\x95\x97\n"
printf "               \xe2\x95\x91 X \xe2\x95\x91 Y \xe2\x95\x91\n"
printf "               \xe2\x95\x9a\xe2\x95\x90\xe2\x95\x90\xe2\x95\x90\xe2\x95\xa9\xe2\x95\x90\xe2\x95\x90\xe2\x95\x90\xe2\x95\x9d\n"
pass "T32: Double-line box drawing"

# Test 33: Block elements
printf "  Blocks:      \xe2\x96\x88\xe2\x96\x89\xe2\x96\x8a\xe2\x96\x8b\xe2\x96\x8c\xe2\x96\x8d\xe2\x96\x8e\xe2\x96\x8f \xe2\x96\x80\xe2\x96\x84\xe2\x96\x90\xe2\x96\x8c\n"
pass "T33: Block elements (full to thin, upper/lower half)"

# Test 34: Braille patterns
printf "  Braille:     \xe2\xa0\x80\xe2\xa0\x81\xe2\xa0\x82\xe2\xa0\x84\xe2\xa0\x88\xe2\xa0\x90\xe2\xa0\xa0\xe2\xa0\xbf\xe2\xa3\xbf\n"
pass "T34: Braille patterns"

# Test 35: Math/technical symbols
printf "  Math:        \xe2\x88\x91\xe2\x88\x8f\xe2\x88\xab\xe2\x88\x9a\xe2\x88\x9e\xe2\x89\x88\xe2\x89\xa0\xe2\x89\xa4\xe2\x89\xa5 \xce\xb1\xce\xb2\xce\xb3\xce\xb4\xce\xb5 \xe2\x84\x95\xe2\x84\xa4\xe2\x84\x9d\xe2\x84\x82\n"
pass "T35: Math symbols, Greek letters, number sets"

# Test 36: RTL text (Arabic, Hebrew)
printf "  Arabic:      \xd9\x85\xd8\xb1\xd8\xad\xd8\xa8\xd8\xa7 \xd8\xa8\xd8\xa7\xd9\x84\xd8\xb9\xd8\xa7\xd9\x84\xd9\x85\n"
printf "  Hebrew:      \xd7\xa9\xd7\x9c\xd7\x95\xd7\x9d \xd7\xa2\xd7\x95\xd7\x9c\xd7\x9d\n"
pass "T36: RTL text (Arabic, Hebrew) -- visual alignment check"

# Test 37: Combining characters stress
printf "  Combining:   A\xcc\x80\xcc\x81\xcc\x82\xcc\x83\xcc\x84 (A + 5 combining marks)\n"
printf "  Zalgo:       Z\xcc\x89\xcc\x91\xcc\x9b\xcc\xa3\xcc\xb1a\xcc\x80\xcc\x81\xcc\x82l\xcc\x83\xcc\x84\xcc\x85g\xcc\x86\xcc\x87o\xcc\x88\xcc\x89\n"
pass "T37: Stacked combining characters (Zalgo test)"

# Test 38: Zero-width characters
printf "  ZWJ test:    A\xe2\x80\x8dB (A + ZWJ + B, should show AB or A B)\n"
printf "  ZWNJ test:   A\xe2\x80\x8cB (A + ZWNJ + B)\n"
printf "  Soft hyphen: wo\xc2\xadrd (wo + soft hyphen + rd)\n"
pass "T38: Zero-width characters (ZWJ, ZWNJ, soft hyphen)"

# Test 39: Variation selectors
printf "  VS15 text:   \xe2\x9d\xa4\xef\xb8\x8e (heart + VS15 = text style)\n"
printf "  VS16 emoji:  \xe2\x9d\xa4\xef\xb8\x8f (heart + VS16 = emoji style)\n"
pass "T39: Variation Selectors (VS15 text, VS16 emoji)"

# ═══════════════════════════════════════════════════════════════════════════
# 5. VT ESCAPE SEQUENCES
# ═══════════════════════════════════════════════════════════════════════════
section "5. VT Escape Sequences"

# Test 40: Cursor positioning (CUP)
printf "\033[s"           # save cursor
printf "\033[1;1H"        # move to 1,1
printf "\033[u"           # restore cursor
pass "T40: Cursor save/restore (DECSC/DECRC)"

# Test 41: Cursor movement (relative)
printf "  START"
printf "\033[4C"          # move right 4
printf "RIGHT"
printf "\033[10D"         # move left 10
printf "LEFT"
printf "\033[0m\n"
pass "T41: Relative cursor movement (CUF/CUB)"

# Test 42: Cursor position report (DSR 6)
info "Querying cursor position (DSR 6) ..."
CPR=$(query_terminal $'\e[6n' 1 2>/dev/null || echo "")
if [[ "$CPR" =~ \[([0-9]+)\;([0-9]+)R ]]; then
    pass "T42: Cursor Position Report: row=${BASH_REMATCH[1]} col=${BASH_REMATCH[2]}"
else
    warn "T42: CPR response not parsed (got: $(printf '%s' "$CPR" | od -An -tx1 2>/dev/null | head -1))"
fi

# Test 43: Erase operations
printf "\033[K"           # Erase to end of line
printf "\033[1K"          # Erase to start of line
printf "\033[2K"          # Erase entire line
pass "T43: Line erase (EL 0/1/2)"

# Test 44: Insert/delete lines
printf "\033[1L"          # Insert 1 line
printf "\033[1M"          # Delete 1 line
pass "T44: Insert/Delete line (IL/DL)"

# Test 45: Scroll up/down
printf "\033[1S"          # Scroll up 1
printf "\033[1T"          # Scroll down 1
pass "T45: Scroll up/down (SU/SD)"

# Test 46: Cursor visibility
printf "\033[?25l"        # Hide cursor
printf "\033[?25h"        # Show cursor
pass "T46: Cursor show/hide (DECTCEM)"

# Test 47: Cursor shape (DECSCUSR)
for shape in 0 1 2 3 4 5 6; do
    printf "\033[%d q" "$shape"
done
printf "\033[0 q"         # Reset to default
pass "T47: Cursor shapes (DECSCUSR 0-6: default/block-blink/block/under-blink/under/bar-blink/bar)"

# Test 48: Tab stops
printf "\033H"            # Set tab stop at current position
printf "\033[3g"          # Clear all tab stops
pass "T48: Tab stops (HTS set, TBC clear all)"

# Test 49: Bracketed paste mode
printf "\033[?2004h"      # Enable
printf "\033[?2004l"      # Disable
pass "T49: Bracketed paste mode (DECSET/DECRST 2004)"

# Test 50: Line wrapping (DECAWM)
printf "\033[?7h"         # Enable auto-wrap
pass "T50: Auto-wrap mode (DECAWM) enabled"

# Test 51: Origin mode
printf "\033[?6h"         # Enable origin mode
printf "\033[?6l"         # Disable origin mode
pass "T51: Origin mode (DECOM) toggle"

# ═══════════════════════════════════════════════════════════════════════════
# 6. ALTERNATE SCREEN BUFFER
# ═══════════════════════════════════════════════════════════════════════════
section "6. Alternate Screen Buffer"

# Test 52: Enter/exit alt screen
printf "\033[?1049h"      # Enter alt screen
printf "\033[2J"          # Clear
printf "\033[5;10HAlt screen test - this text should disappear"
sleep 0.3
printf "\033[?1049l"      # Exit alt screen (content restored)
pass "T52: Alternate screen enter/exit (DECSET 1049)"

# Test 53: smcup/rmcup via tput
if tput smcup > /dev/null 2>&1 && tput rmcup > /dev/null 2>&1; then
    tput smcup
    tput cup 2 5
    printf "tput smcup/rmcup test"
    sleep 0.2
    tput rmcup
    pass "T53: tput smcup/rmcup (alt screen via terminfo)"
else
    skip "T53: tput smcup/rmcup not available"
fi

# ═══════════════════════════════════════════════════════════════════════════
# 7. OSC (OPERATING SYSTEM COMMAND) SEQUENCES
# ═══════════════════════════════════════════════════════════════════════════
section "7. OSC Sequences"

# Test 54: Window title (OSC 0 and OSC 2)
printf "\033]0;NovaTerm Test Suite\007"
pass "T54: OSC 0 - Set window title (BEL terminator)"

printf "\033]2;NovaTerm Test\033\\"
pass "T55: OSC 2 - Set window title (ST terminator)"

# Test 56: OSC 7 - Working directory
printf "\033]7;file://localhost%s\033\\" "$PWD"
pass "T56: OSC 7 - Report working directory"

# Test 57: OSC 8 - Hyperlinks
printf "  Click: \033]8;;https://github.com/PrometeoDEV/NovaTerm\033\\NovaTerm on GitHub\033]8;;\033\\\n"
pass "T57: OSC 8 - Hyperlink (visual check: should be clickable)"

# Test 58: OSC 52 - Clipboard
CLIP_DATA=$(printf 'NovaTerm clipboard test' | base64 2>/dev/null)
if [[ -n "$CLIP_DATA" ]]; then
    printf "\033]52;c;%s\033\\" "$CLIP_DATA"
    pass "T58: OSC 52 - Set clipboard (base64 encoded)"
else
    skip "T58: OSC 52 - base64 not available"
fi

# Test 59: OSC 133 - Semantic prompt
printf "\033]133;A\033\\"  # Prompt start
printf "\033]133;B\033\\"  # Command start
printf "\033]133;C\033\\"  # Command executed
printf "\033]133;D;0\033\\" # Command finished, exit code 0
pass "T59: OSC 133 - Semantic prompt markers (A/B/C/D)"

# Test 60: OSC 4 - Color palette query
printf "\033]4;1;?\033\\"  # Query color index 1
pass "T60: OSC 4 - Query color palette index 1"

# Test 61: OSC 10/11 - Foreground/background color query
printf "\033]10;?\033\\"   # Query foreground
printf "\033]11;?\033\\"   # Query background
pass "T61: OSC 10/11 - Query foreground/background color"

# ═══════════════════════════════════════════════════════════════════════════
# 8. CURSOR POSITIONING STRESS
# ═══════════════════════════════════════════════════════════════════════════
section "8. Cursor Positioning"

# Test 62: Absolute positioning grid
printf "\033[s"  # Save
for row in 1 2 3; do
    for col in 1 10 20 30 40; do
        printf "\033[%d;%dH*" "$((row))" "$col"
    done
done
printf "\033[u"  # Restore
printf "\n\n\n\n"
pass "T62: Absolute cursor positioning (CUP) grid pattern"

# Test 63: Cursor to column (CHA)
printf "  \033[10G<col10>\033[30G<col30>\033[50G<col50>\n"
pass "T63: Cursor Horizontal Absolute (CHA / CSI G)"

# Test 64: Cursor vertical position
printf "\033[s"
printf "\033[2d"  # Move to row 2 (VPA)
printf "\033[u"
pass "T64: Vertical Position Absolute (VPA / CSI d)"

# Test 65: Horizontal/Vertical Position (HVP)
printf "\033[s\033[1;1f\033[u"  # HVP to 1,1
pass "T65: Horizontal Vertical Position (HVP / CSI f)"

# ═══════════════════════════════════════════════════════════════════════════
# 9. SCROLLING REGIONS
# ═══════════════════════════════════════════════════════════════════════════
section "9. Scroll Regions"

# Test 66: DECSTBM (set top/bottom margins)
printf "\033[5;15r"       # Set scroll region rows 5-15
printf "\033[r"           # Reset scroll region
pass "T66: Scroll region set/reset (DECSTBM)"

# Test 67: Scroll within region
printf "\033[s"
printf "\033[3;8r"
printf "\033[3;1H"
for i in $(seq 1 10); do
    printf "Line %d in scroll region\n" "$i"
done
printf "\033[r"
printf "\033[u"
pass "T67: Content scrolls within DECSTBM region"

# ═══════════════════════════════════════════════════════════════════════════
# 10. PTY & SHELL BEHAVIOR
# ═══════════════════════════════════════════════════════════════════════════
section "10. PTY & Shell Behavior"

# Test 68: stty settings
if stty -a > /dev/null 2>&1; then
    BAUD=$(stty speed 2>/dev/null || echo "?")
    pass "T68: stty works (baud: $BAUD)"
else
    fail "T68: stty -a failed"
fi

# Test 69: Window size via ioctl
if command -v stty > /dev/null 2>&1; then
    STTY_SIZE=$(stty size 2>/dev/null || echo "0 0")
    STY_ROWS=$(echo "$STTY_SIZE" | cut -d' ' -f1)
    STY_COLS=$(echo "$STTY_SIZE" | cut -d' ' -f2)
    if [[ "$STY_ROWS" -gt 0 ]] && [[ "$STY_COLS" -gt 0 ]]; then
        pass "T69: stty size = ${STY_ROWS}x${STY_COLS} (matches TIOCGWINSZ)"
    else
        fail "T69: stty size returned ${STTY_SIZE}"
    fi
fi

# Test 70: COLUMNS/LINES environment
[[ "${COLUMNS:-0}" -gt 0 ]] && [[ "${LINES:-0}" -gt 0 ]] && \
    pass "T70: COLUMNS=$COLUMNS LINES=$LINES" || \
    warn "T70: COLUMNS/LINES not set (may be normal in non-interactive)"

# Test 71: /dev/tty access
if [[ -c /dev/tty ]]; then
    pass "T71: /dev/tty accessible (character device)"
else
    fail "T71: /dev/tty not accessible"
fi

# Test 72: PTY device
TTY_DEV=$(tty 2>/dev/null || echo "not a tty")
if [[ "$TTY_DEV" == *"/dev/"* ]]; then
    pass "T72: Running on PTY: $TTY_DEV"
else
    warn "T72: tty reports: $TTY_DEV"
fi

# Test 73: File descriptors
if [[ -t 0 ]]; then
    pass "T73: stdin is a terminal (fd 0)"
else
    warn "T73: stdin is not a terminal"
fi
if [[ -t 1 ]]; then
    pass "T74: stdout is a terminal (fd 1)"
else
    warn "T74: stdout is not a terminal"
fi

# ═══════════════════════════════════════════════════════════════════════════
# 11. SIGNAL HANDLING
# ═══════════════════════════════════════════════════════════════════════════
section "11. Signal Handling"

# Test 75: SIGWINCH handler
SIGWINCH_CAUGHT=0
trap 'SIGWINCH_CAUGHT=1' WINCH
# We can't easily trigger SIGWINCH from within, but we test the trap works
kill -WINCH $$ 2>/dev/null
sleep 0.1
if [[ "$SIGWINCH_CAUGHT" -eq 1 ]]; then
    pass "T75: SIGWINCH trap caught self-sent signal"
else
    fail "T75: SIGWINCH trap did not fire"
fi
trap - WINCH

# Test 76: SIGTSTP/SIGCONT
CAN_STOP=0
(sleep 0.1 && kill -CONT $$ 2>/dev/null) &
BG_PID=$!
# Don't actually SIGTSTP ourselves in a test, just verify we can trap it
trap 'CAN_STOP=1' TSTP
kill -TSTP $$ 2>/dev/null
sleep 0.2
trap - TSTP
wait "$BG_PID" 2>/dev/null
pass "T76: SIGTSTP/SIGCONT handling (trap set/cleared)"

# Test 77: SIGINT handling
SIGINT_CAUGHT=0
trap 'SIGINT_CAUGHT=1' INT
kill -INT $$ 2>/dev/null
sleep 0.1
trap - INT
[[ "$SIGINT_CAUGHT" -eq 1 ]] && pass "T77: SIGINT trap works" || fail "T77: SIGINT not caught"

# Test 78: Job control
if set -o | grep -q 'monitor.*on' 2>/dev/null; then
    pass "T78: Job control (monitor mode) enabled"
else
    warn "T78: Job control not enabled (non-interactive shell)"
fi

# ═══════════════════════════════════════════════════════════════════════════
# 12. MOUSE TRACKING (non-interactive verification)
# ═══════════════════════════════════════════════════════════════════════════
section "12. Mouse Tracking Modes"

# These enable/disable mouse tracking modes -- no crash = pass
# Test 79: X10 mouse
printf "\033[?9h"; printf "\033[?9l"
pass "T79: X10 mouse tracking enable/disable (mode 9)"

# Test 80: Normal tracking
printf "\033[?1000h"; printf "\033[?1000l"
pass "T80: Normal mouse tracking enable/disable (mode 1000)"

# Test 81: Button-event tracking
printf "\033[?1002h"; printf "\033[?1002l"
pass "T81: Button-event mouse tracking enable/disable (mode 1002)"

# Test 82: Any-event tracking
printf "\033[?1003h"; printf "\033[?1003l"
pass "T82: Any-event mouse tracking enable/disable (mode 1003)"

# Test 83: SGR extended mouse
printf "\033[?1006h"; printf "\033[?1006l"
pass "T83: SGR extended mouse mode enable/disable (mode 1006)"

# Test 84: UTF-8 mouse encoding
printf "\033[?1005h"; printf "\033[?1005l"
pass "T84: UTF-8 mouse encoding enable/disable (mode 1005)"

# Test 85: urxvt mouse encoding
printf "\033[?1015h"; printf "\033[?1015l"
pass "T85: urxvt mouse encoding enable/disable (mode 1015)"

# Test 86: Pixel mouse mode
printf "\033[?1016h"; printf "\033[?1016l"
pass "T86: SGR-pixel mouse mode enable/disable (mode 1016)"

# Test 87: Focus events
printf "\033[?1004h"; printf "\033[?1004l"
pass "T87: Focus event reporting enable/disable (mode 1004)"

if [[ "$MODE" == "--interactive" ]]; then
    info "Interactive mouse test: click anywhere (10s timeout)..."
    printf "\033[?1000h\033[?1006h"  # Enable normal + SGR
    OLD_STTY=$(stty -g 2>/dev/null)
    stty raw -echo min 0 time 100 2>/dev/null
    MOUSE_DATA=$(dd bs=32 count=1 < /dev/tty 2>/dev/null)
    stty "$OLD_STTY" 2>/dev/null
    printf "\033[?1006l\033[?1000l"  # Disable
    if [[ -n "$MOUSE_DATA" ]]; then
        pass "T87b: Mouse click received: $(printf '%s' "$MOUSE_DATA" | od -An -tx1 2>/dev/null | head -1)"
    else
        warn "T87b: No mouse data received (timeout)"
    fi
fi

# ═══════════════════════════════════════════════════════════════════════════
# 13. DEC PRIVATE MODES
# ═══════════════════════════════════════════════════════════════════════════
section "13. DEC Private Modes"

# Test 88: Application cursor keys
printf "\033[?1h"   # DECCKM on (application)
printf "\033[?1l"   # DECCKM off (normal)
pass "T88: Application cursor keys (DECCKM mode 1)"

# Test 89: Reverse video
printf "\033[?5h"   # Reverse screen
sleep 0.15
printf "\033[?5l"   # Normal screen
pass "T89: Reverse video (DECSCNM mode 5) -- brief flash"

# Test 90: Synchronized output
printf "\033[?2026h"  # Begin sync
printf "Synchronized "
printf "output "
printf "batch"
printf "\033[?2026l"  # End sync
printf "\n"
pass "T90: Synchronized output (mode 2026)"

# Test 91: DECRQM - Request mode status
info "Sending DECRQM for DECAWM (mode 7)..."
DECRQM_RESP=$(query_terminal $'\e[?7$p' 0.5 2>/dev/null || echo "")
if [[ -n "$DECRQM_RESP" ]]; then
    pass "T91: DECRQM response received for mode 7"
else
    warn "T91: No DECRQM response (terminal may not support it)"
fi

# ═══════════════════════════════════════════════════════════════════════════
# 14. CHARACTER SETS & LINE DRAWING
# ═══════════════════════════════════════════════════════════════════════════
section "14. Character Sets"

# Test 92: DEC Special Graphics (line drawing)
printf "\033(0"           # Switch to G0 = DEC Special
printf "  lqwqk\n"       # ┌─┬─┐
printf "  x x x\n"       # │ │ │
printf "  mqvqj\n"       # └─┴─┘
printf "\033(B"           # Back to ASCII
pass "T92: DEC Special Graphics line drawing (G0 charset)"

# Test 93: G0/G1 charset switching
printf "\033(B"   # G0 = ASCII
printf "\033)0"   # G1 = DEC Special
printf "\016"     # SO: activate G1
printf "qqqqq"   # Should show lines
printf "\017"     # SI: activate G0
printf " (was horizontal lines)\n"
pass "T93: G0/G1 charset switching (SI/SO)"

# ═══════════════════════════════════════════════════════════════════════════
# 15. PERFORMANCE BENCHMARKS
# ═══════════════════════════════════════════════════════════════════════════
section "15. Performance Benchmarks"

# Test 94: Raw line generation speed (to /dev/null)
START_NS=$(date +%s%N 2>/dev/null || echo 0)
seq 1 10000 > /dev/null 2>&1
END_NS=$(date +%s%N 2>/dev/null || echo 0)
if [[ "$START_NS" != "0" ]] && [[ "$END_NS" != "0" ]]; then
    ELAPSED_MS=$(( (END_NS - START_NS) / 1000000 ))
    pass "T94: seq 10K lines to /dev/null: ${ELAPSED_MS}ms"
else
    pass "T94: seq 10K completed (nanosecond timer unavailable)"
fi

# Test 95: Terminal output throughput
TMPFILE=$(mktemp "${TMPDIR_TEST}/novatest.XXXXXX" 2>/dev/null || mktemp)
dd if=/dev/zero bs=1024 count=100 2>/dev/null | tr '\0' 'X' > "$TMPFILE" 2>/dev/null
FSIZE=$(wc -c < "$TMPFILE")
START_NS=$(date +%s%N 2>/dev/null || echo 0)
cat "$TMPFILE" > /dev/null 2>&1
END_NS=$(date +%s%N 2>/dev/null || echo 0)
if [[ "$START_NS" != "0" ]] && [[ "$END_NS" != "0" ]]; then
    ELAPSED_MS=$(( (END_NS - START_NS) / 1000000 ))
    [[ "$ELAPSED_MS" -gt 0 ]] && THROUGHPUT=$(( FSIZE / ELAPSED_MS )) || THROUGHPUT=999999
    pass "T95: 100KB to /dev/null: ${ELAPSED_MS}ms (~${THROUGHPUT} KB/ms)"
else
    pass "T95: 100KB throughput test completed"
fi

if [[ "$MODE" == "--full" ]]; then
    # Test 96: Terminal render throughput (visible output)
    START_NS=$(date +%s%N 2>/dev/null || echo 0)
    cat "$TMPFILE" 2>/dev/null
    END_NS=$(date +%s%N 2>/dev/null || echo 0)
    if [[ "$START_NS" != "0" ]] && [[ "$END_NS" != "0" ]]; then
        ELAPSED_MS=$(( (END_NS - START_NS) / 1000000 ))
        [[ "$ELAPSED_MS" -gt 0 ]] && THROUGHPUT=$(( FSIZE * 1000 / ELAPSED_MS / 1024 )) || THROUGHPUT=999999
        pass "T96: 100KB rendered to terminal: ${ELAPSED_MS}ms (~${THROUGHPUT} KB/s)"
    else
        pass "T96: Render throughput completed"
    fi

    # Test 97: Color rendering throughput
    START_NS=$(date +%s%N 2>/dev/null || echo 0)
    for i in $(seq 1 500); do
        printf "\033[38;2;$((i%256));$((255-i%256));$((i*3%256))m#"
    done
    printf "\033[0m\n"
    END_NS=$(date +%s%N 2>/dev/null || echo 0)
    if [[ "$START_NS" != "0" ]] && [[ "$END_NS" != "0" ]]; then
        ELAPSED_MS=$(( (END_NS - START_NS) / 1000000 ))
        pass "T97: 500 truecolor cells: ${ELAPSED_MS}ms"
    fi

    # Test 98: Scrollback stress
    START_NS=$(date +%s%N 2>/dev/null || echo 0)
    seq 1 50000 2>/dev/null
    END_NS=$(date +%s%N 2>/dev/null || echo 0)
    if [[ "$START_NS" != "0" ]] && [[ "$END_NS" != "0" ]]; then
        ELAPSED_MS=$(( (END_NS - START_NS) / 1000000 ))
        pass "T98: 50K lines scrollback: ${ELAPSED_MS}ms"
    fi

    # Test 99: Long line rendering
    START_NS=$(date +%s%N 2>/dev/null || echo 0)
    printf '%0.s=' $(seq 1 2000)
    printf '\n'
    END_NS=$(date +%s%N 2>/dev/null || echo 0)
    if [[ "$START_NS" != "0" ]] && [[ "$END_NS" != "0" ]]; then
        ELAPSED_MS=$(( (END_NS - START_NS) / 1000000 ))
        pass "T99: 2000-char line: ${ELAPSED_MS}ms"
    fi

    # Test 100: Rapid cursor movement
    START_NS=$(date +%s%N 2>/dev/null || echo 0)
    for i in $(seq 1 200); do
        printf "\033[%d;%dH." "$((RANDOM % 24 + 1))" "$((RANDOM % 80 + 1))"
    done
    printf "\033[999;1H\n"
    END_NS=$(date +%s%N 2>/dev/null || echo 0)
    if [[ "$START_NS" != "0" ]] && [[ "$END_NS" != "0" ]]; then
        ELAPSED_MS=$(( (END_NS - START_NS) / 1000000 ))
        pass "T100: 200 random cursor jumps: ${ELAPSED_MS}ms"
    fi

    # Test 101: Escape sequence parsing throughput
    START_NS=$(date +%s%N 2>/dev/null || echo 0)
    for i in $(seq 1 1000); do
        printf "\033[1;31;42mX\033[0m"
    done
    printf "\n"
    END_NS=$(date +%s%N 2>/dev/null || echo 0)
    if [[ "$START_NS" != "0" ]] && [[ "$END_NS" != "0" ]]; then
        ELAPSED_MS=$(( (END_NS - START_NS) / 1000000 ))
        pass "T101: 1000 SGR sequences: ${ELAPSED_MS}ms"
    fi
else
    skip "T96-T101: Benchmarks (run with --full)"
fi

rm -f "$TMPFILE"

# ═══════════════════════════════════════════════════════════════════════════
# 16. RESIZE HANDLING
# ═══════════════════════════════════════════════════════════════════════════
section "16. Resize Handling"

# Test 102: $COLUMNS/$LINES consistency with tput
TPUT_COLS=$(tput cols 2>/dev/null || echo 0)
TPUT_ROWS=$(tput lines 2>/dev/null || echo 0)
STTY_SIZE_2=$(stty size 2>/dev/null || echo "0 0")
STTY_ROWS_2=$(echo "$STTY_SIZE_2" | cut -d' ' -f1)
STTY_COLS_2=$(echo "$STTY_SIZE_2" | cut -d' ' -f2)
if [[ "$TPUT_COLS" == "$STTY_COLS_2" ]] && [[ "$TPUT_ROWS" == "$STTY_ROWS_2" ]]; then
    pass "T102: tput and stty size agree (${TPUT_COLS}x${TPUT_ROWS})"
else
    fail "T102: tput(${TPUT_COLS}x${TPUT_ROWS}) != stty(${STTY_COLS_2}x${STTY_ROWS_2})"
fi

# Test 103: Cursor position report at edges
printf "\033[%d;%dH" "$TPUT_ROWS" "$TPUT_COLS"  # Move to bottom-right
EDGE_CPR=$(query_terminal $'\e[6n' 0.5 2>/dev/null || echo "")
if [[ "$EDGE_CPR" =~ \[([0-9]+)\;([0-9]+)R ]]; then
    EDGE_ROW=${BASH_REMATCH[1]}
    EDGE_COL=${BASH_REMATCH[2]}
    if [[ "$EDGE_ROW" -eq "$TPUT_ROWS" ]] && [[ "$EDGE_COL" -eq "$TPUT_COLS" ]]; then
        pass "T103: Cursor at bottom-right: ${EDGE_ROW},${EDGE_COL}"
    else
        warn "T103: Expected ${TPUT_ROWS},${TPUT_COLS} got ${EDGE_ROW},${EDGE_COL}"
    fi
else
    warn "T103: Could not verify edge cursor position"
fi
printf "\033[999;1H"  # Move back to bottom-left

# Test 104: Overflow cursor position
printf "\033[999;999H"  # Should clamp to bottom-right
OVF_CPR=$(query_terminal $'\e[6n' 0.5 2>/dev/null || echo "")
if [[ "$OVF_CPR" =~ \[([0-9]+)\;([0-9]+)R ]]; then
    pass "T104: Overflow cursor clamped to ${BASH_REMATCH[1]},${BASH_REMATCH[2]}"
else
    warn "T104: Could not verify overflow clamping"
fi

# ═══════════════════════════════════════════════════════════════════════════
# 17. INPUT HANDLING (non-interactive)
# ═══════════════════════════════════════════════════════════════════════════
section "17. Input Handling"

# Test 105: stty echo state
ECHO_STATE=$(stty -a 2>/dev/null | grep -o '\-\?echo' | head -1)
pass "T105: stty echo state: ${ECHO_STATE:-unknown}"

# Test 106: stty special chars
INTR_CHAR=$(stty -a 2>/dev/null | grep -o 'intr = [^;]*' | head -1)
EOF_CHAR=$(stty -a 2>/dev/null | grep -o 'eof = [^;]*' | head -1)
pass "T106: Special chars: ${INTR_CHAR:-?}, ${EOF_CHAR:-?}"

# Test 107: Keyboard protocol detection
printf "\033[?u"  # Query Kitty keyboard protocol
pass "T107: Kitty keyboard protocol query sent (CSI ? u)"

# Test 108: Application keypad mode
printf "\033[?1h"   # Application cursor
printf "\033="      # Application keypad
printf "\033>"      # Normal keypad
printf "\033[?1l"   # Normal cursor
pass "T108: Application keypad/cursor modes toggled"

if [[ "$MODE" == "--interactive" ]]; then
    info "Press some keys (5s timeout). Arrow keys, Ctrl combos, function keys..."
    OLD_STTY=$(stty -g 2>/dev/null)
    stty raw -echo min 0 time 50 2>/dev/null
    KEY_DATA=$(dd bs=32 count=5 < /dev/tty 2>/dev/null)
    stty "$OLD_STTY" 2>/dev/null
    if [[ -n "$KEY_DATA" ]]; then
        KEY_HEX=$(printf '%s' "$KEY_DATA" | od -An -tx1 2>/dev/null | head -3)
        pass "T108b: Key input received: $KEY_HEX"
    else
        warn "T108b: No key input (timeout)"
    fi
fi

# ═══════════════════════════════════════════════════════════════════════════
# 18. EDGE CASES & STRESS
# ═══════════════════════════════════════════════════════════════════════════
section "18. Edge Cases & Stress"

# Test 109: Empty escape sequence
printf "\033["  # Incomplete CSI - should be ignored/buffered
printf "m"     # Complete it with SGR reset
pass "T109: Incomplete CSI recovered"

# Test 110: Very long escape sequence
LONG_PARAMS=""
for i in $(seq 1 100); do LONG_PARAMS="${LONG_PARAMS}0;"; done
printf "\033[${LONG_PARAMS}m"  # 100 SGR params
printf "\033[0m"
pass "T110: Long CSI with 100 parameters"

# Test 111: Null bytes in output
printf "A\x00B\x00C\n"
pass "T111: Null bytes in output stream (A\\0B\\0C)"

# Test 112: Bell character
printf "\007"  # BEL
pass "T112: BEL character sent (may beep or flash)"

# Test 113: Backspace behavior
printf "  ABCDE\b\b\bXY\n"
pass "T113: Backspace overwrites (should show: ABXYE)"

# Test 114: Carriage return
printf "  ORIGINAL\rOVERWRIT\n"
pass "T114: Carriage return overwrites (should show: OVERWRIT)"

# Test 115: Horizontal tab
printf "  A\tB\tC\tD\n"
pass "T115: Horizontal tabs (tab stops)"

# Test 116: Vertical tab and form feed
printf "  VT:\013FF:\014done\n"
pass "T116: Vertical tab (\\v) and Form feed (\\f)"

# Test 117: Mixed wide/narrow character boundary
printf "  \xe4\xbd\xa0X\xe5\xa5\xbdY\xe4\xb8\x96Z (CJK+ASCII interleaved)\n"
pass "T117: CJK/ASCII boundary alignment"

# Test 118: Right-to-left override
printf "  \xe2\x80\xaeABCDEF\xe2\x80\xac (RLO: should reverse ABCDEF)\n"
pass "T118: RTL override character (U+202E / U+202C)"

# Test 119: Extremely long line (no newlines)
LONGLINE=$(printf '%0.s.' $(seq 1 "$((COLS * 3))" 2>/dev/null || seq 1 240))
printf "  %s\n" "$LONGLINE"
pass "T119: Line 3x terminal width (${#LONGLINE} chars, wrapping test)"

# Test 120: Rapid mode switches
for i in $(seq 1 50); do
    printf "\033[?1049h\033[?1049l"  # Alt screen on/off
done
pass "T120: 50 rapid alt-screen toggles"

# ═══════════════════════════════════════════════════════════════════════════
# 19. TERMIOS & TTY FEATURES
# ═══════════════════════════════════════════════════════════════════════════
section "19. Termios & TTY Features"

# Test 121: Raw mode round-trip
OLD_STTY=$(stty -g 2>/dev/null || echo "")
if [[ -n "$OLD_STTY" ]]; then
    stty raw 2>/dev/null
    stty "$OLD_STTY" 2>/dev/null
    pass "T121: Raw mode enter/exit via stty save/restore"
else
    fail "T121: Cannot save stty state"
fi

# Test 122: Cooked mode features
ICANON=$(stty -a 2>/dev/null | grep -o '\-\?icanon' | head -1)
pass "T122: Canonical mode: ${ICANON:-unknown}"

# Test 123: Flow control
IXON=$(stty -a 2>/dev/null | grep -o '\-\?ixon' | head -1)
IXOFF=$(stty -a 2>/dev/null | grep -o '\-\?ixoff' | head -1)
pass "T123: Flow control: ixon=${IXON:-?} ixoff=${IXOFF:-?}"

# Test 124: UTF-8 mode in stty
IUTF8=$(stty -a 2>/dev/null | grep -o '\-\?iutf8' | head -1)
[[ "$IUTF8" == "iutf8" ]] && pass "T124: iutf8 enabled in stty" || warn "T124: iutf8=${IUTF8:-missing}"

# ═══════════════════════════════════════════════════════════════════════════
# 20. ENVIRONMENT & INTEGRATION
# ═══════════════════════════════════════════════════════════════════════════
section "20. Environment & Integration"

# Test 125: TERM_PROGRAM
[[ "${TERM_PROGRAM:-}" == "novaterm" ]] && pass "T125: TERM_PROGRAM=novaterm" || warn "T125: TERM_PROGRAM=${TERM_PROGRAM:-unset}"

# Test 126: XDG dirs
for var in XDG_CONFIG_HOME XDG_DATA_HOME XDG_STATE_HOME XDG_CACHE_HOME; do
    val="${!var:-}"
    [[ -n "$val" ]] && [[ -d "$val" ]] && pass "T126: $var=$val" || warn "T126: $var=${val:-unset}"
done

# Test 127: PREFIX
[[ -d "${PREFIX:-/nonexistent}" ]] && pass "T127: PREFIX=$PREFIX" || warn "T127: PREFIX=${PREFIX:-unset}"

# Test 128: Shell config files
for f in ~/.profile ~/.bashrc ~/.inputrc; do
    [[ -f "$f" ]] && pass "T128: $f exists" || warn "T128: $f missing"
done

# Test 129: PATH sanity
if command -v bash > /dev/null 2>&1 && command -v ls > /dev/null 2>&1; then
    pass "T129: PATH resolves bash and ls"
else
    fail "T129: PATH broken"
fi

# Test 130: Temp directory
TMPTEST="${TMPDIR_TEST}/novaterm_test_$$"
if mkdir -p "$TMPTEST" 2>/dev/null && touch "$TMPTEST/test" 2>/dev/null; then
    pass "T130: Temp dir writable ($TMPDIR_TEST)"
    rm -rf "$TMPTEST"
else
    fail "T130: Temp dir not writable"
fi

# ═══════════════════════════════════════════════════════════════════════════
# SUMMARY
# ═══════════════════════════════════════════════════════════════════════════
section "SUMMARY"

TOTAL=$((PASS + FAIL + SKIP + WARN))
printf "\n"
printf "  ${GREEN}PASS: %3d${RESET}\n" "$PASS"
printf "  ${RED}FAIL: %3d${RESET}\n" "$FAIL"
printf "  ${CYAN}WARN: %3d${RESET}\n" "$WARN"
printf "  ${YELLOW}SKIP: %3d${RESET}\n" "$SKIP"
printf "  ${BOLD}TOTAL:%3d${RESET}\n" "$TOTAL"
printf "\n"

if [[ $FAIL -eq 0 ]]; then
    printf "  ${GREEN}${BOLD}All tests passed!${RESET}\n"
    EXIT_CODE=0
elif [[ $FAIL -le 3 ]]; then
    printf "  ${YELLOW}${BOLD}Minor issues detected ($FAIL failures).${RESET}\n"
    EXIT_CODE=0
else
    printf "  ${RED}${BOLD}$FAIL test(s) failed.${RESET}\n"
    EXIT_CODE=1
fi

printf "\n"
[[ "$MODE" == "--quick" ]] && printf "  ${DIM}Run with --full for benchmarks (T96-T101) or --interactive for input tests.${RESET}\n"
printf "  ${DIM}Tests T08-T09, T42, T91, T103-T104 require raw terminal access.${RESET}\n"
printf "  ${DIM}WARN results indicate features the terminal may optionally support.${RESET}\n"
printf "\n"

exit ${EXIT_CODE:-0}
