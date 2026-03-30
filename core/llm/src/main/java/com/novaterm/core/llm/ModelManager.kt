package com.novaterm.core.llm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages model download and storage.
 *
 * The model is NEVER bundled in the APK. The user explicitly
 * triggers download from Settings. Supports resume on interruption.
 */
class ModelManager(private val modelsDir: File) {

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    init {
        modelsDir.mkdirs()
    }

    /** Check if the default model is available on disk. */
    fun isModelAvailable(): Boolean {
        return getModelFile().exists()
    }

    /** Get the path to the default model file. */
    fun getModelPath(): String {
        return getModelFile().absolutePath
    }

    /** Delete the downloaded model to free storage. */
    fun deleteModel(): Boolean {
        val file = getModelFile()
        val deleted = file.delete()
        if (deleted) {
            _downloadState.value = DownloadState.Idle
            Log.i(TAG, "Model deleted: ${file.absolutePath}")
        }
        return deleted
    }

    /** Get model file size on disk (bytes), or 0 if not present. */
    fun getModelSizeBytes(): Long {
        val file = getModelFile()
        return if (file.exists()) file.length() else 0
    }

    /**
     * Download the model from the configured URL.
     *
     * Reports progress via [downloadState]. Writes to a temp file
     * first, then renames to prevent corrupt partial downloads.
     */
    suspend fun downloadModel(url: String = LlmConfig.MODEL_URL): Boolean =
        withContext(Dispatchers.IO) {
            val targetFile = getModelFile()
            val tempFile = File(modelsDir, "${LlmConfig.DEFAULT_MODEL_NAME}.downloading")

            _downloadState.value = DownloadState.Downloading(0, 0)

            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000

                // Resume if partial download exists
                val existingBytes = if (tempFile.exists()) tempFile.length() else 0
                if (existingBytes > 0) {
                    connection.setRequestProperty("Range", "bytes=$existingBytes-")
                }

                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK &&
                    responseCode != HttpURLConnection.HTTP_PARTIAL
                ) {
                    _downloadState.value = DownloadState.Error("HTTP $responseCode")
                    return@withContext false
                }

                val totalBytes = connection.contentLengthLong + existingBytes
                var downloadedBytes = existingBytes

                connection.inputStream.use { input ->
                    FileOutputStream(tempFile, existingBytes > 0).use { output ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            _downloadState.value = DownloadState.Downloading(
                                downloadedBytes,
                                totalBytes,
                            )
                        }
                    }
                }

                // Atomic rename: temp → final
                if (tempFile.renameTo(targetFile)) {
                    _downloadState.value = DownloadState.Completed(targetFile.absolutePath)
                    Log.i(TAG, "Model downloaded: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
                    true
                } else {
                    _downloadState.value = DownloadState.Error("Failed to rename temp file")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                _downloadState.value = DownloadState.Error(e.message ?: "Unknown error")
                // Don't delete temp file — allows resume
                false
            }
        }

    /** Cancel an in-progress download. */
    fun cancelDownload() {
        _downloadState.value = DownloadState.Idle
        // The temp file is kept for resume capability
    }

    private fun getModelFile(): File = File(modelsDir, LlmConfig.DEFAULT_MODEL_NAME)

    companion object {
        private const val TAG = "ModelManager"
    }
}

/** Download progress state. */
sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long) : DownloadState {
        val progressPercent: Int
            get() = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt() else 0
    }
    data class Completed(val modelPath: String) : DownloadState
    data class Error(val message: String) : DownloadState
}
