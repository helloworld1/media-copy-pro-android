package org.liberty.android.mediacopypro.data

import kotlinx.serialization.Serializable

@Serializable
data class CopyProfile(
    val name: String,
    val recursive: Boolean,
    val conflictStrategy: String,
    val extensionPattern: String,
    val fileNamePattern: String,
    val destFormat: String
)
