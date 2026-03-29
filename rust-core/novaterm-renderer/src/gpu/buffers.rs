// GPU buffer management: CellGpu SSBO + Uniforms.
//
// CellGpu is the GPU-side representation of a terminal cell.
// Matches the Cell struct in shader.wgsl exactly.
//
// The data structs (CellGpu, Uniforms) are always available so that
// layout tests can run without a GPU. Only the buffer managers that
// depend on wgpu are behind the "vulkan" feature gate.

/// GPU-side cell data. One per terminal cell. Matches shader Cell struct.
#[repr(C)]
#[derive(Copy, Clone, Debug)]
#[cfg_attr(feature = "vulkan", derive(bytemuck::Pod, bytemuck::Zeroable))]
pub struct CellGpu {
    pub atlas_xy: u32, // packed: x | (y << 16)
    pub atlas_wh: u32, // packed: w | (h << 16)
    pub fg_color: u32, // ARGB
    pub bg_color: u32, // ARGB
    pub flags: u32,
    pub _pad: u32,
}

/// Uniform data for the compute shader. Matches shader Uniforms struct.
#[repr(C)]
#[derive(Copy, Clone, Debug)]
#[cfg_attr(feature = "vulkan", derive(bytemuck::Pod, bytemuck::Zeroable))]
pub struct Uniforms {
    pub cell_width: f32,
    pub cell_height: f32,
    pub grid_cols: u32,
    pub grid_rows: u32,
    pub atlas_width: f32,
    pub atlas_height: f32,
    pub output_width: u32,
    pub output_height: u32,
    pub cursor_row: i32,
    pub cursor_col: i32,
    pub cursor_shape: u32,
    pub cursor_visible: u32,
}

impl CellGpu {
    pub const SIZE: usize = std::mem::size_of::<Self>();

    /// Create a zeroed CellGpu (all fields 0).
    pub const fn zeroed() -> Self {
        Self {
            atlas_xy: 0,
            atlas_wh: 0,
            fg_color: 0,
            bg_color: 0,
            flags: 0,
            _pad: 0,
        }
    }

    /// Pack atlas position into u32.
    pub fn pack_atlas_xy(x: u16, y: u16) -> u32 {
        (x as u32) | ((y as u32) << 16)
    }

    /// Pack atlas glyph size into u32.
    pub fn pack_atlas_wh(w: u16, h: u16) -> u32 {
        (w as u32) | ((h as u32) << 16)
    }
}

/// Manages the cell storage buffer on the GPU.
#[cfg(feature = "vulkan")]
pub struct CellBufferManager {
    buffer: wgpu::Buffer,
    staging: Vec<CellGpu>,
    capacity: usize,
}

#[cfg(feature = "vulkan")]
impl CellBufferManager {
    pub fn new(device: &wgpu::Device, rows: u32, cols: u32) -> Self {
        let capacity = (rows * cols) as usize;
        let size = (capacity * CellGpu::SIZE) as u64;
        let buffer = device.create_buffer(&wgpu::BufferDescriptor {
            label: Some("cell_buffer"),
            size: size.max(CellGpu::SIZE as u64), // At least 1 cell
            usage: wgpu::BufferUsages::STORAGE | wgpu::BufferUsages::COPY_DST,
            mapped_at_creation: false,
        });
        Self {
            buffer,
            staging: vec![CellGpu::zeroed(); capacity],
            capacity,
        }
    }

    /// Convert GridSnapshot cells to CellGpu using GlyphAtlas for lookup.
    pub fn update_from_snapshot(
        &mut self,
        queue: &wgpu::Queue,
        snapshot: &novaterm_vt::GridSnapshot,
        atlas: &mut crate::atlas::GlyphAtlas,
    ) {
        let total = (snapshot.rows * snapshot.cols) as usize;
        if total > self.capacity {
            self.staging.resize(total, CellGpu::zeroed());
            self.capacity = total;
        }

        for (i, cell) in snapshot.cells.iter().enumerate() {
            if i >= total {
                break;
            }

            let key = crate::atlas::GlyphKey {
                codepoint: cell.character,
                flags: if cell.flags & 1 != 0 {
                    1 // bold
                } else if cell.flags & 2 != 0 {
                    2 // italic
                } else {
                    0
                },
            };

            let (ax, ay, aw, ah) = if let Some(entry) = atlas.lookup(key) {
                (entry.x, entry.y, entry.width as u16, entry.height as u16)
            } else {
                (0, 0, 0, 0)
            };

            self.staging[i] = CellGpu {
                atlas_xy: CellGpu::pack_atlas_xy(ax, ay),
                atlas_wh: CellGpu::pack_atlas_wh(aw, ah),
                fg_color: cell.fg,
                bg_color: cell.bg,
                flags: cell.flags,
                _pad: 0,
            };
        }

        queue.write_buffer(
            &self.buffer,
            0,
            bytemuck::cast_slice(&self.staging[..total]),
        );
    }

