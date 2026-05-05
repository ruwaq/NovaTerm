package com.novaterm.core.session.persistence.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

/**
 * SQLite database for structured terminal session persistence.
 *
 * Architecture inspired by:
 * - Warp Terminal: blocks as atomic command+output units
 * - Atuin: structured history with context (cwd, exit code, duration)
 * - Git/CAS: content-addressable output deduplication
 * - Mosh SSP: incremental state updates
 *
 * Schema:
 * - sessions: active terminal tabs (id, name, shell, cwd)
 * - blocks: each command executed = 1 block with metadata
 * - block_output: streaming output chunks per block
 * - snapshots: VT-encoded terminal state for visual restoration
 */
class BlockStoreDb(context: Context) : SQLiteOpenHelper(
    context, DATABASE_NAME, null, DATABASE_VERSION,
) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE sessions (
                id TEXT PRIMARY KEY,
                tab_index INTEGER NOT NULL DEFAULT 0,
                tab_name TEXT NOT NULL DEFAULT 'shell',
                shell TEXT NOT NULL,
                cwd TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
        """)

        db.execSQL("""
            CREATE TABLE blocks (
                id TEXT PRIMARY KEY,
                session_id TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                command TEXT NOT NULL DEFAULT '',
                exit_code INTEGER,
                duration_ms INTEGER,
                cwd TEXT,
                content_hash TEXT,
                FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE
            )
        """)

        db.execSQL("""
            CREATE TABLE snapshots (
                session_id TEXT PRIMARY KEY,
                vt_data BLOB,
                cursor_row INTEGER NOT NULL DEFAULT 0,
                cursor_col INTEGER NOT NULL DEFAULT 0,
                rows INTEGER NOT NULL DEFAULT 24,
                cols INTEGER NOT NULL DEFAULT 80,
                updated_at INTEGER NOT NULL,
                FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE
            )
        """)

        // Content-addressable storage for output deduplication
        db.execSQL("""
            CREATE TABLE cas_blobs (
                hash TEXT PRIMARY KEY,
                data BLOB NOT NULL,
                ref_count INTEGER NOT NULL DEFAULT 1,
                size_bytes INTEGER NOT NULL,
                created_at INTEGER NOT NULL
            )
        """)

        // Indices for common queries
        db.execSQL("CREATE INDEX idx_blocks_session ON blocks(session_id, timestamp)")
        db.execSQL("CREATE INDEX idx_blocks_command ON blocks(command)")
        db.execSQL("CREATE INDEX idx_blocks_cwd ON blocks(cwd)")
        Log.i(TAG, "BlockStore database created (v$DATABASE_VERSION)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.w(TAG, "Upgrading database from v$oldVersion to v$newVersion")
        if (oldVersion < 2) {
            db.execSQL("DROP INDEX IF EXISTS idx_block_output_block")
            db.execSQL("DROP TABLE IF EXISTS block_output")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE blocks DROP COLUMN is_ai_generated")
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
        // WAL mode for crash safety and concurrent reads
        db.enableWriteAheadLogging()
    }

    companion object {
        private const val TAG = "BlockStoreDb"
        private const val DATABASE_NAME = "novaterm_blocks.db"
        private const val DATABASE_VERSION = 3
    }
}
