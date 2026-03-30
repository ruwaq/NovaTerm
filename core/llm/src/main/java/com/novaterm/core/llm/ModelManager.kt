package com.novaterm.core.llm

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages model download, storage, and lifecycle.
 *
 * Uses Android's [DownloadManager] (system service) for downloads because:
 * - Survives app kill (runs in system process — Xiaomi/Samsung can't kill it)
 * - Automatic resume on network reconnect
 * - System notification with progress
 * - HTTP Range headers for resumable downloads
 *
 * The model is stored in app-private external storage (no permissions needed,
 * auto-deleted on uninstall).
 */
class ModelManager(context: Context) {

    private val context: Context = context.applicationContext
    private val modelsDir = File(this.context.getExternalFilesDir(null), "models").also { it.mkdirs() }
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val prefs = context.getSharedPreferences("llm_models", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<ModelState>(ModelState.Idle)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    /** Currently active download ID, or -1 if none. */
    private var activeDownloadId: Long
        get() = prefs.getLong("active_download_id", -1)
        set(value) = prefs.edit().putLong("active_download_id", value).apply()

    /** ID of the currently selected/downloaded model. */
    var selectedModelId: String
        get() = prefs.getString("selected_model_id", ModelCatalog.DEFAULT.id) ?: ModelCatalog.DEFAULT.id
        private set(value) = prefs.edit().putString("selected_model_id", value).apply()

    init {
        refreshState()
    }

    /** Check current state based on what's on disk and any active downloads. */
    fun refreshState() {
        val model = ModelCatalog.findById(selectedModelId) ?: ModelCatalog.DEFAULT
        val file = File(modelsDir, model.filename)

        when {
            file.exists() && file.length() > 0 -> {
                _state.value = ModelState.Ready(
                    modelId = model.id,
                    displayName = model.displayName,
                    sizeMb = (file.length() / (1024 * 1024)).toInt(),
                    path = file.absolutePath,
                )
            }
            activeDownloadId != -1L -> {
                // There's an active download — start polling
                _state.value = ModelState.Downloading(
                    modelId = model.id,
                    displayName = model.displayName,
                    bytesDownloaded = 0,
                    totalBytes = model.sizeMb.toLong() * 1024 * 1024,
                )
            }
            else -> {
                _state.value = ModelState.NotDownloaded(
                    modelId = model.id,
                    displayName = model.displayName,
                    sizeMb = model.sizeMb,
                    ramMb = model.ramMb,
                    description = model.description,
                )
            }
        }
    }

    /**
     * Start downloading a model.
     *
     * Uses DownloadManager (system service) — survives app kill.
     * The user sees a system notification with progress.
     */
    fun startDownload(modelId: String = ModelCatalog.DEFAULT.id) {
        val model = ModelCatalog.findById(modelId) ?: return

        // Cancel any existing download
        if (activeDownloadId != -1L) {
            downloadManager.remove(activeDownloadId)
        }

        selectedModelId = model.id
        val destFile = File(modelsDir, model.filename)

        val request = DownloadManager.Request(Uri.parse(model.url))
            .setTitle("NovaTerm AI: ${model.displayName}")
            .setDescription("${model.sizeMb} MB — on-device command suggestions")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationUri(Uri.fromFile(destFile))
            .setAllowedOverMetered(false) // Wi-Fi only by default

        activeDownloadId = downloadManager.enqueue(request)

        _state.value = ModelState.Downloading(
            modelId = model.id,
            displayName = model.displayName,
            bytesDownloaded = 0,
            totalBytes = model.sizeMb.toLong() * 1024 * 1024,
        )

        Log.i(TAG, "Download started: ${model.displayName} (ID=$activeDownloadId)")
    }

    /**
     * Poll download progress. Call from a coroutine while UI is visible.
     * Returns when download completes, fails, or is cancelled.
     */
    /** Maximum poll time: 24 hours. Prevents infinite loop if download stays paused. */
    private val maxPollIterations = (24 * 60 * 60 * 1000L) / 500 // 24h at 500ms intervals

    suspend fun pollProgress() = withContext(Dispatchers.IO) {
        val downloadId = activeDownloadId
        if (downloadId == -1L) return@withContext

        val query = DownloadManager.Query().setFilterById(downloadId)
        var iterations = 0L

        while (iterations++ < maxPollIterations) {
            val cursor = downloadManager.query(query)
            if (cursor == null || !cursor.moveToFirst()) {
                cursor?.close()
                activeDownloadId = -1
                refreshState()
                return@withContext
            }

            cursor.use {
                val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val bytesDownloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val totalBytes = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

                val model = ModelCatalog.findById(selectedModelId) ?: ModelCatalog.DEFAULT

                when (status) {
                    DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING -> {
                        _state.value = ModelState.Downloading(
                            modelId = model.id,
                            displayName = model.displayName,
                            bytesDownloaded = bytesDownloaded,
                            totalBytes = if (totalBytes > 0) totalBytes else model.sizeMb.toLong() * 1024 * 1024,
                        )
                    }
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        activeDownloadId = -1
                        refreshState()
                        Log.i(TAG, "Download complete: ${model.displayName}")
                        return@withContext
                    }
                    DownloadManager.STATUS_FAILED -> {
                        val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        activeDownloadId = -1
                        _state.value = ModelState.Error("Download failed (reason=$reason)")
                        Log.e(TAG, "Download failed: reason=$reason")
                        return@withContext
                    }
                    DownloadManager.STATUS_PAUSED -> {
                        _state.value = ModelState.Downloading(
                            modelId = model.id,
                            displayName = model.displayName,
                            bytesDownloaded = bytesDownloaded,
                            totalBytes = if (totalBytes > 0) totalBytes else model.sizeMb.toLong() * 1024 * 1024,
                        )
                    }
                }
            }

            delay(500) // Poll every 500ms
        }
    }

    /** Cancel an in-progress download. */
    fun cancelDownload() {
        val id = activeDownloadId
        if (id != -1L) {
            downloadManager.remove(id)
            activeDownloadId = -1
            Log.i(TAG, "Download cancelled")
        }
        refreshState()
    }

    /** Delete the downloaded model to free storage. */
    fun deleteModel() {
        cancelDownload()
        val model = ModelCatalog.findById(selectedModelId) ?: ModelCatalog.DEFAULT
        File(modelsDir, model.filename).delete()
        refreshState()
        Log.i(TAG, "Model deleted: ${model.displayName}")
    }

    /** Get the path to the currently selected model file, or null if not downloaded. */
    fun getModelPath(): String? {
        val model = ModelCatalog.findById(selectedModelId) ?: ModelCatalog.DEFAULT
        val file = File(modelsDir, model.filename)
        return if (file.exists() && file.length() > 0) file.absolutePath else null
    }

    /** Check if a model is ready to use. */
    fun isModelReady(): Boolean = getModelPath() != null

    companion object {
        private const val TAG = "ModelManager"
    }
}

/** Model download/readiness state for the UI. */
sealed interface ModelState {
    data object Idle : ModelState

    data class NotDownloaded(
        val modelId: String,
        val displayName: String,
        val sizeMb: Int,
        val ramMb: Int,
        val description: String,
    ) : ModelState

    data class Downloading(
        val modelId: String,
        val displayName: String,
        val bytesDownloaded: Long,
        val totalBytes: Long,
    ) : ModelState {
        val progressPercent: Int
            get() = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt() else 0
        val downloadedMb: Int
            get() = (bytesDownloaded / (1024 * 1024)).toInt()
        val totalMb: Int
            get() = (totalBytes / (1024 * 1024)).toInt()
    }

    data class Ready(
        val modelId: String,
        val displayName: String,
        val sizeMb: Int,
        val path: String,
    ) : ModelState

    data class Error(val message: String) : ModelState
}
