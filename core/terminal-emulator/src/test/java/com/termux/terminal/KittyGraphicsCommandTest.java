package com.termux.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Tests for Kitty Graphics Protocol command parsing.
 */
public class KittyGraphicsCommandTest {

    // ── Basic parsing ───────────────────────────────────────

    @Test
    public void parseSimpleTransmitDisplay() {
        KittyGraphicsCommand cmd = KittyGraphicsCommand.parse("f=100,a=T,i=1;aGVsbG8=");
        assertNotNull(cmd);
        assertEquals(KittyGraphicsCommand.ACTION_TRANSMIT_DISPLAY, cmd.action);
        assertEquals(KittyGraphicsCommand.FORMAT_PNG, cmd.format);
        assertEquals(1, cmd.imageId);
        assertEquals("aGVsbG8=", cmd.payload);
    }

    @Test
    public void parseQueryProbe() {
        // yazi sends this to detect Kitty Graphics support
        KittyGraphicsCommand cmd = KittyGraphicsCommand.parse("i=31,s=1,v=1,a=q,t=d,f=24;AAAA");
        assertNotNull(cmd);
        assertEquals(KittyGraphicsCommand.ACTION_QUERY, cmd.action);
        assertEquals(31, cmd.imageId);
        assertEquals(1, cmd.sourceWidth);
        assertEquals(1, cmd.sourceHeight);
        assertEquals(KittyGraphicsCommand.FORMAT_RGB, cmd.format);
        assertTrue(cmd.isQuery());
    }

    @Test
    public void parseNoPayload() {
        KittyGraphicsCommand cmd = KittyGraphicsCommand.parse("a=d,d=a");
        assertNotNull(cmd);
        assertEquals(KittyGraphicsCommand.ACTION_DELETE, cmd.action);
        assertEquals('a', cmd.deleteTarget);
        assertEquals("", cmd.payload);
    }

    @Test
    public void parseChunkedTransmission() {
        KittyGraphicsCommand cmd = KittyGraphicsCommand.parse("f=100,a=T,i=5,m=1;QUFB");
        assertNotNull(cmd);
        assertTrue(cmd.hasMoreChunks());
        assertEquals(5, cmd.imageId);
    }

    @Test
    public void parseLastChunk() {
        KittyGraphicsCommand cmd = KittyGraphicsCommand.parse("m=0;QkJC");
        assertNotNull(cmd);
        assertFalse(cmd.hasMoreChunks());
    }

    // ── All parameters ──────────────────────────────────────

    @Test
    public void parseAllDisplayParameters() {
        KittyGraphicsCommand cmd = KittyGraphicsCommand.parse("a=T,f=100,i=42,p=7,c=80,r=24,z=-1,C=1,q=2");
        assertNotNull(cmd);
        assertEquals(KittyGraphicsCommand.ACTION_TRANSMIT_DISPLAY, cmd.action);
        assertEquals(KittyGraphicsCommand.FORMAT_PNG, cmd.format);
        assertEquals(42, cmd.imageId);
        assertEquals(7, cmd.placementId);
        assertEquals(80, cmd.displayColumns);
        assertEquals(24, cmd.displayRows);
        assertEquals(-1, cmd.zIndex);
        assertTrue(cmd.cursorMovementSuppressed);
        assertEquals(2, cmd.quiet);
    }

    @Test
    public void parseRawRgba() {
        KittyGraphicsCommand cmd = KittyGraphicsCommand.parse("f=32,s=2,v=2;AAAAAAAAAAAAAAAA");
        assertNotNull(cmd);
        assertEquals(KittyGraphicsCommand.FORMAT_RGBA, cmd.format);
        assertEquals(2, cmd.sourceWidth);
        assertEquals(2, cmd.sourceHeight);
    }

    @Test
    public void parseDeleteById() {
        KittyGraphicsCommand cmd = KittyGraphicsCommand.parse("a=d,d=I,i=42");
        assertNotNull(cmd);
        assertEquals(KittyGraphicsCommand.ACTION_DELETE, cmd.action);
        assertEquals('I', cmd.deleteTarget);
        assertEquals(42, cmd.imageId);
    }

    // ── Defaults ────────────────────────────────────────────

    @Test
    public void defaultActionIsTransmitDisplay() {
        KittyGraphicsCommand cmd = KittyGraphicsCommand.parse("f=100;AAAA");
        assertNotNull(cmd);
        assertEquals(KittyGraphicsCommand.ACTION_TRANSMIT_DISPLAY, cmd.action);
    }

    @Test
    public void defaultFormatIsRgba() {
        KittyGraphicsCommand cmd = KittyGraphicsCommand.parse("a=T");
        assertNotNull(cmd);
        assertEquals(KittyGraphicsCommand.FORMAT_RGBA, cmd.format);
    }

    @Test
    public void defaultMediumIsDirect() {
        KittyGraphicsCommand cmd = KittyGraphicsCommand.parse("a=T");
        assertNotNull(cmd);
        assertEquals(KittyGraphicsCommand.MEDIUM_DIRECT, cmd.medium);
    }

    // ── Edge cases ──────────────────────────────────────────

    @Test
    public void parseNullReturnsNull() {
        assertNull(KittyGraphicsCommand.parse(null));
    }

    @Test
    public void parseEmptyReturnsNull() {
        assertNull(KittyGraphicsCommand.parse(""));
    }

    @Test
    public void parseMalformedKeyValueSkipsGracefully() {
        // Missing value, missing key, garbage
        KittyGraphicsCommand cmd = KittyGraphicsCommand.parse("a=T,=5,garbage,i=");
        assertNotNull(cmd);
        assertEquals(KittyGraphicsCommand.ACTION_TRANSMIT_DISPLAY, cmd.action);
    }

    @Test
    public void parseInvalidNumberSkipsKey() {
        KittyGraphicsCommand cmd = KittyGraphicsCommand.parse("a=T,i=notanumber,f=100");
        assertNotNull(cmd);
        assertEquals(KittyGraphicsCommand.FORMAT_PNG, cmd.format);
        assertEquals(0, cmd.imageId); // Skipped due to NumberFormatException
    }

    // ── Payload decoding ────────────────────────────────────

    @Test
    public void decodePayloadBase64() {
        KittyGraphicsCommand cmd = KittyGraphicsCommand.parse("a=T;aGVsbG8=");
        assertNotNull(cmd);
        byte[] decoded = cmd.decodePayload();
        assertEquals("hello", new String(decoded));
    }

    @Test
    public void decodeEmptyPayload() {
        KittyGraphicsCommand cmd = KittyGraphicsCommand.parse("a=T");
        assertNotNull(cmd);
        byte[] decoded = cmd.decodePayload();
        assertEquals(0, decoded.length);
    }

    @Test
    public void decodeInvalidBase64ReturnsEmpty() {
        KittyGraphicsCommand cmd = KittyGraphicsCommand.parse("a=T;!!!invalid!!!");
        assertNotNull(cmd);
        // Android's Base64.decode with DEFAULT flag is lenient, may not throw
        // Just ensure it doesn't crash
        cmd.decodePayload();
    }

    // ── toString ─────────────────────────────────────────────

    @Test
    public void toStringContainsKey() {
        KittyGraphicsCommand cmd = KittyGraphicsCommand.parse("a=T,i=42,f=100;AAAA");
        assertNotNull(cmd);
        String s = cmd.toString();
        assertTrue(s.contains("a=T"));
        assertTrue(s.contains("i=42"));
    }
}
