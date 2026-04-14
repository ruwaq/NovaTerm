# NovaTerm Architecture Patterns
**Date:** March 2026 | **Author:** NovaTerm Research

Definiciones exactas de traits/interfaces derivadas del analisis de Alacritty, Ghostty, WezTerm, Kitty, Zellij, Warp y Rio. Cada interfaz esta diseñada para el contexto de NovaTerm: Android-first, Kotlin UI + Rust core, transicion progresiva desde Termux Java core.

---

## 1. TerminalBackend Abstraction

### El problema

NovaTerm tiene hoy el core de Termux (`TerminalSession` + `TerminalEmulator` en Java) y migrara a Rust (alacritty_terminal o libghostty-vt) en Phase 2. La interfaz debe ser identica para ambos backends.

### Patrones de la industria

| Terminal | Abstraccion | Detalles |
|---|---|---|
| Alacritty | `Term<T: EventListener>` | Generico sobre el listener de eventos |
| Ghostty | `Terminal` + `StreamHandler` | Terminal mutable, handler comptime-polymorfico |
| WezTerm | `Pane` trait | Abstrae local, remoto, SSH |
| Warp | Custom UI framework | Cada bloque es una unidad independiente |

### Interfaz para NovaTerm (Kotlin, Phase 1)

```kotlin
/**
 * Backend-agnostic terminal interface.
 *
 * Phase 1: Implemented by TermuxBackend (wraps TerminalSession).
 * Phase 2: Implemented by RustBackend (wraps alacritty_terminal via UniFFI).
 *
 * Design decisions:
 * - StateFlow-based: Compose observes changes reactively.
 * - No threading assumptions: backend handles its own threads.
 * - Cell-level access: renderer reads cells directly, no intermediate buffer.
 */
interface TerminalBackend {

    // ── Lifecycle ─────────────────────────────────────────

    /** Start the terminal process. Returns the shell PID. */
    fun start(config: TerminalConfig): Int

    /** Resize the terminal grid. Called on layout changes. */
    fun resize(rows: Int, cols: Int, pixelWidth: Int, pixelHeight: Int)

    /** Gracefully terminate the process. */
    fun shutdown()

    /** Force-kill the process (SIGKILL). */
    fun kill()

    /** Whether the child process is still running. */
    val isAlive: Boolean

    /** The child process PID, or -1 if not running. */
    val pid: Int

    /** Exit status of the child process. Only valid when !isAlive. */
    val exitStatus: Int

    // ── I/O ───────────────────────────────────────────────

    /** Write user input (keystrokes, paste) to the PTY. */
    fun write(data: ByteArray)

    /** Write a single Unicode codepoint to the PTY. */
    fun writeCodePoint(codePoint: Int)

    /** Send a mouse event to the PTY (if mouse reporting is enabled). */
    fun sendMouseEvent(
        button: Int, column: Int, row: Int, pressed: Boolean, moving: Boolean
    )

    // ── State access (for rendering) ──────────────────────

    /** Current terminal title (set by OSC 0/2). */
    val title: StateFlow<String>

    /** Current working directory (set by OSC 7). */
    val cwd: StateFlow<String>

    /** Terminal modes (cursor visible, app keypad, mouse reporting, etc). */
    val modes: StateFlow<TerminalModes>

    /** Cursor position and style. */
    val cursor: StateFlow<CursorState>

    /** Number of rows in the visible screen. */
    val rows: Int

    /** Number of columns in the visible screen. */
    val cols: Int

    /** Total lines in scrollback + screen. */
    val totalRows: Int

    /**
     * Read a cell from the grid.
     *
     * @param row Row index (0 = first visible row, negative = scrollback).
     * @param col Column index.
     * @return Cell data (codepoint, style, width).
     */
    fun getCell(row: Int, col: Int): TerminalCell

    /**
     * Get a range of cells for batch rendering.
     * More efficient than individual getCell() calls.
     *
     * @param startRow First row (inclusive).
     * @param endRow Last row (exclusive).
     * @return Array of rows, each containing an array of cells.
     */
    fun getCellRange(startRow: Int, endRow: Int): Array<Array<TerminalCell>>

    /**
     * Get the damage (changed regions) since last call.
     * Returns null if everything changed (full redraw needed).
     * Returns empty list if nothing changed.
     */
    fun consumeDamage(): List<DamageRegion>?

    /** Get selected text, or null if no selection. */
    fun getSelectedText(): String?

    // ── Events (terminal -> UI) ───────────────────────────

    /** Events emitted by the terminal for the UI to handle. */
    val events: SharedFlow<TerminalEvent>

    // ── Scrollback ────────────────────────────────────────

    /** Number of lines available in scrollback. */
    val scrollbackLines: Int

    /** Get text content of a scrollback range. */
    fun getTranscript(startRow: Int, endRow: Int): String
}
```

### Tipos de soporte

