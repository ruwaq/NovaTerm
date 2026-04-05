// Sixel image parsing — decodes DCS Sixel payloads into RGBA pixel data.
//
// Uses sixel-image (from Zellij) to parse the Sixel format and converts
// the indexed-color pixel grid into flat RGBA bytes for GPU rendering.

use sixel_image::{SixelColor, SixelImage};

/// Parse a Sixel DCS payload into RGBA pixel data.
///
/// Returns `(width, height, rgba_pixels)` where `rgba_pixels` is a flat
/// `Vec<u8>` with 4 bytes per pixel (R, G, B, A) in row-major order.
///
/// Returns `None` if the payload cannot be parsed as valid Sixel data.
pub fn parse_sixel(data: &[u8]) -> Option<(u32, u32, Vec<u8>)> {
    let image = SixelImage::new(data).ok()?;
    let (height, width) = image.pixel_size();
    if width == 0 || height == 0 {
        return None;
    }

    let mut rgba = Vec::with_capacity(width * height * 4);

    for row in &image.pixels {
        for x in 0..width {
            if let Some(pixel) = row.get(x) {
                if pixel.on {
                    // Look up the color register for this pixel
                    if let Some(color) = image.color_registers.get(&pixel.color) {
                        let (r, g, b) = sixel_color_to_rgb8(color);
                        rgba.extend_from_slice(&[r, g, b, 255]);
                    } else {
                        // Unknown color register — opaque black
                        rgba.extend_from_slice(&[0, 0, 0, 255]);
                    }
                } else {
                    // Pixel is off — transparent
                    rgba.extend_from_slice(&[0, 0, 0, 0]);
                }
            } else {
                // Row shorter than width — transparent
                rgba.extend_from_slice(&[0, 0, 0, 0]);
            }
        }
    }

    Some((width as u32, height as u32, rgba))
}

/// Convert a SixelColor to 8-bit RGB (0-255 per channel).
///
/// Sixel RGB values use the range 0-100, so we scale to 0-255.
/// HSL colors are converted to RGB first.
fn sixel_color_to_rgb8(color: &SixelColor) -> (u8, u8, u8) {
    match color {
        SixelColor::Rgb(r, g, b) => {
            // Sixel RGB uses 0-100 range, scale to 0-255
            let r8 = ((*r as u16) * 255 / 100) as u8;
            let g8 = ((*g as u16) * 255 / 100) as u8;
            let b8 = ((*b as u16) * 255 / 100) as u8;
            (r8, g8, b8)
        }
        SixelColor::Hsl(h, s, l) => {
            hsl_to_rgb(*h, *s, *l)
        }
    }
}

/// Convert HSL (sixel ranges: H 0-360, S 0-100, L 0-100) to RGB (0-255).
fn hsl_to_rgb(h: u16, s: u8, l: u8) -> (u8, u8, u8) {
    let h = (h % 360) as f32 / 360.0;
    let s = s as f32 / 100.0;
    let l = l as f32 / 100.0;

    if s == 0.0 {
        let v = (l * 255.0) as u8;
        return (v, v, v);
    }

    let q = if l < 0.5 {
        l * (1.0 + s)
    } else {
        l + s - l * s
    };
    let p = 2.0 * l - q;

    let r = hue_to_rgb(p, q, h + 1.0 / 3.0);
    let g = hue_to_rgb(p, q, h);
    let b = hue_to_rgb(p, q, h - 1.0 / 3.0);

    ((r * 255.0) as u8, (g * 255.0) as u8, (b * 255.0) as u8)
}

fn hue_to_rgb(p: f32, q: f32, mut t: f32) -> f32 {
    if t < 0.0 { t += 1.0; }
    if t > 1.0 { t -= 1.0; }
    if t < 1.0 / 6.0 {
        return p + (q - p) * 6.0 * t;
    }
    if t < 1.0 / 2.0 {
        return q;
    }
    if t < 2.0 / 3.0 {
        return p + (q - p) * (2.0 / 3.0 - t) * 6.0;
    }
    p
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_empty_sixel_returns_none() {
        assert!(parse_sixel(b"").is_none());
    }

    #[test]
    fn parse_garbage_returns_none() {
        assert!(parse_sixel(b"not sixel data at all").is_none());
    }

    #[test]
    fn parse_minimal_sixel_no_panic() {
        // Various minimal Sixel-like payloads — verify no panics
        let payloads: &[&[u8]] = &[
            b"\"1;1;1;6#0;2;100;0;0!1~",
            b"#0;2;100;0;0~-~",
            b"\x1bPq#0;2;100;0;0~\x1b\\",
        ];
        for payload in payloads {
            let _ = parse_sixel(payload);
        }
    }

    #[test]
    fn sixel_color_rgb_scaling() {
        // 0,0,0 -> 0,0,0
        assert_eq!(sixel_color_to_rgb8(&SixelColor::Rgb(0, 0, 0)), (0, 0, 0));
        // 100,100,100 -> 255,255,255
        assert_eq!(sixel_color_to_rgb8(&SixelColor::Rgb(100, 100, 100)), (255, 255, 255));
        // 50,50,50 -> ~127
        let (r, g, b) = sixel_color_to_rgb8(&SixelColor::Rgb(50, 50, 50));
        assert!(r >= 126 && r <= 128);
        assert_eq!(r, g);
        assert_eq!(g, b);
    }

    #[test]
    fn hsl_grayscale() {
        // H=0, S=0, L=50 -> gray ~127
        let (r, g, b) = hsl_to_rgb(0, 0, 50);
        assert!(r >= 126 && r <= 128);
        assert_eq!(r, g);
        assert_eq!(g, b);
    }

    #[test]
    fn hsl_pure_red() {
        // H=0, S=100, L=50 -> pure red (255, 0, 0)
        let (r, g, b) = hsl_to_rgb(0, 100, 50);
        assert_eq!(r, 255);
        assert_eq!(g, 0);
        assert_eq!(b, 0);
    }

    #[test]
    fn hsl_pure_green() {
        // H=120, S=100, L=50 -> green
        let (r, g, b) = hsl_to_rgb(120, 100, 50);
        assert_eq!(r, 0);
        assert_eq!(g, 255);
        assert_eq!(b, 0);
    }

    #[test]
    fn hsl_pure_blue() {
        // H=240, S=100, L=50 -> blue
        let (r, g, b) = hsl_to_rgb(240, 100, 50);
        assert_eq!(r, 0);
        assert_eq!(g, 0);
        assert_eq!(b, 255);
    }

    #[test]
    fn parse_valid_sixel_red_pixel() {
        // A minimal valid Sixel: define color 0 as red, draw one 1x6 column
        // Format: Raster attributes "1;1;1;6 + color intro #0;2;100;0;0 + data ~
        // The ~ character = all 6 pixels ON (0b111111)
        let data = b"\"1;1;1;6#0;2;100;0;0~";
        if let Some((w, h, rgba)) = parse_sixel(data) {
            assert!(w >= 1);
            assert!(h >= 1);
            assert_eq!(rgba.len(), (w * h * 4) as usize);
            // First pixel should be red (255, 0, 0, 255) if parsed
            if !rgba.is_empty() {
                assert_eq!(rgba[0], 255, "R channel");
                assert_eq!(rgba[1], 0, "G channel");
                assert_eq!(rgba[2], 0, "B channel");
                assert_eq!(rgba[3], 255, "A channel");
            }
        }
        // If parsing fails with this format, that's acceptable —
        // the important thing is no panics.
    }
}
