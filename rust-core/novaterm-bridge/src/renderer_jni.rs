// JNI exports for the GPU renderer.
// Package: com.novaterm.core.session.engine  Class: NativeRenderer
//
// Manages VulkanRenderer lifecycle from Kotlin via JNI handles.
// Includes GPU diagnostics (vendor, name, capabilities) for multi-GPU support.

#[cfg(feature = "gpu")]
use crate::renderer_map;
#[cfg(feature = "gpu")]
use crate::session_map;
#[cfg(feature = "gpu")]
use jni::objects::{JClass, JObject};
#[cfg(feature = "gpu")]
use jni::sys::{jboolean, jint, jlong, jstring, JNI_FALSE, JNI_TRUE};
#[cfg(feature = "gpu")]
use jni::EnvUnowned;
#[cfg(feature = "gpu")]
use novaterm_renderer::VulkanRenderer;
#[cfg(feature = "gpu")]
use std::panic;

// FFI bindings for ANativeWindow (from Android NDK).
// Using raw FFI instead of ndk crate for simplicity.
#[cfg(feature = "gpu")]
extern "C" {
    fn ANativeWindow_fromSurface(
        env: *mut std::ffi::c_void,
        surface: *mut std::ffi::c_void,
    ) -> *mut std::ffi::c_void;
    fn ANativeWindow_release(window: *mut std::ffi::c_void);
}

/// Create a VulkanRenderer. Returns handle (>0) or -1 on error.
#[cfg(feature = "gpu")]
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_novaterm_core_session_engine_NativeRenderer_nativeCreateGpu(
    _env: EnvUnowned,
    _class: JClass,
) -> jlong {
    let result = panic::catch_unwind(|| match VulkanRenderer::new() {
        Ok(renderer) => {
            let handle = renderer_map::insert(renderer);
            log::info!("NativeRenderer created: handle={}", handle);
            handle as jlong
        }
        Err(e) => {
            log::error!("NativeRenderer creation failed: {}", e);
            -1
        }
    });
    result.unwrap_or(-1)
}

/// Attach a Surface to the renderer for presentation.
/// Gets ANativeWindow from the Java Surface object via NDK.
#[cfg(feature = "gpu")]
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_novaterm_core_session_engine_NativeRenderer_nativeAttachSurface<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _class: JClass<'local>,
    handle: jlong,
    surface: JObject<'local>,
    width: jint,
    height: jint,
) {
    let _ = panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        // Get raw JNIEnv and Surface jobject pointers for NDK call
        let mut env_ptr: *mut std::ffi::c_void = std::ptr::null_mut();
        let mut surface_ptr: *mut std::ffi::c_void = std::ptr::null_mut();

        let _ = env.with_env(|e| -> Result<(), jni::errors::Error> {
            env_ptr = e.get_raw() as *mut std::ffi::c_void;
            surface_ptr = surface.as_raw() as *mut std::ffi::c_void;
            Ok(())
        });

        if env_ptr.is_null() || surface_ptr.is_null() {
            log::error!("NativeRenderer: null env or surface pointer");
            return;
        }

        let native_window = unsafe { ANativeWindow_fromSurface(env_ptr, surface_ptr) };
        if native_window.is_null() {
            log::error!("NativeRenderer: ANativeWindow_fromSurface returned null");
            return;
        }

        let w = width.max(1) as u32;
        let h = height.max(1) as u32;

        let attached = renderer_map::with_renderer(handle as u64, |r| {
            let result = unsafe { r.attach_surface(native_window, w, h) };
            if let Err(ref e) = result {
                log::error!("NativeRenderer: attach_surface failed: {}", e);
            }
            result.is_ok()
        });

        // Release the native window reference (wgpu Surface holds its own ref)
        unsafe {
            ANativeWindow_release(native_window);
        }

        if attached != Some(true) {
            log::error!("NativeRenderer: attach failed for handle={}", handle);
        }
    }));
}

/// Detach the surface (e.g., when Activity is backgrounded).
#[cfg(feature = "gpu")]
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_novaterm_core_session_engine_NativeRenderer_nativeDetachSurface(
    _env: EnvUnowned,
    _class: JClass,
    handle: jlong,
) {
    let _ = panic::catch_unwind(|| {
        renderer_map::with_renderer(handle as u64, |r| r.detach_surface());
    });
}

