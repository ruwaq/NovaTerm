// Glyph atlas: rasterizes glyphs and packs them into a 2048×2048 RGBA texture.
//
// Uses cosmic-text for font loading, shaping, and rasterization (supports
// complex scripts, color emoji, and font fallback). Uses etagere for
// shelf-based bin packing optimized for monospace terminal glyphs.
//
// No GPU dependency — this module produces a CPU-side bitmap that the
// GPU renderer uploads to a texture.

use cosmic_text::{
    Attrs, Buffer, Family, FontSystem, Metrics, Shaping, SwashCache,
};
use etagere::{size2, AllocId, BucketedAtlasAllocator};
use std::collections::HashMap;

/// Default atlas dimensions (2048×2048 RGBA = 16MB, fits ~4000 glyphs at 16×32).
pub const ATLAS_WIDTH: u32 = 2048;
pub const ATLAS_HEIGHT: u32 = 2048;

/// Key for looking up a glyph in the atlas cache.
#[derive(Debug, Clone, Copy, Hash, Eq, PartialEq)]
pub struct GlyphKey {
    pub codepoint: char,
    pub flags: u8, // 0=regular, 1=bold, 2=italic, 3=bold+italic
}

/// Location and metrics of a glyph in the atlas bitmap.
#[derive(Debug, Clone, Copy)]
pub struct AtlasEntry {
    pub x: u16,
    pub y: u16,
    pub width: u8,
    pub height: u8,
    pub bearing_x: i8,
    pub bearing_y: i8,
}

/// Rectangle marking a dirty region in the atlas bitmap.
#[derive(Debug, Clone, Copy)]
pub struct Rect {
    pub x: u32,
    pub y: u32,
    pub width: u32,
    pub height: u32,
}

/// Glyph atlas: CPU-side bitmap + cache + packer.
pub struct GlyphAtlas {
    font_system: FontSystem,
    swash_cache: SwashCache,
    packer: BucketedAtlasAllocator,
    cache: HashMap<GlyphKey, CachedGlyph>,
    lru: std::collections::VecDeque<GlyphKey>,
    bitmap: Vec<u8>,
    dirty_rects: Vec<Rect>,
    cell_width: f32,
    cell_height: f32,
    font_size: f32,
}

struct CachedGlyph {
    entry: AtlasEntry,
    alloc_id: AllocId,
}

impl GlyphAtlas {
    /// Create a new atlas with the system font database.
    /// Loads fonts from /system/fonts/ on Android.
    pub fn new(font_size: f32) -> Self {
        let mut db = cosmic_text::fontdb::Database::new();
        // Load Android system fonts
        db.load_fonts_dir("/system/fonts");
        // Also try NovaTerm prefix fonts if available
        db.load_fonts_dir("/data/data/com.nvterm/files/usr/share/fonts/TTF");
        let mut font_system = FontSystem::new_with_locale_and_db("en-US".to_string(), db);
        let swash_cache = SwashCache::new();

        // Measure cell dimensions using a reference character
        let metrics = Metrics::new(font_size, font_size * 1.2);
        let mut buffer = Buffer::new(&mut font_system, metrics);
        buffer.set_text(
            &mut font_system,
            "X",
            &Attrs::new().family(Family::Monospace),
            Shaping::Advanced,
            None,
        );

        let cell_width = font_size * 0.6; // Approximate monospace width
        let cell_height = font_size * 1.2; // Line height

        // Try to get actual metrics from layout
        let (cw, ch) = buffer
            .layout_runs()
            .next()
            .and_then(|run| {
                run.glyphs.first().map(|g| {
                    (g.w, run.line_height)
                })
            })
            .unwrap_or((cell_width, cell_height));

        let packer = BucketedAtlasAllocator::new(size2(
            ATLAS_WIDTH as i32,
            ATLAS_HEIGHT as i32,
        ));

        Self {
            font_system,
            swash_cache,
            packer,
            cache: HashMap::with_capacity(256),
            lru: std::collections::VecDeque::with_capacity(256),
            bitmap: vec![0u8; (ATLAS_WIDTH * ATLAS_HEIGHT * 4) as usize],
            dirty_rects: Vec::new(),
            cell_width: cw,
            cell_height: ch,
            font_size,
        }
    }

