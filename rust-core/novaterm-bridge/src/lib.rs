// NovaTerm JNI Bridge — connects Rust VT core to Android/Kotlin.
//
// This is the cdylib that produces libnovaterm.so. It exposes JNI
// functions that the Kotlin TerminalViewModel calls to manage
// terminal sessions backed by the Rust VT parser.
//
// Architecture:
//   Kotlin TerminalViewModel → JNI → this bridge → novaterm-vt
//   PTY I/O stays in Java/C (termux.c) for Phase 2a.
//   Bridge only handles VT parsing and grid state.

mod handle_map;
mod jni_bridge;

pub use jni_bridge::*;
