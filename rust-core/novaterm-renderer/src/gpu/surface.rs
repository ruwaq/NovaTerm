// Android surface wrapper for wgpu.
//
// Wraps ANativeWindow → wgpu::Surface for Vulkan presentation.

use super::context::{GpuContext, GpuError};
use raw_window_handle::{
    AndroidNdkWindowHandle, HasDisplayHandle, HasWindowHandle,
    RawDisplayHandle, RawWindowHandle,
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
}

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
        let ptr = NonNull::new(native_window)
            .ok_or_else(|| GpuError::Other("null ANativeWindow".into()))?;

        let window = AndroidWindow { window: ptr };
        let surface = ctx.instance.create_surface(window).map_err(GpuError::Surface)?;

        let caps = surface.get_capabilities(&ctx.adapter);
        let format = caps.formats.iter().find(|f| f.is_srgb()).copied()
            .unwrap_or(caps.formats[0]);

        let present_mode = if caps.present_modes.contains(&wgpu::PresentMode::Mailbox) {
            wgpu::PresentMode::Mailbox
        } else {
            wgpu::PresentMode::AutoVsync
        };

        let config = wgpu::SurfaceConfiguration {
            usage: wgpu::TextureUsages::RENDER_ATTACHMENT | wgpu::TextureUsages::COPY_DST,
            format,
            width,
            height,
            present_mode,
            desired_maximum_frame_latency: 2,
            alpha_mode: wgpu::CompositeAlphaMode::Auto,
            view_formats: vec![],
        };
        surface.configure(&ctx.device, &config);

        log::info!("Surface: {}x{} {:?} {:?}", width, height, format, present_mode);

        Ok(AndroidSurface {
            surface: Some(surface),
            config: Some(config),
            width,
            height,
        })
    }

    /// Resize surface.
    pub fn resize(&mut self, ctx: &GpuContext, width: u32, height: u32) {
        if width == 0 || height == 0 { return; }
        self.width = width;
        self.height = height;
        if let (Some(s), Some(c)) = (&self.surface, &mut self.config) {
            c.width = width;
            c.height = height;
            s.configure(&ctx.device, c);
        }
    }

    /// Get current texture for rendering. Returns None if surface is lost.
    pub fn get_current_texture(&self) -> Option<wgpu::SurfaceTexture> {
        use wgpu::CurrentSurfaceTexture;
        let surface = self.surface.as_ref()?;
        match surface.get_current_texture() {
            CurrentSurfaceTexture::Success(tex) | CurrentSurfaceTexture::Suboptimal(tex) => {
                Some(tex)
            }
            other => {
                log::warn!("Surface not ready: {:?}", other);
                None
            }
        }
    }

    pub fn size(&self) -> (u32, u32) { (self.width, self.height) }

    pub fn format(&self) -> Option<wgpu::TextureFormat> {
        self.config.as_ref().map(|c| c.format)
    }

    pub fn is_attached(&self) -> bool { self.surface.is_some() }

    pub fn detach(&mut self) {
        self.surface = None;
        self.config = None;
        log::info!("Surface detached");
    }
}

impl Drop for AndroidSurface {
    fn drop(&mut self) { self.detach(); }
}
