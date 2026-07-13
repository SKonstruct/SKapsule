package com.skarm.launcher

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

class FolderProvider : DocumentsProvider() {

    private lateinit var baseDir: File

    override fun onCreate(): Boolean {
        baseDir = context?.filesDir ?: return false
        return true
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        val row = result.newRow()
        row.add(Root.COLUMN_ROOT_ID, getDocIdForFile(baseDir))
        row.add(Root.COLUMN_DOCUMENT_ID, getDocIdForFile(baseDir))
        row.add(Root.COLUMN_SUMMARY, "SKapsule game files folder")
        row.add(
            Root.COLUMN_FLAGS,
            Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_IS_CHILD,
        )
        row.add(Root.COLUMN_TITLE, "SKapsule")
        row.add(Root.COLUMN_MIME_TYPES, "*/*")
        row.add(Root.COLUMN_AVAILABLE_BYTES, baseDir.freeSpace)
        row.add(Root.COLUMN_ICON, R.mipmap.ic_launcher)
        return result
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        includeFile(result, documentId, null)
        return result
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val parent = getFileForDocId(parentDocumentId)
        val children = parent.listFiles() ?: throw FileNotFoundException("Cannot list directory")
        for (child in children) {
            includeFile(result, null, child)
        }
        return result
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        val file = getFileForDocId(documentId)
        val accessMode = ParcelFileDescriptor.parseMode(mode)
        return ParcelFileDescriptor.open(file, accessMode)
    }

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String,
    ): String {
        val parent = getFileForDocId(parentDocumentId)
        val newFile = File(parent, displayName)
        if (Document.MIME_TYPE_DIR == mimeType) {
            if (!newFile.mkdir()) throw FileNotFoundException("Failed to create directory")
        } else {
            if (!newFile.createNewFile()) throw FileNotFoundException("Failed to create file")
        }
        return getDocIdForFile(newFile)
    }

    override fun deleteDocument(documentId: String) {
        val file = getFileForDocId(documentId)
        if (file.isDirectory) {
            file.deleteRecursively()
        } else {
            if (!file.delete()) throw FileNotFoundException("Failed to delete file")
        }
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        val file = getFileForDocId(documentId)
        val parent = file.parentFile ?: throw FileNotFoundException("Cannot rename root")
        val dest = File(parent, displayName)
        if (!file.renameTo(dest)) throw FileNotFoundException("Failed to rename file")
        return getDocIdForFile(dest)
    }

    override fun getDocumentType(documentId: String): String {
        val file = getFileForDocId(documentId)
        return getMimeType(file)
    }

    private fun getDocIdForFile(file: File): String {
        return file.absolutePath
    }

    private fun getFileForDocId(docId: String): File {
        val file = File(docId)
        if (!file.exists()) throw FileNotFoundException("File not found: $docId")
        return file
    }

    private fun getMimeType(file: File): String {
        if (file.isDirectory) return Document.MIME_TYPE_DIR
        val ext = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    private fun includeFile(result: MatrixCursor, docId: String?, file: File?) {
        val resolvedDocId = docId ?: getDocIdForFile(file!!)
        val resolvedFile = file ?: getFileForDocId(resolvedDocId)

        var flags = 0
        if (resolvedFile.isDirectory) {
            if (resolvedFile.canWrite()) flags = flags or Document.FLAG_DIR_SUPPORTS_CREATE
        } else if (resolvedFile.canWrite()) {
            flags = flags or Document.FLAG_SUPPORTS_WRITE
        }
        if (resolvedFile.parentFile?.canWrite() == true) {
            flags = flags or Document.FLAG_SUPPORTS_DELETE
        }

        val row = result.newRow()
        row.add(Document.COLUMN_DOCUMENT_ID, resolvedDocId)
        row.add(Document.COLUMN_DISPLAY_NAME, resolvedFile.name)
        row.add(Document.COLUMN_SIZE, resolvedFile.length())
        row.add(Document.COLUMN_MIME_TYPE, getMimeType(resolvedFile))
        row.add(Document.COLUMN_LAST_MODIFIED, resolvedFile.lastModified())
        row.add(Document.COLUMN_FLAGS, flags)
        row.add(Document.COLUMN_ICON, R.mipmap.ic_launcher)
    }

    companion object {
        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_MIME_TYPES,
            Root.COLUMN_FLAGS,
            Root.COLUMN_ICON,
            Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_AVAILABLE_BYTES,
        )

        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE,
        )
    }
}
