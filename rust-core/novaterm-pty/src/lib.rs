//! PTY management for NovaTerm — replaces termux.c JNI with safe Rust.
//!
//! Provides subprocess spawning via PTY, window size control, and UTF-8 mode
//! configuration. Uses `libc::fork()` directly with async-signal-safe child
//! setup (no Rust allocator after fork).

use std::ffi::{CStr, CString};
use std::fmt;
use std::os::unix::io::{AsRawFd, FromRawFd, OwnedFd, RawFd};

// ---------------------------------------------------------------------------
// Error
// ---------------------------------------------------------------------------

/// Errors from PTY operations.
#[derive(Debug)]
pub enum Error {
    /// An I/O or syscall error.
    Io(std::io::Error),
    /// `fork()` returned -1.
    ForkFailed(std::io::Error),
    /// The slave name returned by `ptsname_r` is not valid UTF-8 / C string.
    InvalidPtsName,
    /// A string argument contained an interior NUL byte.
    NulError(std::ffi::NulError),
}

impl fmt::Display for Error {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Error::Io(e) => write!(f, "I/O error: {e}"),
            Error::ForkFailed(e) => write!(f, "fork failed: {e}"),
            Error::InvalidPtsName => write!(f, "invalid PTS name"),
            Error::NulError(e) => write!(f, "NUL in string: {e}"),
        }
    }
}

impl std::error::Error for Error {}

impl From<std::io::Error> for Error {
    fn from(e: std::io::Error) -> Self {
        Error::Io(e)
    }
}

impl From<std::ffi::NulError> for Error {
    fn from(e: std::ffi::NulError) -> Self {
        Error::NulError(e)
    }
}

impl From<rustix::io::Errno> for Error {
    fn from(e: rustix::io::Errno) -> Self {
        Error::Io(e.into())
    }
}

// ---------------------------------------------------------------------------
// Pid wrapper
// ---------------------------------------------------------------------------

/// Process ID returned by `fork()`.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Pid(pub i32);

// ---------------------------------------------------------------------------
// ChildSetup — pre-allocated C strings for the child side of fork
// ---------------------------------------------------------------------------

/// All data the child process needs, pre-allocated before `fork()`.
///
/// After `fork()` we must only call async-signal-safe functions. This struct
/// ensures we never allocate in the child.
pub struct ChildSetup {
    /// Shell path as a C string (also `argv[0]`).
    shell: CString,
    /// Working directory.
    cwd: CString,
    /// NULL-terminated argv array. First element is `shell`.
    argv_ptrs: Vec<*const libc::c_char>,
    /// Owned CStrings for arguments (keeps memory alive).
    _argv_storage: Vec<CString>,
    /// `KEY=VALUE` pairs as CStrings.
    env_storage: Vec<CString>,
    /// NULL-terminated envp array passed to execve().
    envp_ptrs: Vec<*const libc::c_char>,
}

// SAFETY: The raw pointers in argv_ptrs / envp_ptrs point into CStrings
// owned by the same struct. We never share across threads before fork.
unsafe impl Send for ChildSetup {}

impl ChildSetup {
    /// Build a new `ChildSetup`.
    ///
    /// * `shell`  — absolute path to the shell, e.g. `/system/bin/sh`
    /// * `cwd`    — working directory for the child
    /// * `args`   — extra arguments after `argv[0]`
    /// * `env`    — environment variables as `KEY=VALUE` strings
    pub fn new(
        shell: &str,
        cwd: &str,
        args: &[&str],
        env: &[&str],
    ) -> Result<Self, Error> {
        let shell_c = CString::new(shell)?;
        let cwd_c = CString::new(cwd)?;

        // Build argv: [shell, args..., NULL]
        let mut argv_storage: Vec<CString> = Vec::with_capacity(args.len());
        let mut argv_ptrs: Vec<*const libc::c_char> = Vec::with_capacity(args.len() + 2);
        argv_ptrs.push(shell_c.as_ptr());
        for arg in args {
            let c = CString::new(*arg)?;
            argv_ptrs.push(c.as_ptr());
            argv_storage.push(c);
        }
        argv_ptrs.push(std::ptr::null());

        // Build envp: [KEY=VALUE, ..., NULL]
        let mut env_storage: Vec<CString> = Vec::with_capacity(env.len());
        let mut envp_ptrs: Vec<*const libc::c_char> = Vec::with_capacity(env.len() + 1);
        for e in env {
            let c = CString::new(*e)?;
            envp_ptrs.push(c.as_ptr());
            env_storage.push(c);
        }
        envp_ptrs.push(std::ptr::null());

        Ok(Self {
            shell: shell_c,
            cwd: cwd_c,
            argv_ptrs,
            _argv_storage: argv_storage,
            env_storage,
            envp_ptrs,
        })
    }
}

