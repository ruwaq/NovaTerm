package com.novaterm.core.session.persistence.db

import android.content.ContentValues
import android.content.Context
import android.util.Log
import java.security.MessageDigest
import java.util.UUID

/**
 * High-level API for the structured block store.
 *
 * Each terminal command creates a "block" (command + output as a unit),
 * inspired by Warp Terminal's block model. Output is deduplicated using
 * content-addressable storage (CAS) — identical outputs are stored once.
 *
 * Thread-safe: all write operations are synchronized.
 */
class BlockStore(context: Context) {

    private val db = BlockStoreDb(context)
    private val lock = Any()
    @Volatile private var closed = false

    // ── Sessions ──────────────────────────────────────────

    fun saveSession(
        id: String,
        tabIndex: Int,
        tabName: String,
        shell: String,
        cwd: String,
    ) {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            if (closed) return
            val values = ContentValues().apply {
                put("id", id)
                put("tab_index", tabIndex)
                put("tab_name", tabName)
                put("shell", shell)
                put("cwd", cwd)
                put("created_at", now)
                put("updated_at", now)
            }
            db.writableDatabase.insertWithOnConflict(
                "sessions", null, values,
                android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
    }

    fun updateSessionCwd(sessionId: String, cwd: String) {
        synchronized(lock) {
            if (closed) return
            val values = ContentValues().apply {
                put("cwd", cwd)
                put("updated_at", System.currentTimeMillis())
            }
            db.writableDatabase.update("sessions", values, "id = ?", arrayOf(sessionId))
        }
    }

    fun getSessions(): List<SessionRecord> {
        return synchronized(lock) {
            if (closed) return emptyList()
            try {
                val cursor = db.readableDatabase.rawQuery(
                    "SELECT id, tab_index, tab_name, shell, cwd, created_at FROM sessions ORDER BY tab_index",
                    null,
                )
                val result = mutableListOf<SessionRecord>()
                cursor.use {
                    while (it.moveToNext()) {
                        result.add(SessionRecord(
                            id = it.getString(0),
                            tabIndex = it.getInt(1),
                            tabName = it.getString(2),
                            shell = it.getString(3),
                            cwd = it.getString(4),
                            createdAt = it.getLong(5),
                        ))
                    }
                }
                result
            } catch (e: Exception) {
                Log.e(TAG, "getSessions failed", e)
                emptyList()
            }
        }
    }

    fun deleteSession(sessionId: String) {
        synchronized(lock) {
            if (closed) return
            db.writableDatabase.delete("sessions", "id = ?", arrayOf(sessionId))
        }
    }

    fun clearAllSessions() {
        synchronized(lock) {
            if (closed) return
            val wdb = db.writableDatabase
            wdb.beginTransaction()
            try {
                wdb.delete("sessions", null, null)
                wdb.delete("blocks", null, null)
                wdb.delete("snapshots", null, null)
                wdb.delete("cas_blobs", null, null)
                wdb.setTransactionSuccessful()
            } finally {
                wdb.endTransaction()
            }
        }
    }

    // ── Blocks (command + output units) ───────────────────

    fun insertBlock(
        sessionId: String,
        command: String,
        cwd: String? = null,
        exitCode: Int? = null,
        durationMs: Long? = null,
        isAiGenerated: Boolean = false,
    ): String? {
        val id = UUID.randomUUID().toString()
        synchronized(lock) {
            if (closed) return null
            val values = ContentValues().apply {
                put("id", id)
                put("session_id", sessionId)
                put("timestamp", System.currentTimeMillis())
                put("command", command)
                put("cwd", cwd)
                put("exit_code", exitCode)
                put("duration_ms", durationMs)
                put("is_ai_generated", if (isAiGenerated) 1 else 0)
            }
            db.writableDatabase.insert("blocks", null, values)
        }
        return id
    }

    fun getRecentBlocks(sessionId: String, limit: Int = 50): List<BlockRecord> {
        return synchronized(lock) {
            if (closed) return emptyList()
            try {
                val cursor = db.readableDatabase.rawQuery(
                    "SELECT id, timestamp, command, exit_code, duration_ms, cwd, is_ai_generated " +
                    "FROM blocks WHERE session_id = ? ORDER BY timestamp ASC LIMIT ?",
                    arrayOf(sessionId, limit.toString()),
                )
                val result = mutableListOf<BlockRecord>()
                cursor.use {
                    while (it.moveToNext()) {
                        result.add(BlockRecord(
                            id = it.getString(0),
                            timestamp = it.getLong(1),
                            command = it.getString(2),
                            exitCode = if (it.isNull(3)) null else it.getInt(3),
                            durationMs = if (it.isNull(4)) null else it.getLong(4),
                            cwd = it.getString(5),
                            isAiGenerated = it.getInt(6) == 1,
                        ))
                    }
                }
                result
            } catch (e: Exception) {
                Log.e(TAG, "getRecentBlocks failed", e)
                emptyList()
            }
        }
    }

    // ── Content-Addressable Storage ───────────────────────

    /**
     * Store output data with deduplication.
     * Returns the content hash. If identical content already exists,
     * increments reference count instead of storing a duplicate.
     */
    fun storeOutput(data: ByteArray): String? {
        val hash = sha256(data)
        synchronized(lock) {
            if (closed) return null
            try {
                // Use INSERT OR IGNORE + UPDATE to avoid TOCTOU race condition.
                // If another thread inserts the same hash between our check and insert,
                // INSERT OR IGNORE safely no-ops, and the UPDATE bumps ref_count.
                val wdb = db.writableDatabase
                wdb.beginTransaction()
                try {
                    val values = ContentValues().apply {
                        put("hash", hash)
                        put("data", data)
                        put("ref_count", 1)
                        put("size_bytes", data.size)
                        put("created_at", System.currentTimeMillis())
                    }
                    val inserted = wdb.insertWithOnConflict(
                        "cas_blobs", null, values,
                        android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE,
                    )
                    if (inserted == -1L) {
                        // Row already exists — bump reference count atomically
                        wdb.execSQL(
                            "UPDATE cas_blobs SET ref_count = ref_count + 1 WHERE hash = ?",
                            arrayOf(hash),
                        )
                    }
                    wdb.setTransactionSuccessful()
                } finally {
                    wdb.endTransaction()
                }
            } catch (e: Exception) {
                android.util.Log.e("BlockStore", "storeOutput failed for hash=$hash", e)
                return null
            }
        }
        return hash
    }

    fun getOutput(hash: String): ByteArray? {
        return synchronized(lock) {
            if (closed) return null
            try {
                val cursor = db.readableDatabase.rawQuery(
                    "SELECT data FROM cas_blobs WHERE hash = ?",
                    arrayOf(hash),
                )
                cursor.use {
                    if (it.moveToFirst()) it.getBlob(0) else null
                }
            } catch (e: Exception) {
                Log.e(TAG, "getOutput failed", e)
                null
            }
        }
    }

    // ── Snapshots (VT state for visual restoration) ───────

    fun saveSnapshot(
        sessionId: String,
        vtData: ByteArray,
        cursorRow: Int,
        cursorCol: Int,
        rows: Int,
        cols: Int,
    ) {
        synchronized(lock) {
            if (closed) return
            val values = ContentValues().apply {
                put("session_id", sessionId)
                put("vt_data", vtData)
                put("cursor_row", cursorRow)
                put("cursor_col", cursorCol)
                put("rows", rows)
                put("cols", cols)
                put("updated_at", System.currentTimeMillis())
            }
            db.writableDatabase.insertWithOnConflict(
                "snapshots", null, values,
                android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
    }

    fun getSnapshot(sessionId: String): SnapshotRecord? {
        return synchronized(lock) {
            if (closed) return null
            try {
                val cursor = db.readableDatabase.rawQuery(
                    "SELECT vt_data, cursor_row, cursor_col, rows, cols, updated_at FROM snapshots WHERE session_id = ?",
                    arrayOf(sessionId),
                )
                cursor.use {
                    if (it.moveToFirst()) {
                        SnapshotRecord(
                            vtData = it.getBlob(0),
                            cursorRow = it.getInt(1),
                            cursorCol = it.getInt(2),
                            rows = it.getInt(3),
                            cols = it.getInt(4),
                            updatedAt = it.getLong(5),
                        )
                    } else null
                }
            } catch (e: Exception) {
                Log.e(TAG, "getSnapshot failed", e)
                null
            }
        }
    }

    // ── Stats ─────────────────────────────────────────────

    fun getStats(): StoreStats {
        return synchronized(lock) {
            if (closed) return StoreStats(0, 0, 0, 0L, 0L)
            try {
                val blockCount = queryCount("SELECT COUNT(*) FROM blocks")
                val sessionCount = queryCount("SELECT COUNT(*) FROM sessions")
                val casCount = queryCount("SELECT COUNT(*) FROM cas_blobs")
                val casSizeBytes = queryLong("SELECT COALESCE(SUM(size_bytes), 0) FROM cas_blobs")
                val dedupSaved = queryLong(
                    "SELECT COALESCE(SUM((ref_count - 1) * size_bytes), 0) FROM cas_blobs WHERE ref_count > 1"
                )
                StoreStats(
                    sessions = sessionCount,
                    blocks = blockCount,
                    casBlobs = casCount,
                    casSizeBytes = casSizeBytes,
                    dedupSavedBytes = dedupSaved,
                )
            } catch (e: Exception) {
                Log.e(TAG, "getStats failed", e)
                StoreStats(0, 0, 0, 0L, 0L)
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────

    private fun sha256(data: ByteArray): String {
        // ThreadLocal avoids the cost of getInstance() on every call (reflection + init).
        val digest = requireNotNull(SHA256_DIGEST.get()) { "SHA-256 digest unavailable" }
        digest.reset()
        val bytes = digest.digest(data)
        // Manual hex is ~3x faster than joinToString + String.format
        val hex = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hex[i * 2] = HEX_CHARS[v ushr 4]
            hex[i * 2 + 1] = HEX_CHARS[v and 0x0F]
        }
        return String(hex)
    }

    private fun queryCount(sql: String): Int {
        val cursor = db.readableDatabase.rawQuery(sql, null)
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    private fun queryLong(sql: String): Long {
        val cursor = db.readableDatabase.rawQuery(sql, null)
        return cursor.use { if (it.moveToFirst()) it.getLong(0) else 0L }
    }

    val isClosed: Boolean get() = closed

    fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
        }
        try { db.close() } catch (e: Exception) { Log.w(TAG, "BlockStore close failed", e) }
    }

    companion object {
        private const val TAG = "BlockStore"
        private val SHA256_DIGEST = ThreadLocal.withInitial<MessageDigest> {
            MessageDigest.getInstance("SHA-256")
        }
        private val HEX_CHARS = "0123456789abcdef".toCharArray()
    }
}

// ── Data records ──────────────────────────────────────────

data class SessionRecord(
    val id: String,
    val tabIndex: Int,
    val tabName: String,
    val shell: String,
    val cwd: String,
    val createdAt: Long,
)

data class BlockRecord(
    val id: String,
    val timestamp: Long,
    val command: String,
    val exitCode: Int?,
    val durationMs: Long?,
    val cwd: String?,
    val isAiGenerated: Boolean,
)

data class SnapshotRecord(
    val vtData: ByteArray,
    val cursorRow: Int,
    val cursorCol: Int,
    val rows: Int,
    val cols: Int,
    val updatedAt: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SnapshotRecord) return false
        return vtData.contentEquals(other.vtData) &&
            cursorRow == other.cursorRow && cursorCol == other.cursorCol &&
            rows == other.rows && cols == other.cols
    }

    override fun hashCode(): Int = vtData.contentHashCode()
}

data class StoreStats(
    val sessions: Int,
    val blocks: Int,
    val casBlobs: Int,
    val casSizeBytes: Long,
    val dedupSavedBytes: Long,
)
