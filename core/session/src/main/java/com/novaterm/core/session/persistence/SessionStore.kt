package com.novaterm.core.session.persistence

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Persists session metadata and group definitions to app-private storage.
 * Sessions are saved as a JSON array in a single file for simplicity.
 * Groups are saved separately so they persist independently.
 *
 * Thread-safe: all operations are synchronized on the respective file lock.
 * File locations:
 *   - /data/data/com.nvterm/files/sessions.json
 *   - /data/data/com.nvterm/files/session_groups.json
 */
class SessionStore(context: Context) {

    private val sessionsFile = File(context.filesDir, SESSIONS_FILENAME)
    private val groupsFile = File(context.filesDir, GROUPS_FILENAME)
    private val sessionsLock = Any()
    private val groupsLock = Any()

    /**
     * Save all active sessions. Called periodically and on service destroy.
     */
    fun save(sessions: List<SessionMetadata>) {
        synchronized(sessionsLock) {
            try {
                sessionsFile.writeText(SessionMetadataSerializer.serialize(sessions))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save sessions", e)
            }
        }
    }

    /**
     * Load previously saved sessions. Returns empty list if none saved or on error.
     */
    fun load(): List<SessionMetadata> {
        synchronized(sessionsLock) {
            return try {
                if (sessionsFile.exists()) {
                    SessionMetadataSerializer.deserialize(sessionsFile.readText())
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
        synchronized(sessionsLock) {
            sessionsFile.delete()
        }
    }

    /**
     * Check if there are saved sessions to restore.
     */
    fun hasSavedSessions(): Boolean {
        synchronized(sessionsLock) {
            return sessionsFile.exists() && sessionsFile.length() > 2 // More than "[]"
        }
    }

    /**
     * Save session group definitions.
     */
    fun saveGroups(groups: List<SessionGroup>) {
        synchronized(groupsLock) {
            try {
                groupsFile.writeText(SessionGroupSerializer.serialize(groups))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save session groups", e)
            }
        }
    }

    /**
     * Load session group definitions. Returns built-in defaults if none saved.
     */
    fun loadGroups(): List<SessionGroup> {
        synchronized(groupsLock) {
            return try {
                if (groupsFile.exists()) {
                    val groups = SessionGroupSerializer.deserialize(groupsFile.readText())
                    if (groups.isEmpty()) SessionGroup.BUILT_INS else groups
                } else {
                    SessionGroup.BUILT_INS
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load session groups", e)
                SessionGroup.BUILT_INS
            }
        }
    }

    /**
     * Clear saved groups (resets to built-in defaults on next load).
     */
    fun clearGroups() {
        synchronized(groupsLock) {
            groupsFile.delete()
        }
    }

    companion object {
        private const val TAG = "SessionStore"
        private const val SESSIONS_FILENAME = "sessions.json"
        private const val GROUPS_FILENAME = "session_groups.json"
    }
}