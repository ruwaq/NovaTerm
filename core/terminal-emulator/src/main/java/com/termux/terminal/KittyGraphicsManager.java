package com.termux.terminal;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages Kitty Graphics Protocol images for a terminal session.
 *
 * Handles:
 * - Image transmission (direct PNG/RGBA, chunked)
 * - Image storage with memory budget and LRU eviction
 * - Image placement tracking (anchored to terminal cells)
 * - Query responses for tool detection (yazi, viu, timg, etc.)
 *
 * Memory budget: 128MB default (conservative for 12GB RAM phones).
 * Images are evicted LRU when budget is exceeded.
 *
 * @see <a href="https://sw.kovidgoyal.net/kitty/graphics-protocol/">Kitty Graphics Protocol</a>
 */
public final class KittyGraphicsManager {

    private static final String TAG = "KittyGraphics";

    /** Maximum memory for stored images (128MB). */
    private static final long MAX_MEMORY_BYTES = 128L * 1024 * 1024;

    /** Maximum dimension for a single image (prevent OOM). */
    private static final int MAX_IMAGE_DIMENSION = 10000;

    /** Next auto-assigned image ID. */
    private int nextImageId = 1;

    /** Stored images by ID (LRU order: oldest first). */
    private final LinkedHashMap<Integer, KittyImage> images = new LinkedHashMap<>(16, 0.75f, true);

    /** Active placements (image ID → placement). */
    private final HashMap<Integer, KittyPlacement> placements = new HashMap<>();

    /** Maximum accumulated payload size for chunked transmission (128MB base64 ≈ 96MB raw). */
    private static final int MAX_PENDING_PAYLOAD = 128 * 1024 * 1024;

    /** Chunked transmission in progress (accumulates base64 until m=0). */
    private KittyGraphicsCommand pendingCommand;
    /** Note: Not final to allow recreation after large allocations to free memory. */
    private StringBuilder pendingPayload = new StringBuilder();

    /** Current total memory used by stored images. */
    private long usedMemoryBytes;

    /** Terminal output for sending responses back to the PTY. */
    private final TerminalOutput output;

    public KittyGraphicsManager(TerminalOutput output) {
        this.output = output;
    }

    /**
     * Process a parsed Kitty Graphics command.
     *
     * @param cmd The parsed command from APC sequence.
     * @param cursorRow Current cursor row (for placement).
     * @param cursorCol Current cursor column (for placement).
     */
    public void processCommand(KittyGraphicsCommand cmd, int cursorRow, int cursorCol) {
        // Handle chunked transmission
        if (pendingCommand != null) {
            pendingPayload.append(cmd.payload);
            // Prevent unbounded memory growth from malicious chunk streams
            if (pendingPayload.length() > MAX_PENDING_PAYLOAD) {
                Log.w(TAG, "Chunked payload too large, aborting");
                pendingCommand = null;
                // Recreate StringBuilder to free memory (setLength(0) keeps capacity)
                pendingPayload = new StringBuilder();
                return;
            }
            if (cmd.hasMoreChunks()) {
                return; // More chunks to come
            }
            // Last chunk — merge with pending command
            cmd = pendingCommand;
            cmd.payload = pendingPayload.toString();
            pendingCommand = null;
            pendingPayload.setLength(0);
        } else if (cmd.hasMoreChunks()) {
            // First chunk of multi-chunk transmission
            pendingCommand = cmd;
            pendingPayload.setLength(0);
            pendingPayload.append(cmd.payload);
            return;
        }

        switch (cmd.action) {
            case KittyGraphicsCommand.ACTION_QUERY:
                handleQuery(cmd);
                break;
            case KittyGraphicsCommand.ACTION_TRANSMIT:
                handleTransmit(cmd);
                break;
            case KittyGraphicsCommand.ACTION_TRANSMIT_DISPLAY:
                int id = handleTransmit(cmd);
                if (id > 0) {
                    handleDisplay(id, cmd, cursorRow, cursorCol);
                }
                break;
            case KittyGraphicsCommand.ACTION_DISPLAY:
                handleDisplay(cmd.imageId, cmd, cursorRow, cursorCol);
                break;
            case KittyGraphicsCommand.ACTION_DELETE:
                handleDelete(cmd);
                break;
            default:
                sendResponse(cmd, "EINVAL:unsupported action");
                break;
        }
    }

    // ── Query ───────────────────────────────────────────────

