package org.liberty.android.mediacopypro.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object HistoryManager {
    private fun getHistoryDir(context: Context): File {
        val dir = File(context.filesDir, "history")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    suspend fun saveHistory(context: Context, history: CopyHistory) = withContext(Dispatchers.IO) {
        val dir = getHistoryDir(context)
        val file = File(dir, "${history.timestamp}.json")
        val jsonString = Json.encodeToString(history)
        file.writeText(jsonString)
    }

    suspend fun getHistories(context: Context): List<CopyHistory> = withContext(Dispatchers.IO) {
        val dir = getHistoryDir(context)
        val files = dir.listFiles { _, name -> name.endsWith(".json") } ?: emptyArray()
        
        files.mapNotNull { file ->
            try {
                Json.decodeFromString<CopyHistory>(file.readText())
            } catch (e: Exception) {
                null
            }
        }.sortedByDescending { it.timestamp }
    }

    suspend fun getHistory(context: Context, timestamp: Long): CopyHistory? = withContext(Dispatchers.IO) {
        val dir = getHistoryDir(context)
        val file = File(dir, "$timestamp.json")
        if (file.exists()) {
            try {
                Json.decodeFromString<CopyHistory>(file.readText())
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    suspend fun clearAll(context: Context) = withContext(Dispatchers.IO) {
        val dir = getHistoryDir(context)
        dir.listFiles()?.forEach { it.delete() }
    }
}