```kotlin
data class TerminalConfig(
    val shell: String,
    val args: Array<String>,
    val env: Array<String>,
    val cwd: String,
    val transcriptRows: Int = 2000,
    val initialRows: Int = 24,
    val initialCols: Int = 80,
)

data class TerminalCell(
    val codePoint: Int,       // Unicode codepoint (0 = empty)
    val width: Int,           // Display width (1 or 2 for CJK)
    val foreground: Int,      // ARGB color
    val background: Int,      // ARGB color
    val bold: Boolean,
    val italic: Boolean,
    val underline: Boolean,
    val strikethrough: Boolean,
    val dim: Boolean,
    val blink: Boolean,
    val inverse: Boolean,
    val invisible: Boolean,
)

data class CursorState(
    val row: Int,
    val col: Int,
    val style: CursorStyle,
    val visible: Boolean,
    val blinking: Boolean,
)

enum class CursorStyle {
    BLOCK, UNDERLINE, BAR
}

data class TerminalModes(
    val cursorVisible: Boolean,
    val appCursorKeys: Boolean,
    val appKeypad: Boolean,
    val mouseTrackingPress: Boolean,
    val mouseTrackingDrag: Boolean,
    val mouseTrackingAll: Boolean,
    val mouseProtocolSgr: Boolean,
    val bracketedPaste: Boolean,
    val altScreen: Boolean,
    val autoWrap: Boolean,
    val insertMode: Boolean,
    val originMode: Boolean,
    val focusTracking: Boolean,
    val synchronizedOutput: Boolean,
)

data class DamageRegion(
    val startRow: Int,
    val endRow: Int,    // exclusive
    val startCol: Int,
    val endCol: Int,    // exclusive
)

sealed interface TerminalEvent {
    data class TitleChanged(val title: String) : TerminalEvent
    data class Bell(val urgent: Boolean) : TerminalEvent
    data class ClipboardStore(val text: String) : TerminalEvent
    data object ClipboardRequest : TerminalEvent
    data class ColorRequest(val index: Int, val callback: (Int) -> String) : TerminalEvent
    data class CwdChanged(val cwd: String) : TerminalEvent
    data class Hyperlink(val uri: String, val params: Map<String, String>) : TerminalEvent
    data class ProcessExited(val exitCode: Int) : TerminalEvent
    data object Wakeup : TerminalEvent
}
```

### Implementacion Phase 1: TermuxBackend

```kotlin
/**
 * Wraps Termux's TerminalSession behind the TerminalBackend interface.
 * This is the strangler-fig adapter that will be replaced by RustBackend.
 */
class TermuxBackend(
    private val session: TerminalSession,
) : TerminalBackend {

    // Delegate to session.mEmulator for cell access
    override fun getCell(row: Int, col: Int): TerminalCell {
        val emulator = session.emulator ?: return EMPTY_CELL
        val screen = emulator.screen
        val style = screen.getStyleAt(row, col)
        return TerminalCell(
            codePoint = screen.getCodePointAt(row, col),
            width = WcWidth.width(screen.getCodePointAt(row, col)),
            foreground = TextStyle.decodeForeColor(style),
            background = TextStyle.decodeBackColor(style),
            // ... decode TextStyle flags
        )
    }
    // ...
}
```

### Interfaz Phase 2: Rust Backend Trait

```rust
/// Core terminal backend trait (Rust side).
///
/// Exposed to Kotlin via UniFFI for control operations.
/// Cell data is accessed via shared memory (JNI direct) for
/// zero-copy rendering, NOT through UniFFI (too slow for per-cell access).
pub trait TerminalBackend: Send + Sync {
    /// Start the shell process. Returns PID.
    fn start(&mut self, config: TerminalConfig) -> Result<u32, TerminalError>;

    /// Resize the terminal grid.
    fn resize(&mut self, rows: u16, cols: u16, pixel_w: u16, pixel_h: u16);

    /// Write input bytes to the PTY.
    fn write(&self, data: &[u8]);

    /// Write a single Unicode codepoint.
    fn write_codepoint(&self, cp: u32);

    /// Send mouse event.
    fn send_mouse_event(&self, button: u8, col: u16, row: u16,
                        pressed: bool, moving: bool);

    /// Graceful shutdown.
    fn shutdown(&mut self);

    /// Force kill (SIGKILL).
    fn kill(&mut self);

    /// Whether child process is alive.
    fn is_alive(&self) -> bool;

    /// Child PID.
    fn pid(&self) -> i32;

    /// Get terminal title.
    fn title(&self) -> String;

    /// Get working directory.
    fn cwd(&self) -> String;

    /// Get cursor state.
    fn cursor(&self) -> CursorState;

    /// Get terminal mode flags.
    fn modes(&self) -> TerminalModes;

    /// Grid dimensions.
    fn rows(&self) -> u16;
    fn cols(&self) -> u16;

    /// Get damage since last call. None = full redraw.
    fn consume_damage(&mut self) -> Option<Vec<DamageRegion>>;

    /// Get selected text.
    fn selected_text(&self) -> Option<String>;

    /// Total scrollback lines.
    fn scrollback_lines(&self) -> u32;

    /// Get transcript text for a range.
    fn transcript(&self, start_row: i32, end_row: i32) -> String;

    /// Poll for events. Non-blocking, returns all pending events.
    fn poll_events(&mut self) -> Vec<TerminalEvent>;
}
```

### Acceso a celdas por JNI directo (hot path)

```rust
/// NOT exposed via UniFFI. Called directly from JNI for zero-copy rendering.
///
/// The renderer calls this every frame to get the visible grid data.
/// Returns a pointer to a contiguous array of PackedCell structs.
///
/// PackedCell layout (16 bytes, cache-line friendly):
///   [0..3]  codepoint: u32
///   [4..7]  fg_color: u32 (ARGB)
///   [8..11] bg_color: u32 (ARGB)
///   [12]    width: u8
///   [13]    flags: u8 (bold|italic|underline|strike|dim|blink|inverse|invisible)
///   [14..15] reserved: u16
#[repr(C)]
pub struct PackedCell {
    pub codepoint: u32,
    pub fg_color: u32,
    pub bg_color: u32,
    pub width: u8,
    pub flags: u8,
    pub _reserved: u16,
}

/// Get a raw pointer to the visible grid for JNI direct buffer access.
/// The buffer is valid until the next call to process_output() or resize().
///
/// # Safety
/// Caller must ensure the terminal mutex is held during the entire read.
pub unsafe fn get_visible_grid(&self) -> (*const PackedCell, usize) {
    let cells = &self.packed_grid;
    (cells.as_ptr(), cells.len())
}
```

