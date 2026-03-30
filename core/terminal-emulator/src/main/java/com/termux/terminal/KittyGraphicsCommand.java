package com.termux.terminal;

import java.util.HashMap;
import java.util.Map;

/**
 * Parsed Kitty Graphics Protocol command.
 *
 * Format: ESC _ G key=val,key=val ; base64_payload ESC \
 *
 * This class parses the control data (key=value pairs) and holds
 * the raw payload. It does NOT decode the image — that's done by
 * {@link KittyGraphicsManager}.
 *
 * @see <a href="https://sw.kovidgoyal.net/kitty/graphics-protocol/">Kitty Graphics Protocol</a>
 */
public final class KittyGraphicsCommand {

    // ── Action (a) ──────────────────────────────────────────

    /** Transmit image data only (don't display). */
    public static final char ACTION_TRANSMIT = 't';
    /** Transmit and display. Default if 'a' is omitted. */
    public static final char ACTION_TRANSMIT_DISPLAY = 'T';
    /** Query terminal support. */
    public static final char ACTION_QUERY = 'q';
    /** Display a previously transmitted image. */
    public static final char ACTION_DISPLAY = 'p';
    /** Delete image(s). */
    public static final char ACTION_DELETE = 'd';

    // ── Format (f) ──────────────────────────────────────────

    /** RGB raw pixels (3 bytes/pixel). Requires s (width) and v (height). */
    public static final int FORMAT_RGB = 24;
    /** RGBA raw pixels (4 bytes/pixel, default). Requires s and v. */
    public static final int FORMAT_RGBA = 32;
    /** PNG image (dimensions from header). */
    public static final int FORMAT_PNG = 100;

    // ── Transmission medium (t) ─────────────────────────────

    /** Direct/inline: payload is image data. */
    public static final char MEDIUM_DIRECT = 'd';
    /** File: payload is base64-encoded file path. */
    public static final char MEDIUM_FILE = 'f';

    // ── Parsed fields ───────────────────────────────────────

    /** Action type. Default: 'T' (transmit+display). */
    public char action = ACTION_TRANSMIT_DISPLAY;
    /** Image format. Default: 32 (RGBA). */
    public int format = FORMAT_RGBA;
    /** Transmission medium. Default: 'd' (direct). */
    public char medium = MEDIUM_DIRECT;
    /** Image ID (0 = auto-assign). */
    public int imageId;
    /** Image number (for auto-assign). */
    public int imageNumber;
    /** Placement ID. */
    public int placementId;
    /** Source image width in pixels (for raw formats). */
    public int sourceWidth;
    /** Source image height in pixels (for raw formats). */
    public int sourceHeight;
    /** Display width in terminal columns. */
    public int displayColumns;
    /** Display height in terminal rows. */
    public int displayRows;
    /** Z-index (negative = behind text). */
    public int zIndex;
    /** More chunks follow (1) or last/only chunk (0). */
    public int moreChunks;
    /** Suppress responses: 0=always, 1=suppress OK, 2=suppress all. */
    public int quiet;
    /** Delete target: 'a'=all, 'i'=by ID, etc. */
    public char deleteTarget;
    /** Don't move cursor after placement. */
    public boolean cursorMovementSuppressed;
    /** Raw base64 payload (not yet decoded). */
    public String payload = "";

    /**
     * Parse a Kitty Graphics command from the APC body.
     *
     * @param apcBody Everything between 'G' and ST, e.g. "f=100,a=T,i=1;base64data"
     * @return Parsed command, or null if not a graphics command.
     */
    public static KittyGraphicsCommand parse(String apcBody) {
        if (apcBody == null || apcBody.isEmpty()) return null;

        KittyGraphicsCommand cmd = new KittyGraphicsCommand();

        // Split control data and payload at first semicolon
        int semicolonIdx = apcBody.indexOf(';');
        String controlData;
        if (semicolonIdx >= 0) {
            controlData = apcBody.substring(0, semicolonIdx);
            cmd.payload = apcBody.substring(semicolonIdx + 1);
        } else {
            controlData = apcBody;
        }

        // Parse key=value pairs
        for (String pair : controlData.split(",")) {
            int eqIdx = pair.indexOf('=');
            if (eqIdx < 0 || eqIdx + 1 >= pair.length()) continue;
            char key = pair.charAt(0);
            String val = pair.substring(eqIdx + 1);

            try {
                switch (key) {
                    case 'a': cmd.action = val.charAt(0); break;
                    case 'f': cmd.format = Integer.parseInt(val); break;
                    case 't': cmd.medium = val.charAt(0); break;
                    case 'i': cmd.imageId = Integer.parseInt(val); break;
                    case 'I': cmd.imageNumber = Integer.parseInt(val); break;
                    case 'p': cmd.placementId = Integer.parseInt(val); break;
                    case 's': cmd.sourceWidth = Integer.parseInt(val); break;
                    case 'v': cmd.sourceHeight = Integer.parseInt(val); break;
                    case 'c': cmd.displayColumns = Integer.parseInt(val); break;
                    case 'r': cmd.displayRows = Integer.parseInt(val); break;
                    case 'z': cmd.zIndex = Integer.parseInt(val); break;
                    case 'm': cmd.moreChunks = Integer.parseInt(val); break;
                    case 'q': cmd.quiet = Integer.parseInt(val); break;
                    case 'd': cmd.deleteTarget = val.charAt(0); break;
                    case 'C': cmd.cursorMovementSuppressed = "1".equals(val); break;
                }
            } catch (NumberFormatException | IndexOutOfBoundsException ignored) {
                // Malformed value — skip this key
            }
        }

        return cmd;
    }

    /** Decode the base64 payload to raw bytes. */
    public byte[] decodePayload() {
        if (payload == null || payload.isEmpty()) return new byte[0];
        try {
            return java.util.Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException e) {
            return new byte[0];
        }
    }

    /** Whether this command has more chunks to follow. */
    public boolean hasMoreChunks() {
        return moreChunks == 1;
    }

    /** Whether this is a query probe (like yazi/viu send to detect support). */
    public boolean isQuery() {
        return action == ACTION_QUERY;
    }

    @Override
    public String toString() {
        return "KittyGraphicsCommand{a=" + action + ", f=" + format + ", t=" + medium +
            ", i=" + imageId + ", m=" + moreChunks + ", payload=" + payload.length() + " chars}";
    }
}
