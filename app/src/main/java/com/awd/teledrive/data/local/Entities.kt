package com.awd.teledrive.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "drive_items",
    primaryKeys = ["id", "parentChatId"]
)
data class DriveItemEntity(
    val id: Long,
    val name: String,
    val size: Long,
    val mimeType: String,
    val telegramFileId: Int,
    val parentChatId: Long = 0,
    val isFolder: Boolean = false,
    val isStarred: Boolean = false,
    val thumbnailPath: String? = null,
    val localPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val thumbnailFileId: Int? = null,
    val remoteUniqueId: String? = null,
    val thumbnailRemoteUniqueId: String? = null,
    val splitGroupId: String? = null,
    val partIndex: Int = 0,
    val totalParts: Int = 1,
    val isVirtual: Boolean = false,
    val virtualId: String? = null,
    val virtualParentId: String? = "0",
    val isSecure: Boolean = false,
    val isEncrypted: Boolean = false,
    val isTrashed: Boolean = false,
    val trashedAt: Long? = null,
    val isOffline: Boolean = false,
    val offlinePath: String? = null
)

@Entity(tableName = "transfers")
data class TransferEntity(
    @PrimaryKey val remoteUniqueId: String,
    val fileId: Int,
    val fileName: String,
    val progress: Float,
    val isDownload: Boolean,
    val status: String,
    val totalSize: Long = 0,
    val downloadedSize: Long = 0,
    val localPath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