---

## 2. Event-Driven Architecture

### El problema

PTY I/O, VT parsing, rendering y UI input deben estar desacoplados. El modelo de threading incorrecto causa lag de input (tipico en terminales Android) o tearing visual.

### Patrones de la industria

| Terminal | Threads | Comunicacion |
|---|---|---|
| Alacritty | Main + EventLoop(IO+parse) | `Msg` enum via channel + mio polling |
| Ghostty | Main + IO + PTY-read + Render | SPSC mailboxes + xev.Async wakeup |
| WezTerm | Main + PtyReader per pane + async executor | `Arc<Mutex<>>` + `MuxNotification` |
| Warp | Main + IO + Render | Custom UI framework event loop |

### Modelo optimo para Android

```
┌─────────────────────────────────────────────────────┐
│                    MAIN THREAD                       │
│  Compose UI + Input handling + Service binding       │
│  Receives: TerminalEvent via SharedFlow              │
│  Sends: user input via Channel<InputMsg>             │
└─────────────────┬───────────────────────────────────┘
                  │ SharedFlow<TerminalEvent>
                  │ Channel<InputMsg>
┌─────────────────┴───────────────────────────────────┐
│                   I/O THREAD                         │
│  Event loop: poll PTY fd + drain input channel       │
│  VT parsing happens here (with terminal lock held)   │
│  Notifies render thread after state change            │
│                                                       │
│  ┌─────────────────────────────────┐                 │
│  │       PTY READ THREAD           │                 │
│  │  Blocking read() on PTY fd      │                 │
│  │  Wakes I/O thread via eventfd   │                 │
│  └─────────────────────────────────┘                 │
└─────────────────┬───────────────────────────────────┘
                  │ wakeup signal (Choreographer or eventfd)
┌─────────────────┴───────────────────────────────────┐
│                 RENDER THREAD                         │
│  Phase 1: AndroidView invalidate (main thread)       │
│  Phase 2: Vulkan/wgpu on dedicated thread            │
│  Acquires terminal lock, reads grid, releases lock   │
│  Submits GPU commands independently                   │
└─────────────────────────────────────────────────────┘
```

### Mensajes del Event Loop

```kotlin
/**
 * Messages from UI thread to I/O thread.
 * Modeled after Alacritty's Msg enum.
 */
sealed interface InputMsg {
    /** Raw bytes to write to PTY (keyboard input, paste). */
    data class Write(val data: ByteArray) : InputMsg

    /** Terminal resize request. */
    data class Resize(val rows: Int, val cols: Int,
                      val pixelW: Int, val pixelH: Int) : InputMsg

    /** Mouse event to forward to PTY. */
    data class Mouse(val button: Int, val col: Int, val row: Int,
                     val pressed: Boolean, val moving: Boolean) : InputMsg

    /** Configuration change (colors, font size, etc). */
    data class ConfigChange(val config: TerminalConfig) : InputMsg

    /** Focus gained/lost (for focus tracking mode). */
    data class FocusChange(val focused: Boolean) : InputMsg

    /** Shutdown the event loop. */
    data object Shutdown : InputMsg
}
```

### Rust Event Loop (Phase 2)

