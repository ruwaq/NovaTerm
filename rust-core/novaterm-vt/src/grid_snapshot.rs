// Grid snapshot types for serializing terminal state across FFI.
//
// These types are designed to be efficiently passed over JNI without
// requiring alacritty_terminal types on the Kotlin side. The bridge
// crate converts these into JNI-compatible formats.

use alacritty_terminal::grid::Dimensions;
use alacritty_terminal::term::cell::Flags as CellFlags;
use alacritty_terminal::term::TermDamage;
use alacritty_terminal::vte::ansi::{Color, CursorShape, NamedColor};

/// A single cell's data, flattened for FFI transfer.
#[derive(Debug, Clone, Copy)]
pub struct CellData {
    /// The character in this cell (0 for empty).
    pub character: char,
    /// Foreground color as packed ARGB (0xAARRGGBB).
    pub fg: u32,
    /// Background color as packed ARGB.
    pub bg: u32,
    /// Cell flags (bold, italic, underline, etc.) as a bitmask.
    pub flags: u32,
}

/// Cursor position and style.
#[derive(Debug, Clone, Copy)]
pub struct CursorState {
    pub row: i32,
    pub col: i32,
    pub shape: u8, // 0=Block, 1=Underline, 2=Beam
    pub visible: bool,
}

/// A range of damaged (changed) lines.
#[derive(Debug, Clone, Copy)]
pub struct DamageRange {
    pub line: i32,
    pub col_start: i32,
    pub col_end: i32,
}

/// Complete snapshot of the visible terminal grid.
///
/// This is the main type passed across the JNI boundary. It contains
/// a flat array of CellData (row-major order) plus metadata.
pub struct GridSnapshot {
    pub cells: Vec<CellData>,
    pub rows: i32,
    pub cols: i32,
    pub cursor: CursorState,
    pub damage: Vec<DamageRange>,
    pub title: String,
}

/// Default 16-color palette (Gruvbox Dark inspired, matches NovaTerm defaults).
/// Used when resolving NamedColor to RGB.
const NAMED_COLORS: [(u8, u8, u8); 16] = [
    (40, 40, 40),     // Black (bg)
    (204, 36, 29),    // Red
    (152, 151, 26),   // Green
    (215, 153, 33),   // Yellow
    (69, 133, 136),   // Blue
    (177, 98, 134),   // Magenta
    (104, 157, 106),  // Cyan
    (168, 153, 132),  // White
    (146, 131, 116),  // Bright Black
    (251, 73, 52),    // Bright Red
    (184, 187, 38),   // Bright Green
    (250, 189, 47),   // Bright Yellow
    (131, 165, 152),  // Bright Blue
    (211, 134, 155),  // Bright Magenta
    (142, 192, 124),  // Bright Cyan
    (235, 219, 178),  // Bright White (fg)
];

/// Resolve an alacritty Color to packed ARGB u32.
pub fn color_to_argb(color: &Color) -> u32 {
    match color {
        Color::Named(named) => {
            let idx = named_color_index(named);
            if idx < 16 {
                let (r, g, b) = NAMED_COLORS[idx];
                pack_argb(255, r, g, b)
            } else {
                // 256-color palette: approximate with a simple mapping
                ansi256_to_argb(idx as u8)
            }
        }
        Color::Spec(rgb) => pack_argb(255, rgb.r, rgb.g, rgb.b),
        Color::Indexed(idx) => {
            if (*idx as usize) < 16 {
                let (r, g, b) = NAMED_COLORS[*idx as usize];
                pack_argb(255, r, g, b)
            } else {
                ansi256_to_argb(*idx)
            }
        }
    }
}

fn named_color_index(named: &NamedColor) -> usize {
    match named {
        NamedColor::Black => 0,
        NamedColor::Red => 1,
        NamedColor::Green => 2,
        NamedColor::Yellow => 3,
        NamedColor::Blue => 4,
        NamedColor::Magenta => 5,
        NamedColor::Cyan => 6,
        NamedColor::White => 7,
        NamedColor::BrightBlack => 8,
        NamedColor::BrightRed => 9,
        NamedColor::BrightGreen => 10,
        NamedColor::BrightYellow => 11,
        NamedColor::BrightBlue => 12,
        NamedColor::BrightMagenta => 13,
        NamedColor::BrightCyan => 14,
        NamedColor::BrightWhite => 15,
        // Foreground/Background/Cursor use theme colors
        NamedColor::Foreground => 15,  // Bright White
        NamedColor::Background => 0,   // Black
        _ => 7, // Default to white for dimmed/cursor variants
    }
}

