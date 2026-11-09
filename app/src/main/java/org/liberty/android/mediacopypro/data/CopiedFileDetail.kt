package org.liberty.android.mediacopypro.data

import kotlinx.serialization.Serializable

@Serializable
enum class CopyStatus {
    COPIED, SKIPPED, FAILED, RENAMED
}

@Serializable
data class CopiedFileDetail(
    val srcName: String,
    val destName: String,
    val sizeBytes: Long,
    val status: CopyStatus,
    val errorMessage: String? = null
)
