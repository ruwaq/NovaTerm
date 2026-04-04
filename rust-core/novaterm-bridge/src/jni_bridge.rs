// JNI exports for NovaTerm Rust core.
//
// jni 0.22: native methods receive EnvUnowned (FFI-safe).
// Use .with_env(|env| { ... Ok::<_, jni::errors::Error>(()) }) for Env API.
//
// Package: com.novaterm.core.session.engine  Class: NativeTerminal

use crate::handle_map;
use jni::objects::{JByteArray, JClass};
use jni::sys::{jboolean, jint, jintArray, jlong, jstring, JNI_FALSE, JNI_TRUE};
use jni::EnvUnowned;
use novaterm_vt::TerminalBackend;
use std::panic;
use std::ptr;

type JniResult<T> = Result<T, jni::errors::Error>;

/// Initialize Rust logger → Android logcat.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_novaterm_core_session_engine_NativeTerminal_nativeInit(
    _env: EnvUnowned,
    _class: JClass,
) {
    #[cfg(target_os = "android")]
    {
        android_logger::init_once(
            android_logger::Config::default()
                .with_max_level(log::LevelFilter::Debug)
                .with_tag("NovaTerm-Rust"),
        );
    }
    log::info!("NovaTerm Rust core initialized (alacritty_terminal 0.25.1)");
}

/// Create backend. Returns handle (>0) or -1.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_novaterm_core_session_engine_NativeTerminal_nativeCreate(
    _env: EnvUnowned,
    _class: JClass,
    rows: jint,
    cols: jint,
) -> jlong {
    panic::catch_unwind(|| {
        if rows <= 0 || cols <= 0 || rows > 500 || cols > 500 {
            return -1;
        }
        handle_map::create_backend(rows as u32, cols as u32) as jlong
    })
    .unwrap_or(-1)
}

/// Destroy backend.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_novaterm_core_session_engine_NativeTerminal_nativeDestroy(
    _env: EnvUnowned,
    _class: JClass,
    handle: jlong,
) {
    let _ = panic::catch_unwind(|| {
        handle_map::destroy_backend(handle as u64);
    });
}

/// Feed PTY output bytes into VT parser (HOT PATH).
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_novaterm_core_session_engine_NativeTerminal_nativeProcessBytes<'local>(
    mut unowned_env: EnvUnowned<'local>,
    _class: JClass<'local>,
    handle: jlong,
    data: JByteArray<'local>,
) {
    let _ = panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let _ = unowned_env.with_env(|env| -> JniResult<()> {
            let bytes = env.convert_byte_array(&data)?;
            handle_map::with_backend(handle as u64, |backend| {
                backend.process_bytes(&bytes);
            });
            Ok(())
        });
    }));
}

/// Resize terminal grid.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_novaterm_core_session_engine_NativeTerminal_nativeResize(
    _env: EnvUnowned,
    _class: JClass,
    handle: jlong,
    rows: jint,
    cols: jint,
) {
    let _ = panic::catch_unwind(|| {
        if rows <= 0 || cols <= 0 || rows > 500 || cols > 500 {
            return;
        }
        handle_map::with_backend(handle as u64, |backend| {
            backend.resize(rows as u32, cols as u32);
        });
    });
}

/// Get visible grid as flat int[]: [char, fg, bg, flags] per cell.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_novaterm_core_session_engine_NativeTerminal_nativeGetGrid<'local>(
    mut unowned_env: EnvUnowned<'local>,
    _class: JClass<'local>,
    handle: jlong,
) -> jintArray {
    let result = panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let snapshot = match handle_map::with_backend(handle as u64, |b| b.snapshot()) {
            Some(s) => s,
            None => return ptr::null_mut(),
        };

        // Guard: empty snapshot → return null (#9)
        if snapshot.cells.is_empty() {
            return ptr::null_mut();
        }

        // Guard: unreasonable grid dimensions → return null (#11)
        if snapshot.rows > 500 || snapshot.cols > 500 {
            return ptr::null_mut();
        }

        // Checked multiplication to avoid capacity overflow (#21)
        let cap = (snapshot.rows as usize)
            .checked_mul(snapshot.cols as usize)
            .and_then(|n| n.checked_mul(4))
            .unwrap_or(0);
        let mut flat = Vec::with_capacity(cap);
        for cell in &snapshot.cells {
            flat.push(cell.character as u32 as i32);
            flat.push(cell.fg as i32);
            flat.push(cell.bg as i32);
            flat.push(cell.flags as i32);
        }

        let mut out: jintArray = ptr::null_mut();
        let _ = unowned_env.with_env(|env| -> JniResult<()> {
            let arr = env.new_int_array(flat.len())?;
            arr.set_region(env, 0, &flat)?;
            out = arr.into_raw();
            Ok(())
        });
        out
    }));
    result.unwrap_or(ptr::null_mut())
}

