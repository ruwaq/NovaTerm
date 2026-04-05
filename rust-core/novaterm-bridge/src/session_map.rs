// Handle map for RustSession objects.
//
// Uses try_read + try_lock (like handle_map) to prevent deadlock:
// if destroy() holds a write lock, JNI calls return None instead of blocking.

use crate::session::RustSession;
use parking_lot::{Mutex, RwLock};
use std::collections::HashMap;
use std::sync::atomic::{AtomicU64, Ordering};

static NEXT_HANDLE: AtomicU64 = AtomicU64::new(1);
static SESSIONS: std::sync::LazyLock<RwLock<HashMap<u64, Mutex<RustSession>>>> =
    std::sync::LazyLock::new(|| RwLock::new(HashMap::new()));

pub fn insert(session: RustSession) -> u64 {
    let handle = NEXT_HANDLE.fetch_add(1, Ordering::Relaxed);
    let pid = session.pid();
    SESSIONS.write().insert(handle, Mutex::new(session));
    log::debug!("Session created: handle={} pid={}", handle, pid);
    handle
}

/// Execute a closure with mutable access to a session.
/// Returns None if handle is invalid or lock is contended (prevents deadlock).
pub fn with_session<F, R>(handle: u64, f: F) -> Option<R>
where F: FnOnce(&mut RustSession) -> R {
    let sessions = SESSIONS.try_read()?;
    let mutex = sessions.get(&handle)?;
    let mut guard = mutex.try_lock()?;
    Some(f(&mut *guard))
}

/// Execute a closure with shared access to a session.
/// Returns None if handle is invalid or lock is contended (prevents deadlock).
pub fn with_session_ref<F, R>(handle: u64, f: F) -> Option<R>
where F: FnOnce(&RustSession) -> R {
    let sessions = SESSIONS.try_read()?;
    let mutex = sessions.get(&handle)?;
    let guard = mutex.try_lock()?;
    Some(f(&*guard))
}

pub fn destroy(handle: u64) -> bool {
    if let Some(m) = SESSIONS.write().remove(&handle) {
        m.into_inner().stop();
        true
    } else {
        false
    }
}

pub fn active_count() -> usize {
    SESSIONS.read().len()
}
