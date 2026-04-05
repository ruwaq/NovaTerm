package com.novaterm.terminal;

import android.graphics.Bitmap;
import android.util.Log;

/**
 * Minimal Sixel decoder for the Java terminal emulator path.
 *
 * Sixel format (DCS payload after parameters + 'q'):
 * - Color intro: #Pc;Pu;Px;Py;Pz  (register, type, r/h, g/s, b/l)
 * - Raster attributes: "Pan;Pad;Ph;Pv
 * - Sixel data: characters 0x3F ('?') to 0x7E ('~') — each encodes 6 vertical pixels
 * - Repeat: !Pn ch — repeat character ch Pn times
 * - Graphics new line: '-' (moves down 6 rows, resets X to 0)
 * - Graphics carriage return: '$' (resets X to 0 without moving down)
 *
 * The Rust parser (NativeTerminal.parseSixel) should be preferred when available,
 * as it handles edge cases better. This is a fallback for the legacy Java path.
 *
 * @see <a href="https://vt100.net/docs/vt3xx-gp/chapter14.html">VT330/VT340 Sixel Graphics</a>
 */
public final class SixelParser {

    private static final String TAG = "SixelParser";

    /** Maximum image dimension to prevent OOM. */
    private static final int MAX_DIMENSION = 4096;

    /** Maximum number of color registers. */
    private static final int MAX_COLORS = 1024;

    private SixelParser() {}

    /**
     * Parse a Sixel DCS payload into a Bitmap.
     *
     * @param payload The raw DCS payload (after parameter bytes and 'q', before ST).
     * @return Bitmap with the decoded image, or null if parsing fails.
     */
    public static Bitmap parse(byte[] payload) {
        if (payload == null || payload.length == 0) return null;

        try {
            return doParse(payload);
        } catch (Throwable e) {
            // Catch Throwable to handle both parsing errors and missing Android
            // framework classes in unit tests (Bitmap, Log are stubs).
            try {
                Log.w(TAG, "Sixel parse failed", e);
            } catch (Throwable ignored) {
                // Log unavailable in unit tests
            }
            return null;
        }
    }

