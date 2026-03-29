// Terminal compute shader — one invocation per cell.
// Reads cell data from storage buffer, looks up glyph in atlas,
// writes colored pixels to output storage texture.
//
// Zutty pattern: each workgroup thread handles one cell.
// Workgroup size 8×8 → covers 64 cells per dispatch.

struct Cell {
    atlas_xy: u32,      // packed: x | (y << 16)
    atlas_wh: u32,      // packed: w | (h << 16)
    fg_color: u32,      // ARGB packed
    bg_color: u32,      // ARGB packed
    flags: u32,         // bold, italic, underline, etc.
    _pad: u32,
};

struct Uniforms {
    cell_width: f32,
    cell_height: f32,
    grid_cols: u32,
    grid_rows: u32,
    atlas_width: f32,
    atlas_height: f32,
    output_width: u32,
    output_height: u32,
    cursor_row: i32,
    cursor_col: i32,
    cursor_shape: u32,
    cursor_visible: u32,
};

@group(0) @binding(0) var<storage, read> cells: array<Cell>;
@group(0) @binding(1) var<uniform> uniforms: Uniforms;
@group(0) @binding(2) var atlas_tex: texture_2d<f32>;
@group(0) @binding(3) var atlas_samp: sampler;
@group(0) @binding(4) var output: texture_storage_2d<rgba8unorm, write>;

fn unpack_argb(packed: u32) -> vec4<f32> {
    let a = f32((packed >> 24u) & 0xFFu) / 255.0;
    let r = f32((packed >> 16u) & 0xFFu) / 255.0;
    let g = f32((packed >> 8u) & 0xFFu) / 255.0;
    let b = f32(packed & 0xFFu) / 255.0;
    return vec4<f32>(r, g, b, a);
}

@compute @workgroup_size(8, 8, 1)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
    let col = gid.x;
    let row = gid.y;
    if (col >= uniforms.grid_cols || row >= uniforms.grid_rows) { return; }

    let cell_idx = row * uniforms.grid_cols + col;
    let cell = cells[cell_idx];

    let px_x = u32(f32(col) * uniforms.cell_width);
    let px_y = u32(f32(row) * uniforms.cell_height);
    let cw = u32(uniforms.cell_width);
    let ch = u32(uniforms.cell_height);

    var bg = unpack_argb(cell.bg_color);
    var fg = unpack_argb(cell.fg_color);

    // Inverse video (flag bit 4)
    let inverse = (cell.flags & 16u) != 0u;
    if (inverse) {
        let tmp = bg;
        bg = fg;
        fg = tmp;
    }

    let ax = cell.atlas_xy & 0xFFFFu;
    let ay = cell.atlas_xy >> 16u;
    let gw = cell.atlas_wh & 0xFFFFu;
    let gh = cell.atlas_wh >> 16u;

    // Is cursor at this cell?
    let at_cursor = uniforms.cursor_visible == 1u
        && i32(row) == uniforms.cursor_row
        && i32(col) == uniforms.cursor_col;

    for (var dy: u32 = 0u; dy < ch; dy++) {
        for (var dx: u32 = 0u; dx < cw; dx++) {
            let out_x = px_x + dx;
            let out_y = px_y + dy;
            if (out_x >= uniforms.output_width || out_y >= uniforms.output_height) { continue; }

            var color = bg;

            // Sample glyph from atlas
            if (dx < gw && dy < gh) {
                let uv = vec2<f32>(
                    (f32(ax) + f32(dx) + 0.5) / uniforms.atlas_width,
                    (f32(ay) + f32(dy) + 0.5) / uniforms.atlas_height,
                );
                let alpha = textureSampleLevel(atlas_tex, atlas_samp, uv, 0.0).r;
                color = mix(bg, fg, vec4<f32>(alpha, alpha, alpha, 1.0));
            }

            // Cursor overlay
            if (at_cursor) {
                if (uniforms.cursor_shape == 0u) {
                    // Block cursor: invert colors
                    color = vec4<f32>(1.0 - color.r, 1.0 - color.g, 1.0 - color.b, 1.0);
                } else if (uniforms.cursor_shape == 1u && dy >= ch - 3u) {
                    // Underline cursor: bottom 3px
                    color = fg;
                } else if (uniforms.cursor_shape == 2u && dx < 2u) {
                    // Beam cursor: left 2px
                    color = fg;
                }
            }

            // Underline attribute (flag bit 2)
            if ((cell.flags & 4u) != 0u && dy >= ch - 2u) {
                color = fg;
            }

            // Strikethrough attribute (flag bit 3)
            if ((cell.flags & 8u) != 0u && dy >= ch / 2u - 1u && dy <= ch / 2u) {
                color = fg;
            }

            textureStore(output, vec2<i32>(i32(out_x), i32(out_y)), color);
        }
    }
}
