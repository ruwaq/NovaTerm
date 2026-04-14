// wgpu GPU context for NovaTerm.
//
// Multi-GPU compatible: detects vendor (Adreno, Mali, PowerVR, Xclipse,
// Immortalis, etc.) and adapts limits and workarounds per chipset.

use std::fmt;

#[derive(Debug)]
pub enum GpuError {
    NoAdapter,
    DeviceRequest(wgpu::RequestDeviceError),
    Surface(wgpu::CreateSurfaceError),
    SurfaceLost,
    Other(String),
}

impl fmt::Display for GpuError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            GpuError::NoAdapter => write!(f, "No GPU adapter found"),
            GpuError::DeviceRequest(e) => write!(f, "Device request failed: {e}"),
            GpuError::Surface(e) => write!(f, "Surface error: {e}"),
            GpuError::SurfaceLost => write!(f, "Surface lost"),
            GpuError::Other(s) => write!(f, "{s}"),
        }
    }
}

impl std::error::Error for GpuError {}

/// Known GPU vendors on Android devices.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum GpuVendor {
    /// Qualcomm Adreno (Snapdragon)
    Qualcomm,
    /// ARM Mali / Immortalis (Exynos, Tensor, Dimensity, Kirin)
    Arm,
    /// Imagination PowerVR (older MediaTek, some Apple)
    Imagination,
    /// Samsung Xclipse (Exynos with AMD RDNA)
    Samsung,
    /// Software fallback (lavapipe, swiftshader)
    Software,
    /// Unknown vendor
    Unknown,
}

impl GpuVendor {
    /// Detect vendor from adapter info.
    pub fn from_adapter_info(info: &wgpu::AdapterInfo) -> Self {
        let name = info.name.to_lowercase();
        let driver = info.driver.to_lowercase();

        // Check for software renderers first
        if name.contains("lavapipe")
            || name.contains("llvmpipe")
            || name.contains("swiftshader")
            || info.device_type == wgpu::DeviceType::Cpu
        {
            return GpuVendor::Software;
        }

        // Vendor detection by name patterns
        if name.contains("adreno") || driver.contains("qualcomm") {
            GpuVendor::Qualcomm
        } else if name.contains("mali") || name.contains("immortalis") {
            GpuVendor::Arm
        } else if name.contains("powervr") || driver.contains("imagination") {
            GpuVendor::Imagination
        } else if name.contains("xclipse") || name.contains("rdna") {
            GpuVendor::Samsung
        } else {
            // Fallback: check vendor_id
            match info.vendor {
                0x5143 => GpuVendor::Qualcomm,   // QC
                0x13B5 => GpuVendor::Arm,         // ARM
                0x1010 => GpuVendor::Imagination,  // IMG
                0x144D => GpuVendor::Samsung,      // Samsung
                _ => GpuVendor::Unknown,
            }
        }
    }
}

impl fmt::Display for GpuVendor {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            GpuVendor::Qualcomm => write!(f, "Qualcomm Adreno"),
            GpuVendor::Arm => write!(f, "ARM Mali/Immortalis"),
            GpuVendor::Imagination => write!(f, "Imagination PowerVR"),
            GpuVendor::Samsung => write!(f, "Samsung Xclipse"),
            GpuVendor::Software => write!(f, "Software"),
            GpuVendor::Unknown => write!(f, "Unknown"),
        }
    }
}

/// Detailed GPU information for diagnostics.
#[derive(Debug, Clone)]
pub struct GpuInfo {
    pub name: String,
    pub vendor: GpuVendor,
    pub driver: String,
    pub driver_info: String,
    pub backend: String,
    pub device_type: String,
    /// Max 2D texture dimension supported
    pub max_texture_2d: u32,
    /// Max storage buffer size (bytes)
    pub max_storage_buffer: u32,
    /// Max compute workgroup invocations
    pub max_workgroup_invocations: u32,
}

impl GpuInfo {
    /// One-line summary for logging.
    pub fn summary(&self) -> String {
        format!(
            "{} ({}) [{}] max_tex={}px max_ssbo={}KB",
            self.name,
            self.vendor,
            self.backend,
            self.max_texture_2d,
            self.max_storage_buffer / 1024,
        )
    }
}

/// GPU capability limits, adapted per vendor.
struct AdaptedLimits {
    max_storage_buffer: u32,
    max_storage_buffers_per_stage: u32,
    max_compute_workgroups: u32,
    max_compute_invocations: u32,
    max_texture_2d: u32,
}

