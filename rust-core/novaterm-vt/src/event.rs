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
    /// Bytes that must be written back to the PTY (DA responses, DSR, etc.)
    PtyWrite(Vec<u8>),
    /// A decoded Sixel image at a grid position, pixel data in RGBA format.
    SixelImage {
        x: u16,
        y: u16,
        width: u32,
        height: u32,
        pixels: Vec<u8>, // RGBA
    },
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

    /// Drain only PtyWrite events, leaving other events in the buffer.
    /// This avoids the race condition of drain-filter-reinsert.
    pub fn drain_pty_writes(&self) -> Vec<u8> {
        let mut events = self.events.lock();
        let mut bytes = Vec::new();
        events.retain(|event| {
            match event {
                BackendEvent::PtyWrite(data) => {
                    bytes.extend_from_slice(data);
                    false // remove from buffer
                }
                _ => true, // keep in buffer
            }
        });
        bytes
    }

    pub fn has_pending(&self) -> bool {
        !self.events.lock().is_empty()
    }

    /// Push an event back into the buffer.
    pub fn push(&self, event: BackendEvent) {
        self.events.lock().push(event);
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
            Event::PtyWrite(text) => BackendEvent::PtyWrite(text.into_bytes()),
            // MouseCursorDirty is renderer-only, others handled at bridge level
            _ => return,
        };
        self.events.lock().push(backend_event);
    }
}
