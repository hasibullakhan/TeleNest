package com.awd.teledrive.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
sealed class DriveItem {
    abstract val id: Long
    abstract val parentChatId: Long
    abstract val name: String
    abstract val isStarred: Boolean
    abstract val createdAt: Long
    abstract val isTrashed: Boolean
    abstract val trashedAt: Long?
    abstract val isOffline: Boolean

    val uniqueKey: String
        get() = when (this) {
            is Folder -> if (isVirtual) "vf_${virtualId ?: id}" else "folder_${parentChatId}_$id"
            is File -> "file_${parentChatId}_$id"
        }

    @Immutable
    @Serializable
    data class Folder(
        override val id: Long,
        override val parentChatId: Long = 0,
        override val name: String,
        val telegramChatId: Long = 0,
        override val isStarred: Boolean = false,
        val isVirtual: Boolean = false,
        val virtualId: String? = null,
        val virtualParentId: String? = "0",
        val isSecure: Boolean = false,
        override val createdAt: Long = 0L,
        val itemCount: Int = 0,
        val totalSize: Long = 0L,
        val colorHex: String? = null,
        override val isTrashed: Boolean = false,
        override val trashedAt: Long? = null,
        override val isOffline: Boolean = false
    ) : DriveItem()

    @Immutable
    @Serializable
    data class File(
        override val id: Long,
        override val parentChatId: Long = 0,
        override val name: String,
        val size: Long,
        val mimeType: String,
        val telegramFileId: Int,
        val thumbnailPath: String? = null,
        val localPath: String? = null,
        override val isStarred: Boolean = false,
        val remoteUniqueId: String = "",
        val splitGroupId: String? = null,
        val totalParts: Int = 1,
        val virtualParentId: String? = "0",
        val isEncrypted: Boolean = false,
        override val createdAt: Long = 0L,
        override val isTrashed: Boolean = false,
        override val trashedAt: Long? = null,
        override val isOffline: Boolean = false,
        val offlinePath: String? = null
    ) : DriveItem() {
        val isSplit: Boolean
            get() = totalParts > 1 || !splitGroupId.isNullOrBlank()

        fun isApkFile(): Boolean {
            return mimeType.equals("application/vnd.android.package-archive", ignoreCase = true) ||
                name.endsWith(".apk", ignoreCase = true)
        }
    }
}

