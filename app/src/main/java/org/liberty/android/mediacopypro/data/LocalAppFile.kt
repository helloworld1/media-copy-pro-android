package org.liberty.android.mediacopypro.data

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import java.io.InputStream
import java.io.OutputStream

class LocalAppFile(val documentFile: DocumentFile) : AppFile {
    override val name: String get() = documentFile.name ?: ""
    override val isDirectory: Boolean get() = documentFile.isDirectory
    override val isFile: Boolean get() = documentFile.isFile
    override val uriString: String get() = documentFile.uri.toString()

    override fun exists(): Boolean = documentFile.exists()
    override fun length(): Long = documentFile.length()
    override fun lastModified(): Long = documentFile.lastModified()

    override fun listFiles(): List<AppFile> {
        return documentFile.listFiles().map { LocalAppFile(it) }
    }

    override fun findFile(name: String): AppFile? {
        val found = documentFile.findFile(name)
        return found?.let { LocalAppFile(it) }
    }

    override fun createDirectory(name: String): AppFile? {
        val newDir = documentFile.createDirectory(name)
        return newDir?.let { LocalAppFile(it) }
    }

    override fun createFile(mimeType: String, name: String): AppFile? {
        val newFile = documentFile.createFile(mimeType, name)
        return newFile?.let { LocalAppFile(it) }
    }

    override fun openInputStream(context: Context): InputStream? {
        return context.contentResolver.openInputStream(documentFile.uri)
    }

    override fun openOutputStream(context: Context, mode: String): OutputStream? {
        val actualMode = if (mode == "w") "wt" else mode // SAF uses "wt" for write-truncate
        return context.contentResolver.openOutputStream(documentFile.uri, actualMode)
    }
}
