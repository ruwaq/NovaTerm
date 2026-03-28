# The Perfect Mobile Terminal: NovaTerm Design Vision
**Date:** March 2026 | **Target device:** Flagship Android phones (6"+, AMOLED, 120Hz+)

---

## Core Philosophy

A mobile terminal is NOT a desktop terminal squeezed into a phone. It is a fundamentally different interaction paradigm where fingers replace mice, network connections are unreliable, screen real estate is precious, and the user is probably standing on a train. Every design decision must start from this reality.

**Three pillars:**

1. **Touch-first, not touch-adapted** -- every interaction designed for fingers from day one
2. **Intelligent defaults, infinite customization** -- works perfectly out of the box, customizable for power users
3. **Resilient by design** -- survives network drops, process kills, phone restarts, and battery optimization

---

## 1. Touch-First User Experience

### 1.1 Text Selection

The #1 pain point in every mobile terminal. Current implementations (Termux, ConnectBot) use crude long-press-then-drag that fights with the terminal's own mouse mode.

**Design:**

| Gesture | Action |
|---|---|
| Long press | Enter selection mode at word boundary (smart word detection: paths, URLs, IPs, hashes) |
| Drag handles | Expand/contract selection with magnifying glass loupe (like iOS text editing) |
| Double-tap | Select word under finger |
| Triple-tap | Select entire line |
| Double-tap + drag | Select word-by-word (not character-by-character) |
| Two-finger tap | Select entire command output block |
| Swipe up on selection | Copy to clipboard (with haptic confirmation) |

**Smart selection targets** (regex-based detection, highlighted with subtle underline):
- File paths (`/home/user/.config/nvim/init.lua`)
- URLs (`https://github.com/...`)
- IP addresses and ports (`192.168.1.1:8080`)
- Git hashes (`a1b2c3d`)
- Error codes and stack traces
- Environment variables (`$HOME`, `${PATH}`)

Tapping a smart-detected element shows a contextual popup: "Copy", "Open in browser", "Open in file manager", "Insert in command line".

### 1.2 Gesture Navigation

| Gesture | Action |
|---|---|
| Swipe left/right (from edge) | Switch between sessions (with peek preview) |
| Swipe down from top | Pull down session drawer (session list + quick actions) |
| Pinch | Zoom font size (temporary, snap back with double-tap) |
| Two-finger swipe up/down | Scroll through output (momentum scrolling, configurable speed) |
| One-finger swipe on extra keys bar | Cycle through extra key pages |
| Long press + drag on empty area | Cursor positioning (like iOS space bar cursor) |
| Three-finger swipe left | Undo last command input (shake to undo alternative) |

**Edge gestures** must not conflict with Android's system gesture navigation (back, home, recents). Use insets properly: the swipe-to-switch-sessions zone starts 20dp inward from the screen edge.

### 1.3 Extra Keys Bar

The most critical touch-specific UI element. This is what separates a usable mobile terminal from an unusable one.

**Architecture: 3-tier system**

```
Tier 1: Always-visible bar (1 row, most used keys)
Tier 2: Swipe-up expansion (2 additional rows, less common keys)
Tier 3: Long-press popup menus (related key variants)
```

**Tier 1 (always visible, 8-10 keys):**
```
[ESC] [TAB] [CTRL] [ALT] [UP] [DOWN] [LEFT] [RIGHT] [-] [|]
```

- Each key supports swipe-up for a related key popup:
  - ESC swipe-up: F1-F12 row
  - CTRL swipe-up: Ctrl+C, Ctrl+D, Ctrl+Z, Ctrl+L, Ctrl+R, Ctrl+A
  - ALT swipe-up: Alt+., Alt+B, Alt+F, Alt+D (word movement/editing)
  - UP swipe-up: Page Up, Home
  - DOWN swipe-up: Page Down, End
  - `-` swipe-up: `_`, `=`, `+`
  - `|` swipe-up: `\`, `/`, `~`, `` ` ``

**Tier 2 (expandable, appears on swipe-up of the entire bar):**
```
Row 2: [!] [@] [#] [$] [%] [^] [&] [*] [(] [)]
Row 3: [{] [}] [[] []] [<] [>] ["] ['] [;] [:]
```

**Modifier key behavior (from Moshi, refined):**
- Single tap: activate for next keypress only (then auto-deactivate)
- Double tap: lock modifier (stays active, visual indicator changes to filled/solid)
- Visual state: outline = inactive, half-fill = one-shot, solid = locked

**Customization:**
- Drag-to-reorder keys in settings
- Add/remove keys from a catalog
- Import/export key configurations (JSON)
- Per-session key bar profiles (different layouts for SSH vs local)
- Preset profiles: "Default", "Vim", "Emacs", "tmux", "DevOps"

**Haptic feedback on extra keys:**
- Light tick on key press (HapticFeedbackConstants.KEYBOARD_TAP)
- Stronger pulse on modifier lock (HapticFeedbackConstants.LONG_PRESS)
- No haptic on key release (avoid fatigue)

### 1.4 Keyboard Integration

| Event | Behavior |
|---|---|
| App launch with session | Show keyboard automatically |
| Tap on terminal area | Show keyboard if hidden |
| Swipe down on terminal | Hide keyboard (maximize terminal view) |
| External keyboard connected | Auto-hide soft keyboard, expand terminal, show hardware keyboard shortcut hints |
| Split keyboard mode (Samsung/foldable) | Reflow terminal to visible area between keyboard halves |
| Floating keyboard (Android 14+) | Terminal area fills space not occupied by floating keyboard |

**Volume key bindings (when terminal focused):**
- Vol Up/Down: scroll output (configurable)
- Long press Vol Up: Page Up
- Long press Vol Down: Page Down
- Optional: disable if user prefers system volume control

### 1.5 Font Size and Readability

Target device: 6.83" AMOLED, 2772x1280 (Xiaomi 15T Pro equivalent).

| Setting | Default | Range |
|---|---|---|
| Font size | 14sp | 8-24sp |
| Font family | JetBrains Mono NF | Selectable from bundled + system fonts |
| Line height | 1.2x | 1.0-2.0x |
| Letter spacing | 0 | -0.5 to 2.0 |
| Terminal columns | Auto-fit to width | Fixed or auto |
| Horizontal margin | 4dp | 0-32dp |
| Vertical margin | 4dp | 0-32dp |

**Bundled fonts (all Nerd Font patched):**
- JetBrains Mono NF (default)
- Fira Code NF
- Cascadia Code NF
- Hack NF
- Source Code Pro NF
- Iosevka NF (narrower, more columns on small screens)
- Monaspace Neon NF

**Dynamic font sizing:**
- Pinch to zoom temporarily (shows font size indicator, snaps back on double-tap)
- Persistent size change via settings or Ctrl+= / Ctrl+-
- "Fit width" mode: auto-calculate font size to fit exactly 80 columns
- Landscape auto-adjustment: optionally reduce font size in landscape to show more

### 1.6 Color Themes

**Built-in themes (15+):**

| Category | Themes |
|---|---|
| Dark | Gruvbox Dark (default), Catppuccin Mocha, Tokyo Night, Dracula, Nord, One Dark, Solarized Dark |
| Light | Gruvbox Light, Catppuccin Latte, Tokyo Night Day, Solarized Light, One Light |
| AMOLED | Gruvbox AMOLED (#000000 background), Pure AMOLED, Catppuccin AMOLED |
| Accessible | High Contrast Dark, High Contrast Light, Deuteranopia Safe, Protanopia Safe |

**AMOLED black mode:**
- Pure #000000 background to turn off OLED pixels
- Saves 30-40% battery on OLED screens
- Slightly lighter (#0a0a0a) option for those who find pure black too harsh
- Extra keys bar matches AMOLED background

**Material You / Dynamic Colors (Android 12+):**
- Option to derive terminal chrome (toolbar, drawer, extra keys bar background) from wallpaper colors
- Terminal text colors stay from the selected theme (don't derive from wallpaper -- readability matters more than aesthetic matching)
- Accent color from Material You for UI elements: selected tab, active session indicator, buttons

**Theme engine:**
- Import from .itermcolors, .Xresources, base16-yaml, Alacritty TOML
- Live preview before applying
- Schedule themes (auto dark mode at sunset, or follow system)

### 1.7 Haptic Feedback Strategy

Less is more. Haptics should confirm actions, not annoy.

| Event | Haptic Type | Configurable |
|---|---|---|
| Extra key press | Light tick | Yes, on/off |
| Modifier key lock | Medium pulse | Yes |
| Text selection start | Light tick | Yes |
| Copy to clipboard | Success pattern (tick-pause-tick) | Yes |
| Session switch (swipe) | Light tick at boundary | Yes |
| Error (command not found) | Double short vibration | Yes, off by default |
| Long-running command complete | Strong pulse | Yes |
| Edge of scrollback reached | Boundary bump | Yes |

Global toggle: Settings > Haptics > Off / Subtle / Standard / Strong

---

## 2. Session Management

### 2.1 Navigation Pattern: Bottom Sheet + Tabs

Neither a hamburger drawer nor top tabs alone work well for mobile terminals.

**Hybrid approach:**

```
+--------------------------------------------------+
| [Session name]         [+] [Search] [Settings]   |  <- Mini top bar
|                                                   |
|                                                   |
|        T E R M I N A L   O U T P U T             |
|                                                   |
|                                                   |
|                                                   |
+--------------------------------------------------+
| [ESC] [TAB] [CTRL] [ALT] [^] [v] [<] [>] [-] [|]|  <- Extra keys
+--------------------------------------------------+
| Session 1 | Session 2 | Session 3 | [+]          |  <- Session tabs
+--------------------------------------------------+
|              [ KEYBOARD ]                         |
+--------------------------------------------------+
```

- **Session tabs** sit between extra keys bar and keyboard (closest to thumbs)
- Horizontally scrollable when >4 sessions
- Long press on tab: rename, change color/icon, duplicate, close
- Drag tabs to reorder
- Active tab has colored underline (accent color)
- Swipe down on tab bar: expand into full session sheet with grid view

**Full session sheet (expanded):**

```
+--------------------------------------------------+
|  Sessions                            [Edit] [+]  |
+--------------------------------------------------+
|  +--------+  +--------+  +--------+              |
|  | ~      |  | SSH:   |  | Python |              |
|  | $ _    |  | server |  | >>> _  |              |
|  | local  |  | prod   |  | venv   |              |
|  +--------+  +--------+  +--------+              |
|                                                   |
|  +--------+  +--------+                           |
|  | Docker |  |  [+]   |                           |
|  | logs   |  |  New   |                           |
|  | compose|  | session|                           |
|  +--------+  +--------+                           |
+--------------------------------------------------+
```

Each card shows:
- Session name (editable)
- Color tag (left border)
- Last 3 lines of terminal output (live mini-preview)
- Connection status icon (local, SSH connected, SSH disconnected, Mosh)
- Running process indicator (spinner if command is executing)

### 2.2 Session Persistence

**Levels of persistence:**

| Level | What survives | How |
|---|---|---|
| App background | Everything | Foreground service (Android 14+ `specialUse`) |
| App killed by system | Session state + scrollback | Serialize TerminalEmulator state to disk on every output change (debounced 2s) |
| Phone restart | Session configuration | Restore sessions on boot via Termux:Boot-style mechanism |
| Crash | Last known state | Write-ahead log of terminal state, recover on next launch |

**Implementation details:**
- Foreground service with persistent notification ("NovaTerm: 3 sessions active")
- `WAKE_LOCK` only when explicitly requested by user (battery conscious)
- Request battery optimization exemption during onboarding (explain why)
- Serialize terminal grid (rows, columns, cursor position, scrollback buffer) to protobuf
- Scrollback limit: configurable 1K-100K lines (default 10K, with memory warning above 50K)
- Use `START_STICKY` to restart service if killed

### 2.3 Session Naming and Organization

- Auto-generated names based on running process: "bash", "ssh:prod-server", "python3", "nvim: main.py"
- User-assignable names, colors, and icons
- Session groups/folders for organizing (e.g., "Work servers", "Local dev", "Monitoring")
- Pin favorite sessions to the top
- Session templates: "SSH to prod", "Start dev server", "Docker logs" (one-tap launch with predefined commands)

### 2.4 Split View

**Phone (portrait):** Not supported -- too narrow, would be unreadable. Instead, fast-switch between sessions.

**Phone (landscape):** Optional 50/50 horizontal split (two sessions side by side). Useful for "code on left, output on right" or "local + SSH".

**Tablet / Foldable / Desktop mode:**
- Drag a session tab to the left or right edge to enter split view
- Support 2-4 panes in grid layout
- Each pane is fully independent (own extra keys bar, own scrollback)
- Desktop mode (Android 16): each session can be its own freeform window

---

## 3. Productivity Features

### 3.1 Command Blocks (inspired by Warp)

The most transformative UX innovation in recent terminal history. Each command and its output are grouped into a discrete, interactive block.

**Block structure:**
```
+--------------------------------------------------+
| $ npm install                         0.8s  [0]  |
+--------------------------------------------------+
| added 847 packages in 0.8s                       |
| 120 packages are looking for funding             |
|   run `npm fund` for details                     |
+--------------------------------------------------+
```

**Block metadata (right side of command line):**
- Execution time (0.8s)
- Exit code ([0] green, non-zero red)
- Timestamp (on hover/long-press)

**Block interactions:**
- Tap block header: collapse/expand output
- Long press block: "Copy command", "Copy output", "Re-run", "Share", "Explain (AI)"
- Swipe left on block: delete from scrollback
- Tap exit code: show signal details (SIGTERM, SIGKILL, etc.)
- Blocks with non-zero exit code: red left border, optional auto-expand AI explanation

**Compatibility note:** Blocks require shell integration (injecting PS1/PROMPT_COMMAND hooks). Offer this as opt-in during setup. Fall back to traditional continuous scrollback if the user declines or if shell integration fails (e.g., inside nested SSH sessions).

### 3.2 Autocomplete and Suggestions

**Three tiers of completion:**

1. **Shell-native completions** -- pass through to bash/zsh/fish completions as-is
2. **History-based suggestions** -- fish-style autosuggestions from command history (gray ghost text, tap right arrow or swipe right to accept)
3. **AI suggestions (Phase 3)** -- natural language to command, triggered by `#` prefix or "Ask AI" button

**Touch-optimized completion UI:**
- Completions appear as a horizontal scrollable chip bar above the keyboard
- Most likely completion is pre-selected (tap to insert)
- File path completions show icons (folder, file type)
- Man page summaries for flag completions (e.g., `-v  verbose output`)

### 3.3 Command History

- `Ctrl+R` opens full-screen fuzzy search (fzf-style)
- Search box with real-time filtering
- Results grouped by session and day
- Pin frequently used commands
- Sync history across sessions (shared history file)
- Export/import history

### 3.4 Snippet Manager

Built-in snippet system for saving and organizing reusable commands.

**Features:**
- Save any command block as a snippet with name + description
- Snippets support parameters: `ssh ${user}@${host} -p ${port:22}` (with defaults)
- Organize in folders/tags
- Quick-insert via command palette (Ctrl+Shift+P or swipe down from top bar)
- Share snippets (export as JSON, QR code for phone-to-phone transfer)
- Pre-installed starter pack: common git commands, docker commands, system info commands

### 3.5 URL and Path Detection

- URLs: underlined and tappable (open in browser)
- File paths: tappable (option to "Open in file manager", "View contents", "Copy path")
- IP:port: tappable (option to open in browser, SSH to, ping)
- Email addresses: tappable (open mail app)
- Configurable: colors, underline style, which detectors are enabled

### 3.6 Clipboard Integration

- Standard Android clipboard for copy/paste
- Clipboard history (last 20 items, accessible via long-press paste)
- "Paste safely" mode: show preview of paste content, strip control characters, warn on multi-line paste
- Bracket-paste mode support (for vim, etc.)
- Share intent: receive text from other apps directly into terminal input

### 3.7 Command Palette

Accessible via swipe down from top bar or Ctrl+Shift+P:
- Fuzzy search across: snippets, sessions, settings, recent commands, files
- Quick actions: "New session", "SSH to...", "Toggle theme", "Font size..."
- Extensible via user-defined commands

---

## 4. Connectivity

### 4.1 SSH Client

**Built-in SSH with a proper management UI:**

- Host manager: save hosts with name, address, port, user, key, proxy/jump host
- Key manager: generate Ed25519/RSA keys, import existing keys, export public key
- Biometric unlock for private keys (Android BiometricPrompt API)
- Agent forwarding support
- ProxyJump / bastion host chains
- Port forwarding UI (local, remote, dynamic SOCKS)
- Known hosts management with fingerprint verification UI
- Connection profiles: group related hosts ("Production", "Staging", "Home lab")

### 4.2 Mosh Support

Mosh is THE killer feature for mobile terminals. Network switches (WiFi to cellular), subway tunnels, phone sleep -- Mosh handles it all.

- Bundle mosh-client in the app (or use Termux packages)
- Visual indicator: connection quality meter (green/yellow/red)
- Predictive local echo with visual distinction (gray text that turns white when confirmed)
- Auto-reconnect with exponential backoff
- Fallback to SSH if Mosh server not available

### 4.3 SFTP/SCP File Transfer

- Visual file browser for remote hosts (after SSH connection)
- Drag and drop between local and remote (on tablets/desktop mode)
- Transfer progress with notification (continue in background)
- Resume interrupted transfers
- Batch operations (multi-select, upload folder)

### 4.4 Cloud Sync (optional, opt-in)

- Sync: themes, snippets, SSH host configurations, key bar layouts, preferences
- Do NOT sync: private keys, command history (sensitive), session content
- Backend options: user's own cloud (Google Drive, WebDAV) or NovaTerm account
- End-to-end encryption for synced data
- Conflict resolution UI for merge conflicts

---

## 5. Android Integration

### 5.1 Share Intent

- Register as a share target for text/plain and application/octet-stream
- Receiving text: paste into active session's command line (with confirmation)
- Receiving files: copy to a staging directory, show path in terminal
- Share terminal output: select block > Share > sends formatted text

### 5.2 App Shortcuts (launcher long-press)

- "New local session"
- "SSH to [favorite host]" (user-configurable, up to 4 shortcuts)
- "Run [saved snippet]"
- Dynamic shortcuts based on recent sessions

### 5.3 Widgets

**Quick Command Widget (4x1):**
- Row of 4 configurable buttons, each runs a predefined command in a new/existing session
- Examples: "Git pull", "Server status", "Docker ps", "Disk usage"
- Shows last output line below each button

**Session Status Widget (4x2):**
- Shows active sessions with mini-preview
- Tap to jump to session
- Shows running process, last output line, connection status

### 5.4 Notification Actions

Persistent notification while sessions are active:
```
NovaTerm - 3 sessions active
[New Session] [Toggle Wake Lock] [Stop All]
```

Expandable notification shows session list with individual "Switch to" actions.

### 5.5 Quick Settings Tile

- Toggle terminal foreground service on/off
- Long press: open session picker
- Badge shows number of active sessions

### 5.6 Bubble Mode (Android 17+)

Using the new Android 17 Bubbles API (general-purpose windowing, not chat bubbles):
- Minimize any session to a floating bubble
- Tap bubble to expand into a floating terminal window
- Resize floating window freely
- Multiple bubbles for multiple sessions
- Great for monitoring logs while using other apps

**Fallback for Android 14-16:** Use overlay window permission (TYPE_APPLICATION_OVERLAY) like Termux:Float, but with modern UI.

### 5.7 Picture-in-Picture Mode

- Shrink terminal to PiP window while using other apps
- Shows live output (read-only in PiP, tap to expand)
- Useful for: watching build output, monitoring logs, waiting for long commands
- Custom actions in PiP: "Ctrl+C" button, "Expand" button

### 5.8 Desktop Mode (Android 16+)

When connected to external display:
- Each session opens as a freeform window on the external display
- Phone becomes a touchpad + extra keys input device
- Full multi-window support with window management (snap left/right, maximize)
- Adapt to larger display: larger font, more columns, optional sidebar with session list
- Use Jetpack WindowManager 1.5.0 window size classes (Large: 1200-1600dp, Extra-large: 1600dp+)

---

## 6. Accessibility

### 6.1 TalkBack / Screen Reader

Terminal emulators are notoriously bad with screen readers. NovaTerm should be best-in-class.

- Announce new output lines as they appear (configurable verbosity: all, errors only, command prompts only)
- Command blocks are navigable as units (swipe between blocks, not between individual lines)
- Extra keys bar: each key properly labeled with contentDescription
- Session tabs: announce session name, running process, and connection status
- Custom TalkBack actions: "Read last command output", "Read current directory"
- Focus management: after command execution, focus moves to output start

### 6.2 Font Scaling

- Respect Android system font scale setting
- Additional in-app scale multiplier (for users who need terminal text larger than other apps)
- Minimum touch target: 48dp for all interactive elements (Google accessibility guideline)
- Extra keys bar key size: minimum 44dp, grows with system font scale

### 6.3 Color Blind Friendly

**Built-in accessible themes:**

| Theme | Description |
|---|---|
| Deuteranopia Safe | Avoids red-green contrast, uses blue-orange for error/success |
| Protanopia Safe | Avoids red-green contrast, similar to deuteranopia but shifted |
| High Contrast Dark | WCAG AAA (7:1 ratio), pure white on near-black |
| High Contrast Light | WCAG AAA (7:1 ratio), near-black on pure white |

**Additional measures:**
- Error/success indicators use icons AND color (not color alone)
- Exit code blocks: red border + X icon for errors, green border + checkmark for success
- Connection status: icons (connected plug, disconnected plug, warning triangle) + color
- Configurable: override any ANSI color individually

### 6.4 Reduced Motion

- Respect Android "Remove animations" setting
- Disable: page transitions, swipe previews, session switch animations
- Replace with: instant state changes, no parallax effects
- Keep essential feedback: cursor blink (configurable rate or off)

### 6.5 Switch Access and External Input

- All features accessible via keyboard shortcuts (no touch-only interactions)
- D-pad navigation support for accessibility switches
- Bluetooth keyboard full support with customizable key bindings

---

## 7. What Users Want That Nobody Provides

Based on analysis of GitHub issues, Reddit discussions, and community forums across Termux, r/commandline, r/androiddev, and Hacker News:

### 7.1 Pain Points Nobody Has Solved

| Problem | Current state | NovaTerm solution |
|---|---|---|
| Soft keyboard is terrible for terminals | Everyone relies on extra keys bar hacks | Purpose-built 3-tier extra keys system with swipe menus and modifier locking |
| Text selection is broken | Long-press is clunky, fights with terminal mouse mode | Smart selection with word/line/block targets, magnifying loupe, contextual actions |
| Android kills background sessions | Users lose work, SSH disconnects | Aggressive foreground service + state serialization + wake lock UX + battery exemption onboarding |
| No GPU acceleration | All Android terminals are Canvas-based, slow with large output | Phase 2: Vulkan renderer (first on Android, following Ghostty/Alacritty model) |
| Setup/bootstrap fails frequently | Termux bootstrap download is unreliable, no onboarding | Bundled bootstrap with fallback mirrors, guided first-run wizard, progress feedback |
| Outdated UI, no Material 3 | Termux maintainers explicitly rejected modernization | Native Compose UI with Material 3, dynamic colors, modern navigation |
| No AI assistance | Desktop has Warp, mobile has nothing | Phase 3: on-device LLM via LiteRT + NPU, natural language to command |
| Poor tablet/foldable support | No terminal adapts to form factor changes | Responsive layout with WindowSizeClass, split view, desktop mode |
| No floating terminal | Termux:Float is barely maintained | Bubble mode (Android 17+) + overlay fallback |
| Difficult theme customization | Edit config files manually | In-app theme browser with live preview, import from popular formats |

### 7.2 Features Users Ask For Most (by frequency)

1. **Material 3 / modern UI** -- the single most requested feature across all Termux discussions
2. **Better extra keys customization** -- every user has different needs
3. **Tab-based session management** -- the drawer UX confuses new users
4. **GPU rendering performance** -- power users running heavy workloads
5. **AI command help** -- growing demand since Warp popularized it on desktop
6. **Floating terminal mode** -- multitask without leaving other apps
7. **Better onboarding** -- new users give up within 5 minutes
8. **Reliable Play Store version** -- Termux's fork situation is confusing
9. **Voice input** -- natural for mobile, nobody implements it for terminals
10. **Cross-device settings sync** -- switching phones shouldn't mean reconfiguring everything

### 7.3 Lessons From iOS Terminals

| App | Key lesson for NovaTerm |
|---|---|
| Blink Shell | Mosh support is a killer differentiator on mobile. VS Code integration draws developers. Premium pricing works if quality justifies it. Subscription model causes backlash -- prefer one-time purchase. |
| Moshi | AI agent monitoring is a new category. Voice-to-terminal resonates. Long-press modifier menus (Ctrl > tmux commands, Claude commands) are brilliant UX. Webhook notifications for task completion. |
| a-Shell | Great onboarding and UX for non-experts matters. Multiple windows support is expected. |
| iSH | Users will tolerate performance penalties for compatibility. Community is everything. |

---

## 8. Innovation: Features Nobody Has Yet

### 8.1 Command Blocks for Mobile (adapted from Warp)

Warp proved that treating commands as discrete objects (not a continuous text stream) is transformative. On mobile, this is even more impactful because scrolling through unstructured output on a small screen is painful.

- Each command + output is a collapsible card
- Tap to collapse/expand (save screen space)
- Color-coded exit status (green border = success, red = error)
- "Share this block" sends formatted command + output
- "Explain this error" sends to AI assistant (Phase 3)
- "Re-run this command" one-tap
- Filter output within a block (search within a single command's output)

### 8.2 Smart Notifications

- Configure alerts for patterns: "Notify me when build finishes" (watch for "BUILD SUCCESSFUL" or exit code 0)
- Long-running command detection: if a command runs >30s, offer to notify when done
- Pattern-based alerts: regex match on output triggers Android notification
- Integration with Tasker/Automate for advanced automation

### 8.3 Voice-to-Terminal (Phase 3)

- On-device Whisper model (no cloud, no latency, privacy-first)
- Hold hardware button or tap mic icon to dictate
- AI interprets natural language: "list all docker containers" -> `docker ps -a`
- Confirmation step before executing (show generated command, tap to run)
- Voice feedback option: TTS reads command output aloud (useful with screen off / AirPods)

### 8.4 Terminal Workspace Snapshots

- Save entire workspace state: which sessions are open, their positions, running commands
- "Morning routine" snapshot: opens SSH to prod, local dev server, docker logs
- Restore workspace with one tap
- Share workspace configurations (export/import)

### 8.5 Connection Health Dashboard

- Visual indicator for each SSH/Mosh session: latency, packet loss, bandwidth
- Alert when connection is degrading
- Auto-suggest switching to Mosh when SSH latency spikes on mobile
- Network type indicator (WiFi 6, 5G, LTE) with quality estimation

### 8.6 Integrated Monitoring View

- Split a session into "command" (top) + "monitor" (bottom)
- Monitor pane auto-runs a command at intervals (like `watch`)
- Useful for: `htop`, `kubectl get pods`, `docker stats`, `tail -f`
- Monitor pane is read-only, doesn't accept input

---

## 9. Onboarding and First-Run Experience

The single biggest failure of every Android terminal app.

### 9.1 First Launch Flow

```
Screen 1: Welcome
  "NovaTerm - A modern terminal for Android"
  [Get Started]

Screen 2: Bootstrap
  "Setting up your Linux environment..."
  [Progress bar with detailed status]
  - Downloading packages (with mirror selection and retry)
  - Installing base system
  - Configuring shell
  [Bundled fallback if network fails]

Screen 3: Choose your shell
  [bash] [zsh] [fish]
  Brief description of each, recommendation for beginners

Screen 4: Appearance
  Live terminal preview with theme selector
  Font size slider
  "Try typing something!" interactive area

Screen 5: Extra keys
  Interactive tutorial: "These are your special keys"
  Show swipe-up gestures
  Let user try Ctrl+C, Tab, arrows

Screen 6: Battery optimization
  "To keep your sessions alive, we need..."
  [Request battery exemption] with clear explanation
  [Skip for now]

Screen 7: Ready!
  "You're all set. Here are some things to try:"
  - "Install packages: pkg install git python nodejs"
  - "Connect to a server: ssh user@host"
  - "Customize: swipe down from top for settings"
  [Open Terminal]
```

### 9.2 Interactive Help System

- `?` command or "Help" button in command palette: contextual help
- "Tip of the day" (dismissable, shows one feature per session)
- Built-in cheat sheet: common commands, keyboard shortcuts, gestures
- Long-press any UI element for tooltip explanation

---

## 10. Technical Architecture Summary

### Rendering Pipeline

```
Phase 1 (now):   TerminalView (Canvas) -> AndroidView in Compose
Phase 2 (future): Vulkan SurfaceView -> custom glyph atlas renderer
                   - Multi-threaded: parse thread, layout thread, render thread
                   - Glyph atlas caching (pre-render all ASCII + common Unicode)
                   - Ligature support (for programming fonts)
                   - 120fps capable (match phone refresh rate)
                   - Sub-pixel anti-aliasing
```

### State Management

```
Compose UI <-> ViewModel <-> TerminalEmulator (core/terminal-emulator)
                   |
                   +-> SessionRepository (Room DB for persistence)
                   +-> PreferencesDataStore (settings)
                   +-> SnippetRepository (Room DB)
                   +-> SSHHostRepository (Room DB + encrypted credentials)
```

### Key Dependencies

| Component | Technology |
|---|---|
| UI Framework | Jetpack Compose + Material 3 |
| Terminal Engine | Termux terminal-emulator (Apache 2.0) |
| Terminal Rendering | Phase 1: TerminalView (Canvas), Phase 2: Vulkan |
| SSH | Apache SSHD or Jsch-fork (TBD) |
| Mosh | Native mosh-client binary |
| AI (Phase 3) | LiteRT (TFLite) + Google AI Edge SDK |
| Local Storage | Room DB + DataStore |
| Serialization | Protocol Buffers (terminal state) |
| Background | Foreground Service (specialUse) |
| Navigation | Compose Navigation |

---

## 11. Competitive Positioning

```
                    HIGH FEATURES
                         |
             Termius     |     NovaTerm (target)
          (remote only)  |     (local + remote, open source)
                         |
  LOW POLISH ------------|-------------- HIGH POLISH
                         |
             Termux      |     (empty - this is the gap)
          (most features,|
           dated UI)     |
                         |
                    LOW FEATURES
```

NovaTerm occupies the empty quadrant: **high polish + high features + open source**. No competitor is here.

---

## 12. Business Model Considerations

| Model | Pros | Cons | Recommendation |
|---|---|---|---|
| Fully free/open source | Maximum adoption, community contributions | No revenue, sustainability risk | Base model |
| One-time purchase | Users prefer it, no subscription fatigue | Lower revenue ceiling | Good for "Pro" features |
| Freemium | Free core + paid advanced features | Must be careful what to gate | Best balance |
| Subscription | Recurring revenue | Users hate it (Blink Shell backlash) | Avoid |

**Recommended: Freemium with one-time Pro unlock**
- Free: full terminal, 5 sessions, 3 themes, basic extra keys
- Pro ($9.99 one-time): unlimited sessions, all themes, custom key bars, snippets, cloud sync, AI features, widgets
- All open source regardless (paid features are an honor system / convenience)

---

## Sources

### Mobile Terminal UX
- [Termius: New Touch Terminal on iOS](https://termius.com/blog/new-touch-terminal-on-ios)
- [Termius: UX improvements on Android and iOS](https://blog.termius.com/ux-improvements-on-android-and-ios-ba4cc368b87d)
- [Moshi: Terminal Keyboard Guide](https://getmoshi.app/articles/moshi-keyboard-guide)
- [Moshi: SSH Terminal for AI Agents](https://getmoshi.app)
- [Smashing Magazine: Touch Gesture Controls](https://www.smashingmagazine.com/2017/02/touch-gesture-controls-mobile-interfaces/)

### Desktop Terminal Innovation
- [Warp: Block Basics](https://docs.warp.dev/terminal/blocks/block-basics)
- [Warp: Autosuggestions](https://docs.warp.dev/terminal/command-completions/autosuggestions)
- [Warp: All Features](https://www.warp.dev/all-features)
- [Ghostty Terminal](https://ghostty.org/)
- [Best Terminal Emulators 2026 Comparison](https://www.devtoolreviews.com/reviews/best-terminal-emulators-2026)
- [Scopir: Terminal Emulators 2026](https://scopir.com/posts/best-terminal-emulators-developers-2026/)

### iOS Terminal Competitors
- [Blink Shell](https://blink.sh/)
- [Blink Shell Alternatives (Moshi)](https://getmoshi.app/articles/blink-shell-alternatives)
- [Best Terminals/SSH for iPad and iPhone 2026](https://geekflare.com/dev/best-terminals-ssh-apps/)

### Android Platform
- [Android: Desktop Windowing Features](https://source.android.com/docs/core/display/desktop-windowing)
- [Android Developers Blog: Connected Displays](https://android-developers.googleblog.com/2026/03/android-devices-extend-seamlessly-to-connected-displays.html)
- [Android: Bubbles API](https://developer.android.com/develop/ui/views/notifications/bubbles)
- [Android: Picture-in-Picture](https://developer.android.com/develop/ui/views/picture-in-picture)
- [Android: Quick Settings Tiles](https://developer.android.com/develop/ui/views/quicksettings-tiles)
- [Android 17 Beta: Bubbles + iPiP](https://android.gadgethacks.com/news/android-17-beta-3-desktop-multitasking-bubbles-and-ipip-arrive/)
- [Jetpack WindowManager 1.5.0](https://developer.android.com/develop/ui/views/layout/adaptive-layout)

### Haptics and Accessibility
- [Android: Haptic Feedback](https://developer.android.com/develop/ui/views/haptics/haptic-feedback)
- [Android: Haptics Design Principles](https://developer.android.com/develop/ui/views/haptics/haptics-principles)
- [2025 Guide to Haptics](https://saropa-contacts.medium.com/2025-guide-to-haptics-enhancing-mobile-ux-with-tactile-feedback-676dd5937774)
- [WCAG Color Contrast Guide 2025](https://www.allaccessible.org/blog/color-contrast-accessibility-wcag-guide-2025)
- [Android: Testing for Accessibility](https://developer.android.com/codelabs/basic-android-kotlin-compose-test-accessibility)

### Community Feedback
- [Termux GitHub: Feature Request - Tabs #4134](https://github.com/termux/termux-app/issues/4134)
- [Termux GitHub: Navigation Bar #2785](https://github.com/termux/termux-app/issues/2785)
- [Termux GitHub: Multiple Instances #1005](https://github.com/termux/termux-app/issues/1005)
- [Reddit opinions on Termux](https://redditfavorites.com/android_apps/termux)
- [Termux G2 Reviews](https://www.g2.com/products/termux/reviews)
- [Termux in 2025: What's New](https://dev.to/terminaltools/termux-in-2025-whats-new-and-what-you-should-try-3l4m)

### Process Persistence
- [Understanding Process Death in Android](https://androidengineers.substack.com/p/understanding-process-death-in-android)
- [7 Techniques for Handling Process Death](https://programmerofpersia.medium.com/best-practices-for-handling-process-death-in-android-applications-cheat-sheet-series-42004afda242)