```rust
use std::sync::{Arc, Mutex};
use std::os::fd::RawFd;

/// Terminal I/O event loop, runs on a dedicated thread.
///
/// Inspired by Alacritty's EventLoop + Ghostty's three-thread model.
/// Optimized for Android: uses epoll directly (no mio overhead).
pub struct EventLoop<B: TerminalBackend> {
    /// The terminal backend (behind a fair mutex for renderer access).
    terminal: Arc<FairMutex<B>>,

    /// PTY master file descriptor.
    pty_fd: RawFd,

    /// Channel receiver for input messages from UI thread.
    input_rx: crossbeam_channel::Receiver<InputMsg>,

    /// Wakeup eventfd (signaled by PTY read thread).
    wakeup_fd: RawFd,

    /// Signal to renderer that a frame is needed.
    render_wakeup: Arc<AtomicBool>,

    /// Read buffer (reused across iterations, 1MB like Alacritty).
    read_buf: Vec<u8>,

    /// Write queue (VecDeque for efficient partial writes).
    write_queue: VecDeque<Vec<u8>>,

    /// VT parser state.
    parser: vte::Parser,
}

/// Messages from UI to event loop.
pub enum InputMsg {
    Write(Vec<u8>),
    Resize { rows: u16, cols: u16, pixel_w: u16, pixel_h: u16 },
    Mouse { button: u8, col: u16, row: u16, pressed: bool, moving: bool },
    ConfigChange(TerminalConfig),
    FocusChange(bool),
    Shutdown,
}

impl<B: TerminalBackend> EventLoop<B> {
    /// Spawn the event loop on a new thread.
    /// Returns a JoinHandle and a Sender for input messages.
    pub fn spawn(
        terminal: Arc<FairMutex<B>>,
        pty_fd: RawFd,
        render_wakeup: Arc<AtomicBool>,
    ) -> (JoinHandle<()>, crossbeam_channel::Sender<InputMsg>) {
        let (tx, rx) = crossbeam_channel::unbounded();
        let handle = std::thread::Builder::new()
            .name("novaterm-io".into())
            .spawn(move || {
                let mut event_loop = Self {
                    terminal,
                    pty_fd,
                    input_rx: rx,
                    wakeup_fd: create_eventfd(),
                    render_wakeup,
                    read_buf: vec![0u8; 1024 * 1024], // 1MB
                    write_queue: VecDeque::new(),
                    parser: vte::Parser::new(),
                };
                event_loop.run();
            })
            .expect("failed to spawn I/O thread");
        (handle, tx)
    }

    fn run(&mut self) {
        let epoll_fd = epoll_create();
        epoll_add(epoll_fd, self.pty_fd, EPOLLIN | EPOLLOUT);
        epoll_add(epoll_fd, self.wakeup_fd, EPOLLIN);

        let mut events = [EpollEvent::empty(); 4];

        loop {
            // Drain input channel (non-blocking)
            while let Ok(msg) = self.input_rx.try_recv() {
                match msg {
                    InputMsg::Write(data) => self.write_queue.push_back(data),
                    InputMsg::Resize { rows, cols, pixel_w, pixel_h } => {
                        let mut term = self.terminal.lock();
                        term.resize(rows, cols, pixel_w, pixel_h);
                        // Also resize the PTY fd
                        pty_resize(self.pty_fd, rows, cols, pixel_w, pixel_h);
                    }
                    InputMsg::Shutdown => return,
                    _ => { /* handle other messages */ }
                }
            }

            // Wait for PTY readiness or wakeup
            let n = epoll_wait(epoll_fd, &mut events, 100 /*ms timeout*/);

            for event in &events[..n] {
                if event.fd == self.pty_fd {
                    if event.is_readable() {
                        self.pty_read();
                    }
                    if event.is_writable() && !self.write_queue.is_empty() {
                        self.pty_write();
                    }
                }
            }
        }
    }

    /// Read from PTY and feed to VT parser.
    /// Limits locked time to MAX_LOCKED_READ (64KB, like Alacritty).
    fn pty_read(&mut self) {
        const MAX_LOCKED_READ: usize = 64 * 1024;
        let mut total = 0;

        loop {
            let n = match nix::unistd::read(self.pty_fd, &mut self.read_buf) {
                Ok(0) => break,     // EOF
                Ok(n) => n,
                Err(nix::errno::Errno::EAGAIN) => break,
                Err(_) => break,    // PTY closed
            };

            // Lock terminal and feed bytes to parser
            {
                let mut term = self.terminal.lock();
                for byte in &self.read_buf[..n] {
                    self.parser.advance(&mut *term, *byte);
                }
            }

            total += n;
            if total >= MAX_LOCKED_READ {
                break; // Yield to prevent blocking renderer
            }
        }

        // Signal renderer that new content is available
        self.render_wakeup.store(true, Ordering::Release);
    }

    /// Drain write queue to PTY.
    fn pty_write(&mut self) {
        while let Some(data) = self.write_queue.front_mut() {
            match nix::unistd::write(self.pty_fd, data) {
                Ok(n) if n == data.len() => { self.write_queue.pop_front(); }
                Ok(n) => { *data = data[n..].to_vec(); break; }
                Err(nix::errno::Errno::EAGAIN) => break,
                Err(_) => { self.write_queue.pop_front(); }
            }
        }
    }
}
```

### FairMutex (patron de Alacritty)

```rust
/// A mutex that prevents starvation by yielding to waiters.
///
/// Critical for terminal rendering: prevents the I/O thread from
/// starving the renderer during heavy output (e.g., cat large_file).
///
/// Alacritty's parking_lot::FairMutex is the reference implementation.
/// On Android, we use parking_lot which works with bionic libc.
pub type FairMutex<T> = parking_lot::FairMutex<T>;
```

---

## 3. Plugin/Extension System

### Patrones de la industria

| Terminal | Sistema | Lenguaje | Aislamiento | Rendimiento |
|---|---|---|---|---|
| Kitty | Kittens | Python | Proceso overlay | Bueno (out-of-process) |
| WezTerm | Config+Events | Lua 5.4 | In-process | Rapido |
| Zellij | WASM | Rust/Go/etc | Sandbox estricto | Medio |
| Ghostty | Ninguno (por ahora) | - | - | - |

### Arquitectura para NovaTerm (3 capas)

```
┌─────────────────────────────────────────────────┐
│           Layer 3: WASM Plugins (Phase 4)       │
│  Chicory runtime (pure Java, zero JNI)          │
│  Protobuf IPC, WASI sandboxing                  │
│  Para: plugins de terceros, sin trust            │
└─────────────────────────┬───────────────────────┘
                          │ PluginHost interface
┌─────────────────────────┴───────────────────────┐
│           Layer 2: Lua Scripts (Phase 4)         │
│  LuaJ runtime (pure Java)                       │
│  Para: themes, keybindings, output filters       │
└─────────────────────────┬───────────────────────┘
                          │ PluginHost interface
┌─────────────────────────┴───────────────────────┐
│           Layer 1: Kotlin API (Phase 2+)         │
│  Built-in extension points via interfaces        │
│  Para: features core (AI, MCP, etc.)             │
└─────────────────────────────────────────────────┘
```

### Interfaces del Plugin System

