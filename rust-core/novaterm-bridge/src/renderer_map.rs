// Handle map for VulkanRenderer objects.
//
// Same pattern as session_map.rs: atomic counter + RwLock<HashMap<u64, Mutex<T>>>.

#[cfg(feature = "gpu")]
use novaterm_renderer::VulkanRenderer;
#[cfg(feature = "gpu")]
use parking_lot::{Mutex, RwLock};
#[cfg(feature = "gpu")]
use std::collections::HashMap;
#[cfg(feature = "gpu")]
use std::sync::atomic::{AtomicU64, Ordering};

#[cfg(feature = "gpu")]
static NEXT_HANDLE: AtomicU64 = AtomicU64::new(1);

#[cfg(feature = "gpu")]
static RENDERERS: std::sync::LazyLock<RwLock<HashMap<u64, Mutex<VulkanRenderer>>>> =
    std::sync::LazyLock::new(|| RwLock::new(HashMap::new()));

#[cfg(feature = "gpu")]
pub fn insert(renderer: VulkanRenderer) -> u64 {
    let handle = NEXT_HANDLE.fetch_add(1, Ordering::Relaxed);
    RENDERERS.write().insert(handle, Mutex::new(renderer));
    log::debug!("Renderer created: handle={}", handle);
    handle
}

#[cfg(feature = "gpu")]
pub fn with_renderer<F, R>(handle: u64, f: F) -> Option<R>
where
    F: FnOnce(&mut VulkanRenderer) -> R,
{
    let renderers = RENDERERS.try_read()?;
    let mutex = renderers.get(&handle)?;
    let mut guard = mutex.try_lock()?;
    Some(f(&mut *guard))
}

#[cfg(feature = "gpu")]
pub fn destroy(handle: u64) -> bool {
    if let Some(m) = RENDERERS.write().remove(&handle) {
        m.into_inner().destroy();
        true
    } else {
        false
    }
}