    /// Pre-rasterize ASCII printable characters (0x20-0x7E).
    pub fn pre_rasterize_ascii(&mut self) {
        for cp in 0x20u32..=0x7Eu32 {
            if let Some(ch) = char::from_u32(cp) {
                let key = GlyphKey { codepoint: ch, flags: 0 };
                self.lookup(key);
            }
        }
    }

    /// Look up a glyph, rasterizing on demand if not cached.
    pub fn lookup(&mut self, key: GlyphKey) -> Option<AtlasEntry> {
        // Check cache
        if let Some(cached) = self.cache.get(&key) {
            // Update LRU
            self.lru.retain(|k| k != &key);
            self.lru.push_back(key);
            return Some(cached.entry);
        }

        // Rasterize the glyph
        let (image_data, width, height) = self.rasterize_glyph(key)?;

        if width == 0 || height == 0 {
            // Space or zero-width — create a dummy entry
            let entry = AtlasEntry {
                x: 0, y: 0, width: 0, height: 0,
                bearing_x: 0, bearing_y: 0,
            };
            self.cache.insert(key, CachedGlyph {
                entry,
                alloc_id: AllocId::deserialize(0), // Dummy
            });
            self.lru.push_back(key);
            return Some(entry);
        }

        // Allocate space in atlas (with eviction if full)
        let alloc = loop {
            if let Some(alloc) = self.packer.allocate(size2(width as i32, height as i32)) {
                break alloc;
            }
            // Atlas full — evict LRU
            if !self.evict_lru() {
                log::warn!("Atlas full, cannot allocate {}x{}", width, height);
                return None;
            }
        };

        let ax = alloc.rectangle.min.x as u32;
        let ay = alloc.rectangle.min.y as u32;

        // Copy glyph bitmap to atlas
        for row in 0..height {
            for col in 0..width {
                let src_idx = (row * width + col) as usize;
                let dst_x = ax + col;
                let dst_y = ay + row;
                let dst_idx = ((dst_y * ATLAS_WIDTH + dst_x) * 4) as usize;

                if dst_idx + 3 < self.bitmap.len() && src_idx < image_data.len() {
                    let alpha = image_data[src_idx];
                    self.bitmap[dst_idx] = alpha;     // R = coverage
                    self.bitmap[dst_idx + 1] = alpha; // G = coverage
                    self.bitmap[dst_idx + 2] = alpha; // B = coverage
                    self.bitmap[dst_idx + 3] = alpha; // A = coverage
                }
            }
        }

        // Track dirty region
        self.dirty_rects.push(Rect {
            x: ax, y: ay,
            width, height,
        });

        let entry = AtlasEntry {
            x: ax as u16,
            y: ay as u16,
            width: width.min(255) as u8,
            height: height.min(255) as u8,
            bearing_x: 0,
            bearing_y: 0,
        };

        self.cache.insert(key, CachedGlyph {
            entry,
            alloc_id: alloc.id,
        });
        self.lru.push_back(key);

        Some(entry)
    }