```kotlin
/**
 * Plugin host interface exposed to all plugin types.
 *
 * Inspired by:
 * - Kitty's Boss object (method-based access to terminal state)
 * - WezTerm's wezterm.on() event system
 * - Zellij's explicit permissions model
 */
interface PluginHost {

    // ── Terminal access ───────────────────────────────────

    /** Get the active terminal session. */
    fun activeSession(): TerminalBackend?

    /** Get all terminal sessions. */
    fun allSessions(): List<TerminalBackend>

    /** Create a new terminal session. */
    fun createSession(config: TerminalConfig? = null): TerminalBackend

    /** Write text to the active terminal. */
    fun writeToTerminal(text: String)

    /** Get visible text from the active terminal. */
    fun getVisibleText(): String

    /** Get scrollback text. */
    fun getScrollbackText(lines: Int = -1): String

    // ── UI manipulation ──────────────────────────────────

    /** Show a toast/notification to the user. */
    fun notify(title: String, body: String, urgency: Urgency = Urgency.NORMAL)

    /** Show an overlay panel (like Kitty kittens). */
    fun showOverlay(content: OverlayContent)

    /** Dismiss the current overlay. */
    fun dismissOverlay()

    /** Set the status bar text. */
    fun setStatusText(text: String)

    // ── Configuration ────────────────────────────────────

    /** Read a configuration value. */
    fun getConfig(key: String): String?

    /** Set a configuration value (runtime only, not persisted). */
    fun setConfig(key: String, value: String)

    /** Get the current color scheme as a map. */
    fun getColorScheme(): Map<String, Int>

    // ── Event system ─────────────────────────────────────

    /** Register an event handler (like wezterm.on). */
    fun on(event: String, handler: EventHandler)

    /** Emit a custom event (like wezterm.emit). */
    fun emit(event: String, args: Map<String, Any>): Boolean

    // ── Clipboard ────────────────────────────────────────

    fun clipboardGet(): String?
    fun clipboardSet(text: String)

    // ── Filesystem (sandboxed) ───────────────────────────

    /** Read a file within the plugin's data directory. */
    fun readFile(path: String): ByteArray?

    /** Write a file within the plugin's data directory. */
    fun writeFile(path: String, data: ByteArray)
}

/** Event handler callback. */
fun interface EventHandler {
    /**
     * Handle an event. Return false to prevent default action
     * (like WezTerm's event system).
     */
    fun handle(args: Map<String, Any>): Boolean
}

enum class Urgency { LOW, NORMAL, HIGH }
```

### Plugin Manifest (para WASM plugins, Phase 4)

```kotlin
/**
 * Plugin manifest, loaded from plugin.toml.
 * Based on Zellij's permission model.
 */
data class PluginManifest(
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val minNovaTermVersion: String,

    /** Explicit permissions (Zellij model). */
    val permissions: Set<PluginPermission>,

    /** Events this plugin wants to receive. */
    val subscribedEvents: Set<String>,

    /** WASI filesystem mounts. */
    val mounts: List<WasiMount> = emptyList(),
)

enum class PluginPermission {
    READ_TERMINAL,        // Read terminal content
    WRITE_TERMINAL,       // Write to terminal
    READ_CLIPBOARD,       // Read system clipboard
    WRITE_CLIPBOARD,      // Write system clipboard
    RUN_COMMANDS,         // Execute shell commands
    NETWORK_ACCESS,       // HTTP/WebSocket access
    FILESYSTEM_READ,      // Read files outside plugin dir
    FILESYSTEM_WRITE,     // Write files outside plugin dir
    SHOW_OVERLAY,         // Display overlay UI
    SEND_NOTIFICATIONS,   // Send Android notifications
}

data class WasiMount(
    val guest: String,    // Path inside WASM (e.g., "/data")
    val host: String,     // Path on Android (e.g., plugin data dir)
    val writable: Boolean,
)
```

### Eventos built-in del sistema

```kotlin
/**
 * Standard events that plugins can subscribe to.
 * Named after WezTerm conventions where applicable.
 */
object TerminalEvents {
    // Session lifecycle
    const val SESSION_CREATED = "session-created"
    const val SESSION_DESTROYED = "session-destroyed"
    const val SESSION_FOCUSED = "session-focused"

    // Output events
    const val OUTPUT_LINE = "output-line"           // New line of output
    const val COMMAND_COMPLETE = "command-complete"  // OSC 133 command done
    const val BELL = "bell"

    // Input events (pre-processing, can be intercepted)
    const val KEY_INPUT = "key-input"               // Return false to consume
    const val PASTE = "paste"                        // Return false to consume

    // UI events
    const val WINDOW_RESIZED = "window-resized"
    const val CONFIG_RELOADED = "config-reloaded"
    const val THEME_CHANGED = "theme-changed"

    // AI events (Phase 3)
    const val AI_SUGGESTION = "ai-suggestion"
    const val AI_CONTEXT_REQUEST = "ai-context-request"

    // Custom events (plugins can define their own)
    // Convention: "plugin-name:event-name"
}
```

---

## 4. Rendering Pipeline Abstraction

### El problema

NovaTerm necesita migrar de Canvas (Phase 1, via TermuxView) a Vulkan/wgpu (Phase 2). La interfaz de rendering debe ser swappable sin cambiar el resto de la app.

### Patrones de la industria

| Terminal | Renderers | Patron |
|---|---|---|
| Alacritty | GLES2 + GLSL3 | Dual renderer con feature flag |
| Ghostty | Metal + OpenGL | Backend seleccionado en compile time |
| WezTerm | OpenGL + WebGPU | `RenderState` trait-like |
| Rio | wgpu (DX/Metal/Vk/GL/WebGPU) | Single abstraction via wgpu |
| Warp | Metal (+ planned GL/WebGL) | Primitives layer: rect, glyph, image |

### Interfaz para NovaTerm

