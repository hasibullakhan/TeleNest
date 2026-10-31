package com.awd.teledrive.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class SyncMethod(
    val key: String,
    val displayName: String,
    val shortSummary: String,
    val descriptionEn: String,
    val descriptionBn: String
) {
    TWO_WAY(
        key = "two_way",
        displayName = "Two-way",
        shortSummary = "Sync additions, changes & deletions both ways",
        descriptionEn = "Syncs additions, modifications, and deletions in both directions between device and cloud.",
        descriptionBn = "ফোন এবং ক্লাউড উভয় দিকেই নতুন ফাইল আপলোড/ডাউনলোড হবে এবং যেকোনো এক পাশ থেকে ডিলিট করলে অপর পাশ থেকেও ডিলিট হবে।"
    ),
    UPLOAD_ONLY(
        key = "upload_only",
        displayName = "Upload only",
        shortSummary = "Upload new local files; keep cloud files if deleted locally",
        descriptionEn = "Uploads new and modified local files to cloud. Files deleted on device are kept safe in cloud.",
        descriptionBn = "ফোনের নতুন ফাইল ক্লাউডে আপলোড হবে। ফোন থেকে কোনো ফাইল ডিলিট করলেও ক্লাউড থেকে ডিলিট হবে না।"
    ),
    UPLOAD_THEN_DELETE(
        key = "upload_then_delete",
        displayName = "Upload then delete",
        shortSummary = "Upload local files to cloud, then delete from phone",
        descriptionEn = "Uploads local files to cloud, then automatically deletes the local copy to free up phone storage.",
        descriptionBn = "ফোনের ফাইল ক্লাউডে আপলোড হওয়ার সাথে সাথে ফোন থেকে অটোমেটিক ডিলিট হয়ে ফোনের মেমোরি খালি করবে।"
    ),
    UPLOAD_MIRROR(
        key = "upload_mirror",
        displayName = "Upload mirror",
        shortSummary = "Make cloud folder an exact mirror of local folder",
        descriptionEn = "Makes the cloud folder an exact mirror of the local folder. Files deleted locally are also deleted in cloud.",
        descriptionBn = "ক্লাউড ফোল্ডারকে ফোনের ফোল্ডারের হুবহু কপি (Mirror) রাখবে। ফোনে কোনো ফাইল না থাকলে ক্লাউড থেকেও মুছে যাবে।"
    ),
    DOWNLOAD_ONLY(
        key = "download_only",
        displayName = "Download only",
        shortSummary = "Download new cloud files; keep local files if deleted in cloud",
        descriptionEn = "Downloads new files from the cloud folder to device. Files deleted in cloud are kept on device.",
        descriptionBn = "ক্লাউড ফোল্ডারের নতুন ফাইল ফোনে ডাউনলোড হবে। ক্লাউড থেকে ডিলিট হলেও ফোনে ফাইল থেকে যাবে।"
    ),
    DOWNLOAD_THEN_DELETE(
        key = "download_then_delete",
        displayName = "Download then delete",
        shortSummary = "Download cloud files to phone, then delete from cloud",
        descriptionEn = "Downloads files from cloud to device, then deletes them from the cloud folder.",
        descriptionBn = "ক্লাউড ফোল্ডার থেকে ফাইল ফোনে ডাউনলোড হওয়ার পর ক্লাউড ফোল্ডার থেকে অটোমেটিক ডিলিট হয়ে যাবে।"
    ),
    DOWNLOAD_MIRROR(
        key = "download_mirror",
        displayName = "Download mirror",
        shortSummary = "Make local folder an exact mirror of cloud folder",
        descriptionEn = "Makes the local folder an exact mirror of the cloud folder. Files deleted in cloud are also deleted locally.",
        descriptionBn = "ফোনের ফোল্ডারকে ক্লাউড ফোল্ডারের হুবহু কপি (Mirror) রাখবে। ক্লাউডে কোনো ফাইল না থাকলে ফোন থেকেও মুছে যাবে।"
    );

    companion object {
        fun fromKey(key: String): SyncMethod {
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) || it.name.equals(key, ignoreCase = true) }
                ?: TWO_WAY
        }
    }
}

@Serializable
data class SyncFolderPair(
    val id: String = "pair_${System.nanoTime()}",
    val name: String = "",
    val localPath: String = "",
    val remotePathDisplay: String = "",
    val remoteChatId: Long = 0L,
    val remoteVirtualFolderId: String = "0",
    val syncMethod: SyncMethod = SyncMethod.TWO_WAY,
    val excludeHiddenFiles: Boolean = true,
    val excludeSubfolders: Boolean = false,
    val deleteEmptySubfolders: Boolean = false,
    val instantUpload: Boolean = true,
    val selectFilesByName: Boolean = false,
    val nameFilterPattern: String = "",
    val useDefaultSyncOptions: Boolean = true,
    val enabled: Boolean = true,
    val lastSyncTime: Long = 0L,
    val lastSyncStatus: String = "",
    val totalSyncedFiles: Int = 0
) {
    val effectiveTitle: String
        get() = when {
            name.isNotBlank() -> name
            localPath == "/storage/emulated/0" || localPath == "/storage/emulated/0/" ->
                remotePathDisplay.trim('/').substringAfterLast('/').ifBlank { "Internal storage" }
            localPath.isNotBlank() -> localPath.trimEnd('/').substringAfterLast('/').ifBlank { "Folder Pair" }
            remotePathDisplay.isNotBlank() -> remotePathDisplay.trim('/').substringAfterLast('/').ifBlank { "Folder Pair" }
            else -> "Folder Pair"
        }

    val formattedLocalPath: String
        get() = when {
            localPath.isBlank() -> ""
            localPath == "/storage/emulated/0" || localPath == "/storage/emulated/0/" -> "[Internal storage]"
            localPath.startsWith("/storage/emulated/0/") -> "[Internal storage]/${localPath.removePrefix("/storage/emulated/0/")}"
            else -> localPath
        }

    val formattedRemotePath: String
        get() = when {
            remotePathDisplay.isBlank() -> ""
            remotePathDisplay.startsWith("/") -> remotePathDisplay
            else -> "/$remotePathDisplay"
        }
}

