// Renderer trait — the abstraction for terminal rendering backends.
//
// Phase 2a: SoftwareRenderer (passes grid to Java Canvas via JNI)
// Phase 2b: VulkanRenderer (wgpu compute shaders, Zutty pattern)

use novaterm_vt::GridSnapshot;

/// Configuration for the renderer.
#[derive(Debug, Clone)]
pub struct RenderConfig {
    /// Font size in pixels.
    pub font_size: f32,
    /// Cell width in pixels (computed from font metrics).
    pub cell_width: f32,
    /// Cell height in pixels (line spacing).
    pub cell_height: f32,
    /// Whether to enable hardware acceleration.
    pub hardware_accel: bool,
}

impl Default for RenderConfig {
    fn default() -> Self {
        Self {
            font_size: 32.0,
            cell_width: 16.0,
            cell_height: 32.0,
            hardware_accel: true,
        }
    }
}

/// Abstract surface that the renderer draws to.
///
/// On Android:
/// - Phase 2a: this is a no-op (Java Canvas handles the surface)
/// - Phase 2b: this wraps an Android Surface/SurfaceHolder for wgpu
pub trait Surface {
    /// Get the surface dimensions in pixels.
    fn size(&self) -> (u32, u32);

    /// Whether the surface is still valid (not destroyed).
    fn is_valid(&self) -> bool;
}

/// Terminal renderer abstraction.
///
/// The renderer receives a GridSnapshot from novaterm-vt and produces
/// a frame. Different implementations handle different backends:
///
/// - `SoftwareRenderer`: returns the grid as a flat int[] for Java Canvas
/// - `VulkanRenderer` (Phase 2b): renders directly to GPU surface
pub trait Renderer: Send {
    /// Initialize the renderer with the given config.
    fn init(&mut self, config: &RenderConfig);

    /// Render a frame from the given grid snapshot.
    ///
    /// For SoftwareRenderer: updates internal grid buffer (read via get_grid).
    /// For VulkanRenderer: submits GPU commands and presents.
    fn render(&mut self, snapshot: &GridSnapshot);

    /// Notify the renderer that the surface size changed.
    fn resize(&mut self, width: u32, height: u32);

    /// Release GPU resources. Must be called before dropping.
    fn destroy(&mut self);

    /// Get the grid as a flat int array for JNI transfer.
    ///
    /// Only meaningful for SoftwareRenderer. VulkanRenderer returns None
    /// because it renders directly to the GPU surface.
    fn get_grid_buffer(&self) -> Option<&[i32]> {
        None
    }
}
