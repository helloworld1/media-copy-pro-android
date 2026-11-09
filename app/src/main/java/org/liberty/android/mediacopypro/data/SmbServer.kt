package org.liberty.android.mediacopypro.data

import kotlinx.serialization.Serializable

@Serializable
data class SmbServer(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val share: String,
    val domain: String = "",
    val username: String = "",
    val password: String = ""
)