    /// Rasterize a single glyph using cosmic-text's swash cache.
    fn rasterize_glyph(&mut self, key: GlyphKey) -> Option<(Vec<u8>, u32, u32)> {
        let attrs = match key.flags {
            1 => Attrs::new().family(Family::Monospace).weight(cosmic_text::Weight::BOLD),
            2 => Attrs::new().family(Family::Monospace).style(cosmic_text::Style::Italic),
            3 => Attrs::new().family(Family::Monospace)
                .weight(cosmic_text::Weight::BOLD)
                .style(cosmic_text::Style::Italic),
            _ => Attrs::new().family(Family::Monospace),
        };

        let metrics = Metrics::new(self.font_size, self.font_size * 1.2);
        let mut buffer = Buffer::new(&mut self.font_system, metrics);
        let text = key.codepoint.to_string();
        buffer.set_text(&mut self.font_system, &text, &attrs, Shaping::Advanced, None);

        // Get the first glyph from layout
        let run = buffer.layout_runs().next()?;
        let glyph = run.glyphs.first()?;

        // Use swash to rasterize
        let physical = glyph.physical((0.0, 0.0), 1.0);
        let image = self.swash_cache.get_image(&mut self.font_system, physical.cache_key);
        let image = image.as_ref()?;

        let width = image.placement.width;
        let height = image.placement.height;

        // Extract coverage data based on image format
        let coverage = match image.content {
            cosmic_text::SwashContent::Mask => {
                // Grayscale coverage map
                image.data.clone()
            }
            cosmic_text::SwashContent::Color => {
                // RGBA — extract alpha channel as coverage
                image.data.chunks(4).map(|rgba: &[u8]| rgba.get(3).copied().unwrap_or(0)).collect::<Vec<u8>>()
            }
            cosmic_text::SwashContent::SubpixelMask => {
                // RGB subpixel — average for grayscale
                image.data.chunks(3).map(|rgb: &[u8]| {
                    let r = rgb.get(0).copied().unwrap_or(0) as u16;
                    let g = rgb.get(1).copied().unwrap_or(0) as u16;
                    let b = rgb.get(2).copied().unwrap_or(0) as u16;
                    ((r + g + b) / 3) as u8
                }).collect::<Vec<u8>>()
            }
        };

        Some((coverage, width, height))
    }

    /// Evict the least recently used glyph. Returns false if nothing to evict.
    fn evict_lru(&mut self) -> bool {
        if self.lru.is_empty() {
            return false;
        }
        let key = match self.lru.pop_front() {
            Some(k) => k,
            None => return false,
        };
        if let Some(cached) = self.cache.remove(&key) {
            self.packer.deallocate(cached.alloc_id);
            true
        } else {
            false
        }
    }

    /// Get the atlas bitmap (RGBA, ATLAS_WIDTH × ATLAS_HEIGHT).
    pub fn bitmap(&self) -> &[u8] {
        &self.bitmap
    }

    /// Get dirty rectangles since last clear.
    pub fn dirty_rects(&self) -> &[Rect] {
        &self.dirty_rects
    }

    /// Clear dirty rect tracking (call after GPU upload).
    pub fn clear_dirty(&mut self) {
        self.dirty_rects.clear();
    }

    /// Get cell metrics (width, height) in pixels.
    pub fn cell_metrics(&self) -> (f32, f32) {
        (self.cell_width, self.cell_height)
    }

    /// Get font size.
    pub fn font_size(&self) -> f32 {
        self.font_size
    }

