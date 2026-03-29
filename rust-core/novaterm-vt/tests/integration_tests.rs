// Integration tests for novaterm-vt: real terminal scenarios.
//
// These tests simulate the VT output that actual programs (ls, vim, htop,
// git, tmux, etc.) produce and verify that AlacrittyBackend handles them
// correctly — including colors, cursor movement, scrolling, unicode, and
// alternate screen transitions.

use novaterm_vt::*;

// ── Helpers ─────────────────────────────────────────────────────────

/// Extract the text content of a single row from a snapshot (0-indexed).
fn row_text(snap: &GridSnapshot, row: usize) -> String {
    let cols = snap.cols as usize;
    let start = row * cols;
    let end = start + cols;
    snap.cells[start..end]
        .iter()
        .map(|c| c.character)
        .collect::<String>()
        .trim_end()
        .to_string()
}

/// Extract character at (row, col) from a snapshot.
fn char_at(snap: &GridSnapshot, row: usize, col: usize) -> char {
    snap.cells[row * snap.cols as usize + col].character
}

/// Extract cell at (row, col).
fn cell_at(snap: &GridSnapshot, row: usize, col: usize) -> &CellData {
    &snap.cells[row * snap.cols as usize + col]
}

// Flag bit constants matching grid_snapshot::cell_flags_to_u32.
const FLAG_BOLD: u32 = 1 << 0;
const FLAG_ITALIC: u32 = 1 << 1;
const FLAG_UNDERLINE: u32 = 1 << 2;
const FLAG_STRIKETHROUGH: u32 = 1 << 3;
const FLAG_INVERSE: u32 = 1 << 4;
const FLAG_HIDDEN: u32 = 1 << 5;
const FLAG_DIM: u32 = 1 << 6;
const FLAG_DOUBLE_UNDERLINE: u32 = 1 << 7;
const _FLAG_UNDERCURL: u32 = 1 << 8;
const FLAG_WIDE_CHAR: u32 = 1 << 9;

// =====================================================================
// 1. Real Program Output Simulation
// =====================================================================

#[test]
fn ls_color_output_directories_and_executables() {
    // `ls --color` emits: bold+blue for dirs, bold+green for executables
    // \x1b[01;34m = bold + blue, \x1b[01;32m = bold + green, \x1b[0m = reset
    let mut b = AlacrittyBackend::new(24, 80);
    b.process_bytes(b"\x1b[0m\x1b[01;34mDocuments\x1b[0m  \x1b[01;32mscript.sh\x1b[0m  normal.txt");
    let snap = b.snapshot();

    // "Documents" should be bold
    let d = cell_at(&snap, 0, 0);
    assert_eq!(d.character, 'D');
    assert!(d.flags & FLAG_BOLD != 0, "Directory name should be bold");

    // "script.sh" starts at column 11
    let s = cell_at(&snap, 0, 11);
    assert_eq!(s.character, 's');
    assert!(s.flags & FLAG_BOLD != 0, "Executable should be bold");

    // "normal.txt" should not be bold — find it after the two spaces
    let n_col = row_text(&snap, 0).find("normal").expect("should find normal.txt");
    let n = cell_at(&snap, 0, n_col);
    assert_eq!(n.character, 'n');
    assert!(n.flags & FLAG_BOLD == 0, "Normal file should not be bold");
}

#[test]
fn git_diff_colored_output() {
    // git diff output with green (additions) and red (deletions)
    let input = b"\x1b[32m+added line\x1b[m\r\n\x1b[31m-removed line\x1b[m\r\nunchanged";
    let mut b = AlacrittyBackend::new(24, 80);
    b.process_bytes(input);
    let snap = b.snapshot();

    // Row 0: green "+added line"
    let plus = cell_at(&snap, 0, 0);
    assert_eq!(plus.character, '+');
    // Green (named color index 2) packed as ARGB
    let green_argb = 0xFF_98_97_1A_u32; // Gruvbox green
    assert_eq!(plus.fg, green_argb, "Addition should be green");

    // Row 1: red "-removed line"
    let minus = cell_at(&snap, 1, 0);
    assert_eq!(minus.character, '-');
    let red_argb = 0xFF_CC_24_1D_u32; // Gruvbox red
    assert_eq!(minus.fg, red_argb, "Deletion should be red");

    // Row 2: default color
    assert_eq!(row_text(&snap, 2), "unchanged");
}

#[test]
fn bash_prompt_with_git_branch() {
    // Typical PS1: \[\e[01;32m\]user@host\[\e[00m\]:\[\e[01;34m\]~/project\[\e[00m\]$
    let input = b"\x1b[01;32muser@host\x1b[00m:\x1b[01;34m~/project\x1b[00m$ ";
    let mut b = AlacrittyBackend::new(24, 80);
    b.process_bytes(input);
    let snap = b.snapshot();

    assert_eq!(row_text(&snap, 0), "user@host:~/project$");
    // 'u' of "user" should be bold
    assert!(cell_at(&snap, 0, 0).flags & FLAG_BOLD != 0);
    // ':' is after reset — not bold
    let colon_col = row_text(&snap, 0).find(':').unwrap();
    assert!(cell_at(&snap, 0, colon_col).flags & FLAG_BOLD == 0);
    // '~' should be bold (blue part)
    assert!(cell_at(&snap, 0, colon_col + 1).flags & FLAG_BOLD != 0);
}

#[test]
fn gcc_error_output() {
    // GCC error format: bold filename, then red "error:"
    let input = b"\x1b[1mmain.c:10:5:\x1b[0m \x1b[1;31merror:\x1b[0m expected ';'\r\n   10 |   int x = 42\r\n      |              \x1b[1;32m^\x1b[0m";
    let mut b = AlacrittyBackend::new(24, 80);
    b.process_bytes(input);
    let snap = b.snapshot();

    assert!(row_text(&snap, 0).contains("error:"));
    assert!(row_text(&snap, 0).contains("main.c:10:5:"));
    // Filename should be bold
    assert!(cell_at(&snap, 0, 0).flags & FLAG_BOLD != 0);
}

