//! Fuzz target: random bytes → snapshot → verify grid consistency.

#![no_main]
use libfuzzer_sys::fuzz_target;
use novaterm_vt::{AlacrittyBackend, TerminalBackend};

fuzz_target!(|data: &[u8]| {
    if data.len() < 4 {
        return;
    }

    // Use first 2 bytes for dimensions (1-100 range)
    let rows = (data[0] as u32 % 100) + 1;
    let cols = (data[1] as u32 % 100) + 1;

    let mut backend = AlacrittyBackend::new(rows, cols);
    backend.process_bytes(&data[2..]);

    let snap = backend.snapshot();

    // Invariants that must always hold:
    assert_eq!(snap.rows, rows as i32);
    assert_eq!(snap.cols, cols as i32);
    assert_eq!(snap.cells.len(), (rows * cols) as usize);

    // Cursor must be within bounds
    assert!(snap.cursor.row >= 0);
    assert!(snap.cursor.col >= 0);
});
