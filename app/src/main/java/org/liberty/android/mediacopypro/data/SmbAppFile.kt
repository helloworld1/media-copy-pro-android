package org.liberty.android.mediacopypro.data

import android.content.Context
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.share.DiskShare
import java.io.InputStream
import java.io.OutputStream
import java.util.EnumSet

class SmbAppFile(
    private val server: SmbServer,
    private val share: DiskShare,
    private val path: String, // path is using '\\' as separator
    private val isDir: Boolean = false,
    private val fileSize: Long = 0,
    private val fileLastModified: Long = 0
) : AppFile {
    override val name: String get() = path.substringAfterLast('\\').ifEmpty { path }
    override val isDirectory: Boolean get() = isDir
    override val isFile: Boolean get() = !isDir
    override val uriString: String get() = "smb://${server.id}/${path.replace('\\', '/')}"

    override fun exists(): Boolean {
        return share.fileExists(path) || share.folderExists(path)
    }

    override fun length(): Long {
        if (!exists()) return 0
        if (fileSize > 0) return fileSize
        return try {
            val fileInfo = share.getFileInformation(path)
            fileInfo.standardInformation.endOfFile
        } catch (e: Exception) {
            0
        }
    }

    override fun lastModified(): Long {
        if (!exists()) return 0
        if (fileLastModified > 0) return fileLastModified
        return try {
            val fileInfo = share.getFileInformation(path)
            fileInfo.basicInformation.lastWriteTime.toEpochMillis()
        } catch (e: Exception) {
            0
        }
    }

    override fun listFiles(): List<AppFile> {
        if (!isDirectory) return emptyList()
        val list = mutableListOf<AppFile>()
        try {
            val files = share.list(path)
            for (file in files) {
                val fileName = file.fileName
                if (fileName == "." || fileName == "..") continue

                val newPath = if (path.isEmpty()) fileName else "$path\\$fileName"
                val isD = (file.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) == FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value
                list.add(SmbAppFile(server, share, newPath, isD, file.endOfFile, file.lastWriteTime.toEpochMillis()))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    override fun findFile(name: String): AppFile? {
        val childPath = if (path.isEmpty()) name else "$path\\$name"
        if (share.fileExists(childPath)) {
            return SmbAppFile(server, share, childPath, false)
        }
        if (share.folderExists(childPath)) {
            return SmbAppFile(server, share, childPath, true)
        }
        return null
    }

    override fun createDirectory(name: String): AppFile? {
        val childPath = if (path.isEmpty()) name else "$path\\$name"
        return try {
            if (!share.folderExists(childPath)) {
                share.mkdir(childPath)
            }
            SmbAppFile(server, share, childPath, true)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun createFile(mimeType: String, name: String): AppFile? {
        val childPath = if (path.isEmpty()) name else "$path\\$name"
        return try {
            if (!share.fileExists(childPath)) {
                val f = share.openFile(
                    childPath,
                    EnumSet.of(AccessMask.GENERIC_WRITE),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_CREATE,
                    null
                )
                f.close()
            }
            SmbAppFile(server, share, childPath, false)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun openInputStream(context: Context): InputStream? {
        return try {
            val f = share.openFile(
                path,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null
            )
            f.inputStream
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun openOutputStream(context: Context, mode: String): OutputStream? {
        return try {
            val f = share.openFile(
                path,
                EnumSet.of(AccessMask.GENERIC_WRITE),
                null,
                SMB2ShareAccess.ALL,
                if (mode.contains("w")) SMB2CreateDisposition.FILE_OVERWRITE_IF else SMB2CreateDisposition.FILE_OPEN,
                null
            )
            f.outputStream
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