impl AdaptedLimits {
    /// Choose conservative limits based on GPU vendor.
    /// These are the limits we REQUEST — the driver can give us more.
    fn for_vendor(vendor: GpuVendor) -> Self {
        match vendor {
            // Adreno: excellent compute, but older models (6xx) have
            // smaller storage buffer limits. Request conservatively.
            GpuVendor::Qualcomm => AdaptedLimits {
                max_storage_buffer: 4 * 1024 * 1024,
                max_storage_buffers_per_stage: 2,
                max_compute_workgroups: 65535,
                max_compute_invocations: 256,
                max_texture_2d: 4096,
            },
            // Mali/Immortalis: great on newer models (G720+, Immortalis),
            // but older Mali-G76/G78 have tighter limits.
            GpuVendor::Arm => AdaptedLimits {
                max_storage_buffer: 4 * 1024 * 1024,
                max_storage_buffers_per_stage: 2,
                max_compute_workgroups: 65535,
                max_compute_invocations: 256,
                max_texture_2d: 4096,
            },
            // PowerVR: older tech, be conservative.
            GpuVendor::Imagination => AdaptedLimits {
                max_storage_buffer: 2 * 1024 * 1024,
                max_storage_buffers_per_stage: 2,
                max_compute_workgroups: 32768,
                max_compute_invocations: 128,
                max_texture_2d: 2048,
            },
            // Xclipse (RDNA2): powerful, similar to desktop AMD.
            GpuVendor::Samsung => AdaptedLimits {
                max_storage_buffer: 4 * 1024 * 1024,
                max_storage_buffers_per_stage: 2,
                max_compute_workgroups: 65535,
                max_compute_invocations: 256,
                max_texture_2d: 4096,
            },
            // Software: very conservative.
            GpuVendor::Software => AdaptedLimits {
                max_storage_buffer: 2 * 1024 * 1024,
                max_storage_buffers_per_stage: 2,
                max_compute_workgroups: 16384,
                max_compute_invocations: 128,
                max_texture_2d: 2048,
            },
            // Unknown: use downlevel defaults, conservative.
            GpuVendor::Unknown => AdaptedLimits {
                max_storage_buffer: 2 * 1024 * 1024,
                max_storage_buffers_per_stage: 2,
                max_compute_workgroups: 32768,
                max_compute_invocations: 128,
                max_texture_2d: 4096,
            },
        }
    }
}

pub struct GpuContext {
    pub instance: wgpu::Instance,
    pub adapter: wgpu::Adapter,
    pub device: wgpu::Device,
    pub queue: wgpu::Queue,
    pub info: GpuInfo,
}

impl GpuContext {
    pub fn new() -> Result<Self, GpuError> {
        // Request Vulkan backend explicitly on Android
        // Request Vulkan on Android.
        let instance = wgpu::Instance::new(wgpu::InstanceDescriptor {
            backends: wgpu::Backends::VULKAN,
            flags: wgpu::InstanceFlags::default(),
            memory_budget_thresholds: wgpu::MemoryBudgetThresholds::default(),
            backend_options: wgpu::BackendOptions::default(),
            display: None,
        });

        let adapter = pollster::block_on(async {
            instance
                .request_adapter(&wgpu::RequestAdapterOptions {
                    power_preference: wgpu::PowerPreference::HighPerformance,
                    compatible_surface: None,
                    force_fallback_adapter: false,
                })
                .await
        })
        .map_err(|_| GpuError::NoAdapter)?;

        let adapter_info = adapter.get_info();
        let vendor = GpuVendor::from_adapter_info(&adapter_info);
        let limits = AdaptedLimits::for_vendor(vendor);

        log::info!(
            "GPU detected: name={}, vendor={}, driver={}, driver_info={}, backend={:?}, type={:?}",
            adapter_info.name,
            vendor,
            adapter_info.driver,
            adapter_info.driver_info,
            adapter_info.backend,
            adapter_info.device_type,
        );

        // Query actual device limits for info
        let device_limits = adapter.limits();
        log::info!(
            "GPU limits: max_tex_2d={}, max_ssbo={}KB, max_compute_wg={}, max_invocations={}",
            device_limits.max_texture_dimension_2d,
            device_limits.max_storage_buffer_binding_size / 1024,
            device_limits.max_compute_workgroups_per_dimension,
            device_limits.max_compute_invocations_per_workgroup,
        );

        let (device, queue) = pollster::block_on(async {
            adapter
                .request_device(&wgpu::DeviceDescriptor {
                    label: Some("NovaTerm"),
                    required_features: wgpu::Features::empty(),
                    required_limits: wgpu::Limits {
                        max_storage_buffer_binding_size: limits.max_storage_buffer as u64,
                        max_storage_buffers_per_shader_stage: limits.max_storage_buffers_per_stage,
                        max_compute_workgroups_per_dimension: limits.max_compute_workgroups,
                        max_compute_invocations_per_workgroup: limits.max_compute_invocations,
                        max_texture_dimension_2d: limits.max_texture_2d,
                        ..wgpu::Limits::downlevel_defaults()
                    },
                    memory_hints: wgpu::MemoryHints::Performance,
                    ..Default::default()
                })
                .await
        })
        .map_err(GpuError::DeviceRequest)?;

        let info = GpuInfo {
            name: adapter_info.name.clone(),
            vendor,
            driver: adapter_info.driver.clone(),
            driver_info: adapter_info.driver_info.clone(),
            backend: format!("{:?}", adapter_info.backend),
            device_type: format!("{:?}", adapter_info.device_type),
            max_texture_2d: device_limits.max_texture_dimension_2d,
            max_storage_buffer: device_limits.max_storage_buffer_binding_size as u32,
            max_workgroup_invocations: device_limits.max_compute_invocations_per_workgroup,
        };

        log::info!("GPU ready: {}", info.summary());

        Ok(GpuContext {
            instance,
            adapter,
            device,
            queue,
            info,
        })
    }

