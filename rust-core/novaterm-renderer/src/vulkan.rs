// Vulkan renderer — wgpu compute shader pipeline (Phase 2b).
//
// Ties together GpuContext, AndroidSurface, TerminalPipeline, GlyphAtlas,
// AtlasTexture, CellBufferManager, UniformBufferManager, and output texture
// into a complete GPU rendering pipeline.
//
// Architecture (Zutty pattern):
//   1. GlyphAtlas rasterizes glyphs on CPU -> RGBA bitmap (size adapts to GPU)
//   2. CellBufferManager packs cell data into a GPU storage buffer
//   3. Compute shader: one invocation per cell, copies glyph from atlas
//   4. Output texture is copied to surface texture, then presented
//
// Multi-GPU: Adapts to Adreno, Mali, PowerVR, Xclipse, and software renderers.
// Handles surface loss/recovery, vendor-specific present modes, and adaptive
// atlas sizing based on GPU max texture dimension.

#[cfg(feature = "vulkan")]
use crate::atlas::{GlyphAtlas, ATLAS_HEIGHT, ATLAS_WIDTH};
#[cfg(feature = "vulkan")]
use crate::gpu::atlas_texture::AtlasTexture;
#[cfg(feature = "vulkan")]
use crate::gpu::buffers::{CellBufferManager, UniformBufferManager, Uniforms};
#[cfg(feature = "vulkan")]
use crate::gpu::context::{GpuContext, GpuError, GpuInfo};
#[cfg(feature = "vulkan")]
use crate::gpu::pipeline::TerminalPipeline;
#[cfg(feature = "vulkan")]
use crate::gpu::surface::AndroidSurface;
#[cfg(feature = "vulkan")]
use crate::traits::{RenderConfig, Renderer};
#[cfg(feature = "vulkan")]
use novaterm_vt::GridSnapshot;

/// Default grid dimensions used before surface is attached.
#[cfg(feature = "vulkan")]
const DEFAULT_ROWS: u32 = 24;
#[cfg(feature = "vulkan")]
const DEFAULT_COLS: u32 = 80;

/// GPU-accelerated terminal renderer using wgpu compute shaders.
#[cfg(feature = "vulkan")]
pub struct VulkanRenderer {
    ctx: GpuContext,
    surface: Option<AndroidSurface>,
    pipeline: TerminalPipeline,
    atlas: GlyphAtlas,
    atlas_texture: AtlasTexture,
    cell_buffer: CellBufferManager,
    uniform_buffer: UniformBufferManager,
    output_texture: Option<wgpu::Texture>,
    output_view: Option<wgpu::TextureView>,
    font_size: f32,
    grid_rows: u32,
    grid_cols: u32,
    /// Actual atlas dimensions (may be smaller than ATLAS_WIDTH/HEIGHT on weaker GPUs)
    atlas_w: u32,
    atlas_h: u32,
    /// Consecutive render failures for fallback decisions
    render_errors: u32,
}

/// Max consecutive render errors before signaling caller to fall back.
#[cfg(feature = "vulkan")]
const MAX_RENDER_ERRORS: u32 = 10;

#[cfg(feature = "vulkan")]
impl VulkanRenderer {
    /// Create a new VulkanRenderer. Initializes GPU context, pipeline,
    /// atlas, and pre-rasterizes ASCII glyphs.
    ///
    /// Adapts atlas size to the GPU's max texture dimension.
    pub fn new() -> Result<Self, GpuError> {
        let ctx = GpuContext::new()?;

        // Adapt atlas size to GPU capabilities.
        // Default is 2048x2048 but weaker GPUs may only support smaller textures.
        let gpu_max_tex = ctx.max_texture_2d();
        let atlas_w = ATLAS_WIDTH.min(gpu_max_tex);
        let atlas_h = ATLAS_HEIGHT.min(gpu_max_tex);

        if atlas_w < ATLAS_WIDTH || atlas_h < ATLAS_HEIGHT {
            log::warn!(
                "GPU max texture {}px < default {}px, using {}x{} atlas",
                gpu_max_tex,
                ATLAS_WIDTH,
                atlas_w,
                atlas_h,
            );
        }

        let pipeline = TerminalPipeline::new(&ctx.device);
        let mut atlas = GlyphAtlas::new(32.0);
        atlas.pre_rasterize_ascii();

        let atlas_texture = AtlasTexture::new(&ctx.device, &ctx.queue, atlas_w, atlas_h);

        // Upload full atlas bitmap after pre-rasterization
        atlas_texture.upload_full(&ctx.queue, atlas.bitmap(), atlas_w, atlas_h);
        atlas.clear_dirty();

        let cell_buffer = CellBufferManager::new(&ctx.device, DEFAULT_ROWS, DEFAULT_COLS);
        let uniform_buffer = UniformBufferManager::new(&ctx.device);

        log::info!(
            "VulkanRenderer created: {} atlas={}x{}, {} ASCII glyphs cached",
            ctx.info.summary(),
            atlas_w,
            atlas_h,
            atlas.cached_count(),
        );

        Ok(VulkanRenderer {
            ctx,
            surface: None,
            pipeline,
            atlas,
            atlas_texture,
            cell_buffer,
            uniform_buffer,
            output_texture: None,
            output_view: None,
            font_size: 32.0,
            grid_rows: DEFAULT_ROWS,
            grid_cols: DEFAULT_COLS,
            atlas_w,
            atlas_h,
            render_errors: 0,
        })
    }