    /// Number of cached glyphs.
    pub fn cached_count(&self) -> usize {
        self.cache.len()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn create_atlas() -> GlyphAtlas {
        GlyphAtlas::new(32.0)
    }

    #[test]
    fn ascii_pre_rasterize() {
        let mut atlas = create_atlas();
        atlas.pre_rasterize_ascii();
        // 95 printable ASCII chars (0x20-0x7E)
        assert!(atlas.cached_count() >= 95, "got {}", atlas.cached_count());
    }

    #[test]
    fn cache_hit() {
        let mut atlas = create_atlas();
        let key = GlyphKey { codepoint: 'A', flags: 0 };
        let entry1 = atlas.lookup(key).expect("first lookup");
        let entry2 = atlas.lookup(key).expect("second lookup");
        assert_eq!(entry1.x, entry2.x);
        assert_eq!(entry1.y, entry2.y);
    }

    #[test]
    fn on_demand_unicode() {
        let mut atlas = create_atlas();
        let key = GlyphKey { codepoint: '日', flags: 0 };
        let entry = atlas.lookup(key);
        assert!(entry.is_some(), "CJK char should rasterize");
    }

    #[test]
    fn dirty_rect_tracking() {
        let mut atlas = create_atlas();
        assert!(atlas.dirty_rects().is_empty());
        atlas.lookup(GlyphKey { codepoint: 'X', flags: 0 });
        assert!(!atlas.dirty_rects().is_empty(), "should have dirty rects after rasterize");
        atlas.clear_dirty();
        assert!(atlas.dirty_rects().is_empty(), "should be empty after clear");
    }

    #[test]
    fn cell_metrics_positive() {
        let atlas = create_atlas();
        let (w, h) = atlas.cell_metrics();
        assert!(w > 0.0, "cell width should be positive: {}", w);
        assert!(h > 0.0, "cell height should be positive: {}", h);
    }

    #[test]
    fn bold_variant() {
        let mut atlas = create_atlas();
        let regular = atlas.lookup(GlyphKey { codepoint: 'B', flags: 0 });
        let bold = atlas.lookup(GlyphKey { codepoint: 'B', flags: 1 });
        assert!(regular.is_some());
        assert!(bold.is_some());
        // Bold and regular are cached separately
        assert!(atlas.cached_count() >= 2);
    }

    #[test]
    fn bitmap_not_empty_after_rasterize() {
        let mut atlas = create_atlas();
        atlas.lookup(GlyphKey { codepoint: 'M', flags: 0 });
        let bitmap = atlas.bitmap();
        let non_zero = bitmap.iter().any(|&b| b != 0);
        assert!(non_zero, "bitmap should have non-zero pixels after rasterization");
    }

    #[test]
    fn space_has_entry() {
        let mut atlas = create_atlas();
        let entry = atlas.lookup(GlyphKey { codepoint: ' ', flags: 0 });
        assert!(entry.is_some(), "space should have an atlas entry (even if zero-sized)");
    }

    #[test]
    fn emoji_lookup() {
        let mut atlas = create_atlas();
        // Emoji may or may not be available depending on system fonts
        let _entry = atlas.lookup(GlyphKey { codepoint: '🚀', flags: 0 });
        // Don't assert Some — emoji font may not be installed in Termux
        // Just verify no panic
    }

    // ── Pure data structure tests (no font system needed) ──

    #[test]
    fn glyph_key_equality() {
        let k1 = GlyphKey { codepoint: 'A', flags: 0 };
        let k2 = GlyphKey { codepoint: 'A', flags: 0 };
        let k3 = GlyphKey { codepoint: 'A', flags: 1 }; // bold
        assert_eq!(k1, k2);
        assert_ne!(k1, k3);
    }

    #[test]
    fn glyph_key_hash_consistency() {
        use std::collections::HashMap;
        let mut map = HashMap::new();
        let key = GlyphKey { codepoint: 'X', flags: 2 };
        map.insert(key, 42);
        assert_eq!(map.get(&GlyphKey { codepoint: 'X', flags: 2 }), Some(&42));
        assert_eq!(map.get(&GlyphKey { codepoint: 'X', flags: 0 }), None);
    }

    #[test]
    fn atlas_entry_fields() {
        let entry = AtlasEntry {
            x: 100,
            y: 200,
            width: 16,
            height: 32,
            bearing_x: -1,
            bearing_y: 2,
        };
        assert_eq!(entry.x, 100);
        assert_eq!(entry.width, 16);
    }

    #[test]
    fn rect_fields() {
        let rect = Rect {
            x: 10,
            y: 20,
            width: 30,
            height: 40,
        };
        assert_eq!(rect.width, 30);
        assert_eq!(rect.height, 40);
    }

    #[test]
    fn atlas_constants() {
        assert_eq!(ATLAS_WIDTH, 2048);
        assert_eq!(ATLAS_HEIGHT, 2048);
    }

    #[test]
    fn italic_variant() {
        let mut atlas = create_atlas();
        let regular = atlas.lookup(GlyphKey { codepoint: 'I', flags: 0 });
        let italic = atlas.lookup(GlyphKey { codepoint: 'I', flags: 2 });
        assert!(regular.is_some());
        assert!(italic.is_some());
    }

    #[test]
    fn bold_italic_variant() {
        let mut atlas = create_atlas();
        let bi = atlas.lookup(GlyphKey { codepoint: 'Z', flags: 3 });
        assert!(bi.is_some(), "bold+italic should rasterize");
    }

    #[test]
    fn atlas_bitmap_size() {
        let atlas = create_atlas();
        let bitmap = atlas.bitmap();
        // 2048 * 2048 * 4 bytes (RGBA)
        assert_eq!(bitmap.len(), (ATLAS_WIDTH * ATLAS_HEIGHT * 4) as usize);
    }

    #[test]
    fn font_size_matches() {
        let atlas = create_atlas();
        assert_eq!(atlas.font_size(), 32.0);
    }
}
