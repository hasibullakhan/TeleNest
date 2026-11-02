package com.awd.teledrive.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.awd.teledrive.data.local.DriveDao
import com.awd.teledrive.data.local.DriveItemEntity
import com.awd.teledrive.data.local.TeleDriveDatabase
import com.awd.teledrive.data.local.TransferDao
import com.awd.teledrive.data.local.TransferEntity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountDatabaseManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val databases = ConcurrentHashMap<String, TeleDriveDatabase>()
    private val prefs = context.getSharedPreferences("teledrive_accounts_store", Context.MODE_PRIVATE)

    private val _activeSlotId = MutableStateFlow(
        prefs.getString("active_account_slot", "default")?.takeIf { it.isNotBlank() } ?: "default"
    )
    val activeSlotId: StateFlow<String> = _activeSlotId.asStateFlow()

    private val migration5To6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                db.execSQL("ALTER TABLE drive_items ADD COLUMN isTrashed INTEGER NOT NULL DEFAULT 0")
            } catch (_: Exception) {}
            try {
                db.execSQL("ALTER TABLE drive_items ADD COLUMN trashedAt INTEGER")
            } catch (_: Exception) {}
            try {
                db.execSQL("ALTER TABLE drive_items ADD COLUMN isOffline INTEGER NOT NULL DEFAULT 0")
            } catch (_: Exception) {}
            try {
                db.execSQL("ALTER TABLE drive_items ADD COLUMN offlinePath TEXT")
            } catch (_: Exception) {}
        }
    }

    private val migration6To7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_drive_items_folder_lookup` ON `drive_items` (`parentChatId`, `virtualParentId`, `isTrashed`, `createdAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_drive_items_type_trash_date` ON `drive_items` (`isFolder`, `isTrashed`, `createdAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_drive_items_remote_uid` ON `drive_items` (`remoteUniqueId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_drive_items_thumb_uid` ON `drive_items` (`thumbnailRemoteUniqueId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_drive_items_virtual_id` ON `drive_items` (`virtualId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_drive_items_virtual_parent` ON `drive_items` (`virtualParentId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_drive_items_starred` ON `drive_items` (`isStarred`, `isTrashed`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_drive_items_offline` ON `drive_items` (`isOffline`, `isTrashed`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_drive_items_split_group` ON `drive_items` (`splitGroupId`, `parentChatId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_transfers_file_id` ON `transfers` (`fileId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_transfers_local_path` ON `transfers` (`localPath`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_transfers_status` ON `transfers` (`status`)")
        }
    }

    private fun getDatabaseName(slotId: String): String {
        val clean = slotId.ifBlank { "default" }
        return if (clean == "default") "teledrive.db" else "teledrive_$clean.db"
    }

    @Synchronized
    fun getDatabase(slotId: String = _activeSlotId.value): TeleDriveDatabase {
        val clean = slotId.ifBlank { "default" }
        databases[clean]?.let { return it }
        val dbName = getDatabaseName(clean)
        val built = Room.databaseBuilder(
            context,
            TeleDriveDatabase::class.java,
            dbName
        )
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addMigrations(migration5To6, migration6To7)
            .fallbackToDestructiveMigration(true)
            .build()
        databases[clean] = built
        return built
    }

    fun switchAccountSlot(slotId: String) {
        val clean = slotId.ifBlank { "default" }
        getDatabase(clean)
        _activeSlotId.value = clean
    }

    @Synchronized
    fun deleteAccountDatabase(slotId: String) {
        val clean = slotId.ifBlank { "default" }
        val dbName = getDatabaseName(clean)
        val existing = databases.remove(clean)
        try {
            existing?.close()
        } catch (_: Exception) {}
        try {
            context.deleteDatabase(dbName)
        } catch (_: Exception) {}
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private class SwitchableDriveDao(
    private val manager: AccountDatabaseManager
) : DriveDao {

    private fun currentDao(slotId: String = manager.activeSlotId.value): DriveDao =
        manager.getDatabase(slotId).driveDao()

    override fun getAllFoldersFlow(): Flow<List<DriveItemEntity>> =
        manager.activeSlotId.flatMapLatest { slot -> currentDao(slot).getAllFoldersFlow() }

    override fun getAllFoldersIncludingTrashedFlow(): Flow<List<DriveItemEntity>> =
        manager.activeSlotId.flatMapLatest { slot -> currentDao(slot).getAllFoldersIncludingTrashedFlow() }

    override fun getAllFiles(): Flow<List<DriveItemEntity>> =
        manager.activeSlotId.flatMapLatest { slot -> currentDao(slot).getAllFiles() }

    override fun getAllFilesIncludingTrashedFlow(): Flow<List<DriveItemEntity>> =
        manager.activeSlotId.flatMapLatest { slot -> currentDao(slot).getAllFilesIncludingTrashedFlow() }

    override suspend fun getAllItemsSync(): List<DriveItemEntity> =
        currentDao().getAllItemsSync()

    override fun getStarredItems(): Flow<List<DriveItemEntity>> =
        manager.activeSlotId.flatMapLatest { slot -> currentDao(slot).getStarredItems() }

    override fun getTrashedItemsFlow(): Flow<List<DriveItemEntity>> =
        manager.activeSlotId.flatMapLatest { slot -> currentDao(slot).getTrashedItemsFlow() }

    override suspend fun getTrashedItemsSync(): List<DriveItemEntity> =
        currentDao().getTrashedItemsSync()

    override suspend fun getExpiredTrashedItemsSync(cutoffTimestamp: Long): List<DriveItemEntity> =
        currentDao().getExpiredTrashedItemsSync(cutoffTimestamp)

    override fun getOfflineItemsFlow(): Flow<List<DriveItemEntity>> =
        manager.activeSlotId.flatMapLatest { slot -> currentDao(slot).getOfflineItemsFlow() }

    override fun getRecentFilesFlow(sinceTimestamp: Long): Flow<List<DriveItemEntity>> =
        manager.activeSlotId.flatMapLatest { slot -> currentDao(slot).getRecentFilesFlow(sinceTimestamp) }

    override fun searchGlobal(query: String): Flow<List<DriveItemEntity>> =
        manager.activeSlotId.flatMapLatest { slot -> currentDao(slot).searchGlobal(query) }

    override fun getItemsFlow(parentChatId: Long, virtualParentId: String): Flow<List<DriveItemEntity>> =
        manager.activeSlotId.flatMapLatest { slot -> currentDao(slot).getItemsFlow(parentChatId, virtualParentId) }

    override suspend fun getItemById(id: Long, parentChatId: Long): DriveItemEntity? =
        currentDao().getItemById(id, parentChatId)

    override suspend fun getItemsByIdsInChat(parentChatId: Long, ids: List<Long>): List<DriveItemEntity> =
        currentDao().getItemsByIdsInChat(parentChatId, ids)

    override suspend fun getVirtualFolderById(virtualId: String): DriveItemEntity? =
        currentDao().getVirtualFolderById(virtualId)

    override suspend fun getItemsByVirtualParentSync(virtualId: String): List<DriveItemEntity> =
        currentDao().getItemsByVirtualParentSync(virtualId)

    override suspend fun getItemByRemoteUniqueIdOrFileId(uniqueId: String, fileId: Int): DriveItemEntity? =
        currentDao().getItemByRemoteUniqueIdOrFileId(uniqueId, fileId)

    override suspend fun insertItems(items: List<DriveItemEntity>) =
        currentDao().insertItems(items)

    override suspend fun insertItemsPreservingStarred(items: List<DriveItemEntity>) =
        currentDao().insertItemsPreservingStarred(items)

    override suspend fun updateLocalPathByUniqueId(uniqueId: String, path: String?) =
        currentDao().updateLocalPathByUniqueId(uniqueId, path)

    override suspend fun updateThumbnailPathByUniqueId(uniqueId: String, path: String) =
        currentDao().updateThumbnailPathByUniqueId(uniqueId, path)

    override suspend fun updateFileAndThumbnailPathIfChanged(uniqueId: String, path: String): Int =
        currentDao().updateFileAndThumbnailPathIfChanged(uniqueId, path)

    override suspend fun updateFileAndThumbnailPathsBatch(updates: Map<String, String>): Int =
        currentDao().updateFileAndThumbnailPathsBatch(updates)

    override suspend fun updateStarred(id: Long, parentChatId: Long, isStarred: Boolean) =
        currentDao().updateStarred(id, parentChatId, isStarred)

    override suspend fun updateTrashState(id: Long, parentChatId: Long, isTrashed: Boolean, trashedAt: Long?) =
        currentDao().updateTrashState(id, parentChatId, isTrashed, trashedAt)

    override suspend fun updateTrashStateBySplitGroup(splitGroupId: String, parentChatId: Long, isTrashed: Boolean, trashedAt: Long?) =
        currentDao().updateTrashStateBySplitGroup(splitGroupId, parentChatId, isTrashed, trashedAt)

    override suspend fun updateVirtualFolderTrashState(virtualId: String, isTrashed: Boolean, trashedAt: Long?) =
        currentDao().updateVirtualFolderTrashState(virtualId, isTrashed, trashedAt)

    override suspend fun updateTrashStateByVirtualParent(virtualParentId: String, isTrashed: Boolean, trashedAt: Long?) =
        currentDao().updateTrashStateByVirtualParent(virtualParentId, isTrashed, trashedAt)

    override suspend fun updateTrashStateByChat(chatId: Long, isTrashed: Boolean, trashedAt: Long?) =
        currentDao().updateTrashStateByChat(chatId, isTrashed, trashedAt)

    override suspend fun updateOfflineState(id: Long, parentChatId: Long, isOffline: Boolean, offlinePath: String?) =
        currentDao().updateOfflineState(id, parentChatId, isOffline, offlinePath)

    override suspend fun updateVirtualParent(messageId: Long, virtualFolderId: String) =
        currentDao().updateVirtualParent(messageId, virtualFolderId)

    override suspend fun updateVirtualParentInChat(messageId: Long, chatId: Long, virtualFolderId: String) =
        currentDao().updateVirtualParentInChat(messageId, chatId, virtualFolderId)

    override suspend fun renameItem(id: Long, parentChatId: Long, newName: String) =
        currentDao().renameItem(id, parentChatId, newName)

    override suspend fun renameVirtualFolderByVirtualId(virtualId: String, newName: String) =
        currentDao().renameVirtualFolderByVirtualId(virtualId, newName)

    override suspend fun deleteItemCompletely(id: Long, parentChatId: Long) =
        currentDao().deleteItemCompletely(id, parentChatId)

    override suspend fun deleteItemsByChat(chatId: Long) =
        currentDao().deleteItemsByChat(chatId)

    override suspend fun getActiveFileIdsByChatSync(chatId: Long): List<Long> =
        currentDao().getActiveFileIdsByChatSync(chatId)

    override suspend fun deleteActiveFilesByIdsInChat(chatId: Long, idsToDelete: List<Long>) =
        currentDao().deleteActiveFilesByIdsInChat(chatId, idsToDelete)

    override suspend fun deleteFilesByChatNotInList(chatId: Long, keepIds: List<Long>) =
        currentDao().deleteFilesByChatNotInList(chatId, keepIds)

    override suspend fun clearAllThumbnailPaths() =
        currentDao().clearAllThumbnailPaths()

    override suspend fun clearAllLocalPaths() =
        currentDao().clearAllLocalPaths()

    override suspend fun resetAllFileVirtualParents() =
        currentDao().resetAllFileVirtualParents()

    override suspend fun cleanupZeroChatPhysicalFolders(savedChatId: Long) =
        currentDao().cleanupZeroChatPhysicalFolders(savedChatId)

    override suspend fun cleanupAllZeroChatItems(savedChatId: Long) =
        currentDao().cleanupAllZeroChatItems(savedChatId)

    override suspend fun getRecoveredFolderVirtualIds(): List<String> =
        currentDao().getRecoveredFolderVirtualIds()

    override suspend fun deleteRecoveredFolders() =
        currentDao().deleteRecoveredFolders()

    override suspend fun cleanupRecoveredFoldersAndRescueChildren(): List<String> =
        currentDao().cleanupRecoveredFoldersAndRescueChildren()

    override suspend fun updateVirtualParentByOldParent(oldParentId: String, newParentId: String) =
        currentDao().updateVirtualParentByOldParent(oldParentId, newParentId)

    override suspend fun getAllVirtualFoldersSync(): List<DriveItemEntity> =
        currentDao().getAllVirtualFoldersSync()

    override suspend fun deleteAllVirtualFolders() =
        currentDao().deleteAllVirtualFolders()

    override suspend fun syncVirtualFolders(entities: List<DriveItemEntity>) =
        currentDao().syncVirtualFolders(entities)

    override fun getCloudFileTypeStats(): Flow<List<DriveDao.FileTypeStat>> =
        manager.activeSlotId.flatMapLatest { slot -> currentDao(slot).getCloudFileTypeStats() }
}

@OptIn(ExperimentalCoroutinesApi::class)
private class SwitchableTransferDao(
    private val manager: AccountDatabaseManager
) : TransferDao {

    private fun currentDao(slotId: String = manager.activeSlotId.value): TransferDao =
        manager.getDatabase(slotId).transferDao()

    override fun getAllTransfersFlow(): Flow<List<TransferEntity>> =
        manager.activeSlotId.flatMapLatest { slot -> currentDao(slot).getAllTransfersFlow() }

    override suspend fun getTransferByUniqueId(uniqueId: String): TransferEntity? =
        currentDao().getTransferByUniqueId(uniqueId)

    override suspend fun getTransferByFileId(fileId: Int): TransferEntity? =
        currentDao().getTransferByFileId(fileId)

    override suspend fun getTransferByLocalPath(path: String): TransferEntity? =
        currentDao().getTransferByLocalPath(path)

    override suspend fun insertTransfer(transfer: TransferEntity) =
        currentDao().insertTransfer(transfer)

    override suspend fun deleteTransfer(uniqueId: String) =
        currentDao().deleteTransfer(uniqueId)

    override suspend fun clearCompleted() =
        currentDao().clearCompleted()

    override suspend fun clearAll() =
        currentDao().clearAll()

    override suspend fun clearStaleDownloads() =
        currentDao().clearStaleDownloads()

    override suspend fun updateProgress(uniqueId: String, progress: Float, status: String, downloadedSize: Long) =
        currentDao().updateProgress(uniqueId, progress, status, downloadedSize)

    override suspend fun updateProgressOnly(uniqueId: String, progress: Float, status: String) =
        currentDao().updateProgressOnly(uniqueId, progress, status)

    override suspend fun updateProgressFull(uniqueId: String, progress: Float, status: String, size: Long) =
        currentDao().updateProgressFull(uniqueId, progress, status, size)

    override suspend fun updateUniqueId(oldUniqueId: String, newUniqueId: String) =
        currentDao().updateUniqueId(oldUniqueId, newUniqueId)

    override suspend fun updateFileId(uniqueId: String, fileId: Int) =
        currentDao().updateFileId(uniqueId, fileId)

    override suspend fun updateLocalPath(uniqueId: String, path: String) =
        currentDao().updateLocalPath(uniqueId, path)
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(accountDatabaseManager: AccountDatabaseManager): TeleDriveDatabase {
        return accountDatabaseManager.getDatabase()
    }

    @Provides
    @Singleton
    fun provideDriveDao(accountDatabaseManager: AccountDatabaseManager): DriveDao {
        return SwitchableDriveDao(accountDatabaseManager)
    }

    @Provides
    @Singleton
    fun provideTransferDao(accountDatabaseManager: AccountDatabaseManager): TransferDao {
        return SwitchableTransferDao(accountDatabaseManager)
    }
}