/// Get cursor as int[4]: [row, col, shape, visible].
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_novaterm_core_session_engine_NativeTerminal_nativeGetCursor<'local>(
    mut unowned_env: EnvUnowned<'local>,
    _class: JClass<'local>,
    handle: jlong,
) -> jintArray {
    let result = panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let cursor = match handle_map::with_backend(handle as u64, |b| b.cursor_state()) {
            Some(c) => c,
            None => return ptr::null_mut(),
        };

        let data = [
            cursor.row,
            cursor.col,
            cursor.shape as i32,
            if cursor.visible { 1 } else { 0 },
        ];

        let mut out: jintArray = ptr::null_mut();
        let _ = unowned_env.with_env(|env| -> JniResult<()> {
            let arr = env.new_int_array(4)?;
            arr.set_region(env, 0, &data)?;
            out = arr.into_raw();
            Ok(())
        });
        out
    }));
    result.unwrap_or(ptr::null_mut())
}

/// Get terminal title.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_novaterm_core_session_engine_NativeTerminal_nativeGetTitle<'local>(
    mut unowned_env: EnvUnowned<'local>,
    _class: JClass<'local>,
    handle: jlong,
) -> jstring {
    let result = panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let title = handle_map::with_backend(handle as u64, |_b| String::new())
            .unwrap_or_default();

        let mut out: jstring = ptr::null_mut();
        let _ = unowned_env.with_env(|env| -> JniResult<()> {
            let s = env.new_string(&title)?;
            out = s.into_raw();
            Ok(())
        });
        out
    }));
    result.unwrap_or(ptr::null_mut())
}

/// Scroll viewport.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_novaterm_core_session_engine_NativeTerminal_nativeScroll(
    _env: EnvUnowned,
    _class: JClass,
    handle: jlong,
    delta: jint,
) {
    let _ = panic::catch_unwind(|| {
        handle_map::with_backend(handle as u64, |b| b.scroll(delta));
    });
}

/// Reset terminal.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_novaterm_core_session_engine_NativeTerminal_nativeReset(
    _env: EnvUnowned,
    _class: JClass,
    handle: jlong,
) {
    let _ = panic::catch_unwind(|| {
        handle_map::with_backend(handle as u64, |b| b.reset());
    });
}

/// Check for pending events.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_novaterm_core_session_engine_NativeTerminal_nativeHasEvents(
    _env: EnvUnowned,
    _class: JClass,
    handle: jlong,
) -> jboolean {
    let result = panic::catch_unwind(|| {
        handle_map::with_backend(handle as u64, |b| !b.drain_events().is_empty())
            .unwrap_or(false)
    });
    if result.unwrap_or(false) { JNI_TRUE } else { JNI_FALSE }
}

/// Drain bytes that need to be written back to the PTY (DA responses, etc.)
/// Returns null if no bytes pending or handle invalid.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_novaterm_core_session_engine_NativeTerminal_nativeDrainPtyWrites<'local>(
    mut unowned_env: EnvUnowned<'local>,
    _class: JClass<'local>,
    handle: jlong,
) -> jni::sys::jbyteArray {
    let result = panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let bytes = match handle_map::with_backend(handle as u64, |b| b.drain_pty_writes()) {
            Some(b) if !b.is_empty() => b,
            _ => return ptr::null_mut(),
        };

        let mut out: jni::sys::jbyteArray = ptr::null_mut();
        let _ = unowned_env.with_env(|env| -> JniResult<()> {
            let arr = env.byte_array_from_slice(&bytes)?;
            out = arr.into_raw();
            Ok(())
        });
        out
    }));
    result.unwrap_or(ptr::null_mut())
}

/// Active backend count (debug).
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_novaterm_core_session_engine_NativeTerminal_nativeActiveCount(
    _env: EnvUnowned,
    _class: JClass,
) -> jint {
    handle_map::active_count() as jint
}
