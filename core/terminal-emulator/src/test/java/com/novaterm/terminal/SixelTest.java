package com.novaterm.terminal;

/**
 * Tests for Sixel DCS sequence handling in the terminal emulator.
 *
 * Sixel format: ESC P [params] q <sixel-data> ESC \
 * The terminal should detect the Sixel DCS, accumulate data,
 * and pass it to SixelParser for decoding.
 */
public class SixelTest extends TerminalTestCase {

    /**
     * Verify that a minimal Sixel DCS sequence doesn't crash or hang the emulator.
     * The Sixel data won't produce a visible image in tests (no Android Bitmap),
     * but the sequence must be consumed without error.
     */
    public void testSixelDcsDoesNotHang() {
        // ESC P q (no params, sixel intro) + color def + data + ESC \
        withTerminalSized(5, 3).enterString("A\033Pq#0;2;100;0;0~\033\\B");
        assertLineIs(0, "AB   ");
    }

    /**
     * Verify that Sixel DCS with parameters is detected correctly.
     */
    public void testSixelDcsWithParams() {
        // ESC P 0;0;0 q <data> ESC \
        withTerminalSized(5, 3).enterString("X\033P0;0;0q#0;2;0;100;0~\033\\Y");
        assertLineIs(0, "XY   ");
    }

    /**
     * Verify that non-Sixel DCS sequences still work correctly.
     * DECRQSS ($q) should not be confused with Sixel.
     */
    public void testNonSixelDcsStillWorks() {
        withTerminalSized(5, 3);
        assertEnteringStringGivesResponse("\033P$q\"p\033\\", "\033P1$r64;1\"p\033\\");
    }

    /**
     * Verify that an empty Sixel DCS doesn't crash.
     */
    public void testEmptySixelDcs() {
        withTerminalSized(5, 3).enterString("A\033Pq\033\\B");
        assertLineIs(0, "AB   ");
    }

    /**
     * Verify that a Sixel sequence with graphics new line (-) doesn't crash.
     */
    public void testSixelWithNewLine() {
        withTerminalSized(5, 3).enterString("\033Pq#0;2;100;0;0~-~\033\\Z");
        assertLineIs(0, "Z    ");
    }

    /**
     * Verify that a Sixel sequence with repeat (!) works without crash.
     */
    public void testSixelWithRepeat() {
        withTerminalSized(5, 3).enterString("\033Pq#0;2;50;50;50!10~\033\\W");
        assertLineIs(0, "W    ");
    }

    /**
     * Verify that termcap query with 'q' in it is not confused with Sixel.
     * The +q DCS starts with '+' before 'q', so isSixelParamPrefix should reject it.
     */
    public void testTermcapQueryNotConfusedWithSixel() {
        // Request termcap for "ku" (up arrow): ESC P + q 6B75 ESC \
        // "ku" = 0x6B 0x75, response value for up arrow = ESC [ A = 1B 5B 41
        withTerminalSized(5, 3);
        assertEnteringStringGivesResponse("\033P+q6B75\033\\", "\033P1+r6B75=1B5B41\033\\");
    }

}