    /// Attach an Android native window as the rendering surface.
    ///
    /// # Safety
    /// `native_window` must be a valid ANativeWindow pointer.
    pub unsafe fn attach_surface(
        &mut self,
        native_window: *mut std::ffi::c_void,
        width: u32,
        height: u32,
    ) -> Result<(), GpuError> {
        let surface = unsafe { AndroidSurface::attach(&self.ctx, native_window, width, height)? };
        self.surface = Some(surface);
        self.create_output_texture(width, height);
        self.render_errors = 0;
        log::info!("VulkanRenderer surface attached: {}x{}", width, height);
        Ok(())
    }

    /// Detach the rendering surface (e.g., when Activity goes to background).
    pub fn detach_surface(&mut self) {
        self.output_texture = None;
        self.output_view = None;
        self.surface = None;
        self.render_errors = 0;
        log::info!("VulkanRenderer surface detached");
    }

    /// Render a single frame from a grid snapshot.
    /// Returns true if frame was presented, false if surface is unavailable.
    pub fn render_frame(&mut self, snapshot: &GridSnapshot) -> bool {
        // 1. Must have a surface
        let has_surface = self
            .surface
            .as_ref()
            .is_some_and(|s| s.is_attached() && !s.is_errored());
        if !has_surface {
            return false;
        }

        // Update grid dimensions from snapshot
        self.grid_rows = snapshot.rows.max(1) as u32;
        self.grid_cols = snapshot.cols.max(1) as u32;

        // 2. Update cell buffer from snapshot
        self.cell_buffer
            .resize(&self.ctx.device, self.grid_rows, self.grid_cols);
        self.cell_buffer
            .update_from_snapshot(&self.ctx.queue, snapshot, &mut self.atlas);

        // 3. Upload dirty atlas rects if any
        let dirty = self.atlas.dirty_rects();
        if !dirty.is_empty() {
            self.atlas_texture.upload_dirty(
                &self.ctx.queue,
                self.atlas.bitmap(),
                self.atlas_w,
                dirty,
            );
            self.atlas.clear_dirty();
        }

        // 4. Update uniforms
        let (cell_w, cell_h) = self.atlas.cell_metrics();
        let (out_w, out_h) = match self.surface.as_ref() {
            Some(s) => s.size(),
            None => return false,
        };
        let uniforms = Uniforms {
            cell_width: cell_w,
            cell_height: cell_h,
            grid_cols: self.grid_cols,
            grid_rows: self.grid_rows,
            atlas_width: self.atlas_w as f32,
            atlas_height: self.atlas_h as f32,
            output_width: out_w,
            output_height: out_h,
            cursor_row: snapshot.cursor.row,
            cursor_col: snapshot.cursor.col,
            cursor_shape: snapshot.cursor.shape as u32,
            cursor_visible: if snapshot.cursor.visible { 1 } else { 0 },
        };
        self.uniform_buffer.update(&self.ctx.queue, &uniforms);

        // 5. Get current surface texture (with recovery)
        let surface_texture = match self
            .surface
            .as_mut()
            .and_then(|s| s.get_current_texture(&self.ctx))
        {
            Some(tex) => tex,
            None => {
                self.render_errors += 1;
                if self.render_errors > MAX_RENDER_ERRORS {
                    log::error!(
                        "VulkanRenderer: {} consecutive render failures, suggest fallback",
                        self.render_errors,
                    );
                }
                return false;
            }
        };

        // 6. Get or create output texture
        let output_texture = match self.output_texture.as_ref() {
            Some(t) => t,
            None => {
                log::warn!("VulkanRenderer: no output texture");
                return false;
            }
        };

        // 7. Recreate bind group (output view changes each frame with surface)
        let output_view = match self.output_view.as_ref() {
            Some(v) => v,
            None => return false,
        };
        let bind_group = self.pipeline.create_bind_group(
            &self.ctx.device,
            self.cell_buffer.buffer(),
            self.uniform_buffer.buffer(),
            &self.atlas_texture.view,
            &self.atlas_texture.sampler,
            output_view,
        );

        // 8. Encode and present (surface_texture consumed by present())
        let presented = crate::gpu::frame::encode_and_present(
            &self.ctx.device,
            &self.ctx.queue,
            &self.pipeline,
            &bind_group,
            output_texture,
            surface_texture,
            self.grid_cols,
            self.grid_rows,
        );

        if presented {
            self.render_errors = 0;
        } else {
            self.render_errors += 1;
        }

        presented
    }

