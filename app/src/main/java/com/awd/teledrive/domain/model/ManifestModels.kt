package com.awd.teledrive.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class VirtualFolder(
    val id: String,
    val name: String,
    val parentId: String = "0",
    val createdAt: Long = System.currentTimeMillis() / 1000,
    val type: String = "virtual_folder",
    val isSecure: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis() / 1000,
    val colorHex: String? = null
)

@Serializable
data class FileMetadata(
    val messageId: Long,
    val fileName: String,
    val virtualFolderId: String? = "0",
    val isEncrypted: Boolean = false,
    val originalTimestamp: Long = 0L
)

@Serializable
data class SplitFileMaster(
    val groupId: String,
    val originalName: String,
    val totalSize: Long,
    val mimeType: String,
    val totalParts: Int,
    val virtualFolderId: String? = "0",
    val isEncrypted: Boolean = false,
    val partMessageIds: List<Long> = emptyList(),
    val metadataMessageId: Long = 0L,
    val originalTimestamp: Long = 0L
)

@Serializable
data class TeleDriveManifest(
    val version: Int = 1,
    val updatedAt: Long = System.currentTimeMillis() / 1000,
    val virtualFolders: Map<String, VirtualFolder> = emptyMap(),
    val fileMappings: Map<String, String> = emptyMap(),
    val secureFolderChatIds: Set<Long> = emptySet(),
    val splitFileMasters: Map<String, SplitFileMaster> = emptyMap(),
    val folderMetadataIds: Map<String, Long> = emptyMap(),
    val fileMetadataIds: Map<String, Long> = emptyMap(),
    val deletedFolderIds: Map<String, Long> = emptyMap(),
    val trashedFileIds: Map<String, Long> = emptyMap(),
    val trashedFolderIds: Map<String, Long> = emptyMap()
)

@Serializable
data class CloudBackup(
    val messageId: Long,
    val date: Long,
    val folderCount: Int
)

@Serializable
data class MoveCopyDestination(
    val label: String,
    val isRoot: Boolean = false,
    val isVirtual: Boolean = false,
    val targetChatId: Long = 0L,
    val targetVirtualId: String = "0"
)
