package com.awd.teledrive.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DriveDao {

    data class FileTypeStat(
        val category: String,
        val totalSize: Long,
        val count: Int
    )

    @Query("SELECT * FROM drive_items WHERE isFolder = 1 AND isTrashed = 0 ORDER BY name ASC")
    fun getAllFoldersFlow(): Flow<List<DriveItemEntity>>

    @Query("SELECT * FROM drive_items WHERE isFolder = 1 ORDER BY name ASC")
    fun getAllFoldersIncludingTrashedFlow(): Flow<List<DriveItemEntity>>

    @Query("SELECT * FROM drive_items WHERE isFolder = 0 AND isTrashed = 0 ORDER BY createdAt DESC, id DESC")
    fun getAllFiles(): Flow<List<DriveItemEntity>>

    @Query("SELECT * FROM drive_items WHERE isFolder = 0 ORDER BY createdAt DESC, id DESC")
    fun getAllFilesIncludingTrashedFlow(): Flow<List<DriveItemEntity>>

    @Query("SELECT * FROM drive_items")
    suspend fun getAllItemsSync(): List<DriveItemEntity>

    @Query("SELECT * FROM drive_items WHERE isStarred = 1 AND isTrashed = 0 ORDER BY isFolder DESC, createdAt DESC, id DESC")
    fun getStarredItems(): Flow<List<DriveItemEntity>>

    @Query("SELECT * FROM drive_items WHERE isTrashed = 1 ORDER BY trashedAt DESC, id DESC")
    fun getTrashedItemsFlow(): Flow<List<DriveItemEntity>>

    @Query("SELECT * FROM drive_items WHERE isTrashed = 1")
    suspend fun getTrashedItemsSync(): List<DriveItemEntity>

    @Query("SELECT * FROM drive_items WHERE isTrashed = 1 AND trashedAt IS NOT NULL AND trashedAt <= :cutoffTimestamp")
    suspend fun getExpiredTrashedItemsSync(cutoffTimestamp: Long): List<DriveItemEntity>

    @Query("SELECT * FROM drive_items WHERE isOffline = 1 AND isTrashed = 0 ORDER BY isFolder DESC, createdAt DESC, id DESC")
    fun getOfflineItemsFlow(): Flow<List<DriveItemEntity>>

    @Query("SELECT * FROM drive_items WHERE isFolder = 0 AND isTrashed = 0 AND createdAt >= :sinceTimestamp ORDER BY createdAt DESC, id DESC")
    fun getRecentFilesFlow(sinceTimestamp: Long): Flow<List<DriveItemEntity>>

    @Query("SELECT * FROM drive_items WHERE isTrashed = 0 AND name LIKE '%' || :query || '%' ORDER BY isFolder DESC, createdAt DESC, id DESC")
    fun searchGlobal(query: String): Flow<List<DriveItemEntity>>

    @Query("""
        SELECT * FROM drive_items 
        WHERE isTrashed = 0 AND (
            (:virtualParentId != '0' AND virtualParentId = :virtualParentId)
            OR (:virtualParentId = '0' AND parentChatId = :parentChatId AND (virtualParentId IS NULL OR virtualParentId = '0' OR virtualParentId = CAST(:parentChatId AS TEXT)))
        )
        ORDER BY isFolder DESC, createdAt DESC, id DESC
    """)
    fun getItemsFlow(parentChatId: Long, virtualParentId: String): Flow<List<DriveItemEntity>>

    @Query("SELECT * FROM drive_items WHERE id = :id AND parentChatId = :parentChatId LIMIT 1")
    suspend fun getItemById(id: Long, parentChatId: Long): DriveItemEntity?

    @Query("SELECT * FROM drive_items WHERE virtualId = :virtualId AND isFolder = 1 LIMIT 1")
    suspend fun getVirtualFolderById(virtualId: String): DriveItemEntity?

    @Query("SELECT * FROM drive_items WHERE virtualParentId = :virtualId")
    suspend fun getItemsByVirtualParentSync(virtualId: String): List<DriveItemEntity>

    @Query("SELECT * FROM drive_items WHERE (remoteUniqueId = :uniqueId AND :uniqueId != '') OR (telegramFileId = :fileId AND :fileId != 0) LIMIT 1")
    suspend fun getItemByRemoteUniqueIdOrFileId(uniqueId: String, fileId: Int): DriveItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<DriveItemEntity>)

    @Transaction
    suspend fun insertItemsPreservingStarred(items: List<DriveItemEntity>) {
        val merged = items.map { incoming ->
            val existing = getItemById(incoming.id, incoming.parentChatId)
            if (existing != null) {
                incoming.copy(
                    isStarred = existing.isStarred || incoming.isStarred,
                    isTrashed = existing.isTrashed || incoming.isTrashed,
                    trashedAt = existing.trashedAt ?: incoming.trashedAt,
                    isOffline = existing.isOffline || incoming.isOffline,
                    offlinePath = existing.offlinePath ?: incoming.offlinePath,
                    localPath = incoming.localPath ?: existing.localPath,
                    thumbnailPath = incoming.thumbnailPath ?: existing.thumbnailPath,
                    virtualParentId = if (incoming.virtualParentId.isNullOrEmpty() || incoming.virtualParentId == "0") {
                        existing.virtualParentId ?: "0"
                    } else {
                        incoming.virtualParentId
                    }
                )
            } else {
                incoming
            }
        }
        insertItems(merged)
    }

    @Query("UPDATE drive_items SET localPath = :path WHERE remoteUniqueId = :uniqueId")
    suspend fun updateLocalPathByUniqueId(uniqueId: String, path: String?)

    @Query("UPDATE drive_items SET thumbnailPath = :path WHERE thumbnailRemoteUniqueId = :uniqueId OR remoteUniqueId = :uniqueId")
    suspend fun updateThumbnailPathByUniqueId(uniqueId: String, path: String)

    @Query("""
        UPDATE drive_items 
        SET localPath = CASE WHEN remoteUniqueId = :uniqueId THEN :path ELSE localPath END,
            thumbnailPath = CASE WHEN thumbnailRemoteUniqueId = :uniqueId OR remoteUniqueId = :uniqueId THEN :path ELSE thumbnailPath END
        WHERE (remoteUniqueId = :uniqueId AND (localPath IS NULL OR localPath != :path))
           OR (thumbnailRemoteUniqueId = :uniqueId AND (thumbnailPath IS NULL OR thumbnailPath != :path))
    """)
    suspend fun updateFileAndThumbnailPathIfChanged(uniqueId: String, path: String): Int

    @Query("UPDATE drive_items SET isStarred = :isStarred WHERE id = :id AND parentChatId = :parentChatId")
    suspend fun updateStarred(id: Long, parentChatId: Long, isStarred: Boolean)

    @Query("UPDATE drive_items SET isTrashed = :isTrashed, trashedAt = :trashedAt WHERE id = :id AND (parentChatId = :parentChatId OR parentChatId = 0)")
    suspend fun updateTrashState(id: Long, parentChatId: Long, isTrashed: Boolean, trashedAt: Long?)

    @Query("UPDATE drive_items SET isTrashed = :isTrashed, trashedAt = :trashedAt WHERE splitGroupId = :splitGroupId AND (parentChatId = :parentChatId OR parentChatId = 0)")
    suspend fun updateTrashStateBySplitGroup(splitGroupId: String, parentChatId: Long, isTrashed: Boolean, trashedAt: Long?)

    @Query("UPDATE drive_items SET isTrashed = :isTrashed, trashedAt = :trashedAt WHERE virtualId = :virtualId")
    suspend fun updateVirtualFolderTrashState(virtualId: String, isTrashed: Boolean, trashedAt: Long?)

    @Query("UPDATE drive_items SET isTrashed = :isTrashed, trashedAt = :trashedAt WHERE virtualParentId = :virtualParentId")
    suspend fun updateTrashStateByVirtualParent(virtualParentId: String, isTrashed: Boolean, trashedAt: Long?)

    @Query("UPDATE drive_items SET isTrashed = :isTrashed, trashedAt = :trashedAt WHERE parentChatId = :chatId AND id != :chatId")
    suspend fun updateTrashStateByChat(chatId: Long, isTrashed: Boolean, trashedAt: Long?)

    @Query("UPDATE drive_items SET isOffline = :isOffline, offlinePath = :offlinePath WHERE id = :id AND parentChatId = :parentChatId")
    suspend fun updateOfflineState(id: Long, parentChatId: Long, isOffline: Boolean, offlinePath: String?)

    @Query("UPDATE drive_items SET virtualParentId = :virtualFolderId WHERE id = :messageId")
    suspend fun updateVirtualParent(messageId: Long, virtualFolderId: String)

    @Query("UPDATE drive_items SET virtualParentId = :virtualFolderId WHERE id = :messageId AND parentChatId = :chatId")
    suspend fun updateVirtualParentInChat(messageId: Long, chatId: Long, virtualFolderId: String)

    @Query("UPDATE drive_items SET name = :newName WHERE id = :id AND parentChatId = :parentChatId")
    suspend fun renameItem(id: Long, parentChatId: Long, newName: String)

    @Query("UPDATE drive_items SET name = :newName WHERE virtualId = :virtualId AND isFolder = 1")
    suspend fun renameVirtualFolderByVirtualId(virtualId: String, newName: String)

    @Query("DELETE FROM drive_items WHERE id = :id AND parentChatId = :parentChatId")
    suspend fun deleteItemCompletely(id: Long, parentChatId: Long)

    @Query("DELETE FROM drive_items WHERE parentChatId = :chatId")
    suspend fun deleteItemsByChat(chatId: Long)

    @Query("DELETE FROM drive_items WHERE parentChatId = :chatId AND isFolder = 0 AND isTrashed = 0 AND id NOT IN (:keepIds)")
    suspend fun deleteFilesByChatNotInList(chatId: Long, keepIds: List<Long>)

    @Query("UPDATE drive_items SET thumbnailPath = NULL")
    suspend fun clearAllThumbnailPaths()

    @Query("UPDATE drive_items SET localPath = NULL WHERE isOffline = 0")
    suspend fun clearAllLocalPaths()

    @Query("UPDATE drive_items SET virtualParentId = '0' WHERE isFolder = 0")
    suspend fun resetAllFileVirtualParents()

    @Query("DELETE FROM drive_items WHERE isFolder = 1 AND isVirtual = 0 AND parentChatId = 0 AND id = 0 AND :savedChatId != 0")
    suspend fun cleanupZeroChatPhysicalFolders(savedChatId: Long)

    @Query("DELETE FROM drive_items WHERE parentChatId = 0 AND :savedChatId != 0")
    suspend fun cleanupAllZeroChatItems(savedChatId: Long)

    @Query("SELECT virtualId FROM drive_items WHERE isFolder = 1 AND isVirtual = 1 AND name LIKE 'Recovered Folder%'")
    suspend fun getRecoveredFolderVirtualIds(): List<String>

    @Query("DELETE FROM drive_items WHERE isFolder = 1 AND isVirtual = 1 AND name LIKE 'Recovered Folder%'")
    suspend fun deleteRecoveredFolders()

    @Transaction
    suspend fun cleanupRecoveredFoldersAndRescueChildren(): List<String> {
        val ids = getRecoveredFolderVirtualIds()
        ids.forEach { vid ->
            updateVirtualParentByOldParent(vid, "0")
        }
        deleteRecoveredFolders()
        return ids
    }

    @Query("UPDATE drive_items SET virtualParentId = :newParentId WHERE virtualParentId = :oldParentId")
    suspend fun updateVirtualParentByOldParent(oldParentId: String, newParentId: String)

    @Query("SELECT * FROM drive_items WHERE isFolder = 1 AND isVirtual = 1")
    suspend fun getAllVirtualFoldersSync(): List<DriveItemEntity>

    @Query("DELETE FROM drive_items WHERE isFolder = 1 AND isVirtual = 1")
    suspend fun deleteAllVirtualFolders()

    @Transaction
    suspend fun syncVirtualFolders(entities: List<DriveItemEntity>) {
        val existingMap = getAllVirtualFoldersSync().associateBy { it.virtualId }
        deleteAllVirtualFolders()
        val merged = entities.map { incoming ->
            val old = incoming.virtualId?.let { existingMap[it] }
            if (old != null) {
                incoming.copy(
                    isStarred = old.isStarred || incoming.isStarred,
                    isTrashed = incoming.isTrashed,
                    trashedAt = incoming.trashedAt
                )
            } else {
                incoming
            }
        }
        insertItems(merged)
    }

    @Query("""
        SELECT 
            CASE 
                WHEN mimeType LIKE 'image/%' THEN 'Images'
                WHEN mimeType LIKE 'video/%' THEN 'Videos'
                WHEN mimeType LIKE 'audio/%' THEN 'Audio'
                WHEN mimeType LIKE 'application/pdf%' OR mimeType LIKE 'text/%' THEN 'Documents'
                WHEN mimeType LIKE '%android.package-archive%' THEN 'APKs'
                ELSE 'Others'
            END AS category,
            COALESCE(SUM(size), 0) AS totalSize,
            COUNT(*) AS count
        FROM drive_items
        WHERE isFolder = 0 AND isTrashed = 0 AND partIndex = 0
        GROUP BY category
    """)
    fun getCloudFileTypeStats(): Flow<List<FileTypeStat>>
}
