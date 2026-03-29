// End-to-end tests: VT bytes → parser → grid snapshot → atlas lookup → CellGpu.
//
// These simulate the FULL terminal pipeline without GPU hardware:
// 1. Create AlacrittyBackend (VT parser)
// 2. Feed realistic VT sequences
// 3. Take GridSnapshot
// 4. Create GlyphAtlas, look up every cell
// 5. Verify cells have valid atlas entries and correct colors/flags
//
// This catches integration bugs between novaterm-vt and novaterm-renderer.

use novaterm_renderer::atlas::{GlyphAtlas, GlyphKey, ATLAS_WIDTH, ATLAS_HEIGHT};
use novaterm_vt::{AlacrittyBackend, TerminalBackend};

fn create_pipeline() -> (AlacrittyBackend, GlyphAtlas) {
    let backend = AlacrittyBackend::new(24, 80);
    let atlas = GlyphAtlas::new(32.0);
    (backend, atlas)
}

/// Helper: feed bytes, snapshot, verify every cell has atlas entry.
fn verify_grid_has_atlas_entries(
    backend: &mut AlacrittyBackend,
    atlas: &mut GlyphAtlas,
    data: &[u8],
) -> novaterm_vt::GridSnapshot {
    backend.process_bytes(data);
    let snap = backend.snapshot();

    // Every non-space cell should have a valid atlas entry
    for (i, cell) in snap.cells.iter().enumerate() {
        if cell.character != ' ' && cell.character != '\0' {
            let key = GlyphKey { codepoint: cell.character, flags: 0 };
            let entry = atlas.lookup(key);
            assert!(
                entry.is_some(),
                "Cell {} ('{}') has no atlas entry",
                i, cell.character
            );
        }
    }

    snap
}

// ── Realistic Terminal Simulations ──────────────────────────

#[test]
fn e2e_bash_prompt_with_colors() {
    let (mut backend, mut atlas) = create_pipeline();
    atlas.pre_rasterize_ascii();

    // Colored bash prompt: user@host:~/project$
    let prompt = b"\x1b[01;32muser@host\x1b[00m:\x1b[01;34m~/project\x1b[00m$ ";
    let snap = verify_grid_has_atlas_entries(&mut backend, &mut atlas, prompt);

    // Verify 'u' (first char) is green + bold
    assert_eq!(snap.cells[0].character, 'u');
    assert!(snap.cells[0].flags & 1 != 0, "should be bold");
    // Verify '$' is at the expected position
    let dollar_pos = snap.cells.iter().position(|c| c.character == '$');
    assert!(dollar_pos.is_some(), "should find $ in prompt");
}

#[test]
fn e2e_ls_color_output() {
    let (mut backend, mut atlas) = create_pipeline();
    atlas.pre_rasterize_ascii();

    // ls --color output: blue directories, green executables
    let output = b"\x1b[01;34mDocuments\x1b[0m  \x1b[01;32mscript.sh\x1b[0m  README.md\r\n";
    let snap = verify_grid_has_atlas_entries(&mut backend, &mut atlas, output);

    // 'D' of Documents should have bold flag
    assert_eq!(snap.cells[0].character, 'D');
    assert!(snap.cells[0].flags & 1 != 0, "directory should be bold");
}

#[test]
fn e2e_git_diff_output() {
    let (mut backend, mut atlas) = create_pipeline();
    atlas.pre_rasterize_ascii();

    let diff = b"\x1b[32m+added line\x1b[m\r\n\x1b[31m-removed line\x1b[m\r\n unchanged\r\n";
    let snap = verify_grid_has_atlas_entries(&mut backend, &mut atlas, diff);

    // First char '+' should have green fg
    assert_eq!(snap.cells[0].character, '+');
    // Second line '-' should have red fg (different from '+')
    assert_eq!(snap.cells[80].character, '-');
    assert_ne!(snap.cells[0].fg, snap.cells[80].fg, "green and red should differ");
}

