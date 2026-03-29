// wgpu GPU context for NovaTerm.

use std::fmt;

#[derive(Debug)]
pub enum GpuError {
    NoAdapter,
    DeviceRequest(wgpu::RequestDeviceError),
    Surface(wgpu::CreateSurfaceError),
    Other(String),
}

impl fmt::Display for GpuError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            GpuError::NoAdapter => write!(f, "No GPU adapter"),
            GpuError::DeviceRequest(e) => write!(f, "Device: {e}"),
            GpuError::Surface(e) => write!(f, "Surface: {e}"),
            GpuError::Other(s) => write!(f, "{s}"),
        }
    }
}

impl std::error::Error for GpuError {}

pub struct GpuContext {
    pub instance: wgpu::Instance,
    pub adapter: wgpu::Adapter,
    pub device: wgpu::Device,
    pub queue: wgpu::Queue,
}

impl GpuContext {
    pub fn new() -> Result<Self, GpuError> {
        let instance = wgpu::Instance::default();

        let adapter = pollster::block_on(async {
            instance.request_adapter(&wgpu::RequestAdapterOptions {
                power_preference: wgpu::PowerPreference::HighPerformance,
                compatible_surface: None,
                force_fallback_adapter: false,
            }).await
        })
        .map_err(|_| GpuError::NoAdapter)?;

        log::info!("GPU: {} ({:?})", adapter.get_info().name, adapter.get_info().backend);

        let (device, queue) = pollster::block_on(async {
            adapter.request_device(
                &wgpu::DeviceDescriptor {
                    label: Some("NovaTerm"),
                    required_features: wgpu::Features::empty(),
                    required_limits: wgpu::Limits {
                        max_storage_buffer_binding_size: 4 * 1024 * 1024,
                        max_storage_buffers_per_shader_stage: 2,
                        max_compute_workgroups_per_dimension: 65535,
                        max_compute_invocations_per_workgroup: 256,
                        max_texture_dimension_2d: 4096,
                        ..wgpu::Limits::downlevel_defaults()
                    },
                    memory_hints: wgpu::MemoryHints::Performance,
                    ..Default::default()
                },
            ).await
        })
        .map_err(GpuError::DeviceRequest)?;

        log::info!("GPU device created");
        Ok(GpuContext { instance, adapter, device, queue })
    }

    pub fn adapter_name(&self) -> String {
        self.adapter.get_info().name.clone()
    }
}