```kotlin
/**
 * Terminal renderer interface.
 *
 * Phase 1: CanvasRenderer (wraps TerminalView's Canvas drawing).
 * Phase 2: WgpuRenderer (Rust + wgpu, renders to ANativeWindow).
 *
 * The renderer reads from TerminalBackend and produces pixels.
 * It does NOT own the terminal state - it only reads it.
 */
interface TerminalRenderer {

    // ── Lifecycle ─────────────────────────────────────────

    /** Initialize the renderer with a surface. */
    fun initialize(surface: RenderSurface, config: RenderConfig)

    /** Release all GPU/rendering resources. */
    fun destroy()

    /** Whether the renderer is initialized and ready to draw. */
    val isReady: Boolean

    // ── Rendering ─────────────────────────────────────────

    /**
     * Render the current terminal state to the surface.
     *
     * @param backend The terminal backend to read state from.
     * @param selection Optional selection range to highlight.
     * @param searchMatches Optional search result highlights.
     */
    fun render(
        backend: TerminalBackend,
        selection: TextSelection? = null,
        searchMatches: List<TextRange>? = null,
    )

    // ── Configuration ─────────────────────────────────────

    /** Update rendering configuration (font, colors, etc). */
    fun updateConfig(config: RenderConfig)

    /** Called when the surface size changes. */
    fun onSurfaceChanged(width: Int, height: Int)

    // ── Metrics ───────────────────────────────────────────

    /** Cell dimensions in pixels (for input coordinate mapping). */
    val cellWidth: Float
    val cellHeight: Float

    /** Current FPS (for debug overlay). */
    val fps: Float

    /** Last frame render time in milliseconds. */
    val lastFrameTimeMs: Float
}

/**
 * Render surface abstraction.
 * Phase 1: Wraps Android Canvas.
 * Phase 2: Wraps ANativeWindow (for Vulkan/wgpu).
 */
sealed interface RenderSurface {
    /** Android Canvas surface (Phase 1). */
    data class CanvasSurface(val view: android.view.View) : RenderSurface

    /** Native window surface for GPU rendering (Phase 2). */
    data class NativeSurface(val nativeWindow: Long) : RenderSurface
}

data class RenderConfig(
    val fontFamily: String,
    val fontSize: Float,            // in sp
    val fontWeight: Int = 400,      // 100-900
    val lineSpacing: Float = 1.0f,  // multiplier
    val colorScheme: ColorScheme,
    val cursorStyle: CursorStyle,
    val cursorBlink: Boolean,
    val cursorBlinkRate: Long = 530, // ms
    val antiAlias: Boolean = true,
    val ligatures: Boolean = false,  // Phase 2: HarfBuzz
    val gpuAccelerated: Boolean = false, // Phase 2: wgpu
)
```

### Rust Renderer Trait (Phase 2)

```rust
/// GPU renderer trait.
///
/// Implementations:
/// - WgpuRenderer: Primary (Vulkan on Android, WebGPU for WASM)
/// - SoftwareRenderer: Fallback (pixman or tiny-skia)
///
/// Architecture inspired by:
/// - Ghostty: separate render thread, mutex on terminal state
/// - Alacritty: 2 draw calls (background + foreground), damage tracking
/// - Windows Terminal: dynamic glyph atlas with LRU eviction
pub trait Renderer: Send {
    /// Initialize with a raw window handle.
    fn initialize(
        &mut self,
        window: &impl raw_window_handle::HasRawWindowHandle,
        width: u32,
        height: u32,
        config: &RenderConfig,
    ) -> Result<(), RenderError>;

    /// Render a frame.
    ///
    /// The caller holds a read lock on the terminal.
    /// The renderer MUST NOT hold the lock longer than necessary.
    fn render(&mut self, state: &RenderState) -> Result<FrameStats, RenderError>;

    /// Resize the rendering surface.
    fn resize(&mut self, width: u32, height: u32);

    /// Update configuration (font change, color change, etc).
    fn update_config(&mut self, config: &RenderConfig);

    /// Release all resources.
    fn destroy(&mut self);

    /// Cell dimensions in pixels.
    fn cell_size(&self) -> (f32, f32);
}

/// Snapshot of terminal state needed for rendering.
/// Created by locking the terminal briefly, copying only what's needed.
///
/// This is the Ghostty pattern: lock terminal, copy visible state,
/// unlock, then render at leisure.
pub struct RenderState {
    /// Visible grid cells (rows * cols PackedCells).
    pub cells: Vec<PackedCell>,
    pub rows: u16,
    pub cols: u16,

    /// Cursor state.
    pub cursor: CursorState,
    pub cursor_visible: bool,

    /// Damage regions since last frame.
    pub damage: Option<Vec<DamageRegion>>,

    /// Selection range (if any).
    pub selection: Option<SelectionRange>,

    /// Search highlights (if any).
    pub search_matches: Vec<TextRange>,

    /// Terminal color palette (256 colors + fg/bg/cursor).
    pub colors: [u32; 259],
}

/// Stats returned after each frame for monitoring.
pub struct FrameStats {
    pub frame_time_us: u64,    // Total frame time
    pub gpu_time_us: u64,      // GPU execution time
    pub cells_drawn: u32,      // Cells actually rendered
    pub cells_skipped: u32,    // Cells skipped by damage tracking
    pub atlas_utilization: f32, // Glyph atlas usage (0.0-1.0)
}
```

### Glyph Atlas (Phase 2)

```rust
/// Glyph atlas manager.
///
/// Architecture:
/// - Grayscale atlas for regular text (smaller, faster)
/// - BGRA atlas for emoji and color fonts
/// - LFU eviction when atlas is full (WezTerm pattern)
/// - SharedGridSet pattern: share atlas across terminal tabs (Ghostty pattern)
///
/// fontdue for rasterization (pure Rust, fast, no system deps).
pub struct GlyphAtlas {
    /// Grayscale texture atlas for text glyphs.
    text_atlas: TextureAtlas,

    /// BGRA texture atlas for emoji/color glyphs.
    color_atlas: TextureAtlas,

    /// LFU cache mapping GlyphKey -> AtlasEntry.
    cache: LfuCache<GlyphKey, AtlasEntry>,

    /// Font database for rasterization.
    fonts: FontSet,
}

#[derive(Hash, Eq, PartialEq, Clone)]
pub struct GlyphKey {
    pub codepoint: u32,
    pub flags: u8,       // bold, italic
    pub font_size_x10: u16, // font size * 10 for sub-pixel precision
}

pub struct AtlasEntry {
    pub atlas_type: AtlasType,  // Text or Color
    pub x: u16,
    pub y: u16,
    pub width: u16,
    pub height: u16,
    pub bearing_x: i16,
    pub bearing_y: i16,
    pub advance: u16,
}

enum AtlasType { Text, Color }
```

