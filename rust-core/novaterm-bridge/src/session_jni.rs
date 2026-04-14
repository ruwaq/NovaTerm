// JNI exports for unified RustSession.
// Package: com.novaterm.core.session.engine  Class: NativeSession
//
// jni 0.22: .with_env() returns EnvOutcome. Use .resolve_with::<LogContextErrorAndDefault, _>()
// for proper error handling (logs + clears pending exceptions).

use crate::session::RustSession;
use crate::session_map;
use jni::errors::LogContextErrorAndDefault;
use jni::objects::{JByteArray, JClass, JObjectArray, JString};
use jni::sys::{jboolean, jint, jintArray, jlong, JNI_FALSE, JNI_TRUE};
use jni::EnvUnowned;
use std::panic;
use std::ptr;

type JniResult<T> = Result<T, jni::errors::Error>;

/// Spawn a unified Rust session (PTY + VT + I/O threads).
///
/// # Safety
///
/// This function is safe to call from JNI as it:
/// - Validates JNI string handles before dereferencing
/// - Properly handles JNI exceptions from string conversion
/// - Ensures all JNI object references are properly managed
/// - Returns -1 on any error condition
///
/// Returns handle (>0) or -1 on error.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_novaterm_core_session_engine_NativeSession_nativeSpawn<'local>(
    mut unowned_env: EnvUnowned<'local>,
    _class: JClass<'local>,
    shell: JString<'local>,
    cwd: JString<'local>,
    args: JObjectArray<'local>,
    env_vars: JObjectArray<'local>,
    rows: jint,
    cols: jint,
) -> jlong {
    let result = panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let mut shell_str = String::new();
        let mut cwd_str = String::new();
        let mut args_vec: Vec<String> = Vec::new();
        let mut env_vec: Vec<String> = Vec::new();

        unowned_env.with_env(|e| -> JniResult<()> {
            shell_str = e.get_string(&shell)?.into();
            cwd_str = e.get_string(&cwd)?.into();

            let args_len = e.get_array_length(&args)?;
            for i in 0..args_len {
                let obj = e.get_object_array_element(&args, i as usize)?;
                let raw = obj.into_raw();
                if raw.is_null() {
                    continue;
                }
                let jstr = unsafe { JString::from_raw(e, raw) };
                let s = match e.get_string(&jstr) {
                    Ok(s) => s.into(),
                    Err(e) => {
                        log::warn!("Failed to get JNI string for args[{}] (handle={:p}): {}", i, raw, e);
                        continue;
                    }
                };
                args_vec.push(s);
            }

            let env_len = e.get_array_length(&env_vars)?;
            for i in 0..env_len {
                let obj = e.get_object_array_element(&env_vars, i as usize)?;
                let raw = obj.into_raw();
                if raw.is_null() {
                    continue;
                }
                let jstr = unsafe { JString::from_raw(e, raw) };
                let s = match e.get_string(&jstr) {
                    Ok(s) => s.into(),
                    Err(e) => {
                        log::warn!("Failed to get JNI string for env_vars[{}] (handle={:?}): {}", i, raw, e);
                        continue;
                    }
                };
                env_vec.push(s);
            }
            Ok(())
        }).resolve_with::<LogContextErrorAndDefault, _>(|| "nativeSpawn/params".to_string());

        if shell_str.is_empty() || cwd_str.is_empty() {
            log::error!("Empty shell or cwd");
            return -1;
        }

        let args_refs: Vec<&str> = args_vec.iter().map(|s| s.as_str()).collect();
        // env vars are "KEY=VALUE" strings — novaterm-pty expects &[&str]
        let env_refs: Vec<&str> = env_vec.iter().map(|s| s.as_str()).collect();

        let r = rows.clamp(1, 500) as u16;
        let c = cols.clamp(1, 500) as u16;

        match RustSession::spawn(&shell_str, &cwd_str, &args_refs, &env_refs, r, c) {
            Ok(session) => {
                let pid = session.pid();
                let handle = session_map::insert(session);
                log::info!("RustSession spawned: handle={} pid={} {}x{}", handle, pid, r, c);
                handle as jlong
            }
            Err(e) => {
                log::error!("RustSession spawn failed: {}", e);
                -1
            }
        }
    }));
    result.unwrap_or(-1)
}

/// Process pending PTY output and return the number of bytes processed.
///
/// # Safety
///
/// This function is safe to call from JNI as it:
/// - Uses panic::catch_unwind to handle any runtime errors
/// - Properly propagates the result through the JNI boundary
/// - Returns 0 on any error condition
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_novaterm_core_session_engine_NativeSession_nativeProcessPending(
    _env: EnvUnowned,
    _class: JClass,
    handle: jlong,
) -> jint {
    panic::catch_unwind(|| {
        session_map::with_session(handle as u64, |s| s.process_pending() as jint)
            .unwrap_or(0)
    }).unwrap_or(0)
}

