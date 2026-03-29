// Selection highlight for GPU renderer.
//
// Converts a selection range (start_row, start_col, end_row, end_col)
// into per-cell flags that the compute shader can use to apply
// inverse video on selected cells.

/// Selection range in terminal coordinates.
#[derive(Debug, Clone, Copy, Default)]
pub struct SelectionRange {
    pub start_row: i32,
    pub start_col: i32,
    pub end_row: i32,
    pub end_col: i32,
    pub active: bool,
}

impl SelectionRange {
    /// Check if a cell is within the selection range.
    pub fn contains(&self, row: i32, col: i32) -> bool {
        if !self.active { return false; }

        let (sr, sc, er, ec) = if self.start_row < self.end_row
            || (self.start_row == self.end_row && self.start_col <= self.end_col)
        {
            (self.start_row, self.start_col, self.end_row, self.end_col)
        } else {
            (self.end_row, self.end_col, self.start_row, self.start_col)
        };

        if row < sr || row > er { return false; }
        if row == sr && row == er { return col >= sc && col <= ec; }
        if row == sr { return col >= sc; }
        if row == er { return col <= ec; }
        true // Middle rows are fully selected
    }

    /// Modify cell flags to apply selection highlight (set inverse bit).
    pub fn apply_to_flags(&self, row: i32, col: i32, flags: u32) -> u32 {
        if self.contains(row, col) {
            flags | (1 << 4) // Set inverse flag
        } else {
            flags
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn inactive_selection_contains_nothing() {
        let sel = SelectionRange::default();
        assert!(!sel.contains(0, 0));
        assert!(!sel.contains(5, 10));
    }

    #[test]
    fn single_line_selection() {
        let sel = SelectionRange {
            start_row: 3, start_col: 5,
            end_row: 3, end_col: 15,
            active: true,
        };
        assert!(!sel.contains(3, 4));
        assert!(sel.contains(3, 5));
        assert!(sel.contains(3, 10));
        assert!(sel.contains(3, 15));
        assert!(!sel.contains(3, 16));
        assert!(!sel.contains(2, 10));
        assert!(!sel.contains(4, 10));
    }

    #[test]
    fn multi_line_selection() {
        let sel = SelectionRange {
            start_row: 2, start_col: 10,
            end_row: 5, end_col: 20,
            active: true,
        };
        // Row 2: from col 10 onwards
        assert!(!sel.contains(2, 9));
        assert!(sel.contains(2, 10));
        assert!(sel.contains(2, 79));
        // Row 3-4: entire row
        assert!(sel.contains(3, 0));
        assert!(sel.contains(4, 79));
        // Row 5: up to col 20
        assert!(sel.contains(5, 0));
        assert!(sel.contains(5, 20));
        assert!(!sel.contains(5, 21));
    }

    #[test]
    fn reversed_selection() {
        // End before start — should normalize
        let sel = SelectionRange {
            start_row: 5, start_col: 20,
            end_row: 2, end_col: 10,
            active: true,
        };
        assert!(sel.contains(3, 0));
        assert!(sel.contains(2, 10));
        assert!(sel.contains(5, 20));
    }

    #[test]
    fn apply_to_flags_sets_inverse() {
        let sel = SelectionRange {
            start_row: 0, start_col: 0,
            end_row: 0, end_col: 5,
            active: true,
        };
        let flags = sel.apply_to_flags(0, 3, 0);
        assert_eq!(flags & (1 << 4), 1 << 4, "inverse bit should be set");

        let flags_outside = sel.apply_to_flags(0, 10, 0);
        assert_eq!(flags_outside & (1 << 4), 0, "inverse bit should not be set");
    }
}