    private static Bitmap doParse(byte[] payload) {
        // Color registers: index → ARGB
        int[] colorRegisters = new int[MAX_COLORS];
        // Default: register 0 = black, register 1 = blue, etc. (not critical)
        colorRegisters[0] = 0xFF000000;

        // First pass: determine dimensions from raster attributes or data scan
        int rasterWidth = 0, rasterHeight = 0;

        // Working state
        int curX = 0, curY = 0;
        int maxX = 0, maxY = 0;
        int currentColor = 0;

        // We need two passes — first to get dimensions, then to fill pixels.
        // For efficiency, do a single pass collecting into a dynamically grown buffer.

        // Scan for raster attributes first
        int pos = 0;
        while (pos < payload.length) {
            int ch = payload[pos] & 0xFF;
            if (ch == '"') {
                // Raster attributes: "Pan;Pad;Ph;Pv
                pos++;
                int[] params = parseParams(payload, pos, 4);
                pos = skipParams(payload, pos);
                if (params.length >= 4) {
                    rasterWidth = Math.min(params[2], MAX_DIMENSION);
                    rasterHeight = Math.min(params[3], MAX_DIMENSION);
                }
                continue;
            }
            break;
        }

        // If no raster attributes, scan the data to determine dimensions
        if (rasterWidth == 0 || rasterHeight == 0) {
            int scanX = 0, scanY = 0, scanMaxX = 0, scanMaxY = 0;
            int scanPos = 0;
            while (scanPos < payload.length) {
                int ch = payload[scanPos] & 0xFF;
                if (ch == '#') {
                    scanPos++;
                    scanPos = skipParams(payload, scanPos);
                } else if (ch == '"') {
                    scanPos++;
                    scanPos = skipParams(payload, scanPos);
                } else if (ch == '!') {
                    scanPos++;
                    int repeat = parseNumber(payload, scanPos);
                    scanPos = skipNumber(payload, scanPos);
                    if (scanPos < payload.length) {
                        int sixelCh = payload[scanPos] & 0xFF;
                        if (sixelCh >= 0x3F && sixelCh <= 0x7E) {
                            scanX += repeat;
                            if (scanX > scanMaxX) scanMaxX = scanX;
                            scanPos++;
                        }
                    }
                } else if (ch == '-') {
                    // Graphics new line
                    scanY += 6;
                    scanX = 0;
                    scanPos++;
                } else if (ch == '$') {
                    // Graphics carriage return
                    scanX = 0;
                    scanPos++;
                } else if (ch >= 0x3F && ch <= 0x7E) {
                    scanX++;
                    if (scanX > scanMaxX) scanMaxX = scanX;
                    // Check which bits are set for max Y
                    int bits = ch - 0x3F;
                    for (int bit = 5; bit >= 0; bit--) {
                        if ((bits & (1 << bit)) != 0) {
                            int py = scanY + bit;
                            if (py > scanMaxY) scanMaxY = py;
                        }
                    }
                    scanPos++;
                } else {
                    scanPos++;
                }
            }
            if (rasterWidth == 0) rasterWidth = Math.min(scanMaxX, MAX_DIMENSION);
            if (rasterHeight == 0) rasterHeight = Math.min(scanMaxY + 1, MAX_DIMENSION);
        }

        if (rasterWidth <= 0 || rasterHeight <= 0) return null;

        // Allocate pixel buffer (ARGB)
        int[] pixels = new int[rasterWidth * rasterHeight];
        // Initialize to transparent
        // (Java int arrays are zero-initialized, 0x00000000 = transparent black)

        // Second pass: decode pixels
        pos = 0;
        curX = 0;
        curY = 0;
        currentColor = 0;

        while (pos < payload.length) {
            int ch = payload[pos] & 0xFF;

            if (ch == '#') {
                // Color introduction
                pos++;
                int[] params = parseParams(payload, pos, 5);
                pos = skipParams(payload, pos);

                if (params.length >= 1) {
                    int colorIndex = params[0];
                    if (params.length >= 5 && colorIndex >= 0 && colorIndex < MAX_COLORS) {
                        int colorType = params[1];
                        if (colorType == 2) {
                            // RGB (0-100 range)
                            int r = (params[2] * 255 / 100) & 0xFF;
                            int g = (params[3] * 255 / 100) & 0xFF;
                            int b = (params[4] * 255 / 100) & 0xFF;
                            colorRegisters[colorIndex] = 0xFF000000 | (r << 16) | (g << 8) | b;
                        } else if (colorType == 1) {
                            // HLS (Hue 0-360, Lightness 0-100, Saturation 0-100)
                            colorRegisters[colorIndex] = hlsToArgb(params[2], params[3], params[4]);
                        }
                    }
                    if (params.length == 1 && colorIndex >= 0 && colorIndex < MAX_COLORS) {
                        // Just selecting a color, not defining
                        currentColor = colorIndex;
                    } else if (params.length >= 5 && params[0] >= 0 && params[0] < MAX_COLORS) {
                        currentColor = params[0];
                    }
                }
            } else if (ch == '"') {
                // Raster attributes — already parsed
                pos++;
                pos = skipParams(payload, pos);
            } else if (ch == '!') {
                // Repeat introducer
                pos++;
                int repeat = parseNumber(payload, pos);
                pos = skipNumber(payload, pos);
                if (repeat <= 0) repeat = 1;
                if (repeat > MAX_DIMENSION) repeat = MAX_DIMENSION;

                if (pos < payload.length) {
                    int sixelCh = payload[pos] & 0xFF;
                    if (sixelCh >= 0x3F && sixelCh <= 0x7E) {
                        int bits = sixelCh - 0x3F;
                        int argb = (currentColor >= 0 && currentColor < MAX_COLORS)
                            ? colorRegisters[currentColor] : 0xFF000000;
                        for (int r = 0; r < repeat; r++) {
                            if (curX < rasterWidth) {
                                setSixelColumn(pixels, rasterWidth, rasterHeight, curX, curY, bits, argb);
                                curX++;
                            }
                        }
                        pos++;
                    }
                }
            } else if (ch == '-') {
                // Graphics new line
                curY += 6;
                curX = 0;
                pos++;
            } else if (ch == '$') {
                // Graphics carriage return
                curX = 0;
                pos++;
            } else if (ch >= 0x3F && ch <= 0x7E) {
                // Sixel character
                int bits = ch - 0x3F;
                int argb = (currentColor >= 0 && currentColor < MAX_COLORS)
                    ? colorRegisters[currentColor] : 0xFF000000;
                if (curX < rasterWidth) {
                    setSixelColumn(pixels, rasterWidth, rasterHeight, curX, curY, bits, argb);
                }
                curX++;
                pos++;
            } else {
                // Skip unknown characters
                pos++;
            }
        }

        Bitmap bitmap = Bitmap.createBitmap(rasterWidth, rasterHeight, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, rasterWidth, 0, 0, rasterWidth, rasterHeight);
        return bitmap;
    }