/// Write user input data to the PTY.
///
/// # Safety
///
/// This function is safe to call from JNI as it:
/// - Validates the byte array before processing
/// - Uses panic::catch_unwind to handle any runtime errors
/// - Properly propagates the result through the JNI boundary
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_novaterm_core_session_engine_NativeSession_nativeWrite<'local>(
    mut unowned_env: EnvUnowned<'local>,
    _class: JClass<'local>,
    handle: jlong,
    data: JByteArray<'local>,
) {
    let _ = panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        unowned_env.with_env(|e| -> JniResult<()> {
            let bytes = e.convert_byte_array(&data)?;
            session_map::with_session_ref(handle as u64, |s| s.write(&bytes));
            Ok(())
        }).resolve_with::<LogContextErrorAndDefault, _>(|| "nativeWrite".to_string());
    }));
}

/// Get the terminal grid as a flat int[] array.
///
/// # Safety
///
/// This function is safe to call from JNI as it:
/// - Validates the session handle before accessing
/// - Properly handles JNI exceptions during array creation
/// - Returns null on any error condition
/// - Ensures all JNI object references are properly managed
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_novaterm_core_session_engine_NativeSession_nativeGetGrid<'local>(
    mut unowned_env: EnvUnowned<'local>,
    _class: JClass<'local>,
    handle: jlong,
) -> jintArray {
    let result = panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let snap = match session_map::with_session(handle as u64, |s| s.snapshot()) {
            Some(s) => s,
            None => return ptr::null_mut(),
        };
        if snap.cells.is_empty() { return ptr::null_mut(); }

        let cap = (snap.rows as usize)
            .checked_mul(snap.cols as usize)
            .and_then(|n| n.checked_mul(4))
            .unwrap_or(0);
        let mut flat = Vec::with_capacity(cap);
        for cell in &snap.cells {
            flat.push(cell.character as u32 as i32);
            flat.push(cell.fg as i32);
            flat.push(cell.bg as i32);
            flat.push(cell.flags as i32);
        }

        let mut out: jintArray = ptr::null_mut();
        unowned_env.with_env(|e| -> JniResult<()> {
            let arr = e.new_int_array(flat.len())?;
            arr.set_region(e, 0, &flat)?;
            out = arr.into_raw();
            Ok(())
        }).resolve_with::<LogContextErrorAndDefault, _>(|| "nativeGetGrid".to_string());
        out
    }));
    result.unwrap_or(ptr::null_mut())
}

/// Get cursor as int[4].
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_novaterm_core_session_engine_NativeSession_nativeGetCursor<'local>(
    mut unowned_env: EnvUnowned<'local>,
    _class: JClass<'local>,
    handle: jlong,
) -> jintArray {
    let result = panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let c = match session_map::with_session_ref(handle as u64, |s| s.cursor_state()) {
            Some(c) => c,
            None => return ptr::null_mut(),
        };
        let data = [c.row, c.col, c.shape as i32, if c.visible { 1 } else { 0 }];
        let mut out: jintArray = ptr::null_mut();
        unowned_env.with_env(|e| -> JniResult<()> {
            let arr = e.new_int_array(4)?;
            arr.set_region(e, 0, &data)?;
            out = arr.into_raw();
            Ok(())
        }).resolve_with::<LogContextErrorAndDefault, _>(|| "nativeGetCursor".to_string());
        out
    }));
    result.unwrap_or(ptr::null_mut())
}

/// Resize PTY + parser.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_novaterm_core_session_engine_NativeSession_nativeResize(
    _env: EnvUnowned, _class: JClass, handle: jlong, rows: jint, cols: jint,
) {
    let _ = panic::catch_unwind(|| {
        let r = rows.clamp(1, 500) as u16;
        let c = cols.clamp(1, 500) as u16;
        session_map::with_session(handle as u64, |s| s.resize(r, c));
    });
}

/// Get child PID.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_novaterm_core_session_engine_NativeSession_nativeGetPid(
    _env: EnvUnowned, _class: JClass, handle: jlong,
) -> jint {
    panic::catch_unwind(|| {
        session_map::with_session_ref(handle as u64, |s| s.pid()).unwrap_or(-1)
    }).unwrap_or(-1)
}

/// Stop + destroy session.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_novaterm_core_session_engine_NativeSession_nativeDestroy(
    _env: EnvUnowned, _class: JClass, handle: jlong,
) {
    let _ = panic::catch_unwind(|| { session_map::destroy(handle as u64); });
}

/// Check for pending PTY output.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_novaterm_core_session_engine_NativeSession_nativeHasPending(
    _env: EnvUnowned, _class: JClass, handle: jlong,
) -> jboolean {
    let r = panic::catch_unwind(|| {
        session_map::with_session_ref(handle as u64, |s| s.has_pending_output()).unwrap_or(false)
    });
    if r.unwrap_or(false) { JNI_TRUE } else { JNI_FALSE }
}

/// Active session count.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_novaterm_core_session_engine_NativeSession_nativeSessionCount(
    _env: EnvUnowned, _class: JClass,
) -> jint {
    session_map::active_count() as jint
}