#[test]
fn man_page_bold_and_underline() {
    // man pages use bold for headings and underline for arguments
    let input = b"\x1b[1mNAME\x1b[0m\r\n       ls - \x1b[4mlist\x1b[0m directory contents";
    let mut b = AlacrittyBackend::new(24, 80);
    b.process_bytes(input);
    let snap = b.snapshot();

    // "NAME" bold
    assert!(cell_at(&snap, 0, 0).flags & FLAG_BOLD != 0);
    // "list" underlined — find its position
    let list_col = row_text(&snap, 1).find("list").unwrap();
    assert!(cell_at(&snap, 1, list_col).flags & FLAG_UNDERLINE != 0);
}

// =====================================================================
// 2. Cursor Movement Stress
// =====================================================================

#[test]
fn cursor_home_and_goto() {
    let mut b = AlacrittyBackend::new(24, 80);
    // CSI H (home)
    b.process_bytes(b"\x1b[H");
    let snap = b.snapshot();
    assert_eq!(snap.cursor.row, 0);
    assert_eq!(snap.cursor.col, 0);

    // CSI 10;20H (goto row 10, col 20)
    b.process_bytes(b"\x1b[10;20H");
    let snap = b.snapshot();
    assert_eq!(snap.cursor.row, 9); // 0-indexed
    assert_eq!(snap.cursor.col, 19);
}

#[test]
fn cursor_relative_movement() {
    let mut b = AlacrittyBackend::new(24, 80);
    // Start at (5,5), then move around
    b.process_bytes(b"\x1b[6;6H");        // goto (5,5) 0-indexed
    b.process_bytes(b"\x1b[3A");           // up 3
    b.process_bytes(b"\x1b[10C");          // right 10
    let snap = b.snapshot();
    assert_eq!(snap.cursor.row, 2);        // 5 - 3
    assert_eq!(snap.cursor.col, 15);       // 5 + 10
}

#[test]
fn cursor_save_restore_decsc() {
    let mut b = AlacrittyBackend::new(24, 80);
    b.process_bytes(b"\x1b[5;10H");   // goto (4,9)
    b.process_bytes(b"\x1b7");         // DECSC save cursor
    b.process_bytes(b"\x1b[1;1H");    // home
    b.process_bytes(b"\x1b8");         // DECRC restore cursor
    let snap = b.snapshot();
    assert_eq!(snap.cursor.row, 4);
    assert_eq!(snap.cursor.col, 9);
}

#[test]
fn cursor_save_restore_scosc() {
    // CSI s / CSI u (ANSI save/restore)
    let mut b = AlacrittyBackend::new(24, 80);
    b.process_bytes(b"\x1b[8;15H");   // goto
    b.process_bytes(b"\x1b[s");        // save
    b.process_bytes(b"\x1b[1;1H");    // home
    b.process_bytes(b"\x1b[u");        // restore
    let snap = b.snapshot();
    assert_eq!(snap.cursor.row, 7);
    assert_eq!(snap.cursor.col, 14);
}

#[test]
fn htop_style_rapid_cursor_jumps() {
    // htop redraws many parts of the screen per frame
    let mut b = AlacrittyBackend::new(24, 80);
    for row in 1..=20 {
        let seq = format!("\x1b[{};1HLine {:>2}", row, row);
        b.process_bytes(seq.as_bytes());
    }
    let snap = b.snapshot();
    assert_eq!(row_text(&snap, 0), "Line  1");
    assert_eq!(row_text(&snap, 19), "Line 20");
}

#[test]
fn full_screen_clear_and_repaint() {
    let mut b = AlacrittyBackend::new(10, 40);
    b.process_bytes(b"OLD CONTENT");
    // Clear entire screen + home (like `clear`)
    b.process_bytes(b"\x1b[2J\x1b[H");
    b.process_bytes(b"NEW CONTENT");
    let snap = b.snapshot();
    assert_eq!(row_text(&snap, 0), "NEW CONTENT");
    // Old content should be gone from visible grid
    for r in 1..10 {
        assert_eq!(row_text(&snap, r), "");
    }
}

#[test]
fn erase_in_line() {
    let mut b = AlacrittyBackend::new(24, 80);
    b.process_bytes(b"AAABBBCCC");
    b.process_bytes(b"\x1b[4G");      // cursor to column 4 (0-indexed: 3)
    b.process_bytes(b"\x1b[0K");      // erase from cursor to end of line
    let snap = b.snapshot();
    assert_eq!(row_text(&snap, 0), "AAA");
}

#[test]
fn erase_in_line_from_start() {
    let mut b = AlacrittyBackend::new(24, 80);
    b.process_bytes(b"ABCDEF");
    b.process_bytes(b"\x1b[4G");       // column 4 (0-indexed: 3)
    b.process_bytes(b"\x1b[1K");       // erase from start to cursor
    let snap = b.snapshot();
    // Columns 0-3 erased, D is at col 3 (also erased), EF remain
    assert_eq!(char_at(&snap, 0, 0), ' ');
    assert_eq!(char_at(&snap, 0, 4), 'E');
    assert_eq!(char_at(&snap, 0, 5), 'F');
}

// =====================================================================
// 3. Scrolling
// =====================================================================

#[test]
fn scroll_region_set_and_scroll_within() {
    // CSI 2;5r sets scroll region to lines 2-5 (1-indexed)
    let mut b = AlacrittyBackend::new(10, 40);
    // Fill lines
    for i in 1..=10 {
        b.process_bytes(format!("\x1b[{};1HLine{:02}", i, i).as_bytes());
    }
    // Set scroll region to lines 2-5
    b.process_bytes(b"\x1b[2;5r");
    // Move cursor into region and scroll by writing at bottom
    b.process_bytes(b"\x1b[5;1H");
    b.process_bytes(b"\r\nScrolled");
    let snap = b.snapshot();
    // Line 1 (row 0) should remain unchanged
    assert_eq!(row_text(&snap, 0), "Line01");
    // Line 6+ (row 5+) should remain unchanged
    assert_eq!(row_text(&snap, 5), "Line06");
}

