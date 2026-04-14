// Cursor blink state machine for the GPU renderer.
//
// Manages cursor visibility toggle at a fixed interval (530ms default).
// The VulkanRenderer checks tick() each frame and updates the uniform.

#[cfg(feature = "vulkan")]
use std::time::{Duration, Instant};

/// Default blink interval matching common terminal emulators.
#[cfg(feature = "vulkan")]
const BLINK_INTERVAL: Duration = Duration::from_millis(530);

/// Cursor blink state machine.
#[cfg(feature = "vulkan")]
pub struct CursorBlinker {
    visible: bool,
    last_toggle: Instant,
    interval: Duration,
    enabled: bool,
}

#[cfg(feature = "vulkan")]
impl CursorBlinker {
    pub fn new() -> Self {
        Self {
            visible: true,
            last_toggle: Instant::now(),
            interval: BLINK_INTERVAL,
            enabled: true,
        }
    }

    /// Tick the blink state. Returns true if visibility changed.
    pub fn tick(&mut self) -> bool {
        if !self.enabled {
            if !self.visible {
                self.visible = true;
                return true;
            }
            return false;
        }

        if self.last_toggle.elapsed() >= self.interval {
            self.visible = !self.visible;
            self.last_toggle = Instant::now();
            true
        } else {
            false
        }
    }

    /// Reset to visible (e.g., after user input).
    pub fn reset(&mut self) {
        self.visible = true;
        self.last_toggle = Instant::now();
    }

    /// Whether cursor should be visible this frame.
    pub fn is_visible(&self) -> bool {
        self.visible
    }

    /// Enable or disable blinking.
    pub fn set_enabled(&mut self, enabled: bool) {
        self.enabled = enabled;
        if !enabled {
            self.visible = true;
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn initial_state_visible() {
        let blinker = CursorBlinker::new();
        assert!(blinker.is_visible());
    }

    #[test]
    fn tick_toggles_after_interval() {
        let mut blinker = CursorBlinker::new();
        blinker.interval = Duration::from_millis(1); // Speed up for test
        std::thread::sleep(Duration::from_millis(5));
        let changed = blinker.tick();
        assert!(changed, "should toggle after interval");
        assert!(!blinker.is_visible(), "should be invisible after first toggle");
    }

    #[test]
    fn reset_makes_visible() {
        let mut blinker = CursorBlinker::new();
        blinker.visible = false;
        blinker.reset();
        assert!(blinker.is_visible());
    }

    #[test]
    fn disabled_always_visible() {
        let mut blinker = CursorBlinker::new();
        blinker.set_enabled(false);
        blinker.visible = false;
        let changed = blinker.tick();
        assert!(changed);
        assert!(blinker.is_visible());
    }

    #[test]
    fn disabled_no_double_toggle() {
        let mut blinker = CursorBlinker::new();
        blinker.set_enabled(false);
        // Already visible, tick should return false (no change)
        let changed = blinker.tick();
        assert!(!changed, "no change needed when already visible and disabled");
        assert!(blinker.is_visible());
    }

    #[test]
    fn tick_no_change_before_interval() {
        let mut blinker = CursorBlinker::new();
        // Immediately after creation, interval hasn't elapsed
        let changed = blinker.tick();
        assert!(!changed, "should not toggle before interval");
        assert!(blinker.is_visible());
    }

    #[test]
    fn double_toggle_returns_to_visible() {
        let mut blinker = CursorBlinker::new();
        blinker.interval = Duration::from_millis(1);
        // First toggle: visible → invisible
        std::thread::sleep(Duration::from_millis(5));
        blinker.tick();
        assert!(!blinker.is_visible());
        // Second toggle: invisible → visible
        std::thread::sleep(Duration::from_millis(5));
        blinker.tick();
        assert!(blinker.is_visible());
    }

    #[test]
    fn reset_updates_last_toggle() {
        let mut blinker = CursorBlinker::new();
        blinker.interval = Duration::from_millis(1);
        std::thread::sleep(Duration::from_millis(5));
        blinker.tick(); // toggle to invisible
        assert!(!blinker.is_visible());
        blinker.reset(); // back to visible
        assert!(blinker.is_visible());
        // Right after reset, tick should not toggle
        let changed = blinker.tick();
        assert!(!changed, "should not toggle right after reset");
    }

    #[test]
    fn enable_while_invisible_restores() {
        let mut blinker = CursorBlinker::new();
        blinker.visible = false;
        blinker.set_enabled(true); // enabling should not force visible
        assert!(!blinker.is_visible(), "enabling should not force visible");
    }
}