/// Render a frame. Processes pending PTY output from the session,
/// takes a grid snapshot, and renders it via the GPU pipeline.
/// Returns JNI_TRUE if frame was presented, JNI_FALSE otherwise.
#[cfg(feature = "gpu")]
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_novaterm_core_session_engine_NativeRenderer_nativeRenderFrame(
    _env: EnvUnowned,
    _class: JClass,
    handle: jlong,
    session_handle: jlong,
) -> jboolean {
    let result = panic::catch_unwind(|| {
        // Process pending PTY output in the session
        let snapshot = session_map::with_session(session_handle as u64, |s| {
            s.process_pending();
            s.snapshot()
        });

        let snapshot = match snapshot {
            Some(snap) => snap,
            None => return JNI_FALSE,
        };

        // Render the snapshot
        let presented = renderer_map::with_renderer(handle as u64, |r| r.render_frame(&snapshot));

        if presented == Some(true) {
            JNI_TRUE
        } else {
            JNI_FALSE
        }
    });
    result.unwrap_or(JNI_FALSE)
}

/// Resize the rendering surface.
#[cfg(feature = "gpu")]
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_novaterm_core_session_engine_NativeRenderer_nativeResizeSurface(
    _env: EnvUnowned,
    _class: JClass,
    handle: jlong,
    width: jint,
    height: jint,
) {
    let _ = panic::catch_unwind(|| {
        let w = width.max(1) as u32;
        let h = height.max(1) as u32;
        renderer_map::with_renderer(handle as u64, |r| r.resize_surface(w, h));
    });
}

/// Destroy the GPU renderer and release all resources.
#[cfg(feature = "gpu")]
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_novaterm_core_session_engine_NativeRenderer_nativeDestroyGpu(
    _env: EnvUnowned,
    _class: JClass,
    handle: jlong,
) {
    let _ = panic::catch_unwind(|| {
        renderer_map::destroy(handle as u64);
        log::info!("NativeRenderer destroyed: handle={}", handle);
    });
}

/// Get GPU information as a pipe-delimited string:
/// "name|vendor|driver|backend|device_type|max_tex|max_ssbo|max_invocations"
///
/// Returns null if handle is invalid.
#[cfg(feature = "gpu")]
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_novaterm_core_session_engine_NativeRenderer_nativeGetGpuInfo<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _class: JClass<'local>,
    handle: jlong,
) -> jstring {
    let result = panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let info_str = renderer_map::with_renderer(handle as u64, |r| {
            let info = r.gpu_info();
            format!(
                "{}|{}|{}|{}|{}|{}|{}|{}",
                info.name,
                info.vendor,
                info.driver,
                info.backend,
                info.device_type,
                info.max_texture_2d,
                info.max_storage_buffer,
                info.max_workgroup_invocations,
            )
        });

        match info_str {
            Some(s) => {
                let mut jstr: jstring = std::ptr::null_mut();
                let _ = env.with_env(|e| -> Result<(), jni::errors::Error> {
                    match e.new_string(&s) {
                        Ok(js) => {
                            jstr = js.into_raw();
                        }
                        Err(err) => {
                            log::error!("Failed to create Java string: {}", err);
                        }
                    }
                    Ok(())
                });
                jstr
            }
            None => std::ptr::null_mut(),
        }
    }));
    result.unwrap_or(std::ptr::null_mut())
}

/// Check if the renderer recommends falling back to software rendering.
/// Returns JNI_TRUE if too many consecutive render errors have occurred.
#[cfg(feature = "gpu")]
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_novaterm_core_session_engine_NativeRenderer_nativeShouldFallback(
    _env: EnvUnowned,
    _class: JClass,
    handle: jlong,
) -> jboolean {
    let result = panic::catch_unwind(|| {
        let should = renderer_map::with_renderer(handle as u64, |r| r.should_fallback());
        if should == Some(true) {
            JNI_TRUE
        } else {
            JNI_FALSE
        }
    });
    result.unwrap_or(JNI_FALSE)
}