    private void handleQuery(KittyGraphicsCommand cmd) {
        // Respond OK to indicate Kitty Graphics support.
        // Tools like yazi, viu, timg use this to detect capability.
        sendResponse(cmd, "OK");
    }

    // ── Transmit ────────────────────────────────────────────

    /**
     * Decode and store an image.
     * @return The assigned image ID, or -1 on failure.
     */
    private int handleTransmit(KittyGraphicsCommand cmd) {
        // Security: reject file-based transmission (path traversal risk)
        if (cmd.medium == KittyGraphicsCommand.MEDIUM_FILE) {
            sendResponse(cmd, "EINVAL:file transmission not supported");
            return -1;
        }

        byte[] data = cmd.decodePayload();
        if (data.length == 0) {
            sendResponse(cmd, "EINVAL:empty payload");
            return -1;
        }

        Bitmap bitmap;
        try {
            bitmap = decodeImage(cmd, data);
        } catch (Exception e) {
            Log.w(TAG, "Failed to decode image", e);
            sendResponse(cmd, "EINVAL:decode failed");
            return -1;
        }

        if (bitmap == null) {
            sendResponse(cmd, "EINVAL:unsupported format");
            return -1;
        }

        // Assign ID
        int id = cmd.imageId > 0 ? cmd.imageId : nextImageId++;
        if (cmd.imageId <= 0) cmd.imageId = id;

        // Reject images that individually exceed budget
        long bitmapBytes = bitmap.getAllocationByteCount();
        if (bitmapBytes > MAX_MEMORY_BYTES / 2) {
            Log.w(TAG, "Image too large: " + (bitmapBytes / 1024 / 1024) + "MB, rejecting");
            bitmap.recycle();
            sendResponse(cmd, "EINVAL:image too large");
            return -1;
        }

        // Evict old images if over budget
        while (usedMemoryBytes + bitmapBytes > MAX_MEMORY_BYTES && !images.isEmpty()) {
            evictOldest();
        }

        // Remove existing image with same ID
        KittyImage existing = images.remove(id);
        if (existing != null) {
            usedMemoryBytes -= existing.bitmap.getAllocationByteCount();
            existing.bitmap.recycle();
            placements.remove(id);
        }

        // Store
        images.put(id, new KittyImage(id, bitmap));
        usedMemoryBytes += bitmapBytes;

        sendResponse(cmd, "OK");
        return id;
    }

    // ── Display ─────────────────────────────────────────────

    private void handleDisplay(int imageId, KittyGraphicsCommand cmd, int cursorRow, int cursorCol) {
        KittyImage image = images.get(imageId);
        if (image == null) {
            sendResponse(cmd, "ENOENT:image not found");
            return;
        }

        KittyPlacement placement = new KittyPlacement(
            imageId,
            cmd.placementId,
            cursorRow,
            cursorCol,
            cmd.displayColumns,
            cmd.displayRows,
            cmd.zIndex,
            cmd.cursorMovementSuppressed
        );
        placements.put(imageId, placement);
    }

    // ── Delete ──────────────────────────────────────────────

    private void handleDelete(KittyGraphicsCommand cmd) {
        char target = cmd.deleteTarget != 0 ? cmd.deleteTarget : 'a';
        boolean freeData = Character.isUpperCase(target);
        target = Character.toLowerCase(target);

        switch (target) {
            case 'a': // Delete all
                if (freeData) {
                    for (KittyImage img : images.values()) img.bitmap.recycle();
                    images.clear();
                    usedMemoryBytes = 0;
                }
                placements.clear();
                break;

            case 'i': // Delete by image ID
                int id = cmd.imageId;
                placements.remove(id);
                if (freeData) {
                    KittyImage img = images.remove(id);
                    if (img != null) {
                        usedMemoryBytes -= img.bitmap.getAllocationByteCount();
                        img.bitmap.recycle();
                    }
                }
                break;

            default:
                // Other delete targets (by position, z-index, etc.) — simplified
                break;
        }
    }

    // ── Image decoding ──────────────────────────────────────

    private Bitmap decodeImage(KittyGraphicsCommand cmd, byte[] data) {
        switch (cmd.format) {
            case KittyGraphicsCommand.FORMAT_PNG:
                return decodePng(data);
            case KittyGraphicsCommand.FORMAT_RGBA:
                return decodeRaw(data, cmd.sourceWidth, cmd.sourceHeight, true);
            case KittyGraphicsCommand.FORMAT_RGB:
                return decodeRaw(data, cmd.sourceWidth, cmd.sourceHeight, false);
            default:
                return null;
        }
    }