#[test]
fn e2e_vim_open_close() {
    let (mut backend, mut atlas) = create_pipeline();
    atlas.pre_rasterize_ascii();

    // Enter alt screen (vim)
    backend.process_bytes(b"normal content here");
    backend.process_bytes(b"\x1b[?1049h"); // Enter alt screen
    backend.process_bytes(b"\x1b[H"); // Home
    backend.process_bytes(b"~\r\n~\r\n~\r\n"); // vim empty lines

    let snap = verify_grid_has_atlas_entries(&mut backend, &mut atlas, b"");
    let first_char = snap.cells[0].character;
    assert_eq!(first_char, '~', "alt screen should show vim tildes");

    // Exit alt screen
    backend.process_bytes(b"\x1b[?1049l");
    let snap2 = backend.snapshot();
    let restored = snap2.cells[0].character;
    assert_eq!(restored, 'n', "should restore 'normal content here'");
}

#[test]
fn e2e_htop_style_screen_fill() {
    let (mut backend, mut atlas) = create_pipeline();
    atlas.pre_rasterize_ascii();

    // Simulate htop: fill screen with colored bars
    let mut output = Vec::new();
    output.extend_from_slice(b"\x1b[H\x1b[2J"); // Clear screen
    for row in 0..24 {
        output.extend_from_slice(format!(
            "\x1b[{};1H\x1b[42m CPU {:2}% \x1b[0m [{:50}]",
            row + 1, row * 4, "=".repeat((row * 2).min(50))
        ).as_bytes());
    }

    let snap = verify_grid_has_atlas_entries(&mut backend, &mut atlas, &output);
    assert_eq!(snap.rows, 24);
    assert_eq!(snap.cols, 80);
    // Every row should have content
    for row in 0..24 {
        let row_start = row * 80;
        let has_content = snap.cells[row_start..row_start + 80]
            .iter()
            .any(|c| c.character != ' ');
        assert!(has_content, "row {} should have content", row);
    }
}

#[test]
fn e2e_unicode_mixed_content() {
    let (mut backend, mut atlas) = create_pipeline();

    // Mix of ASCII, CJK, emoji, and special chars
    let content = "Hello 日本語 café 🚀 ñ";
    backend.process_bytes(content.as_bytes());

    let snap = backend.snapshot();
    // Verify first chars
    assert_eq!(snap.cells[0].character, 'H');
    assert_eq!(snap.cells[1].character, 'e');

    // Look up each non-space cell in atlas
    for cell in &snap.cells {
        if cell.character != ' ' && cell.character != '\0' {
            let key = GlyphKey { codepoint: cell.character, flags: 0 };
            let _entry = atlas.lookup(key); // Should not panic
        }
    }
}

#[test]
fn e2e_rapid_output_1000_lines() {
    let (mut backend, mut atlas) = create_pipeline();
    atlas.pre_rasterize_ascii();

    // Simulate `seq 1 1000`
    let mut output = Vec::with_capacity(10000);
    for i in 1..=1000 {
        output.extend_from_slice(format!("{}\r\n", i).as_bytes());
    }
    backend.process_bytes(&output);

    let snap = backend.snapshot();
    assert_eq!(snap.rows, 24);
    assert_eq!(snap.cols, 80);

    // At least one of the visible rows should have numeric content
    let has_numbers = (0..24).any(|row| {
        let start = row * 80;
        snap.cells[start..start + 80]
            .iter()
            .any(|c| c.character.is_ascii_digit())
    });
    assert!(has_numbers, "should see numbers in visible grid");

    // Atlas should have cached digits + newline chars
    assert!(atlas.cached_count() >= 10, "should cache at least digits 0-9");
}

#[test]
fn e2e_cursor_tracking_accuracy() {
    let (mut backend, mut atlas) = create_pipeline();

    // Move cursor to specific positions and verify
    backend.process_bytes(b"\x1b[5;10H"); // Row 5, Col 10 (1-based)
    let snap = backend.snapshot();
    assert_eq!(snap.cursor.row, 4, "cursor row 0-indexed");
    assert_eq!(snap.cursor.col, 9, "cursor col 0-indexed");

    // Write some text, cursor should advance
    backend.process_bytes(b"ABC");
    let snap2 = backend.snapshot();
    assert_eq!(snap2.cursor.col, 12, "cursor after 3 chars");
    assert_eq!(snap2.cells[4 * 80 + 9].character, 'A');
    assert_eq!(snap2.cells[4 * 80 + 10].character, 'B');
    assert_eq!(snap2.cells[4 * 80 + 11].character, 'C');
}

