package com.awd.teledrive.core.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.StatFs
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import kotlin.math.log10
import kotlin.math.pow

object FileUtils {

    /**
     * Resolves the true original display name of a file from any Uri (content:// or file://),
     * preventing raw numeric IDs (e.g. "11801") from replacing authentic filenames.
     */
    fun resolveUriFileName(context: Context, uri: Uri): String {
        var fileName: String? = null

        // 1. First attempt: Query OpenableColumns.DISPLAY_NAME via ContentResolver
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) {
                        fileName = cursor.getString(nameIdx)?.trim()
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Second attempt: Check MediaStore if name is missing or looks like a raw numeric ID (e.g. "11801")
        if (fileName.isNullOrBlank() || (!fileName!!.contains('.') && fileName!!.all { it.isDigit() })) {
            try {
                val projection = arrayOf(
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.DATA
                )
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                        if (nameIdx >= 0) {
                            val candidate = cursor.getString(nameIdx)?.trim()
                            if (!candidate.isNullOrBlank()) {
                                fileName = candidate
                            }
                        }
                        if (fileName.isNullOrBlank() || !fileName!!.contains('.')) {
                            val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                            if (dataIdx >= 0) {
                                val dataPath = cursor.getString(dataIdx)
                                if (!dataPath.isNullOrBlank()) {
                                    val nameFromPath = File(dataPath).name.trim()
                                    if (nameFromPath.isNotBlank()) {
                                        fileName = nameFromPath
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 3. Third attempt: uri.lastPathSegment
        if (fileName.isNullOrBlank() || (!fileName!!.contains('.') && fileName!!.all { it.isDigit() })) {
            val segment = uri.lastPathSegment?.substringAfterLast('/')?.trim()
            if (!segment.isNullOrBlank() && segment.contains('.')) {
                fileName = segment
            }
        }

        // 4. Ensure file has an appropriate extension based on MIME type if missing
        val mimeType = try {
            context.contentResolver.getType(uri)
        } catch (_: Exception) {
            null
        }
        val extension = mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }

        if (fileName.isNullOrBlank()) {
            val prefix = when {
                mimeType?.startsWith("video/") == true -> "VID_"
                mimeType?.startsWith("image/") == true -> "IMG_"
                mimeType?.startsWith("audio/") == true -> "AUD_"
                else -> "FILE_"
            }
            fileName = "${prefix}${System.currentTimeMillis()}${if (!extension.isNullOrBlank()) ".$extension" else ""}"
        } else if (!fileName!!.contains('.') && !extension.isNullOrBlank()) {
            fileName = "$fileName.$extension"
        }

        return fileName!!
    }

    // 1.95 GB per split part (safe below Telegram's 2 GB standard upload limit)
    const val MAX_FILE_SIZE: Long = 1950L * 1024L * 1024L

    fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext.isEmpty()) return "application/octet-stream"
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
        return when (ext) {
            "apk", "apks", "xapk" -> "application/vnd.android.package-archive"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "rar" -> "application/vnd.rar"
            "7z" -> "application/x-7z-compressed"
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            "ogg", "oga" -> "audio/ogg"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "heic" -> "image/heic"
            "txt", "log", "md", "json", "xml", "csv" -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    fun hasEnoughSpace(context: Context, requiredBytes: Long): Boolean {
        return try {
            val stat = StatFs(context.cacheDir.absolutePath)
            stat.availableBytes >= requiredBytes
        } catch (_: Exception) {
            true
        }
    }

    fun generateSplitGroupId(): String = UUID.randomUUID().toString().replace("-", "").take(12)

    fun splitFile(
        context: Context,
        sourceFile: File,
        onProgress: (Float) -> Unit = {}
    ): List<File> {
        val parts = mutableListOf<File>()
        val totalSize = sourceFile.length()
        if (totalSize <= 0L) return parts
        val splitDir = File(context.cacheDir, "split_${System.nanoTime()}").apply { mkdirs() }
        val buffer = ByteArray(1024 * 1024)
        var bytesProcessed = 0L
        var partIndex = 0

        FileInputStream(sourceFile).use { input ->
            while (bytesProcessed < totalSize) {
                val partFile = File(splitDir, "${sourceFile.name}.part$partIndex")
                var partBytes = 0L
                FileOutputStream(partFile).use { output ->
                    while (partBytes < MAX_FILE_SIZE && bytesProcessed < totalSize) {
                        val toRead = minOf(buffer.size.toLong(), MAX_FILE_SIZE - partBytes).toInt()
                        val read = input.read(buffer, 0, toRead)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        partBytes += read
                        bytesProcessed += read
                        onProgress((bytesProcessed.toFloat() / totalSize.toFloat()).coerceIn(0f, 1f))
                    }
                }
                parts.add(partFile)
                partIndex++
            }
        }
        return parts
    }

    fun openFileExternal(context: Context, filePath: String, mimeType: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Toast.makeText(context, "File not found locally. Please wait for download.", Toast.LENGTH_SHORT).show()
                return
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val extension = file.extension.lowercase(Locale.ROOT)
            val finalMime = if (mimeType.isBlank() || mimeType == "application/octet-stream") {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: getMimeType(file.name)
            } else {
                mimeType
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, finalMime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(intent, "Open with")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Uses decimal SI units (1000-based: KB, MB, GB, TB) matching Android OS, Google Files,
     * and Telegram Android so file sizes match 100% with the system File Manager.
     */
    fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(size.toDouble()) / log10(1000.0)).toInt().coerceIn(0, units.lastIndex)
        return String.format(Locale.getDefault(), "%.1f %s", size / 1000.0.pow(digitGroups.toDouble()), units[digitGroups])
    }

    /**
     * Extracts the true original creation/capture timestamp of a file in milliseconds.
     * Checks EXIF DateTimeOriginal for photos, MediaMetadataRetriever for videos,
     * and falls back to the file's filesystem lastModified() timestamp.
     */
    fun extractOriginalTimestampMillis(file: File): Long {
        if (!file.exists()) return System.currentTimeMillis()
        val diskModified = file.lastModified().takeIf { it > 946684800000L } // after year 2000

        val mime = getMimeType(file.name)
        // 1. Try EXIF for images
        if (mime.startsWith("image/")) {
            try {
                val exif = android.media.ExifInterface(file.absolutePath)
                val dateStr = exif.getAttribute(android.media.ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(android.media.ExifInterface.TAG_DATETIME_DIGITIZED)
                    ?: exif.getAttribute(android.media.ExifInterface.TAG_DATETIME)
                if (!dateStr.isNullOrBlank()) {
                    val sdf = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
                    val parsed = sdf.parse(dateStr.trim())?.time
                    if (parsed != null && parsed > 946684800000L) {
                        return parsed
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Try MediaMetadataRetriever for videos
        if (mime.startsWith("video/")) {
            val retriever = android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                val metaDate = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DATE)
                if (!metaDate.isNullOrBlank()) {
                    // Format is often "20251015T143022.000Z" or "2025-10-15T14:30:22Z"
                    val clean = metaDate.replace("-", "").replace(":", "").trim()
                    val formats = listOf(
                        java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).apply {
                            timeZone = java.util.TimeZone.getTimeZone("UTC")
                        },
                        java.text.SimpleDateFormat("yyyyMMdd HHmmss", Locale.US)
                    )
                    for (fmt in formats) {
                        try {
                            val parsed = fmt.parse(clean.take(15))?.time
                            if (parsed != null && parsed > 946684800000L) {
                                return if (diskModified != null) minOf(parsed, diskModified) else parsed
                            }
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        }

        return diskModified ?: file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
    }

    /**
     * Restores the exact original creation/modification timestamp on a downloaded local file
     * and updates Android MediaStore so Gallery & File Manager apps display the original date/time
     * (e.g., 3 months or 1 year ago) instead of today's download time.
     */
    fun restoreOriginalTimestampAndScan(context: Context, file: File, originalTimestampMillis: Long) {
        if (!file.exists()) return
        val validTs = if (originalTimestampMillis > 946684800000L) {
            originalTimestampMillis
        } else {
            extractOriginalTimestampMillis(file)
        }
        try {
            file.setLastModified(validTs)
        } catch (_: Exception) {}

        val mime = getMimeType(file.name)
        try {
            android.media.MediaScannerConnection.scanFile(
                context.applicationContext,
                arrayOf(file.absolutePath),
                arrayOf(mime)
            ) { _, scannedUri ->
                if (scannedUri != null) {
                    try {
                        val values = android.content.ContentValues().apply {
                            put(MediaStore.MediaColumns.DATE_MODIFIED, validTs / 1000L)
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                put(MediaStore.MediaColumns.DATE_TAKEN, validTs)
                            }
                        }
                        context.applicationContext.contentResolver.update(scannedUri, values, null, null)
                    } catch (_: Exception) {}
                    try {
                        file.setLastModified(validTs)
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }
}
