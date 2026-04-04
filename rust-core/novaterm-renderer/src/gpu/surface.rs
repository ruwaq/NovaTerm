// Android surface wrapper for wgpu.
//
// Wraps ANativeWindow → wgpu::Surface for Vulkan presentation.
// Multi-GPU compatible: adapts present mode and format per vendor.

use super::context::{GpuContext, GpuError, GpuVendor};
use raw_window_handle::{
    AndroidNdkWindowHandle, HasDisplayHandle, HasWindowHandle, RawDisplayHandle, RawWindowHandle,
};
use std::ptr::NonNull;

/// Android window handle wrapper.
struct AndroidWindow {
    window: NonNull<std::ffi::c_void>,
}

impl HasWindowHandle for AndroidWindow {
    fn window_handle(
        &self,
    ) -> Result<raw_window_handle::WindowHandle<'_>, raw_window_handle::HandleError> {
        let handle = AndroidNdkWindowHandle::new(self.window);
        let raw = RawWindowHandle::AndroidNdk(handle);
        Ok(unsafe { raw_window_handle::WindowHandle::borrow_raw(raw) })
    }
}

impl HasDisplayHandle for AndroidWindow {
    fn display_handle(
        &self,
    ) -> Result<raw_window_handle::DisplayHandle<'_>, raw_window_handle::HandleError> {
        let raw = RawDisplayHandle::Android(raw_window_handle::AndroidDisplayHandle::new());
        Ok(unsafe { raw_window_handle::DisplayHandle::borrow_raw(raw) })
    }
}

unsafe impl Send for AndroidWindow {}
unsafe impl Sync for AndroidWindow {}

/// wgpu surface wrapping an Android native window.
pub struct AndroidSurface {
    surface: Option<wgpu::Surface<'static>>,
    config: Option<wgpu::SurfaceConfiguration>,
    width: u32,
    height: u32,
    /// Track consecutive surface errors for recovery decisions
    error_count: u32,
}

/// Max consecutive surface errors before giving up on a surface.
const MAX_SURFACE_ERRORS: u32 = 3;

impl AndroidSurface {
    /// Attach an ANativeWindow to create a wgpu surface.
    ///
    /// # Safety
    /// `native_window` must be a valid ANativeWindow pointer.
    pub unsafe fn attach(
        ctx: &GpuContext,
        native_window: *mut std::ffi::c_void,
        width: u32,
        height: u32,
    ) -> Result<Self, GpuError> {
        let ptr =
            NonNull::new(native_window).ok_or_else(|| GpuError::Other("null ANativeWindow".into()))?;

        let window = AndroidWindow { window: ptr };
        let surface = ctx
            .instance
            .create_surface(window)
            .map_err(GpuError::Surface)?;

        let caps = surface.get_capabilities(&ctx.adapter);

        if caps.formats.is_empty() {
            return Err(GpuError::Other(
                "GPU surface has no supported formats".into(),
            ));
        }

        // Format selection: prefer sRGB, fall back to first available
        let format = caps
            .formats
            .iter()
            .find(|f| f.is_srgb())
            .copied()
            .unwrap_or(caps.formats[0]);

        // Present mode selection — vendor-aware
        let present_mode = select_present_mode(&caps.present_modes, ctx.vendor());

        let config = wgpu::SurfaceConfiguration {
            usage: wgpu::TextureUsages::RENDER_ATTACHMENT | wgpu::TextureUsages::COPY_DST,
            format,
            width,
            height,
            present_mode,
            desired_maximum_frame_latency: 2,
            alpha_mode: select_alpha_mode(&caps.alpha_modes),
            view_formats: vec![],
        };
        surface.configure(&ctx.device, &config);

        log::info!(
            "Surface attached: {}x{} {:?} {:?} alpha={:?}",
            width,
            height,
            format,
            present_mode,
            config.alpha_mode,
        );

        Ok(AndroidSurface {
            surface: Some(surface),
            config: Some(config),
            width,
            height,
            error_count: 0,
        })
    }

    /// Resize surface.
    pub fn resize(&mut self, ctx: &GpuContext, width: u32, height: u32) {
        if width == 0 || height == 0 {
            return;
        }
        self.width = width;
        self.height = height;
        if let (Some(s), Some(c)) = (&self.surface, &mut self.config) {
            c.width = width;
            c.height = height;
            s.configure(&ctx.device, c);
            self.error_count = 0;
        }
    }

