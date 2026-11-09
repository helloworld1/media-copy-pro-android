package org.liberty.android.mediacopypro.data

import android.content.Context
import java.io.InputStream
import java.io.OutputStream

interface AppFile {
    val name: String
    val isDirectory: Boolean
    val isFile: Boolean
    val uriString: String // Unique identifier

    fun exists(): Boolean
    fun length(): Long
    fun lastModified(): Long
    fun listFiles(): List<AppFile>
    fun findFile(name: String): AppFile?
    fun createDirectory(name: String): AppFile?
    fun createFile(mimeType: String, name: String): AppFile?
    fun openInputStream(context: Context): InputStream?
    fun openOutputStream(context: Context, mode: String = "w"): OutputStream?
}
