package org.liberty.android.mediacopypro

import android.app.Application
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import org.liberty.android.mediacopypro.data.*
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

enum class ConflictStrategy {
    SKIP, OVERWRITE, RENAME, SMART
}

data class CopyState(
    val srcUri: String? = null,
    val destUri: String? = null,
    val recursive: Boolean = true,
    val conflictStrategy: ConflictStrategy = ConflictStrategy.SMART,
    val extensionPattern: String = "arw,cr*,dng,mov,mp4,jpg,heic",
    val fileNamePattern: String = "*",
    val destFormat: String = "{file_name}",
    
    val statusText: String = "",
    val filesCopied: Int = 0,
    val totalFiles: Int = 0,
    val bytesCopied: Long = 0L,
    val totalBytesToCopy: Long = 0L,
    val copySpeed: String = "", // e.g. "15.2 MB/s"
    val skippedFilesCount: Int = 0,
    val renamedFilesCount: Int = 0,
    val failedFilesCount: Int = 0,
    val warnings: List<String> = emptyList(),
    val currentJobTimestamp: Long = 0L,
    
    val elapsedTime: String = "",
    val estimatedTime: String = "",
    val averageSpeed: String = "",
    
    val isRunning: Boolean = false,
    val isCancelled: Boolean = false,
    val isDone: Boolean = false,
    
    val profiles: List<CopyProfile> = emptyList(),
    val currentProfileName: String = "Default"
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("media_copy_prefs", Context.MODE_PRIVATE)

    private val DEFAULT_PROFILE = CopyProfile(
        name = "Default",
        recursive = true,
        conflictStrategy = ConflictStrategy.SMART.name,
        extensionPattern = "arw,cr*,dng,mov,mp4,jpg,heic",
        fileNamePattern = "*",
        destFormat = "{file_name}"
    )

    private val profilesList = mutableListOf<CopyProfile>()

    private val _uiState = MutableStateFlow(
        CopyState(
            srcUri = prefs.getString("srcUri", null),
            destUri = prefs.getString("destUri", null)
        )
    )
    val uiState: StateFlow<CopyState> = _uiState.asStateFlow()

    init {
        val jsonStr = prefs.getString("profiles_json", null)
        if (jsonStr != null) {
            try {
                profilesList.addAll(Json.decodeFromString<List<CopyProfile>>(jsonStr))
            } catch (e: Exception) {
                profilesList.add(DEFAULT_PROFILE)
            }
        } else {
            profilesList.add(DEFAULT_PROFILE)
        }

        val lastProfileName = prefs.getString("currentProfileName", "Default") ?: "Default"
        val activeProfile = profilesList.find { it.name == lastProfileName } ?: profilesList.first()

        _uiState.value = _uiState.value.copy(
            profiles = profilesList.toList(),
            currentProfileName = activeProfile.name,
            recursive = activeProfile.recursive,
            conflictStrategy = try { ConflictStrategy.valueOf(activeProfile.conflictStrategy) } catch (e:Exception) { ConflictStrategy.SMART },
            extensionPattern = activeProfile.extensionPattern,
            fileNamePattern = activeProfile.fileNamePattern,
            destFormat = activeProfile.destFormat
        )
    }

    fun updateSrcUri(uri: String?) {
        prefs.edit().putString("srcUri", uri).apply()
        _uiState.value = _uiState.value.copy(srcUri = uri)
    }
    fun updateDestUri(uri: String?) {
        prefs.edit().putString("destUri", uri).apply()
        _uiState.value = _uiState.value.copy(destUri = uri)
    }
    fun updateRecursive(recursive: Boolean) {
        _uiState.value = _uiState.value.copy(recursive = recursive)
    }
    fun updateConflictStrategy(strategy: ConflictStrategy) {
        _uiState.value = _uiState.value.copy(conflictStrategy = strategy)
    }
    fun updateExtensionPattern(pattern: String) {
        _uiState.value = _uiState.value.copy(extensionPattern = pattern)
    }
    fun updateFileNamePattern(pattern: String) {
        _uiState.value = _uiState.value.copy(fileNamePattern = pattern)
    }
    fun updateDestFormat(destFormat: String) {
        _uiState.value = _uiState.value.copy(destFormat = destFormat)
    }
    
    private fun persistProfiles() {
        prefs.edit()
            .putString("profiles_json", Json.encodeToString(profilesList.toList()))
            .putString("currentProfileName", _uiState.value.currentProfileName)
            .apply()
        _uiState.value = _uiState.value.copy(profiles = profilesList.toList())
    }

    fun saveProfile() {
        val state = _uiState.value
        val updatedProfile = CopyProfile(
            name = state.currentProfileName,
            recursive = state.recursive,
            conflictStrategy = state.conflictStrategy.name,
            extensionPattern = state.extensionPattern,
            fileNamePattern = state.fileNamePattern,
            destFormat = state.destFormat
        )
        val index = profilesList.indexOfFirst { it.name == state.currentProfileName }
        if (index != -1) {
            profilesList[index] = updatedProfile
        } else {
            profilesList.add(updatedProfile)
        }
        persistProfiles()
    }

    fun saveAsProfile(newName: String) {
        if (newName.isBlank()) return
        val state = _uiState.value
        val newProfile = CopyProfile(
            name = newName.trim(),
            recursive = state.recursive,
            conflictStrategy = state.conflictStrategy.name,
            extensionPattern = state.extensionPattern,
            fileNamePattern = state.fileNamePattern,
            destFormat = state.destFormat
        )
        val index = profilesList.indexOfFirst { it.name.equals(newName.trim(), ignoreCase = true) }
        if (index != -1) {
            profilesList[index] = newProfile
        } else {
            profilesList.add(newProfile)
        }
        _uiState.value = _uiState.value.copy(currentProfileName = newProfile.name)
        persistProfiles()
    }

    fun loadProfile(name: String) {
        val profile = profilesList.find { it.name == name } ?: return
        _uiState.value = _uiState.value.copy(
            currentProfileName = profile.name,
            recursive = profile.recursive,
            conflictStrategy = try { ConflictStrategy.valueOf(profile.conflictStrategy) } catch (e:Exception) { ConflictStrategy.SMART },
            extensionPattern = profile.extensionPattern,
            fileNamePattern = profile.fileNamePattern,
            destFormat = profile.destFormat
        )
        prefs.edit().putString("currentProfileName", profile.name).apply()
    }

    fun deleteProfile(name: String) {
        if (name == "Default") return // Cannot delete Default
        val removed = profilesList.removeAll { it.name == name }
        if (removed) {
            if (_uiState.value.currentProfileName == name) {
                loadProfile("Default")
            }
            persistProfiles()
        }
    }

    fun clearWarnings() {
        _uiState.value = _uiState.value.copy(warnings = emptyList())
    }

    fun cancelCopy() {
        if (_uiState.value.isRunning && !_uiState.value.isCancelled) {
            _uiState.value = _uiState.value.copy(
                isCancelled = true,
                statusText = "Cancelling after current file finishes..."
            )
        }
    }

    fun resetProgress() {
        _uiState.value = _uiState.value.copy(
            statusText = "",
            filesCopied = 0,
            totalFiles = 0,
            bytesCopied = 0L,
            totalBytesToCopy = 0L,
            copySpeed = "",
            skippedFilesCount = 0,
            renamedFilesCount = 0,
            failedFilesCount = 0,
            warnings = emptyList(),
            elapsedTime = "",
            estimatedTime = "",
            averageSpeed = "",
            isRunning = false,
            isCancelled = false,
            isDone = false
        )
    }

    fun startCopy(context: Context) {
        val state = uiState.value
        if (state.srcUri == null || state.destUri == null || state.isRunning) return

        resetProgress()
        
        _uiState.value = _uiState.value.copy(
            isRunning = true,
            statusText = "Scanning...",
        )

        viewModelScope.launch {
            try {
                performCopy(context)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(statusText = "Error: ${e.message}", isRunning = false, isDone = true)
                Log.e("MainViewModel", "Copy failed", e)
            }
        }
    }

    private fun formatTime(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format(Locale.US, "%02d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
    }


    private fun getAppFile(uriStr: String, context: Context): AppFile {
        if (uriStr.startsWith("smb://")) {
            val withoutScheme = uriStr.removePrefix("smb://")
            val serverId = withoutScheme.substringBefore('/')
            val path = withoutScheme.substringAfter('/', "")
            val repo = SmbServerRepository(context)
            val server = repo.getServer(serverId) ?: throw Exception("SMB Server not found")
            val share = SmbManager.getShare(server)
            return SmbAppFile(server, share, path.replace('/', '\\'), true)
        } else {
            val uri = Uri.parse(uriStr)
            val docFile = DocumentFile.fromTreeUri(context, uri) ?: throw Exception("Invalid local directory")
            return LocalAppFile(docFile)
        }
    }

    private suspend fun performCopy(context: Context) = withContext(Dispatchers.IO) {

        val state = uiState.value
        val srcDir = getAppFile(state.srcUri!!, context)
        if (!srcDir.exists()) {
            throw Exception("Source directory is unavailable or unmounted")
        }

        val destDir = getAppFile(state.destUri!!, context)
        if (!destDir.exists()) {
            throw Exception("Destination directory is unavailable or unmounted")
        }

        // Parse patterns
        val rawExtList = state.extensionPattern.split(",").map { it.trim().lowercase(Locale.US) }
        val extList = rawExtList.map { it.removePrefix(".") }.filter { it.isNotEmpty() && it != "*" }
        val matchAllExts = rawExtList.isEmpty() || rawExtList.contains("*") || rawExtList.all { it.isEmpty() }
        
        val extRegexList = extList.map {
            it.replace(".", "\\.").replace("*", ".*").toRegex(RegexOption.IGNORE_CASE)
        }

        val namePatternStr = if (state.fileNamePattern.isBlank()) "*" else state.fileNamePattern
        val nameRegexList = namePatternStr.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map {
                it.replace(".", "\\.").replace("*", ".*").toRegex(RegexOption.IGNORE_CASE)
            }

        val filesToCopy = mutableListOf<Pair<AppFile, String>>()
        var totalBytesScanned = 0L
        var lastScanUpdateTime = System.currentTimeMillis()

        fun scanDir(dir: AppFile, relativePathPrefix: String) {
            if (_uiState.value.isCancelled || !isActive) return

            val files = dir.listFiles()
            for (file in files) {
                if (_uiState.value.isCancelled || !isActive) return
                if (file.isDirectory && state.recursive) {
                    val name = file.name ?: continue
                    scanDir(file, "$relativePathPrefix$name/")
                } else if (file.isFile) {
                    val name = file.name ?: continue
                    val ext = if (name.contains(".")) name.substringAfterLast(".").lowercase(Locale.US) else ""
                    
                    val nameMatches = nameRegexList.isEmpty() || nameRegexList.any { it.matches(name) }
                    val extMatches = matchAllExts || extRegexList.any { it.matches(ext) }

                    if (nameMatches && extMatches) {
                        filesToCopy.add(file to relativePathPrefix.removeSuffix("/"))
                        totalBytesScanned += file.length()
                        
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastScanUpdateTime >= 250) {
                            _uiState.value = _uiState.value.copy(
                                totalFiles = filesToCopy.size,
                                totalBytesToCopy = totalBytesScanned
                            )
                            lastScanUpdateTime = currentTime
                        }
                    }
                }
            }
        }

        scanDir(srcDir, "")
        
        if (_uiState.value.isCancelled) {
             _uiState.value = _uiState.value.copy(
                 statusText = "Cancelled during scan.",
                 isRunning = false,
                 isDone = true
             )
             return@withContext
        }

        val startTime = System.currentTimeMillis()
        _uiState.value = _uiState.value.copy(
            totalFiles = filesToCopy.size,
            totalBytesToCopy = totalBytesScanned,
            statusText = "Copying...",
            currentJobTimestamp = startTime
        )

        if (filesToCopy.isEmpty()) {
            _uiState.value = _uiState.value.copy(statusText = "Done. No files matched.", isRunning = false, isDone = true)
            return@withContext
        }

        var totalBytesCopied = 0L
        var filesCopiedCount = 0
        var skippedCount = 0
        var renamedCount = 0
        val fileDetails = mutableListOf<CopiedFileDetail>()

        var lastSpeedUpdateTime = startTime
        var bytesCopiedSinceLastUpdate = 0L
        val warningsList = mutableListOf<String>()
        
        var failedCount = 0
        var consecutiveFailures = 0

        for ((srcFile, relativePath) in filesToCopy) {
            if (_uiState.value.isCancelled || !isActive) {
                break
            }
            val loopOriginalName = srcFile.name ?: "Unknown"
            var wasRenamed = false
            var bytesCopiedThisAttempt = 0L
            
            for (attempt in 1..3) {
                bytesCopiedThisAttempt = 0L
                try {
                    val originalName = srcFile.name ?: break
            val mimeType = "application/octet-stream" // AppFile doesn't have type yet, fallback
            val ext = if (originalName.contains(".")) originalName.substringAfterLast(".") else ""

            var fileDate: Date? = null

            // Try to extract date
            try {
                // Heuristics for mimeType since AppFile doesn't provide it
                val extLower = ext.lowercase(Locale.US)
                val isImage = extLower in listOf("jpg", "jpeg", "heic", "heif", "png", "arw", "cr2", "cr3", "dng")
                val isVideo = extLower in listOf("mp4", "mov", "mkv", "avi")

                if (isImage) {
                    srcFile.openInputStream(context)?.use { stream ->
                        val exif = ExifInterface(stream)
                        val datetime = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                            ?: exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED)
                            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)

                        if (datetime != null) {
                            // EXIF date format is usually "yyyy:MM:dd HH:mm:ss"
                            val format = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
                            fileDate = format.parse(datetime)
                        }
                    }
                } else if (isVideo) {
                    fileDate = Mp4DateExtractor.extractTimezoneAwareDate(context, srcFile)
                    if (fileDate == null && srcFile is LocalAppFile) {
                        val retriever = MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(context, srcFile.documentFile.uri)
                            val datetime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                            if (datetime != null) {
                                // Try multiple formats
                                val formats = listOf(
                                    "yyyyMMdd'T'HHmmss.SSS'Z'",
                                    "yyyyMMdd'T'HHmmss'Z'",
                                    "yyyyMMdd'T'HHmmss"
                                )
                                for (fmt in formats) {
                                    try {
                                        val format = SimpleDateFormat(fmt, Locale.US)
                                        // Treat missing timezone / Z as UTC, which will be converted to local time
                                        format.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                        fileDate = format.parse(datetime)
                                        if (fileDate != null) break
                                    } catch (e: Exception) {
                                        // ignore and try next
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("MainViewModel", "Failed to extract video metadata for $originalName", e)
                        } finally {
                            retriever.release()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("MainViewModel", "Failed to extract metadata for $originalName", e)
            }

            if (fileDate == null) {
                warningsList.add("Missing EXIF for: $originalName. Using last modified date.")
                fileDate = Date(srcFile.lastModified())
            }

            val nameWithoutExt = originalName.substringBeforeLast(".")
            
            val actualDestFormat = if (state.destFormat.isNotBlank()) state.destFormat else "{file_name}"
            
            val yyyy = SimpleDateFormat("yyyy", Locale.US).format(fileDate!!)
            val MM = SimpleDateFormat("MM", Locale.US).format(fileDate!!)
            val dd = SimpleDateFormat("dd", Locale.US).format(fileDate!!)
            val HH = SimpleDateFormat("HH", Locale.US).format(fileDate!!)
            val mm = SimpleDateFormat("mm", Locale.US).format(fileDate!!)
            val ss = SimpleDateFormat("ss", Locale.US).format(fileDate!!)

            var finalPath = actualDestFormat
                .replace("{yyyy}", yyyy)
                .replace("{MM}", MM)
                .replace("{dd}", dd)
                .replace("{HH}", HH)
                .replace("{mm}", mm)
                .replace("{ss}", ss)
                .replace("{file_name_only}", nameWithoutExt)
                .replace("{file_name}", originalName)
                .replace("{file_relative_path}", relativePath)
                .replace(".{file_ext}", if (ext.isNotEmpty()) ".$ext" else "")
                .replace("{file_ext}", ext)

            val parts = finalPath.split("/")
            var currentDir = destDir

            // Create directories iteratively
            for (i in 0 until parts.size - 1) {
                val dirName = parts[i]
                if (dirName.isEmpty()) continue
                var nextDir = currentDir.findFile(dirName)
                if (nextDir == null) {
                    nextDir = currentDir.createDirectory(dirName)
                }
                if (nextDir == null) {
                    throw Exception("Failed to create directory $dirName. Is destination unmounted?")
                }
                currentDir = nextDir
            }

            var targetName = parts.last()
            if (targetName.isEmpty()) targetName = originalName
            var skipFile = false

            var existingFile = currentDir.findFile(targetName)
            if (existingFile != null) {
                when (state.conflictStrategy) {
                    ConflictStrategy.SKIP -> {
                        skipFile = true
                    }
                    ConflictStrategy.OVERWRITE -> {
                        // Will overwrite below
                    }
                    ConflictStrategy.SMART -> {
                        if (existingFile.length() == srcFile.length()) {
                            skipFile = true
                        } else {
                            // Will overwrite below
                        }
                    }
                    ConflictStrategy.RENAME -> {
                        var counter = 1
                        val nameNoExt = targetName.substringBeforeLast(".")
                        val targetExt = if (targetName.contains(".")) "." + targetName.substringAfterLast(".") else ""
                        while (existingFile != null) {
                            targetName = "${nameNoExt}_$counter$targetExt"
                            existingFile = currentDir.findFile(targetName)
                            counter++
                        }
                    }
                }
            }
            
            wasRenamed = targetName != originalName && state.conflictStrategy == ConflictStrategy.RENAME

            if (skipFile) {
                skippedCount++
                filesCopiedCount++
                _uiState.value = _uiState.value.copy(
                    filesCopied = filesCopiedCount,
                    skippedFilesCount = skippedCount,
                    bytesCopied = totalBytesCopied + srcFile.length()
                )
                totalBytesCopied += srcFile.length()
                fileDetails.add(CopiedFileDetail(originalName, finalPath, srcFile.length(), CopyStatus.SKIPPED))
                consecutiveFailures = 0
                break
            }

            var destFile = currentDir.findFile(targetName)
            if (destFile == null) {
                destFile = currentDir.createFile(mimeType, targetName)
            }

            if (destFile == null) {
                throw Exception("Failed to create dest file: $targetName. Is destination full or unmounted?")
            }

            // Copy data
            val inputStream = srcFile.openInputStream(context)
                ?: throw Exception("Failed to read source file: $originalName. Is source unmounted?")
                
            val outputStream = destFile.openOutputStream(context, "wt")
                ?: throw Exception("Failed to write to dest file: $targetName. Is destination unmounted?")

            inputStream.use { ins ->
                outputStream.use { outs ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesCopied += bytesRead
                        bytesCopiedSinceLastUpdate += bytesRead
                        bytesCopiedThisAttempt += bytesRead

                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastSpeedUpdateTime >= 500) {
                            val elapsedSec = (currentTime - lastSpeedUpdateTime) / 1000.0
                            val speedMbps = (bytesCopiedSinceLastUpdate / (1024.0 * 1024.0)) / elapsedSec
                            val speedStr = String.format(Locale.US, "%.1f MB/s", speedMbps)

                            val totalElapsedSec = (currentTime - startTime) / 1000.0
                            val overallSpeedMbps = if (totalElapsedSec > 0) (totalBytesCopied / (1024.0 * 1024.0)) / totalElapsedSec else 0.0
                            val elapsedStr = formatTime(totalElapsedSec.toLong())
                            
                            val bytesRemaining = totalBytesScanned - totalBytesCopied
                            val estimatedSec = if (overallSpeedMbps > 0) (bytesRemaining / (1024.0 * 1024.0)) / overallSpeedMbps else 0.0
                            val estimatedStr = formatTime(estimatedSec.toLong())

                            _uiState.value = _uiState.value.copy(
                                bytesCopied = totalBytesCopied,
                                copySpeed = speedStr,
                                averageSpeed = String.format(Locale.US, "%.1f MB/s", overallSpeedMbps),
                                elapsedTime = elapsedStr,
                                estimatedTime = estimatedStr,
                                warnings = warningsList,
                                renamedFilesCount = renamedCount,
                                skippedFilesCount = skippedCount
                            )

                            lastSpeedUpdateTime = currentTime
                            bytesCopiedSinceLastUpdate = 0L
                        }
                    }
                }
            }

            filesCopiedCount++
            val status = if (targetName != originalName && state.conflictStrategy == ConflictStrategy.RENAME) CopyStatus.RENAMED else CopyStatus.COPIED
            if (status == CopyStatus.RENAMED) renamedCount++
            fileDetails.add(CopiedFileDetail(originalName, finalPath, srcFile.length(), status))
            _uiState.value = _uiState.value.copy(
                filesCopied = filesCopiedCount,
                bytesCopied = totalBytesCopied,
                warnings = warningsList,
                renamedFilesCount = renamedCount,
                skippedFilesCount = skippedCount
            )
                    consecutiveFailures = 0
                    break // Success, exit retry loop
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    totalBytesCopied -= bytesCopiedThisAttempt
                    bytesCopiedSinceLastUpdate -= bytesCopiedThisAttempt
                    if (bytesCopiedSinceLastUpdate < 0) bytesCopiedSinceLastUpdate = 0
                    
                    if (attempt == 3 || wasRenamed) {
                        warningsList.add("Failed to copy $loopOriginalName" + (if (wasRenamed) " (renamed)" else " after 3 attempts") + ": ${e.message}")
                        fileDetails.add(CopiedFileDetail(loopOriginalName, "Unknown", 0L, CopyStatus.FAILED, e.message))
                        failedCount++
                        consecutiveFailures++
                        filesCopiedCount++
                        _uiState.value = _uiState.value.copy(
                            filesCopied = filesCopiedCount,
                            failedFilesCount = failedCount, 
                            warnings = warningsList,
                            bytesCopied = totalBytesCopied
                        )
                        break
                    } else {
                        warningsList.add("Retry $attempt for $loopOriginalName: ${e.message}")
                        _uiState.value = _uiState.value.copy(
                            warnings = warningsList,
                            bytesCopied = totalBytesCopied
                        )
                    }
                }
            }
            
            if (consecutiveFailures >= 3) {
                warningsList.add("Aborting job due to 3 consecutive failures.")
                _uiState.value = _uiState.value.copy(warnings = warningsList)
                break
            }
        }

        val finalStatus = if (_uiState.value.isCancelled) "Cancelled." else "Done."

        val totalElapsedSec = (System.currentTimeMillis() - startTime) / 1000.0
        val overallSpeedMbps = if (totalElapsedSec > 0) (totalBytesCopied / (1024.0 * 1024.0)) / totalElapsedSec else 0.0

        HistoryManager.saveHistory(context, CopyHistory(startTime, state.srcUri ?: "", state.destUri ?: "", fileDetails))

        _uiState.value = _uiState.value.copy(
            statusText = finalStatus,
            isRunning = false,
            isDone = true,
            copySpeed = "0.0 MB/s",
            averageSpeed = String.format(Locale.US, "%.1f MB/s", overallSpeedMbps),
            elapsedTime = formatTime(totalElapsedSec.toLong()),
            estimatedTime = "00:00",
            warnings = warningsList
        )
    }
}
