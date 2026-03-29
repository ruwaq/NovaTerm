package com.termux.view;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Typeface;

import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalRow;
import com.termux.terminal.TextStyle;
import com.termux.terminal.WcWidth;

/**
 * Renderer of a {@link TerminalEmulator} into a {@link Canvas}.
 * <p/>
 * Saves font metrics, so needs to be recreated each time the typeface or font size changes.
 */
public final class TerminalRenderer {

    final int mTextSize;
    final Typeface mTypeface;
    private final Paint mTextPaint = new Paint();

    /** The width of a single mono spaced character obtained by {@link Paint#measureText(String)} on a single 'X'. */
    final float mFontWidth;
    /** The {@link Paint#getFontSpacing()}. See http://www.fampennings.nl/maarten/android/08numgrid/font.png */
    final int mFontLineSpacing;
    /** The {@link Paint#ascent()}. See http://www.fampennings.nl/maarten/android/08numgrid/font.png */
    private final int mFontAscent;
    /** The {@link #mFontLineSpacing} + {@link #mFontAscent}. */
    final int mFontLineSpacingAndAscent;

    private final float[] asciiMeasures = new float[127];

    public TerminalRenderer(int textSize, Typeface typeface) {
        mTextSize = textSize;
        mTypeface = typeface;

        mTextPaint.setTypeface(typeface);
        mTextPaint.setAntiAlias(true);
        mTextPaint.setTextSize(textSize);

        mFontLineSpacing = (int) Math.ceil(mTextPaint.getFontSpacing());
        mFontAscent = (int) Math.ceil(mTextPaint.ascent());
        mFontLineSpacingAndAscent = mFontLineSpacing + mFontAscent;
        mFontWidth = mTextPaint.measureText("X");

        StringBuilder sb = new StringBuilder(" ");
        for (int i = 0; i < asciiMeasures.length; i++) {
            sb.setCharAt(0, (char) i);
            asciiMeasures[i] = mTextPaint.measureText(sb, 0, 1);
        }
    }

