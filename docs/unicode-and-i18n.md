# NovaTerm Unicode and Internationalization Guide
**Date:** March 2026 | **Author:** NovaTerm Research

---

## Current State

`WcWidth.java` is based on **Unicode 15** (2022-12-16). Needs update to **Unicode 17.0** (Sep 2025).

## Key Issues

### wcwidth() Problem
Operates on individual codepoints, not grapheme clusters. Emoji ZWJ sequences report wrong widths (e.g., farmer emoji = 4 cells instead of 2).

### Mode 2027 (Grapheme Cluster Support)
- `CSI ? 2027 h` (activate) / `CSI ? 2027 l` (deactivate)
- When active: use grapheme clusters for width calculation
- Supported by: Ghostty, Contour, Foot, WezTerm
- Use `android.icu.text.BreakIterator.getCharacterInstance()` for cluster detection

### Complex Scripts (Arabic, Devanagari, Thai)
NO terminal renders these correctly due to character cell model limitations.
Don't attempt in Phase 1. Phase 2 (HarfBuzz + Rust) enables shaped runs within cell grid.

### Bidirectional Text
Controversial in terminals. Don't implement in Phase 1.
Phase 2 can offer optional display-only bidi (UAX #9) without affecting cursor logic.

## Phase 1 Actions (Priority Order)

1. **Update WcWidth.java to Unicode 17.0** — highest impact, affects all users
2. **Implement Mode 2027** — grapheme cluster support negotiation
3. **Improve emoji rendering** — measure full grapheme cluster width, not per-codepoint
4. **Support custom fonts** — TTF/OTF loading for Nerd Fonts
5. **Custom box drawing** — draw U+2500-U+257F with Canvas.drawLine() for pixel-perfect alignment
6. **Real bold/italic** — use actual font variants instead of fake bold/skew
7. **Modern decorations** — undercurl (Path + Bezier), overline, colored underline, double underline

## Phase 2 Actions (Vulkan + Rust)

1. **SDF font atlas** — scaling without quality loss, zoom without re-rasterize
2. **HarfBuzz in Rust** — enables ligatures and complex script shaping
3. **Hybrid cell + shaped runs** — maintain grid for compatibility, shape runs before rendering
4. **Optional bidi** — display-only UAX #9, toggleable per session
5. **Custom font fallback chain** — user-defined: JetBrains Mono → Noto Arabic → Noto CJK → Emoji
6. **GPU box drawing** — fragment shader for pixel-perfect at any size
7. **Sub-pixel positioning** — Vulkan enables per-glyph sub-pixel placement

## Font Rendering Stack

### Phase 1 (Android Canvas)
```
Font files → Android Paint/Canvas
  → Minikin (itemization, fallback)
  → HarfBuzz (shaping — invoked by Canvas.drawTextRun())
  → Skia (rasterization + compositing)
```

### Phase 2 (Vulkan)
```
Font files → HarfBuzz (harfbuzz-rs) → FreeType → SDF Atlas → wgpu rendering
```
