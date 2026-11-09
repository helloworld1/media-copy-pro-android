package org.liberty.android.mediacopypro

import android.content.Context
import android.util.Log
import org.liberty.android.mediacopypro.data.AppFile
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Mp4DateExtractor {

    private const val TAG = "Mp4DateExtractor"
    private const val MAX_MOOV_SIZE = 20 * 1024 * 1024 // 20MB limit for safety

    // Matches formats like: 
    // 2026:07:16 17:09:52-07:00
    // 2026-07-12T12:02:28-0700
    private val tzDateRegex = Regex("""(\d{4})[:\-](\d{2})[:\-](\d{2})[ T](\d{2}):(\d{2}):(\d{2})([+\-])(\d{2}):?(\d{2})""")

    fun extractTimezoneAwareDate(context: Context, appFile: AppFile): Date? {
        try {
            appFile.openInputStream(context)?.use { stream ->
                val moovBytes = findMoovBox(stream) ?: return null
                
                // Convert bytes to string, replacing invalid chars (we only care about the date strings which are ASCII/UTF-8)
                val text = String(moovBytes, Charsets.UTF_8)
                
                var earliestDate: Date? = null
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
                
                tzDateRegex.findAll(text).forEach { matchResult ->
                    try {
                        val year = matchResult.groupValues[1]
                        val month = matchResult.groupValues[2]
                        val day = matchResult.groupValues[3]
                        val hour = matchResult.groupValues[4]
                        val min = matchResult.groupValues[5]
                        val sec = matchResult.groupValues[6]
                        val sign = matchResult.groupValues[7]
                        val tzHour = matchResult.groupValues[8]
                        val tzMin = matchResult.groupValues[9]
                        
                        val isoString = "${year}-${month}-${day}T${hour}:${min}:${sec}${sign}${tzHour}${tzMin}"
                        val date = format.parse(isoString)
                        
                        if (date != null) {
                            if (earliestDate == null || date.before(earliestDate)) {
                                earliestDate = date
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore parse errors for individual matches
                    }
                }
                
                return earliestDate
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract MP4 timezone date", e)
        }
        return null
    }

    private fun findMoovBox(stream: InputStream): ByteArray? {
        val header = ByteArray(8)
        
        while (true) {
            var read = readFully(stream, header, 8)
            if (read < 8) return null // EOF
            
            var size = (header[0].toLong() and 0xFFL shl 24) or
                       (header[1].toLong() and 0xFFL shl 16) or
                       (header[2].toLong() and 0xFFL shl 8) or
                       (header[3].toLong() and 0xFFL)
                       
            val type = String(header, 4, 4, Charsets.US_ASCII)
            
            var boxDataSize = size - 8
            
            if (size == 1L) {
                // 64-bit large box
                val largeSizeBuf = ByteArray(8)
                read = readFully(stream, largeSizeBuf, 8)
                if (read < 8) return null
                
                size = 0L
                for (i in 0..7) {
                    size = (size shl 8) or (largeSizeBuf[i].toLong() and 0xFFL)
                }
                boxDataSize = size - 16
            }
            
            if (type == "moov") {
                if (boxDataSize > MAX_MOOV_SIZE || boxDataSize < 0) {
                    Log.w(TAG, "moov box too large or invalid: $boxDataSize")
                    return null
                }
                val moovData = ByteArray(boxDataSize.toInt())
                readFully(stream, moovData, boxDataSize.toInt())
                return moovData
            } else {
                // Skip this box
                if (boxDataSize > 0) {
                    skipFully(stream, boxDataSize)
                }
            }
        }
    }
    
    private fun readFully(stream: InputStream, buffer: ByteArray, length: Int): Int {
        var totalRead = 0
        while (totalRead < length) {
            val read = stream.read(buffer, totalRead, length - totalRead)
            if (read == -1) break
            totalRead += read
        }
        return totalRead
    }
    
    private fun skipFully(stream: InputStream, length: Long) {
        var totalSkipped = 0L
        while (totalSkipped < length) {
            val skipped = stream.skip(length - totalSkipped)
            if (skipped <= 0L) {
                // fallback to read if skip fails
                val buffer = ByteArray(8192)
                var read = stream.read(buffer, 0, minOf(8192L, length - totalSkipped).toInt())
                if (read == -1) break
                totalSkipped += read
            } else {
                totalSkipped += skipped
            }
        }
    }
}
