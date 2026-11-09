package org.liberty.android.mediacopypro.data

import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import java.util.concurrent.ConcurrentHashMap

object SmbManager {
    private val client = SMBClient()
    private val connections = ConcurrentHashMap<String, Connection>()
    private val sessions = ConcurrentHashMap<String, Session>()
    private val shares = ConcurrentHashMap<String, DiskShare>()

    @Synchronized
    fun getShare(server: SmbServer): DiskShare {
        val shareKey = "${server.id}_${server.share}"
        shares[shareKey]?.let {
            if (it.isConnected) return it
        }

        // Need to connect or reconnect
        var connection = connections[server.id]
        if (connection == null || !connection.isConnected) {
            connection = client.connect(server.host)
            connections[server.id] = connection
        }

        var session = sessions[server.id]
        if (session == null || !session.connection.isConnected) {
            val authContext = if (server.username.isNotEmpty()) {
                AuthenticationContext(server.username, server.password.toCharArray(), server.domain)
            } else {
                AuthenticationContext.anonymous()
            }
            session = connection.authenticate(authContext)
            sessions[server.id] = session
        }

        val share = session.connectShare(server.share) as DiskShare
        shares[shareKey] = share
        return share
    }

    @Synchronized
    fun closeAll() {
        shares.values.forEach { try { it.close() } catch (e: Exception) {} }
        shares.clear()
        
        sessions.values.forEach { try { it.close() } catch (e: Exception) {} }
        sessions.clear()
        
        connections.values.forEach { try { it.close() } catch (e: Exception) {} }
        connections.clear()
    }
}