---

## 5. Cross-Platform Rust Core

### El problema

El Rust core debe funcionar en Android (arm64, via JNI/UniFFI), WASM (browser terminal, Phase 4), y potencialmente desktop para testing. El mismo codigo VT, el mismo parser, la misma logica de terminal.

### Patrones de la industria

| Proyecto | Estructura | FFI | Build |
|---|---|---|---|
| Mux (video SDK) | workspace: core + platform crates | JNI + FFI + wasm-bindgen | cargo-ndk |
| Firefox (Mozilla) | workspace: shared + platform | UniFFI | cargo-ndk + xcode |
| Ghostty | monolithic Zig | C-ABI | zig build |
| Rio | workspace: sugarloaf + core | Direct Rust | cargo |

### Cargo Workspace para NovaTerm

```
novaterm-core/
├── Cargo.toml                    # Workspace root
├── crates/
│   ├── novaterm-vt/              # VT parser + terminal state
│   │   ├── Cargo.toml
│   │   └── src/
│   │       ├── lib.rs
│   │       ├── parser.rs         # VT state machine (wraps vte crate)
│   │       ├── terminal.rs       # Terminal struct (grid, modes, cursor)
│   │       ├── grid.rs           # Grid storage (PageList-inspired)
│   │       ├── cell.rs           # Cell types
│   │       ├── modes.rs          # Terminal modes (TerminalModes)
│   │       ├── color.rs          # Color management
│   │       ├── selection.rs      # Text selection logic
│   │       ├── damage.rs         # Damage tracking (rangeset)
│   │       └── handler.rs        # VT action handler
│   │
│   ├── novaterm-pty/             # PTY management
│   │   ├── Cargo.toml
│   │   └── src/
│   │       ├── lib.rs
│   │       ├── pty.rs            # Pty trait + Unix impl
│   │       └── event_loop.rs     # I/O event loop
│   │
│   ├── novaterm-renderer/        # Rendering abstraction
│   │   ├── Cargo.toml
│   │   └── src/
│   │       ├── lib.rs
│   │       ├── renderer.rs       # Renderer trait
│   │       ├── atlas.rs          # Glyph atlas
│   │       ├── wgpu_backend.rs   # wgpu implementation
│   │       └── software.rs       # Software fallback
│   │
│   ├── novaterm-android/         # Android JNI bridge
│   │   ├── Cargo.toml
│   │   └── src/
│   │       ├── lib.rs            # JNI entry points
│   │       ├── uniffi_api.rs     # UniFFI-exported functions
│   │       └── jni_direct.rs     # Direct JNI for hot paths
│   │
│   └── novaterm-wasm/            # WASM bridge (Phase 4)
│       ├── Cargo.toml
│       └── src/
│           ├── lib.rs            # wasm-bindgen entry points
│           └── web_renderer.rs   # WebGPU renderer adapter
│
├── novaterm-vt/Cargo.toml:
│   [package]
│   name = "novaterm-vt"
│   version = "0.1.0"
│   edition = "2021"
│
│   [dependencies]
│   vte = "0.15"                  # Alacritty's VT parser
│   rangeset = "0.3"              # Damage tracking
│   bitflags = "2"                # Terminal modes
│   unicode-width = "0.2"        # Character width
│   log = "0.4"
│
│   # NO platform-specific dependencies here.
│   # This crate must compile for all targets.
│
├── novaterm-pty/Cargo.toml:
│   [package]
│   name = "novaterm-pty"
│
│   [dependencies]
│   novaterm-vt = { path = "../novaterm-vt" }
│   crossbeam-channel = "0.5"
│   parking_lot = "0.12"         # FairMutex
│   nix = { version = "0.30", features = ["process", "pty", "signal"] }
│   log = "0.4"
│
│   [target.'cfg(target_os = "android")'.dependencies]
│   android_logger = "0.14"
│
├── novaterm-renderer/Cargo.toml:
│   [package]
│   name = "novaterm-renderer"
│
│   [dependencies]
│   novaterm-vt = { path = "../novaterm-vt" }
│   raw-window-handle = "0.6"
│   fontdue = "0.9"              # Glyph rasterization
│   log = "0.4"
│
│   [features]
│   default = ["wgpu-backend"]
│   wgpu-backend = ["wgpu"]
│   software = ["tiny-skia"]
│
│   [dependencies.wgpu]
│   version = "29"
│   optional = true
│   features = ["vulkan"]        # Android: Vulkan primary
│
│   [dependencies.tiny-skia]
│   version = "0.11"
│   optional = true
│
│   [target.'cfg(target_arch = "wasm32")'.dependencies]
│   wgpu = { version = "29", features = ["webgpu"] }
│
├── novaterm-android/Cargo.toml:
│   [package]
│   name = "novaterm-android"
│
│   [lib]
│   crate-type = ["cdylib"]      # Generates .so
│
│   [dependencies]
│   novaterm-vt = { path = "../novaterm-vt" }
│   novaterm-pty = { path = "../novaterm-pty" }
│   novaterm-renderer = { path = "../novaterm-renderer" }
│   uniffi = "0.31"
│   jni = "0.21"                 # Direct JNI for hot paths
│   android_logger = "0.14"
│   log = "0.4"
│
│   [build-dependencies]
│   uniffi = { version = "0.31", features = ["build"] }
│
└── novaterm-wasm/Cargo.toml:
    [package]
    name = "novaterm-wasm"

    [lib]
    crate-type = ["cdylib"]

    [dependencies]
    novaterm-vt = { path = "../novaterm-vt" }
    novaterm-renderer = { path = "../novaterm-renderer", features = ["wgpu-backend"] }
    wasm-bindgen = "0.2"
    web-sys = { version = "0.3", features = ["HtmlCanvasElement", "GpuDevice"] }
```