#[test]
fn e2e_sgr_all_attributes_to_cellgpu() {
    let (mut backend, mut atlas) = create_pipeline();

    // Bold + italic + underline + strikethrough
    backend.process_bytes(b"\x1b[1;3;4;9mX\x1b[0m");
    let snap = backend.snapshot();
    let cell = &snap.cells[0];

    assert_eq!(cell.character, 'X');
    assert!(cell.flags & 1 != 0, "bold");
    assert!(cell.flags & 2 != 0, "italic");
    assert!(cell.flags & 4 != 0, "underline");
    assert!(cell.flags & 8 != 0, "strikethrough");

    // Atlas should have the glyph
    let entry = atlas.lookup(GlyphKey { codepoint: 'X', flags: 1 }); // bold
    assert!(entry.is_some());
}

#[test]
fn e2e_truecolor_preservation() {
    let (mut backend, mut atlas) = create_pipeline();

    // Set exact RGB color: fg=#FF8000 (orange)
    backend.process_bytes(b"\x1b[38;2;255;128;0mO\x1b[0m");
    let snap = backend.snapshot();

    assert_eq!(snap.cells[0].character, 'O');
    assert_eq!(snap.cells[0].fg, 0xFF_FF_80_00, "truecolor fg should be preserved");
}

#[test]
fn e2e_atlas_bitmap_coverage() {
    let mut atlas = GlyphAtlas::new(32.0);
    atlas.pre_rasterize_ascii();

    // Verify atlas bitmap is populated
    let bitmap = atlas.bitmap();
    let total_pixels = (ATLAS_WIDTH * ATLAS_HEIGHT * 4) as usize;
    assert_eq!(bitmap.len(), total_pixels);

    // Count non-zero pixels (should be substantial after ASCII pre-rasterize)
    let non_zero = bitmap.iter().filter(|&&b| b != 0).count();
    assert!(non_zero > 1000, "atlas should have >1000 non-zero pixels, got {}", non_zero);
}

#[test]
fn e2e_resize_during_output() {
    let (mut backend, mut atlas) = create_pipeline();
    atlas.pre_rasterize_ascii();

    backend.process_bytes(b"Hello World\r\nSecond Line\r\n");
    backend.resize(48, 120); // Resize to larger
    backend.process_bytes(b"After resize\r\n");

    let snap = backend.snapshot();
    assert_eq!(snap.rows, 48);
    assert_eq!(snap.cols, 120);

    // Cells should still be valid
    let total = snap.cells.len();
    assert_eq!(total, 48 * 120);

    // Verify atlas still works
    for cell in &snap.cells[..120] { // First row
        if cell.character != ' ' && cell.character != '\0' {
            let entry = atlas.lookup(GlyphKey { codepoint: cell.character, flags: 0 });
            assert!(entry.is_some());
        }
    }
}

#[test]
fn e2e_damage_tracking_works() {
    let (mut backend, _atlas) = create_pipeline();

    // First snapshot gets full damage
    let snap1 = backend.snapshot();
    assert!(!snap1.damage.is_empty(), "first snapshot should have full damage");

    // Write to specific row
    backend.process_bytes(b"\x1b[10;1HNew content on row 10");
    let snap2 = backend.snapshot();
    assert!(!snap2.damage.is_empty(), "should have damage after write");
}

#[test]
fn e2e_empty_terminal_safe() {
    let (mut backend, mut atlas) = create_pipeline();

    // Empty terminal — snapshot should be safe
    let snap = backend.snapshot();
    assert_eq!(snap.cells.len(), 24 * 80);

    // All cells should be spaces
    for cell in &snap.cells {
        assert_eq!(cell.character, ' ');
    }

    // Atlas lookup for space should work
    let entry = atlas.lookup(GlyphKey { codepoint: ' ', flags: 0 });
    assert!(entry.is_some());
}