// ---------------------------------------------------------------------------
// Async-signal-safe helpers for the child process
// ---------------------------------------------------------------------------

/// Parse an ASCII decimal integer from a C string.
/// Async-signal-safe (no allocation). Returns -1 on invalid input.
pub fn libc_atoi(s: &[u8]) -> i32 {
    let mut result: i32 = 0;
    for &b in s {
        if b == b'.' || b == 0 {
            break;
        }
        if !b.is_ascii_digit() {
            return -1;
        }
        result = result * 10 + (b - b'0') as i32;
    }
    result
}

/// Close all file descriptors > 2 except `keep_fd`, by reading `/proc/self/fd`.
///
/// # Safety
/// Must only be called after `fork()` in the child process.
unsafe fn close_fds_except(keep_fd: RawFd) {
    let dir = libc::opendir(b"/proc/self/fd\0".as_ptr() as *const libc::c_char);
    if dir.is_null() {
        // Fallback: brute-force close fds 3..256
        for fd in 3..256 {
            if fd != keep_fd {
                libc::close(fd);
            }
        }
        return;
    }

    loop {
        let entry = libc::readdir(dir);
        if entry.is_null() {
            break;
        }
        let name = CStr::from_ptr((*entry).d_name.as_ptr());
        let fd_num = libc_atoi(name.to_bytes());
        if fd_num < 0 {
            continue; // "." or ".."
        }
        let dir_fd = libc::dirfd(dir);
        if fd_num > 2 && fd_num != keep_fd && fd_num != dir_fd {
            libc::close(fd_num);
        }
    }
    libc::closedir(dir);
}

// ---------------------------------------------------------------------------
// PTY helpers
// ---------------------------------------------------------------------------

