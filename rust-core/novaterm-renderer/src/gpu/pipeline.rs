// Compute pipeline for terminal rendering.
//
// Creates the wgpu ComputePipeline from the embedded WGSL shader,
// along with the bind group layout matching shader bindings.

#[cfg(feature = "vulkan")]
pub struct TerminalPipeline {
    pub pipeline: wgpu::ComputePipeline,
    pub bind_group_layout: wgpu::BindGroupLayout,
}

#[cfg(feature = "vulkan")]
impl TerminalPipeline {
    /// Create the compute pipeline from embedded WGSL shader.
    pub fn new(device: &wgpu::Device) -> Self {
        let shader_source = include_str!("shader.wgsl");
        let shader = device.create_shader_module(wgpu::ShaderModuleDescriptor {
            label: Some("terminal_shader"),
            source: wgpu::ShaderSource::Wgsl(shader_source.into()),
        });

        let bind_group_layout =
            device.create_bind_group_layout(&wgpu::BindGroupLayoutDescriptor {
                label: Some("terminal_bgl"),
                entries: &[
                    // 0: Cell buffer (storage, read-only)
                    wgpu::BindGroupLayoutEntry {
                        binding: 0,
                        visibility: wgpu::ShaderStages::COMPUTE,
                        ty: wgpu::BindingType::Buffer {
                            ty: wgpu::BufferBindingType::Storage { read_only: true },
                            has_dynamic_offset: false,
                            min_binding_size: None,
                        },
                        count: None,
                    },
                    // 1: Uniforms (uniform buffer)
                    wgpu::BindGroupLayoutEntry {
                        binding: 1,
                        visibility: wgpu::ShaderStages::COMPUTE,
                        ty: wgpu::BindingType::Buffer {
                            ty: wgpu::BufferBindingType::Uniform,
                            has_dynamic_offset: false,
                            min_binding_size: None,
                        },
                        count: None,
                    },
                    // 2: Atlas texture (2D, float sampled)
                    wgpu::BindGroupLayoutEntry {
                        binding: 2,
                        visibility: wgpu::ShaderStages::COMPUTE,
                        ty: wgpu::BindingType::Texture {
                            sample_type: wgpu::TextureSampleType::Float { filterable: true },
                            view_dimension: wgpu::TextureViewDimension::D2,
                            multisampled: false,
                        },
                        count: None,
                    },
                    // 3: Atlas sampler
                    wgpu::BindGroupLayoutEntry {
                        binding: 3,
                        visibility: wgpu::ShaderStages::COMPUTE,
                        ty: wgpu::BindingType::Sampler(wgpu::SamplerBindingType::Filtering),
                        count: None,
                    },
                    // 4: Output storage texture (write-only)
                    wgpu::BindGroupLayoutEntry {
                        binding: 4,
                        visibility: wgpu::ShaderStages::COMPUTE,
                        ty: wgpu::BindingType::StorageTexture {
                            access: wgpu::StorageTextureAccess::WriteOnly,
                            format: wgpu::TextureFormat::Rgba8Unorm,
                            view_dimension: wgpu::TextureViewDimension::D2,
                        },
                        count: None,
                    },
                ],
            });

        let pipeline_layout = device.create_pipeline_layout(&wgpu::PipelineLayoutDescriptor {
            label: Some("terminal_pipeline_layout"),
            bind_group_layouts: &[Some(&bind_group_layout)],
            immediate_size: 0,
        });

        let pipeline = device.create_compute_pipeline(&wgpu::ComputePipelineDescriptor {
            label: Some("terminal_compute"),
            layout: Some(&pipeline_layout),
            module: &shader,
            entry_point: Some("main"),
            compilation_options: Default::default(),
            cache: None,
        });

        Self {
            pipeline,
            bind_group_layout,
        }
    }

    /// Create a bind group for one frame.
    pub fn create_bind_group(
        &self,
        device: &wgpu::Device,
        cell_buffer: &wgpu::Buffer,
        uniform_buffer: &wgpu::Buffer,
        atlas_view: &wgpu::TextureView,
        atlas_sampler: &wgpu::Sampler,
        output_view: &wgpu::TextureView,
    ) -> wgpu::BindGroup {
        device.create_bind_group(&wgpu::BindGroupDescriptor {
            label: Some("terminal_bg"),
            layout: &self.bind_group_layout,
            entries: &[
                wgpu::BindGroupEntry {
                    binding: 0,
                    resource: cell_buffer.as_entire_binding(),
                },
                wgpu::BindGroupEntry {
                    binding: 1,
                    resource: uniform_buffer.as_entire_binding(),
                },
                wgpu::BindGroupEntry {
                    binding: 2,
                    resource: wgpu::BindingResource::TextureView(atlas_view),
                },
                wgpu::BindGroupEntry {
                    binding: 3,
                    resource: wgpu::BindingResource::Sampler(atlas_sampler),
                },
                wgpu::BindGroupEntry {
                    binding: 4,
                    resource: wgpu::BindingResource::TextureView(output_view),
                },
            ],
        })
    }

    /// Calculate workgroup dispatch dimensions for a grid.
    /// Workgroup size is 8x8, so we ceil-divide both dimensions.
    pub fn dispatch_size(cols: u32, rows: u32) -> (u32, u32) {
        ((cols + 7) / 8, (rows + 7) / 8)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn dispatch_size_exact_workgroup() {
        assert_eq!(TerminalPipeline::dispatch_size(8, 8), (1, 1));
    }

    #[test]
    fn dispatch_size_minimum() {
        assert_eq!(TerminalPipeline::dispatch_size(1, 1), (1, 1));
    }

    #[test]
    fn dispatch_size_one_over() {
        assert_eq!(TerminalPipeline::dispatch_size(9, 9), (2, 2));
    }

    #[test]
    fn dispatch_size_standard_terminal() {
        assert_eq!(TerminalPipeline::dispatch_size(80, 24), (10, 3));
    }

    #[test]
    fn dispatch_size_large_terminal() {
        assert_eq!(TerminalPipeline::dispatch_size(120, 40), (15, 5));
    }

    #[test]
    fn dispatch_size_rectangular() {
        assert_eq!(TerminalPipeline::dispatch_size(200, 8), (25, 1));
    }

    #[test]
    fn dispatch_size_7_width() {
        // 7 cols fits in one workgroup (7+7)/8 = 1
        assert_eq!(TerminalPipeline::dispatch_size(7, 7), (1, 1));
    }
}
