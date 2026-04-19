// Software renderer — passes grid data to Java Canvas via JNI.
//
// This is the Phase 2a renderer. It doesn't actually render anything;
// it converts GridSnapshot into a flat i32 buffer that the Java
// TerminalRenderer.renderFromGrid() can consume.
//
// In Phase 2b, this will be replaced by the VulkanRenderer which
// renders directly to an Android Surface via wgpu compute shaders.

use crate::traits::{RenderConfig, Renderer};
use novaterm_vt::GridSnapshot;

/// Software renderer that produces a flat grid buffer for Java Canvas.
pub struct SoftwareRenderer {
    grid_buffer: Vec<i32>,
    rows: usize,
    cols: usize,
}

impl SoftwareRenderer {
    pub fn new() -> Self {
        Self {
            grid_buffer: Vec::new(),
            rows: 0,
            cols: 0,
        }
    }
}

impl Default for SoftwareRenderer {
    fn default() -> Self {
        Self::new()
    }
}

impl Renderer for SoftwareRenderer {
    fn init(&mut self, _config: &RenderConfig) {
        // Software renderer doesn't need font metrics — Java handles that.
    }

    fn render(&mut self, snapshot: &GridSnapshot) {
        let rows = snapshot.rows as usize;
        let cols = snapshot.cols as usize;
        // Guard against overflow: reject impossibly large grids
        let total = match rows.checked_mul(cols).and_then(|n| n.checked_mul(4)) {
            Some(n) => n,
            None => return,
        };

        // Resize buffer if dimensions changed
        if self.grid_buffer.len() != total {
            self.grid_buffer.resize(total, 0);
            self.rows = rows;
            self.cols = cols;
        }

        // Pack cells into flat buffer: [char, fg, bg, flags] per cell
        let max_cells = total / 4;
        for (i, cell) in snapshot.cells.iter().enumerate() {
            if i >= max_cells { break; }
            let base = i * 4;
            self.grid_buffer[base] = cell.character as u32 as i32;
            self.grid_buffer[base + 1] = cell.fg as i32;
            self.grid_buffer[base + 2] = cell.bg as i32;
            self.grid_buffer[base + 3] = cell.flags as i32;
        }
    }

    fn resize(&mut self, _width: u32, _height: u32) {
        // Software renderer doesn't manage surfaces — Java does.
    }

    fn destroy(&mut self) {
        self.grid_buffer.clear();
        self.grid_buffer.shrink_to_fit();
    }

    fn get_grid_buffer(&self) -> Option<&[i32]> {
        if self.grid_buffer.is_empty() {
            None
        } else {
            Some(&self.grid_buffer)
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use novaterm_vt::{CellData, CursorState};

    fn make_snapshot(rows: i32, cols: i32) -> GridSnapshot {
        let cells = vec![
            CellData {
                character: 'A',
                fg: 0xFF_EB_DB_B2,
                bg: 0xFF_28_28_28,
                flags: 1, // bold
            };
            (rows * cols) as usize
        ];
        GridSnapshot {
            cells,
            rows,
            cols,
            cursor: CursorState { row: 0, col: 0, shape: 0, visible: true },
            damage: vec![],
            title: String::new(),
        }
    }

    #[test]
    fn software_renderer_produces_grid() {
        let mut renderer = SoftwareRenderer::new();
        renderer.init(&RenderConfig::default());

        let snap = make_snapshot(24, 80);
        renderer.render(&snap);

        let buf = renderer.get_grid_buffer().expect("should have buffer");
        assert_eq!(buf.len(), 24 * 80 * 4);
        // First cell: 'A', fg, bg, bold
        assert_eq!(buf[0], 'A' as i32);
        assert_eq!(buf[3], 1); // bold flag
    }

    #[test]
    fn software_renderer_handles_resize() {
        let mut renderer = SoftwareRenderer::new();

        let snap1 = make_snapshot(24, 80);
        renderer.render(&snap1);
        assert_eq!(renderer.get_grid_buffer().unwrap().len(), 24 * 80 * 4);

        let snap2 = make_snapshot(48, 120);
        renderer.render(&snap2);
        assert_eq!(renderer.get_grid_buffer().unwrap().len(), 48 * 120 * 4);
    }

    #[test]
    fn software_renderer_destroy_clears() {
        let mut renderer = SoftwareRenderer::new();
        let snap = make_snapshot(24, 80);
        renderer.render(&snap);
        assert!(renderer.get_grid_buffer().is_some());

        renderer.destroy();
        assert!(renderer.get_grid_buffer().is_none());
    }
}
