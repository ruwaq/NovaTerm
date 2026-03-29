// Vulkan renderer — wgpu compute shader pipeline (Phase 2b).
//
// This module is behind the "vulkan" feature flag and will contain:
//
// 1. GlyphAtlas: fontdue rasterization → 2048x2048 RGBA texture
//    - Pre-rasterize ASCII (0x20-0x7E) at init
//    - On-demand rasterize for Unicode/CJK/emoji
//    - LRU eviction when atlas is full
//
// 2. CellBuffer: GPU buffer of packed cell data
//    - One u32x4 per cell: (glyph_index, fg_argb, bg_argb, flags)
//    - Upload only damaged cells (from Rust damage tracking)
//
// 3. ComputeShader (Zutty pattern):
//    - One invocation per cell (workgroup size 8x8)
//    - Reads cell from CellBuffer, looks up glyph in atlas texture
//    - Writes RGBA pixels to output texture
//    - 2 draw calls per frame: backgrounds (clear) + text (compute)
//
// 4. Surface management:
//    - AndroidExternalSurface → wgpu::Surface
//    - Triple buffering via wgpu present modes
//    - Automatic recreation on surface loss
//
// Target: 120 FPS, <4ms input latency, 1-2 draw calls on Dimensity 9400
//
// NOT YET IMPLEMENTED — this is the Phase 2b skeleton.

#[cfg(feature = "vulkan")]
use crate::traits::{Renderer, RenderConfig};

#[cfg(feature = "vulkan")]
pub struct VulkanRenderer {
    // Will contain:
    // device: wgpu::Device,
    // queue: wgpu::Queue,
    // surface: wgpu::Surface,
    // pipeline: wgpu::ComputePipeline,
    // glyph_atlas: GlyphAtlas,
    // cell_buffer: wgpu::Buffer,
}

// Implementation deferred to Phase 2b.