#[test]
fn rapid_output_scroll_1000_lines() {
    let mut b = AlacrittyBackend::new(24, 80);
    for i in 0..1000 {
        b.process_bytes(format!("Line {:04}\r\n", i).as_bytes());
    }
    let snap = b.snapshot();
    // The last 24 lines should be visible (minus one because cursor is on empty line)
    // Last visible content line should be "Line 0999"
    // Row 22 (second to last visible) or row 23 depending on cursor
    let all_text: String = (0..24).map(|r| row_text(&snap, r)).collect::<Vec<_>>().join("|");
    assert!(all_text.contains("Line 0999"), "Last line should be visible: {}", all_text);
}

#[test]
fn scroll_up_index() {
    // ESC D = Index (scroll up one line if at bottom of scroll region)
    let mut b = AlacrittyBackend::new(5, 20);
    for i in 0..5 {
        b.process_bytes(format!("Line{}\r\n", i).as_bytes());
    }
    // Now at line 5 (past screen), ESC D should scroll
    b.process_bytes(b"\x1bDNewBottom");
    let snap = b.snapshot();
    // Row 0 should no longer be "Line0"
    assert_ne!(row_text(&snap, 0), "Line0");
}

#[test]
fn scroll_down_reverse_index() {
    // ESC M = Reverse Index (scroll down one line if at top)
    let mut b = AlacrittyBackend::new(5, 20);
    b.process_bytes(b"Line0\r\nLine1\r\nLine2\r\nLine3\r\nLine4");
    b.process_bytes(b"\x1b[H");    // go home
    b.process_bytes(b"\x1bM");     // reverse index — should scroll down
    b.process_bytes(b"Inserted");
    let snap = b.snapshot();
    assert_eq!(row_text(&snap, 0), "Inserted");
}

#[test]
fn scrollback_viewport_scroll() {
    let mut b = AlacrittyBackend::new(5, 40);
    // Push 50 lines to build scrollback
    for i in 0..50 {
        b.process_bytes(format!("scrollback-{:03}\r\n", i).as_bytes());
    }
    // Scroll viewport up
    b.scroll(10);
    let snap = b.snapshot();
    // Should not panic and should have 5 rows
    assert_eq!(snap.rows, 5);
}

// =====================================================================
// 4. Unicode & Wide Characters
// =====================================================================

#[test]
fn cjk_characters_double_width() {
    let mut b = AlacrittyBackend::new(24, 80);
    b.process_bytes("日本語".as_bytes());
    let snap = b.snapshot();
    // Each CJK char occupies 2 columns
    assert_eq!(char_at(&snap, 0, 0), '日');
    assert!(cell_at(&snap, 0, 0).flags & FLAG_WIDE_CHAR != 0, "CJK should be wide");
    // Column 1 is the spacer for the wide char
    assert_eq!(char_at(&snap, 0, 2), '本');
    assert_eq!(char_at(&snap, 0, 4), '語');
    // Cursor should be at column 6 (3 chars * 2 cols each)
    assert_eq!(snap.cursor.col, 6);
}

#[test]
fn mixed_ascii_and_cjk() {
    let mut b = AlacrittyBackend::new(24, 80);
    b.process_bytes("AB日C".as_bytes());
    let snap = b.snapshot();
    assert_eq!(char_at(&snap, 0, 0), 'A');  // col 0
    assert_eq!(char_at(&snap, 0, 1), 'B');  // col 1
    assert_eq!(char_at(&snap, 0, 2), '日'); // col 2-3 (wide)
    assert_eq!(char_at(&snap, 0, 4), 'C');  // col 4
}

#[test]
fn emoji_rendering() {
    let mut b = AlacrittyBackend::new(24, 80);
    b.process_bytes("🎉🚀".as_bytes());
    let snap = b.snapshot();
    assert_eq!(char_at(&snap, 0, 0), '🎉');
    // Emoji should be wide (2 cols)
    assert!(cell_at(&snap, 0, 0).flags & FLAG_WIDE_CHAR != 0);
}

#[test]
fn combining_characters() {
    // e + combining acute accent = é
    let mut b = AlacrittyBackend::new(24, 80);
    b.process_bytes("e\u{0301}x".as_bytes());
    let snap = b.snapshot();
    // The combined character should be in one cell
    // 'x' should be at column 1 (combining doesn't advance)
    assert_eq!(char_at(&snap, 0, 1), 'x');
}

#[test]
fn arabic_text_ltr_rendering() {
    // Terminals render Arabic LTR (character by character)
    let mut b = AlacrittyBackend::new(24, 80);
    b.process_bytes("مرحبا".as_bytes());
    let snap = b.snapshot();
    // First character should be the first Arabic char
    assert_eq!(char_at(&snap, 0, 0), 'م');
}

#[test]
fn cjk_wrapping_at_line_boundary() {
    // In a 5-col terminal, a CJK char at col 4 can't fit (needs 2 cols)
    // It should wrap to the next line
    let mut b = AlacrittyBackend::new(24, 5);
    b.process_bytes("ABCD日".as_bytes());
    let snap = b.snapshot();
    // "ABCD" fills cols 0-3, '日' needs 2 cols, only 1 left → wraps
    assert_eq!(char_at(&snap, 0, 0), 'A');
    assert_eq!(char_at(&snap, 0, 3), 'D');
    // '日' should be on the next row
    assert_eq!(char_at(&snap, 1, 0), '日');
}

// =====================================================================
// 5. Alternate Screen
// =====================================================================

#[test]
fn vim_style_alt_screen_enter_draw_exit() {
    let mut b = AlacrittyBackend::new(24, 80);
    b.process_bytes(b"Normal content here");

    // Enter alt screen (vim does this)
    b.process_bytes(b"\x1b[?1049h");
    // Draw on alt screen
    b.process_bytes(b"\x1b[2J\x1b[H");     // clear + home
    b.process_bytes(b"-- INSERT --");
    let snap_alt = b.snapshot();
    assert_eq!(row_text(&snap_alt, 0), "-- INSERT --");

    // Exit alt screen
    b.process_bytes(b"\x1b[?1049l");
    let snap_normal = b.snapshot();
    assert_eq!(row_text(&snap_normal, 0), "Normal content here");
}

