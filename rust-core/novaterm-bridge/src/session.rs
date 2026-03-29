// Unified Rust terminal session: PTY + VT parser + I/O threads.
//
// Reader thread buffers PTY output. JNI thread drains and parses.
// This avoids lock contention on the parser in the hot read path.

use novaterm_pty::Pid;
use novaterm_vt::{AlacrittyBackend, GridSnapshot, TerminalBackend};
use parking_lot::Mutex;
use std::io::{Read, Write};
use std::os::unix::io::{AsRawFd, FromRawFd, OwnedFd};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::thread;

/// Shared byte buffer between threads.
struct ByteBuffer {
    data: Mutex<Vec<u8>>,
}

impl ByteBuffer {
    fn new() -> Self {
        Self { data: Mutex::new(Vec::with_capacity(64 * 1024)) }
    }
    fn push(&self, bytes: &[u8]) {
        self.data.lock().extend_from_slice(bytes);
    }
    fn drain(&self) -> Vec<u8> {
        std::mem::take(&mut *self.data.lock())
    }
    fn is_empty(&self) -> bool {
        self.data.lock().is_empty()
    }
}

/// Complete terminal session: PTY + VT parser + I/O threads.
pub struct RustSession {
    backend: AlacrittyBackend,
    read_buffer: Arc<ByteBuffer>,
    write_buffer: Arc<ByteBuffer>,
    master_fd: OwnedFd,
    child_pid: i32,
    alive: Arc<AtomicBool>,
    rows: u16,
    cols: u16,
}

impl RustSession {
    /// Spawn a new session with PTY, I/O threads, and VT parser.
    pub fn spawn(
        shell: &str,
        cwd: &str,
        args: &[&str],
        env: &[&str],
        rows: u16,
        cols: u16,
    ) -> Result<Self, novaterm_pty::Error> {
        let (master_fd, pid) =
            novaterm_pty::create_subprocess(shell, cwd, args, env, rows, cols)?;
        let child_pid = pid.0;

        let alive = Arc::new(AtomicBool::new(true));
        let read_buffer = Arc::new(ByteBuffer::new());
        let write_buffer = Arc::new(ByteBuffer::new());

        // Dup fds for threads
        let reader_raw = unsafe { libc::dup(master_fd.as_raw_fd()) };
        if reader_raw < 0 {
            return Err(novaterm_pty::Error::Io(std::io::Error::last_os_error()));
        }
        let writer_raw = unsafe { libc::dup(master_fd.as_raw_fd()) };
        if writer_raw < 0 {
            unsafe { libc::close(reader_raw); }
            return Err(novaterm_pty::Error::Io(std::io::Error::last_os_error()));
        }

        // Reader: PTY → read_buffer
        let rb = read_buffer.clone();
        let ar = alive.clone();
        thread::Builder::new()
            .name(format!("nova-read[{}]", child_pid))
            .spawn(move || {
                let mut file = unsafe { std::fs::File::from_raw_fd(reader_raw) };
                let mut buf = [0u8; 8192];
                while ar.load(Ordering::Relaxed) {
                    match file.read(&mut buf) {
                        Ok(0) | Err(_) => break,
                        Ok(n) => rb.push(&buf[..n]),
                    }
                }
            })
            .map_err(novaterm_pty::Error::Io)?;

        // Writer: write_buffer → PTY
        let wb = write_buffer.clone();
        let aw = alive.clone();
        thread::Builder::new()
            .name(format!("nova-write[{}]", child_pid))
            .spawn(move || {
                let mut file = unsafe { std::fs::File::from_raw_fd(writer_raw) };
                while aw.load(Ordering::Relaxed) {
                    let data = wb.drain();
                    if data.is_empty() {
                        thread::park_timeout(std::time::Duration::from_millis(1));
                        continue;
                    }
                    if file.write_all(&data).is_err() {
                        break;
                    }
                }
            })
            .map_err(novaterm_pty::Error::Io)?;

        Ok(RustSession {
            backend: AlacrittyBackend::new(rows as u32, cols as u32),
            read_buffer,
            write_buffer,
            master_fd,
            child_pid,
            alive,
            rows,
            cols,
        })
    }

    /// Drain PTY output and feed to VT parser. Returns bytes processed.
    pub fn process_pending(&mut self) -> usize {
        let bytes = self.read_buffer.drain();
        let n = bytes.len();
        if n > 0 {
            self.backend.process_bytes(&bytes);
            let pty_writes = self.backend.drain_pty_writes();
            if !pty_writes.is_empty() {
                self.write_buffer.push(&pty_writes);
            }
        }
        n
    }

    pub fn write(&self, data: &[u8]) {
        self.write_buffer.push(data);
    }

    pub fn pid(&self) -> i32 {
        self.child_pid
    }

    pub fn resize(&mut self, rows: u16, cols: u16) {
        self.rows = rows;
        self.cols = cols;
        let _ = novaterm_pty::set_window_size(&self.master_fd, rows, cols, 0, 0);
        self.backend.resize(rows as u32, cols as u32);
    }

    pub fn snapshot(&mut self) -> GridSnapshot {
        self.backend.snapshot()
    }

    pub fn cursor_state(&self) -> novaterm_vt::CursorState {
        self.backend.cursor_state()
    }

    pub fn dimensions(&self) -> (u16, u16) {
        (self.rows, self.cols)
    }

    pub fn drain_events(&self) -> Vec<novaterm_vt::BackendEvent> {
        self.backend.drain_events()
    }

    pub fn has_pending_output(&self) -> bool {
        !self.read_buffer.is_empty()
    }

    pub fn stop(&self) {
        self.alive.store(false, Ordering::Relaxed);
        unsafe { libc::kill(self.child_pid, libc::SIGKILL); }
    }

    pub fn wait(&self) -> i32 {
        novaterm_pty::wait_for(Pid(self.child_pid))
    }
}

impl Drop for RustSession {
    fn drop(&mut self) {
        self.stop();
    }
}
