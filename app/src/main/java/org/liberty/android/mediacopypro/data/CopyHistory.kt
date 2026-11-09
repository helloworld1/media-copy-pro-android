package org.liberty.android.mediacopypro.data

import kotlinx.serialization.Serializable

@Serializable
data class CopyHistory(
    val timestamp: Long,
    val srcBaseDir: String = "",
    val destBaseDir: String = "",
    val details: List<CopiedFileDetail>
)