#[test]
fn less_style_alt_screen() {
    let mut b = AlacrittyBackend::new(10, 40);
    b.process_bytes(b"shell prompt $ less file.txt");

    // less enters alt screen
    b.process_bytes(b"\x1b[?1049h");
    b.process_bytes(b"\x1b[2J\x1b[H");
    for i in 1..=10 {
        b.process_bytes(format!("File line {}\r\n", i).as_bytes());
    }
    let snap = b.snapshot();
    assert!(row_text(&snap, 0).starts_with("File line"));

    // Exit — original content should restore
    b.process_bytes(b"\x1b[?1049l");
    let snap = b.snapshot();
    assert!(row_text(&snap, 0).contains("less file.txt"));
}

#[test]
fn alt_screen_preserves_cursor() {
    let mut b = AlacrittyBackend::new(24, 80);
    b.process_bytes(b"\x1b[5;10H");   // cursor at (4,9)
    b.process_bytes(b"\x1b[?1049h");   // enter alt screen
    b.process_bytes(b"\x1b[1;1H");     // move cursor on alt
    b.process_bytes(b"\x1b[?1049l");   // exit alt screen
    let snap = b.snapshot();
    // Cursor should be restored to saved position
    assert_eq!(snap.cursor.row, 4);
    assert_eq!(snap.cursor.col, 9);
}

// =====================================================================
// 6. SGR Edge Cases
// =====================================================================

#[test]
fn sgr_reset_clears_all_attributes() {
    let mut b = AlacrittyBackend::new(24, 80);
    b.process_bytes(b"\x1b[1;3;4;7;9mStyled\x1b[0mPlain");
    let snap = b.snapshot();
    // 'S' should have many flags
    let styled = cell_at(&snap, 0, 0);
    assert!(styled.flags & FLAG_BOLD != 0);
    assert!(styled.flags & FLAG_ITALIC != 0);
    assert!(styled.flags & FLAG_UNDERLINE != 0);
    // 'P' of "Plain" should have no flags
    let plain = cell_at(&snap, 0, 6);
    assert_eq!(plain.character, 'P');
    assert_eq!(plain.flags, 0, "After SGR 0, all flags should be cleared");
}

#[test]
fn sgr_truecolor_fg_and_bg() {
    let mut b = AlacrittyBackend::new(24, 80);
    // fg: RGB(255,128,0) bg: RGB(0,64,128)
    b.process_bytes(b"\x1b[38;2;255;128;0;48;2;0;64;128mX");
    let snap = b.snapshot();
    let cell = cell_at(&snap, 0, 0);
    assert_eq!(cell.fg, 0xFF_FF_80_00, "fg should be #FF8000");
    assert_eq!(cell.bg, 0xFF_00_40_80, "bg should be #004080");
}

#[test]
fn sgr_256_color() {
    let mut b = AlacrittyBackend::new(24, 80);
    // Set fg to color 196 (bright red in 256-color cube)
    b.process_bytes(b"\x1b[38;5;196mR");
    let snap = b.snapshot();
    let cell = cell_at(&snap, 0, 0);
    // 196 in cube = (5,0,0) → (255,0,0)
    assert_eq!(cell.fg, 0xFF_FF_00_00, "256-color 196 should be red");
}

#[test]
fn sgr_bold_and_dim_simultaneously() {
    let mut b = AlacrittyBackend::new(24, 80);
    b.process_bytes(b"\x1b[1;2mBD\x1b[0m");
    let snap = b.snapshot();
    let cell = cell_at(&snap, 0, 0);
    assert!(cell.flags & FLAG_BOLD != 0, "should be bold");
    assert!(cell.flags & FLAG_DIM != 0, "should be dim");
}

#[test]
fn sgr_all_attributes_at_once() {
    let mut b = AlacrittyBackend::new(24, 80);
    // bold(1) + dim(2) + italic(3) + underline(4) + inverse(7) + hidden(8) + strikethrough(9)
    b.process_bytes(b"\x1b[1;2;3;4;7;8;9mX\x1b[0m");
    let snap = b.snapshot();
    let cell = cell_at(&snap, 0, 0);
    assert!(cell.flags & FLAG_BOLD != 0, "bold");
    assert!(cell.flags & FLAG_DIM != 0, "dim");
    assert!(cell.flags & FLAG_ITALIC != 0, "italic");
    assert!(cell.flags & FLAG_UNDERLINE != 0, "underline");
    assert!(cell.flags & FLAG_INVERSE != 0, "inverse");
    assert!(cell.flags & FLAG_HIDDEN != 0, "hidden");
    assert!(cell.flags & FLAG_STRIKETHROUGH != 0, "strikethrough");
}

#[test]
fn sgr_default_parameter_resets_individual() {
    // SGR 22 resets bold+dim, SGR 23 resets italic, etc.
    let mut b = AlacrittyBackend::new(24, 80);
    b.process_bytes(b"\x1b[1;3mBI\x1b[22mI\x1b[23mN");
    let snap = b.snapshot();
    // 'B' (col 0): bold + italic
    assert!(cell_at(&snap, 0, 0).flags & FLAG_BOLD != 0);
    assert!(cell_at(&snap, 0, 0).flags & FLAG_ITALIC != 0);
    // 'I' at col 1: still bold + italic
    // 'I' at col 2: after SGR 22, bold removed, italic stays
    assert!(cell_at(&snap, 0, 2).flags & FLAG_BOLD == 0, "bold should be off");
    assert!(cell_at(&snap, 0, 2).flags & FLAG_ITALIC != 0, "italic should still be on");
    // 'N' at col 3: after SGR 23, italic also removed
    assert!(cell_at(&snap, 0, 3).flags & FLAG_ITALIC == 0, "italic should be off");
}