@Serializable
data class AutosyncGlobalSettings(
    val enableAutosync: Boolean = false,
    val scheduleIntervalMinutes: Int = SCHEDULE_AUTO_DETECT, // -1 = Auto detection, 0 = Manual, 5..1440 = Periodic
    val onlyWhileCharging: Boolean = false,
    val minBatteryLevel: Int = 30, // 0, 15, 20, 30, 40, 50, 60, 70, 80
    val wifiOnly: Boolean = true, // true = Using Wi-Fi only, false = Wi-Fi or mobile data
    val allowedOnMeteredWifi: Boolean = true,
    val allowedOnRoaming: Boolean = false,
    val wifiUploadSizeLimitBytes: Long = 0L, // 0L = no limit
    val wifiDownloadSizeLimitBytes: Long = 0L, // 0L = no limit
    val mobileUploadSizeLimitBytes: Long = 0L, // 0L = no limit
    val mobileDownloadSizeLimitBytes: Long = 0L, // 0L = no limit
    val warnOnMobileNetwork: Boolean = false,
    val retryAutomatically: Boolean = true,
    val retryWaitMinutes: Int = 5,
    val maxRetryAttempts: Int = 1,
    val allowParallelUploads: Boolean = true,
    val allowParallelDownloads: Boolean = true
) {
    companion object {
        const val SCHEDULE_AUTO_DETECT = -1

        val SCHEDULE_OPTIONS_MINUTES = listOf(
            SCHEDULE_AUTO_DETECT, // Auto detection (Instant on file changes)
            5,                    // Every 5 minutes
            15,                   // Every 15 minutes
            30,                   // Every 30 minutes
            60,                   // Every hour
            120,                  // Every 2 hours
            240,                  // Every 4 hours
            360,                  // Every 6 hours
            480,                  // Every 8 hours
            720,                  // Every 12 hours
            1440,                 // Every 24 hours (Daily)
            0                     // Disabled (Manual only)
        )

        val BATTERY_LEVEL_OPTIONS = listOf(
            0, 15, 20, 30, 40, 50, 60, 70, 80
        )

        val FILE_SIZE_LIMIT_OPTIONS_BYTES = listOf(
            0L,                         // no limit
            5L * 1000L * 1000L,         // 5 MB
            10L * 1000L * 1000L,        // 10 MB
            25L * 1000L * 1000L,        // 25 MB
            50L * 1000L * 1000L,        // 50 MB
            100L * 1000L * 1000L,       // 100 MB
            250L * 1000L * 1000L,       // 250 MB
            500L * 1000L * 1000L,       // 500 MB
            1000L * 1000L * 1000L,      // 1 GB
            2000L * 1000L * 1000L       // 2 GB
        )

        val RETRY_WAIT_OPTIONS_MINUTES = listOf(1, 5, 15, 30)
        val MAX_RETRY_ATTEMPTS_OPTIONS = listOf(1, 2, 3, 5, 10)

        fun formatSchedule(minutes: Int): String = when (minutes) {
            SCHEDULE_AUTO_DETECT -> "Auto detection (Instant)"
            0 -> "Disabled (Manual only)"
            5 -> "Every 5 minutes"
            15 -> "Every 15 minutes"
            30 -> "Every 30 minutes"
            60 -> "Every hour"
            120 -> "Every 2 hours"
            240 -> "Every 4 hours"
            360 -> "Every 6 hours"
            480 -> "Every 8 hours"
            720 -> "Every 12 hours"
            1440 -> "Every 24 hours"
            else -> "Every $minutes minutes"
        }

        fun formatSizeLimit(bytes: Long): String = when {
            bytes <= 0L -> "no limit"
            bytes >= 1000L * 1000L * 1000L -> "${bytes / (1000L * 1000L * 1000L)} GB"
            else -> "${bytes / (1000L * 1000L)} MB"
        }
    }
}

@Serializable
data class SyncedFileRecord(
    val relativePath: String,
    val localSize: Long,
    val localLastModified: Long,
    val originalTimestamp: Long,
    val remoteMessageId: Long = 0L,
    val remoteChatId: Long = 0L,
    val remoteVirtualFolderId: String = "0",
    val syncedAt: Long = System.currentTimeMillis()
)

@Serializable
data class SyncStateStore(
    val recordsByPair: Map<String, Map<String, SyncedFileRecord>> = emptyMap()
)

data class SyncActiveStatus(
    val isRunning: Boolean = false,
    val activePairId: String? = null,
    val activePairName: String = "",
    val currentFileName: String = "",
    val currentAction: String = "", // "Uploading", "Downloading", "Scanning", "Mirroring"
    val processedCount: Int = 0,
    val totalCount: Int = 0,
    val progress: Float = 0f,
    val statusMessage: String = ""
)
