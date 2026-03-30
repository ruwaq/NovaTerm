package com.novaterm.core.session.persistence

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Persists session metadata to app-private storage.
 * Sessions are saved as a JSON array in a single file for simplicity.
 *
 * Thread-safe: all operations are synchronized on the file lock.
 * File location: /data/data/com.nvterm/files/sessions.json
 */
class SessionStore(context: Context) {

    private val file = File(context.filesDir, FILENAME)
    private val lock = Any()

    /**
     * Save all active sessions. Called periodically and on service destroy.
     */
    fun save(sessions: List<SessionMetadata>) {
        synchronized(lock) {
            try {
                file.writeText(SessionMetadataSerializer.serialize(sessions))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save sessions", e)
            }
        }
    }

    /**
     * Load previously saved sessions. Returns empty list if none saved or on error.
     */
    fun load(): List<SessionMetadata> {
        synchronized(lock) {
            return try {
                if (file.exists()) {
                    SessionMetadataSerializer.deserialize(file.readText())
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load sessions", e)
                emptyList()
            }
        }
    }

    /**
     * Clear saved sessions (after successful restore or explicit reset).
     */
    fun clear() {
        synchronized(lock) {
            file.delete()
        }
    }

    /**
     * Check if there are saved sessions to restore.
     */
    fun hasSavedSessions(): Boolean {
        synchronized(lock) {
            return file.exists() && file.length() > 2 // More than "[]"
        }
    }

    companion object {
        private const val TAG = "SessionStore"
        private const val FILENAME = "sessions.json"
    }
}
