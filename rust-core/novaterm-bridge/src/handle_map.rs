// Thread-safe handle map for managing Rust objects from JNI.
//
// Each terminal backend instance is stored here and referenced by
// a u64 handle on the Kotlin side. This follows the Signal/libsignal
// pattern of opaque handles for cross-FFI object management.

use novaterm_vt::AlacrittyBackend;
use parking_lot::RwLock;
use std::collections::HashMap;
use std::sync::atomic::{AtomicU64, Ordering};

static NEXT_HANDLE: AtomicU64 = AtomicU64::new(1);
static BACKENDS: std::sync::LazyLock<RwLock<HashMap<u64, parking_lot::Mutex<AlacrittyBackend>>>> =
    std::sync::LazyLock::new(|| RwLock::new(HashMap::new()));

/// Create a new backend and return its handle.
pub fn create_backend(rows: u32, cols: u32) -> u64 {
    let handle = NEXT_HANDLE.fetch_add(1, Ordering::Relaxed);
    let backend = AlacrittyBackend::new(rows, cols);
    BACKENDS
        .write()
        .insert(handle, parking_lot::Mutex::new(backend));
    log::debug!("Created backend handle={} ({}x{})", handle, rows, cols);
    handle
}

/// Execute a closure with mutable access to a backend.
/// Returns None if the handle is invalid.
///
/// Safety note on deadlock: This acquires BACKENDS.read() then mutex.lock().
/// destroy_backend() acquires BACKENDS.write(). parking_lot's RwLock is
/// write-preferring, so read→write on the same thread would deadlock.
/// NEVER call destroy_backend() from within a with_backend() closure.
pub fn with_backend<F, R>(handle: u64, f: F) -> Option<R>
where
    F: FnOnce(&mut AlacrittyBackend) -> R,
{
    let backends = BACKENDS.read();
    let mutex = backends.get(&handle)?;
    let mut backend = mutex.lock();
    Some(f(&mut backend))
}

/// Destroy a backend and free its resources.
pub fn destroy_backend(handle: u64) -> bool {
    BACKENDS.write().remove(&handle).is_some()
}

/// Get the number of active backends (for debugging).
pub fn active_count() -> usize {
    BACKENDS.read().len()
}