### Build Pipeline

```bash
# Android (from novaterm-core/)
cargo ndk -t arm64-v8a build --release -p novaterm-android

# WASM (Phase 4)
wasm-pack build crates/novaterm-wasm --target web

# Desktop testing (runs terminal in native window)
cargo run -p novaterm-desktop --features software
```

### UniFFI Interface Definition (novaterm-android)

```rust
// Using proc-macro approach (not UDL files).
// UniFFI generates Kotlin bindings automatically.

#[uniffi::export]
pub fn create_terminal(config: TerminalConfig) -> Arc<TerminalHandle> {
    // ...
}

#[uniffi::export]
impl TerminalHandle {
    pub fn write(&self, data: Vec<u8>) { /* ... */ }
    pub fn resize(&self, rows: u16, cols: u16, pw: u16, ph: u16) { /* ... */ }
    pub fn shutdown(&self) { /* ... */ }
    pub fn title(&self) -> String { /* ... */ }
    pub fn cwd(&self) -> String { /* ... */ }
    pub fn cursor(&self) -> CursorState { /* ... */ }
    pub fn modes(&self) -> TerminalModes { /* ... */ }
    pub fn is_alive(&self) -> bool { /* ... */ }
    pub fn pid(&self) -> i32 { /* ... */ }
    pub fn poll_events(&self) -> Vec<TerminalEvent> { /* ... */ }
    pub fn consume_damage(&self) -> Option<Vec<DamageRegion>> { /* ... */ }
    pub fn selected_text(&self) -> Option<String> { /* ... */ }
}

// Hot path: JNI direct (NOT UniFFI)
// This is called every frame by the renderer.
#[no_mangle]
pub unsafe extern "C" fn Java_com_novaterm_core_rust_NativeTerminal_getVisibleGrid(
    env: jni::JNIEnv,
    _class: jni::objects::JClass,
    handle: jni::sys::jlong,
    buffer: jni::objects::JByteBuffer,
) -> jni::sys::jint {
    let terminal = &*(handle as *const TerminalHandle);
    let term = terminal.inner.lock();
    let (ptr, len) = term.get_visible_grid();

    // Copy to DirectByteBuffer for zero-copy access from Kotlin/GPU
    let buf_ptr = env.get_direct_buffer_address(&buffer).unwrap();
    std::ptr::copy_nonoverlapping(
        ptr as *const u8,
        buf_ptr,
        len * std::mem::size_of::<PackedCell>(),
    );

    len as jni::sys::jint
}
```

---

## Resumen de Decisiones

| Patron | Referencia | Decision NovaTerm |
|---|---|---|
| Backend abstraction | WezTerm Pane trait | `TerminalBackend` interface en Kotlin, `TerminalBackend` trait en Rust |
| Event loop | Alacritty EventLoop + Ghostty 3-thread | epoll + FairMutex + dedicated PTY read thread |
| VT parser | Alacritty vte crate (Perform trait) | Wrap vte crate, implement Perform para Terminal |
| Renderer swap | Rio wgpu multi-backend | `TerminalRenderer` interface, wgpu default, software fallback |
| Plugin system | Zellij WASM + WezTerm events | 3 capas: Kotlin API + Lua + WASM |
| Cross-platform core | Mozilla UniFFI pattern | Cargo workspace, UniFFI para control, JNI directo para hot paths |
| Glyph caching | WezTerm LFU + Ghostty SharedGridSet | LFU atlas con shared set entre tabs |
| Damage tracking | Alacritty rangeset | Cell-level damage, skip unchanged regions |
| Cell access | Ghostty PackedCell + mutex snapshot | 16-byte PackedCell via DirectByteBuffer |
| Permissions | Zellij explicit model | PluginPermission enum, manifest-declared |

---

## Sources

- [Alacritty event_loop.rs](https://github.com/alacritty/alacritty/blob/master/alacritty_terminal/src/event_loop.rs)
- [Alacritty event.rs](https://github.com/alacritty/alacritty/blob/master/alacritty_terminal/src/event.rs)
- [Alacritty Term](https://github.com/alacritty/alacritty/blob/master/alacritty_terminal/src/term/mod.rs)
- [vte Perform trait](https://docs.rs/vte/latest/vte/trait.Perform.html)
- [alacritty_terminal Handler trait](https://docs.rs/alacritty_terminal/0.10.0/alacritty_terminal/ansi/trait.Handler.html)
- [Ghostty Architecture (DeepWiki)](https://deepwiki.com/ghostty-org/ghostty)
- [Ghostty Terminal Emulation](https://deepwiki.com/ghostty-org/ghostty/3-terminal-emulation)
- [WezTerm Architecture (DeepWiki)](https://deepwiki.com/wezterm/wezterm)
- [WezTerm Plugins](https://wezterm.org/config/plugins.html)
- [WezTerm Events](https://wezterm.org/config/lua/wezterm/on.html)
- [Kitty Custom Kittens](https://sw.kovidgoyal.net/kitty/kittens/custom/)
- [Kitty Protocol Extensions](https://sw.kovidgoyal.net/kitty/protocol-extensions/)
- [Zellij Plugin System](https://deepwiki.com/zellij-org/zellij)
- [UniFFI on Android](https://sal.dev/android/intro-rust-android-uniffi/)
- [UniFFI GitHub](https://github.com/mozilla/uniffi-rs)
- [Mux Rust Cross-Platform](https://www.mux.com/blog/practical-client-side-rust-for-android-ios-and-web)
- [Warp: How Warp Works](https://www.warp.dev/blog/how-warp-works)
- [Rust Universal Template](https://github.com/Rightpoint/rust-universal-template)
