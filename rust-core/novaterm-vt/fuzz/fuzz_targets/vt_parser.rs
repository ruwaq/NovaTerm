//! Fuzz target: feed random bytes into the VT parser.
//! Any panic = bug. Based on Ghostty's AFL++ approach that found 5+ crashes.

#![no_main]
use libfuzzer_sys::fuzz_target;
use novaterm_vt::{AlacrittyBackend, TerminalBackend};

fuzz_target!(|data: &[u8]| {
    // Create a small terminal (faster fuzzing)
    let mut backend = AlacrittyBackend::new(24, 80);

    // Feed arbitrary bytes — must not panic
    backend.process_bytes(data);

    // Taking a snapshot must also not panic
    let _ = backend.snapshot();

    // Cursor state must not panic
    let _ = backend.cursor_state();

    // Events drain must not panic
    let _ = backend.drain_events();
    let _ = backend.drain_pty_writes();
});
