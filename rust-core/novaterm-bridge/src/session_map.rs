// Handle map for RustSession objects.

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

pub fn with_session<F, R>(handle: u64, f: F) -> Option<R>
where F: FnOnce(&mut RustSession) -> R {
    let sessions = SESSIONS.read();
    let mutex = sessions.get(&handle)?;
    let mut guard = mutex.lock();
    let result = f(&mut *guard);
    Some(result)
}

pub fn with_session_ref<F, R>(handle: u64, f: F) -> Option<R>
where F: FnOnce(&RustSession) -> R {
    let sessions = SESSIONS.read();
    let mutex = sessions.get(&handle)?;
    let guard = mutex.lock();
    let result = f(&*guard);
    Some(result)
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
