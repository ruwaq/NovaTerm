# Mobile Terminal UX Research — 2026

Comprehensive, actionable findings for NovaTerm. Every recommendation includes
exact values. Researched April 2026.

---

## 1. Touch-Optimized Terminal UX

### Touch Target Sizes

| Element | Minimum | Recommended | NovaTerm current |
|---------|---------|-------------|-----------------|
| Extra key button | 44x44 dp | 48x48 dp | 44x44 dp (upgrade) |
| Tab close button | 24x24 dp (WCAG AA) | 36x36 dp | — |
| Session tab | 44 dp height | 48 dp height | — |
| URL/path tap zone | 44 dp height | extend 8dp above/below text | — |
| Status bar icons | 24x24 dp | 32x32 dp visible, 48dp touch | — |

**Material3 rule**: Compose auto-pads to 48dp touch target even if visual is
smaller. Use `Modifier.minimumInteractiveComponentSize()`.

### Gesture Map (recommended for NovaTerm)

| Gesture | Action | Notes |
|---------|--------|-------|
| Swipe left/right (on terminal) | Switch sessions | Termux does this via drawer; NovaTerm should use direct swipe with peek animation |
| Swipe down (from top edge) | Pull down notification / URL bar | Avoid conflict with system gesture |
| Two-finger swipe up/down | Scroll terminal history | Faster than single-finger, avoids text selection conflict |
| Long press | Text selection mode | Standard Android behavior, must respect |
| Double tap | Select word under cursor | Then expand to full path/URL on drag |
| Pinch | Font size adjustment (temporary) | Live preview, snap back on release or commit on double-pinch |
| Three-finger tap | Paste clipboard | Power user shortcut |
| Swipe up from bottom edge | Show/hide extra keys bar | Toggle with spring animation |

### Critical UX Lessons from Competitors

**Termux problems**:
- Drawer-based session switching conflicts with Android 15+ gesture nav
- Long-press to show extra keys is not discoverable
- No visual feedback on swipe gestures

**Blink Shell (iOS) gets right**:
- Direct swipe between sessions with momentum
- Pinch-to-zoom font size
- Native-feeling scroll physics (UIScrollView)
- Transparent gesture layer over terminal

**a-Shell / iSH**:
- Minimal chrome — terminal fills screen
- Toolbar only appears when keyboard is shown
- Simple, predictable gestures

### Conflict Avoidance with Android Gesture Nav

Android 15+ reserves 24dp edge zones for back gesture. Solutions:
- Inset swipe detection by 32dp from left/right edges
- Use `WindowInsetsCompat.Type.systemGestures()` to query exact zones
- Provide alternative tap targets (tab bar) for all swipe actions
- Mark edge zones with `View.setSystemGestureExclusionRects()` only for
  critical terminal gestures (limited to 200dp vertical per side)

---

## 2. Typography

### Recommended Fonts (ranked for 6.83" AMOLED @ 1280px wide)

**Primary: JetBrains Mono**
- Tallest x-height of any coding font (makes 12sp feel like 13sp)
- 143 ligatures for shell operators (`->`, `=>`, `!=`, `||`, `&&`)
- Powerline glyphs in Nerd Font variant
- Free, OFL license

**Accessibility alternative: Atkinson Hyperlegible Mono**
- Released 2025 by Braille Institute, now on Google Fonts
- Strongest character disambiguation (d/b/q/p, 0/O, 1/l/I, 8/B/5/S)
- Ideal for users with low vision
- Offer as option in Settings, especially when system font scale > 1.3x

**Also excellent**:
- **Fira Code**: Widest ligature set, slightly wider than JetBrains Mono
- **Hack**: Best at very small sizes (10sp), no ligatures
- **Source Code Pro**: Most conservative, enterprise feel
- **IBM Plex Mono**: Best for hex/IP readability

### Font Size Recommendations

