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

    // Dirty tracking: skip write when data hasn't changed since last save.
    @Volatile private var sessionsDirty = true
    @Volatile private var lastSavedHash: Int = 0

    /**
     * Save all active sessions. Called periodically and on service destroy.
     * Skips the write if data hasn't changed since the last successful save.
     * Uses atomic write (temp file + rename) to prevent corruption on crash.
     */
    fun save(sessions: List<SessionMetadata>) {
        synchronized(sessionsLock) {
            try {
                val serialized = SessionMetadataSerializer.serialize(sessions)
                val currentHash = serialized.hashCode()
                if (!sessionsDirty && currentHash == lastSavedHash) return
                val tmp = File(sessionsFile.parentFile, "${SESSIONS_FILENAME}.tmp")
                tmp.writeText(serialized)
                if (!tmp.renameTo(sessionsFile)) {
                    // renameTo can fail on some filesystems; fall back to direct write
                    sessionsFile.writeText(serialized)
                }
                lastSavedHash = currentHash
                sessionsDirty = false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save sessions", e)
            }
        }
    }

    /**
     * Mark sessions as dirty so the next [save] will write regardless of hash.
     * Call this when a session is created, removed, or has metadata changed.
     */
    fun markSessionsDirty() {
        sessionsDirty = true
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
     * Uses atomic write to prevent corruption on crash.
     */
    fun saveGroups(groups: List<SessionGroup>) {
        synchronized(groupsLock) {
            try {
                val serialized = SessionGroupSerializer.serialize(groups)
                val tmp = File(groupsFile.parentFile, "${GROUPS_FILENAME}.tmp")
                tmp.writeText(serialized)
                if (!tmp.renameTo(groupsFile)) {
                    groupsFile.writeText(serialized)
                }
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