    pub fn buffer(&self) -> &wgpu::Buffer {
        &self.buffer
    }

    /// Resize the GPU buffer if grid dimensions changed.
    pub fn resize(&mut self, device: &wgpu::Device, rows: u32, cols: u32) {
        let new_cap = (rows * cols) as usize;
        if new_cap > self.capacity {
            let size = (new_cap * CellGpu::SIZE) as u64;
            self.buffer = device.create_buffer(&wgpu::BufferDescriptor {
                label: Some("cell_buffer"),
                size,
                usage: wgpu::BufferUsages::STORAGE | wgpu::BufferUsages::COPY_DST,
                mapped_at_creation: false,
            });
            self.staging.resize(new_cap, CellGpu::zeroed());
            self.capacity = new_cap;
        }
    }
}

/// Manages the uniform buffer.
#[cfg(feature = "vulkan")]
pub struct UniformBufferManager {
    buffer: wgpu::Buffer,
}

#[cfg(feature = "vulkan")]
impl UniformBufferManager {
    pub fn new(device: &wgpu::Device) -> Self {
        let buffer = device.create_buffer(&wgpu::BufferDescriptor {
            label: Some("uniforms"),
            size: std::mem::size_of::<Uniforms>() as u64,
            usage: wgpu::BufferUsages::UNIFORM | wgpu::BufferUsages::COPY_DST,
            mapped_at_creation: false,
        });
        Self { buffer }
    }

    pub fn update(&self, queue: &wgpu::Queue, uniforms: &Uniforms) {
        queue.write_buffer(&self.buffer, 0, bytemuck::bytes_of(uniforms));
    }

    pub fn buffer(&self) -> &wgpu::Buffer {
        &self.buffer
    }
}

// Tests (no GPU needed — verify struct layout and packing)
#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn cell_gpu_size() {
        // CellGpu must be 24 bytes (6 x u32) for SSBO alignment
        assert_eq!(std::mem::size_of::<CellGpu>(), 24);
    }

    #[test]
    fn uniforms_size() {
        // Uniforms must be 48 bytes (12 x f32/u32/i32)
        assert_eq!(std::mem::size_of::<Uniforms>(), 48);
    }

    #[test]
    fn pack_atlas_xy() {
        assert_eq!(CellGpu::pack_atlas_xy(100, 200), 100 | (200 << 16));
    }

    #[test]
    fn pack_atlas_wh() {
        assert_eq!(CellGpu::pack_atlas_wh(16, 32), 16 | (32 << 16));
    }

    #[test]
    fn cell_gpu_zeroed() {
        let cell = CellGpu {
            atlas_xy: 0,
            atlas_wh: 0,
            fg_color: 0,
            bg_color: 0,
            flags: 0,
            _pad: 0,
        };
        assert_eq!(cell.atlas_xy, 0);
    }

    #[test]
    fn uniforms_default() {
        let u = Uniforms {
            cell_width: 16.0,
            cell_height: 32.0,
            grid_cols: 80,
            grid_rows: 24,
            atlas_width: 2048.0,
            atlas_height: 2048.0,
            output_width: 1280,
            output_height: 768,
            cursor_row: 0,
            cursor_col: 0,
            cursor_shape: 2,
            cursor_visible: 1,
        };
        assert_eq!(u.grid_cols, 80);
        assert_eq!(u.cursor_shape, 2);
    }

    #[test]
    fn dispatch_dimensions_80x24() {
        let cols: u32 = 80;
        let rows: u32 = 24;
        let wx = (cols + 7) / 8;
        let wy = (rows + 7) / 8;
        assert_eq!(wx, 10);
        assert_eq!(wy, 3);
    }

    #[test]
    fn dispatch_dimensions_120x40() {
        let cols: u32 = 120;
        let rows: u32 = 40;
        let wx = (cols + 7) / 8;
        let wy = (rows + 7) / 8;
        assert_eq!(wx, 15);
        assert_eq!(wy, 5);
    }
}