    /**
     * Set 6 vertical pixels at (x, y..y+5) based on the sixel bit pattern.
     */
    private static void setSixelColumn(int[] pixels, int width, int height,
                                        int x, int y, int bits, int argb) {
        for (int bit = 0; bit < 6; bit++) {
            if ((bits & (1 << bit)) != 0) {
                int py = y + bit;
                if (py < height && x < width) {
                    pixels[py * width + x] = argb;
                }
            }
        }
    }

    /**
     * Parse semicolon-separated numeric parameters from payload starting at pos.
     */
    private static int[] parseParams(byte[] payload, int pos, int maxParams) {
        int[] result = new int[maxParams];
        int count = 0;
        int value = 0;
        boolean hasValue = false;

        while (pos < payload.length && count < maxParams) {
            int ch = payload[pos] & 0xFF;
            if (ch >= '0' && ch <= '9') {
                value = value * 10 + (ch - '0');
                hasValue = true;
                pos++;
            } else if (ch == ';') {
                if (hasValue) {
                    result[count] = value;
                }
                count++;
                value = 0;
                hasValue = false;
                pos++;
            } else {
                break;
            }
        }
        if (hasValue && count < maxParams) {
            result[count] = value;
            count++;
        }

        int[] trimmed = new int[count];
        System.arraycopy(result, 0, trimmed, 0, count);
        return trimmed;
    }

    /** Skip past parameter bytes (digits and semicolons). */
    private static int skipParams(byte[] payload, int pos) {
        while (pos < payload.length) {
            int ch = payload[pos] & 0xFF;
            if ((ch >= '0' && ch <= '9') || ch == ';') {
                pos++;
            } else {
                break;
            }
        }
        return pos;
    }

    /** Parse a decimal number from payload. */
    private static int parseNumber(byte[] payload, int pos) {
        int value = 0;
        while (pos < payload.length) {
            int ch = payload[pos] & 0xFF;
            if (ch >= '0' && ch <= '9') {
                value = value * 10 + (ch - '0');
                pos++;
            } else {
                break;
            }
        }
        return value;
    }

    /** Skip past a decimal number. */
    private static int skipNumber(byte[] payload, int pos) {
        while (pos < payload.length) {
            int ch = payload[pos] & 0xFF;
            if (ch >= '0' && ch <= '9') {
                pos++;
            } else {
                break;
            }
        }
        return pos;
    }

    /**
     * Convert HLS (Hue 0-360, Lightness 0-100, Saturation 0-100) to ARGB.
     * Note: Sixel uses HLS order (not HSL).
     */
    private static int hlsToArgb(int h, int l, int s) {
        float hf = (h % 360) / 360.0f;
        float lf = l / 100.0f;
        float sf = s / 100.0f;

        if (sf == 0) {
            int v = Math.round(lf * 255);
            return 0xFF000000 | (v << 16) | (v << 8) | v;
        }

        float q = lf < 0.5f ? lf * (1.0f + sf) : lf + sf - lf * sf;
        float p = 2.0f * lf - q;

        int r = Math.round(hueToRgb(p, q, hf + 1.0f / 3.0f) * 255);
        int g = Math.round(hueToRgb(p, q, hf) * 255);
        int b = Math.round(hueToRgb(p, q, hf - 1.0f / 3.0f) * 255);

        return 0xFF000000 | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    private static float hueToRgb(float p, float q, float t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1.0f / 6.0f) return p + (q - p) * 6.0f * t;
        if (t < 0.5f) return q;
        if (t < 2.0f / 3.0f) return p + (q - p) * (2.0f / 3.0f - t) * 6.0f;
        return p;
    }
}
