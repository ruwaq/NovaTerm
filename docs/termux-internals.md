# Termux Terminal Engine Internals
**Date:** March 2026 | **Author:** NovaTerm Research

Deep technical analysis of the Termux terminal-emulator and terminal-view code that NovaTerm builds on.

---

## TerminalEmulator.java - VT Parser

### State Machine
Manual state machine (not table-driven like vte/alacritty). State stored in `mEscapeState` as int with 24 possible states (`ESC_*`).

**Processing flow:**
```
PTY bytes -> processByte() [per byte]
  -> Manual UTF-8 decoding (mUtf8ToFollow, mUtf8InputBuffer[4])
  -> processCodePoint()
    -> Control chars: NUL, BEL, BS, HT, LF, CR, SO, SI, CAN, SUB, ESC
    -> switch(mEscapeState):
      ESC_NONE -> emitCodePoint() (normal chars)
      ESC -> doEsc() (ESC + char)
      ESC_CSI -> doCsi() (most complex - cursor, erase, SGR, etc.)
      ESC_CSI_QUESTIONMARK -> doCsiQuestionMark() (DEC private modes)
      ESC_OSC -> doOsc() (title, colors, clipboard)
      ESC_P -> doDeviceControl() (DECRQSS, termcap)
      ESC_APC -> doApc() (silently consumed)
```

### Arguments parsing
- `parseArg()`: digit-by-digit, `;` separator, `:` for sub-params (Kitty underlines)
- Max 32 params (`MAX_ESCAPE_PARAMETERS`), values capped at 9999

### Colors (TextStyle.java)
All style info packed in a `long` (64 bits):
- Bits 0-10: effect flags (bold, italic, underline, blink, inverse, etc.)
- Bits 16-39: background color
- Bits 40-63: foreground color
- Indexed: 16 ANSI + 216 color cube + 24 grayscale
- True color: `0xff_RR_GG_BB` with `TRUECOLOR_*` flag bit
- Underline color (SGR 58): parsed but **NOT rendered**

### Grid: TerminalBuffer + TerminalRow
**TerminalBuffer**: Circular buffer of `TerminalRow[]`
- `mScreenFirstRow`: where visible screen starts in ring
- `mActiveTranscriptRows`: scrollback lines count
- Default scrollback: 2000 lines (configurable 100-50000)

**TerminalRow**: Optimized per-row storage
- `mText[]`: char array (Java chars, surrogate pairs for supplementary)
- `mStyle[]`: long array, one per column
- `mHasNonOneWidthOrSurrogateChars`: fast-path flag
- `mSpaceUsed`: chars used in mText
- SPARE_CAPACITY_FACTOR: 1.5x to reduce reallocations
- `findStartOfColumn()`: O(n) per row (scans mText counting widths)
- Max 15 combining chars per column

### Supported Sequences
**CSI:** CUU/D/F/B, CNL, CPL, CHA, CUP, CHT, VPA, ED, EL, ECH, SU, SD, ICH, IL, DL, DCH, SGR, DSR, DECSTBM, REP, CBT, Window manipulation
**DEC Private:** DECCKM, DECCOLM, DECSCNM, DECOM, DECAWM, cursor visible, alt screen, DECNKM, DECLRMM, mouse tracking (1000/1002/1006), bracketed paste (2004)
**OSC:** 0/1/2 (title), 4 (indexed color), 10/11/12 (fg/bg/cursor), 52 (clipboard), 104/110/111/112 (reset)
**DCS:** DECRQSS, Termcap/Terminfo queries
**Rectangular area ops:** DECCRA, DECSERA, DECFRA, DECERA, DECCARA, DECRARA

### NOT Supported (opportunities for NovaTerm)
- **OSC 8** (hyperlinks)
- **OSC 133** (shell integration) - EASY to add, HIGH value
- **OSC 7** (current working directory)
- **Kitty Graphics Protocol** - HIGH effort
- **Sixel** - MEDIUM effort
- **Synchronized output** (Mode 2026) - MEDIUM effort
- **DECSET 1003** (any-event mouse tracking)
- **DECSET 1004** (focus events) - partially defined but not used
- **Undercurl/colored underline** - parsed but not rendered
- **Mode 2027** (grapheme clusters)

### How to Extend (cleanest paths)
- New OSC: add `case N:` in `doOscSetTextParameters()` (line 2031)
- New CSI: add in `doCsi()` (line 1529) or `doCsiQuestionMark()` (line 1102)
- New DCS: add in `doDeviceControl()` (line 918)
- New APC: modify `doApc()` (line 1043) to stop silently consuming

### Known Issues
- Thread safety: TODO about `mCursorCol`/`mCursorRow` sync (line 2488)
- `findStartOfColumn()` is O(n) per row
- WcWidth tables are Unicode 15 (needs update to 17.0)
- Reverse wrap-around: TODO (not implemented)
- Full screen re-render on every frame (no dirty rect tracking)
- Bold uses `setFakeBoldText()` instead of real bold font
- Italic uses `setTextSkewX(-0.35)` instead of real italic font

---

## TerminalSession.java - PTY Management

### PTY Creation (JNI)
`JNI.createSubprocess()`:
1. Opens `/dev/ptmx` with `O_RDWR | O_CLOEXEC`
2. `grantpt()` + `unlockpt()` + `ptsname_r()`
3. Configures `IUTF8`, disables flow control
4. Sets window size via `TIOCSWINSZ`
5. `fork()`: child opens pts slave, `setsid()`, `dup2()` to stdin/stdout/stderr
6. Closes all fds > 2 via `/proc/self/fd`
7. `clearenv()` + `putenv()` per variable
8. `execvp()` the command

### I/O Threads
- **TermSessionInputReader**: reads PTY master fd -> `mProcessToTerminalIOQueue` (64KB ByteQueue)
- **TermSessionOutputWriter**: reads `mTerminalToProcessIOQueue` (4KB) -> writes PTY master fd
- **TermSessionWaiter**: `waitpid()` blocking, sends MSG_PROCESS_EXITED

### MainThreadHandler
- MSG_NEW_INPUT: reads queue, calls `mEmulator.append()`, notifies screen update
- MSG_PROCESS_EXITED: cleanup, shows "[Process completed]"

---

## TerminalRenderer.java - Canvas Renderer

### Rendering Loop
1. Iterates column by column
2. Groups consecutive chars with same style into "runs"
3. Per-codepoint font width mismatch detection
4. `Canvas.drawTextRun()` for each run (invokes HarfBuzz internally)
5. If width mismatch: `canvas.scale()` horizontally to force correct width
6. Pre-caches ASCII character widths (`asciiMeasures[127]`)

### Cursor Styles
- Block: full rect + inverted text
- Underline: 1/4 cell height rect
- Bar: 1/4 cell width rect
- Blinking via `TerminalCursorBlinkerRunnable`

### Touch/Gesture (GestureAndScaleRecognizer)
- Tap: mouse event or focus
- Long press: text selection
- Scroll: mouse tracking or transcript scroll
- Scale: pinch-to-zoom
- Physical mouse: right=context, middle=paste, left=mouse events

---

## Security Notes
- OSC 52 clipboard: writes allowed without restriction (should gate on focus)
- OSC 20/21 (report title): disabled for security
- DECRQSS: implemented, verify responses don't include control chars
- MAX_OSC_STRING_LENGTH = 8192 (limits OSC payload)
- Bracketed paste (2004): supported but no bypass protection in paste content
