// TerminalBackend trait and AlacrittyBackend implementation.
//
// The trait defines the contract that any VT backend must fulfill.
// Phase 2 starts with AlacrittyBackend; the trait allows swapping
// to libghostty-vt or a custom parser in the future.

use crate::event::EventCollector;
use crate::grid_snapshot::{self, GridSnapshot};

use alacritty_terminal::grid::Dimensions;
use alacritty_terminal::term::{Config as TermConfig, Term};
use alacritty_terminal::vte::ansi::Processor;

/// Default scrollback buffer size in lines.
const DEFAULT_SCROLLBACK: usize = 10_000;

/// Abstract terminal backend — the Strangler Fig interface.
///
/// Both the Java (Termux) emulator and the Rust (alacritty) emulator
/// can implement this trait. The Kotlin TerminalViewModel talks to
/// whichever backend is active through the JNI bridge.
pub trait TerminalBackend: Send {
    /// Feed raw bytes from the PTY into the VT parser.
    fn process_bytes(&mut self, data: &[u8]);

    /// Write user input (keyboard) to be sent to the PTY.
    /// Returns bytes that should be written to the PTY fd.
    fn input_bytes(&mut self, data: &[u8]) -> Vec<u8>;

    /// Take a snapshot of the current visible grid.
    /// Mutable because reading damage resets it (consume-on-read).
    fn snapshot(&mut self) -> GridSnapshot;

    /// Resize the terminal grid.
    fn resize(&mut self, rows: u32, cols: u32);

    /// Scroll the viewport (positive = up, negative = down).
    fn scroll(&mut self, delta: i32);

    /// Get pending events (bell, title change, etc.) and drain them.
    fn drain_events(&self) -> Vec<crate::event::BackendEvent>;

    /// Check if there are pending events without consuming them.
    fn has_pending_events(&self) -> bool;

    /// Reset terminal state (like `reset` command).
    fn reset(&mut self);

    /// Get current grid dimensions.
    fn dimensions(&self) -> (u32, u32);

    /// Drain bytes that need to be written back to the PTY.
    /// These are responses to DA, DSR, and other query sequences.
    fn drain_pty_writes(&self) -> Vec<u8>;
}

/// Terminal size for alacritty_terminal's Dimensions trait.
struct TermSize {
    columns: usize,
    screen_lines: usize,
}

impl Dimensions for TermSize {
    fn columns(&self) -> usize {
        self.columns
    }

    fn screen_lines(&self) -> usize {
        self.screen_lines
    }

    fn total_lines(&self) -> usize {
        self.screen_lines
    }
}

/// TerminalBackend implementation backed by alacritty_terminal 0.25.1.
///
/// Uses vte 0.15's ansi::Processor to parse VT sequences and
/// alacritty_terminal's Term for grid/state management.
pub struct AlacrittyBackend {
    term: Term<EventCollector>,
    processor: Processor,
    event_collector: EventCollector,
    cols: u32,
    rows: u32,
}

impl AlacrittyBackend {
    /// Create a new backend with the given initial dimensions.
    pub fn new(rows: u32, cols: u32) -> Self {
        let event_collector = EventCollector::new();
        let size = TermSize {
            columns: cols as usize,
            screen_lines: rows as usize,
        };

        let config = TermConfig {
            scrolling_history: DEFAULT_SCROLLBACK,
            kitty_keyboard: true, // Enable Kitty Keyboard Protocol
            ..TermConfig::default()
        };

        let term = Term::new(config, &size, event_collector.clone());
        let processor = Processor::new();

        Self {
            term,
            processor,
            event_collector,
            cols,
            rows,
        }
    }

    /// Access the underlying term for advanced operations.
    pub fn term(&self) -> &Term<EventCollector> {
        &self.term
    }

    /// Get cursor state without taking a full grid snapshot.
    pub fn cursor_state(&self) -> crate::grid_snapshot::CursorState {
        let point = self.term.grid().cursor.point;
        debug_assert!(
            point.column.0 <= i32::MAX as usize,
            "column index {} exceeds i32::MAX", point.column.0
        );
        crate::grid_snapshot::CursorState {
            row: point.line.0,
            col: point.column.0 as i32,
            shape: crate::grid_snapshot::cursor_shape_to_u8(self.term.cursor_style().shape),
            visible: true,
        }
    }
}

impl TerminalBackend for AlacrittyBackend {
    fn process_bytes(&mut self, data: &[u8]) {
        // Feed PTY output through the VT parser into the terminal grid.
        // This is the hot path — called for every chunk of PTY output.
        // vte 0.15 accepts &[u8] slices directly (batch processing).
        self.processor.advance(&mut self.term, data);
    }

