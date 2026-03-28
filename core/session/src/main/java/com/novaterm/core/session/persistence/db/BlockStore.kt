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
            val values = ContentValues().apply {
                put("cwd", cwd)
                put("updated_at", System.currentTimeMillis())
            }
            db.writableDatabase.update("sessions", values, "id = ?", arrayOf(sessionId))
        }
    }

    fun getSessions(): List<SessionRecord> {
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
        return result
    }

    fun deleteSession(sessionId: String) {
        synchronized(lock) {
            db.writableDatabase.delete("sessions", "id = ?", arrayOf(sessionId))
        }
    }

    fun clearAllSessions() {
        synchronized(lock) {
            db.writableDatabase.delete("sessions", null, null)
            db.writableDatabase.delete("blocks", null, null)
            db.writableDatabase.delete("block_output", null, null)
            db.writableDatabase.delete("snapshots", null, null)
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
    ): String {
        val id = UUID.randomUUID().toString()
        synchronized(lock) {
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
        val cursor = db.readableDatabase.rawQuery(
            "SELECT id, timestamp, command, exit_code, duration_ms, cwd, is_ai_generated " +
            "FROM blocks WHERE session_id = ? ORDER BY timestamp DESC LIMIT ?",
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
        return result.reversed() // chronological order
    }

    // ── Content-Addressable Storage ───────────────────────

    /**
     * Store output data with deduplication.
     * Returns the content hash. If identical content already exists,
     * increments reference count instead of storing a duplicate.
     */
    fun storeOutput(data: ByteArray): String {
        val hash = sha256(data)
        synchronized(lock) {
            val cursor = db.readableDatabase.rawQuery(
                "SELECT ref_count FROM cas_blobs WHERE hash = ?",
                arrayOf(hash),
            )
            val exists = cursor.use { it.moveToFirst() }

            if (exists) {
                db.writableDatabase.execSQL(
                    "UPDATE cas_blobs SET ref_count = ref_count + 1 WHERE hash = ?",
                    arrayOf(hash),
                )
            } else {
                val values = ContentValues().apply {
                    put("hash", hash)
                    put("data", data)
                    put("ref_count", 1)
                    put("size_bytes", data.size)
                    put("created_at", System.currentTimeMillis())
                }
                db.writableDatabase.insert("cas_blobs", null, values)
            }
        }
        return hash
    }

    fun getOutput(hash: String): ByteArray? {
        val cursor = db.readableDatabase.rawQuery(
            "SELECT data FROM cas_blobs WHERE hash = ?",
            arrayOf(hash),
        )
        return cursor.use {
            if (it.moveToFirst()) it.getBlob(0) else null
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
        val cursor = db.readableDatabase.rawQuery(
            "SELECT vt_data, cursor_row, cursor_col, rows, cols, updated_at FROM snapshots WHERE session_id = ?",
            arrayOf(sessionId),
        )
        return cursor.use {
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
    }

    // ── Stats ─────────────────────────────────────────────

    fun getStats(): StoreStats {
        val blockCount = queryCount("SELECT COUNT(*) FROM blocks")
        val sessionCount = queryCount("SELECT COUNT(*) FROM sessions")
        val casCount = queryCount("SELECT COUNT(*) FROM cas_blobs")
        val casSizeBytes = queryLong("SELECT COALESCE(SUM(size_bytes), 0) FROM cas_blobs")
        val dedupSaved = queryLong(
            "SELECT COALESCE(SUM((ref_count - 1) * size_bytes), 0) FROM cas_blobs WHERE ref_count > 1"
        )
        return StoreStats(
            sessions = sessionCount,
            blocks = blockCount,
            casBlobs = casCount,
            casSizeBytes = casSizeBytes,
            dedupSavedBytes = dedupSaved,
        )
    }

    // ── Helpers ────────────────────────────────────────────

    private fun sha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data).joinToString("") { "%02x".format(it) }
    }

    private fun queryCount(sql: String): Int {
        val cursor = db.readableDatabase.rawQuery(sql, null)
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    private fun queryLong(sql: String): Long {
        val cursor = db.readableDatabase.rawQuery(sql, null)
        return cursor.use { if (it.moveToFirst()) it.getLong(0) else 0L }
    }

    fun close() {
        db.close()
    }

    companion object {
        private const val TAG = "BlockStore"
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
