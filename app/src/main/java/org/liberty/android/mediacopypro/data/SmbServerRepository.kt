package org.liberty.android.mediacopypro.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SmbServerRepository(context: Context) {
    private val prefs = context.getSharedPreferences("smb_servers_prefs", Context.MODE_PRIVATE)
    private val KEY_SERVERS = "servers"

    private val _servers = MutableStateFlow<List<SmbServer>>(emptyList())
    val servers: StateFlow<List<SmbServer>> = _servers.asStateFlow()

    init {
        loadServers()
    }

    private fun loadServers() {
        val jsonString = prefs.getString(KEY_SERVERS, null)
        if (jsonString != null) {
            try {
                val list = Json.decodeFromString<List<SmbServer>>(jsonString)
                _servers.value = list
            } catch (e: Exception) {
                _servers.value = emptyList()
            }
        } else {
            _servers.value = emptyList()
        }
    }

    fun addServer(server: SmbServer) {
        val currentList = _servers.value.toMutableList()
        currentList.add(server)
        saveServers(currentList)
    }

    fun updateServer(server: SmbServer) {
        val currentList = _servers.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == server.id }
        if (index != -1) {
            currentList[index] = server
            saveServers(currentList)
        }
    }

    fun removeServer(serverId: String) {
        val currentList = _servers.value.toMutableList()
        currentList.removeAll { it.id == serverId }
        saveServers(currentList)
    }

    fun getServer(serverId: String): SmbServer? {
        return _servers.value.find { it.id == serverId }
    }

    private fun saveServers(list: List<SmbServer>) {
        try {
            val jsonString = Json.encodeToString(list)
            prefs.edit().putString(KEY_SERVERS, jsonString).apply()
            _servers.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
