package com.novaterm.app.provider

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException

/**
 * Exposes NovaTerm's home directory to Android's Storage Access Framework.
 *
 * This allows external file managers (Material Files, Solid Explorer, etc.)
 * to browse, open, create, and delete files in the terminal's home directory.
 * The provider appears as "NovaTerm" in the system file picker.
 *
 * Security: Only the home directory is exposed. Symlinks pointing outside
 * the home directory are resolved and blocked to prevent traversal attacks.
 */
class NovaTermDocumentsProvider : DocumentsProvider() {

    private val homeDir: File
        get() {
            val ctx = context ?: throw IllegalStateException("Context is null")
            val root = ctx.filesDir.parentFile?.absolutePath ?: ctx.filesDir.absolutePath
            return File("$root/home")
        }

    override fun onCreate(): Boolean = true

    // ── Roots ──────────────────────────────────────────────

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        val home = homeDir
        if (!home.isDirectory) return result

        result.newRow().apply {
            add(Root.COLUMN_ROOT_ID, ROOT_ID)
            add(Root.COLUMN_DOCUMENT_ID, docIdForFile(home))
            add(Root.COLUMN_TITLE, "NovaTerm")
            add(Root.COLUMN_SUMMARY, "Terminal files")
            add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_IS_CHILD)
            add(Root.COLUMN_ICON, android.R.drawable.ic_menu_manage)
            add(Root.COLUMN_MIME_TYPES, "*/*")
        }
        return result
    }

    // ── Documents ──────────────────────────────────────────

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val file = fileForDocId(documentId)
        addFileRow(result, file)
        return result
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val parent = fileForDocId(parentDocumentId)
        if (!parent.isDirectory) return result

        parent.listFiles()
            ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            ?.forEach { file ->
                addFileRow(result, file)
            }
        return result
    }

    // ── File operations ────────────────────────────────────

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        val file = fileForDocId(documentId)
        val accessMode = ParcelFileDescriptor.parseMode(mode)
        return ParcelFileDescriptor.open(file, accessMode)
    }

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String,
    ): String {
        val parent = fileForDocId(parentDocumentId)
        val newFile = File(parent, displayName)

        if (mimeType == Document.MIME_TYPE_DIR) {
            newFile.mkdirs()
        } else {
            newFile.createNewFile()
        }

        return docIdForFile(newFile)
    }

    override fun deleteDocument(documentId: String) {
        val file = fileForDocId(documentId)
        if (file.isDirectory) {
            file.deleteRecursively()
        } else {
            file.delete()
        }
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        val parent = fileForDocId(parentDocumentId)
        val child = fileForDocId(documentId)
        return child.canonicalPath.startsWith(parent.canonicalPath)
    }

    // ── Helpers ─────────────────────────────────────────────

    private fun docIdForFile(file: File): String {
        val homePath = homeDir.absolutePath
        val filePath = file.absolutePath
        return if (filePath == homePath) {
            ROOT_DOC_ID
        } else {
            filePath.removePrefix("$homePath/")
        }
    }

    private fun fileForDocId(docId: String): File {
        val file = if (docId == ROOT_DOC_ID) {
            homeDir
        } else {
            File(homeDir, docId)
        }

        // Security: block symlinks pointing outside home
        val canonical = file.canonicalFile
        val homeCanonical = homeDir.canonicalFile
        if (!canonical.path.startsWith(homeCanonical.path)) {
            throw FileNotFoundException("Access denied: $docId resolves outside home directory")
        }

        if (!file.exists()) throw FileNotFoundException("File not found: $docId")
        return file
    }

    private fun addFileRow(cursor: MatrixCursor, file: File) {
        val mimeType = if (file.isDirectory) {
            Document.MIME_TYPE_DIR
        } else {
            val ext = file.extension.lowercase()
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }

        var flags = 0
        if (file.isDirectory) {
            flags = flags or Document.FLAG_DIR_SUPPORTS_CREATE
        }
        if (file.canWrite()) {
            flags = flags or Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_DELETE
        }

        cursor.newRow().apply {
            add(Document.COLUMN_DOCUMENT_ID, docIdForFile(file))
            add(Document.COLUMN_DISPLAY_NAME, file.name)
            add(Document.COLUMN_MIME_TYPE, mimeType)
            add(Document.COLUMN_SIZE, file.length())
            add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
            add(Document.COLUMN_FLAGS, flags)
        }
    }

    companion object {
        private const val ROOT_ID = "novaterm_home"
        private const val ROOT_DOC_ID = "home"

        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY,
            Root.COLUMN_FLAGS,
            Root.COLUMN_ICON,
            Root.COLUMN_MIME_TYPES,
        )

        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
        )
    }
}