fn pack_argb(a: u8, r: u8, g: u8, b: u8) -> u32 {
    (a as u32) << 24 | (r as u32) << 16 | (g as u32) << 8 | (b as u32)
}

/// Convert 256-color ANSI index (16-255) to packed ARGB.
fn ansi256_to_argb(idx: u8) -> u32 {
    if idx < 16 {
        let (r, g, b) = NAMED_COLORS[idx as usize];
        return pack_argb(255, r, g, b);
    }
    if idx < 232 {
        // 216-color cube: indices 16-231
        let idx = idx - 16;
        let r = cube_component(idx / 36);
        let g = cube_component((idx / 6) % 6);
        let b = cube_component(idx % 6);
        pack_argb(255, r, g, b)
    } else {
        // Grayscale ramp: indices 232-255
        let gray = 8 + (idx - 232) * 10;
        pack_argb(255, gray, gray, gray)
    }
}

fn cube_component(value: u8) -> u8 {
    if value == 0 { 0 } else { 55 + 40 * value }
}

/// Extract cursor shape as u8 for FFI.
pub fn cursor_shape_to_u8(shape: CursorShape) -> u8 {
    match shape {
        CursorShape::Block => 0,
        CursorShape::Underline => 1,
        CursorShape::Beam => 2,
        // HiddenCursor and other variants default to block
        _ => 0,
    }
}

/// Convert alacritty cell flags to our u32 bitmask.
/// Bit layout:
///   0: bold
///   1: italic
///   2: underline
///   3: strikethrough
///   4: inverse
///   5: hidden
///   6: dim
///   7: double_underline
///   8: undercurl
///   9: wide_char
pub fn cell_flags_to_u32(flags: CellFlags) -> u32 {
    let mut result: u32 = 0;
    if flags.contains(CellFlags::BOLD) { result |= 1 << 0; }
    if flags.contains(CellFlags::ITALIC) { result |= 1 << 1; }
    if flags.contains(CellFlags::UNDERLINE) { result |= 1 << 2; }
    if flags.contains(CellFlags::STRIKEOUT) { result |= 1 << 3; }
    if flags.contains(CellFlags::INVERSE) { result |= 1 << 4; }
    if flags.contains(CellFlags::HIDDEN) { result |= 1 << 5; }
    if flags.contains(CellFlags::DIM) { result |= 1 << 6; }
    if flags.contains(CellFlags::DOUBLE_UNDERLINE) { result |= 1 << 7; }
    if flags.contains(CellFlags::UNDERCURL) { result |= 1 << 8; }
    if flags.contains(CellFlags::WIDE_CHAR) { result |= 1 << 9; }
    result
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn pack_argb_basic() {
        assert_eq!(pack_argb(255, 255, 0, 0), 0xFFFF0000); // Red
        assert_eq!(pack_argb(255, 0, 255, 0), 0xFF00FF00); // Green
        assert_eq!(pack_argb(255, 0, 0, 255), 0xFF0000FF); // Blue
        assert_eq!(pack_argb(255, 0, 0, 0), 0xFF000000);   // Black
        assert_eq!(pack_argb(0, 0, 0, 0), 0x00000000);     // Transparent
    }

    #[test]
    fn ansi256_first_16_match_palette() {
        for i in 0..16u8 {
            let (r, g, b) = NAMED_COLORS[i as usize];
            assert_eq!(ansi256_to_argb(i), pack_argb(255, r, g, b));
        }
    }

    #[test]
    fn ansi256_color_cube() {
        // Color 16 = (0,0,0) in cube
        assert_eq!(ansi256_to_argb(16), pack_argb(255, 0, 0, 0));
        // Color 196 = (5,0,0) in cube → (255, 0, 0)
        assert_eq!(ansi256_to_argb(196), pack_argb(255, 255, 0, 0));
        // Color 21 = (0,0,5) → (0, 0, 255)
        assert_eq!(ansi256_to_argb(21), pack_argb(255, 0, 0, 255));
    }

    #[test]
    fn ansi256_grayscale() {
        // 232 = gray 8
        assert_eq!(ansi256_to_argb(232), pack_argb(255, 8, 8, 8));
        // 255 = gray 238
        assert_eq!(ansi256_to_argb(255), pack_argb(255, 238, 238, 238));
    }

    #[test]
    fn cube_component_values() {
        assert_eq!(cube_component(0), 0);
        assert_eq!(cube_component(1), 95);
        assert_eq!(cube_component(2), 135);
        assert_eq!(cube_component(5), 255);
    }

    #[test]
    fn cell_flags_empty() {
        assert_eq!(cell_flags_to_u32(CellFlags::empty()), 0);
    }

    #[test]
    fn cell_flags_all_mapped() {
        let flags = CellFlags::BOLD | CellFlags::ITALIC | CellFlags::UNDERLINE
            | CellFlags::STRIKEOUT | CellFlags::INVERSE | CellFlags::HIDDEN
            | CellFlags::DIM | CellFlags::DOUBLE_UNDERLINE | CellFlags::UNDERCURL
            | CellFlags::WIDE_CHAR;
        let result = cell_flags_to_u32(flags);
        assert_eq!(result & (1 << 0), 1 << 0, "bold");
        assert_eq!(result & (1 << 1), 1 << 1, "italic");
        assert_eq!(result & (1 << 2), 1 << 2, "underline");
        assert_eq!(result & (1 << 3), 1 << 3, "strikethrough");
        assert_eq!(result & (1 << 4), 1 << 4, "inverse");
        assert_eq!(result & (1 << 5), 1 << 5, "hidden");
        assert_eq!(result & (1 << 6), 1 << 6, "dim");
        assert_eq!(result & (1 << 7), 1 << 7, "double_underline");
        assert_eq!(result & (1 << 8), 1 << 8, "undercurl");
        assert_eq!(result & (1 << 9), 1 << 9, "wide_char");
    }

    #[test]
    fn cursor_shape_mapping() {
        assert_eq!(cursor_shape_to_u8(CursorShape::Block), 0);
        assert_eq!(cursor_shape_to_u8(CursorShape::Underline), 1);
        assert_eq!(cursor_shape_to_u8(CursorShape::Beam), 2);
    }

    #[test]
    fn named_color_foreground_background() {
        // Foreground should map to bright white (15)
        assert_eq!(named_color_index(&NamedColor::Foreground), 15);
        // Background should map to black (0)
        assert_eq!(named_color_index(&NamedColor::Background), 0);
    }
}