/// Open `/dev/ptmx` and return the master fd + slave name.
fn open_ptmx() -> Result<(OwnedFd, String), Error> {
    let fd = unsafe {
        libc::open(
            b"/dev/ptmx\0".as_ptr() as *const libc::c_char,
            libc::O_RDWR | libc::O_CLOEXEC,
        )
    };
    if fd < 0 {
        return Err(Error::Io(std::io::Error::last_os_error()));
    }
    let master = unsafe { OwnedFd::from_raw_fd(fd) };

    // grantpt
    if unsafe { libc::grantpt(master.as_raw_fd()) } != 0 {
        return Err(Error::Io(std::io::Error::last_os_error()));
    }
    // unlockpt
    if unsafe { libc::unlockpt(master.as_raw_fd()) } != 0 {
        return Err(Error::Io(std::io::Error::last_os_error()));
    }
    // ptsname_r
    let mut buf = [0u8; 256];
    let rc = unsafe {
        libc::ptsname_r(
            master.as_raw_fd(),
            buf.as_mut_ptr() as *mut libc::c_char,
            buf.len(),
        )
    };
    if rc != 0 {
        return Err(Error::Io(std::io::Error::last_os_error()));
    }
    let name = CStr::from_bytes_until_nul(&buf)
        .map_err(|_| Error::InvalidPtsName)?
        .to_str()
        .map_err(|_| Error::InvalidPtsName)?
        .to_owned();

    Ok((master, name))
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/// Spawn a subprocess connected to a new PTY.
///
/// Returns `(master_fd, child_pid)` on success.
///
/// * `shell` — absolute path to the shell binary
/// * `cwd`   — working directory for the child
/// * `args`  — extra arguments (after argv[0])
/// * `env`   — environment variables as `KEY=VALUE`
/// * `rows`  — initial terminal rows
/// * `cols`  — initial terminal columns
pub fn create_subprocess(
    shell: &str,
    cwd: &str,
    args: &[&str],
    env: &[&str],
    rows: u16,
    cols: u16,
) -> Result<(OwnedFd, Pid), Error> {
    // Pre-allocate everything the child needs.
    let setup = ChildSetup::new(shell, cwd, args, env)?;

    let (master, slave_name) = open_ptmx()?;
    let slave_name_c = CString::new(slave_name)?;

    // Set initial window size on master.
    let ws = libc::winsize {
        ws_row: rows,
        ws_col: cols,
        ws_xpixel: 0,
        ws_ypixel: 0,
    };
    unsafe {
        libc::ioctl(master.as_raw_fd(), libc::TIOCSWINSZ, &ws);
    }

    let pid = unsafe { libc::fork() };
    if pid < 0 {
        return Err(Error::ForkFailed(std::io::Error::last_os_error()));
    }

    if pid == 0 {
        // ---- CHILD (async-signal-safe only) ----
        unsafe {
            // New session leader.
            libc::setsid();

            // Open slave PTY — becomes controlling terminal.
            let slave_fd = libc::open(slave_name_c.as_ptr(), libc::O_RDWR);
            if slave_fd < 0 {
                libc::_exit(127);
            }

            // Dup slave to stdin/stdout/stderr.
            libc::dup2(slave_fd, 0);
            libc::dup2(slave_fd, 1);
            libc::dup2(slave_fd, 2);

            // Close all other fds (including master via O_CLOEXEC, but be safe).
            close_fds_except(-1);

            // Change directory.
            libc::chdir(setup.cwd.as_ptr());

            // Exec the shell with pre-built envp (avoids putenv dangling pointers).
            libc::execve(setup.shell.as_ptr(), setup.argv_ptrs.as_ptr(), setup.envp_ptrs.as_ptr());

            // If execv returns, exit with error code.
            libc::_exit(127);
        }
    }

    // ---- PARENT ----
    Ok((master, Pid(pid)))
}

/// Set the window size on a PTY master file descriptor.
pub fn set_window_size(
    fd: &OwnedFd,
    rows: u16,
    cols: u16,
    cell_w: u16,
    cell_h: u16,
) -> Result<(), Error> {
    let ws = libc::winsize {
        ws_row: rows,
        ws_col: cols,
        ws_xpixel: cell_w,
        ws_ypixel: cell_h,
    };
    let rc = unsafe { libc::ioctl(fd.as_raw_fd(), libc::TIOCSWINSZ, &ws) };
    if rc < 0 {
        return Err(Error::Io(std::io::Error::last_os_error()));
    }
    Ok(())
}

/// Wait for a child process and return its exit status.
///
/// Returns the raw exit status from `waitpid`. Use `libc::WEXITSTATUS` to
/// extract the exit code if `libc::WIFEXITED` is true.
pub fn wait_for(pid: Pid) -> i32 {
    let mut status: libc::c_int = 0;
    loop {
        let rc = unsafe { libc::waitpid(pid.0, &mut status, 0) };
        if rc == -1 {
            let err = std::io::Error::last_os_error();
            if err.raw_os_error() == Some(libc::EINTR) {
                continue;
            }
            log::error!("waitpid({}) failed: {}", pid.0, err);
            return -1;
        }
        break;
    }
    status
}

/// Enable UTF-8 mode on a PTY master and configure sane termios defaults.
///
/// Sets `IUTF8` on input, disables `IXON`/`IXOFF` (software flow control).
pub fn set_utf8_mode(fd: &OwnedFd) -> Result<(), Error> {
    use rustix::termios::{self, InputModes, OptionalActions};

    let borrowed = unsafe { rustix::fd::BorrowedFd::borrow_raw(fd.as_raw_fd()) };
    let mut attrs = termios::tcgetattr(borrowed)?;

    // Enable UTF-8 mode.
    attrs.input_modes.insert(InputModes::IUTF8);
    // Disable software flow control.
    attrs.input_modes.remove(InputModes::IXON);
    attrs.input_modes.remove(InputModes::IXOFF);

    termios::tcsetattr(borrowed, OptionalActions::Now, &attrs)?;

    Ok(())
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::{Read, Write};
    use std::os::unix::io::AsRawFd;

    #[test]
    fn test_libc_atoi_valid() {
        assert_eq!(libc_atoi(b"123"), 123);
        assert_eq!(libc_atoi(b"42"), 42);
        assert_eq!(libc_atoi(b"7"), 7);
        assert_eq!(libc_atoi(b"999999"), 999999);
    }

    #[test]
    fn test_libc_atoi_dot() {
        // Stops at '.' (like dirent names won't have dots, but test boundary)
        assert_eq!(libc_atoi(b"."), 0);
        assert_eq!(libc_atoi(b"12.34"), 12);
    }

    #[test]
    fn test_libc_atoi_zero() {
        assert_eq!(libc_atoi(b"0"), 0);
        assert_eq!(libc_atoi(b"0\0rest"), 0);
    }

    #[test]
    fn test_child_setup_builds() {
        // Use a generic test path — not tied to any specific app
        const TEST_HOME: &str = "/data/data/com.nvterm/files/home";

        let setup = ChildSetup::new(
            "/system/bin/sh",
            TEST_HOME,
            &["-l"],
            &[&format!("HOME={TEST_HOME}"), "TERM=xterm-256color"],
        )
        .expect("ChildSetup::new should succeed");

        // argv: [shell, "-l", NULL] → 3 entries
        assert_eq!(setup.argv_ptrs.len(), 3);
        assert!(setup.argv_ptrs[2].is_null());

        // envp: [HOME=..., TERM=..., NULL] → 3 entries
        assert_eq!(setup.envp_ptrs.len(), 3);
        assert!(setup.envp_ptrs[2].is_null());

        // Verify shell path
        assert_eq!(setup.shell.to_str().unwrap(), "/system/bin/sh");
        assert_eq!(setup.cwd.to_str().unwrap(), TEST_HOME);
    }

    #[test]
    fn test_pty_open_and_ptsname() {
        let (master, name) = open_ptmx().expect("open_ptmx should succeed");
        assert!(master.as_raw_fd() >= 0);
        assert!(
            name.starts_with("/dev/pts/"),
            "slave name should start with /dev/pts/, got: {name}"
        );
    }

    #[test]
    fn test_termios_utf8() {
        use rustix::termios::{self, InputModes};

        let (master, _name) = open_ptmx().expect("open_ptmx should succeed");
        set_utf8_mode(&master).expect("set_utf8_mode should succeed");

        let borrowed = unsafe { rustix::fd::BorrowedFd::borrow_raw(master.as_raw_fd()) };
        let attrs = termios::tcgetattr(borrowed).expect("tcgetattr should succeed");

        assert!(
            attrs.input_modes.contains(InputModes::IUTF8),
            "IUTF8 should be set"
        );
        assert!(
            !attrs.input_modes.contains(InputModes::IXON),
            "IXON should be cleared"
        );
        assert!(
            !attrs.input_modes.contains(InputModes::IXOFF),
            "IXOFF should be cleared"
        );
    }

    #[test]
    fn test_winsize() {
        let (master, _name) = open_ptmx().expect("open_ptmx should succeed");
        set_window_size(&master, 24, 80, 8, 16).expect("set_window_size should succeed");

        // Read back the window size.
        let mut ws: libc::winsize = unsafe { std::mem::zeroed() };
        let rc = unsafe { libc::ioctl(master.as_raw_fd(), libc::TIOCGWINSZ, &mut ws) };
        assert_eq!(rc, 0, "ioctl TIOCGWINSZ should succeed");
        assert_eq!(ws.ws_row, 24);
        assert_eq!(ws.ws_col, 80);
        assert_eq!(ws.ws_xpixel, 8);
        assert_eq!(ws.ws_ypixel, 16);
    }

    #[test]
    fn test_spawn_system_shell() {
        let (master, pid) = create_subprocess(
            "/system/bin/sh",
            "/",
            &[],
            &[
                "PATH=/system/bin:/sbin",
                "TERM=dumb",
                "HOME=/",
            ],
            24,
            80,
        )
        .expect("create_subprocess should succeed");

        assert!(pid.0 > 0, "child pid should be positive");

        // Give the shell a moment to start, then send "exit\n".
        std::thread::sleep(std::time::Duration::from_millis(200));

        let mut file = unsafe { std::fs::File::from_raw_fd(master.as_raw_fd()) };
        file.write_all(b"exit\n").expect("write to master should succeed");

        // Prevent the OwnedFd from double-closing (File took ownership via from_raw_fd).
        std::mem::forget(master);

        // Read any output (non-blocking drain).
        let mut buf = [0u8; 4096];
        // Set non-blocking for the read so we don't hang.
        unsafe {
            let flags = libc::fcntl(file.as_raw_fd(), libc::F_GETFL);
            libc::fcntl(file.as_raw_fd(), libc::F_SETFL, flags | libc::O_NONBLOCK);
        }
        let _ = file.read(&mut buf); // Don't care about content, just drain.

        let status = wait_for(pid);
        let exited = libc::WIFEXITED(status);
        assert!(exited, "child should have exited normally");
        let code = libc::WEXITSTATUS(status);
        assert_eq!(code, 0, "exit code should be 0");

        // File will close the fd on drop.
        // Forget the file to avoid double-close since we already forgot master.
        // Actually File owns the fd now, let it close naturally.
    }
}