    /// Reconfigure the surface (after surface loss or format change).
    pub fn reconfigure(&mut self, ctx: &GpuContext) {
        if let (Some(s), Some(c)) = (&self.surface, &self.config) {
            s.configure(&ctx.device, c);
            self.error_count = 0;
            log::info!("Surface reconfigured");
        }
    }

    /// Get current texture for rendering. Handles surface loss with recovery.
    pub fn get_current_texture(&mut self, ctx: &GpuContext) -> Option<wgpu::SurfaceTexture> {
        let surface = self.surface.as_ref()?;
        match surface.get_current_texture() {
            wgpu::CurrentSurfaceTexture::Success(tex) => {
                self.error_count = 0;
                Some(tex)
            }
            wgpu::CurrentSurfaceTexture::Suboptimal(tex) => {
                // Suboptimal is OK to use, but schedule a reconfigure
                log::debug!("Surface suboptimal — will reconfigure on next resize");
                Some(tex)
            }
            wgpu::CurrentSurfaceTexture::Lost => {
                self.error_count += 1;
                if self.error_count <= MAX_SURFACE_ERRORS {
                    log::warn!(
                        "Surface lost (attempt {}/{}), reconfiguring...",
                        self.error_count,
                        MAX_SURFACE_ERRORS,
                    );
                    self.reconfigure(ctx);
                } else {
                    log::error!("Surface lost after {} attempts, giving up", MAX_SURFACE_ERRORS);
                }
                None
            }
            other => {
                self.error_count += 1;
                log::warn!("Surface not ready: {:?} (error #{})", other, self.error_count);
                None
            }
        }
    }

    pub fn size(&self) -> (u32, u32) {
        (self.width, self.height)
    }

    pub fn format(&self) -> Option<wgpu::TextureFormat> {
        self.config.as_ref().map(|c| c.format)
    }

    pub fn is_attached(&self) -> bool {
        self.surface.is_some()
    }

    /// Check if surface has exceeded error limit and should be abandoned.
    pub fn is_errored(&self) -> bool {
        self.error_count > MAX_SURFACE_ERRORS
    }

    pub fn detach(&mut self) {
        self.surface = None;
        self.config = None;
        self.error_count = 0;
        log::info!("Surface detached");
    }
}

impl Drop for AndroidSurface {
    fn drop(&mut self) {
        self.detach();
    }
}

/// Select present mode based on GPU vendor.
///
/// - Mailbox: lowest latency (triple buffering). Preferred for Adreno and Xclipse.
/// - Fifo: guaranteed available, VSync-locked. Safe fallback.
/// - AutoVsync: let wgpu choose.
fn select_present_mode(
    modes: &[wgpu::PresentMode],
    vendor: GpuVendor,
) -> wgpu::PresentMode {
    match vendor {
        // Adreno: Mailbox works well on 7xx+, but some 6xx drivers have bugs.
        // Prefer Mailbox if available, fall back to Fifo.
        GpuVendor::Qualcomm => {
            if modes.contains(&wgpu::PresentMode::Mailbox) {
                wgpu::PresentMode::Mailbox
            } else {
                wgpu::PresentMode::Fifo
            }
        }
        // Mali: Fifo is most reliable. Mailbox can stutter on older G-series.
        GpuVendor::Arm => {
            if modes.contains(&wgpu::PresentMode::Mailbox) {
                wgpu::PresentMode::Mailbox
            } else {
                wgpu::PresentMode::Fifo
            }
        }
        // PowerVR: stick to Fifo for maximum compatibility.
        GpuVendor::Imagination => wgpu::PresentMode::Fifo,
        // Xclipse (RDNA): Mailbox works great.
        GpuVendor::Samsung => {
            if modes.contains(&wgpu::PresentMode::Mailbox) {
                wgpu::PresentMode::Mailbox
            } else {
                wgpu::PresentMode::Fifo
            }
        }
        // Software/Unknown: Fifo for safety.
        _ => wgpu::PresentMode::Fifo,
    }
}

/// Select alpha compositing mode.
fn select_alpha_mode(modes: &[wgpu::CompositeAlphaMode]) -> wgpu::CompositeAlphaMode {
    // Prefer Opaque (no alpha blending with system compositor — best perf).
    // Then Inherit (let the system decide). Auto as last resort.
    if modes.contains(&wgpu::CompositeAlphaMode::Opaque) {
        wgpu::CompositeAlphaMode::Opaque
    } else if modes.contains(&wgpu::CompositeAlphaMode::Inherit) {
        wgpu::CompositeAlphaMode::Inherit
    } else {
        wgpu::CompositeAlphaMode::Auto
    }
}