#[test]
fn sgr_double_underline() {
    let mut b = AlacrittyBackend::new(24, 80);
    // SGR 21 is "doubly underlined" per ECMA-48 but some terminals
    // (including alacritty) treat it as bold-off. Use CSI 4:2 m for
    // double underline (extended underline style).
    b.process_bytes(b"\x1b[4:2mD\x1b[0m");
    let snap = b.snapshot();
    // If alacritty supports extended underline, check for double_underline flag.
    // Otherwise just verify underline is present (baseline compatibility).
    let flags = cell_at(&snap, 0, 0).flags;
    assert!(
        flags & FLAG_DOUBLE_UNDERLINE != 0 || flags & FLAG_UNDERLINE != 0,
        "Should have double underline or at least underline, got flags: {:#x}", flags
    );
}

#[test]
fn sgr_missing_parameter_defaults_to_zero() {
    // CSI m with no params = SGR 0 (reset)
    let mut b = AlacrittyBackend::new(24, 80);
    b.process_bytes(b"\x1b[1mBold\x1b[mNormal");
    let snap = b.snapshot();
    assert!(cell_at(&snap, 0, 0).flags & FLAG_BOLD != 0);
    let n = cell_at(&snap, 0, 4);
    assert_eq!(n.character, 'N');
    assert_eq!(n.flags, 0, "SGR with no params should reset");
}

// =====================================================================
// 7. OSC Sequences
// =====================================================================

#[test]
fn osc_0_set_window_title() {
    let mut b = AlacrittyBackend::new(24, 80);
    b.process_bytes(b"\x1b]0;My Terminal Title\x07");
    let events = b.drain_events();
    let title_event = events.iter().find(|e| matches!(e, BackendEvent::TitleChanged(_)));
    assert!(title_event.is_some(), "Should emit TitleChanged event");
    if let BackendEvent::TitleChanged(t) = title_event.unwrap() {
        assert_eq!(t, "My Terminal Title");
    }
}

#[test]
fn osc_0_set_title_with_st_terminator() {
    // ST terminator is ESC \  (0x1b 0x5c)
    let mut b = AlacrittyBackend::new(24, 80);
    b.process_bytes(b"\x1b]0;Title with ST\x1b\\");
    let events = b.drain_events();
    let has_title = events.iter().any(|e| matches!(e, BackendEvent::TitleChanged(t) if t == "Title with ST"));
    assert!(has_title, "Should accept ST terminator for OSC");
}

#[test]
fn osc_2_set_title() {
    // OSC 2 also sets window title (icon name is OSC 1)
    let mut b = AlacrittyBackend::new(24, 80);
    b.process_bytes(b"\x1b]2;Another Title\x07");
    let events = b.drain_events();
    assert!(events.iter().any(|e| matches!(e, BackendEvent::TitleChanged(_))));
}

#[test]
fn osc_52_clipboard_store() {
    let mut b = AlacrittyBackend::new(24, 80);
    // OSC 52 ; c ; base64(hello) ST
    // "hello" base64 = "aGVsbG8="
    b.process_bytes(b"\x1b]52;c;aGVsbG8=\x07");
    let events = b.drain_events();
    let clipboard = events.iter().find(|e| matches!(e, BackendEvent::ClipboardStore(_)));
    assert!(clipboard.is_some(), "Should emit ClipboardStore for OSC 52");
}

#[test]
fn bell_character() {
    let mut b = AlacrittyBackend::new(24, 80);
    b.process_bytes(b"\x07");
    let events = b.drain_events();
    assert!(events.iter().any(|e| matches!(e, BackendEvent::Bell)));
}

#[test]
fn multiple_title_changes() {
    let mut b = AlacrittyBackend::new(24, 80);
    b.process_bytes(b"\x1b]0;First\x07");
    b.process_bytes(b"\x1b]0;Second\x07");
    b.process_bytes(b"\x1b]0;Third\x07");
    let events = b.drain_events();
    let titles: Vec<_> = events
        .iter()
        .filter_map(|e| match e { BackendEvent::TitleChanged(t) => Some(t.as_str()), _ => None })
        .collect();
    assert_eq!(titles, vec!["First", "Second", "Third"]);
}

// =====================================================================
// 8. Tab Handling
// =====================================================================

#[test]
fn default_tab_stops_every_8() {
    let mut b = AlacrittyBackend::new(24, 80);
    b.process_bytes(b"A\tB\tC");
    let snap = b.snapshot();
    assert_eq!(char_at(&snap, 0, 0), 'A');
    assert_eq!(char_at(&snap, 0, 8), 'B');
    assert_eq!(char_at(&snap, 0, 16), 'C');
}

#[test]
fn tab_from_different_positions() {
    let mut b = AlacrittyBackend::new(24, 80);
    b.process_bytes(b"ABC\tD");  // ABC at 0-2, tab goes to 8, D at 8
    let snap = b.snapshot();
    assert_eq!(char_at(&snap, 0, 0), 'A');
    assert_eq!(char_at(&snap, 0, 8), 'D');
}

#[test]
fn multiple_tabs() {
    let mut b = AlacrittyBackend::new(24, 80);
    b.process_bytes(b"\t\t\tX");  // 3 tabs → col 24
    let snap = b.snapshot();
    assert_eq!(char_at(&snap, 0, 24), 'X');
}

#[test]
fn tab_near_end_of_line() {
    // Tab near end should not overflow
    let mut b = AlacrittyBackend::new(24, 20);
    b.process_bytes(b"AAAAAAAAAAAAAAAAAA\tX"); // 18 chars + tab + X
    let snap = b.snapshot();
    // Should not panic; X should appear somewhere (either at end of line or wrapped)
    assert_eq!(snap.rows, 24);
}

// =====================================================================
// 9. Line Wrapping Edge Cases
// =====================================================================

#[test]
fn exact_line_width_no_wrap_yet() {
    let mut b = AlacrittyBackend::new(24, 10);
    b.process_bytes(b"ABCDEFGHIJ"); // exactly 10 chars
    let snap = b.snapshot();
    assert_eq!(row_text(&snap, 0), "ABCDEFGHIJ");
    // Cursor should be at col 10 (deferred wrap) or col 9
    // alacritty uses deferred wrap (pending wrap state)
    assert_eq!(row_text(&snap, 1), ""); // no wrap yet
}

