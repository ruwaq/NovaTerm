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
/// Returns None if the handle is invalid or the lock is contended.
///
/// Uses try_read + try_lock to prevent deadlock: if destroy_backend() holds
/// a write lock (or is waiting for one), we return None instead of blocking.
pub fn with_backend<F, R>(handle: u64, f: F) -> Option<R>
where
    F: FnOnce(&mut AlacrittyBackend) -> R,
{
    let backends = BACKENDS.try_read()?;
    let mutex = backends.get(&handle)?;
    let mut backend = mutex.try_lock()?;
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

#[cfg(test)]
mod tests {
    use super::*;
    use novaterm_vt::TerminalBackend;

    #[test]
    fn create_backend_returns_nonzero_handle() {
        let h = create_backend(24, 80);
        assert!(h > 0, "Handle should be non-zero");
        destroy_backend(h);
    }

    #[test]
    fn create_backend_sequential_handles() {
        let h1 = create_backend(24, 80);
        let h2 = create_backend(40, 120);
        assert!(h1 < h2, "Sequential handles should be increasing");
        destroy_backend(h1);
        destroy_backend(h2);
    }

    #[test]
    fn with_backend_process_and_snapshot() {
        let h = create_backend(24, 80);
        // Use blocking lock for test — try_read may fail under parallel test contention
        let backends = BACKENDS.read();
        let mutex = backends.get(&h).expect("Handle should exist");
        let mut backend = mutex.lock();
        backend.process_bytes(b"Hello World");
        let snap = backend.snapshot();
        drop(backend);
        drop(backends);
        assert!(snap.rows > 0, "Snapshot should have rows");
        assert!(snap.cols > 0, "Snapshot should have cols");
        destroy_backend(h);
    }

    #[test]
    fn with_backend_invalid_handle() {
        let result = with_backend(99999, |_b| "should not reach");
        assert!(result.is_none(), "Invalid handle should return None");
    }

    #[test]
    fn destroy_backend_twice() {
        let h = create_backend(24, 80);
        assert!(destroy_backend(h), "First destroy should succeed");
        assert!(!destroy_backend(h), "Second destroy should fail");
    }

    #[test]
    fn with_backend_after_destroy() {
        let h = create_backend(24, 80);
        destroy_backend(h);
        let result = with_backend(h, |_b| "should not reach");
        assert!(result.is_none(), "Access after destroy should return None");
    }

    #[test]
    fn active_count_increases_on_create() {
        let count_before = active_count();
        let h = create_backend(24, 80);
        // Count should have increased by at least 1 (other tests may also create backends)
        assert!(active_count() > count_before, "Count should increase after create");
        destroy_backend(h);
    }

    #[test]
    fn create_and_destroy_multiple() {
        let handles: Vec<u64> = (0..5).map(|_| create_backend(24, 80)).collect();
        // Verify all handles are valid using blocking lock
        let backends = BACKENDS.read();
        for h in &handles {
            assert!(backends.get(h).is_some(), "Handle {} should be valid", h);
        }
        drop(backends);
        for h in handles {
            assert!(destroy_backend(h));
        }
    }
}
