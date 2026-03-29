// GPU rendering module (Phase 2b).
//
// Provides wgpu-based Vulkan compute shader rendering for the terminal.
// Architecture follows the Zutty pattern: one compute invocation per cell,
// glyph atlas texture, cell storage buffer, output to surface.

pub mod context;
pub mod surface;
pub mod buffers;
pub mod pipeline;
pub mod atlas_texture;
pub mod frame;
pub mod cursor;
pub mod selection;