    /// Resize the surface and recreate the output texture.
    pub fn resize_surface(&mut self, width: u32, height: u32) {
        if width == 0 || height == 0 {
            return;
        }
        if let Some(ref mut surface) = self.surface {
            surface.resize(&self.ctx, width, height);
        }
        self.create_output_texture(width, height);
        log::info!("VulkanRenderer resized: {}x{}", width, height);
    }

    /// Clean up all GPU resources.
    pub fn destroy(&mut self) {
        self.output_texture = None;
        self.output_view = None;
        self.surface = None;
        log::info!("VulkanRenderer destroyed");
    }

    /// Get detailed GPU information for diagnostics.
    pub fn gpu_info(&self) -> &GpuInfo {
        &self.ctx.info
    }

    /// Get the GPU adapter name for diagnostics.
    pub fn adapter_name(&self) -> String {
        self.ctx.adapter_name()
    }

    /// Check if a surface is currently attached.
    pub fn has_surface(&self) -> bool {
        self.surface
            .as_ref()
            .is_some_and(|s| s.is_attached() && !s.is_errored())
    }

    /// Check if renderer has exceeded error threshold and should fall back.
    pub fn should_fallback(&self) -> bool {
        self.render_errors > MAX_RENDER_ERRORS
    }

    // -- private helpers --

    fn create_output_texture(&mut self, width: u32, height: u32) {
        if width == 0 || height == 0 {
            return;
        }
        let texture = self.ctx.device.create_texture(&wgpu::TextureDescriptor {
            label: Some("output_texture"),
            size: wgpu::Extent3d {
                width,
                height,
                depth_or_array_layers: 1,
            },
            mip_level_count: 1,
            sample_count: 1,
            dimension: wgpu::TextureDimension::D2,
            // Must match shader: texture_storage_2d<rgba8unorm, write>
            format: wgpu::TextureFormat::Rgba8Unorm,
            usage: wgpu::TextureUsages::STORAGE_BINDING | wgpu::TextureUsages::COPY_SRC,
            view_formats: &[],
        });
        let view = texture.create_view(&wgpu::TextureViewDescriptor::default());
        self.output_texture = Some(texture);
        self.output_view = Some(view);
    }
}

#[cfg(feature = "vulkan")]
impl Renderer for VulkanRenderer {
    fn init(&mut self, config: &RenderConfig) {
        self.font_size = config.font_size;
        // Recreate atlas with new font size if it changed
        if (self.atlas.font_size() - config.font_size).abs() > 0.1 {
            self.atlas = GlyphAtlas::new(config.font_size);
            self.atlas.pre_rasterize_ascii();
            self.atlas_texture.upload_full(
                &self.ctx.queue,
                self.atlas.bitmap(),
                self.atlas_w,
                self.atlas_h,
            );
            self.atlas.clear_dirty();
        }
    }

    fn render(&mut self, snapshot: &GridSnapshot) {
        self.render_frame(snapshot);
    }

    fn resize(&mut self, width: u32, height: u32) {
        self.resize_surface(width, height);
    }

    fn destroy(&mut self) {
        VulkanRenderer::destroy(self);
    }

    fn get_grid_buffer(&self) -> Option<&[i32]> {
        // VulkanRenderer renders directly to GPU surface, no CPU grid buffer.
        None
    }
}
