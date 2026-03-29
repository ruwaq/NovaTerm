// NovaTerm GPU Renderer — Phase 2b skeleton.
//
// Architecture (Zutty pattern):
//   1. Glyph atlas: fontdue rasterizes glyphs → 2048x2048 RGBA texture
//   2. Cell buffer: flat array of (char_index, fg, bg, flags) per cell
//   3. Compute shader: one invocation per cell, copies glyph from atlas
//   4. Result: 1-2 draw calls per frame, zero CPU rendering cost
//
// Current state: trait definitions only. Implementations come in Phase 2b.

mod traits;
pub mod atlas;

#[cfg(feature = "software")]
mod software;

#[cfg(feature = "vulkan")]
pub mod gpu;

#[cfg(feature = "vulkan")]
mod vulkan;

pub use traits::{Renderer, RenderConfig, Surface};
pub use atlas::GlyphAtlas;

#[cfg(feature = "software")]
pub use software::SoftwareRenderer;
