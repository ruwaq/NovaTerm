// NovaTerm VT — Terminal emulation core wrapping alacritty_terminal.
//
// This crate provides the TerminalBackend trait and an implementation
// backed by alacritty_terminal 0.25.1. It is platform-agnostic: no JNI,
// no Android deps. The bridge crate handles FFI.

mod backend;
pub mod grid_snapshot;
mod event;

pub use backend::{AlacrittyBackend, TerminalBackend};
pub use event::{BackendEvent, EventCollector};
pub use grid_snapshot::{CellData, CursorState, DamageRange, GridSnapshot};