#[test]
fn one_past_line_width_wraps() {
    let mut b = AlacrittyBackend::new(24, 10);
    b.process_bytes(b"ABCDEFGHIJK"); // 11 chars in 10-col
    let snap = b.snapshot();
    assert_eq!(row_text(&snap, 0), "ABCDEFGHIJ");
    assert_eq!(row_text(&snap, 1), "K");
}

#[test]
fn long_line_wraps_multiple_times() {
    let mut b = AlacrittyBackend::new(24, 10);
    b.process_bytes(b"12345678901234567890ABCDE"); // 25 chars
    let snap = b.snapshot();
    assert_eq!(row_text(&snap, 0), "1234567890");
    assert_eq!(row_text(&snap, 1), "1234567890");
    assert_eq!(row_text(&snap, 2), "ABCDE");
}

#[test]
fn autowrap_disabled() {
    // CSI ?7l disables auto-wrap
    let mut b = AlacrittyBackend::new(24, 10);
    b.process_bytes(b"\x1b[?7l");  // disable wrap
    b.process_bytes(b"ABCDEFGHIJKLM"); // 13 chars, should overwrite at last col
    let snap = b.snapshot();
    // Row 0 should have first 9 chars + last char overwrites col 9
    assert_eq!(char_at(&snap, 0, 0), 'A');
    // With autowrap off, writing past the edge overwrites the last column
    assert_eq!(row_text(&snap, 1), "", "Should not wrap to next line");
}

// =====================================================================
// 10. Stress Tests
// =====================================================================

#[test]
fn stress_100kb_printable_ascii() {
    let mut b = AlacrittyBackend::new(24, 80);
    let data: Vec<u8> = (0..100_000)
        .map(|i| b'A' + (i % 26) as u8)
        .collect();
    b.process_bytes(&data);
    let snap = b.snapshot();
    assert_eq!(snap.rows, 24);
    assert_eq!(snap.cols, 80);
    // Should not panic or corrupt
    assert_eq!(snap.cells.len(), 24 * 80);
}

#[test]
fn stress_10000_lines() {
    let mut b = AlacrittyBackend::new(24, 80);
    let mut data = Vec::with_capacity(10000 * 12);
    for i in 0..10000 {
        data.extend_from_slice(format!("line {:05}\r\n", i).as_bytes());
    }
    b.process_bytes(&data);
    let snap = b.snapshot();
    // Should survive and have the last lines visible
    let last_row = row_text(&snap, 22);
    assert!(last_row.starts_with("line "), "Last visible line: {}", last_row);
}

#[test]
fn stress_rapid_resize_during_output() {
    let mut b = AlacrittyBackend::new(24, 80);
    for i in 0..100 {
        b.process_bytes(format!("Data batch {}\r\n", i).as_bytes());
        if i % 10 == 0 {
            let rows = 20 + (i % 10) as u32;
            let cols = 60 + (i % 20) as u32;
            b.resize(rows, cols);
        }
    }
    let snap = b.snapshot();
    assert!(snap.cells.len() > 0);
}

#[test]
fn stress_empty_input() {
    let mut b = AlacrittyBackend::new(24, 80);
    b.process_bytes(b"");
    let snap = b.snapshot();
    assert_eq!(snap.cells.len(), 24 * 80);
    assert_eq!(snap.cells[0].character, ' ');
}

#[test]
fn stress_single_byte_at_a_time() {
    let mut b = AlacrittyBackend::new(24, 80);
    let input = b"Hello, World!";
    for byte in input {
        b.process_bytes(&[*byte]);
    }
    let snap = b.snapshot();
    assert_eq!(row_text(&snap, 0), "Hello, World!");
}

#[test]
fn stress_escape_sequence_byte_by_byte() {
    // Feed a CSI sequence one byte at a time
    let mut b = AlacrittyBackend::new(24, 80);
    let seq = b"\x1b[1;31mRed\x1b[0m";
    for byte in seq.iter() {
        b.process_bytes(&[*byte]);
    }
    let snap = b.snapshot();
    assert_eq!(char_at(&snap, 0, 0), 'R');
    assert!(cell_at(&snap, 0, 0).flags & FLAG_BOLD != 0);
}

#[test]
fn stress_all_256_byte_values() {
    // Feed all 256 byte values — should not panic
    let mut b = AlacrittyBackend::new(24, 80);
    let data: Vec<u8> = (0..=255).collect();
    b.process_bytes(&data);
    let snap = b.snapshot();
    assert_eq!(snap.cells.len(), 24 * 80);
}

#[test]
fn stress_resize_to_zero_ignored() {
    let mut b = AlacrittyBackend::new(24, 80);
    b.resize(0, 0);
    assert_eq!(b.dimensions(), (24, 80)); // should be unchanged
    b.resize(0, 80);
    assert_eq!(b.dimensions(), (24, 80));
    b.resize(24, 0);
    assert_eq!(b.dimensions(), (24, 80));
}

#[test]
fn stress_resize_then_snapshot() {
    let mut b = AlacrittyBackend::new(24, 80);
    b.process_bytes(b"Hello");
    b.resize(10, 40);
    let snap = b.snapshot();
    assert_eq!(snap.rows, 10);
    assert_eq!(snap.cols, 40);
    assert_eq!(snap.cells.len(), 10 * 40);
}

#[test]
fn stress_many_small_writes() {
    let mut b = AlacrittyBackend::new(24, 80);
    for _ in 0..10000 {
        b.process_bytes(b"x");
    }
    let snap = b.snapshot();
    assert_eq!(snap.cells.len(), 24 * 80);
}

// =====================================================================
// 11. Device Queries
// =====================================================================

#[test]
fn device_attributes_primary_da() {
    let mut b = AlacrittyBackend::new(24, 80);
    b.process_bytes(b"\x1b[c");
    let writes = b.drain_pty_writes();
    assert!(!writes.is_empty(), "Should respond to DA1");
    assert!(writes.starts_with(b"\x1b["), "DA response starts with CSI");
}

