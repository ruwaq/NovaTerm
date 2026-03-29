// GPU atlas texture management.
//
// Uploads the CPU-side glyph atlas bitmap to a wgpu texture.
// Supports both full upload and incremental dirty-rect updates.

#[cfg(feature = "vulkan")]
pub struct AtlasTexture {
    pub texture: wgpu::Texture,
    pub view: wgpu::TextureView,
    pub sampler: wgpu::Sampler,
}

#[cfg(feature = "vulkan")]
impl AtlasTexture {
    /// Create atlas texture on GPU.
    pub fn new(device: &wgpu::Device, _queue: &wgpu::Queue, width: u32, height: u32) -> Self {
        let texture = device.create_texture(&wgpu::TextureDescriptor {
            label: Some("glyph_atlas"),
            size: wgpu::Extent3d {
                width,
                height,
                depth_or_array_layers: 1,
            },
            mip_level_count: 1,
            sample_count: 1,
            dimension: wgpu::TextureDimension::D2,
            format: wgpu::TextureFormat::Rgba8Unorm,
            usage: wgpu::TextureUsages::TEXTURE_BINDING | wgpu::TextureUsages::COPY_DST,
            view_formats: &[],
        });

        let view = texture.create_view(&wgpu::TextureViewDescriptor::default());

        let sampler = device.create_sampler(&wgpu::SamplerDescriptor {
            label: Some("atlas_sampler"),
            address_mode_u: wgpu::AddressMode::ClampToEdge,
            address_mode_v: wgpu::AddressMode::ClampToEdge,
            mag_filter: wgpu::FilterMode::Linear,
            min_filter: wgpu::FilterMode::Linear,
            ..Default::default()
        });

        Self {
            texture,
            view,
            sampler,
        }
    }

    /// Upload full atlas bitmap to GPU.
    pub fn upload_full(&self, queue: &wgpu::Queue, bitmap: &[u8], width: u32, height: u32) {
        queue.write_texture(
            wgpu::TexelCopyTextureInfo {
                texture: &self.texture,
                mip_level: 0,
                origin: wgpu::Origin3d::ZERO,
                aspect: wgpu::TextureAspect::All,
            },
            bitmap,
            wgpu::TexelCopyBufferLayout {
                offset: 0,
                bytes_per_row: Some(width * 4),
                rows_per_image: Some(height),
            },
            wgpu::Extent3d {
                width,
                height,
                depth_or_array_layers: 1,
            },
        );
    }

    /// Upload only dirty rectangles (incremental update).
    pub fn upload_dirty(
        &self,
        queue: &wgpu::Queue,
        bitmap: &[u8],
        atlas_width: u32,
        rects: &[crate::atlas::Rect],
    ) {
        for rect in rects {
            // Extract the sub-rectangle from the full bitmap
            let mut region =
                Vec::with_capacity((rect.width * rect.height * 4) as usize);
            for row in 0..rect.height {
                let src_y = rect.y + row;
                let src_start = ((src_y * atlas_width + rect.x) * 4) as usize;
                let src_end = src_start + (rect.width * 4) as usize;
                if src_end <= bitmap.len() {
                    region.extend_from_slice(&bitmap[src_start..src_end]);
                }
            }

            if region.is_empty() {
                continue;
            }

            queue.write_texture(
                wgpu::TexelCopyTextureInfo {
                    texture: &self.texture,
                    mip_level: 0,
                    origin: wgpu::Origin3d {
                        x: rect.x,
                        y: rect.y,
                        z: 0,
                    },
                    aspect: wgpu::TextureAspect::All,
                },
                &region,
                wgpu::TexelCopyBufferLayout {
                    offset: 0,
                    bytes_per_row: Some(rect.width * 4),
                    rows_per_image: Some(rect.height),
                },
                wgpu::Extent3d {
                    width: rect.width,
                    height: rect.height,
                    depth_or_array_layers: 1,
                },
            );
        }
    }
}
