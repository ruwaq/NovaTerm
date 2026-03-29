// NovaTerm JNI Bridge — connects Rust core to Android/Kotlin.
//
// Two APIs:
// 1. NativeTerminal: standalone VT parser (Phase 2a, dual-run mode)
// 2. NativeSession: unified PTY + VT + I/O threads (Phase 2c, full Rust)

// Phase 2a: standalone VT parser
mod handle_map;
mod jni_bridge;

// Phase 2c: unified session (PTY + VT + I/O)
mod session;
mod session_map;
mod session_jni;

// Phase 2b: GPU renderer
#[cfg(feature = "gpu")]
mod renderer_jni;
#[cfg(feature = "gpu")]
mod renderer_map;

pub use jni_bridge::*;
pub use session_jni::*;