    private Bitmap decodePng(byte[] data) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, opts);

        // Downscale if too large
        int sampleSize = 1;
        while (opts.outWidth / sampleSize > MAX_IMAGE_DIMENSION ||
               opts.outHeight / sampleSize > MAX_IMAGE_DIMENSION) {
            sampleSize *= 2;
        }

        opts.inJustDecodeBounds = false;
        opts.inSampleSize = sampleSize;
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;

        return BitmapFactory.decodeByteArray(data, 0, data.length, opts);
    }

    private Bitmap decodeRaw(byte[] data, int width, int height, boolean hasAlpha) {
        if (width <= 0 || height <= 0 || width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION) {
            return null;
        }

        int bytesPerPixel = hasAlpha ? 4 : 3;
        long expectedSize = (long) width * height * bytesPerPixel;
        if (data.length < expectedSize || expectedSize > MAX_MEMORY_BYTES) return null;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[width * height];

        for (int i = 0; i < width * height; i++) {
            int offset = i * bytesPerPixel;
            int r = data[offset] & 0xFF;
            int g = data[offset + 1] & 0xFF;
            int b = data[offset + 2] & 0xFF;
            int a = hasAlpha ? (data[offset + 3] & 0xFF) : 0xFF;
            pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }

    // ── Response ────────────────────────────────────────────

    private void sendResponse(KittyGraphicsCommand cmd, String message) {
        if (cmd.quiet == 2) return; // Suppress all
        if (cmd.quiet == 1 && "OK".equals(message)) return; // Suppress OK

        StringBuilder resp = new StringBuilder("\033_G");
        if (cmd.imageId > 0) resp.append("i=").append(cmd.imageId);
        if (cmd.imageNumber > 0) {
            if (resp.length() > 3) resp.append(',');
            resp.append("I=").append(cmd.imageNumber);
        }
        resp.append(';').append(message).append("\033\\");

        output.write(resp.toString());
    }

    // ── Memory management ───────────────────────────────────

    private void evictOldest() {
        Iterator<Map.Entry<Integer, KittyImage>> it = images.entrySet().iterator();
        if (it.hasNext()) {
            Map.Entry<Integer, KittyImage> entry = it.next();
            usedMemoryBytes -= entry.getValue().bitmap.getAllocationByteCount();
            entry.getValue().bitmap.recycle();
            placements.remove(entry.getKey());
            it.remove();
            Log.d(TAG, "Evicted image " + entry.getKey() + ", used=" + (usedMemoryBytes / 1024 / 1024) + "MB");
        }
    }

    // ── Public API for renderer ─────────────────────────────

    /** Get all active placements for rendering. Returns a defensive copy (thread-safe). */
    public Map<Integer, KittyPlacement> getPlacements() {
        return new HashMap<>(placements);
    }

    /** Get a stored image by ID. */
    public KittyImage getImage(int id) {
        return images.get(id);
    }

    /** Number of stored images. */
    public int getImageCount() {
        return images.size();
    }

    /** Memory used by stored images in bytes. */
    public long getUsedMemoryBytes() {
        return usedMemoryBytes;
    }

    /** Release all resources. */
    public void destroy() {
        for (KittyImage img : images.values()) img.bitmap.recycle();
        images.clear();
        placements.clear();
        pendingCommand = null;
        pendingPayload.setLength(0);
        usedMemoryBytes = 0;
    }

    // ── Inner classes ───────────────────────────────────────

    /** A stored image with its decoded bitmap. */
    public static final class KittyImage {
        public final int id;
        public final Bitmap bitmap;

        KittyImage(int id, Bitmap bitmap) {
            this.id = id;
            this.bitmap = bitmap;
        }
    }

    /** An image placement anchored to terminal cells. */
    public static final class KittyPlacement {
        public final int imageId;
        public final int placementId;
        public final int row;
        public final int col;
        public final int columns; // Display width in columns (0 = auto)
        public final int rows;    // Display height in rows (0 = auto)
        public final int zIndex;
        public final boolean cursorSuppressed;

        KittyPlacement(int imageId, int placementId, int row, int col,
                       int columns, int rows, int zIndex, boolean cursorSuppressed) {
            this.imageId = imageId;
            this.placementId = placementId;
            this.row = row;
            this.col = col;
            this.columns = columns;
            this.rows = rows;
            this.zIndex = zIndex;
            this.cursorSuppressed = cursorSuppressed;
        }
    }
}