    fn input_bytes(&mut self, data: &[u8]) -> Vec<u8> {
        // For now, pass through directly. In the future, this will handle
        // Kitty Keyboard Protocol encoding.
        data.to_vec()
    }

    fn snapshot(&mut self) -> GridSnapshot {
        grid_snapshot::snapshot_from_term(&mut self.term)
    }

    fn resize(&mut self, rows: u32, cols: u32) {
        if rows == 0 || cols == 0 {
            return;
        }
        self.rows = rows;
        self.cols = cols;
        let size = TermSize {
            columns: cols as usize,
            screen_lines: rows as usize,
        };
        self.term.resize(size);
    }

    fn scroll(&mut self, delta: i32) {
        use alacritty_terminal::grid::Scroll;
        if delta != 0 {
            self.term.scroll_display(Scroll::Delta(delta));
        }
    }

    fn drain_events(&self) -> Vec<crate::event::BackendEvent> {
        self.event_collector.drain()
    }

    fn has_pending_events(&self) -> bool {
        self.event_collector.has_pending()
    }

    fn reset(&mut self) {
        *self = Self::new(self.rows, self.cols);
    }

    fn dimensions(&self) -> (u32, u32) {
        (self.rows, self.cols)
    }

    fn drain_pty_writes(&self) -> Vec<u8> {
        // Use atomic drain that filters PtyWrite inside the lock,
        // avoiding the race condition of drain-all → filter → re-push.
        self.event_collector.drain_pty_writes()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn create_backend() {
        let backend = AlacrittyBackend::new(24, 80);
        assert_eq!(backend.dimensions(), (24, 80));
    }

    #[test]
    fn process_simple_text() {
        let mut backend = AlacrittyBackend::new(24, 80);
        backend.process_bytes(b"Hello, NovaTerm!");

        let snap = backend.snapshot();
        assert_eq!(snap.rows, 24);
        assert_eq!(snap.cols, 80);

        // First row should contain "Hello, NovaTerm!"
        let first_row: String = snap.cells[..80]
            .iter()
            .map(|c| c.character)
            .collect::<String>()
            .trim_end()
            .to_string();
        assert!(first_row.starts_with("Hello, NovaTerm!"));
    }

    #[test]
    fn process_escape_sequences() {
        let mut backend = AlacrittyBackend::new(24, 80);
        // Set title via OSC
        backend.process_bytes(b"\x1b]0;Test Title\x07");
        // Bold + red text
        backend.process_bytes(b"\x1b[1;31mRed Bold\x1b[0m Normal");

        let snap = backend.snapshot();
        // First cell should have bold flag set
        let first_cell = &snap.cells[0];
        assert_eq!(first_cell.character, 'R');
        assert!(first_cell.flags & 1 != 0, "Should have bold flag");
    }

    #[test]
    fn resize() {
        let mut backend = AlacrittyBackend::new(24, 80);
        backend.resize(48, 120);
        assert_eq!(backend.dimensions(), (48, 120));

        let snap = backend.snapshot();
        assert_eq!(snap.rows, 48);
        assert_eq!(snap.cols, 120);
    }

    #[test]
    fn cursor_position() {
        let mut backend = AlacrittyBackend::new(24, 80);
        // Move cursor to row 5, col 10
        backend.process_bytes(b"\x1b[5;10H");

        let snap = backend.snapshot();
        // alacritty uses 0-based internally, CSI is 1-based
        assert_eq!(snap.cursor.col, 9);
    }

    #[test]
    fn scroll_operations() {
        let mut backend = AlacrittyBackend::new(5, 80);
        // Fill screen and force scrollback
        for i in 0..20 {
            backend.process_bytes(format!("Line {}\r\n", i).as_bytes());
        }
        // Scroll up
        backend.scroll(5);
        // Should not panic
        let snap = backend.snapshot();
        assert_eq!(snap.rows, 5);
    }

    #[test]
    fn reset_clears_state() {
        let mut backend = AlacrittyBackend::new(24, 80);
        backend.process_bytes(b"Some content here");
        backend.reset();

        let snap = backend.snapshot();
        // After reset, first cell should be empty (space)
        assert_eq!(snap.cells[0].character, ' ');
    }

    // ── Comprehensive VT tests ──────────────────────────────

    #[test]
    fn sgr_colors_256() {
        let mut backend = AlacrittyBackend::new(24, 80);
        // Set fg to color 196 (bright red in 256-color)
        backend.process_bytes(b"\x1b[38;5;196mX");
        let snap = backend.snapshot();
        let cell = &snap.cells[0];
        assert_eq!(cell.character, 'X');
        // Color 196 is in the 216-color cube, should have non-default fg
        assert_ne!(cell.fg, 0xFF_EB_DB_B2, "fg should not be default white");
    }

    #[test]
    fn sgr_truecolor() {
        let mut backend = AlacrittyBackend::new(24, 80);
        // Set fg to RGB(255, 128, 0) — orange
        backend.process_bytes(b"\x1b[38;2;255;128;0mO");
        let snap = backend.snapshot();
        let cell = &snap.cells[0];
        assert_eq!(cell.character, 'O');
        assert_eq!(cell.fg, 0xFF_FF_80_00, "fg should be orange #FF8000");
    }

    #[test]
    fn sgr_multiple_attributes() {
        let mut backend = AlacrittyBackend::new(24, 80);
        // Bold + Italic + Underline
        backend.process_bytes(b"\x1b[1;3;4mBIU\x1b[0m");
        let snap = backend.snapshot();
        let cell = &snap.cells[0];
        assert_eq!(cell.character, 'B');
        assert!(cell.flags & (1 << 0) != 0, "bold");
        assert!(cell.flags & (1 << 1) != 0, "italic");
        assert!(cell.flags & (1 << 2) != 0, "underline");
    }

    #[test]
    fn cursor_movement_home() {
        let mut backend = AlacrittyBackend::new(24, 80);
        backend.process_bytes(b"ABCDE\x1b[H"); // Home
        let snap = backend.snapshot();
        assert_eq!(snap.cursor.row, 0);
        assert_eq!(snap.cursor.col, 0);
    }

    #[test]
    fn cursor_state_without_snapshot() {
        let mut backend = AlacrittyBackend::new(24, 80);
        backend.process_bytes(b"\x1b[10;20H");
        let cursor = backend.cursor_state();
        assert_eq!(cursor.row, 9); // 0-indexed
        assert_eq!(cursor.col, 19);
    }

    #[test]
    fn erase_display() {
        let mut backend = AlacrittyBackend::new(24, 80);
        backend.process_bytes(b"Hello World");
        backend.process_bytes(b"\x1b[2J"); // Clear screen
        backend.process_bytes(b"\x1b[H");  // Home
        let snap = backend.snapshot();
        // Screen should be cleared
        let first_chars: String = snap.cells[..10].iter().map(|c| c.character).collect();
        assert_eq!(first_chars.trim(), "");
    }

    #[test]
    fn line_wrapping() {
        let mut backend = AlacrittyBackend::new(24, 10); // Very narrow
        backend.process_bytes(b"ABCDEFGHIJKLM"); // 13 chars in 10-col terminal
        let snap = backend.snapshot();
        // First row: ABCDEFGHIJ
        let row1: String = snap.cells[..10].iter().map(|c| c.character).collect();
        assert_eq!(row1, "ABCDEFGHIJ");
        // Second row should have KLM
        let row2: String = snap.cells[10..20].iter().map(|c| c.character).collect();
        assert!(row2.starts_with("KLM"));
    }

    #[test]
    fn newline_and_carriage_return() {
        let mut backend = AlacrittyBackend::new(24, 80);
        backend.process_bytes(b"Line1\r\nLine2\r\nLine3");
        let snap = backend.snapshot();
        let row0: String = snap.cells[..80].iter().map(|c| c.character).collect::<String>()
            .trim_end().to_string();
        let row1: String = snap.cells[80..160].iter().map(|c| c.character).collect::<String>()
            .trim_end().to_string();
        let row2: String = snap.cells[160..240].iter().map(|c| c.character).collect::<String>()
            .trim_end().to_string();
        assert_eq!(row0, "Line1");
        assert_eq!(row1, "Line2");
        assert_eq!(row2, "Line3");
    }

    #[test]
    fn unicode_basic() {
        let mut backend = AlacrittyBackend::new(24, 80);
        backend.process_bytes("café ñ 日本語".as_bytes());
        let snap = backend.snapshot();
        assert_eq!(snap.cells[0].character, 'c');
        assert_eq!(snap.cells[1].character, 'a');
        assert_eq!(snap.cells[2].character, 'f');
        assert_eq!(snap.cells[3].character, 'é');
    }

    #[test]
    fn tab_character() {
        let mut backend = AlacrittyBackend::new(24, 80);
        backend.process_bytes(b"A\tB");
        let snap = backend.snapshot();
        assert_eq!(snap.cells[0].character, 'A');
        // Tab moves to next tab stop (default every 8)
        assert_eq!(snap.cells[8].character, 'B');
    }

    #[test]
    fn alternate_screen_buffer() {
        let mut backend = AlacrittyBackend::new(24, 80);
        backend.process_bytes(b"Normal screen");
        backend.process_bytes(b"\x1b[?1049h"); // Enter alt screen (saves cursor, clears)
        backend.process_bytes(b"\x1b[HAlt screen"); // Home + write
        let snap_alt = backend.snapshot();
        let alt_row: String = snap_alt.cells[..80].iter().map(|c| c.character).collect::<String>()
            .trim_end().to_string();
        assert_eq!(alt_row, "Alt screen");

        backend.process_bytes(b"\x1b[?1049l"); // Exit alt screen (restores)
        let snap_normal = backend.snapshot();
        let normal_row: String = snap_normal.cells[..80].iter().map(|c| c.character).collect::<String>()
            .trim_end().to_string();
        assert_eq!(normal_row, "Normal screen");
    }

    #[test]
    fn bell_event() {
        let mut backend = AlacrittyBackend::new(24, 80);
        backend.process_bytes(b"\x07"); // BEL
        let events = backend.drain_events();
        assert!(events.iter().any(|e| matches!(e, crate::event::BackendEvent::Bell)));
    }

    #[test]
    fn damage_tracking() {
        let mut backend = AlacrittyBackend::new(24, 80);
        // First snapshot consumes initial full damage
        let snap1 = backend.snapshot();
        assert!(!snap1.damage.is_empty(), "Initial snapshot should have damage");

        // Write some text
        backend.process_bytes(b"Hello");
        let snap2 = backend.snapshot();
        assert!(!snap2.damage.is_empty(), "Should have damage after write");
    }

    #[test]
    fn large_output_stress() {
        let mut backend = AlacrittyBackend::new(24, 80);
        // Simulate fast output (ls -la with many files)
        let line = "drwxr-xr-x  2 user user  4096 Mar 29 04:00 directory_name\r\n";
        let bulk: Vec<u8> = line.as_bytes().repeat(1000);
        backend.process_bytes(&bulk);
        // Should not panic or OOM
        let snap = backend.snapshot();
        assert_eq!(snap.rows, 24);
        assert_eq!(snap.cols, 80);
    }

    #[test]
    fn grid_cell_count_matches_dimensions() {
        let mut backend = AlacrittyBackend::new(30, 100);
        backend.process_bytes(b"test");
        let snap = backend.snapshot();
        assert_eq!(snap.cells.len(), 30 * 100);
    }

    #[test]
    fn inverse_video() {
        let mut backend = AlacrittyBackend::new(24, 80);
        backend.process_bytes(b"\x1b[7mInverse\x1b[0m");
        let snap = backend.snapshot();
        assert!(snap.cells[0].flags & (1 << 4) != 0, "inverse flag");
    }

    #[test]
    fn dim_text() {
        let mut backend = AlacrittyBackend::new(24, 80);
        backend.process_bytes(b"\x1b[2mDim\x1b[0m");
        let snap = backend.snapshot();
        assert!(snap.cells[0].flags & (1 << 6) != 0, "dim flag");
    }

    #[test]
    fn strikethrough() {
        let mut backend = AlacrittyBackend::new(24, 80);
        backend.process_bytes(b"\x1b[9mStrike\x1b[0m");
        let snap = backend.snapshot();
        assert!(snap.cells[0].flags & (1 << 3) != 0, "strikethrough flag");
    }

    #[test]
    fn input_bytes_passthrough() {
        let mut backend = AlacrittyBackend::new(24, 80);
        let input = b"hello";
        let output = backend.input_bytes(input);
        assert_eq!(output, input);
    }

    #[test]
    fn kitty_keyboard_enabled() {
        let mut backend = AlacrittyBackend::new(24, 80);
        // Send CSI ? u to query keyboard protocol support
        // alacritty_terminal should respond via PtyWrite event
        backend.process_bytes(b"\x1b[?u");
        let writes = backend.drain_pty_writes();
        // Response should be CSI ? <flags> u
        assert!(!writes.is_empty(), "Should have PtyWrite response to keyboard query");
    }

    #[test]
    fn pty_write_drain_empty_when_no_queries() {
        let backend = AlacrittyBackend::new(24, 80);
        assert!(backend.drain_pty_writes().is_empty());
    }

    #[test]
    fn device_attributes_response() {
        let mut backend = AlacrittyBackend::new(24, 80);
        // Send DA (Device Attributes) query
        backend.process_bytes(b"\x1b[c");
        let writes = backend.drain_pty_writes();
        // Should respond with DA response
        assert!(!writes.is_empty(), "Should respond to DA query");
        // DA response starts with ESC [
        assert!(writes.starts_with(b"\x1b["), "DA response should start with CSI");
    }
}