/// Build a GridSnapshot from an alacritty_terminal Term.
/// Requires &mut because damage() and renderable_content() consume state.
pub fn snapshot_from_term<T: alacritty_terminal::event::EventListener>(
    term: &mut alacritty_terminal::term::Term<T>,
) -> GridSnapshot {
    let cols = term.grid().columns();
    let rows = term.grid().screen_lines();

    // Guard against integer overflow (#2): reject grids larger than 500x500.
    if rows > 500 || cols > 500 || rows.checked_mul(cols).is_none() {
        return GridSnapshot {
            cells: Vec::new(),
            rows: rows as i32,
            cols: cols as i32,
            cursor: CursorState { row: 0, col: 0, shape: 0, visible: false },
            damage: Vec::new(),
            title: String::new(),
        };
    }
    let total_cells = rows * cols;

    // Read damage FIRST (consumes accumulated damage)
    let damage = match term.damage() {
        TermDamage::Full => {
            (0..rows as i32)
                .map(|line| DamageRange {
                    line,
                    col_start: 0,
                    col_end: cols as i32,
                })
                .collect()
        }
        TermDamage::Partial(iter) => iter
            .map(|d| DamageRange {
                line: d.line as i32,
                col_start: d.left as i32,
                col_end: d.right as i32,
            })
            .collect(),
    };

    // Reset damage after reading
    term.reset_damage();

    // Read cursor from grid (immutable borrow OK after damage is consumed)
    let grid_cursor = term.grid().cursor.point;
    let cursor_state = CursorState {
        row: grid_cursor.line.0,
        col: grid_cursor.column.0 as i32,
        shape: cursor_shape_to_u8(term.cursor_style().shape),
        visible: true,
    };

    // Read renderable content (takes &mut self)
    let content = term.renderable_content();
    let mut cells = vec![
        CellData { character: ' ', fg: 0xFF_EB_DB_B2, bg: 0xFF_28_28_28, flags: 0 };
        total_cells
    ];

    for cell in content.display_iter {
        let line = cell.point.line.0;
        if line < 0 { continue; } // Skip scrollback lines
        let row = line as usize;
        let col = cell.point.column.0;
        if row < rows && col < cols {
            cells[row * cols + col] = CellData {
                character: cell.c,
                fg: color_to_argb(&cell.fg),
                bg: color_to_argb(&cell.bg),
                flags: cell_flags_to_u32(cell.flags),
            };
        }
    }

    GridSnapshot {
        cells,
        rows: rows as i32,
        cols: cols as i32,
        cursor: cursor_state,
        damage,
        title: String::new(),
    }
}
