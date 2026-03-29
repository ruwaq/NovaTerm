// Frame encoding: compute pass + copy to surface + present.
//
// Encodes one frame of terminal rendering:
// 1. Dispatch compute shader (cell -> glyph -> output texture)
// 2. Copy output texture to surface texture
// 3. Present the surface texture

#[cfg(feature = "vulkan")]
pub fn encode_and_present(
    device: &wgpu::Device,
    queue: &wgpu::Queue,
    pipeline: &super::pipeline::TerminalPipeline,
    bind_group: &wgpu::BindGroup,
    output_texture: &wgpu::Texture,
    surface_texture: wgpu::SurfaceTexture,
    grid_cols: u32,
    grid_rows: u32,
) -> bool {
    let mut encoder = device.create_command_encoder(&wgpu::CommandEncoderDescriptor {
        label: Some("frame_encoder"),
    });

    // Compute pass: cell -> glyph -> output texture
    {
        let mut pass = encoder.begin_compute_pass(&wgpu::ComputePassDescriptor {
            label: Some("terminal_compute"),
            ..Default::default()
        });
        pass.set_pipeline(&pipeline.pipeline);
        pass.set_bind_group(0, bind_group, &[]);
        let (wx, wy) = super::pipeline::TerminalPipeline::dispatch_size(grid_cols, grid_rows);
        pass.dispatch_workgroups(wx, wy, 1);
    }

    // Copy output texture -> surface texture
    let output_size = output_texture.size();
    let surface_size = surface_texture.texture.size();
    let copy_width = output_size.width.min(surface_size.width);
    let copy_height = output_size.height.min(surface_size.height);

    if copy_width > 0 && copy_height > 0 {
        encoder.copy_texture_to_texture(
            output_texture.as_image_copy(),
            surface_texture.texture.as_image_copy(),
            wgpu::Extent3d {
                width: copy_width,
                height: copy_height,
                depth_or_array_layers: 1,
            },
        );
    }

    queue.submit(std::iter::once(encoder.finish()));
    surface_texture.present();
    true
}