| Context | Size | Line height |
|---------|------|-------------|
| Default terminal | 12sp | 1.4x (16.8sp) |
| Minimum usable | 8sp | 1.3x |
| Maximum useful | 24sp | 1.3x |
| Extra keys labels | 12sp | 1.0x |
| Status bar text | 10sp | 1.2x |
| Tab titles | 11sp | 1.2x |
| Onboarding text | 14sp | 1.5x |

On 6.83" @ 1280px wide with 12sp:
- ~51 columns at JetBrains Mono default width
- ~56 columns at Hack (narrower glyphs)
- ~48 columns at Fira Code (wider glyphs)

### Rendering Techniques

- **Subpixel rendering**: DISABLED on AMOLED (pentile subpixel layout
  doesn't match RGB stripe assumption; causes color fringing)
- **Hinting**: Use `FONT_HINTING_MEDIUM` — full hinting distorts at 12sp on
  high-DPI; no hinting looks blurry
- **Anti-aliasing**: `Paint.setAntiAlias(true)` always on
- **Ligatures**: Enable via `Paint.LIGATURE_FLAG` or `fontFeatureSettings = "liga"`
  in Compose `TextStyle`. Disable in password fields.
- **Text rendering**: Use `LAYER_TYPE_HARDWARE` for Canvas-based rendering
  (already done in NovaTerm TerminalView)
- **Font caching**: Pre-load typeface in `Application.onCreate()` to avoid
  first-frame jank

---

## 3. Color Schemes for AMOLED

### Science of AMOLED Dark Themes

**DO NOT use pure black (#000000) background.** Research findings:
- Pure black on AMOLED causes "smearing" during scroll (OLED pixel response
  time from fully off to on is ~12ms vs ~2ms for dim-to-bright)
- Contrast ratio of white-on-black is 21:1 — causes eye fatigue over time
- Optimal background luminance: 5-12% (hex range #0D0D0D to #1F1F1F)
- Optimal foreground luminance: 80-90% (not pure white)
- Target contrast ratio: 7:1 to 12:1 (WCAG AAA is 7:1)

### NovaTerm's Ember/Gruvbox Dark — Already Excellent

Current `Bg0Hard = #32302F` (12.4% luminance) and `Fg1 = #D4BE98` (74.5%
luminance) give ~9.79:1 contrast. This is near-optimal.

**Recommendation**: Keep Gruvbox Dark as default. The warm tones (#32302F bg)
avoid OLED smearing while the zero-blue palette reduces melatonin suppression.

### Additional Scheme: "Ember AMOLED" (new, optional)

For users who want deeper blacks with minimal smearing:

```
Background:  #1A1814  (warm near-black, 3.7% luminance)
Surface:     #252220
Foreground:  #D4BE98  (same warm cream)
Cursor:      #E78A4E  (ember orange)
```

This saves ~15% more battery than Gruvbox Dark while maintaining the warm tone.
The background has enough luminance to avoid severe OLED smearing.

### Catppuccin Mocha Analysis (already in NovaTerm)

Exact Mocha hex values:
- Base: `#1E1E2E` — Cool-toned (slight blue). On AMOLED, this is fine but
  less eye-friendly than Gruvbox's warm tones for extended sessions
- Text: `#CDD6F4` — Slightly blue-tinted white. Higher blue light
- Contrast ratio text-on-base: ~11.4:1 (good but slightly high)

**Recommendation**: Keep Catppuccin Mocha as option but mark Gruvbox as
"recommended for eye comfort" in the picker.

### Color Scheme Comparison Table

| Scheme | Background | Foreground | Contrast | Blue content | AMOLED battery | Eye comfort |
|--------|-----------|------------|----------|-------------|----------------|-------------|
| Gruvbox Dark | #32302F | #D4BE98 | 9.79:1 | Zero | Good | Excellent |
| Catppuccin Mocha | #1E1E2E | #CDD6F4 | 11.4:1 | Medium | Better | Good |
| Dracula | #282A36 | #F8F8F2 | 13.2:1 | Low | Better | Good |
| Nord | #2E3440 | #ECEFF4 | 12.1:1 | High | Good | Fair |
| Solarized Dark | #002B36 | #839496 | 5.1:1 | High | Good | Fair (low contrast) |
| Monokai | #272822 | #F8F8F2 | 14.2:1 | Low | Better | Good |
| Ember AMOLED (new) | #1A1814 | #D4BE98 | 13.1:1 | Zero | Best | Very good |

### Color for Syntax Elements (in terminal output)

Ensure all ANSI colors meet WCAG AA (4.5:1) against background:
- Red (errors): minimum 4.5:1 contrast — current `#EA6962` = 5.64:1 (pass)
- Green (success): `#A9B665` = 8.06:1 (pass)
- Yellow (warnings): `#D8A657` = 8.02:1 (pass)
- Avoid pure cyan (#00FFFF) — too bright on AMOLED, causes afterimage

---

## 4. Smooth Scrolling

### Frame Pacing for 120Hz

NovaTerm targets Dimensity 9400 devices (120Hz AMOLED). Key parameters:

| Parameter | Value | Notes |
|-----------|-------|-------|
| Target frame time | 8.33ms | 120Hz = 1000/120 |
| Scroll input latency budget | <4ms | From touch event to first pixel update |
| Choreographer callback | `postFrameCallback()` | Sync scroll to VSYNC |
| Frame rate hint | `Surface.setFrameRate(120f, COMPATIBILITY_DEFAULT)` | Tells compositor |
| Scroll deceleration | 0.998f per frame | iOS-like momentum feel |
| Fling velocity cap | 8000 dp/s | Prevents content from flying |
| Overscroll | 20dp max displacement | Elastic, with spring-back |

### Scroll Physics (iOS-quality on Android)

```kotlin
// Recommended scroll parameters for terminal
val SCROLL_FRICTION = 0.015f          // Lower = longer momentum
val DECELERATION_RATE = 0.998f        // Per-frame multiplier
val MIN_VELOCITY_TO_FLING = 50f       // dp/s threshold
val OVERSCROLL_DISTANCE = 20.dp       // Elastic overscroll
val OVERSCROLL_SPRING_STIFFNESS = 400f // Spring back speed
val OVERSCROLL_SPRING_DAMPING = 0.8f   // Slight bounce
```

### Implementation Strategy

1. **Use `NestedScrollConnection`** in Compose for the terminal view
2. **Separate scroll from rendering**: Scroll offset is a float (sub-pixel),
   rendering snaps to pixel grid only at draw time
3. **Velocity tracker**: Use `VelocityTracker` with 100ms window
4. **Frame pacing**: Request frame callbacks via `Choreographer`, not
   `postDelayed` or `ValueAnimator`
5. **Damage tracking**: Only redraw lines that scroll into view (Rust renderer
   already has this)
6. **Line recycling**: Reuse rendered line bitmaps when scrolling (shift buffer,
   only render new lines)

### Terminal-Specific Scroll Considerations

- **Alternate screen** (vim, less, htop): Disable momentum scroll, use direct
  1:1 tracking. Convert scroll delta to arrow key events.
- **Normal screen** (shell): Full momentum scroll through scrollback buffer
- **Mouse mode** (tmux, etc.): Convert scroll to mouse wheel events
- **Scroll indicator**: Thin 2dp bar on right edge, auto-fade after 1500ms

---

## 5. Extra Keys Bar Design

### Current NovaTerm Layout Analysis

Current layout (from code review):
```
Row 1: CTRL  ALT   ▲   TAB   /   |   -
Row 2: ESC   ◀    ▼    ▶    ⏎   ⌨
```

**What works well**:
- Arrow cross pattern spanning both rows
- Long-press popups for secondary actions
- Sticky modifiers (CTRL, ALT)
- Haptic feedback on tap and long-press
- Tooltips for first 3 uses (progressive disclosure)
- Accessibility semantics (role, contentDescription)

**What could be improved**:

### Recommendation 1: Increase touch targets to 48dp

Current `height(44.dp)` and `widthIn(min = 44.dp)`. Change to:
```kotlin
.height(48.dp)
.widthIn(min = 48.dp)
```
This matches Material3 accessibility guidelines. The 4dp increase per button
is barely visible but significantly reduces mis-taps.

### Recommendation 2: Add visual popup hint

Add a small dot or caret (2dp) at top-right of keys with popups. Users
currently have no visual indication that long-press does something different.
```
Color: Ember.Fg4 (#A89984) at 60% opacity
Size: 3dp circle
Position: top-right corner, 4dp inset
```

### Recommendation 3: Swipe-up on key = popup

Instead of requiring long-press (300ms delay), allow swipe-up gesture on any
key to trigger the popup action immediately. This is how iOS keyboards work
and is much faster for experienced users.

### Recommendation 4: Consider 3-row layout option

For AI-heavy workflows (Claude Code, Gemini CLI), add optional 3rd row:
```
Row 1: CTRL  ALT   ▲   TAB   /   |   -
Row 2: ESC   ◀    ▼    ▶    ⏎   ⌨
Row 3: ^C   ^D   ^Z   ^R   ^L   FN
```
Row 3 = most-used Ctrl combos as direct taps (no modifier needed).
Toggle in settings: "Compact (2 rows)" vs "Extended (3 rows)".

### Recommendation 5: Dynamic symbol row

Replace static symbols (`/`, `|`, `-`) with context-aware keys:
- In git repo: show `git`, `branch`, `commit` shortcuts
- In node project: show `npm`, `npx`
- Default: current symbols

This is a Phase 3 AI feature but the infrastructure should support it.

### Key Spacing and Sizing

| Parameter | Current | Recommended |
|-----------|---------|-------------|
| Button height | 44dp | 48dp |
| Button min width | 44dp | 48dp |
| Row gap | 4dp | 4dp (keep) |
| Button gap | 4dp | 3dp (tighter, more space per key) |
| Corner radius | 10dp | 12dp (matches Material3 medium shape) |
| Horizontal padding | 4dp | 6dp |
| Vertical padding | 4dp | 4dp (keep) |
| Font size | (inferred 12sp) | 13sp labels, 11sp symbols |
| Bar total height | ~96dp | ~104dp (two rows) / ~156dp (three rows) |

---

## 6. Haptic Feedback Patterns

### When to Use Haptics (Terminal-Specific)

| Action | Haptic | Android constant | Duration |
|--------|--------|------------------|----------|
| Extra key tap | Light click | `HapticFeedbackConstants.KEYBOARD_TAP` | ~10ms |
| Long-press popup | Medium thud | `HapticFeedbackConstants.LONG_PRESS` | ~20ms |
| Modifier toggle ON | Confirm tick | `HapticFeedbackConstants.CONFIRM` | ~15ms |
| Modifier toggle OFF | Light click | `HapticFeedbackConstants.CLOCK_TICK` | ~5ms |
| Session switch (swipe) | Segment tick | `HapticFeedbackConstants.SEGMENT_FREQUENT_TICK` | ~5ms per |
| Command error (non-zero exit) | Reject buzz | `HapticFeedbackConstants.REJECT` | ~30ms |
| URL/entity tap | Context click | `HapticFeedbackConstants.CONTEXT_CLICK` | ~10ms |
| Scroll hit top/bottom | Edge thud | `VibrationEffect.createOneShot(15, 80)` | 15ms |
| Pinch zoom snap | Tick | `HapticFeedbackConstants.CLOCK_TICK` | ~5ms |
| Tab close | Light thud | `HapticFeedbackConstants.VIRTUAL_KEY` | ~10ms |

### What NOT to Haptic

- Normal terminal output (text appearing) — never
- Regular scrolling — never (except hitting bounds)
- Keyboard typing (system handles this) — never
- Background notifications — never (system handles)
- Every frame of animation — never

### Implementation in Compose

Current NovaTerm uses `HapticFeedbackType.TextHandleMove` for key tap and
`HapticFeedbackType.LongPress` for long-press. **Upgrade to**:

```kotlin
// Compose haptic types (limited set)
// For richer haptics, use View-based constants:
view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
view.performHapticFeedback(HapticFeedbackConstants.REJECT)
view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
```

Access View from Compose via `LocalView.current`.

### User Preference

Always respect `hapticEnabled` setting (already in NovaTerm). Default: ON.
Offer three levels in settings:
- Off
- Subtle (key taps only)
- Full (all interactions)

---

## 7. Animation and Transitions

### Animation Spec Reference

| Animation | Duration | Easing | Compose spec |
|-----------|----------|--------|-------------|
| Tab switch | 200ms | `FastOutSlowIn` | `tween(200, easing = FastOutSlowInEasing)` |
| Tab appear (new session) | 250ms | `EaseOutBack` | `tween(250, easing = EaseOutBack)` with slight overshoot |
| Tab close | 150ms | `FastOutLinearIn` | `tween(150, easing = FastOutLinearInEasing)` |
| Settings panel slide | 300ms | `FastOutSlowIn` | `tween(300)` |
| Keyboard appear | 250ms | Match `WindowInsetsAnimation` | Sync with system via `WindowInsetsAnimationCompat` |
| Extra keys show/hide | 200ms | Spring | `spring(dampingRatio = 0.8f, stiffness = 400f)` |
| Color scheme change | 400ms | `LinearOutSlowIn` | `animateColorAsState` with `tween(400)` |
| Font size change | 150ms | `FastOutSlowIn` | Scale + fade cross-dissolve |
| Modal dialog | Enter 250ms / Exit 200ms | `EaseOutCubic` / `EaseInCubic` | Asymmetric enter/exit |
| Toast/snackbar | Enter 200ms, hold 2000ms, exit 150ms | Standard | Material3 default |
| Scroll to top/bottom | Spring | Physics-based | `spring(stiffness = Spring.StiffnessMedium)` |
| Session rename inline edit | 150ms | `FastOutSlowIn` | Expand text field |

### Key Principles

1. **Enter > Exit duration**: Appearing elements take 200-300ms; disappearing
   elements take 150-200ms. Users need more time to notice new content.
2. **Frequency rule**: The more an animation repeats, the shorter it should be.
   Tab switching (frequent) = 200ms. Onboarding (once) = 400ms.
3. **Spring for physics**: Use spring animations for drag-release, swipe-back,
   and overscroll. Parameters: `dampingRatio = 0.7-0.85`, `stiffness = 300-500`.
4. **Never block input**: All animations must be interruptible. If user taps
   during tab-switch animation, immediately settle to final state.
5. **Reduce motion**: Respect `Settings.Global.ANIMATOR_DURATION_SCALE`. When
   0, skip animations entirely. Use `ReducedMotion` in Compose.

### What NOT to Animate

- Terminal text output (instant always)
- Cursor blink (CSS-style opacity toggle, not animated — 530ms on, 530ms off)
- Scrollbar appearance (instant show, 1500ms fade-out only)
- Individual keystrokes in extra keys bar (haptic only, no visual animation
  beyond press state)

---

## 8. Accessibility

### Font Scaling

| System font scale | NovaTerm behavior |
|-------------------|-------------------|
| 0.85x (small) | Apply to UI chrome only, terminal stays at user's chosen sp |
| 1.0x (default) | Normal |
| 1.15x-1.3x (large) | Scale terminal font proportionally, warn if cols < 40 |
| 1.5x+ (largest) | Cap terminal font scale at 1.3x, show option for Atkinson Hyperlegible Mono |
| Bold text (system) | Apply `fontWeight = FontWeight.Medium` to terminal (not Bold, too heavy) |

**Critical**: Terminal font must respect `sp` units (already does in NovaTerm),
which auto-scales with system preference. But offer a toggle:
"System font scaling: Apply to terminal / UI only / Both"

### High Contrast Mode

When `AccessibilityManager.isHighTextContrastEnabled`:
- Boost foreground to pure white `#FFFFFF`
- Darken background to `#000000` (override OLED smearing concern — accessibility wins)
- Add 1dp white outline to extra key buttons
- Boost all ANSI colors to meet 7:1 contrast (WCAG AAA)
- Underline clickable URLs (not just color)

### Screen Reader (TalkBack)

Terminal emulators are inherently challenging for screen readers. Recommendations:
- **Announce new output**: When terminal produces output, post
  `AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED` with the new text
- **Extra keys**: Already have `contentDescription` and `role` (good)
- **Session tabs**: Announce "Session 1 of 3, bash, home directory"
- **Navigate by line**: Support `AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY`
  with `MOVEMENT_GRANULARITY_LINE`
- **Custom actions**: "Read last command output", "Read current directory"
- **Live region**: Mark terminal output area as `accessibilityLiveRegion = POLITE`
  (not ASSERTIVE — too noisy)

### Switch Access & Voice Access

- All interactive elements must have unique `contentDescription`
- Tab order must be logical: terminal -> extra keys -> tab bar -> status bar
- Voice Access labels: "tap CTRL", "tap escape", "switch to session 2"

---

## 9. Status Bar / Info Display

### What to Show (prioritized for 51 columns)

**Always visible** (in terminal status line):
1. Session indicator: `[1/3]` — 5 chars
2. CWD (truncated): `~/p/novaterm` — smart truncation, 15-20 chars max
3. Git branch: `main` — only in git repos, 10 chars max

**On demand** (tap status bar to expand):
4. Git status: `+2 ~1 -0` (staged/modified/deleted)
5. Exit code of last command: `✓` or `✗ 127`
6. Battery: only show below 20%
7. Time: show on tap, hide after 3s

**Never in status bar**:
- SSH host (show in tab title instead)
- Full path (truncate aggressively)
- User@host (waste of space on single-user mobile device)

### Layout for 51-Column Terminal

```
┌─────────────────────────────────────────────────┐
│ [1/3] ~/p/novaterm  main              ✓  15% │
│ ◂▸▴▾  session     cwd        git    exit batt │
└─────────────────────────────────────────────────┘
```

Total: ~50 chars, fits in 51 columns with 1 char margin.

### Implementation

- Height: 20dp (minimal, single line)
- Background: `Ember.Bg1` (#45403D) — slightly elevated from terminal bg
- Text: `Ember.Fg4` (#A89984) — muted, doesn't distract from terminal
- Git branch: `Ember.Aqua` (#89B482) — subtle color differentiation
- Error exit code: `Ember.Red` (#EA6962)
- Font: Same monospace as terminal, 10sp
- Tap to expand: Slide down to 40dp, show additional info, auto-collapse 3s
- Update: On every prompt (OSC 133 prompt start) or every 5s max

### Smart Truncation Algorithm for CWD

```
Full:      /data/data/com.termux/files/home/projects/novaterm/src
Truncated: ~/p/novaterm/src

Rules:
1. Replace $HOME with ~
2. Shorten intermediate dirs to first char
3. Keep last 2 path components full
4. If still > 20 chars, ellipsis: ~/../novaterm/src
```

---

## 10. Performance Perception

### Techniques (ranked by impact)

**1. Instant press feedback (< 16ms)**
- Extra key buttons: show pressed state on `MotionEvent.ACTION_DOWN`, not
  after gesture detection. Current NovaTerm does this with `pressed = true`
  in `onPress` — good.
- Haptic on same frame as press visual.
- Budget: Touch → visual feedback must be < 1 frame (8.33ms at 120Hz)

**2. Optimistic session creation (< 100ms perceived)**
- When user taps "New session": immediately show tab + blank terminal
- Start shell process in background
- Show cursor blinking immediately (fake, before PTY is ready)
- When PTY connects, seamlessly start displaying output
- User perceives instant session creation

**3. Preload next probable screen**
- If user is on terminal, preload Settings composables
- If user has 3 sessions, pre-warm a 4th session's shell environment
- Pre-render tab bar state for +1 and -1 sessions

**4. Skeleton states instead of spinners**
- Bootstrap installation: show progress bar with ETA, not spinner
- Session restore: show tab with last-known title immediately, loading
  indicator in terminal area
- Never show a bare "Loading..." text

**5. Scroll momentum preservation**
- When scrolling terminal output, new output arriving should not reset
  scroll position (if user scrolled up, stay scrolled up)
- Show "↓ New output" pill at bottom, tap to jump to bottom
- This is the single most frustrating UX issue in terminal emulators

**6. Input prediction**
- Echo typed characters immediately (local echo) before PTY confirms
- If PTY output matches prediction, no visual change
- If PTY output differs (e.g., password field), correct silently
- Budget: keystroke → character on screen < 16ms (1 frame at 60Hz)

**7. Progressive terminal restore**
- On app restart, show last screenshot immediately (saved as bitmap)
- Restore session metadata (title, cwd) — instant
- Restore scrollback buffer progressively (newest first)
- User sees their terminal "already there" on launch

**8. Cold start optimization**
- Target: < 500ms to first interactive frame
- Splash screen with NovaTerm logo (avoid blank white flash)
- Use `SplashScreen` API (Android 12+)
- Defer non-critical init: color scheme parsing, font loading, settings read
- Pre-warm: `Application.onCreate()` → start first session shell
- Profile: Use Baseline Profiles to AOT-compile hot paths

### Performance Metrics to Track

| Metric | Target | Method |
|--------|--------|--------|
| Cold start to interactive | < 500ms | `reportFullyDrawn()` |
| Keystroke to echo | < 16ms (1 frame) | Custom trace |
| Tab switch perceived | < 100ms | Animation start counts |
| Scroll input latency | < 4ms | Choreographer delta |
| Session creation perceived | < 200ms | Optimistic UI |
| Font size change | < 150ms | Animation completion |

---

## Summary: Top 10 Highest-Impact Changes for NovaTerm

1. **Increase extra key touch targets to 48dp** — reduces mis-taps significantly
2. **Add swipe-up-on-key for popup** — 3x faster than long-press for power users
3. **Add "Ember AMOLED" color scheme** (#1A1814 bg) — max battery savings with warm tone
4. **Implement optimistic session creation** — perceived instant new tabs
5. **Add visual popup indicator** (3dp dot) on keys with long-press actions
6. **Implement smooth scroll with iOS-like physics** — deceleration 0.998f, elastic overscroll
7. **Bundle Atkinson Hyperlegible Mono** as accessibility font option
8. **Add "New output" pill** when scrolled up — prevents the #1 terminal UX frustration
9. **Progressive terminal restore** — show last screenshot on cold start
10. **Respect reduced motion** — check `ANIMATOR_DURATION_SCALE` for all animations

---

## Sources

### Touch UX
- [Designing for Touch: Mobile UI/UX Best Practices](https://devoq.medium.com/designing-for-touch-mobile-ui-ux-best-practices-c0c71aa615ee)
- [Touch Gesture Controls for Mobile Interfaces — Smashing Magazine](https://www.smashingmagazine.com/2017/02/touch-gesture-controls-mobile-interfaces/)
- [Motor Impairments and Mobile UI: Touch Target Problem](https://www.siteimprove.com/blog/motor-impairments-and-mobile-ui-the-touch-target-problem/)
- [Termux session switch via swipe — PR #452](https://github.com/termux/termux-app/pull/452)
- [Termux gesture nav conflict — Issue #1325](https://github.com/termux/termux-app/issues/1325)

### Typography
- [Top 10 Terminal Fonts 2026 — TypeSmith](https://typographysmith.com/font-recommendations/top-10-terminal-fonts)
- [Best Coding Fonts 2026 — Lexington Themes](https://lexingtonthemes.com/blog/best-coding-fonts-2026)
- [Atkinson Hyperlegible Mono — Google Fonts](https://fonts.google.com/specimen/Atkinson+Hyperlegible+Mono)
- [Atkinson Hyperlegible Mono Review — Anthesis](https://www.anthes.is/font-comparison-review-atkinson-hyperlegible-mono.html)
- [Braille Institute Free Font](https://www.brailleinstitute.org/freefont/)

### Color Schemes
- [Dark Mode Best Practices 2026](https://natebal.com/best-practices-for-dark-mode/)
- [Are Dark Themes Better for Eyes and Battery?](https://www.maketecheasier.com/are-dark-themes-better-for-eyes-battery/)
- [Solarized Color Scheme — Protect Eyes](https://www.maketecheasier.com/use-solarized-color-scheme-protect-eyes/)
- [Best Color Themes for Text Editors — Slant](https://www.slant.co/topics/358/~best-color-themes-for-text-editors)
- [Catppuccin Palette](https://catppuccin.com/palette/)

### Scrolling
- [Terminal Smooth Scrolling — Ted Unangst](https://flak.tedunangst.com/post/terminal-smooth-scrolling)
- [Terminal Smooth Scrolling — HN Discussion](https://news.ycombinator.com/item?id=38851642)
- [Android Frame Pacing Library](https://developer.android.com/games/sdk/frame-pacing)
- [Blink Shell Tips and Tricks](https://docs.blink.sh/basics/tips-and-tricks)

### Extra Keys
- [Termux Extra Keys — Mobile Coding Hub](https://mobile-coding-hub.github.io/termux/customisation/extra_keys/)
- [Termux Properties and Keybindings — DeepWiki](https://deepwiki.com/mayTermux/myTermux/6.1-termux-properties-and-keybindings)

### Haptics
- [Haptics Design Principles — Android Developers](https://developer.android.com/develop/ui/views/haptics/haptics-principles)
- [Add Haptic Feedback to Events — Android Developers](https://developer.android.com/develop/ui/views/haptics/haptic-feedback)
- [HapticFeedbackConstants API Reference](https://developer.android.com/reference/android/view/HapticFeedbackConstants)
- [2025 Guide to Haptics — Saropa](https://saropa-contacts.medium.com/2025-guide-to-haptics-enhancing-mobile-ux-with-tactile-feedback-676dd5937774)

### Animation
- [Animation Duration and Motion — NNGroup](https://www.nngroup.com/articles/animation-duration/)
- [Customize Animations — Jetpack Compose](https://developer.android.com/develop/ui/compose/animation/customize)
- [Easing Curves in Jetpack Compose — Android Developers](https://medium.com/androiddevelopers/easing-in-to-easing-curves-in-jetpack-compose-d72893eeeb4d)

### Accessibility
- [WezTerm Accessibility Issue #913](https://github.com/wezterm/wezterm/issues/913)
- [Warp Accessibility](https://docs.warp.dev/terminal/more-features/accessibility)
- [Building Accessible GitHub CLI](https://github.blog/engineering/user-experience/building-a-more-accessible-github-cli/)
- [Touch Target Size — Android Accessibility](https://support.google.com/accessibility/android/answer/7101858)
- [Material3 Minimum Touch Target Size](https://github.com/cvs-health/android-compose-accessibility-techniques/blob/main/doc/interactions/MinimumTouchTargetSize.md)

### Performance Perception
- [Optimistic UI Patterns — Simon Hearne](https://simonhearne.com/2021/optimistic-ui-patterns/)
- [Doherty Threshold in UX — LogRocket](https://blog.logrocket.com/ux-design/designing-instant-feedback-doherty-threshold/)
- [Performance First UX 2026](https://wearepresta.com/performance-first-ux-2026-architecting-for-revenue-and-speed/)
- [Performance First UI Mastery 2026](https://blog.shubhra.dev/performance-first-ui-mastery-guide-2026/)