    /** Render the terminal to a canvas with at a specified row scroll, and an optional rectangular selection. */
    public final void render(TerminalEmulator mEmulator, Canvas canvas, int topRow,
                             int selectionY1, int selectionY2, int selectionX1, int selectionX2) {
        final boolean reverseVideo = mEmulator.isReverseVideo();
        final int endRow = topRow + mEmulator.mRows;
        final int columns = mEmulator.mColumns;
        final int cursorCol = mEmulator.getCursorCol();
        final int cursorRow = mEmulator.getCursorRow();
        final boolean cursorVisible = mEmulator.shouldCursorBeVisible();
        final TerminalBuffer screen = mEmulator.getScreen();
        final int[] palette = mEmulator.mColors.mCurrentColors;
        final int cursorShape = mEmulator.getCursorStyle();

        if (reverseVideo)
            canvas.drawColor(palette[TextStyle.COLOR_INDEX_FOREGROUND], PorterDuff.Mode.SRC);

        float heightOffset = mFontLineSpacingAndAscent;
        for (int row = topRow; row < endRow; row++) {
            heightOffset += mFontLineSpacing;

            final int cursorX = (row == cursorRow && cursorVisible) ? cursorCol : -1;
            int selx1 = -1, selx2 = -1;
            if (row >= selectionY1 && row <= selectionY2) {
                if (row == selectionY1) selx1 = selectionX1;
                selx2 = (row == selectionY2) ? selectionX2 : mEmulator.mColumns;
            }

            TerminalRow lineObject = screen.allocateFullLineIfNecessary(screen.externalToInternalRow(row));
            final char[] line = lineObject.mText;
            final int charsUsedInLine = lineObject.getSpaceUsed();

            long lastRunStyle = 0;
            boolean lastRunInsideCursor = false;
            boolean lastRunInsideSelection = false;
            int lastRunStartColumn = -1;
            int lastRunStartIndex = 0;
            boolean lastRunFontWidthMismatch = false;
            int currentCharIndex = 0;
            float measuredWidthForRun = 0.f;

            for (int column = 0; column < columns; ) {
                final char charAtIndex = line[currentCharIndex];
                final boolean charIsHighsurrogate = Character.isHighSurrogate(charAtIndex);
                final int charsForCodePoint = charIsHighsurrogate ? 2 : 1;
                final int codePoint = charIsHighsurrogate ? Character.toCodePoint(charAtIndex, line[currentCharIndex + 1]) : charAtIndex;
                final int codePointWcWidth = WcWidth.width(codePoint);
                final boolean insideCursor = (cursorX == column || (codePointWcWidth == 2 && cursorX == column + 1));
                final boolean insideSelection = column >= selx1 && column <= selx2;
                final long style = lineObject.getStyle(column);

                // Check if the measured text width for this code point is not the same as that expected by wcwidth().
                // This could happen for some fonts which are not truly monospace, or for more exotic characters such as
                // smileys which android font renders as wide.
                // If this is detected, we draw this code point scaled to match what wcwidth() expects.
                final float measuredCodePointWidth = (codePoint < asciiMeasures.length) ? asciiMeasures[codePoint] : mTextPaint.measureText(line,
                    currentCharIndex, charsForCodePoint);
                final boolean fontWidthMismatch = Math.abs(measuredCodePointWidth / mFontWidth - codePointWcWidth) > 0.01;

                if (style != lastRunStyle || insideCursor != lastRunInsideCursor || insideSelection != lastRunInsideSelection || fontWidthMismatch || lastRunFontWidthMismatch) {
                    if (column == 0) {
                        // Skip first column as there is nothing to draw, just record the current style.
                    } else {
                        final int columnWidthSinceLastRun = column - lastRunStartColumn;
                        final int charsSinceLastRun = currentCharIndex - lastRunStartIndex;
                        int cursorColor = lastRunInsideCursor ? mEmulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] : 0;
                        boolean invertCursorTextColor = false;
                        if (lastRunInsideCursor && cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK) {
                            invertCursorTextColor = true;
                        }
                        drawTextRun(canvas, line, palette, heightOffset, lastRunStartColumn, columnWidthSinceLastRun,
                            lastRunStartIndex, charsSinceLastRun, measuredWidthForRun,
                            cursorColor, cursorShape, lastRunStyle, reverseVideo || invertCursorTextColor || lastRunInsideSelection);
                    }
                    measuredWidthForRun = 0.f;
                    lastRunStyle = style;
                    lastRunInsideCursor = insideCursor;
                    lastRunInsideSelection = insideSelection;
                    lastRunStartColumn = column;
                    lastRunStartIndex = currentCharIndex;
                    lastRunFontWidthMismatch = fontWidthMismatch;
                }
                measuredWidthForRun += measuredCodePointWidth;
                column += codePointWcWidth;
                currentCharIndex += charsForCodePoint;
                while (currentCharIndex < charsUsedInLine && WcWidth.width(line, currentCharIndex) <= 0) {
                    // Eat combining chars so that they are treated as part of the last non-combining code point,
                    // instead of e.g. being considered inside the cursor in the next run.
                    currentCharIndex += Character.isHighSurrogate(line[currentCharIndex]) ? 2 : 1;
                }
            }

            final int columnWidthSinceLastRun = columns - lastRunStartColumn;
            final int charsSinceLastRun = currentCharIndex - lastRunStartIndex;
            int cursorColor = lastRunInsideCursor ? mEmulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] : 0;
            boolean invertCursorTextColor = false;
            if (lastRunInsideCursor && cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK) {
                invertCursorTextColor = true;
            }
            drawTextRun(canvas, line, palette, heightOffset, lastRunStartColumn, columnWidthSinceLastRun, lastRunStartIndex, charsSinceLastRun,
                measuredWidthForRun, cursorColor, cursorShape, lastRunStyle, reverseVideo || invertCursorTextColor || lastRunInsideSelection);
        }
    }

    private void drawTextRun(Canvas canvas, char[] text, int[] palette, float y, int startColumn, int runWidthColumns,
                             int startCharIndex, int runWidthChars, float mes, int cursor, int cursorStyle,
                             long textStyle, boolean reverseVideo) {
        int foreColor = TextStyle.decodeForeColor(textStyle);
        final int effect = TextStyle.decodeEffect(textStyle);
        int backColor = TextStyle.decodeBackColor(textStyle);
        final boolean bold = (effect & (TextStyle.CHARACTER_ATTRIBUTE_BOLD | TextStyle.CHARACTER_ATTRIBUTE_BLINK)) != 0;
        final boolean underline = (effect & TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE) != 0;
        final boolean italic = (effect & TextStyle.CHARACTER_ATTRIBUTE_ITALIC) != 0;
        final boolean strikeThrough = (effect & TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH) != 0;
        final boolean dim = (effect & TextStyle.CHARACTER_ATTRIBUTE_DIM) != 0;

        if ((foreColor & 0xff000000) != 0xff000000) {
            // Let bold have bright colors if applicable (one of the first 8):
            if (bold && foreColor >= 0 && foreColor < 8) foreColor += 8;
            foreColor = palette[foreColor];
        }

        if ((backColor & 0xff000000) != 0xff000000) {
            backColor = palette[backColor];
        }

        // Reverse video here if _one and only one_ of the reverse flags are set:
        final boolean reverseVideoHere = reverseVideo ^ (effect & (TextStyle.CHARACTER_ATTRIBUTE_INVERSE)) != 0;
        if (reverseVideoHere) {
            int tmp = foreColor;
            foreColor = backColor;
            backColor = tmp;
        }

        float left = startColumn * mFontWidth;
        float right = left + runWidthColumns * mFontWidth;

        mes = mes / mFontWidth;
        boolean savedMatrix = false;
        if (Math.abs(mes - runWidthColumns) > 0.01) {
            canvas.save();
            canvas.scale(runWidthColumns / mes, 1.f);
            left *= mes / runWidthColumns;
            right *= mes / runWidthColumns;
            savedMatrix = true;
        }

        if (backColor != palette[TextStyle.COLOR_INDEX_BACKGROUND]) {
            // Only draw non-default background.
            mTextPaint.setColor(backColor);
            canvas.drawRect(left, y - mFontLineSpacingAndAscent + mFontAscent, right, y, mTextPaint);
        }

        if (cursor != 0) {
            mTextPaint.setColor(cursor);
            float cursorHeight = mFontLineSpacingAndAscent - mFontAscent;
            if (cursorStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE) cursorHeight /= 4.;
            else if (cursorStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR) right -= ((right - left) * 3) / 4.;
            canvas.drawRect(left, y - cursorHeight, right, y, mTextPaint);
        }

        if ((effect & TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE) == 0) {
            if (dim) {
                int red = (0xFF & (foreColor >> 16));
                int green = (0xFF & (foreColor >> 8));
                int blue = (0xFF & foreColor);
                // Dim color handling used by libvte which in turn took it from xterm
                // (https://bug735245.bugzilla-attachments.gnome.org/attachment.cgi?id=284267):
                red = red * 2 / 3;
                green = green * 2 / 3;
                blue = blue * 2 / 3;
                foreColor = 0xFF000000 + (red << 16) + (green << 8) + blue;
            }

            mTextPaint.setFakeBoldText(bold);
            mTextPaint.setUnderlineText(underline);
            mTextPaint.setTextSkewX(italic ? -0.35f : 0.f);
            mTextPaint.setStrikeThruText(strikeThrough);
            mTextPaint.setColor(foreColor);

            // The text alignment is the default Paint.Align.LEFT.
            canvas.drawTextRun(text, startCharIndex, runWidthChars, startCharIndex, runWidthChars, left, y - mFontLineSpacingAndAscent, false, mTextPaint);
        }

        if (savedMatrix) canvas.restore();
    }

    /**
     * Render from a flat grid array produced by the Rust backend.
     *
     * Uses text-run batching (same pattern as the legacy render method):
     * consecutive cells with identical fg+bg+flags are grouped into a single
     * drawTextRun() call. This reduces draw calls from ~2295 (cell-by-cell)
     * to ~50-200 per frame.
     *
     * Grid layout: [char_codepoint, fg_argb, bg_argb, flags] per cell, row-major.
     * Flag bits: 0=bold, 1=italic, 2=underline, 3=strikethrough, 4=inverse,
     *            5=hidden, 6=dim, 7=double_underline, 8=undercurl, 9=wide_char.
     *
     * @param damageLines if non-null, only these rows are redrawn (from Rust damage tracking)
     */
    public final void renderFromGrid(int[] grid, int rows, int cols,
                                     int cursorRow, int cursorCol, int cursorShape, boolean cursorVisible,
                                     Canvas canvas, int topRow,
                                     int selectionY1, int selectionY2, int selectionX1, int selectionX2,
                                     int[] damageLines) {
        final int defaultBg = 0xFF282828;
        final int defaultFg = 0xFFEBDBB2;
        final int cursorColor = 0xFFEBDBB2;

        // Scratch buffer for building text runs (max row width in chars, ×2 for surrogates)
        final char[] runChars = new char[cols * 2];

        float heightOffset = mFontLineSpacingAndAscent;
        for (int viewRow = 0; viewRow < rows; viewRow++) {
            heightOffset += mFontLineSpacing;
            final int gridRow = topRow + viewRow;
            if (gridRow < 0 || gridRow >= rows) continue;

            // Damage tracking: skip undamaged rows
            if (damageLines != null && !isRowDamaged(damageLines, gridRow)) continue;

            final int rowBase = gridRow * cols * 4;
            if (rowBase + cols * 4 > grid.length) continue;

            // Selection range for this row
            int selx1 = -1, selx2 = -1;
            if (gridRow >= selectionY1 && gridRow <= selectionY2) {
                selx1 = (gridRow == selectionY1) ? selectionX1 : 0;
                selx2 = (gridRow == selectionY2) ? selectionX2 : cols;
            }

            // ── Text run batching ────────────────────────────
            int runStartCol = 0;
            int runCharCount = 0;
            int runFg = 0, runBg = 0, runFlags = 0;
            boolean runInsideCursor = false;
            boolean runInsideSelection = false;

            for (int col = 0; col <= cols; col++) {
                // At end of row or at a new cell, check if we need to flush
                int cellFg = defaultFg, cellBg = defaultBg, cellFlags = 0;
                int codePoint = ' ';
                boolean insideCursor = false;
                boolean insideSelection = false;

                if (col < cols) {
                    final int idx = rowBase + col * 4;
                    codePoint = grid[idx];
                    cellFg = grid[idx + 1];
                    cellBg = grid[idx + 2];
                    cellFlags = grid[idx + 3];
                    insideCursor = cursorVisible && gridRow == cursorRow && col == cursorCol;
                    insideSelection = col >= selx1 && col <= selx2;
                }

                // Flush run if style changed or at end of row
                boolean styleChanged = col == cols
                    || cellFg != runFg || cellBg != runBg || cellFlags != runFlags
                    || insideCursor != runInsideCursor || insideSelection != runInsideSelection;

                if (styleChanged && col > 0 && runCharCount > 0) {
                    drawGridRun(canvas, runChars, runCharCount, heightOffset,
                        runStartCol, col - runStartCol, runFg, runBg, runFlags,
                        runInsideCursor, runInsideSelection, cursorShape,
                        cursorColor, defaultBg);
                    runCharCount = 0;
                }

                if (col < cols) {
                    if (styleChanged) {
                        runStartCol = col;
                        runFg = cellFg;
                        runBg = cellBg;
                        runFlags = cellFlags;
                        runInsideCursor = insideCursor;
                        runInsideSelection = insideSelection;
                    }

                    // Append character to run buffer
                    if (codePoint > 0xFFFF) {
                        // Supplementary plane: encode as surrogate pair
                        runChars[runCharCount++] = Character.highSurrogate(codePoint);
                        runChars[runCharCount++] = Character.lowSurrogate(codePoint);
                    } else {
                        runChars[runCharCount++] = (char) codePoint;
                    }

                    // Wide char: skip next column
                    if ((cellFlags & (1 << 9)) != 0) col++;
                }
            }
        }
    }

    /** Overload without damage tracking — redraws everything. */
    public final void renderFromGrid(int[] grid, int rows, int cols,
                                     int cursorRow, int cursorCol, int cursorShape, boolean cursorVisible,
                                     Canvas canvas, int topRow,
                                     int selectionY1, int selectionY2, int selectionX1, int selectionX2) {
        renderFromGrid(grid, rows, cols, cursorRow, cursorCol, cursorShape, cursorVisible,
            canvas, topRow, selectionY1, selectionY2, selectionX1, selectionX2, null);
    }

    /** Check if a row index is in the damage list. */
    private static boolean isRowDamaged(int[] damageLines, int row) {
        for (int d : damageLines) {
            if (d == row) return true;
        }
        return false;
    }

    /**
     * Draw a single text run from the Rust grid.
     * A run is a contiguous group of cells with identical fg, bg, and flags.
     */
    private void drawGridRun(Canvas canvas, char[] chars, int charCount, float y,
                             int startCol, int colWidth, int fg, int bg, int flags,
                             boolean insideCursor, boolean insideSelection, int cursorShape,
                             int cursorColor, int defaultBg) {
        final boolean inverse = (flags & (1 << 4)) != 0;
        final boolean hidden = (flags & (1 << 5)) != 0;
        final boolean dim = (flags & (1 << 6)) != 0;

        // Resolve colors with inverse
        int drawFg = inverse ? bg : fg;
        int drawBg = inverse ? fg : bg;
        if (insideSelection) { int tmp = drawFg; drawFg = drawBg; drawBg = tmp; }

        final float left = startCol * mFontWidth;
        final float right = left + colWidth * mFontWidth;
        final float top = y - mFontLineSpacingAndAscent + mFontAscent;

        // Background (skip default to reduce overdraw)
        if (drawBg != defaultBg) {
            mTextPaint.setColor(drawBg);
            canvas.drawRect(left, top, right, y, mTextPaint);
        }

        // Cursor
        if (insideCursor) {
            mTextPaint.setColor(cursorColor);
            float cursorHeight = mFontLineSpacingAndAscent - mFontAscent;
            float cursorRight = right;
            if (cursorShape == 1) cursorHeight /= 4f; // Underline
            else if (cursorShape == 2) cursorRight = left + mFontWidth / 4f; // Beam
            canvas.drawRect(left, y - cursorHeight, cursorRight, y, mTextPaint);
            // Block cursor: invert text color
            if (cursorShape == 0) drawFg = defaultBg;
        }

        // Text (skip if hidden or all spaces)
        if (!hidden) {
            if (dim) {
                int r = ((drawFg >> 16) & 0xFF) * 2 / 3;
                int g = ((drawFg >> 8) & 0xFF) * 2 / 3;
                int b = (drawFg & 0xFF) * 2 / 3;
                drawFg = 0xFF000000 | (r << 16) | (g << 8) | b;
            }

            mTextPaint.setColor(drawFg);
            mTextPaint.setFakeBoldText((flags & (1 << 0)) != 0);
            mTextPaint.setTextSkewX((flags & (1 << 1)) != 0 ? -0.35f : 0f);
            mTextPaint.setUnderlineText((flags & (1 << 2)) != 0);
            mTextPaint.setStrikeThruText((flags & (1 << 3)) != 0);

            // drawTextRun is batch-optimized in Skia — much faster than per-char drawText
            canvas.drawTextRun(chars, 0, charCount, 0, charCount,
                left, y - mFontLineSpacingAndAscent, false, mTextPaint);
        }
    }

    public float getFontWidth() {
        return mFontWidth;
    }

    public int getFontLineSpacing() {
        return mFontLineSpacing;
    }
}