    pub fn adapter_name(&self) -> String {
        self.info.name.clone()
    }

    /// Get the max 2D texture dimension this GPU supports.
    pub fn max_texture_2d(&self) -> u32 {
        self.info.max_texture_2d
    }

    /// Get the detected GPU vendor.
    pub fn vendor(&self) -> GpuVendor {
        self.info.vendor
    }
}

// Tests — vendor detection, error display, GpuInfo formatting.
// No GPU required; these test pure logic on the data types.
#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn gpu_vendor_display() {
        assert_eq!(format!("{}", GpuVendor::Qualcomm), "Qualcomm Adreno");
        assert_eq!(format!("{}", GpuVendor::Arm), "ARM Mali/Immortalis");
        assert_eq!(format!("{}", GpuVendor::Imagination), "Imagination PowerVR");
        assert_eq!(format!("{}", GpuVendor::Samsung), "Samsung Xclipse");
        assert_eq!(format!("{}", GpuVendor::Software), "Software");
        assert_eq!(format!("{}", GpuVendor::Unknown), "Unknown");
    }

    #[test]
    fn gpu_vendor_equality() {
        assert_eq!(GpuVendor::Qualcomm, GpuVendor::Qualcomm);
        assert_ne!(GpuVendor::Qualcomm, GpuVendor::Arm);
    }

    #[test]
    fn gpu_error_display() {
        assert_eq!(format!("{}", GpuError::NoAdapter), "No GPU adapter found");
        assert_eq!(format!("{}", GpuError::SurfaceLost), "Surface lost");
        assert_eq!(
            format!("{}", GpuError::Other("custom error".to_string())),
            "custom error"
        );
    }

    #[test]
    fn gpu_info_summary_format() {
        let info = GpuInfo {
            name: "Adreno (TM) 740".to_string(),
            vendor: GpuVendor::Qualcomm,
            driver: "v1.0".to_string(),
            driver_info: "v590".to_string(),
            backend: "Vulkan".to_string(),
            device_type: "DiscreteGpu".to_string(),
            max_texture_2d: 4096,
            max_storage_buffer: 4 * 1024 * 1024,
            max_workgroup_invocations: 256,
        };
        let summary = info.summary();
        assert!(summary.contains("Adreno (TM) 740"));
        assert!(summary.contains("Qualcomm Adreno"));
        assert!(summary.contains("4096px"));
        assert!(summary.contains("4096KB"));
    }

    #[test]
    fn adapted_limits_for_all_vendors() {
        // Verify that every vendor variant produces valid limits
        let vendors = [
            GpuVendor::Qualcomm,
            GpuVendor::Arm,
            GpuVendor::Imagination,
            GpuVendor::Samsung,
            GpuVendor::Software,
            GpuVendor::Unknown,
        ];
        for vendor in vendors {
            let limits = AdaptedLimits::for_vendor(vendor);
            assert!(limits.max_storage_buffer >= 2 * 1024 * 1024, "{vendor:?}: ssbo too small");
            assert!(limits.max_storage_buffers_per_stage >= 2, "{vendor:?}: too few SSBO bindings");
            assert!(limits.max_compute_workgroups >= 16384, "{vendor:?}: too few workgroups");
            assert!(limits.max_compute_invocations >= 128, "{vendor:?}: too few invocations");
            assert!(limits.max_texture_2d >= 2048, "{vendor:?}: texture too small");
        }
    }

    #[test]
    fn adapted_limits_qualcomm_vs_imagination() {
        let qc = AdaptedLimits::for_vendor(GpuVendor::Qualcomm);
        let img = AdaptedLimits::for_vendor(GpuVendor::Imagination);
        // Qualcomm should have higher limits than Imagination
        assert!(qc.max_storage_buffer >= img.max_storage_buffer);
        assert!(qc.max_compute_invocations >= img.max_compute_invocations);
        assert!(qc.max_texture_2d >= img.max_texture_2d);
    }
}