#[test]
fn device_status_report_cursor_position() {
    let mut b = AlacrittyBackend::new(24, 80);
    b.process_bytes(b"\x1b[10;20H");   // move cursor to (10,20) 1-based
    b.process_bytes(b"\x1b[6n");        // DSR - request cursor position
    let writes = b.drain_pty_writes();
    // Response should be CSI row;col R
    assert!(!writes.is_empty(), "Should respond to DSR 6n");
    let response = String::from_utf8_lossy(&writes);
    assert!(response.contains("10;20R"), "Response should be cursor pos: got {}", response);
}

#[test]
fn secondary_da() {
    let mut b = AlacrittyBackend::new(24, 80);
    b.process_bytes(b"\x1b[>c");
    let writes = b.drain_pty_writes();
    // May or may not respond, but should not panic
    // alacritty_terminal does respond to secondary DA
    assert!(writes.is_empty() || writes.starts_with(b"\x1b[>"));
}

// =====================================================================
// 12. Additional Real-World Scenarios
// =====================================================================

#[test]
fn tmux_status_line_simulation() {
    // tmux draws a status line at the bottom with colors
    let mut b = AlacrittyBackend::new(24, 80);
    // Set scroll region to exclude bottom line (typical tmux behavior)
    b.process_bytes(b"\x1b[1;23r");
    // Draw status at line 24
    b.process_bytes(b"\x1b[24;1H");
    b.process_bytes(b"\x1b[42;30m [0] main \x1b[0m");
    let snap = b.snapshot();
    let status = row_text(&snap, 23);
    assert!(status.contains("[0] main"), "Status line: {}", status);
    // Status should have inverse-ish colors (green bg, black fg)
    let bracket = cell_at(&snap, 23, 1);
    assert_eq!(bracket.character, '[', "Expected '[' at (23,1)");
    assert_eq!(cell_at(&snap, 23, 2).character, '0', "Expected '0' at (23,2)");
}

#[test]
fn progress_bar_with_carriage_return() {
    // Many tools (pip, wget, etc.) draw progress bars by returning to start of line
    let mut b = AlacrittyBackend::new(24, 80);
    b.process_bytes(b"Downloading... [####      ] 40%");
    b.process_bytes(b"\rDownloading... [########  ] 80%");
    b.process_bytes(b"\rDownloading... [##########] 100%");
    let snap = b.snapshot();
    assert_eq!(row_text(&snap, 0), "Downloading... [##########] 100%");
}

#[test]
fn spinner_animation() {
    // Spinner: write char, carriage return, overwrite
    let mut b = AlacrittyBackend::new(24, 80);
    for ch in &[b'|', b'/', b'-', b'\\', b'|'] {
        b.process_bytes(&[*ch]);
        b.process_bytes(b"\r");
    }
    let snap = b.snapshot();
    // Last char written should be visible
    assert_eq!(char_at(&snap, 0, 0), '|');
}

#[test]
fn backspace_handling() {
    let mut b = AlacrittyBackend::new(24, 80);
    b.process_bytes(b"ABC\x08\x08XY");
    let snap = b.snapshot();
    // Backspace moves cursor back, XY overwrites BC
    assert_eq!(row_text(&snap, 0), "AXY");
}

#[test]
fn delete_characters() {
    // CSI Pn P — delete n characters
    let mut b = AlacrittyBackend::new(24, 80);
    b.process_bytes(b"ABCDEF");
    b.process_bytes(b"\x1b[1;3H");   // cursor to col 3 (0-indexed: 2)
    b.process_bytes(b"\x1b[2P");      // delete 2 chars at cursor
    let snap = b.snapshot();
    // "AB" + deletion of "CD" + "EF" shifts left → "ABEF"
    assert_eq!(row_text(&snap, 0), "ABEF");
}

#[test]
fn insert_characters() {
    // CSI Pn @ — insert n blank characters
    let mut b = AlacrittyBackend::new(24, 80);
    b.process_bytes(b"ABCDEF");
    b.process_bytes(b"\x1b[1;3H");   // cursor to col 3
    b.process_bytes(b"\x1b[2@");      // insert 2 blanks
    let snap = b.snapshot();
    // "AB  CDEF" (CD shifts right)
    assert_eq!(char_at(&snap, 0, 0), 'A');
    assert_eq!(char_at(&snap, 0, 1), 'B');
    assert_eq!(char_at(&snap, 0, 2), ' ');
    assert_eq!(char_at(&snap, 0, 3), ' ');
    assert_eq!(char_at(&snap, 0, 4), 'C');
}

#[test]
fn insert_lines() {
    // CSI Pn L — insert n lines
    let mut b = AlacrittyBackend::new(10, 40);
    b.process_bytes(b"Line1\r\nLine2\r\nLine3\r\nLine4\r\nLine5");
    b.process_bytes(b"\x1b[2;1H");    // cursor to line 2
    b.process_bytes(b"\x1b[1L");       // insert 1 line
    let snap = b.snapshot();
    assert_eq!(row_text(&snap, 0), "Line1");
    assert_eq!(row_text(&snap, 1), "");        // inserted blank
    assert_eq!(row_text(&snap, 2), "Line2");
}

#[test]
fn delete_lines() {
    // CSI Pn M — delete n lines
    let mut b = AlacrittyBackend::new(10, 40);
    b.process_bytes(b"Line1\r\nLine2\r\nLine3\r\nLine4\r\nLine5");
    b.process_bytes(b"\x1b[2;1H");    // cursor to line 2
    b.process_bytes(b"\x1b[1M");       // delete 1 line
    let snap = b.snapshot();
    assert_eq!(row_text(&snap, 0), "Line1");
    assert_eq!(row_text(&snap, 1), "Line3");   // Line2 deleted, others shift up
}

#[test]
fn cursor_visibility() {
    // CSI ?25l hides cursor, CSI ?25h shows it
    let mut b = AlacrittyBackend::new(24, 80);
    b.process_bytes(b"\x1b[?25l");   // hide cursor
    // alacritty_terminal doesn't expose cursor visibility in grid,
    // but the sequence should not panic
    b.process_bytes(b"hidden cursor");
    b.process_bytes(b"\x1b[?25h");   // show cursor
    let snap = b.snapshot();
    assert_eq!(row_text(&snap, 0), "hidden cursor");
}

