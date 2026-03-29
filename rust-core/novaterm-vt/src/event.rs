// Event collection from alacritty_terminal.
//
// alacritty_terminal emits events (bell, title change, clipboard, etc.)
// via the EventListener trait. We collect them for the bridge to forward
// to Kotlin.

use alacritty_terminal::event::{Event, EventListener};
use parking_lot::Mutex;
use std::sync::Arc;

/// Events that the terminal backend produces for the host app.
#[derive(Debug, Clone)]
pub enum BackendEvent {
    Bell,
    TitleChanged(String),
    ClipboardStore(String),
    ClipboardLoad,
    ChildExit(i32),
    CursorBlinkingChange(bool),
    Wakeup,
    ColorRequest(usize),
}

/// Collects events from alacritty_terminal into a thread-safe buffer.
///
/// The bridge layer drains this buffer periodically and forwards events
/// to Kotlin via JNI callbacks.
#[derive(Clone)]
pub struct EventCollector {
    events: Arc<Mutex<Vec<BackendEvent>>>,
}

impl EventCollector {
    pub fn new() -> Self {
        Self {
            events: Arc::new(Mutex::new(Vec::with_capacity(16))),
        }
    }

    /// Drain all pending events, returning them.
    pub fn drain(&self) -> Vec<BackendEvent> {
        let mut events = self.events.lock();
        std::mem::take(&mut *events)
    }

    pub fn has_pending(&self) -> bool {
        !self.events.lock().is_empty()
    }
}

impl Default for EventCollector {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn new_collector_empty() {
        let collector = EventCollector::new();
        assert!(!collector.has_pending());
        assert!(collector.drain().is_empty());
    }

    #[test]
    fn drain_returns_and_clears() {
        let collector = EventCollector::new();
        collector.send_event(Event::Bell);
        collector.send_event(Event::Bell);
        assert!(collector.has_pending());

        let events = collector.drain();
        assert_eq!(events.len(), 2);
        assert!(!collector.has_pending());
        assert!(collector.drain().is_empty());
    }

    #[test]
    fn title_event_captured() {
        let collector = EventCollector::new();
        collector.send_event(Event::Title("test".to_string()));
        let events = collector.drain();
        assert!(matches!(&events[0], BackendEvent::TitleChanged(t) if t == "test"));
    }

    #[test]
    fn wakeup_not_filtered() {
        let collector = EventCollector::new();
        collector.send_event(Event::Wakeup);
        assert_eq!(collector.drain().len(), 1);
    }

    #[test]
    fn clone_shares_state() {
        let collector = EventCollector::new();
        let clone = collector.clone();
        collector.send_event(Event::Bell);
        // Clone should see the same events
        assert!(clone.has_pending());
        assert_eq!(clone.drain().len(), 1);
    }
}

impl EventListener for EventCollector {
    fn send_event(&self, event: Event) {
        let backend_event = match event {
            Event::Bell => BackendEvent::Bell,
            Event::Title(title) => BackendEvent::TitleChanged(title),
            Event::ClipboardStore(_, text) => BackendEvent::ClipboardStore(text),
            Event::ClipboardLoad(_, _) => BackendEvent::ClipboardLoad,
            Event::ChildExit(code) => BackendEvent::ChildExit(code),
            Event::CursorBlinkingChange => BackendEvent::CursorBlinkingChange(true),
            Event::Wakeup => BackendEvent::Wakeup,
            Event::ColorRequest(idx, _) => BackendEvent::ColorRequest(idx),
            // Events we don't need to forward to Kotlin:
            // PtyWrite is handled internally, MouseCursorDirty is renderer-only,
            // ResetTitle and TextAreaSizeRequest are handled at the bridge level
            _ => return,
        };
        self.events.lock().push(backend_event);
    }
}
