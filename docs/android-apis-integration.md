# NovaTerm Android Platform APIs Integration
**Date:** March 2026 | **Author:** NovaTerm Research

---

## Phase 1 (Now) — High Priority, Low Effort

### Predictive Back Gesture (CRITICAL — mandatory API 36)
`onBackPressed()` no longer invoked in Android 16+. Must use `OnBackPressedCallback`.
Confirm exit when sessions are running.

### Notification Channels + Actions
- Channels: service (LOW), alerts (HIGH), commands (DEFAULT)
- Actions: New Session, Toggle Wake Lock, Exit

### App Shortcuts (static)
`res/xml/shortcuts.xml`: "New Session", "SSH Session"
Max 4 visible simultaneously.

### Quick Settings Tile
`TileService` for terminal access and wake lock toggle.

### Clipboard (with sensitive flag)
`ClipDescription.EXTRA_IS_SENSITIVE` for passwords/tokens (API 33+).

### Deep Links (`novaterm://`)
- `novaterm://new` — new session
- `novaterm://run?cmd=...` — execute command
- `novaterm://ssh?host=...&user=...` — SSH connection

### Share Intent (receive text/files)
Accept `ACTION_SEND` with `text/*` and `*/*` MIME types.

### Battery Optimization Request
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` dialog on first launch.

### Thermal Monitoring
`PowerManager.addThermalStatusListener()` — reduce rendering on THERMAL_STATUS_MODERATE+.

### Adaptive Icon (with monochrome)
Monochrome layer for Android 13+ themed icons.

### Keyboard Shortcuts Helper
`onProvideKeyboardShortcuts()`: Ctrl+T (new), Ctrl+W (close), Ctrl+Tab (switch), etc.

---

## Phase 2 (Post-launch)

### Foldable Support
`WindowInfoTracker` + `FoldingFeature.State.HALF_OPENED`:
- Horizontal fold → tabletop mode (terminal top, keyboard bottom)
- Vertical fold → book mode (two sessions side by side)

### WindowSizeClass
Compact → phone layout, Medium → wider terminal, Expanded → sidebar + terminal.

### Accessibility (TalkBack)
- Line-mode `AccessibilityNodeInfo` for terminal buffer
- OSC 133 semantic zones for command navigation
- Non-linear font scaling (Android 14+)

### Dynamic Shortcuts
Update shortcuts based on recent sessions/SSH hosts.

### Drag and Drop
`dragAndDropSource`/`dragAndDropTarget` modifiers for cross-window text/file transfer.

### SAF Picker
Import/export files to/from terminal filesystem.

### Glance Widgets
Quick command runner, session status, system monitor.

### Material You Dynamic Colors
For app chrome only (toolbar, tabs). Terminal always uses its own palette.

### ADPF Performance Hints
`PerformanceHintManager.createHintSession()` for Vulkan render thread (Phase 2 renderer).

---

## Phase 3+

### DocumentsProvider
Expose terminal filesystem to other apps via SAF.

### Picture-in-Picture
Monitor running processes in PiP window. Show last N lines with large font.

### Bubbles
For AI chat integration (Phase 3).

### Tasker/Automate Integration
`BroadcastReceiver` for `com.novaterm.app.RUN_COMMAND` action.