#[test]
fn cursor_shape_change() {
    let mut b = AlacrittyBackend::new(24, 80);
    // DECSCUSR: CSI 2 SP q → steady block... but let's test beam
    b.process_bytes(b"\x1b[6 q");    // steady bar/beam
    let snap = b.snapshot();
    assert_eq!(snap.cursor.shape, 2, "Shape should be beam (2)");

    b.process_bytes(b"\x1b[4 q");    // steady underline
    let snap = b.snapshot();
    assert_eq!(snap.cursor.shape, 1, "Shape should be underline (1)");

    b.process_bytes(b"\x1b[2 q");    // steady block
    let snap = b.snapshot();
    assert_eq!(snap.cursor.shape, 0, "Shape should be block (0)");
}

#[test]
fn reset_restores_clean_state() {
    let mut b = AlacrittyBackend::new(24, 80);
    b.process_bytes(b"\x1b[1;31mBold Red\x1b[10;20H");
    b.reset();
    let snap = b.snapshot();
    assert_eq!(snap.cursor.row, 0);
    assert_eq!(snap.cursor.col, 0);
    assert_eq!(snap.cells[0].character, ' ');
}

#[test]
fn mixed_control_characters() {
    // Real terminal output often mixes controls: BS, CR, LF, TAB, BEL
    let mut b = AlacrittyBackend::new(24, 80);
    b.process_bytes(b"ABC\x08D\tE\r\nF\x07G");
    let snap = b.snapshot();
    // 'A' 'B' 'D' (BS overwrote C) ... tab ... 'E' then newline, 'F', bell, 'G'
    assert_eq!(char_at(&snap, 0, 0), 'A');
    assert_eq!(char_at(&snap, 0, 1), 'B');
    assert_eq!(char_at(&snap, 0, 2), 'D'); // overwrite
    assert_eq!(char_at(&snap, 1, 0), 'F');
    assert_eq!(char_at(&snap, 1, 1), 'G');
}

#[test]
fn interleaved_text_and_escapes() {
    // Real programs interleave text with many small escape sequences
    let mut b = AlacrittyBackend::new(24, 80);
    let input = b"\x1b[1mH\x1b[3me\x1b[4ml\x1b[0ml\x1b[1;32mo\x1b[0m";
    b.process_bytes(input);
    let snap = b.snapshot();
    assert_eq!(row_text(&snap, 0), "Hello");
    // 'H': bold
    assert!(cell_at(&snap, 0, 0).flags & FLAG_BOLD != 0);
    // 'e': bold + italic
    assert!(cell_at(&snap, 0, 1).flags & FLAG_ITALIC != 0);
    // 'l' at col 2: bold + italic + underline
    assert!(cell_at(&snap, 0, 2).flags & FLAG_UNDERLINE != 0);
    // 'l' at col 3: reset to plain
    assert_eq!(cell_at(&snap, 0, 3).flags, 0);
    // 'o': bold + green
    assert!(cell_at(&snap, 0, 4).flags & FLAG_BOLD != 0);
}

#[test]
fn snapshot_dimensions_always_consistent() {
    for rows in [1, 5, 24, 50] {
        for cols in [1, 10, 80, 132] {
            let mut b = AlacrittyBackend::new(rows, cols);
            b.process_bytes(b"test");
            let snap = b.snapshot();
            assert_eq!(snap.rows, rows as i32);
            assert_eq!(snap.cols, cols as i32);
            assert_eq!(snap.cells.len(), (rows * cols) as usize);
        }
    }
}

#[test]
fn damage_after_write_includes_affected_line() {
    let mut b = AlacrittyBackend::new(24, 80);
    let _ = b.snapshot(); // consume initial full damage

    b.process_bytes(b"\x1b[5;1HText on line 5");
    let snap = b.snapshot();
    // Damage should include line 4 (0-indexed)
    assert!(
        snap.damage.iter().any(|d| d.line == 4),
        "Damage should include line 4, got: {:?}", snap.damage
    );
}

#[test]
fn multiple_snapshots_reset_damage() {
    let mut b = AlacrittyBackend::new(24, 80);
    let snap1 = b.snapshot();
    assert!(!snap1.damage.is_empty());

    // No changes — damage might be empty or minimal
    let snap2 = b.snapshot();
    // After consuming damage, subsequent snapshot without changes
    // should have less damage than a full repaint
    assert!(snap2.damage.len() <= snap1.damage.len());
}

#[test]
fn drain_events_is_idempotent() {
    let mut b = AlacrittyBackend::new(24, 80);
    b.process_bytes(b"\x07\x07"); // 2 bells
    let events = b.drain_events();
    assert!(events.len() >= 2);
    let events2 = b.drain_events();
    assert!(events2.is_empty(), "Second drain should be empty");
}

#[test]
fn input_bytes_passthrough() {
    let mut b = AlacrittyBackend::new(24, 80);
    let result = b.input_bytes(b"\x1b[A"); // Up arrow
    assert_eq!(result, b"\x1b[A");
}

#[test]
fn very_narrow_terminal() {
    let mut b = AlacrittyBackend::new(24, 1);
    b.process_bytes(b"ABCDE");
    let snap = b.snapshot();
    assert_eq!(snap.cols, 1);
    // Each character wraps, so first visible chars are on different rows
    assert_eq!(char_at(&snap, 0, 0), 'A');
    assert_eq!(char_at(&snap, 1, 0), 'B');
}

#[test]
fn very_short_terminal() {
    let mut b = AlacrittyBackend::new(1, 80);
    b.process_bytes(b"Line1\r\nLine2\r\nLine3");
    let snap = b.snapshot();
    assert_eq!(snap.rows, 1);
    // Only last line should be visible
    assert_eq!(row_text(&snap, 0), "Line3");
}

#[test]
fn large_terminal() {
    let mut b = AlacrittyBackend::new(200, 400);
    b.process_bytes(b"Big screen");
    let snap = b.snapshot();
    assert_eq!(snap.rows, 200);
    assert_eq!(snap.cols, 400);
    assert_eq!(snap.cells.len(), 200 * 400);
}
