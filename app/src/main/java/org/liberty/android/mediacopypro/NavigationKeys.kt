package org.liberty.android.mediacopypro

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey
@Serializable data object CopyProgress : NavKey

@Serializable data class ServerList(val isSource: Boolean) : NavKey
@Serializable data class ServerEdit(val serverId: String?, val isSource: Boolean) : NavKey
@Serializable data class SmbDirectory(val serverId: String, val isSource: Boolean) : NavKey
@Serializable data class CopiedFilesList(val timestamp: Long) : NavKey
@Serializable data object HistoryList : NavKey
