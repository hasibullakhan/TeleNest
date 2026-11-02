package com.awd.teledrive.data.local

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "drive_items",
    primaryKeys = ["id", "parentChatId"],
    indices = [
        Index(value = ["parentChatId", "virtualParentId", "isTrashed", "createdAt"], name = "idx_drive_items_folder_lookup"),
        Index(value = ["isFolder", "isTrashed", "createdAt"], name = "idx_drive_items_type_trash_date"),
        Index(value = ["remoteUniqueId"], name = "idx_drive_items_remote_uid"),
        Index(value = ["thumbnailRemoteUniqueId"], name = "idx_drive_items_thumb_uid"),
        Index(value = ["virtualId"], name = "idx_drive_items_virtual_id"),
        Index(value = ["virtualParentId"], name = "idx_drive_items_virtual_parent"),
        Index(value = ["isStarred", "isTrashed"], name = "idx_drive_items_starred"),
        Index(value = ["isOffline", "isTrashed"], name = "idx_drive_items_offline"),
        Index(value = ["splitGroupId", "parentChatId"], name = "idx_drive_items_split_group")
    ]
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

@Immutable
@Entity(
    tableName = "transfers",
    indices = [
        Index(value = ["fileId"], name = "idx_transfers_file_id"),
        Index(value = ["localPath"], name = "idx_transfers_local_path"),
        Index(value = ["status"], name = "idx_transfers_status")
    ]
)
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

