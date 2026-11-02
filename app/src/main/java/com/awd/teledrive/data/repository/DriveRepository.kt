package com.awd.teledrive.data.repository

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.awd.teledrive.R
import com.awd.teledrive.core.utils.FileUtils
import com.awd.teledrive.data.local.DriveDao
import com.awd.teledrive.data.local.DriveItemEntity
import com.awd.teledrive.data.remote.TelegramClient
import com.awd.teledrive.data.secure.EncryptionManager
import com.awd.teledrive.data.secure.MasterPasswordService
import com.awd.teledrive.data.secure.SecureSessionManager
import com.awd.teledrive.domain.model.CloudBackup
import com.awd.teledrive.domain.model.DriveItem
import com.awd.teledrive.domain.model.FileMetadata
import com.awd.teledrive.domain.model.MoveCopyDestination
import com.awd.teledrive.domain.model.SplitFileMaster
import com.awd.teledrive.domain.model.TeleDriveManifest
import com.awd.teledrive.domain.model.VirtualFolder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import org.drinkless.tdlib.TdApi
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class DriveRepository @Inject constructor(
    private val telegramClient: TelegramClient,
    private val driveDao: DriveDao,
    private val encryptionManager: EncryptionManager,
    private val masterPasswordService: MasterPasswordService,
    private val secureSessionManager: SecureSessionManager,
    private val transferRepository: TransferRepository,
    private val settingsRepository: SettingsRepository,
    private val accountDatabaseManager: com.awd.teledrive.di.AccountDatabaseManager,
    @param:ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var savedMessagesChatId: Long = 0
    private val _savedMessagesChatIdFlow = MutableStateFlow(0L)
    fun getSavedMessagesChatIdFlow(): Flow<Long> = _savedMessagesChatIdFlow.asStateFlow()

    private val MANIFEST_PREFIX = "#TELEDRIVE_MANIFEST"
    private val MANIFEST_BACKUP_PREFIX = "#TELEDRIVE_MANIFEST_BACKUP_"
    private val VFOLDER_META_PREFIX = "#TELEDRIVE_VFOLDER_DATA"
    private val FILE_META_PREFIX = "#TELEDRIVE_FILE_DATA"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var currentManifest = TeleDriveManifest()
    private var manifestMessageId: Long = 0
    private val manifestPrefs: android.content.SharedPreferences
        get() {
            val slot = accountDatabaseManager.activeSlotId.value.ifBlank { "default" }
            val prefName = if (slot == "default") "teledrive_manifest_store" else "teledrive_manifest_store_$slot"
            return context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
        }
    private val _folderColorVersion = MutableStateFlow(0L)
    private data class PendingSendMeta(
        val fileName: String,
        val virtualFolderId: String,
        val isEncrypted: Boolean,
        val thumbnailPath: String?,
        val localPath: String?,
        val handledByCaller: Boolean = false,
        val originalTimestamp: Long = 0L
    )

    private val pendingMessageSends = java.util.concurrent.ConcurrentHashMap<Long, kotlinx.coroutines.CompletableDeferred<TdApi.Message?>>()
    private val completedMessageSends = java.util.concurrent.ConcurrentHashMap<Long, TdApi.Message>()
    private val failedMessageSends = java.util.concurrent.ConcurrentHashMap<Long, Boolean>()
    private val pendingSendMetaMap = java.util.concurrent.ConcurrentHashMap<Long, PendingSendMeta>()
    private val recentlyCreatedMessageIds = java.util.concurrent.ConcurrentHashMap<Long, Long>()
    private val pendingOfflineDownloads = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Long>>() // remoteUniqueId -> (messageId, chatId)
    private val completedFilePathCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val pendingPathUpdates = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val pathFlushMutex = kotlinx.coroutines.sync.Mutex()
    @Volatile
    private var pathFlushJob: kotlinx.coroutines.Job? = null
    private val folderColorCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val offlineFileExistsCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Boolean>>()
    private val lastChatFetchTimeMs = java.util.concurrent.ConcurrentHashMap<Long, Long>()
    @Volatile
    private var lastFullSyncTimeMs: Long = 0L
    @Volatile
    private var virtualFolderSyncJob: kotlinx.coroutines.Job? = null
    private val liveFileUploadBytes = java.util.concurrent.ConcurrentHashMap<Int, Pair<Long, Long>>() // fileId -> (uploaded, total)
    private val liveFileDownloadBytes = java.util.concurrent.ConcurrentHashMap<Int, Pair<Long, Long>>() // fileId -> (downloaded, total)
    private val activeSyncUploads = java.util.concurrent.ConcurrentHashMap<String, Triple<Long, Long, Int>>() // syncKey -> (chatId, tempMsgId, tdFileId)
    private val activeSyncDownloadFileIds = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()

    companion object {
        const val TRASH_RETENTION_MS = 30L * 24 * 60 * 60 * 1000L // 30 Days
        const val RECENT_WINDOW_MS = 7L * 24 * 60 * 60 * 1000L   // 7 Days
    }

    private fun schedulePathUpdatesFlush() {
        if (pathFlushJob?.isActive == true) return
        pathFlushJob = scope.launch {
            kotlinx.coroutines.delay(180L)
            flushPendingPathUpdatesNow()
        }
    }

    private suspend fun flushPendingPathUpdatesNow() {
        if (pendingPathUpdates.isEmpty()) return
        pathFlushMutex.lock()
        try {
            if (pendingPathUpdates.isEmpty()) return
            val snapshot = HashMap(pendingPathUpdates)
            snapshot.keys.forEach { key -> pendingPathUpdates.remove(key, snapshot[key]) }
            if (snapshot.isNotEmpty()) {
                driveDao.updateFileAndThumbnailPathsBatch(snapshot)
            }
        } catch (e: Exception) {
            Log.w("DriveRepo", "Batch path update error: ${e.message}")
        } finally {
            pathFlushMutex.unlock()
        }
    }

    init {
        loadLocalManifest()
        scope.launch {
            cleanupRecoveredFoldersInDb()
            syncVirtualFoldersToDb()
            reconcileTrashStatesInDb()
            purgeExpiredTrashItems()
        }
        scope.launch {
            var previousSlot = accountDatabaseManager.activeSlotId.value
            accountDatabaseManager.activeSlotId.collect { newSlot ->
                if (newSlot != previousSlot) {
                    previousSlot = newSlot
                    onAccountSlotSwitched()
                }
            }
        }
        scope.launch {
            telegramClient.fileUpdates.collect { update ->
                val file = update.file
                val uniqueId = file.remote.uniqueId
                val path = file.local.path
                val totalExpected = maxOf(file.expectedSize, file.size)
                if (file.remote.isUploadingActive || file.remote.isUploadingCompleted || file.remote.uploadedSize > 0L) {
                    liveFileUploadBytes[file.id] = file.remote.uploadedSize to totalExpected
                }
                if (file.local.isDownloadingActive || file.local.isDownloadingCompleted || file.local.downloadedSize > 0L) {
                    liveFileDownloadBytes[file.id] = file.local.downloadedSize to totalExpected
                }
                if (file.local.isDownloadingCompleted && uniqueId.isNotEmpty() && path.isNotEmpty()) {
                    if (completedFilePathCache[uniqueId] != path) {
                        completedFilePathCache[uniqueId] = path
                        pendingPathUpdates[uniqueId] = path
                        schedulePathUpdatesFlush()
                    }
                    val pendingOffline = pendingOfflineDownloads.remove(uniqueId)
                    if (pendingOffline != null) {
                        val (msgId, chatId) = pendingOffline
                        copyToOfflineStorage(msgId, chatId, path)
                    }
                }
            }
        }
        scope.launch {
            telegramClient.updates.collect { update ->
                when (update) {
                    is TdApi.UpdateMessageSendSucceeded -> {
                        val msg = update.message
                        val oldId = update.oldMessageId
                        val sendMeta = pendingSendMetaMap.remove(oldId)
                        val previousPendingEntity = driveDao.getItemById(oldId, msg.chatId)
                        driveDao.deleteItemCompletely(oldId, msg.chatId)

                        if (sendMeta != null) {
                            recentlyCreatedMessageIds[msg.id] = System.currentTimeMillis()
                            pendingSendMetaMap[msg.id] = sendMeta
                            saveLocalFileRename(msg.id, msg.chatId, sendMeta.fileName)
                            synchronized(currentManifest) {
                                val updatedMappings = currentManifest.fileMappings.toMutableMap()
                                updatedMappings.remove(oldId.toString())
                                updatedMappings[msg.id.toString()] = sendMeta.virtualFolderId
                                currentManifest = currentManifest.copy(
                                    fileMappings = updatedMappings,
                                    updatedAt = System.currentTimeMillis() / 1000
                                )
                                saveLocalManifest()
                            }
                        }

                        val mappedEntity = mapMessageToEntity(msg.chatId, msg)
                        if (mappedEntity != null) {
                            val resolvedVfId = sendMeta?.virtualFolderId
                                ?: previousPendingEntity?.virtualParentId
                                ?: mappedEntity.virtualParentId
                                ?: "0"
                            val resolvedName = sendMeta?.fileName?.takeIf { it.isNotBlank() }
                                ?: previousPendingEntity?.name?.takeIf { it.isNotBlank() }
                                ?: mappedEntity.name
                            val resolvedEncrypted = (sendMeta?.isEncrypted == true) ||
                                (previousPendingEntity?.isEncrypted == true) ||
                                mappedEntity.isEncrypted
                            val resolvedOrigTime = sendMeta?.originalTimestamp?.takeIf { it > 0L }
                                ?: previousPendingEntity?.createdAt?.takeIf { it > 0L }
                                ?: mappedEntity.createdAt
                            saveLocalFileRename(msg.id, msg.chatId, resolvedName)
                            saveLocalFileVirtualParent(msg.id, msg.chatId, resolvedVfId)
                            if (resolvedOrigTime > 0L) {
                                saveLocalFileOriginalTime(msg.id, msg.chatId, resolvedOrigTime)
                            }
                            val finalEntity = mappedEntity.copy(
                                name = resolvedName,
                                virtualParentId = resolvedVfId,
                                isEncrypted = resolvedEncrypted,
                                createdAt = resolvedOrigTime,
                                thumbnailPath = mappedEntity.thumbnailPath ?: sendMeta?.thumbnailPath ?: previousPendingEntity?.thumbnailPath,
                                localPath = mappedEntity.localPath ?: sendMeta?.localPath ?: previousPendingEntity?.localPath
                            )
                            driveDao.insertItemsPreservingStarred(listOf(finalEntity))
                            recentlyCreatedMessageIds[msg.id] = System.currentTimeMillis()
                            if (sendMeta?.handledByCaller != true && msg.chatId == savedMessagesChatId && savedMessagesChatId != 0L && finalEntity.splitGroupId == null && !finalEntity.isFolder) {
                                synchronized(currentManifest) {
                                    val updatedMappings = currentManifest.fileMappings.toMutableMap()
                                    updatedMappings.remove(oldId.toString())
                                    updatedMappings[msg.id.toString()] = resolvedVfId
                                    currentManifest = currentManifest.copy(fileMappings = updatedMappings)
                                    saveLocalManifest()
                                }
                                sendFileMetadata(msg.id, finalEntity.name, resolvedVfId, finalEntity.isEncrypted, finalEntity.createdAt)
                            }
                        }

                        completedMessageSends[oldId] = msg
                        pendingMessageSends.remove(oldId)?.complete(msg)
                    }
                    is TdApi.UpdateMessageSendFailed -> {
                        failedMessageSends[update.oldMessageId] = true
                        pendingSendMetaMap.remove(update.oldMessageId)
                        pendingMessageSends.remove(update.oldMessageId)?.complete(null)
                    }
                    is TdApi.UpdateAuthorizationState -> {
                        if (update.authorizationState is TdApi.AuthorizationStateReady) {
                            fetchFiles(0L)
                        }
                    }
                }
            }
        }
    }

    private fun onAccountSlotSwitched() {
        synchronized(this) {
            savedMessagesChatId = 0L
            _savedMessagesChatIdFlow.value = 0L
            currentManifest = TeleDriveManifest()
            manifestMessageId = 0L
            completedFilePathCache.clear()
            pendingPathUpdates.clear()
            folderColorCache.clear()
            offlineFileExistsCache.clear()
            lastChatFetchTimeMs.clear()
            lastFullSyncTimeMs = 0L
            preloadedChannelIds.clear()
            pendingOfflineDownloads.clear()
            pendingSendMetaMap.clear()
            recentlyCreatedMessageIds.clear()
            loadLocalManifest()
            _folderColorVersion.value = System.currentTimeMillis()
        }
        scope.launch {
            cleanupRecoveredFoldersInDb()
            syncVirtualFoldersToDb()
            reconcileTrashStatesInDb()
            purgeExpiredTrashItems()
            telegramClient.send(TdApi.GetAuthorizationState()) { state ->
                if (state is TdApi.AuthorizationStateReady) {
                    fetchFiles(0L, forceRefresh = true)
                }
            }
        }
    }

    private fun loadLocalManifest() {
        try {
            val cachedSavedChatId = manifestPrefs.getLong("saved_messages_chat_id", 0L)
            if (cachedSavedChatId != 0L) {
                savedMessagesChatId = cachedSavedChatId
                _savedMessagesChatIdFlow.value = cachedSavedChatId
            } else {
                savedMessagesChatId = 0L
                _savedMessagesChatIdFlow.value = 0L
            }
            manifestMessageId = manifestPrefs.getLong("manifest_message_id", 0L)
            val rawJson = manifestPrefs.getString("manifest_json", null)
            if (!rawJson.isNullOrBlank()) {
                val decoded = json.decodeFromString<TeleDriveManifest>(rawJson)
                currentManifest = sanitizeManifest(applyLocalRenames(decoded))
            } else {
                currentManifest = TeleDriveManifest()
            }
        } catch (e: Exception) {
            Log.e("DriveRepo", "Failed to load local manifest: ${e.message}")
        }
    }

    private fun saveLocalManifest() {
        try {
            val encoded = json.encodeToString(currentManifest)
            val editor = manifestPrefs.edit()
                .putString("manifest_json", encoded)
                .putLong("manifest_message_id", manifestMessageId)
            if (savedMessagesChatId != 0L) {
                editor.putLong("saved_messages_chat_id", savedMessagesChatId)
            }
            editor.apply()
        } catch (e: Exception) {
            Log.e("DriveRepo", "Failed to save local manifest: ${e.message}")
        }
    }

    private fun saveLocalRename(virtualId: String, newName: String, timestampSec: Long) {
        manifestPrefs.edit()
            .putString("rename_$virtualId", "$timestampSec|$newName")
            .apply()
    }

    private fun saveLocalFileRename(messageId: Long, chatId: Long, newName: String) {
        if (newName.isBlank()) return
        manifestPrefs.edit()
            .putString("file_rename_${chatId}_$messageId", newName)
            .putString("file_rename_0_$messageId", newName)
            .apply()
    }

    private fun getLocalFileRename(messageId: Long, chatId: Long): String? {
        return manifestPrefs.getString("file_rename_${chatId}_$messageId", null)?.takeIf { it.isNotBlank() }
            ?: manifestPrefs.getString("file_rename_0_$messageId", null)?.takeIf { it.isNotBlank() }
    }

    private fun saveLocalFileVirtualParent(messageId: Long, chatId: Long, virtualFolderId: String) {
        val cleanVf = virtualFolderId.ifBlank { "0" }
        manifestPrefs.edit()
            .putString("file_vf_${chatId}_$messageId", cleanVf)
            .putString("file_vf_0_$messageId", cleanVf)
            .apply()
    }

    private fun getLocalFileVirtualParent(messageId: Long, chatId: Long = 0L): String? {
        return manifestPrefs.getString("file_vf_${chatId}_$messageId", null)?.takeIf { it.isNotBlank() }
            ?: manifestPrefs.getString("file_vf_0_$messageId", null)?.takeIf { it.isNotBlank() }
    }

    private fun saveLocalFileOriginalTime(messageId: Long, chatId: Long, timestampMs: Long) {
        if (timestampMs <= 0L) return
        manifestPrefs.edit()
            .putLong("file_otime_${chatId}_$messageId", timestampMs)
            .putLong("file_otime_0_$messageId", timestampMs)
            .apply()
    }

    private fun getLocalFileOriginalTime(messageId: Long, chatId: Long = 0L): Long? {
        val t1 = manifestPrefs.getLong("file_otime_${chatId}_$messageId", 0L)
        if (t1 > 0L) return t1
        val t2 = manifestPrefs.getLong("file_otime_0_$messageId", 0L)
        return if (t2 > 0L) t2 else null
    }

    private fun resolveOriginalTimestamp(messageId: Long, chatId: Long, caption: String, fallbackMsgDateSec: Int): Long {
        pendingSendMetaMap[messageId]?.originalTimestamp?.takeIf { it > 0L }?.let { return it }
        getLocalFileOriginalTime(messageId, chatId)?.let { return it }
        parseOriginalTimestampTag(caption)?.takeIf { it > 0L }?.let { ts ->
            saveLocalFileOriginalTime(messageId, chatId, ts)
            return ts
        }
        return fallbackMsgDateSec.toLong() * 1000L
    }

    private fun resolveFileName(
        messageId: Long,
        chatId: Long,
        caption: String,
        rawTelegramFileName: String,
        splitOriginalName: String?,
        fallbackName: String
    ): String {
        getLocalFileRename(messageId, chatId)?.let { return it }
        if (!splitOriginalName.isNullOrBlank()) {
            return cleanEncryptedFileName(splitOriginalName)
        }
        val captionCleanName = caption
            .replace(Regex("\\[VF:.*?\\]"), "")
            .replace(Regex("\\[OTIME:\\d+\\]"), "")
            .replace("[ENC]", "")
            .trim()
        if (captionCleanName.isNotBlank() && !captionCleanName.startsWith("[TD_SPLIT")) {
            return cleanEncryptedFileName(captionCleanName)
        }
        if (rawTelegramFileName.isNotBlank()) {
            return cleanEncryptedFileName(rawTelegramFileName)
        }
        return fallbackName
    }

    private fun applyLocalRenames(manifest: TeleDriveManifest): TeleDriveManifest {
        val allEntries = manifestPrefs.all
        val updatedFolders = manifest.virtualFolders.toMutableMap()
        var changed = false
        allEntries.forEach { (key, value) ->
            if (key.startsWith("rename_") && value is String) {
                val virtualId = key.removePrefix("rename_")
                val sepIdx = value.indexOf('|')
                if (sepIdx != -1) {
                    val renamedAt = value.substring(0, sepIdx).toLongOrNull() ?: 0L
                    val newName = value.substring(sepIdx + 1)
                    val existing = updatedFolders[virtualId]
                    if (existing != null && newName.isNotBlank()) {
                        val existingTime = maxOf(existing.updatedAt, existing.createdAt)
                        if (renamedAt >= existingTime && (existing.name != newName || existing.updatedAt < renamedAt)) {
                            updatedFolders[virtualId] = existing.copy(name = newName, updatedAt = renamedAt)
                            changed = true
                        }
                    }
                }
            } else if (key.startsWith("folder_color_") && value is String) {
                val folderKey = key.removePrefix("folder_color_")
                val existing = updatedFolders[folderKey]
                if (existing != null && existing.colorHex != value) {
                    updatedFolders[folderKey] = existing.copy(colorHex = value.ifBlank { null })
                    changed = true
                }
            }
        }
        return if (changed) manifest.copy(virtualFolders = updatedFolders) else manifest
    }

    private fun getResolvedFolderColor(isVirtual: Boolean, virtualId: String?, telegramChatId: Long): String? {
        val key = if (isVirtual) (virtualId ?: telegramChatId.toString()) else telegramChatId.toString()
        folderColorCache[key]?.let { cached ->
            return cached.takeIf { it.isNotEmpty() }
        }
        val localPref = manifestPrefs.getString("folder_color_$key", null)
        val resolved = if (localPref != null) {
            localPref.takeIf { it.isNotBlank() }
        } else if (isVirtual && virtualId != null) {
            currentManifest.virtualFolders[virtualId]?.colorHex?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        folderColorCache[key] = resolved ?: ""
        return resolved
    }

    fun setFolderColor(folder: DriveItem.Folder, colorHex: String?) {
        val cleanColor = colorHex?.trim()?.takeIf { it.isNotEmpty() }
        val key = if (folder.isVirtual) (folder.virtualId ?: folder.id.toString()) else folder.telegramChatId.toString()
        folderColorCache[key] = cleanColor ?: ""
        manifestPrefs.edit()
            .putString("folder_color_$key", cleanColor ?: "")
            .apply()

        if (folder.isVirtual && folder.virtualId != null) {
            val vid = folder.virtualId
            val nowSec = System.currentTimeMillis() / 1000
            var updatedFolder: VirtualFolder? = null
            var metaMsgId: Long? = null
            synchronized(currentManifest) {
                val existing = currentManifest.virtualFolders[vid]
                if (existing != null) {
                    val uf = existing.copy(colorHex = cleanColor, updatedAt = nowSec)
                    updatedFolder = uf
                    val updatedMap = currentManifest.virtualFolders.toMutableMap()
                    updatedMap[vid] = uf
                    currentManifest = currentManifest.copy(virtualFolders = updatedMap, updatedAt = nowSec)
                    metaMsgId = currentManifest.folderMetadataIds[vid]
                    saveLocalManifest()
                }
            }
            saveCloudManifest()
            if (updatedFolder != null && savedMessagesChatId != 0L) {
                val metaJson = json.encodeToString(updatedFolder)
                val text = "$VFOLDER_META_PREFIX\n\n$metaJson"
                val formatted = TdApi.FormattedText(text, emptyArray())
                if (metaMsgId != null && metaMsgId != 0L) {
                    telegramClient.send(
                        TdApi.EditMessageText(
                            savedMessagesChatId,
                            metaMsgId!!,
                            null,
                            TdApi.InputMessageText(formatted, null, true)
                        )
                    ) {}
                }
            }
        }
        _folderColorVersion.value = System.currentTimeMillis()
    }

    private suspend fun cleanupRecoveredFoldersInDb() {
        try {
            val recoveredIds = driveDao.cleanupRecoveredFoldersAndRescueChildren()
            if (recoveredIds.isNotEmpty()) {
                synchronized(currentManifest) {
                    val nowSec = System.currentTimeMillis() / 1000
                    val updatedDeleted = currentManifest.deletedFolderIds.toMutableMap()
                    val metadataToDelete = mutableListOf<Long>()
                    recoveredIds.forEach { id ->
                        updatedDeleted[id] = nowSec
                        currentManifest.folderMetadataIds[id]?.let { msgId ->
                            if (msgId != 0L) metadataToDelete.add(msgId)
                        }
                    }
                    currentManifest = sanitizeManifest(currentManifest.copy(deletedFolderIds = updatedDeleted))
                    saveLocalManifest()
                    if (metadataToDelete.isNotEmpty() && savedMessagesChatId != 0L) {
                        telegramClient.send(TdApi.DeleteMessages(savedMessagesChatId, metadataToDelete.toLongArray(), true)) {}
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("DriveRepo", "Error cleaning up recovered folders: ${e.message}")
        }
    }

    fun fetchCloudManifest(onComplete: () -> Unit = {}) {
        if (savedMessagesChatId == 0L) {
            onComplete()
            return
        }
        
        val allMessages = mutableListOf<TdApi.Message>()
        var searchDone = false
        var historyDone = false

        fun tryFinish() {
            if (searchDone && historyDone) {
                processManifestMessages(allMessages.distinctBy { it.id })
                onComplete()
            }
        }

        // 1. Search for manifests (indexed search)
        val searchRequest = TdApi.SearchChatMessages()
        searchRequest.chatId = savedMessagesChatId
        searchRequest.query = MANIFEST_PREFIX
        searchRequest.limit = 100
        
        telegramClient.send(searchRequest) { result ->
            if (result is TdApi.Messages) {
                synchronized(allMessages) { allMessages.addAll(result.messages.toList()) }
            }
            searchDone = true
            tryFinish()
        }

        // 2. Direct history scan (mirrors Desktop version)
        telegramClient.send(TdApi.GetChatHistory(savedMessagesChatId, 0, 0, 100, false)) { result ->
            if (result is TdApi.Messages) {
                synchronized(allMessages) { allMessages.addAll(result.messages.toList()) }
            }
            historyDone = true
            tryFinish()
        }
    }

    private fun processManifestMessages(messages: List<TdApi.Message>) {
        val possibleManifests = messages.filter { msg ->
            val content = msg.content
            if (content is TdApi.MessageText) {
                val text = content.text.text
                // Only use primary #TELEDRIVE_MANIFEST messages (not manual backups) for live sync
                !text.contains(MANIFEST_BACKUP_PREFIX) && (
                    text.contains(MANIFEST_PREFIX) ||
                    (!text.startsWith(VFOLDER_META_PREFIX) && !text.startsWith(FILE_META_PREFIX) && text.contains("\"virtualFolders\""))
                )
            } else false
        }
        
        if (possibleManifests.isEmpty()) {
            // Always sanitize and sync local manifest + clean up Recovered_ folders even if no manifest message in first batch
            currentManifest = sanitizeManifest(applyLocalRenames(currentManifest))
            saveLocalManifest()
            scope.launch {
                cleanupRecoveredFoldersInDb()
                syncVirtualFoldersToDb()
            }
            return
        }
        
        // Pick the newest prefix-based manifest as primary for ID tracking
        val discoveredId = possibleManifests
            .filter { (it.content as? TdApi.MessageText)?.text?.text?.contains(MANIFEST_PREFIX) == true }
            .maxByOrNull { it.date }?.id ?: 0L
        if (discoveredId != 0L) {
            manifestMessageId = discoveredId
        }
        
        var mergedManifest = currentManifest
        // Sort by date ASC to ensure newer data overwrites older data correctly
        possibleManifests.sortedBy { it.date }.forEach { msg ->
            val content = msg.content
            if (content is TdApi.MessageText) {
                val text = content.text.text
                val jsonStartIndex = text.indexOf('{')
                if (jsonStartIndex != -1) {
                    try {
                        val m = json.decodeFromString<TeleDriveManifest>(text.substring(jsonStartIndex).trim())
                        mergedManifest = mergeManifests(mergedManifest, m)
                    } catch (e: Exception) { 
                        Log.e("DriveRepo", "Error decoding manifest: ${e.message}")
                    }
                }
            }
        }
        
        val sanitized = sanitizeManifest(applyLocalRenames(mergedManifest))
        val hadRecoveredFolders = sanitized != mergedManifest
        currentManifest = sanitized
        saveLocalManifest()
        scope.launch {
            cleanupRecoveredFoldersInDb()
            syncVirtualFoldersToDb()
        }
        if (hadRecoveredFolders) {
            saveCloudManifest()
        }
        currentManifest.secureFolderChatIds.forEach { chatId -> loadAllDriveItems(chatId) }
    }

    private fun sanitizeManifest(manifest: TeleDriveManifest): TeleDriveManifest {
        val recoveredIds = manifest.virtualFolders.values
            .filter { it.name.startsWith("Recovered_") }
            .map { it.id }
            .toSet()

        val updatedDeletedIds = manifest.deletedFolderIds.toMutableMap()
        val nowSec = System.currentTimeMillis() / 1000
        recoveredIds.forEach { id ->
            updatedDeletedIds[id] = nowSec
        }

        val cleanFolders = mutableMapOf<String, VirtualFolder>()
        manifest.virtualFolders.forEach { (id, vf) ->
            val deletedAt = updatedDeletedIds[id]
            val isDeleted = deletedAt != null && vf.createdAt <= deletedAt
            if (!isDeleted && !vf.name.startsWith("Recovered_")) {
                cleanFolders[id] = vf
            }
        }

        // Restore any folder whose parentId was a Recovered_ folder back to root "0"
        val finalFolders = cleanFolders.mapValues { (_, vf) ->
            if (vf.parentId.startsWith("vf_") && vf.parentId in recoveredIds) {
                vf.copy(parentId = "0")
            } else {
                vf
            }
        }

        val cleanMappings = manifest.fileMappings.mapValues { (_, targetVfId) ->
            if (targetVfId in recoveredIds) "0" else targetVfId
        }

        return manifest.copy(
            virtualFolders = finalFolders,
            fileMappings = cleanMappings,
            deletedFolderIds = updatedDeletedIds
        )
    }

    private fun mergeManifests(base: TeleDriveManifest, other: TeleDriveManifest): TeleDriveManifest {
        val mergedDeletedIds = base.deletedFolderIds.toMutableMap()
        other.deletedFolderIds.forEach { (id, delTime) ->
            val existingDel = mergedDeletedIds[id] ?: 0L
            if (delTime > existingDel) mergedDeletedIds[id] = delTime
        }

        val mergedFolders = base.virtualFolders.toMutableMap()
        other.virtualFolders.forEach { (id, vf) ->
            if (vf.name.startsWith("Recovered_")) {
                mergedDeletedIds[id] = maxOf(mergedDeletedIds[id] ?: 0L, System.currentTimeMillis() / 1000)
                return@forEach
            }
            val deletedAt = mergedDeletedIds[id]
            if (deletedAt == null || vf.createdAt > deletedAt) {
                val existing = mergedFolders[id]
                val vfTime = maxOf(vf.updatedAt, vf.createdAt)
                val existingTime = if (existing != null) maxOf(existing.updatedAt, existing.createdAt) else -1L
                if (existing == null || vfTime > existingTime) {
                    mergedFolders[id] = vf
                }
            }
        }
        mergedFolders.keys.removeIf { id ->
            val folder = mergedFolders[id]
            val deletedAt = mergedDeletedIds[id]
            folder?.name?.startsWith("Recovered_") == true ||
                (deletedAt != null && (folder?.createdAt ?: 0L) <= deletedAt)
        }
        
        val otherIsNewer = other.updatedAt >= base.updatedAt
        val mergedMappings = if (otherIsNewer) {
            base.fileMappings.toMutableMap().apply { putAll(other.fileMappings) }
        } else {
            other.fileMappings.toMutableMap().apply { putAll(base.fileMappings) }
        }
        pendingSendMetaMap.forEach { (msgId, meta) ->
            mergedMappings[msgId.toString()] = meta.virtualFolderId
        }
        
        val mergedSecureIds = base.secureFolderChatIds.toMutableSet()
        mergedSecureIds.addAll(other.secureFolderChatIds)
        
        val mergedSplitMasters = base.splitFileMasters.toMutableMap()
        mergedSplitMasters.putAll(other.splitFileMasters)

        val mergedFolderMetaIds = if (otherIsNewer) {
            base.folderMetadataIds.toMutableMap().apply { putAll(other.folderMetadataIds) }
        } else {
            other.folderMetadataIds.toMutableMap().apply { putAll(base.folderMetadataIds) }
        }

        val mergedFileMetaIds = if (otherIsNewer) {
            base.fileMetadataIds.toMutableMap().apply { putAll(other.fileMetadataIds) }
        } else {
            other.fileMetadataIds.toMutableMap().apply { putAll(base.fileMetadataIds) }
        }

        val mergedTrashedFiles = if (otherIsNewer) {
            other.trashedFileIds.toMutableMap()
        } else {
            base.trashedFileIds.toMutableMap()
        }

        val mergedTrashedFolders = if (otherIsNewer) {
            other.trashedFolderIds.toMutableMap()
        } else {
            base.trashedFolderIds.toMutableMap()
        }
        
        return sanitizeManifest(
            applyLocalRenames(
                base.copy(
                    updatedAt = maxOf(base.updatedAt, other.updatedAt),
                    virtualFolders = mergedFolders,
                    fileMappings = mergedMappings,
                    secureFolderChatIds = mergedSecureIds,
                    splitFileMasters = mergedSplitMasters,
                    folderMetadataIds = mergedFolderMetaIds,
                    fileMetadataIds = mergedFileMetaIds,
                    deletedFolderIds = mergedDeletedIds,
                    trashedFileIds = mergedTrashedFiles,
                    trashedFolderIds = mergedTrashedFolders
                )
            )
        )
    }

    fun exportManifestToJson(): String = json.encodeToString(currentManifest)

    fun importManifestFromJson(jsonStr: String): Boolean {
        return try {
            val imported = json.decodeFromString<TeleDriveManifest>(jsonStr)
            currentManifest = imported
            saveCloudManifest()
            syncVirtualFoldersToDb()
            // Reset file parents and re-sync to apply imported mappings
            scope.launch {
                driveDao.resetAllFileVirtualParents()
                fetchFiles()
            }
            true
        } catch (e: Exception) { false }
    }

    fun fetchCloudBackups(onResult: (List<CloudBackup>) -> Unit) {
        if (savedMessagesChatId == 0L) {
            onResult(emptyList())
            return
        }
        val searchRequest = TdApi.SearchChatMessages()
        searchRequest.chatId = savedMessagesChatId
        searchRequest.query = MANIFEST_BACKUP_PREFIX
        searchRequest.limit = 50
        
        telegramClient.send(searchRequest) { result ->
            if (result is TdApi.Messages) {
                val backups = result.messages.mapNotNull { msg ->
                    val content = msg.content
                    if (content is TdApi.MessageText && content.text.text.startsWith(MANIFEST_BACKUP_PREFIX)) {
                        val text = content.text.text
                        val jsonStartIndex = text.indexOf('{')
                        if (jsonStartIndex != -1) {
                            try {
                                val manifest = json.decodeFromString<TeleDriveManifest>(text.substring(jsonStartIndex))
                                CloudBackup(msg.id, msg.date.toLong() * 1000, manifest.virtualFolders.size)
                            } catch (e: Exception) { null }
                        } else null
                    } else null
                }
                onResult(backups)
            } else {
                onResult(emptyList())
            }
        }
    }

    fun restoreManifestFromMessage(messageId: Long, onResult: (Boolean) -> Unit) {
        telegramClient.send(TdApi.GetMessage(savedMessagesChatId, messageId)) { result ->
            if (result is TdApi.Message) {
                val content = result.content
                if (content is TdApi.MessageText) {
                    val text = content.text.text
                    val jsonStartIndex = text.indexOf('{')
                    if (jsonStartIndex != -1) {
                        try {
                            val imported = json.decodeFromString<TeleDriveManifest>(text.substring(jsonStartIndex))
                            currentManifest = imported
                            saveCloudManifest()
                            syncVirtualFoldersToDb()
                            scope.launch {
                                driveDao.resetAllFileVirtualParents()
                                fetchFiles()
                            }
                            onResult(true)
                        } catch (e: Exception) { onResult(false) }
                    } else onResult(false)
                } else onResult(false)
            } else onResult(false)
        }
    }

    fun createManualCloudBackup() {
        if (savedMessagesChatId == 0L) return
        val manifestJson = json.encodeToString(currentManifest)
        val text = "$MANIFEST_BACKUP_PREFIX${System.currentTimeMillis()}\n\n$manifestJson"
        val formatted = TdApi.FormattedText(text, emptyArray())
        telegramClient.send(TdApi.SendMessage(savedMessagesChatId, null, null, null, null, TdApi.InputMessageText(formatted, null, true)))
    }

    private fun resolveTargetChatForVirtualParent(parentId: String): Long {
        var targetChat: Long = savedMessagesChatId
        var currentPid = parentId
        val visited = mutableSetOf<String>()
        var depth = 0
        while (currentPid != "0" && currentPid.isNotEmpty() && depth < 250 && visited.add(currentPid)) {
            if (currentPid.startsWith("vf_")) {
                currentPid = currentManifest.virtualFolders[currentPid]?.parentId ?: "0"
            } else {
                targetChat = currentPid.toLongOrNull() ?: savedMessagesChatId
                break
            }
            depth++
        }
        return targetChat
    }

    private fun isVirtualFolderOrAncestorTrashed(virtualId: String?): Long? {
        if (virtualId.isNullOrEmpty() || virtualId == "0") return null
        var current: String = virtualId
        val visited = mutableSetOf<String>()
        var depth = 0
        while (current.isNotEmpty() && current != "0" && depth < 100 && visited.add(current)) {
            currentManifest.trashedFolderIds[current]?.let { return it }
            currentManifest.deletedFolderIds[current]?.let { return it * 1000L }
            if (current.startsWith("vf_")) {
                val vf = currentManifest.virtualFolders[current]
                current = vf?.parentId ?: "0"
            } else {
                break
            }
            depth++
        }
        return null
    }

    private fun isEntityInActiveDrive(
        entity: DriveItemEntity,
        allFoldersById: Map<Long, DriveItemEntity>,
        allVirtualFoldersById: Map<String, DriveItemEntity>
    ): Boolean {
        if (entity.isTrashed) return false
        if (savedMessagesChatId != 0L && entity.parentChatId == 0L) return false

        if (entity.isFolder) {
            if (entity.isVirtual) {
                val vid = entity.virtualId ?: return false
                if (entity.name.startsWith("Recovered_")) return false
                if (currentManifest.deletedFolderIds.containsKey(vid)) return false
                if (currentManifest.trashedFolderIds.containsKey(vid)) return false
                if (isVirtualFolderOrAncestorTrashed(vid) != null) return false
                if (currentManifest.virtualFolders.isNotEmpty() && !currentManifest.virtualFolders.containsKey(vid)) return false
                var curParent = currentManifest.virtualFolders[vid]?.parentId ?: entity.virtualParentId ?: "0"
                val visited = mutableSetOf(vid)
                var depth = 0
                while (curParent.isNotEmpty() && curParent != "0" && depth < 100 && visited.add(curParent)) {
                    if (curParent.startsWith("vf_")) {
                        if (currentManifest.deletedFolderIds.containsKey(curParent)) return false
                        if (currentManifest.trashedFolderIds.containsKey(curParent)) return false
                        val parentDb = allVirtualFoldersById[curParent]
                        if (parentDb != null && parentDb.isTrashed) return false
                        val parentManifest = currentManifest.virtualFolders[curParent]
                        if (parentManifest == null && parentDb == null) return false
                        curParent = parentManifest?.parentId ?: parentDb?.virtualParentId ?: "0"
                    } else {
                        val chatId = curParent.toLongOrNull()
                        if (chatId != null && chatId != 0L && chatId != savedMessagesChatId) {
                            if (currentManifest.trashedFolderIds.containsKey(chatId.toString())) return false
                            val chFolder = allFoldersById[chatId]
                            if (chFolder != null && chFolder.isTrashed) return false
                        }
                        break
                    }
                    depth++
                }
            } else {
                if (currentManifest.trashedFolderIds.containsKey(entity.id.toString())) return false
            }
            return true
        } else {
            if (currentManifest.trashedFileIds.containsKey("${entity.parentChatId}_${entity.id}")) return false
            if (currentManifest.trashedFileIds.containsKey(entity.id.toString())) return false
            if (currentManifest.trashedFolderIds.containsKey(entity.parentChatId.toString())) return false

            if (savedMessagesChatId != 0L && entity.parentChatId != 0L && entity.parentChatId != savedMessagesChatId) {
                val parentChannel = allFoldersById[entity.parentChatId]
                if (parentChannel == null || parentChannel.isTrashed) return false
            } else if (entity.parentChatId != 0L) {
                val parentChannel = allFoldersById[entity.parentChatId]
                if (parentChannel != null && parentChannel.isTrashed) return false
            }

            val vPid = entity.virtualParentId
            if (!vPid.isNullOrEmpty() && vPid != "0" && vPid != entity.parentChatId.toString()) {
                if (isVirtualFolderOrAncestorTrashed(vPid) != null) return false
                var curParent: String = vPid
                val visited = mutableSetOf<String>()
                var depth = 0
                while (curParent.isNotEmpty() && curParent != "0" && depth < 100 && visited.add(curParent)) {
                    if (curParent.startsWith("vf_")) {
                        if (currentManifest.deletedFolderIds.containsKey(curParent)) return false
                        if (currentManifest.trashedFolderIds.containsKey(curParent)) return false
                        val parentDb = allVirtualFoldersById[curParent]
                        if (parentDb != null && parentDb.isTrashed) return false
                        val parentManifest = currentManifest.virtualFolders[curParent]
                        if (parentManifest == null && parentDb == null) return false
                        curParent = parentManifest?.parentId ?: parentDb?.virtualParentId ?: "0"
                    } else {
                        val chatId = curParent.toLongOrNull()
                        if (chatId != null && chatId != 0L && chatId != savedMessagesChatId) {
                            if (currentManifest.trashedFolderIds.containsKey(chatId.toString())) return false
                            val chFolder = allFoldersById[chatId]
                            if (chFolder != null && chFolder.isTrashed) return false
                        }
                        break
                    }
                    depth++
                }
            }
            return true
        }
    }

    private suspend fun reconcileTrashStatesInDb() {
        try {
            if (savedMessagesChatId != 0L) {
                driveDao.cleanupZeroChatPhysicalFolders(savedMessagesChatId)
                driveDao.cleanupAllZeroChatItems(savedMessagesChatId)
            }
            val allItems = driveDao.getAllItemsSync()
            if (allItems.isEmpty()) return

            val allFolders = allItems.filter { it.isFolder }
            val allFoldersById = allFolders.filter { !it.isVirtual }.associateBy { it.id }
            val allVirtualFoldersById = allFolders.filter { it.isVirtual && it.virtualId != null }
                .associateBy { it.virtualId!! }

            val now = System.currentTimeMillis()
            var manifestChanged = false
            val newlyTrashedFiles = mutableMapOf<String, Long>()
            val newlyTrashedFolders = mutableMapOf<String, Long>()

            for (item in allItems) {
                if (!item.isTrashed && !isEntityInActiveDrive(item, allFoldersById, allVirtualFoldersById)) {
                    val trashedTime = if (item.isFolder) {
                        val vid = item.virtualId
                        if (item.isVirtual && vid != null) {
                            currentManifest.trashedFolderIds[vid]
                                ?: isVirtualFolderOrAncestorTrashed(vid)
                                ?: now
                        } else {
                            currentManifest.trashedFolderIds[item.id.toString()] ?: now
                        }
                    } else {
                        currentManifest.trashedFileIds["${item.parentChatId}_${item.id}"]
                            ?: currentManifest.trashedFileIds[item.id.toString()]
                            ?: currentManifest.trashedFolderIds[item.parentChatId.toString()]
                            ?: allFoldersById[item.parentChatId]?.trashedAt
                            ?: isVirtualFolderOrAncestorTrashed(item.virtualParentId)
                            ?: item.virtualParentId?.let { allVirtualFoldersById[it]?.trashedAt }
                            ?: now
                    }
                    driveDao.updateTrashState(item.id, item.parentChatId, true, trashedTime)
                    if (item.isFolder && item.isVirtual && item.virtualId != null) {
                        driveDao.updateVirtualFolderTrashState(item.virtualId, true, trashedTime)
                        if (!currentManifest.trashedFolderIds.containsKey(item.virtualId) && !currentManifest.deletedFolderIds.containsKey(item.virtualId)) {
                            newlyTrashedFolders[item.virtualId] = trashedTime
                            manifestChanged = true
                        }
                    } else if (!item.isFolder) {
                        val key = "${item.parentChatId}_${item.id}"
                        if (!currentManifest.trashedFileIds.containsKey(key)) {
                            newlyTrashedFiles[key] = trashedTime
                            newlyTrashedFiles[item.id.toString()] = trashedTime
                            manifestChanged = true
                        }
                    }
                }
            }

            if (manifestChanged) {
                synchronized(currentManifest) {
                    val updatedFiles = currentManifest.trashedFileIds.toMutableMap().apply { putAll(newlyTrashedFiles) }
                    val updatedFolders = currentManifest.trashedFolderIds.toMutableMap().apply { putAll(newlyTrashedFolders) }
                    currentManifest = currentManifest.copy(
                        trashedFileIds = updatedFiles,
                        trashedFolderIds = updatedFolders
                    )
                    saveLocalManifest()
                }
            }
        } catch (e: Exception) {
            Log.e("DriveRepo", "Error reconciling trash states in DB: ${e.message}")
        }
    }

    private fun buildVirtualFolderEntities(): List<DriveItemEntity> {
        val vFolders = currentManifest.virtualFolders.values
            .filter { !it.name.startsWith("Recovered_") && !currentManifest.deletedFolderIds.containsKey(it.id) }
            .toList()
        return vFolders.map { vf ->
            val targetChat = resolveTargetChatForVirtualParent(vf.parentId)
            val finalVirtualParentId = if (
                vf.parentId.startsWith("vf_") &&
                !currentManifest.deletedFolderIds.containsKey(vf.parentId)
            ) {
                vf.parentId
            } else {
                "0"
            }
            val deterministicId = vf.id.replace("vf_", "").filter { it.isDigit() }.toLongOrNull()
                ?: (vf.id.hashCode().toLong() and 0x7FFFFFFFFFFFFFFFL)

            val trashedTime = currentManifest.trashedFolderIds[vf.id]
                ?: isVirtualFolderOrAncestorTrashed(vf.id)
            DriveItemEntity(
                id = deterministicId,
                name = vf.name,
                size = 0,
                mimeType = "virtual_folder",
                telegramFileId = 0,
                parentChatId = targetChat,
                isFolder = true,
                isVirtual = true,
                virtualId = vf.id,
                virtualParentId = finalVirtualParentId,
                createdAt = vf.createdAt * 1000,
                isStarred = false,
                isSecure = vf.isSecure,
                isTrashed = trashedTime != null,
                trashedAt = trashedTime
            )
        }
    }

    private fun syncVirtualFoldersToDb(immediate: Boolean = false) {
        if (immediate) {
            virtualFolderSyncJob?.cancel()
            virtualFolderSyncJob = scope.launch {
                if (savedMessagesChatId != 0L) {
                    driveDao.cleanupZeroChatPhysicalFolders(savedMessagesChatId)
                    driveDao.cleanupAllZeroChatItems(savedMessagesChatId)
                }
                val entities = buildVirtualFolderEntities()
                driveDao.syncVirtualFolders(entities)
                reconcileTrashStatesInDb()
            }
            return
        }
        virtualFolderSyncJob?.cancel()
        virtualFolderSyncJob = scope.launch {
            kotlinx.coroutines.delay(120L)
            if (savedMessagesChatId != 0L) {
                driveDao.cleanupZeroChatPhysicalFolders(savedMessagesChatId)
                driveDao.cleanupAllZeroChatItems(savedMessagesChatId)
            }
            val entities = buildVirtualFolderEntities()
            driveDao.syncVirtualFolders(entities)
            reconcileTrashStatesInDb()
        }
    }

    fun createVirtualFolder(name: String, parentId: String = "0", isSecure: Boolean = false) {
        val id = "vf_${System.nanoTime()}"
        val nowSec = System.currentTimeMillis() / 1000
        val folder = VirtualFolder(id, name, parentId, nowSec, "virtual_folder", isSecure, updatedAt = nowSec)
        val updatedFolders = currentManifest.virtualFolders.toMutableMap()
        updatedFolders[id] = folder
        currentManifest = currentManifest.copy(virtualFolders = updatedFolders, updatedAt = nowSec)
        saveLocalManifest()
        syncVirtualFoldersToDb(immediate = true)
        
        // Send distributed metadata message and track its ID
        if (savedMessagesChatId != 0L) {
            val metaJson = json.encodeToString(folder)
            val text = "$VFOLDER_META_PREFIX\n\n$metaJson"
            telegramClient.send(TdApi.SendMessage(savedMessagesChatId, null, null, null, null, TdApi.InputMessageText(TdApi.FormattedText(text, emptyArray()), null, true))) { result ->
                if (result is TdApi.Message) {
                    synchronized(currentManifest) {
                        val updatedMetaIds = currentManifest.folderMetadataIds.toMutableMap()
                        updatedMetaIds[id] = result.id
                        currentManifest = currentManifest.copy(folderMetadataIds = updatedMetaIds)
                        saveLocalManifest()
                        saveCloudManifest() // Save manifest after we have the message ID
                    }
                }
            }
        } else {
            saveCloudManifest()
        }
    }

    private fun saveCloudManifest() {
        saveLocalManifest()
        if (savedMessagesChatId == 0L) return
        val manifestJson = json.encodeToString(currentManifest)
        val text = "$MANIFEST_PREFIX\n\n$manifestJson"
        val formatted = TdApi.FormattedText(text, emptyArray())
        
        if (manifestMessageId != 0L) {
            telegramClient.send(TdApi.EditMessageText(savedMessagesChatId, manifestMessageId, null, TdApi.InputMessageText(formatted, null, true))) { result ->
                if (result is TdApi.Error && !result.message.contains("MESSAGE_NOT_MODIFIED", ignoreCase = true)) {
                    telegramClient.send(TdApi.SendMessage(savedMessagesChatId, null, null, null, null, TdApi.InputMessageText(formatted, null, true))) { sendRes ->
                        if (sendRes is TdApi.Message) {
                            manifestMessageId = sendRes.id
                            saveLocalManifest()
                        }
                    }
                }
            }
        } else {
            telegramClient.send(TdApi.SendMessage(savedMessagesChatId, null, null, null, null, TdApi.InputMessageText(formatted, null, true))) { result ->
                if (result is TdApi.Message) {
                    manifestMessageId = result.id
                    saveLocalManifest()
                }
            }
        }
    }

    private fun computeLogicalSize(files: List<DriveItemEntity>): Long {
        var total = 0L
        val seenGroups = mutableSetOf<String>()
        files.forEach { file ->
            if (file.splitGroupId == null) {
                total += file.size
            } else if (seenGroups.add(file.splitGroupId)) {
                val master = currentManifest.splitFileMasters[file.splitGroupId]
                total += master?.totalSize ?: (file.size * file.totalParts)
            }
        }
        return total
    }

    private fun collectDescendantVirtualIds(rootVirtualId: String, allFolders: List<DriveItemEntity> = emptyList()): Set<String> {
        val result = mutableSetOf(rootVirtualId)
        val childrenByParent = mutableMapOf<String, MutableSet<String>>()
        allFolders.filter { it.isVirtual && it.virtualId != null }.forEach { folder ->
            val pid = folder.virtualParentId ?: "0"
            val cid = folder.virtualId!!
            childrenByParent.getOrPut(pid) { mutableSetOf() }.add(cid)
        }
        currentManifest.virtualFolders.values.forEach { vf ->
            val pid = vf.parentId.ifBlank { "0" }
            childrenByParent.getOrPut(pid) { mutableSetOf() }.add(vf.id)
        }
        val queue = ArrayDeque<String>()
        queue.add(rootVirtualId)
        var guard = 0
        while (queue.isNotEmpty() && guard < 500) {
            val current = queue.removeFirst()
            childrenByParent[current]?.forEach { cid ->
                if (result.add(cid)) {
                    queue.add(cid)
                }
            }
            guard++
        }
        return result
    }

    private inner class FolderStatsIndex(
        allFiles: List<DriveItemEntity>,
        allFolders: List<DriveItemEntity>
    ) {
        private val filesByVirtualParent: Map<String, List<DriveItemEntity>> =
            allFiles.groupBy { it.virtualParentId ?: "0" }
        private val filesByChatId: Map<Long, List<DriveItemEntity>> =
            allFiles.groupBy { it.parentChatId }
        private val childrenByParent: Map<String, Set<String>> = buildMap<String, MutableSet<String>> {
            allFolders.forEach { folder ->
                if (folder.isVirtual && folder.virtualId != null) {
                    val pid = folder.virtualParentId ?: "0"
                    getOrPut(pid) { mutableSetOf() }.add(folder.virtualId)
                }
            }
            currentManifest.virtualFolders.values.forEach { vf ->
                val pid = vf.parentId.ifBlank { "0" }
                getOrPut(pid) { mutableSetOf() }.add(vf.id)
            }
        }
        private val virtualStatsCache = HashMap<String, Pair<Int, Long>>()
        private val chatStatsCache = HashMap<Long, Pair<Int, Long>>()

        fun getVirtualStats(virtualId: String): Pair<Int, Long> {
            virtualStatsCache[virtualId]?.let { return it }
            val descendants = mutableSetOf(virtualId)
            val queue = ArrayDeque<String>()
            queue.add(virtualId)
            var guard = 0
            while (queue.isNotEmpty() && guard < 500) {
                val current = queue.removeFirst()
                childrenByParent[current]?.forEach { cid ->
                    if (descendants.add(cid)) {
                        queue.add(cid)
                    }
                }
                guard++
            }
            var count = 0
            var total = 0L
            val seenGroups = mutableSetOf<String>()
            descendants.forEach { vid ->
                filesByVirtualParent[vid]?.forEach { file ->
                    count++
                    if (file.splitGroupId == null) {
                        total += file.size
                    } else if (seenGroups.add(file.splitGroupId)) {
                        val master = currentManifest.splitFileMasters[file.splitGroupId]
                        total += master?.totalSize ?: (file.size * file.totalParts)
                    }
                }
            }
            val res = count to total
            virtualStatsCache[virtualId] = res
            return res
        }

        fun getPhysicalStats(chatId: Long): Pair<Int, Long> {
            chatStatsCache[chatId]?.let { return it }
            val files = filesByChatId[chatId].orEmpty()
            val res = files.size to computeLogicalSize(files)
            chatStatsCache[chatId] = res
            return res
        }
    }

    private fun mapEntityToDriveItem(
        entity: DriveItemEntity,
        allFiles: List<DriveItemEntity>,
        allFolders: List<DriveItemEntity>,
        statsIndex: FolderStatsIndex? = null
    ): DriveItem {
        return if (entity.isFolder) {
            val (itemCount, totalSize) = if (statsIndex != null) {
                if (entity.isVirtual) {
                    val vid = entity.virtualId
                    if (vid != null) statsIndex.getVirtualStats(vid) else (0 to 0L)
                } else {
                    statsIndex.getPhysicalStats(entity.id)
                }
            } else {
                val folderFiles = if (entity.isVirtual) {
                    val vid = entity.virtualId
                    if (vid != null) {
                        val descendantIds = collectDescendantVirtualIds(vid, allFolders)
                        allFiles.filter { (it.virtualParentId ?: "0") in descendantIds }
                    } else emptyList()
                } else {
                    allFiles.filter { it.parentChatId == entity.id }
                }
                folderFiles.size to computeLogicalSize(folderFiles)
            }
            val resolvedCreatedAt = if (entity.isVirtual) {
                val vfCreatedSec = entity.virtualId?.let { currentManifest.virtualFolders[it]?.createdAt } ?: 0L
                if (vfCreatedSec > 0L) vfCreatedSec * 1000L else entity.createdAt
            } else {
                entity.createdAt
            }
            DriveItem.Folder(
                id = entity.id,
                parentChatId = entity.parentChatId,
                name = entity.name,
                telegramChatId = entity.id,
                isStarred = entity.isStarred,
                isVirtual = entity.isVirtual,
                virtualId = entity.virtualId,
                virtualParentId = entity.virtualParentId,
                isSecure = entity.isSecure,
                createdAt = resolvedCreatedAt,
                itemCount = itemCount,
                totalSize = totalSize,
                colorHex = getResolvedFolderColor(entity.isVirtual, entity.virtualId, entity.id),
                isTrashed = entity.isTrashed,
                trashedAt = entity.trashedAt,
                isOffline = entity.isOffline
            )
        } else {
            val validOfflinePath = if (entity.isOffline && !entity.offlinePath.isNullOrEmpty()) {
                entity.offlinePath.takeIf { isOfflineFilePresentCached(it) }
            } else null
            DriveItem.File(
                id = entity.id,
                parentChatId = entity.parentChatId,
                name = entity.name,
                size = entity.size,
                mimeType = entity.mimeType,
                telegramFileId = entity.telegramFileId,
                thumbnailPath = entity.thumbnailPath,
                localPath = validOfflinePath ?: entity.localPath,
                isStarred = entity.isStarred,
                remoteUniqueId = entity.remoteUniqueId ?: "",
                splitGroupId = entity.splitGroupId,
                totalParts = entity.totalParts,
                virtualParentId = entity.virtualParentId,
                isEncrypted = entity.isEncrypted,
                createdAt = entity.createdAt,
                isTrashed = entity.isTrashed,
                trashedAt = entity.trashedAt,
                isOffline = entity.isOffline && validOfflinePath != null,
                offlinePath = validOfflinePath
            )
        }
    }

    private fun deduplicateFolderEntities(folderEntities: List<DriveItemEntity>): List<DriveItemEntity> {
        val validVirtualIds = currentManifest.virtualFolders.keys
        val deletedIds = currentManifest.deletedFolderIds.keys
        val seenVirtualIds = mutableSetOf<String>()
        val seenPhysicalIds = mutableSetOf<Long>()

        // Sort so rows matching savedMessagesChatId (non-zero) are preferred over parentChatId == 0L
        val sorted = folderEntities.sortedByDescending { entity ->
            if (savedMessagesChatId != 0L && entity.parentChatId == savedMessagesChatId) 1 else 0
        }

        val result = mutableListOf<DriveItemEntity>()
        for (entity in sorted) {
            if (entity.isSecure) continue
            if (entity.isVirtual) {
                val vid = entity.virtualId ?: continue
                if (entity.name.startsWith("Recovered_")) continue
                if (vid in deletedIds) continue
                if (validVirtualIds.isNotEmpty() && vid !in validVirtualIds) continue
                if (seenVirtualIds.add(vid)) {
                    val manifestVf = currentManifest.virtualFolders[vid]
                    val resolvedParentVfId = if (
                        manifestVf != null &&
                        manifestVf.parentId.startsWith("vf_") &&
                        manifestVf.parentId !in deletedIds
                    ) {
                        manifestVf.parentId
                    } else {
                        entity.virtualParentId ?: "0"
                    }
                    result.add(
                        entity.copy(
                            name = manifestVf?.name ?: entity.name,
                            virtualParentId = resolvedParentVfId
                        )
                    )
                }
            } else {
                if (seenPhysicalIds.add(entity.id)) {
                    result.add(entity)
                }
            }
        }
        return result.sortedWith(compareByDescending<DriveItemEntity> { it.isVirtual }.thenBy { it.name.lowercase() })
    }

    private fun isOfflineFilePresentCached(path: String): Boolean {
        val now = System.currentTimeMillis()
        val cached = offlineFileExistsCache[path]
        if (cached != null && now - cached.first < 15_000L) {
            return cached.second
        }
        val exists = try {
            File(path).exists()
        } catch (_: Exception) {
            false
        }
        offlineFileExistsCache[path] = now to exists
        return exists
    }

    @OptIn(FlowPreview::class)
    fun getAllAvailableFolders(): Flow<List<DriveItem.Folder>> {
        return combine(
            driveDao.getAllFoldersIncludingTrashedFlow(),
            driveDao.getAllFiles(),
            _folderColorVersion
        ) { folderEntities, allFiles, _ ->
            val allFoldersById = folderEntities.filter { !it.isVirtual }.associateBy { it.id }
            val allVirtualFoldersById = folderEntities.filter { it.isVirtual && it.virtualId != null }
                .associateBy { it.virtualId!! }
            val activeFolders = folderEntities.filter { isEntityInActiveDrive(it, allFoldersById, allVirtualFoldersById) }
            val uniqueFolders = deduplicateFolderEntities(activeFolders)
            val uniqueFiles = allFiles
                .filter { it.partIndex == 0 && isEntityInActiveDrive(it, allFoldersById, allVirtualFoldersById) }
                .distinctBy { "${it.parentChatId}_${it.id}" }
            val statsIndex = FolderStatsIndex(uniqueFiles, uniqueFolders)
            uniqueFolders.mapNotNull { entity ->
                mapEntityToDriveItem(entity, uniqueFiles, uniqueFolders, statsIndex) as? DriveItem.Folder
            }
        }.debounce(40).distinctUntilChanged().flowOn(Dispatchers.Default)
    }

    @OptIn(FlowPreview::class)
    fun getAllFiles(): Flow<List<DriveItem.File>> {
        return combine(
            driveDao.getAllFiles(),
            driveDao.getAllFoldersIncludingTrashedFlow(),
            _folderColorVersion
        ) { entities, allFolders, _ ->
            val allFoldersById = allFolders.filter { !it.isVirtual }.associateBy { it.id }
            val allVirtualFoldersById = allFolders.filter { it.isVirtual && it.virtualId != null }
                .associateBy { it.virtualId!! }
            entities
                .filter { it.partIndex == 0 && isEntityInActiveDrive(it, allFoldersById, allVirtualFoldersById) }
                .distinctBy { "${it.parentChatId}_${it.id}" }
                .map { entity ->
                    val validOfflinePath = if (entity.isOffline && !entity.offlinePath.isNullOrEmpty()) {
                        entity.offlinePath.takeIf { isOfflineFilePresentCached(it) }
                    } else null
                    DriveItem.File(
                        entity.id,
                        entity.parentChatId,
                        entity.name,
                        entity.size,
                        entity.mimeType,
                        entity.telegramFileId,
                        entity.thumbnailPath,
                        validOfflinePath ?: entity.localPath,
                        entity.isStarred,
                        entity.remoteUniqueId ?: "",
                        entity.splitGroupId,
                        entity.totalParts,
                        entity.virtualParentId,
                        entity.isEncrypted,
                        entity.createdAt,
                        entity.isTrashed,
                        entity.trashedAt,
                        entity.isOffline && validOfflinePath != null,
                        validOfflinePath
                    )
                }
        }.debounce(40).distinctUntilChanged().flowOn(Dispatchers.Default)
    }

    fun getStarredItems(): Flow<List<DriveItem>> {
        return combine(
            driveDao.getStarredItems(),
            driveDao.getAllFiles(),
            driveDao.getAllFoldersIncludingTrashedFlow(),
            _folderColorVersion
        ) { entities, allFiles, allFolders, _ ->
            val allFoldersById = allFolders.filter { !it.isVirtual }.associateBy { it.id }
            val allVirtualFoldersById = allFolders.filter { it.isVirtual && it.virtualId != null }
                .associateBy { it.virtualId!! }
            val activeFolders = allFolders.filter { isEntityInActiveDrive(it, allFoldersById, allVirtualFoldersById) }
            val activeFiles = allFiles
                .filter { it.partIndex == 0 && isEntityInActiveDrive(it, allFoldersById, allVirtualFoldersById) }
                .distinctBy { "${it.parentChatId}_${it.id}" }
            val statsIndex = FolderStatsIndex(activeFiles, activeFolders)
            entities
                .filter { (it.isFolder || it.partIndex == 0) && isEntityInActiveDrive(it, allFoldersById, allVirtualFoldersById) }
                .distinctBy { if (it.isFolder && it.isVirtual) "vf_${it.virtualId}" else "${it.parentChatId}_${it.id}" }
                .map { entity ->
                    mapEntityToDriveItem(entity, activeFiles, activeFolders, statsIndex)
                }
        }.distinctUntilChanged().flowOn(Dispatchers.Default)
    }

    fun getTrashedItemsFlow(): Flow<List<DriveItem>> {
        return combine(
            driveDao.getTrashedItemsFlow(),
            driveDao.getAllFilesIncludingTrashedFlow(),
            driveDao.getAllFoldersIncludingTrashedFlow(),
            _folderColorVersion
        ) { entities, allFiles, allFolders, _ ->
            val uniqueAllFiles = allFiles
                .filter { it.partIndex == 0 }
                .distinctBy { "${it.parentChatId}_${it.id}" }
            val statsIndex = FolderStatsIndex(uniqueAllFiles, allFolders)
            entities
                .filter { entity ->
                    if (entity.isFolder) {
                        if (entity.isVirtual) {
                            val vid = entity.virtualId ?: return@filter false
                            !entity.name.startsWith("Recovered_") && !currentManifest.deletedFolderIds.containsKey(vid)
                        } else {
                            !(savedMessagesChatId != 0L && entity.parentChatId == 0L && entity.id == 0L)
                        }
                    } else {
                        entity.partIndex == 0 && !(savedMessagesChatId != 0L && entity.parentChatId == 0L)
                    }
                }
                .distinctBy { if (it.isFolder && it.isVirtual) "vf_${it.virtualId}" else "${it.parentChatId}_${it.id}" }
                .map { entity ->
                    mapEntityToDriveItem(entity, uniqueAllFiles, allFolders, statsIndex)
                }
        }.distinctUntilChanged().flowOn(Dispatchers.Default)
    }

    fun getOfflineItemsFlow(): Flow<List<DriveItem>> {
        return combine(
            driveDao.getOfflineItemsFlow(),
            driveDao.getAllFiles(),
            driveDao.getAllFoldersIncludingTrashedFlow(),
            _folderColorVersion
        ) { entities, allFiles, allFolders, _ ->
            val allFoldersById = allFolders.filter { !it.isVirtual }.associateBy { it.id }
            val allVirtualFoldersById = allFolders.filter { it.isVirtual && it.virtualId != null }
                .associateBy { it.virtualId!! }
            val activeFolders = allFolders.filter { isEntityInActiveDrive(it, allFoldersById, allVirtualFoldersById) }
            val activeFiles = allFiles
                .filter { it.partIndex == 0 && isEntityInActiveDrive(it, allFoldersById, allVirtualFoldersById) }
                .distinctBy { "${it.parentChatId}_${it.id}" }
            val statsIndex = FolderStatsIndex(activeFiles, activeFolders)
            entities
                .filter { (it.isFolder || it.partIndex == 0) && isEntityInActiveDrive(it, allFoldersById, allVirtualFoldersById) }
                .distinctBy { if (it.isFolder && it.isVirtual) "vf_${it.virtualId}" else "${it.parentChatId}_${it.id}" }
                .map { entity ->
                    mapEntityToDriveItem(entity, activeFiles, activeFolders, statsIndex)
                }
        }.distinctUntilChanged().flowOn(Dispatchers.Default)
    }

    fun getRecentFilesFlow(): Flow<List<DriveItem.File>> {
        val cutoff = System.currentTimeMillis() - RECENT_WINDOW_MS
        return combine(
            driveDao.getRecentFilesFlow(cutoff),
            driveDao.getAllFoldersIncludingTrashedFlow(),
            _folderColorVersion
        ) { entities, allFolders, _ ->
            val allFoldersById = allFolders.filter { !it.isVirtual }.associateBy { it.id }
            val allVirtualFoldersById = allFolders.filter { it.isVirtual && it.virtualId != null }
                .associateBy { it.virtualId!! }
            entities
                .filter { it.partIndex == 0 && isEntityInActiveDrive(it, allFoldersById, allVirtualFoldersById) }
                .distinctBy { "${it.parentChatId}_${it.id}" }
                .mapNotNull { entity ->
                    mapEntityToDriveItem(entity, emptyList(), emptyList()) as? DriveItem.File
                }
        }.distinctUntilChanged().flowOn(Dispatchers.Default)
    }

    @OptIn(FlowPreview::class)
    fun getItems(chatId: Long?, virtualParentId: String = "0", searchQuery: String = ""): Flow<List<DriveItem>> {
        val targetChatId = chatId ?: savedMessagesChatId
        val flow = if (searchQuery.isNotEmpty()) driveDao.searchGlobal(searchQuery)
        else if (targetChatId != 0L) driveDao.getItemsFlow(targetChatId, virtualParentId)
        else kotlinx.coroutines.flow.flowOf(emptyList())

        return combine(
            flow,
            driveDao.getAllFiles(),
            driveDao.getAllFoldersIncludingTrashedFlow(),
            _folderColorVersion
        ) { entities, allFiles, allFolders, _ ->
            val allFoldersById = allFolders.filter { !it.isVirtual }.associateBy { it.id }
            val allVirtualFoldersById = allFolders.filter { it.isVirtual && it.virtualId != null }
                .associateBy { it.virtualId!! }
            val activeFolders = allFolders.filter { isEntityInActiveDrive(it, allFoldersById, allVirtualFoldersById) }
            val uniqueAllFolders = deduplicateFolderEntities(activeFolders)
            val uniqueAllFiles = allFiles
                .filter { it.partIndex == 0 && isEntityInActiveDrive(it, allFoldersById, allVirtualFoldersById) }
                .distinctBy { "${it.parentChatId}_${it.id}" }
            val statsIndex = FolderStatsIndex(uniqueAllFiles, uniqueAllFolders)
            val seenVirtualIds = mutableSetOf<String>()
            val seenPhysicalFolderIds = mutableSetOf<Long>()
            entities
                .filter { entity ->
                    if (!isEntityInActiveDrive(entity, allFoldersById, allVirtualFoldersById)) return@filter false
                    if (entity.isVirtual && entity.name.startsWith("Recovered_")) return@filter false
                    if (entity.isFolder) {
                        if (entity.isVirtual) {
                            val vid = entity.virtualId ?: return@filter false
                            if (currentManifest.deletedFolderIds.containsKey(vid)) return@filter false
                            if (currentManifest.virtualFolders.isNotEmpty() && !currentManifest.virtualFolders.containsKey(vid)) return@filter false
                            seenVirtualIds.add(vid)
                        } else {
                            seenPhysicalFolderIds.add(entity.id)
                        }
                    } else {
                        entity.partIndex == 0
                    }
                }
                .sortedByDescending { it.createdAt }
                .map { entity ->
                    mapEntityToDriveItem(entity, uniqueAllFiles, uniqueAllFolders, statsIndex)
                }
        }.debounce(35).distinctUntilChanged().flowOn(Dispatchers.Default)
    }

    fun fetchFiles(chatId: Long? = null, forceRefresh: Boolean = false) {
        scope.launch { cleanupRecoveredFoldersInDb() }
        val targetChatId = chatId ?: savedMessagesChatId
        if (targetChatId == 0L) {
            telegramClient.send(TdApi.GetMe()) { result ->
                if (result is TdApi.User) {
                    savedMessagesChatId = result.id
                    _savedMessagesChatIdFlow.value = result.id
                    manifestPrefs.edit().putLong("saved_messages_chat_id", result.id).apply()
                    val now = System.currentTimeMillis()
                    lastFullSyncTimeMs = now
                    lastChatFetchTimeMs[savedMessagesChatId] = now
                    fetchCloudManifest { syncFolders { loadAllDriveItems(savedMessagesChatId) } }
                }
            }
        } else {
            val now = System.currentTimeMillis()
            val lastChatFetch = lastChatFetchTimeMs[targetChatId] ?: 0L
            val lastFullSync = lastFullSyncTimeMs
            if (!forceRefresh && now - lastChatFetch < 45_000L && now - lastFullSync < 60_000L) {
                // Already synced recently; local Room cache serves the folder instantaneously without redundant network/chat scans
                return
            }
            lastChatFetchTimeMs[targetChatId] = now
            if (forceRefresh || now - lastFullSync >= 60_000L) {
                lastFullSyncTimeMs = now
                fetchCloudManifest { syncFolders { loadAllDriveItems(targetChatId) } }
            } else {
                loadAllDriveItems(targetChatId)
            }
        }
    }

    private fun syncFolders(onComplete: () -> Unit = {}) {
        val chatLists = listOf(TdApi.ChatListMain(), TdApi.ChatListArchive())
        var listsProcessed = 0
        chatLists.forEach { chatList ->
            telegramClient.send(TdApi.GetChats(chatList, 1000)) { result ->
                if (result is TdApi.Chats) {
                    if (result.chatIds.isEmpty()) {
                        listsProcessed++
                        if (listsProcessed == chatLists.size) onComplete()
                        return@send
                    }
                    var chatsProcessed = 0
                    result.chatIds.forEach { cid ->
                        telegramClient.send(TdApi.GetChat(cid)) { chatResult ->
                            if (chatResult is TdApi.Chat) processDiscoveredFolder(chatResult)
                            chatsProcessed++
                            if (chatsProcessed == result.chatIds.size) {
                                listsProcessed++
                                if (listsProcessed == chatLists.size) onComplete()
                            }
                        }
                    }
                } else {
                    listsProcessed++
                    if (listsProcessed == chatLists.size) onComplete()
                }
            }
        }
    }

    private fun loadAllDriveItems(chatId: Long) {
        fetchHistoryRecursively(chatId, 0, mutableListOf())
    }

    private fun fetchHistoryRecursively(chatId: Long, fromMessageId: Long, allFetchedIds: MutableList<Long>) {
        telegramClient.send(TdApi.GetChatHistory(chatId, fromMessageId, 0, 100, false)) { result ->
            if (result is TdApi.Messages) {
                val batchMessages = result.messages
                if (batchMessages.isEmpty()) {
                    scope.launch {
                        if (allFetchedIds.isNotEmpty()) {
                            val now = System.currentTimeMillis()
                            recentlyCreatedMessageIds.entries.removeIf { now - it.value > 600_000L }
                            val keepIds = (allFetchedIds + recentlyCreatedMessageIds.keys + pendingSendMetaMap.keys).distinct()
                            driveDao.deleteFilesByChatNotInList(chatId, keepIds)
                        }
                        reconcileTrashStatesInDb()
                    }
                    return@send
                }

                scope.launch(Dispatchers.Default) {
                    batchMessages.forEach { message ->
                        if (message.sendingState != null) {
                            allFetchedIds.add(message.id)
                        }
                    }

                    val entities = batchMessages.mapNotNull { message ->
                        if (message.sendingState != null) return@mapNotNull null
                        mapMessageToEntity(chatId, message)
                    }

                    allFetchedIds.addAll(entities.map { it.id })
                    driveDao.insertItemsPreservingStarred(entities)

                    if (allFetchedIds.size < 15000) {
                        fetchHistoryRecursively(chatId, batchMessages.last().id, allFetchedIds)
                    } else {
                        val now = System.currentTimeMillis()
                        recentlyCreatedMessageIds.entries.removeIf { now - it.value > 600_000L }
                        val keepIds = (allFetchedIds + recentlyCreatedMessageIds.keys + pendingSendMetaMap.keys).distinct()
                        driveDao.deleteFilesByChatNotInList(chatId, keepIds)
                        reconcileTrashStatesInDb()
                    }
                }
            }
        }
    }

    private fun mapMessageToEntity(chatId: Long, message: TdApi.Message): DriveItemEntity? {
        val isSecureFolder = currentManifest.secureFolderChatIds.contains(chatId)
        val content = message.content
        
        // Handle Distributed Metadata Text Messages
        if (content is TdApi.MessageText && chatId == savedMessagesChatId) {
            val text = content.text.text
            if (text.startsWith(MANIFEST_PREFIX) && !text.startsWith(MANIFEST_BACKUP_PREFIX)) {
                try {
                    val jsonStartIndex = text.indexOf('{')
                    if (jsonStartIndex != -1) {
                        val m = json.decodeFromString<TeleDriveManifest>(text.substring(jsonStartIndex).trim())
                        synchronized(currentManifest) {
                            if (manifestMessageId == 0L || message.id > manifestMessageId) {
                                manifestMessageId = message.id
                            }
                            currentManifest = mergeManifests(currentManifest, m)
                            saveLocalManifest()
                        }
                        syncVirtualFoldersToDb()
                    }
                } catch (e: Exception) { }
            } else if (text.startsWith(VFOLDER_META_PREFIX)) {
                try {
                    val jsonStartIndex = text.indexOf('{')
                    if (jsonStartIndex != -1) {
                        val vf = json.decodeFromString<VirtualFolder>(text.substring(jsonStartIndex))
                        if (vf.name.startsWith("Recovered_")) {
                            synchronized(currentManifest) {
                                val updatedDel = currentManifest.deletedFolderIds.toMutableMap()
                                updatedDel[vf.id] = System.currentTimeMillis() / 1000
                                currentManifest = sanitizeManifest(currentManifest.copy(deletedFolderIds = updatedDel))
                                saveLocalManifest()
                            }
                            telegramClient.send(TdApi.DeleteMessages(savedMessagesChatId, longArrayOf(message.id), true)) {}
                            return null
                        }
                        
                        // Check if this folder was deleted
                        val deletedAt = currentManifest.deletedFolderIds[vf.id]
                        if (deletedAt != null && vf.createdAt <= deletedAt) {
                            // Skip re-adding deleted folder
                            return null
                        }

                        synchronized(currentManifest) {
                            val existing = currentManifest.virtualFolders[vf.id]
                            val updatedFolders = currentManifest.virtualFolders.toMutableMap()
                            val updatedMetaIds = currentManifest.folderMetadataIds.toMutableMap()
                            
                            val vfEffectiveTime = maxOf(vf.updatedAt, vf.createdAt)
                            val existingEffectiveTime = if (existing != null) maxOf(existing.updatedAt, existing.createdAt) else -1L
                            
                            if (existing == null || vfEffectiveTime > existingEffectiveTime) {
                                updatedFolders[vf.id] = vf
                            }
                            updatedMetaIds[vf.id] = message.id
                            
                            currentManifest = sanitizeManifest(
                                applyLocalRenames(
                                    currentManifest.copy(
                                        virtualFolders = updatedFolders,
                                        folderMetadataIds = updatedMetaIds
                                    )
                                )
                            )
                            saveLocalManifest()
                        }
                        syncVirtualFoldersToDb()
                    }
                } catch (e: Exception) { }
            } else if (text.startsWith(FILE_META_PREFIX)) {
                try {
                    val jsonStartIndex = text.indexOf('{')
                    if (jsonStartIndex != -1) {
                        val jsonStr = text.substring(jsonStartIndex)
                        if (jsonStr.contains("partMessageIds")) {
                            // It's a SplitFileMaster
                            val master = json.decodeFromString<SplitFileMaster>(jsonStr)
                            synchronized(currentManifest) {
                                val updatedMasters = currentManifest.splitFileMasters.toMutableMap()
                                updatedMasters[master.groupId] = master.copy(metadataMessageId = message.id)
                                currentManifest = currentManifest.copy(splitFileMasters = updatedMasters)
                                saveLocalManifest()
                            }
                        } else {
                            // It's a regular FileMetadata
                            val meta = json.decodeFromString<FileMetadata>(jsonStr)
                            val pendingOverride = pendingSendMetaMap[meta.messageId]
                            val effectiveVfId = pendingOverride?.virtualFolderId ?: meta.virtualFolderId ?: "0"
                            var shouldApplyMeta = false
                            synchronized(currentManifest) {
                                val existingMetaMsgId = currentManifest.fileMetadataIds[meta.messageId.toString()] ?: 0L
                                if (message.id >= existingMetaMsgId) {
                                    val updatedMappings = currentManifest.fileMappings.toMutableMap()
                                    val updatedFileMetaIds = currentManifest.fileMetadataIds.toMutableMap()
                                    
                                    updatedMappings[meta.messageId.toString()] = effectiveVfId
                                    updatedFileMetaIds[meta.messageId.toString()] = message.id
                                    
                                    currentManifest = currentManifest.copy(
                                        fileMappings = updatedMappings,
                                        fileMetadataIds = updatedFileMetaIds
                                    )
                                    saveLocalManifest()
                                    shouldApplyMeta = true
                                }
                            }
                            if (shouldApplyMeta) {
                                if (meta.fileName.isNotBlank() && getLocalFileRename(meta.messageId, chatId) == null) {
                                    saveLocalFileRename(meta.messageId, chatId, meta.fileName)
                                }
                                if (meta.originalTimestamp > 0L) {
                                    saveLocalFileOriginalTime(meta.messageId, chatId, meta.originalTimestamp)
                                }
                                scope.launch {
                                    driveDao.updateVirtualParent(meta.messageId, effectiveVfId)
                                    val effectiveName = getLocalFileRename(meta.messageId, chatId) ?: meta.fileName
                                    if (effectiveName.isNotBlank()) {
                                        driveDao.renameItem(meta.messageId, chatId, effectiveName)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) { }
            }
        }

        return when (content) {
            is TdApi.MessageDocument -> {
                val caption = content.caption.text
                val isEncrypted = caption.contains("[ENC]") || isSecureFolder
                val splitInfo = parseSplitMetadata(caption)
                val cleanName = resolveFileName(
                    messageId = message.id,
                    chatId = chatId,
                    caption = caption,
                    rawTelegramFileName = content.document.fileName,
                    splitOriginalName = splitInfo?.originalName,
                    fallbackName = "Document_${message.id}"
                )
                val extMime = FileUtils.getMimeType(cleanName)
                val mimeType = if (content.document.mimeType == "application/octet-stream" || content.document.mimeType.isBlank() || extMime != "application/octet-stream") {
                    if (extMime != "application/octet-stream") extMime else content.document.mimeType.ifBlank { "application/octet-stream" }
                } else {
                    content.document.mimeType
                }
                
                val thumb = content.document.thumbnail
                if (thumb != null && thumb.file.local.path.isEmpty() && thumb.file.local.canBeDownloaded) {
                    telegramClient.send(TdApi.DownloadFile(thumb.file.id, 1, 0, 0, false))
                }

                DriveItemEntity(
                    message.id, cleanName, content.document.document.expectedSize, mimeType, content.document.document.id, chatId, false,
                    thumbnailPath = thumb?.file?.local?.path?.takeIf { it.isNotEmpty() },
                    localPath = content.document.document.local.path.takeIf { it.isNotEmpty() },
                    createdAt = resolveOriginalTimestamp(message.id, chatId, caption, message.date),
                    thumbnailFileId = thumb?.file?.id,
                    remoteUniqueId = content.document.document.remote.uniqueId,
                    thumbnailRemoteUniqueId = thumb?.file?.remote?.uniqueId,
                    splitGroupId = splitInfo?.groupId,
                    partIndex = splitInfo?.partIndex ?: 0,
                    totalParts = splitInfo?.totalParts ?: 1,
                    virtualParentId = getAndEnsureVirtualFolder(message.id, caption),
                    isSecure = isSecureFolder,
                    isEncrypted = isEncrypted
                )
            }
            is TdApi.MessagePhoto -> {
                val caption = content.caption.text
                val isEncrypted = caption.contains("[ENC]") || isSecureFolder
                val sizes = content.photo.sizes
                val thumb = sizes.firstOrNull()
                val full = sizes.lastOrNull()?.photo
                
                if (thumb != null && thumb.photo.local.path.isEmpty() && thumb.photo.local.canBeDownloaded) {
                    telegramClient.send(TdApi.DownloadFile(thumb.photo.id, 1, 0, 0, false))
                }

                val photoName = resolveFileName(
                    messageId = message.id,
                    chatId = chatId,
                    caption = caption,
                    rawTelegramFileName = "",
                    splitOriginalName = null,
                    fallbackName = "Photo_${message.id}.jpg"
                )

                DriveItemEntity(
                    message.id, photoName, full?.expectedSize ?: 0L, "image/jpeg", full?.id ?: 0, chatId, false,
                    thumbnailPath = thumb?.photo?.local?.path?.takeIf { it.isNotEmpty() },
                    localPath = full?.local?.path?.takeIf { it.isNotEmpty() },
                    createdAt = resolveOriginalTimestamp(message.id, chatId, caption, message.date),
                    thumbnailFileId = thumb?.photo?.id,
                    remoteUniqueId = full?.remote?.uniqueId,
                    thumbnailRemoteUniqueId = thumb?.photo?.remote?.uniqueId,
                    virtualParentId = getAndEnsureVirtualFolder(message.id, caption),
                    isSecure = isSecureFolder,
                    isEncrypted = isEncrypted
                )
            }
            is TdApi.MessageVideo -> {
                val caption = content.caption.text
                val isEncrypted = caption.contains("[ENC]") || isSecureFolder
                val splitInfo = parseSplitMetadata(caption)
                val cleanName = resolveFileName(
                    messageId = message.id,
                    chatId = chatId,
                    caption = caption,
                    rawTelegramFileName = content.video.fileName,
                    splitOriginalName = splitInfo?.originalName,
                    fallbackName = "Video_${message.id}.mp4"
                )
                val mimeType = if (content.video.mimeType == "application/octet-stream" || content.video.mimeType.isBlank()) FileUtils.getMimeType(cleanName) else content.video.mimeType
                
                val thumb = content.video.thumbnail
                if (thumb != null && thumb.file.local.path.isEmpty() && thumb.file.local.canBeDownloaded) {
                    telegramClient.send(TdApi.DownloadFile(thumb.file.id, 1, 0, 0, false))
                }

                DriveItemEntity(
                    message.id, cleanName, content.video.video.expectedSize, mimeType, content.video.video.id, chatId, false,
                    thumbnailPath = thumb?.file?.local?.path?.takeIf { it.isNotEmpty() },
                    localPath = content.video.video.local.path.takeIf { it.isNotEmpty() },
                    createdAt = resolveOriginalTimestamp(message.id, chatId, caption, message.date),
                    thumbnailFileId = thumb?.file?.id,
                    remoteUniqueId = content.video.video.remote.uniqueId,
                    thumbnailRemoteUniqueId = thumb?.file?.remote?.uniqueId,
                    splitGroupId = splitInfo?.groupId,
                    partIndex = splitInfo?.partIndex ?: 0,
                    totalParts = splitInfo?.totalParts ?: 1,
                    virtualParentId = getAndEnsureVirtualFolder(message.id, caption),
                    isSecure = isSecureFolder,
                    isEncrypted = isEncrypted
                )
            }
            is TdApi.MessageAudio -> {
                val caption = content.caption.text
                val isEncrypted = caption.contains("[ENC]") || isSecureFolder
                val splitInfo = parseSplitMetadata(caption)
                val cleanName = resolveFileName(
                    messageId = message.id,
                    chatId = chatId,
                    caption = caption,
                    rawTelegramFileName = content.audio.fileName,
                    splitOriginalName = splitInfo?.originalName,
                    fallbackName = "Audio_${message.id}.mp3"
                )
                val mimeType = if (content.audio.mimeType == "application/octet-stream" || content.audio.mimeType.isEmpty()) FileUtils.getMimeType(cleanName) else content.audio.mimeType
                DriveItemEntity(
                    message.id, cleanName, content.audio.audio.expectedSize, mimeType, content.audio.audio.id, chatId, false,
                    localPath = content.audio.audio.local.path.takeIf { it.isNotEmpty() },
                    createdAt = resolveOriginalTimestamp(message.id, chatId, caption, message.date),
                    splitGroupId = splitInfo?.groupId,
                    partIndex = splitInfo?.partIndex ?: 0,
                    totalParts = splitInfo?.totalParts ?: 1,
                    virtualParentId = getAndEnsureVirtualFolder(message.id, caption),
                    isSecure = isSecureFolder,
                    isEncrypted = isEncrypted,
                    remoteUniqueId = content.audio.audio.remote.uniqueId
                )
            }
            is TdApi.MessageAnimation -> {
                val caption = content.caption.text
                val isEncrypted = caption.contains("[ENC]") || isSecureFolder
                
                val thumb = content.animation.thumbnail
                if (thumb != null && thumb.file.local.path.isEmpty() && thumb.file.local.canBeDownloaded) {
                    telegramClient.send(TdApi.DownloadFile(thumb.file.id, 1, 0, 0, false))
                }

                DriveItemEntity(
                    message.id, cleanEncryptedFileName("Animation_${message.id}.mp4"), content.animation.animation.expectedSize, "video/mp4", content.animation.animation.id, chatId, false,
                    thumbnailPath = thumb?.file?.local?.path?.takeIf { it.isNotEmpty() },
                    localPath = content.animation.animation.local.path.takeIf { it.isNotEmpty() },
                    createdAt = resolveOriginalTimestamp(message.id, chatId, caption, message.date),
                    thumbnailFileId = thumb?.file?.id,
                    remoteUniqueId = content.animation.animation.remote.uniqueId,
                    thumbnailRemoteUniqueId = thumb?.file?.remote?.uniqueId,
                    virtualParentId = getAndEnsureVirtualFolder(message.id, caption),
                    isSecure = isSecureFolder,
                    isEncrypted = isEncrypted
                )
            }
            is TdApi.MessageVoiceNote -> {
                DriveItemEntity(message.id, "Voice_${message.id}.ogg", content.voiceNote.voice.expectedSize, "audio/ogg", content.voiceNote.voice.id, chatId, false, localPath = content.voiceNote.voice.local.path.takeIf { it.isNotEmpty() }, createdAt = message.date.toLong() * 1000, virtualParentId = "0", isSecure = isSecureFolder, isEncrypted = isSecureFolder, remoteUniqueId = content.voiceNote.voice.remote.uniqueId)
            }
            is TdApi.MessageVideoNote -> {
                DriveItemEntity(message.id, "VideoNote_${message.id}.mp4", content.videoNote.video.expectedSize, "video/mp4", content.videoNote.video.id, chatId, false, localPath = content.videoNote.video.local.path.takeIf { it.isNotEmpty() }, createdAt = message.date.toLong() * 1000, virtualParentId = "0", isSecure = isSecureFolder, isEncrypted = isSecureFolder, remoteUniqueId = content.videoNote.video.remote.uniqueId)
            }
            else -> null
        }?.let { entity ->
            val vPid = entity.virtualParentId
            val trashedTime = currentManifest.trashedFileIds["${chatId}_${message.id}"]
                ?: currentManifest.trashedFileIds[message.id.toString()]
                ?: currentManifest.trashedFolderIds[chatId.toString()]
                ?: isVirtualFolderOrAncestorTrashed(vPid)
                ?: if (
                    !vPid.isNullOrEmpty() &&
                    vPid != "0" &&
                    vPid.startsWith("vf_") &&
                    currentManifest.virtualFolders.isNotEmpty() &&
                    !currentManifest.virtualFolders.containsKey(vPid)
                ) System.currentTimeMillis() else null
            if (trashedTime != null) {
                entity.copy(isTrashed = true, trashedAt = trashedTime)
            } else {
                entity
            }
        }
    }

    private fun getAndEnsureVirtualFolder(messageId: Long, caption: String): String {
        val pendingId = pendingSendMetaMap[messageId]?.virtualFolderId
        val localVfId = getLocalFileVirtualParent(messageId)
        val mappedId = currentManifest.fileMappings[messageId.toString()]?.takeIf { it != "0" }
        val tagId = parseVirtualFolderTag(caption)?.takeIf { it != "0" }
        val finalId = pendingId ?: localVfId ?: mappedId ?: tagId ?: "0"
        
        if (finalId != "0") {
            val existingFolder = currentManifest.virtualFolders[finalId]
            if (existingFolder?.name?.startsWith("Recovered_") == true) {
                return "0"
            }

            if (currentManifest.fileMappings[messageId.toString()] != finalId) {
                val updatedMappings = currentManifest.fileMappings.toMutableMap()
                updatedMappings[messageId.toString()] = finalId
                currentManifest = currentManifest.copy(fileMappings = updatedMappings)
            }
        }
        
        return finalId
    }

    private val preloadedChannelIds = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

    private fun processDiscoveredFolder(chat: TdApi.Chat) {
        val type = chat.type
        if (type is TdApi.ChatTypeSupergroup) {
            val isKnownSecure = currentManifest.secureFolderChatIds.contains(chat.id)
            val isPrivateGroup = !type.isChannel
            telegramClient.send(TdApi.GetSupergroup(type.supergroupId)) { sgResult ->
                if (sgResult is TdApi.Supergroup) {
                    val status = sgResult.status
                    val isCreator = status is TdApi.ChatMemberStatusCreator
                    val isSecure = isKnownSecure || (isPrivateGroup && isCreator && currentManifest.secureFolderChatIds.contains(chat.id))
                    
                    if (isCreator || status is TdApi.ChatMemberStatusAdministrator) {
                        val sgCreatedMs = if (sgResult.date > 0) sgResult.date.toLong() * 1000L else 0L
                        scope.launch {
                            val existing = driveDao.getItemById(chat.id, savedMessagesChatId)
                            val finalCreatedAt = if (sgCreatedMs > 0L) sgCreatedMs else (existing?.createdAt ?: System.currentTimeMillis())
                            val manifestTrashedAt = currentManifest.trashedFolderIds[chat.id.toString()]
                            val isTrashed = (existing?.isTrashed == true) || (manifestTrashedAt != null)
                            val trashedAt = existing?.trashedAt ?: manifestTrashedAt
                            driveDao.insertItems(
                                listOf(
                                    DriveItemEntity(
                                        chat.id,
                                        existing?.name?.takeIf { it.isNotBlank() } ?: chat.title,
                                        0,
                                        "folder",
                                        0,
                                        savedMessagesChatId,
                                        true,
                                        isStarred = existing?.isStarred ?: false,
                                        createdAt = finalCreatedAt,
                                        isSecure = isSecure,
                                        isTrashed = isTrashed,
                                        trashedAt = trashedAt,
                                        isOffline = existing?.isOffline ?: false,
                                        offlinePath = existing?.offlinePath
                                    )
                                )
                            )
                            if (preloadedChannelIds.add(chat.id)) {
                                loadAllDriveItems(chat.id)
                            }
                        }
                    }
                }
            }
        } else if (type is TdApi.ChatTypeBasicGroup) {
            val isKnownSecure = currentManifest.secureFolderChatIds.contains(chat.id)
            telegramClient.send(TdApi.GetBasicGroup(type.basicGroupId)) { bgResult ->
                if (bgResult is TdApi.BasicGroup) {
                    val status = bgResult.status
                    val isCreator = status is TdApi.ChatMemberStatusCreator
                    val isSecure = isKnownSecure || isCreator
                    if (isCreator || status is TdApi.ChatMemberStatusAdministrator) {
                        val fallbackDateMs = chat.lastMessage?.date?.toLong()?.times(1000L) ?: 0L
                        scope.launch {
                            val existing = driveDao.getItemById(chat.id, savedMessagesChatId)
                            val finalCreatedAt = existing?.createdAt ?: if (fallbackDateMs > 0L) fallbackDateMs else System.currentTimeMillis()
                            val manifestTrashedAt = currentManifest.trashedFolderIds[chat.id.toString()]
                            val isTrashed = (existing?.isTrashed == true) || (manifestTrashedAt != null)
                            val trashedAt = existing?.trashedAt ?: manifestTrashedAt
                            driveDao.insertItems(
                                listOf(
                                    DriveItemEntity(
                                        chat.id,
                                        existing?.name?.takeIf { it.isNotBlank() } ?: chat.title,
                                        0,
                                        "folder",
                                        0,
                                        savedMessagesChatId,
                                        true,
                                        isStarred = existing?.isStarred ?: false,
                                        createdAt = finalCreatedAt,
                                        isSecure = isSecure,
                                        isTrashed = isTrashed,
                                        trashedAt = trashedAt,
                                        isOffline = existing?.isOffline ?: false,
                                        offlinePath = existing?.offlinePath
                                    )
                                )
                            )
                            if (preloadedChannelIds.add(chat.id)) {
                                loadAllDriveItems(chat.id)
                            }
                        }
                    }
                }
            }
        }
    }

    fun createFolder(name: String) {
        telegramClient.send(TdApi.CreateNewSupergroupChat(name, false, true, "TeleNest Folder", null, 0, false)) { result ->
            if (result is TdApi.Chat) fetchFiles()
        }
    }

    fun createFolderForPicker(
        name: String,
        isVirtual: Boolean,
        parentVirtualId: String = "0",
        onCreated: (MoveCopyDestination?) -> Unit
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            onCreated(null)
            return
        }
        if (isVirtual) {
            val id = "vf_${System.nanoTime()}"
            val nowSec = System.currentTimeMillis() / 1000
            val cleanParent = if (
                parentVirtualId.startsWith("vf_") ||
                (parentVirtualId != "0" && parentVirtualId.toLongOrNull() != null)
            ) {
                parentVirtualId
            } else {
                "0"
            }
            val folder = VirtualFolder(id, trimmed, cleanParent, nowSec, "virtual_folder", false, updatedAt = nowSec)
            synchronized(currentManifest) {
                val updatedFolders = currentManifest.virtualFolders.toMutableMap()
                updatedFolders[id] = folder
                currentManifest = currentManifest.copy(virtualFolders = updatedFolders, updatedAt = nowSec)
                saveLocalManifest()
            }
            val targetChat = resolveTargetChatForVirtualParent(cleanParent)
            if (savedMessagesChatId != 0L) {
                val metaJson = json.encodeToString(folder)
                val text = "$VFOLDER_META_PREFIX\n\n$metaJson"
                telegramClient.send(TdApi.SendMessage(savedMessagesChatId, null, null, null, null, TdApi.InputMessageText(TdApi.FormattedText(text, emptyArray()), null, true))) { result ->
                    if (result is TdApi.Message) {
                        synchronized(currentManifest) {
                            val updatedMetaIds = currentManifest.folderMetadataIds.toMutableMap()
                            updatedMetaIds[id] = result.id
                            currentManifest = currentManifest.copy(folderMetadataIds = updatedMetaIds)
                            saveLocalManifest()
                            saveCloudManifest()
                        }
                    }
                }
            } else {
                saveCloudManifest()
            }
            scope.launch {
                val entities = buildVirtualFolderEntities()
                driveDao.syncVirtualFolders(entities)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onCreated(
                        MoveCopyDestination(
                            label = trimmed,
                            isRoot = false,
                            isVirtual = true,
                            targetChatId = targetChat,
                            targetVirtualId = id
                        )
                    )
                }
            }
        } else {
            telegramClient.send(TdApi.CreateNewSupergroupChat(trimmed, false, true, "TeleNest Folder", null, 0, false)) { result ->
                if (result is TdApi.Chat) {
                    scope.launch {
                        driveDao.insertItems(
                            listOf(
                                DriveItemEntity(
                                    result.id,
                                    result.title.ifBlank { trimmed },
                                    0,
                                    "folder",
                                    0,
                                    savedMessagesChatId,
                                    true,
                                    isStarred = false,
                                    createdAt = System.currentTimeMillis(),
                                    isSecure = false
                                )
                            )
                        )
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            onCreated(
                                MoveCopyDestination(
                                    label = result.title.ifBlank { trimmed },
                                    isRoot = false,
                                    isVirtual = false,
                                    targetChatId = result.id,
                                    targetVirtualId = "0"
                                )
                            )
                        }
                    }
                } else {
                    scope.launch(Dispatchers.Main) { onCreated(null) }
                }
            }
        }
    }

    fun createSecureFolder(name: String) {
        telegramClient.send(TdApi.CreateNewSupergroupChat(name, false, false, "TeleNest Secure Folder", null, 0, false)) { result ->
            if (result is TdApi.Chat) {
                val updatedSecureIds = currentManifest.secureFolderChatIds.toMutableSet()
                updatedSecureIds.add(result.id)
                currentManifest = currentManifest.copy(secureFolderChatIds = updatedSecureIds, updatedAt = System.currentTimeMillis() / 1000)
                saveCloudManifest()
                fetchFiles()
            }
        }
    }

    fun uploadFile(
        filePath: String,
        originalFileName: String,
        chatId: Long? = null,
        virtualFolderId: String? = null,
        encryptFile: Boolean = false,
        sendAsDirectMedia: Boolean = false
    ) {
        val targetChatId = chatId ?: savedMessagesChatId
        if (targetChatId == 0L) return
        val vfTag = if (virtualFolderId != null && virtualFolderId != "0") "[VF:$virtualFolderId]" else ""
        val sourceFile = File(filePath)
        val originalTimestampMillis = FileUtils.extractOriginalTimestampMillis(sourceFile)
        val otimeTag = if (originalTimestampMillis > 0L) "[OTIME:$originalTimestampMillis]" else ""
        val shouldEncrypt = encryptFile || isChatSecure(targetChatId) || (virtualFolderId != null && isVirtualFolderSecure(virtualFolderId))
        if (sourceFile.length() > FileUtils.MAX_FILE_SIZE) {
            if (!FileUtils.hasEnoughSpace(context, sourceFile.length())) return
            startTransferService()
            scope.launch {
                val placeholderId = "prep_${System.currentTimeMillis()}"
                transferRepository.addTransfer(0, placeholderId, originalFileName, false, sourceFile.length(), status = context.getString(R.string.splitting_file, ""))
                try {
                    val parts = FileUtils.splitFile(context, sourceFile) { progress ->
                        transferRepository.updateTransferManual(placeholderId, progress, context.getString(R.string.splitting_file_perc, (progress * 100).toInt()))
                    }
                    transferRepository.removeTransfer(placeholderId)
                    val groupId = FileUtils.generateSplitGroupId()
                    val partMessageIds = mutableListOf<Long>()
                    
                    parts.forEachIndexed { index, part ->
                        val caption = "[TD_SPLIT|ID:$groupId|PART:$index/${parts.size}|NAME:$originalFileName]$vfTag$otimeTag"
                        uploadSinglePart(
                            part.absolutePath,
                            originalFileName,
                            targetChatId,
                            caption,
                            virtualFolderId,
                            deleteSourceAfter = true,
                            encryptFile = shouldEncrypt,
                            sendAsDirectMedia = false,
                            originalTimestampMillis = originalTimestampMillis
                        ) { messageId ->
                            if (messageId != 0L) partMessageIds.add(messageId)
                            
                            // If this was the last part, send the Master Metadata
                            if (index == parts.size - 1) {
                                sendSplitMasterMetadata(groupId, originalFileName, sourceFile.length(), FileUtils.getMimeType(originalFileName), parts.size, virtualFolderId, shouldEncrypt, partMessageIds, originalTimestampMillis)
                            }
                        }
                    }
                } catch (e: Exception) {
                    transferRepository.updateTransferManual(placeholderId, 0f, context.getString(R.string.split_fail))
                }
            }
        } else {
            val combinedTags = listOf(vfTag, otimeTag).filter { it.isNotEmpty() }.joinToString("")
            uploadSinglePart(
                filePath = filePath,
                originalFileName = originalFileName,
                targetChatId = targetChatId,
                caption = if (combinedTags.isNotEmpty()) "$originalFileName $combinedTags" else originalFileName,
                virtualFolderId = virtualFolderId,
                encryptFile = shouldEncrypt,
                sendAsDirectMedia = sendAsDirectMedia,
                originalTimestampMillis = originalTimestampMillis
            )
        }
    }

    private fun uploadSinglePart(
        filePath: String,
        originalFileName: String,
        targetChatId: Long,
        caption: String? = null,
        virtualFolderId: String? = null,
        deleteSourceAfter: Boolean = false,
        encryptFile: Boolean = false,
        sendAsDirectMedia: Boolean = false,
        originalTimestampMillis: Long = 0L,
        onMessageSent: (Long) -> Unit = {}
    ) {
        startTransferService()
        val isSecure = encryptFile || isChatSecure(targetChatId) || (virtualFolderId != null && isVirtualFolderSecure(virtualFolderId))
        val password = if (isSecure) {
            secureSessionManager.decryptedPassword.value
                ?: masterPasswordService.getStoredPassword()?.also { secureSessionManager.setSessionPassword(it) }
                ?: masterPasswordService.getOrCreateDefaultPassword().also { secureSessionManager.setSessionPassword(it) }
        } else {
            null
        }

        // Create a unique key for the queue
        val tempId = "up_${System.nanoTime()}"
        
        transferRepository.enqueueTransfer(
            fileId = 0,
            remoteUniqueId = tempId,
            fileName = originalFileName,
            isDownload = false,
            totalSize = File(filePath).length(),
            localPath = if (isSecure) null else filePath // Track by path if not encrypting
        ) {
            var finalPath = filePath
            var finalCaption = caption ?: originalFileName
            var tempEncFile: java.io.File? = null
            var tempStageFile: java.io.File? = null
            
            // Ensure the file on disk has the exact originalFileName so TDLib preserves 100% original filename & metadata
            val sourceDiskFile = java.io.File(filePath)
            val resolvedOrigTs = if (originalTimestampMillis > 0L) originalTimestampMillis else FileUtils.extractOriginalTimestampMillis(sourceDiskFile)
            if (!isSecure && !deleteSourceAfter && sourceDiskFile.exists() && sourceDiskFile.name != originalFileName) {
                try {
                    val exactNameDir = java.io.File(context.cacheDir, "exact_${System.nanoTime()}").apply { mkdirs() }
                    val exactFile = java.io.File(exactNameDir, originalFileName)
                    sourceDiskFile.copyTo(exactFile, overwrite = true)
                    exactFile.setLastModified(resolvedOrigTs)
                    tempStageFile = exactFile
                    finalPath = exactFile.absolutePath
                } catch (_: Exception) {}
            }

            // Set initial status to waiting/preparing
            transferRepository.updateTransferManual(tempId, 0f, context.getString(R.string.status_waiting))
            
            if (isSecure && password != null) {
                try {
                    val originalFile = java.io.File(filePath)
                    val encDir = java.io.File(context.cacheDir, "enc_${System.nanoTime()}").apply { mkdirs() }
                    tempEncFile = java.io.File(encDir, originalFileName)
                    
                    // Update status in UI with throttling enabled; cap progress at 0.99f so encryption never looks like 100% upload completion
                    encryptionManager.encryptFile(originalFile, tempEncFile, password) { progress ->
                        val scaledProgress = (progress * 0.99f).coerceIn(0f, 0.99f)
                        transferRepository.updateTransferManual(
                            tempId, 
                            scaledProgress, 
                            context.getString(R.string.encrypting_perc, (progress * 100).toInt()),
                            throttled = true
                        )
                    }
                    finalPath = tempEncFile.absolutePath
                    finalCaption = if (caption != null) "$caption [ENC]" else "$originalFileName [ENC]"
                    
                    // Reset progress to 0f and status to UPLOADING before starting Telegram upload
                    transferRepository.updateTransferManual(tempId, 0f, TransferRepository.Status.UPLOADING)
                } catch (e: Exception) {
                    transferRepository.updateTransferManual(tempId, 0f, context.getString(R.string.failed_with_error, "Encryption failed"))
                    return@enqueueTransfer 
                }
            }

            // Synchronously bind finalPath to the active transfer BEFORE calling SendMessage so 0%->100% UpdateFile events always match
            transferRepository.updateTransferBindingSync(tempId, tempId, 0, finalPath)
            transferRepository.updateTransferManual(tempId, 0f, TransferRepository.Status.UPLOADING)

            // Build 100% lossless Telegram message content:
            // Never use InputMessagePhoto because Telegram compresses photos, strips EXIF metadata, and discards original filenames.
            // Using InputMessageDocument guarantees 100% bit-for-bit original quality, original EXIF/file metadata, and exact filename for Photos, Videos, Audio, Documents, and APKs.
            val mimeType = FileUtils.getMimeType(originalFileName)
            val generatedThumbInfo = if (!isSecure && (mimeType.startsWith("image/") || mimeType.startsWith("video/"))) {
                generatePreviewThumbnail(filePath, mimeType)
            } else null
            val tdInputThumb = generatedThumbInfo?.let { (thumbPath, w, h) ->
                TdApi.InputThumbnail(TdApi.InputFileLocal(thumbPath), w, h)
            }

            val formattedCaption = TdApi.FormattedText(finalCaption, emptyArray())
            val inputMessageContent: TdApi.InputMessageContent = when {
                !isSecure && sendAsDirectMedia && mimeType.startsWith("video/") -> {
                    TdApi.InputMessageVideo().apply {
                        video = TdApi.InputFileLocal(finalPath)
                        thumbnail = tdInputThumb
                        addedStickerFileIds = intArrayOf()
                        supportsStreaming = true
                        this.caption = formattedCaption
                    }
                }
                else -> {
                    // Always set disableContentTypeDetection = true for lossless documents so Telegram
                    // never re-encodes, alters headers, or strips metadata from any uploaded file.
                    TdApi.InputMessageDocument(TdApi.InputFileLocal(finalPath), tdInputThumb, true, formattedCaption)
                }
            }

            val resolvedVfId = virtualFolderId?.takeIf { it.isNotEmpty() } ?: "0"

            // Step 2: Send message to Telegram (this starts the upload) and wait for initial response
            val sendAck = kotlinx.coroutines.CompletableDeferred<Pair<String, kotlinx.coroutines.CompletableDeferred<TdApi.Message?>?>>()
            telegramClient.send(TdApi.SendMessage(targetChatId, null, null, null, null, inputMessageContent)) { result ->
                if (result is TdApi.Message) {
                    onMessageSent(result.id)
                    val content = result.content
                    val file = when (content) {
                        is TdApi.MessageDocument -> content.document.document
                        is TdApi.MessagePhoto -> content.photo.sizes.lastOrNull()?.photo
                        is TdApi.MessageVideo -> content.video.video
                        is TdApi.MessageAudio -> content.audio.audio
                        else -> null
                    }
                    
                    if (resolvedVfId != "0") {
                        synchronized(currentManifest) {
                            val updatedMappings = currentManifest.fileMappings.toMutableMap()
                            updatedMappings[result.id.toString()] = resolvedVfId
                            currentManifest = currentManifest.copy(fileMappings = updatedMappings)
                            saveLocalManifest()
                        }
                    }

                    val exactOriginalSize = if (!isSecure && sourceDiskFile.exists() && sourceDiskFile.length() > 0L) {
                        sourceDiskFile.length()
                    } else null
                    saveLocalFileOriginalTime(result.id, targetChatId, resolvedOrigTs)
                    pendingSendMetaMap[result.id] = PendingSendMeta(
                        fileName = originalFileName,
                        virtualFolderId = resolvedVfId,
                        isEncrypted = isSecure,
                        thumbnailPath = generatedThumbInfo?.first,
                        localPath = if (isSecure) null else filePath,
                        originalTimestamp = resolvedOrigTs
                    )
                    // Insert provisional item immediately so user sees the file in the folder right away
                    scope.launch {
                        val provisionalEntity = mapMessageToEntity(targetChatId, result)
                        if (provisionalEntity != null) {
                            driveDao.insertItemsPreservingStarred(
                                listOf(
                                    provisionalEntity.copy(
                                        name = originalFileName,
                                        size = exactOriginalSize ?: provisionalEntity.size,
                                        virtualParentId = resolvedVfId,
                                        isEncrypted = isSecure,
                                        createdAt = resolvedOrigTs,
                                        thumbnailPath = provisionalEntity.thumbnailPath ?: generatedThumbInfo?.first
                                    )
                                )
                            )
                        }
                    }

                    val messageSendDeferred: kotlinx.coroutines.CompletableDeferred<TdApi.Message?>? = if (result.sendingState != null) {
                        val alreadyDone = completedMessageSends.remove(result.id)
                        if (alreadyDone != null) {
                            kotlinx.coroutines.CompletableDeferred<TdApi.Message?>(alreadyDone)
                        } else {
                            pendingMessageSends.getOrPut(result.id) { kotlinx.coroutines.CompletableDeferred() }
                        }
                    } else {
                        kotlinx.coroutines.CompletableDeferred<TdApi.Message?>(result)
                    }

                    if (file != null) {
                        val realUniqueId: String = file.remote.uniqueId.ifEmpty { tempId }
                        transferRepository.updateRemoteUniqueId(tempId, realUniqueId, file.id, finalPath)
                        
                        if (file.remote.isUploadingCompleted && result.sendingState == null) {
                            transferRepository.updateTransferManual(realUniqueId, 1.0f, TransferRepository.Status.COMPLETED)
                        }
                        sendAck.complete(Pair(realUniqueId, messageSendDeferred))
                    } else {
                        transferRepository.updateTransferManual(tempId, 1.0f, TransferRepository.Status.COMPLETED)
                        sendAck.complete(Pair(tempId, messageSendDeferred))
                    }
                } else {
                    transferRepository.updateTransferManual(tempId, 0f, context.getString(R.string.failed_with_error, "SendMessage failed"))
                    sendAck.complete(Pair(tempId, null))
                }
            }
            
            val (activeUniqueId, messageSendDeferred) = sendAck.await()
            if (messageSendDeferred != null) {
                // Wait until TDLib confirms the message send succeeded (or failed) so tempEncFile is NEVER deleted during upload
                val sentMsg = messageSendDeferred.await()
                if (sentMsg != null) {
                    val exactOriginalSize = if (!isSecure && sourceDiskFile.exists() && sourceDiskFile.length() > 0L) {
                        sourceDiskFile.length()
                    } else null
                    val mapped = mapMessageToEntity(sentMsg.chatId, sentMsg)
                    if (mapped != null) {
                        saveLocalFileOriginalTime(sentMsg.id, sentMsg.chatId, resolvedOrigTs)
                        driveDao.insertItemsPreservingStarred(
                            listOf(
                                mapped.copy(
                                    name = originalFileName,
                                    size = exactOriginalSize ?: mapped.size,
                                    virtualParentId = resolvedVfId,
                                    isEncrypted = isSecure,
                                    createdAt = resolvedOrigTs,
                                    thumbnailPath = mapped.thumbnailPath ?: generatedThumbInfo?.first
                                )
                            )
                        )
                    }
                    transferRepository.updateTransferManual(activeUniqueId, 1.0f, TransferRepository.Status.COMPLETED)
                    if (activeUniqueId != tempId) {
                        transferRepository.updateTransferManual(tempId, 1.0f, TransferRepository.Status.COMPLETED)
                    }
                } else {
                    transferRepository.updateTransferManual(activeUniqueId, 0f, TransferRepository.Status.FAILED)
                }
            } else {
                transferRepository.waitForCompletion(activeUniqueId)
            }
            
            // CRITICAL: Cleanup temp encrypted file ONLY AFTER message send succeeded
            try {
                tempEncFile?.let { 
                    val parent = it.parentFile
                    if (it.exists()) {
                        it.delete()
                        Log.d("DriveRepo", "Cleaned up temp encrypted file: ${it.absolutePath}")
                    }
                    if (parent != null && parent != context.cacheDir && parent.name.startsWith("enc_")) {
                        parent.delete()
                    }
                }
                tempStageFile?.let {
                    val parent = it.parentFile
                    if (it.exists()) it.delete()
                    if (parent != null && parent != context.cacheDir && parent.name.startsWith("exact_")) {
                        parent.delete()
                    }
                }
                // Cleanup split part if requested
                if (deleteSourceAfter) {
                    val sourceFile = java.io.File(filePath)
                    if (sourceFile.exists()) {
                        sourceFile.delete()
                        Log.d("DriveRepo", "Cleaned up source part: $filePath")
                    }
                }
            } catch (e: Exception) {
                Log.e("DriveRepo", "Failed to cleanup temp files", e)
            }
        }
    }

    private fun sendFileMetadata(messageId: Long, fileName: String, virtualFolderId: String?, isEncrypted: Boolean, originalTimestamp: Long = 0L) {
        val meta = FileMetadata(messageId, fileName, virtualFolderId, isEncrypted, originalTimestamp)
        val text = "$FILE_META_PREFIX\n\n${json.encodeToString(meta)}"
        telegramClient.send(TdApi.SendMessage(savedMessagesChatId, null, null, null, null, TdApi.InputMessageText(TdApi.FormattedText(text, emptyArray()), null, true))) { result ->
            if (result is TdApi.Message) {
                synchronized(currentManifest) {
                    val updatedFileMetaIds = currentManifest.fileMetadataIds.toMutableMap()
                    updatedFileMetaIds[messageId.toString()] = result.id
                    currentManifest = currentManifest.copy(fileMetadataIds = updatedFileMetaIds)
                    saveLocalManifest()
                }
            }
        }
    }

    private fun sendSplitMasterMetadata(groupId: String, name: String, size: Long, mime: String, parts: Int, vfId: String?, enc: Boolean, ids: List<Long>, originalTimestamp: Long = 0L) {
        val master = SplitFileMaster(groupId, name, size, mime, parts, vfId, enc, ids, originalTimestamp = originalTimestamp)
        val text = "$FILE_META_PREFIX\n\n${json.encodeToString(master)}"
        telegramClient.send(TdApi.SendMessage(savedMessagesChatId, null, null, null, null, TdApi.InputMessageText(TdApi.FormattedText(text, emptyArray()), null, true))) { result ->
            val finalMaster = if (result is TdApi.Message) master.copy(metadataMessageId = result.id) else master
            synchronized(currentManifest) {
                val updatedMasters = currentManifest.splitFileMasters.toMutableMap()
                updatedMasters[groupId] = finalMaster
                currentManifest = currentManifest.copy(splitFileMasters = updatedMasters)
                saveLocalManifest()
            }
            saveCloudManifest()
        }
    }

    private fun generatePreviewThumbnail(sourcePath: String, mimeType: String): Triple<String, Int, Int>? {
        return try {
            val thumbsDir = java.io.File(context.cacheDir, "thumbs").apply { mkdirs() }
            val rawBitmap: android.graphics.Bitmap? = when {
                mimeType.startsWith("image/") -> {
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeFile(sourcePath, bounds)
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) null
                    else {
                        var sampleSize = 1
                        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
                        while (maxDim / sampleSize > 640) {
                            sampleSize *= 2
                        }
                        val decodeOpts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sampleSize }
                        android.graphics.BitmapFactory.decodeFile(sourcePath, decodeOpts)
                    }
                }
                mimeType.startsWith("video/") -> {
                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(sourcePath)
                        retriever.getFrameAtTime(1_000_000L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            ?: retriever.frameAtTime
                    } finally {
                        try { retriever.release() } catch (_: Exception) {}
                    }
                }
                else -> null
            }
            val bitmap: android.graphics.Bitmap = rawBitmap ?: return null

            val maxSide = 320
            val w = bitmap.width
            val h = bitmap.height
            val scale = minOf(maxSide.toFloat() / w.toFloat(), maxSide.toFloat() / h.toFloat(), 1f)
            val targetW = (w * scale).toInt().coerceAtLeast(1)
            val targetH = (h * scale).toInt().coerceAtLeast(1)
            val scaled: android.graphics.Bitmap = if (targetW != w || targetH != h) {
                android.graphics.Bitmap.createScaledBitmap(bitmap, targetW, targetH, true).also {
                    if (it != bitmap) bitmap.recycle()
                }
            } else bitmap

            val outFile = java.io.File(thumbsDir, "thumb_${System.nanoTime()}.jpg")
            java.io.FileOutputStream(outFile).use { out ->
                scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
            }
            scaled.recycle()
            Triple(outFile.absolutePath, targetW, targetH)
        } catch (e: Exception) {
            Log.e("DriveRepo", "Failed to generate preview thumbnail: ${e.message}")
            null
        }
    }

    fun downloadFile(messageId: Long, chatId: Long, fileName: String) {
        telegramClient.send(TdApi.GetMessage(chatId, messageId)) { result ->
            if (result is TdApi.Message) {
                val tdFile = when (val content = result.content) {
                    is TdApi.MessageDocument -> content.document.document
                    is TdApi.MessagePhoto -> content.photo.sizes.lastOrNull()?.photo
                    is TdApi.MessageVideo -> content.video.video
                    is TdApi.MessageAudio -> content.audio.audio
                    else -> null
                } ?: return@send

                val fileId = tdFile.id
                val remoteUniqueId = tdFile.remote.uniqueId
                val expectedSize = if (tdFile.expectedSize > 0L) tdFile.expectedSize else tdFile.size

                if (fileId != 0) {
                    val existingLocalPath = tdFile.local.path
                    if (tdFile.local.isDownloadingCompleted && existingLocalPath.isNotEmpty() && java.io.File(existingLocalPath).exists()) {
                        scope.launch {
                            driveDao.updateLocalPathByUniqueId(remoteUniqueId, existingLocalPath)
                            transferRepository.saveToPublicDownloads(existingLocalPath, fileName)
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                Toast.makeText(context, context.getString(R.string.save_success), Toast.LENGTH_SHORT).show()
                            }
                        }
                        return@send
                    }

                    startTransferService()
                    val queueKey = remoteUniqueId.ifEmpty { "dl_${messageId}_$fileId" }
                    transferRepository.enqueueTransfer(
                        fileId,
                        queueKey,
                        fileName,
                        true,
                        expectedSize,
                        localPath = null
                    ) {
                        telegramClient.send(TdApi.DownloadFile(fileId, 16, 0, 0, false))
                        transferRepository.waitForCompletion(queueKey)
                    }
                }
            }
        }
    }

    fun downloadForPreview(messageId: Long, chatId: Long, fileName: String) {
        telegramClient.send(TdApi.GetMessage(chatId, messageId)) { result ->
            if (result is TdApi.Message) {
                val tdFile = when (val content = result.content) {
                    is TdApi.MessageDocument -> content.document.document
                    is TdApi.MessagePhoto -> content.photo.sizes.lastOrNull()?.photo
                    is TdApi.MessageVideo -> content.video.video
                    is TdApi.MessageAudio -> content.audio.audio
                    else -> null
                } ?: return@send

                val fileId = tdFile.id
                val remoteUniqueId = tdFile.remote.uniqueId
                val expectedSize = if (tdFile.expectedSize > 0L) tdFile.expectedSize else tdFile.size

                if (fileId != 0) {
                    val existingLocalPath = tdFile.local.path
                    if (tdFile.local.isDownloadingCompleted && existingLocalPath.isNotEmpty() && java.io.File(existingLocalPath).exists()) {
                        scope.launch {
                            driveDao.updateLocalPathByUniqueId(remoteUniqueId, existingLocalPath)
                        }
                    } else {
                        transferRepository.trackPreviewDownload(fileId, remoteUniqueId, fileName, expectedSize)
                        telegramClient.send(TdApi.DownloadFile(fileId, 32, 0, 0, false))
                    }
                }
            }
        }
    }

    fun saveToPublicStorage(file: DriveItem.File) {
        val source = file.localPath?.let { java.io.File(it) } ?: return
        if (!source.exists()) return
        scope.launch(Dispatchers.IO) {
            try {
                val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val dest = java.io.File(downloadDir, file.name)
                if (file.isEncrypted) {
                    val password = secureSessionManager.decryptedPassword.value
                        ?: masterPasswordService.getStoredPassword()?.also { secureSessionManager.setSessionPassword(it) }
                        ?: masterPasswordService.getOrCreateDefaultPassword().also { secureSessionManager.setSessionPassword(it) }
                    encryptionManager.decryptFile(source, dest, password) { }
                } else {
                    source.copyTo(dest, overwrite = true)
                }
                FileUtils.restoreOriginalTimestampAndScan(context, dest, file.createdAt)
                scope.launch(Dispatchers.Main) { Toast.makeText(context, context.getString(R.string.save_success), Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) { }
        }
    }

    fun toggleStarred(item: DriveItem) {
        scope.launch { driveDao.updateStarred(item.id, item.parentChatId, !item.isStarred) }
    }

    fun renameItem(item: DriveItem, newName: String) {
        scope.launch {
            if (item is DriveItem.Folder) {
                if (item.isVirtual) {
                    val virtualId = item.virtualId ?: return@launch
                    val nowSec = System.currentTimeMillis() / 1000
                    saveLocalRename(virtualId, newName, nowSec)
                    val updatedFolder: VirtualFolder
                    val metaMsgId: Long?
                    synchronized(currentManifest) {
                        val updatedFolders = currentManifest.virtualFolders.toMutableMap()
                        val existing = updatedFolders[virtualId]
                        updatedFolder = if (existing != null) {
                            existing.copy(name = newName, updatedAt = nowSec)
                        } else {
                            VirtualFolder(
                                id = virtualId,
                                name = newName,
                                parentId = item.virtualParentId ?: "0",
                                createdAt = nowSec,
                                type = "virtual_folder",
                                isSecure = item.isSecure,
                                updatedAt = nowSec
                            )
                        }
                        updatedFolders[virtualId] = updatedFolder
                        currentManifest = currentManifest.copy(
                            virtualFolders = updatedFolders,
                            updatedAt = nowSec
                        )
                        metaMsgId = currentManifest.folderMetadataIds[virtualId]
                        saveLocalManifest()
                    }
                    driveDao.renameItem(item.id, item.parentChatId, newName)
                    driveDao.renameVirtualFolderByVirtualId(virtualId, newName)
                    syncVirtualFoldersToDb()
                    saveCloudManifest()

                    // Update or create the distributed #TELEDRIVE_VFOLDER_DATA message in Saved Messages so it never reverts
                    if (savedMessagesChatId != 0L) {
                        val metaJson = json.encodeToString(updatedFolder)
                        val text = "$VFOLDER_META_PREFIX\n\n$metaJson"
                        val formatted = TdApi.FormattedText(text, emptyArray())
                        if (metaMsgId != null && metaMsgId != 0L) {
                            telegramClient.send(
                                TdApi.EditMessageText(
                                    savedMessagesChatId,
                                    metaMsgId,
                                    null,
                                    TdApi.InputMessageText(formatted, null, true)
                                )
                            ) { editRes ->
                                if (editRes is TdApi.Error && !editRes.message.contains("MESSAGE_NOT_MODIFIED", ignoreCase = true)) {
                                    telegramClient.send(TdApi.SendMessage(savedMessagesChatId, null, null, null, null, TdApi.InputMessageText(formatted, null, true))) { sendRes ->
                                        if (sendRes is TdApi.Message) {
                                            synchronized(currentManifest) {
                                                val updatedMetaIds = currentManifest.folderMetadataIds.toMutableMap()
                                                updatedMetaIds[virtualId] = sendRes.id
                                                currentManifest = currentManifest.copy(folderMetadataIds = updatedMetaIds)
                                                saveLocalManifest()
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            telegramClient.send(TdApi.SendMessage(savedMessagesChatId, null, null, null, null, TdApi.InputMessageText(formatted, null, true))) { sendRes ->
                                if (sendRes is TdApi.Message) {
                                    synchronized(currentManifest) {
                                        val updatedMetaIds = currentManifest.folderMetadataIds.toMutableMap()
                                        updatedMetaIds[virtualId] = sendRes.id
                                        currentManifest = currentManifest.copy(folderMetadataIds = updatedMetaIds)
                                        saveLocalManifest()
                                    }
                                }
                            }
                        }
                    }
                } else {
                    telegramClient.send(TdApi.SetChatTitle(item.telegramChatId, newName)) {}
                    driveDao.renameItem(item.id, item.parentChatId, newName)
                }
            } else if (item is DriveItem.File) {
                saveLocalFileRename(item.id, item.parentChatId, newName)
                driveDao.renameItem(item.id, item.parentChatId, newName)
                if (savedMessagesChatId != 0L && item.splitGroupId == null) {
                    sendFileMetadata(item.id, newName, item.virtualParentId, item.isEncrypted)
                }
                telegramClient.send(TdApi.GetMessage(item.parentChatId, item.id)) { result ->
                    if (result is TdApi.Message) {
                        val currentCaption = when (val content = result.content) {
                            is TdApi.MessageDocument -> content.caption.text
                            is TdApi.MessagePhoto -> content.caption.text
                            is TdApi.MessageVideo -> content.caption.text
                            is TdApi.MessageAudio -> content.caption.text
                            else -> ""
                        }
                        val tagRegex = Regex("\\[(ENC|TD_SPLIT|VF:|ID:|PART:|NAME:).*?\\]")
                        val tags = tagRegex.findAll(currentCaption).map { it.value }.toList()
                        val updatedTags = tags.map { tag ->
                            if (tag.startsWith("[TD_SPLIT|") && tag.contains("NAME:")) tag.replace(Regex("NAME:.*?(?=\\]|\\|)"), "NAME:$newName")
                            else tag
                        }
                        val newCaptionText = if (updatedTags.isNotEmpty()) {
                            if (updatedTags.any { it.startsWith("[TD_SPLIT") }) updatedTags.joinToString("")
                            else "$newName ${updatedTags.joinToString("")}"
                        } else newName
                        telegramClient.send(TdApi.EditMessageCaption(item.parentChatId, item.id, null, TdApi.FormattedText(newCaptionText, emptyArray()), false)) { editResult ->
                            if (editResult is TdApi.Message) scope.launch { driveDao.renameItem(item.id, item.parentChatId, newName) }
                        }
                    }
                }
            }
        }
    }

    fun moveToTrash(items: List<DriveItem>) {
        if (items.isEmpty()) return
        val now = System.currentTimeMillis()
        scope.launch {
            val allDbItems = driveDao.getAllItemsSync()
            val allDbFolders = allDbItems.filter { it.isFolder }
            val trashedFiles = mutableMapOf<String, Long>()
            val trashedFolders = mutableMapOf<String, Long>()

            items.forEach { item ->
                when (item) {
                    is DriveItem.File -> {
                        driveDao.updateTrashState(item.id, item.parentChatId, true, now)
                        if (item.splitGroupId != null) {
                            driveDao.updateTrashStateBySplitGroup(item.splitGroupId, item.parentChatId, true, now)
                            allDbItems.filter { !it.isFolder && it.splitGroupId == item.splitGroupId }.forEach { part ->
                                trashedFiles["${part.parentChatId}_${part.id}"] = now
                                trashedFiles[part.id.toString()] = now
                            }
                        }
                        trashedFiles["${item.parentChatId}_${item.id}"] = now
                        trashedFiles[item.id.toString()] = now
                    }
                    is DriveItem.Folder -> {
                        driveDao.updateTrashState(item.id, item.parentChatId, true, now)
                        if (item.isVirtual && item.virtualId != null) {
                            val descendantVirtualIds = collectDescendantVirtualIds(item.virtualId, allDbFolders)
                            descendantVirtualIds.forEach { vid ->
                                driveDao.updateVirtualFolderTrashState(vid, true, now)
                                driveDao.updateTrashStateByVirtualParent(vid, true, now)
                                trashedFolders[vid] = now
                            }
                            allDbItems.filter { !it.isFolder && (it.virtualParentId ?: "0") in descendantVirtualIds }.forEach { childFile ->
                                driveDao.updateTrashState(childFile.id, childFile.parentChatId, true, now)
                                trashedFiles["${childFile.parentChatId}_${childFile.id}"] = now
                                trashedFiles[childFile.id.toString()] = now
                            }
                        } else {
                            driveDao.updateTrashStateByChat(item.telegramChatId, true, now)
                            trashedFolders[item.telegramChatId.toString()] = now
                            allDbItems.filter { !it.isFolder && it.parentChatId == item.telegramChatId }.forEach { childFile ->
                                trashedFiles["${childFile.parentChatId}_${childFile.id}"] = now
                                trashedFiles[childFile.id.toString()] = now
                            }
                            allDbFolders.filter { it.isVirtual && it.parentChatId == item.telegramChatId && it.virtualId != null }.forEach { vfEntity ->
                                val vid = vfEntity.virtualId!!
                                driveDao.updateVirtualFolderTrashState(vid, true, now)
                                trashedFolders[vid] = now
                            }
                        }
                    }
                }
            }

            synchronized(currentManifest) {
                val updatedTrashedFiles = currentManifest.trashedFileIds.toMutableMap().apply { putAll(trashedFiles) }
                val updatedTrashedFolders = currentManifest.trashedFolderIds.toMutableMap().apply { putAll(trashedFolders) }
                currentManifest = currentManifest.copy(
                    trashedFileIds = updatedTrashedFiles,
                    trashedFolderIds = updatedTrashedFolders,
                    updatedAt = System.currentTimeMillis() / 1000
                )
                saveLocalManifest()
            }
            saveCloudManifest()
        }
    }

    fun restoreFromTrash(items: List<DriveItem>) {
        if (items.isEmpty()) return
        scope.launch {
            val allDbItems = driveDao.getAllItemsSync()
            val allDbFolders = allDbItems.filter { it.isFolder }
            val allVirtualFoldersById = allDbFolders.filter { it.isVirtual && it.virtualId != null }.associateBy { it.virtualId!! }
            val restoredFileKeys = mutableSetOf<String>()
            val restoredFolderKeys = mutableSetOf<String>()

            suspend fun restoreAncestorFoldersIfNeeded(parentChatId: Long, virtualParentId: String?) {
                if (parentChatId != 0L && parentChatId != savedMessagesChatId) {
                    driveDao.updateTrashState(parentChatId, savedMessagesChatId, false, null)
                    restoredFolderKeys.add(parentChatId.toString())
                }
                var curVf = virtualParentId ?: "0"
                val visited = mutableSetOf<String>()
                var depth = 0
                while (curVf.isNotEmpty() && curVf != "0" && curVf.startsWith("vf_") && depth < 100 && visited.add(curVf)) {
                    driveDao.updateVirtualFolderTrashState(curVf, false, null)
                    restoredFolderKeys.add(curVf)
                    val manifestVf = currentManifest.virtualFolders[curVf]
                    val dbVf = allVirtualFoldersById[curVf]
                    curVf = manifestVf?.parentId ?: dbVf?.virtualParentId ?: "0"
                    depth++
                }
            }

            items.forEach { item ->
                when (item) {
                    is DriveItem.File -> {
                        driveDao.updateTrashState(item.id, item.parentChatId, false, null)
                        if (item.splitGroupId != null) {
                            driveDao.updateTrashStateBySplitGroup(item.splitGroupId, item.parentChatId, false, null)
                            allDbItems.filter { !it.isFolder && it.splitGroupId == item.splitGroupId }.forEach { part ->
                                restoredFileKeys.add("${part.parentChatId}_${part.id}")
                                restoredFileKeys.add(part.id.toString())
                            }
                        }
                        restoredFileKeys.add("${item.parentChatId}_${item.id}")
                        restoredFileKeys.add(item.id.toString())

                        val vPid = item.virtualParentId
                        if (!vPid.isNullOrEmpty() && vPid != "0" && vPid.startsWith("vf_")) {
                            val folderExists = currentManifest.virtualFolders.containsKey(vPid) || allVirtualFoldersById.containsKey(vPid)
                            if (!folderExists || currentManifest.deletedFolderIds.containsKey(vPid)) {
                                // Parent virtual folder was permanently deleted; restore file to root My Drive
                                driveDao.updateVirtualParentInChat(item.id, item.parentChatId, "0")
                                saveLocalFileVirtualParent(item.id, item.parentChatId, "0")
                                synchronized(currentManifest) {
                                    val updatedMappings = currentManifest.fileMappings.toMutableMap()
                                    updatedMappings[item.id.toString()] = "0"
                                    currentManifest = currentManifest.copy(fileMappings = updatedMappings)
                                }
                            } else {
                                restoreAncestorFoldersIfNeeded(item.parentChatId, vPid)
                            }
                        } else {
                            restoreAncestorFoldersIfNeeded(item.parentChatId, null)
                        }
                    }
                    is DriveItem.Folder -> {
                        driveDao.updateTrashState(item.id, item.parentChatId, false, null)
                        if (item.isVirtual && item.virtualId != null) {
                            val descendantVirtualIds = collectDescendantVirtualIds(item.virtualId, allDbFolders)
                            descendantVirtualIds.forEach { vid ->
                                driveDao.updateVirtualFolderTrashState(vid, false, null)
                                driveDao.updateTrashStateByVirtualParent(vid, false, null)
                                restoredFolderKeys.add(vid)
                            }
                            allDbItems.filter { !it.isFolder && (it.virtualParentId ?: "0") in descendantVirtualIds }.forEach { childFile ->
                                driveDao.updateTrashState(childFile.id, childFile.parentChatId, false, null)
                                restoredFileKeys.add("${childFile.parentChatId}_${childFile.id}")
                                restoredFileKeys.add(childFile.id.toString())
                            }
                            restoreAncestorFoldersIfNeeded(item.parentChatId, item.virtualParentId)
                        } else {
                            driveDao.updateTrashStateByChat(item.telegramChatId, false, null)
                            restoredFolderKeys.add(item.telegramChatId.toString())
                            allDbItems.filter { !it.isFolder && it.parentChatId == item.telegramChatId }.forEach { childFile ->
                                restoredFileKeys.add("${childFile.parentChatId}_${childFile.id}")
                                restoredFileKeys.add(childFile.id.toString())
                            }
                            allDbFolders.filter { it.isVirtual && it.parentChatId == item.telegramChatId && it.virtualId != null }.forEach { vfEntity ->
                                val vid = vfEntity.virtualId!!
                                driveDao.updateVirtualFolderTrashState(vid, false, null)
                                restoredFolderKeys.add(vid)
                            }
                        }
                    }
                }
            }

            synchronized(currentManifest) {
                val updatedTrashedFiles = currentManifest.trashedFileIds.toMutableMap().apply {
                    restoredFileKeys.forEach { remove(it) }
                }
                val updatedTrashedFolders = currentManifest.trashedFolderIds.toMutableMap().apply {
                    restoredFolderKeys.forEach { remove(it) }
                }
                currentManifest = currentManifest.copy(
                    trashedFileIds = updatedTrashedFiles,
                    trashedFolderIds = updatedTrashedFolders,
                    updatedAt = System.currentTimeMillis() / 1000
                )
                saveLocalManifest()
            }
            saveCloudManifest()
        }
    }

    fun emptyTrash() {
        scope.launch {
            val trashedEntities = driveDao.getTrashedItemsSync()
            if (trashedEntities.isEmpty()) return@launch
            val items = trashedEntities.map { mapEntityToDriveItem(it, emptyList(), emptyList()) }
            permanentlyDeleteItems(items)
        }
    }

    suspend fun purgeExpiredTrashItems() {
        try {
            val cutoff = System.currentTimeMillis() - TRASH_RETENTION_MS
            val expiredEntities = driveDao.getExpiredTrashedItemsSync(cutoff)
            if (expiredEntities.isNotEmpty()) {
                val items = expiredEntities.map { mapEntityToDriveItem(it, emptyList(), emptyList()) }
                permanentlyDeleteItems(items)
            }
        } catch (e: Exception) {
            Log.e("DriveRepo", "Error purging expired trash items: ${e.message}")
        }
    }

    private suspend fun copyToOfflineStorage(messageId: Long, chatId: Long, sourcePath: String): String? {
        return try {
            val src = File(sourcePath)
            if (!src.exists()) return null
            val entity = driveDao.getItemById(messageId, chatId)
            val safeName = (entity?.name ?: src.name).replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val offlineDir = File(context.filesDir, "offline_files").apply { mkdirs() }
            val destFile = File(offlineDir, "off_${chatId}_${messageId}_$safeName")
            src.copyTo(destFile, overwrite = true)
            driveDao.updateOfflineState(messageId, chatId, true, destFile.absolutePath)
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e("DriveRepo", "Failed to copy to offline storage: ${e.message}")
            null
        }
    }

    fun setItemsOfflineAvailable(items: List<DriveItem>, makeOffline: Boolean) {
        if (items.isEmpty()) return
        scope.launch {
            val filesToProcess = mutableListOf<DriveItem.File>()
            items.forEach { item ->
                when (item) {
                    is DriveItem.File -> filesToProcess.add(item)
                    is DriveItem.Folder -> {
                        driveDao.updateOfflineState(item.id, item.parentChatId, makeOffline, null)
                        if (item.isVirtual && item.virtualId != null) {
                            val children = driveDao.getItemsByVirtualParentSync(item.virtualId)
                            children.filter { !it.isFolder }.forEach { child ->
                                (mapEntityToDriveItem(child, emptyList(), emptyList()) as? DriveItem.File)?.let {
                                    filesToProcess.add(it)
                                }
                            }
                        }
                    }
                }
            }

            filesToProcess.distinctBy { "${it.parentChatId}_${it.id}" }.forEach { file ->
                if (!makeOffline) {
                    try {
                        file.offlinePath?.let { File(it).delete() }
                    } catch (_: Exception) {}
                    driveDao.updateOfflineState(file.id, file.parentChatId, false, null)
                } else {
                    val existingLocal = file.localPath?.let { File(it) }?.takeIf { it.exists() }
                    if (existingLocal != null) {
                        copyToOfflineStorage(file.id, file.parentChatId, existingLocal.absolutePath)
                    } else {
                        driveDao.updateOfflineState(file.id, file.parentChatId, true, null)
                        telegramClient.send(TdApi.GetMessage(file.parentChatId, file.id)) { result ->
                            if (result is TdApi.Message) {
                                val tdFile = when (val content = result.content) {
                                    is TdApi.MessageDocument -> content.document.document
                                    is TdApi.MessagePhoto -> content.photo.sizes.lastOrNull()?.photo
                                    is TdApi.MessageVideo -> content.video.video
                                    is TdApi.MessageAudio -> content.audio.audio
                                    else -> null
                                } ?: return@send
                                val localPath = tdFile.local.path
                                if (tdFile.local.isDownloadingCompleted && localPath.isNotEmpty() && File(localPath).exists()) {
                                    scope.launch {
                                        driveDao.updateLocalPathByUniqueId(tdFile.remote.uniqueId, localPath)
                                        copyToOfflineStorage(file.id, file.parentChatId, localPath)
                                    }
                                } else if (tdFile.id != 0) {
                                    pendingOfflineDownloads[tdFile.remote.uniqueId] = Pair(file.id, file.parentChatId)
                                    val expectedSize = if (tdFile.expectedSize > 0L) tdFile.expectedSize else tdFile.size
                                    transferRepository.trackPreviewDownload(tdFile.id, tdFile.remote.uniqueId, file.name, expectedSize)
                                    telegramClient.send(TdApi.DownloadFile(tdFile.id, 32, 0, 0, false))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun permanentlyDeleteItems(items: List<DriveItem>) {
        scope.launch {
            // Group files by their parentChatId for correct Telegram deletion
            val filesByChat = items.filterIsInstance<DriveItem.File>().groupBy { it.parentChatId }
            filesByChat.forEach { (chatId, files) ->
                files.forEach { f ->
                    try { f.offlinePath?.let { File(it).delete() } } catch (_: Exception) {}
                }
                val ids = files.map { it.id }.toLongArray()
                telegramClient.send(TdApi.DeleteMessages(chatId, ids, true)) {
                    scope.launch { files.forEach { f -> driveDao.deleteItemCompletely(f.id, chatId) } }
                }
            }

            // Clean up manifest for deleted files
            if (filesByChat.isNotEmpty()) {
                synchronized(currentManifest) {
                    val updatedFileMetaIds = currentManifest.fileMetadataIds.toMutableMap()
                    val updatedFileMappings = currentManifest.fileMappings.toMutableMap()
                    val updatedSplitMasters = currentManifest.splitFileMasters.toMutableMap()
                    val updatedTrashedFiles = currentManifest.trashedFileIds.toMutableMap()
                    val metadataIdsToDelete = mutableListOf<Long>()

                    items.filterIsInstance<DriveItem.File>().forEach { file ->
                        updatedFileMetaIds.remove(file.id.toString())?.let { metadataIdsToDelete.add(it) }
                        updatedFileMappings.remove(file.id.toString())
                        updatedTrashedFiles.remove("${file.parentChatId}_${file.id}")
                        updatedTrashedFiles.remove(file.id.toString())
                        file.splitGroupId?.let { gid ->
                            updatedSplitMasters.remove(gid)?.metadataMessageId?.let { metadataIdsToDelete.add(it) }
                        }
                    }

                    currentManifest = currentManifest.copy(
                        fileMetadataIds = updatedFileMetaIds,
                        fileMappings = updatedFileMappings,
                        splitFileMasters = updatedSplitMasters,
                        trashedFileIds = updatedTrashedFiles,
                        updatedAt = System.currentTimeMillis() / 1000
                    )
                    
                    if (metadataIdsToDelete.isNotEmpty() && savedMessagesChatId != 0L) {
                        telegramClient.send(TdApi.DeleteMessages(savedMessagesChatId, metadataIdsToDelete.toLongArray(), true)) {}
                    }
                }
                saveCloudManifest()
            }

            val physicalFolders = items.filterIsInstance<DriveItem.Folder>().filter { !it.isVirtual }
            physicalFolders.forEach { folder ->
                synchronized(currentManifest) {
                    val updatedTrashedFolders = currentManifest.trashedFolderIds.toMutableMap()
                    val updatedTrashedFiles = currentManifest.trashedFileIds.toMutableMap()
                    updatedTrashedFolders.remove(folder.telegramChatId.toString())
                    updatedTrashedFiles.keys.removeIf { it.startsWith("${folder.telegramChatId}_") }
                    currentManifest = currentManifest.copy(
                        trashedFolderIds = updatedTrashedFolders,
                        trashedFileIds = updatedTrashedFiles
                    )
                    saveLocalManifest()
                }
                telegramClient.send(TdApi.DeleteChat(folder.telegramChatId)) {
                    scope.launch {
                        driveDao.deleteItemsByChat(folder.telegramChatId)
                        driveDao.deleteItemCompletely(folder.telegramChatId, folder.parentChatId)
                    }
                }
            }
            val virtualFolders = items.filterIsInstance<DriveItem.Folder>().filter { it.isVirtual }
            if (virtualFolders.isNotEmpty()) {
                virtualFolders.forEach { folder ->
                    folder.virtualId?.let { vid -> deleteVirtualFolderRecursive(vid) }
                }
                saveCloudManifest()
            }
        }
    }

    private suspend fun deleteVirtualFolderRecursive(virtualId: String) {
        val children = driveDao.getItemsByVirtualParentSync(virtualId)
        
        // 1. Group actual files by chat and delete from Telegram
        val filesByChat = children.filter { !it.isFolder }.groupBy { it.parentChatId }
        filesByChat.forEach { (chatId, entities) ->
            val ids = entities.map { it.id }.toLongArray()
            telegramClient.send(TdApi.DeleteMessages(chatId, ids, true)) {}
        }
        
        // 2. Collect metadata messages to delete from Saved Messages
        val metadataIdsToDelete = mutableListOf<Long>()
        currentManifest.folderMetadataIds[virtualId]?.let { metadataIdsToDelete.add(it) }
        
        children.forEach { child ->
            if (!child.isFolder) {
                currentManifest.fileMetadataIds[child.id.toString()]?.let { metadataIdsToDelete.add(it) }
                child.splitGroupId?.let { gid ->
                    currentManifest.splitFileMasters[gid]?.metadataMessageId?.let { metadataIdsToDelete.add(it) }
                }
            }
        }
        
        if (metadataIdsToDelete.isNotEmpty() && savedMessagesChatId != 0L) {
            telegramClient.send(TdApi.DeleteMessages(savedMessagesChatId, metadataIdsToDelete.toLongArray(), true)) {}
        }

        // 3. Update manifest in-memory (atomically update local maps first)
        synchronized(currentManifest) {
            val updatedFolders = currentManifest.virtualFolders.toMutableMap()
            updatedFolders.remove(virtualId)

            val updatedMetaIds = currentManifest.folderMetadataIds.toMutableMap()
            updatedMetaIds.remove(virtualId)
            
            val updatedFileMetaIds = currentManifest.fileMetadataIds.toMutableMap()
            val updatedFileMappings = currentManifest.fileMappings.toMutableMap()
            val updatedSplitMasters = currentManifest.splitFileMasters.toMutableMap()
            val updatedTrashedFolders = currentManifest.trashedFolderIds.toMutableMap()
            val updatedTrashedFiles = currentManifest.trashedFileIds.toMutableMap()
            
            val updatedDeletedIds = currentManifest.deletedFolderIds.toMutableMap()
            updatedDeletedIds[virtualId] = System.currentTimeMillis() / 1000
            updatedTrashedFolders.remove(virtualId)

            children.forEach { child ->
                if (!child.isFolder) {
                    updatedFileMetaIds.remove(child.id.toString())
                    updatedFileMappings.remove(child.id.toString())
                    updatedTrashedFiles.remove("${child.parentChatId}_${child.id}")
                    updatedTrashedFiles.remove(child.id.toString())
                    child.splitGroupId?.let { updatedSplitMasters.remove(it) }
                }
            }

            currentManifest = currentManifest.copy(
                virtualFolders = updatedFolders,
                folderMetadataIds = updatedMetaIds,
                fileMetadataIds = updatedFileMetaIds,
                fileMappings = updatedFileMappings,
                splitFileMasters = updatedSplitMasters,
                deletedFolderIds = updatedDeletedIds,
                trashedFolderIds = updatedTrashedFolders,
                trashedFileIds = updatedTrashedFiles,
                updatedAt = System.currentTimeMillis() / 1000
            )
            saveLocalManifest()
        }

        // 4. Recurse into sub-folders
        children.filter { it.isFolder && it.isVirtual }.forEach { sf ->
            sf.virtualId?.let { deleteVirtualFolderRecursive(it) }
        }

        // 5. DB Cleanup
        val entity = driveDao.getVirtualFolderById(virtualId)
        if (entity != null) driveDao.deleteItemCompletely(entity.id, entity.parentChatId)
        children.forEach { child ->
            driveDao.deleteItemCompletely(child.id, child.parentChatId)
        }
    }

    fun downloadFolderContents(folder: DriveItem.Folder) {
        if (folder.isVirtual) {
            folder.virtualId?.let { downloadVirtualFolderRecursive(it) }
        } else {
            telegramClient.send(TdApi.GetChatHistory(folder.telegramChatId, 0, 0, 1000, false)) { result ->
                if (result is TdApi.Messages) {
                    result.messages.forEach { message ->
                        val fileId = when (val content = message.content) {
                            is TdApi.MessageDocument -> content.document.document.id
                            is TdApi.MessagePhoto -> content.photo.sizes.lastOrNull()?.photo?.id ?: 0
                            is TdApi.MessageVideo -> content.video.video.id
                            is TdApi.MessageAudio -> content.audio.audio.id
                            else -> 0
                        }
                        if (fileId != 0) telegramClient.send(TdApi.DownloadFile(fileId, 1, 0, 0, false))
                    }
                }
            }
        }
    }

    private fun downloadVirtualFolderRecursive(virtualId: String) {
        scope.launch {
            val children = driveDao.getItemsByVirtualParentSync(virtualId)
            children.forEach { child ->
                if (child.isFolder && child.isVirtual) {
                    child.virtualId?.let { downloadVirtualFolderRecursive(it) }
                } else if (!child.isFolder) {
                    // It's a file, initiate download
                    downloadFile(child.id, child.parentChatId, child.name)
                }
            }
        }
    }

    private suspend fun sendTdSuspend(function: TdApi.Function<*>): TdApi.Object? {
        return suspendCancellableCoroutine { cont ->
            telegramClient.send(function) { result ->
                if (cont.isActive) {
                    cont.resume(result)
                }
            }
        }
    }

    private fun buildTargetCaption(
        existingCaption: String,
        fileName: String,
        targetVirtualFolderId: String,
        isEncrypted: Boolean
    ): String {
        val splitMatch = Regex("\\[TD_SPLIT\\|.*?\\]").find(existingCaption)?.value
        val vfTag = if (targetVirtualFolderId != "0" && targetVirtualFolderId.isNotBlank()) "[VF:$targetVirtualFolderId]" else ""
        val encTag = if (isEncrypted) "[ENC]" else ""
        return if (splitMatch != null) {
            buildString {
                append(splitMatch)
                if (vfTag.isNotEmpty()) append(vfTag)
                if (encTag.isNotEmpty()) append(" ").append(encTag)
            }.trim()
        } else {
            buildString {
                append(fileName)
                if (vfTag.isNotEmpty()) append(" ").append(vfTag)
                if (encTag.isNotEmpty()) append(" ").append(encTag)
            }.trim()
        }
    }

    fun moveOrCopyItems(
        itemsToProcess: List<DriveItem>,
        destination: MoveCopyDestination,
        isCopy: Boolean,
        onComplete: (Int) -> Unit = {}
    ) {
        scope.launch {
            val destChatId = when {
                destination.isRoot -> savedMessagesChatId
                destination.isVirtual -> {
                    val resolved = resolveTargetChatForVirtualParent(destination.targetVirtualId)
                    if (resolved != 0L) resolved
                    else if (destination.targetChatId != 0L) destination.targetChatId
                    else savedMessagesChatId
                }
                else -> if (destination.targetChatId == 0L) savedMessagesChatId else destination.targetChatId
            }
            val destVirtualId = when {
                destination.isRoot -> "0"
                destination.isVirtual -> destination.targetVirtualId.ifBlank { "0" }
                else -> "0"
            }
            if (destChatId == 0L) {
                kotlinx.coroutines.withContext(Dispatchers.Main) { onComplete(0) }
                return@launch
            }

            val files = itemsToProcess.filterIsInstance<DriveItem.File>()
            var processedCount = 0

            for (file in files) {
                val fromChatId = if (file.parentChatId == 0L) savedMessagesChatId else file.parentChatId
                val fromVirtualId = file.virtualParentId ?: "0"

                if (!isCopy && fromChatId == destChatId && fromVirtualId == destVirtualId) {
                    continue
                }

                // Case 1: Move within the same chat (e.g., Saved Messages Root <-> Virtual Folder or Virtual Folder A <-> Virtual Folder B)
                if (!isCopy && fromChatId == destChatId) {
                    driveDao.updateVirtualParentInChat(file.id, fromChatId, destVirtualId)
                    saveLocalFileRename(file.id, fromChatId, file.name)
                    saveLocalFileVirtualParent(file.id, fromChatId, destVirtualId)

                    var oldMetaMsgIdToDelete: Long? = null
                    synchronized(currentManifest) {
                        val updatedMappings = currentManifest.fileMappings.toMutableMap()
                        updatedMappings[file.id.toString()] = destVirtualId
                        val updatedFileMetaIds = currentManifest.fileMetadataIds.toMutableMap()
                        oldMetaMsgIdToDelete = updatedFileMetaIds.remove(file.id.toString())
                        currentManifest = currentManifest.copy(
                            fileMappings = updatedMappings,
                            fileMetadataIds = updatedFileMetaIds,
                            updatedAt = System.currentTimeMillis() / 1000
                        )
                        saveLocalManifest()
                    }
                    if (oldMetaMsgIdToDelete != null && oldMetaMsgIdToDelete != 0L && savedMessagesChatId != 0L) {
                        telegramClient.send(TdApi.DeleteMessages(savedMessagesChatId, longArrayOf(oldMetaMsgIdToDelete!!), true)) {}
                    }
                    if (fromChatId == savedMessagesChatId && savedMessagesChatId != 0L && file.splitGroupId == null) {
                        sendFileMetadata(file.id, file.name, destVirtualId, file.isEncrypted)
                    }
                    saveCloudManifest()

                    // Update caption on Telegram server so [VF:...] tag is permanently updated
                    val msgObj = sendTdSuspend(TdApi.GetMessage(fromChatId, file.id))
                    if (msgObj is TdApi.Message) {
                        val existingCaption = when (val c = msgObj.content) {
                            is TdApi.MessageDocument -> c.caption.text
                            is TdApi.MessagePhoto -> c.caption.text
                            is TdApi.MessageVideo -> c.caption.text
                            is TdApi.MessageAudio -> c.caption.text
                            is TdApi.MessageAnimation -> c.caption.text
                            else -> ""
                        }
                        val updatedCaption = buildTargetCaption(existingCaption, file.name, destVirtualId, file.isEncrypted)
                        sendTdSuspend(
                            TdApi.EditMessageCaption(
                                fromChatId,
                                file.id,
                                null,
                                TdApi.FormattedText(updatedCaption, emptyArray()),
                                false
                            )
                        )
                    }
                    processedCount++
                    continue
                }

                // Case 2: Copy (same chat or cross-chat) OR Move across different chats (Saved Messages <-> Channel Folder or Channel A <-> Channel B)
                val effectiveDestVfId = destVirtualId
                val srcMsgObj = sendTdSuspend(TdApi.GetMessage(fromChatId, file.id)) as? TdApi.Message
                val existingCaption = when (val c = srcMsgObj?.content) {
                    is TdApi.MessageDocument -> c.caption.text
                    is TdApi.MessagePhoto -> c.caption.text
                    is TdApi.MessageVideo -> c.caption.text
                    is TdApi.MessageAudio -> c.caption.text
                    is TdApi.MessageAnimation -> c.caption.text
                    else -> ""
                }
                val targetCaption = buildTargetCaption(existingCaption, file.name, effectiveDestVfId, file.isEncrypted)
                val formattedCaption = TdApi.FormattedText(targetCaption, emptyArray())

                // Note: TDLib strictly forbids InputFileRemote/InputFileId inside InputThumbnail and forbids
                // disableContentTypeDetection=true for non-local files. Using null thumbnail and false for remote files
                // allows TdApi.SendMessage to succeed directly with formattedCaption (containing [VF:effectiveDestVfId])
                // while preserving 100% original server file and thumbnail.
                val inputContent: TdApi.InputMessageContent? = when (val c = srcMsgObj?.content) {
                    is TdApi.MessageDocument -> {
                        val docFile = c.document.document
                        val inputFile = if (docFile.remote.id.isNotEmpty()) TdApi.InputFileRemote(docFile.remote.id) else TdApi.InputFileId(docFile.id)
                        TdApi.InputMessageDocument(inputFile, null, false, formattedCaption)
                    }
                    is TdApi.MessageVideo -> {
                        val v = c.video
                        val inputFile = if (v.video.remote.id.isNotEmpty()) TdApi.InputFileRemote(v.video.remote.id) else TdApi.InputFileId(v.video.id)
                        TdApi.InputMessageVideo().apply {
                            video = inputFile
                            thumbnail = null
                            duration = v.duration
                            width = v.width
                            height = v.height
                            supportsStreaming = v.supportsStreaming
                            caption = formattedCaption
                            addedStickerFileIds = intArrayOf()
                        }
                    }
                    is TdApi.MessagePhoto -> {
                        val p = c.photo.sizes.lastOrNull()
                        if (p != null) {
                            val inputFile = if (p.photo.remote.id.isNotEmpty()) TdApi.InputFileRemote(p.photo.remote.id) else TdApi.InputFileId(p.photo.id)
                            TdApi.InputMessagePhoto().apply {
                                photo = inputFile
                                thumbnail = null
                                width = p.width
                                height = p.height
                                caption = formattedCaption
                                addedStickerFileIds = intArrayOf()
                            }
                        } else null
                    }
                    is TdApi.MessageAudio -> {
                        val a = c.audio
                        val inputFile = if (a.audio.remote.id.isNotEmpty()) TdApi.InputFileRemote(a.audio.remote.id) else TdApi.InputFileId(a.audio.id)
                        TdApi.InputMessageAudio(inputFile, null, a.duration, a.title, a.performer, formattedCaption)
                    }
                    else -> null
                }

                var usedForwardFallback = false
                var initialSentMsg: TdApi.Message? = null

                if (inputContent != null) {
                    val sendRes = sendTdSuspend(TdApi.SendMessage(destChatId, null, null, null, null, inputContent))
                    if (sendRes is TdApi.Message) {
                        initialSentMsg = sendRes
                    }
                }

                if (initialSentMsg == null) {
                    usedForwardFallback = true
                    val options = TdApi.MessageSendOptions(null, false, false, false, false, 0L, false, null, 0L, 0, false)
                    // Pass removeCaption = true so the old source folder's [VF:...] tag is stripped immediately
                    val fwdRes = sendTdSuspend(TdApi.ForwardMessages(destChatId, null, fromChatId, longArrayOf(file.id), options, true, true))
                    if (fwdRes is TdApi.Messages && fwdRes.messages.isNotEmpty()) {
                        initialSentMsg = fwdRes.messages.firstOrNull()
                    }
                }

                if (initialSentMsg != null) {
                    val tempMsg = initialSentMsg
                    val sendMeta = PendingSendMeta(
                        fileName = file.name,
                        virtualFolderId = effectiveDestVfId,
                        isEncrypted = file.isEncrypted,
                        thumbnailPath = file.thumbnailPath,
                        localPath = file.localPath,
                        handledByCaller = true
                    )
                    pendingSendMetaMap[tempMsg.id] = sendMeta
                    recentlyCreatedMessageIds[tempMsg.id] = System.currentTimeMillis()
                    saveLocalFileRename(tempMsg.id, destChatId, file.name)
                    saveLocalFileVirtualParent(tempMsg.id, destChatId, effectiveDestVfId)

                    val sendDeferred = if (tempMsg.sendingState != null) {
                        pendingMessageSends.getOrPut(tempMsg.id) { kotlinx.coroutines.CompletableDeferred() }
                    } else null

                    synchronized(currentManifest) {
                        val updatedMappings = currentManifest.fileMappings.toMutableMap()
                        updatedMappings[tempMsg.id.toString()] = effectiveDestVfId
                        currentManifest = currentManifest.copy(
                            fileMappings = updatedMappings,
                            updatedAt = System.currentTimeMillis() / 1000
                        )
                        saveLocalManifest()
                    }

                    // Insert provisional entity immediately so destination folder shows the file right away
                    val provisional = mapMessageToEntity(destChatId, tempMsg)?.copy(
                        name = file.name,
                        virtualParentId = effectiveDestVfId,
                        isEncrypted = file.isEncrypted,
                        thumbnailPath = file.thumbnailPath,
                        localPath = file.localPath
                    )
                    if (provisional != null) {
                        driveDao.insertItemsPreservingStarred(listOf(provisional))
                    }

                    val confirmedMsg: TdApi.Message = if (sendDeferred != null) {
                        val alreadyDone = completedMessageSends.remove(tempMsg.id)
                        if (alreadyDone != null) {
                            alreadyDone
                        } else {
                            withTimeoutOrNull(20000L) { sendDeferred.await() }
                                ?: completedMessageSends.remove(tempMsg.id)
                                ?: tempMsg
                        }
                    } else {
                        tempMsg
                    }

                    pendingSendMetaMap[confirmedMsg.id] = sendMeta
                    recentlyCreatedMessageIds[confirmedMsg.id] = System.currentTimeMillis()
                    saveLocalFileRename(confirmedMsg.id, destChatId, file.name)
                    saveLocalFileVirtualParent(confirmedMsg.id, destChatId, effectiveDestVfId)

                    synchronized(currentManifest) {
                        val updatedMappings = currentManifest.fileMappings.toMutableMap()
                        if (tempMsg.id != confirmedMsg.id) {
                            updatedMappings.remove(tempMsg.id.toString())
                        }
                        updatedMappings[confirmedMsg.id.toString()] = effectiveDestVfId
                        currentManifest = currentManifest.copy(
                            fileMappings = updatedMappings,
                            updatedAt = System.currentTimeMillis() / 1000
                        )
                        saveLocalManifest()
                    }

                    if (usedForwardFallback && confirmedMsg.sendingState == null) {
                        sendTdSuspend(
                            TdApi.EditMessageCaption(
                                destChatId,
                                confirmedMsg.id,
                                null,
                                formattedCaption,
                                false
                            )
                        )
                    }

                    if (tempMsg.id != confirmedMsg.id) {
                        driveDao.deleteItemCompletely(tempMsg.id, destChatId)
                    }

                    val finalEntity = mapMessageToEntity(destChatId, confirmedMsg)?.copy(
                        name = file.name,
                        virtualParentId = effectiveDestVfId,
                        isEncrypted = file.isEncrypted,
                        thumbnailPath = file.thumbnailPath,
                        localPath = file.localPath
                    )
                    if (finalEntity != null) {
                        driveDao.insertItemsPreservingStarred(listOf(finalEntity))
                    }

                    if ((destChatId == savedMessagesChatId || effectiveDestVfId != "0") && savedMessagesChatId != 0L) {
                        if (destChatId == savedMessagesChatId && confirmedMsg.sendingState == null) {
                            sendFileMetadata(confirmedMsg.id, file.name, effectiveDestVfId, file.isEncrypted)
                        }
                        saveCloudManifest()
                    }

                    // If this was a MOVE across chats, delete the original message from source chat & clean up
                    if (!isCopy) {
                        sendTdSuspend(TdApi.DeleteMessages(fromChatId, longArrayOf(file.id), true))
                        driveDao.deleteItemCompletely(file.id, fromChatId)
                        if (fromChatId == savedMessagesChatId && savedMessagesChatId != 0L) {
                            var oldMetaId: Long? = null
                            synchronized(currentManifest) {
                                val updatedMappings = currentManifest.fileMappings.toMutableMap()
                                updatedMappings.remove(file.id.toString())
                                val updatedMetaIds = currentManifest.fileMetadataIds.toMutableMap()
                                oldMetaId = updatedMetaIds.remove(file.id.toString())
                                currentManifest = currentManifest.copy(
                                    fileMappings = updatedMappings,
                                    fileMetadataIds = updatedMetaIds,
                                    updatedAt = System.currentTimeMillis() / 1000
                                )
                                saveLocalManifest()
                            }
                            if (oldMetaId != null && oldMetaId != 0L) {
                                telegramClient.send(TdApi.DeleteMessages(savedMessagesChatId, longArrayOf(oldMetaId!!), true)) {}
                            }
                            saveCloudManifest()
                        }
                    }

                    processedCount++
                }
            }

            kotlinx.coroutines.withContext(Dispatchers.Main) {
                onComplete(processedCount)
            }
        }
    }

    fun moveItems(fromChatId: Long, toChatId: Long, messageIds: List<Long>) {
        scope.launch {
            val entities = messageIds.mapNotNull { id -> driveDao.getItemById(id, fromChatId) }
            val files = entities.map { entity ->
                DriveItem.File(
                    id = entity.id,
                    parentChatId = entity.parentChatId,
                    name = entity.name,
                    size = entity.size,
                    mimeType = entity.mimeType,
                    telegramFileId = entity.telegramFileId,
                    thumbnailPath = entity.thumbnailPath,
                    localPath = entity.localPath,
                    isStarred = entity.isStarred,
                    remoteUniqueId = entity.remoteUniqueId ?: "",
                    splitGroupId = entity.splitGroupId,
                    totalParts = entity.totalParts,
                    virtualParentId = entity.virtualParentId,
                    isEncrypted = entity.isEncrypted,
                    createdAt = entity.createdAt
                )
            }
            val dest = MoveCopyDestination(
                label = "",
                isRoot = (toChatId == 0L || toChatId == savedMessagesChatId),
                isVirtual = false,
                targetChatId = if (toChatId == 0L) savedMessagesChatId else toChatId,
                targetVirtualId = "0"
            )
            moveOrCopyItems(files, dest, isCopy = false)
        }
    }

    fun moveFolderContentsAndDelete(fromFolderChatId: Long, toChatId: Long) {
        val options = TdApi.MessageSendOptions(null, false, false, false, false, 0L, false, null, 0L, 0, false)
        telegramClient.send(TdApi.GetChatHistory(fromFolderChatId, 0, 0, 100, false)) { result ->
            if (result is TdApi.Messages) {
                val messageIds = result.messages.map { it.id }.toLongArray()
                if (messageIds.isNotEmpty()) {
                    telegramClient.send(TdApi.ForwardMessages(toChatId, null, fromFolderChatId, messageIds, options, true, false)) { forwardResult ->
                        if (forwardResult is TdApi.Messages) {
                            telegramClient.send(TdApi.DeleteChat(fromFolderChatId)) {
                                scope.launch {
                                    driveDao.deleteItemsByChat(fromFolderChatId)
                                    driveDao.deleteItemCompletely(fromFolderChatId, savedMessagesChatId)
                                    fetchFiles(toChatId)
                                }
                            }
                        }
                    }
                } else {
                    telegramClient.send(TdApi.DeleteChat(fromFolderChatId)) {
                        scope.launch {
                            driveDao.deleteItemCompletely(fromFolderChatId, savedMessagesChatId)
                            fetchFiles(toChatId)
                        }
                    }
                }
            }
        }
    }

    fun getInternalCacheSize(): Flow<Long> = kotlinx.coroutines.flow.flow {
        // Initial emit
        emit(calculateTotalStorage())
        while (true) {
            kotlinx.coroutines.delay(10000)
            emit(calculateTotalStorage())
        }
    }

    private fun calculateTotalStorage(): Long {
        return calculateDirectorySize(context.cacheDir) + 
               calculateDirectorySize(context.filesDir) +
               (context.externalCacheDir?.let { calculateDirectorySize(it) } ?: 0L) +
               (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                   calculateDirectorySize(context.noBackupFilesDir) + calculateDirectorySize(context.codeCacheDir)
               } else 0L)
    }

    private fun calculateDirectorySize(directory: File?): Long {
        if (directory == null || !directory.exists()) return 0L
        var size: Long = 0
        try {
            val files = directory.listFiles() ?: return 0L
            for (file in files) {
                size += if (file.isDirectory) calculateDirectorySize(file) else file.length()
            }
        } catch (e: Exception) { }
        return size
    }

    fun clearDatabaseLocalPaths(onlyThumbnails: Boolean = false, onlyFiles: Boolean = false) {
        completedFilePathCache.clear()
        scope.launch {
            if (onlyThumbnails) driveDao.clearAllThumbnailPaths()
            else if (onlyFiles) driveDao.clearAllLocalPaths()
            else {
                driveDao.clearAllLocalPaths()
                driveDao.clearAllThumbnailPaths()
            }
        }
    }

    private fun classifyCloudFileCategory(mimeType: String, fileName: String): String {
        val mime = mimeType.lowercase()
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when {
            mime.startsWith("image/") || ext in setOf(
                "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "svg", "dng", "raw", "ico", "tiff", "tif"
            ) -> "Images"

            mime.startsWith("video/") || ext in setOf(
                "mp4", "mkv", "avi", "mov", "webm", "flv", "3gp", "m4v", "ts", "mpeg", "mpg", "wmv"
            ) -> "Videos"

            mime.startsWith("audio/") || ext in setOf(
                "mp3", "wav", "flac", "aac", "ogg", "m4a", "opus", "wma", "amr", "alac", "aiff"
            ) -> "Audio"

            mime.contains("android.package-archive") || ext in setOf(
                "apk", "xapk", "apks", "apkm"
            ) -> "APKs"

            mime.startsWith("application/pdf") ||
                mime.startsWith("text/") ||
                mime.contains("document") ||
                mime.contains("sheet") ||
                mime.contains("presentation") ||
                mime.contains("msword") ||
                mime.contains("excel") ||
                mime.contains("powerpoint") ||
                ext in setOf(
                    "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
                    "txt", "csv", "rtf", "epub", "odt", "ods", "odp", "md", "json", "xml"
                ) -> "Documents"

            else -> "Others"
        }
    }

    fun getCloudFileTypeStats(): Flow<List<DriveDao.FileTypeStat>> = getAllFiles().map { files ->
        val sizeByCategory = mutableMapOf<String, Long>()
        val countByCategory = mutableMapOf<String, Int>()
        val processedGroups = mutableSetOf<String>()

        files.forEach { file ->
            val fileLogicalSize = if (file.splitGroupId == null) {
                file.size
            } else if (processedGroups.add(file.splitGroupId)) {
                val master = currentManifest.splitFileMasters[file.splitGroupId]
                master?.totalSize ?: (file.size * file.totalParts.coerceAtLeast(1))
            } else {
                null
            }

            if (fileLogicalSize != null) {
                val category = classifyCloudFileCategory(file.mimeType, file.name)
                sizeByCategory[category] = (sizeByCategory[category] ?: 0L) + fileLogicalSize.coerceAtLeast(0L)
                countByCategory[category] = (countByCategory[category] ?: 0) + 1
            }
        }

        val orderedCategories = listOf("Images", "Videos", "Audio", "Documents", "APKs", "Others")
        orderedCategories.map { category ->
            DriveDao.FileTypeStat(
                category = category,
                totalSize = sizeByCategory[category] ?: 0L,
                count = countByCategory[category] ?: 0
            )
        }
    }

    fun getTotalStorageUsed(): Flow<Long> = getCloudFileTypeStats().map { stats ->
        stats.sumOf { it.totalSize }
    }

    fun isChatSecure(chatId: Long): Boolean = currentManifest.secureFolderChatIds.contains(chatId)
    fun isVirtualFolderSecure(virtualId: String): Boolean = currentManifest.virtualFolders[virtualId]?.isSecure == true
    fun getVirtualFolderName(virtualId: String): String? = currentManifest.virtualFolders[virtualId]?.name
    fun getParentVirtualId(virtualId: String): String {
        val pid = currentManifest.virtualFolders[virtualId]?.parentId ?: "0"
        return if (pid.startsWith("vf_") && !currentManifest.deletedFolderIds.containsKey(pid)) pid else "0"
    }
    fun getSavedMessagesChatId(): Long = savedMessagesChatId

    private fun startTransferService() {
        val intent = Intent(context, com.awd.teledrive.data.service.TransferService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun parseSplitMetadata(caption: String): SplitMetadata? {
        if (!caption.contains("[TD_SPLIT|")) return null
        val id = caption.substringAfter("ID:").substringBefore("|")
        val partInfo = caption.substringAfter("PART:").substringBefore("|")
        val partIndex = partInfo.substringBefore("/").toIntOrNull() ?: 0
        val totalParts = partInfo.substringAfter("/").toIntOrNull() ?: 1
        val originalName = caption.substringAfter("NAME:").substringBefore("]")
        return SplitMetadata(id, partIndex, totalParts, originalName)
    }

    private fun parseOriginalTimestampTag(caption: String): Long? {
        val startTag = "[OTIME:"
        val startIndex = caption.indexOf(startTag)
        if (startIndex == -1) return null
        val endIndex = caption.indexOf("]", startIndex)
        if (endIndex == -1) return null
        return caption.substring(startIndex + startTag.length, endIndex).toLongOrNull()
    }

    private fun parseVirtualFolderTag(caption: String): String? {
        val startTag = "[VF:"
        val endTag = "]"
        val startIndex = caption.indexOf(startTag)
        if (startIndex == -1) return null
        val endIndex = caption.indexOf(endTag, startIndex)
        if (endIndex == -1) return null
        return caption.substring(startIndex + startTag.length, endIndex)
    }

    private fun cleanEncryptedFileName(fileName: String): String {
        return fileName.replace(Regex("^enc_\\d+_"), "").replace(Regex("^enc_"), "")
    }

    private data class SplitMetadata(val groupId: String, val partIndex: Int, val totalParts: Int, val originalName: String)

    // =========================================================================
    // AUTOSYNC ENGINE HELPERS (Synchronous / Coroutine-based)
    // =========================================================================

    data class RemoteFolderOption(
        val displayName: String,
        val fullPath: String,
        val chatId: Long,
        val virtualFolderId: String,
        val parentVirtualId: String,
        val folderKey: String = if (fullPath == "/") "ROOT" else if (virtualFolderId != "0" && virtualFolderId.isNotBlank()) virtualFolderId else "ch_$chatId",
        val parentFolderKey: String = if (fullPath == "/") "" else if (parentVirtualId.isNotBlank() && parentVirtualId != "0") parentVirtualId else "ROOT"
    )

    suspend fun ensureReadyChatIdSuspend(): Long {
        if (savedMessagesChatId != 0L) return savedMessagesChatId
        val meObj = sendTdSuspend(TdApi.GetMe())
        if (meObj is TdApi.User) {
            val chatObj = sendTdSuspend(TdApi.CreatePrivateChat(meObj.id, false))
            if (chatObj is TdApi.Chat) {
                savedMessagesChatId = chatObj.id
                _savedMessagesChatIdFlow.value = chatObj.id
                saveLocalManifest()
                return chatObj.id
            }
        }
        return savedMessagesChatId
    }

    suspend fun getAllSelectableRemoteFoldersSuspend(): List<RemoteFolderOption> {
        val rootChat = ensureReadyChatIdSuspend()
        val result = mutableListOf<RemoteFolderOption>()
        result.add(
            RemoteFolderOption(
                displayName = "/ (Root - My Drive)",
                fullPath = "/",
                chatId = rootChat,
                virtualFolderId = "0",
                parentVirtualId = "",
                folderKey = "ROOT",
                parentFolderKey = ""
            )
        )

        val allItems = driveDao.getAllItemsSync().filter { !it.isTrashed }
        val channelFolders = allItems.filter {
            it.isFolder && !it.isVirtual && it.id != rootChat && it.id != 0L &&
                !currentManifest.trashedFolderIds.containsKey(it.id.toString())
        }
        val virtualFoldersMap = synchronized(currentManifest) {
            currentManifest.virtualFolders.filterKeys { vid ->
                !currentManifest.deletedFolderIds.containsKey(vid) &&
                    !currentManifest.trashedFolderIds.containsKey(vid)
            }
        }

        // Track canonical folder key per (parentFolderKey, normalizedName) so duplicate folders are merged cleanly
        val canonicalKeyByParentAndName = LinkedHashMap<Pair<String, String>, RemoteFolderOption>()
        val duplicateKeyRemap = HashMap<String, String>()

        channelFolders
            .sortedBy { it.name.trim().lowercase() }
            .forEach { ch ->
                val cleanName = ch.name.trim().trim('/').ifBlank { "Folder_${ch.id}" }
                val rawKey = "ch_${ch.id}"
                val dedupKey = "ROOT" to cleanName.lowercase()
                val existing = canonicalKeyByParentAndName[dedupKey]
                if (existing == null) {
                    val opt = RemoteFolderOption(
                        displayName = cleanName,
                        fullPath = "/$cleanName",
                        chatId = ch.id,
                        virtualFolderId = "0",
                        parentVirtualId = "0",
                        folderKey = rawKey,
                        parentFolderKey = "ROOT"
                    )
                    canonicalKeyByParentAndName[dedupKey] = opt
                    result.add(opt)
                } else {
                    duplicateKeyRemap[rawKey] = existing.folderKey
                    duplicateKeyRemap[ch.id.toString()] = existing.folderKey
                }
            }

        fun resolveParentKey(rawPid: String): String {
            val pid = rawPid.trim()
            val mappedPid = duplicateKeyRemap[pid] ?: pid
            return when {
                mappedPid.isBlank() || mappedPid == "0" || mappedPid == rootChat.toString() -> "ROOT"
                mappedPid.startsWith("ch_") -> mappedPid
                mappedPid.startsWith("vf_") -> {
                    if (virtualFoldersMap.containsKey(mappedPid) || duplicateKeyRemap.containsKey(pid)) {
                        duplicateKeyRemap[pid] ?: mappedPid
                    } else {
                        "ROOT"
                    }
                }
                else -> {
                    val chId = mappedPid.toLongOrNull()
                    if (chId != null && channelFolders.any { it.id == chId }) {
                        duplicateKeyRemap["ch_$chId"] ?: "ch_$chId"
                    } else {
                        "ROOT"
                    }
                }
            }
        }

        // Process virtual folders in topological order (parents before children)
        val validVfs = virtualFoldersMap.values
            .filter { !it.name.startsWith("Recovered_") && isVirtualFolderOrAncestorTrashed(it.id) == null }

        fun getDepth(vf: VirtualFolder, visited: Set<String> = emptySet()): Int {
            if (vf.id in visited) return 0
            val pid = vf.parentId.trim()
            val parentVf = if (pid.startsWith("vf_")) virtualFoldersMap[pid] else null
            return if (parentVf != null) 1 + getDepth(parentVf, visited + vf.id) else 0
        }

        val sortedVfs = validVfs.sortedWith(
            compareBy<VirtualFolder> { getDepth(it) }
                .thenBy { it.name.trim().lowercase() }
                .thenBy { it.createdAt }
        )

        val optionByFolderKey = HashMap<String, RemoteFolderOption>()
        result.forEach { optionByFolderKey[it.folderKey] = it }

        for (vf in sortedVfs) {
            val cleanName = vf.name.trim().trim('/').ifBlank { continue }
            val parentKey = resolveParentKey(vf.parentId)
            val parentOpt = optionByFolderKey[parentKey] ?: result.first()
            val effectiveParentKey = parentOpt.folderKey
            val resolvedChatId = if (parentOpt.folderKey == "ROOT") rootChat else parentOpt.chatId

            val dedupKey = effectiveParentKey to cleanName.lowercase()
            val existing = canonicalKeyByParentAndName[dedupKey]
            if (existing != null) {
                // Prefer virtualFolderId if existing was a channel or older duplicate, and remap children to existing
                duplicateKeyRemap[vf.id] = existing.folderKey
            } else {
                val fullPath = if (parentOpt.fullPath == "/") {
                    "/$cleanName"
                } else {
                    "${parentOpt.fullPath.trimEnd('/')}/$cleanName"
                }
                val opt = RemoteFolderOption(
                    displayName = cleanName,
                    fullPath = fullPath,
                    chatId = resolvedChatId,
                    virtualFolderId = vf.id,
                    parentVirtualId = if (effectiveParentKey == "ROOT") "0" else effectiveParentKey,
                    folderKey = vf.id,
                    parentFolderKey = effectiveParentKey
                )
                canonicalKeyByParentAndName[dedupKey] = opt
                optionByFolderKey[opt.folderKey] = opt
                result.add(opt)
            }
        }

        return result
    }

    suspend fun resolveSyncTargetFolderSuspend(
        remotePathDisplay: String,
        preferredChatId: Long,
        preferredVfId: String
    ): Pair<Long, String> {
        val rootChat = ensureReadyChatIdSuspend()
        if (rootChat == 0L) return 0L to "0"

        val cleanPath = remotePathDisplay
            .trim()
            .replace('\\', '/')
            .split('/')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("/")

        if (cleanPath.isEmpty() ||
            cleanPath.equals("(Root - My Drive)", ignoreCase = true) ||
            cleanPath.equals("My Drive", ignoreCase = true)
        ) {
            return rootChat to "0"
        }

        // Check if existing selectable folders already have an exact path match
        val allSelectable = getAllSelectableRemoteFoldersSuspend()
        val normalizedTargetPath = "/$cleanPath"
        val exactMatch = allSelectable.firstOrNull {
            it.fullPath.equals(normalizedTargetPath, ignoreCase = true)
        }
        if (exactMatch != null) {
            val chat = if (exactMatch.chatId == 0L) rootChat else exactMatch.chatId
            return chat to exactMatch.virtualFolderId.ifBlank { "0" }
        }

        // Check if first segment is a physical channel folder
        val segments = cleanPath.split('/').map { it.trim() }.filter { it.isNotEmpty() }
        val firstSegment = segments.first()
        val topChannel = allSelectable.firstOrNull {
            it.parentFolderKey == "ROOT" &&
                it.virtualFolderId == "0" &&
                it.chatId != rootChat &&
                it.displayName.equals(firstSegment, ignoreCase = true)
        }

        return if (topChannel != null) {
            if (segments.size == 1) {
                topChannel.chatId to "0"
            } else {
                val subRelPath = segments.drop(1).joinToString("/")
                val vfId = ensureVirtualFolderPathSuspend(topChannel.chatId, "0", subRelPath)
                topChannel.chatId to vfId
            }
        } else {
            val vfId = ensureVirtualFolderPathSuspend(rootChat, "0", cleanPath)
            rootChat to vfId
        }
    }

    suspend fun ensureVirtualFolderPathSuspend(
        baseChatId: Long,
        baseVirtualFolderId: String,
        relativeFolderPath: String
    ): String {
        val cleanRel = relativeFolderPath.trim().trim('/').replace('\\', '/')
        val initialParent = when {
            baseVirtualFolderId.isNotBlank() && baseVirtualFolderId != "0" -> baseVirtualFolderId
            baseChatId != 0L && baseChatId != savedMessagesChatId -> baseChatId.toString()
            else -> "0"
        }
        if (cleanRel.isEmpty()) return if (initialParent.startsWith("vf_")) initialParent else "0"

        val segments = cleanRel.split('/').map { it.trim() }.filter { it.isNotEmpty() }
        var currentParentId = initialParent

        for (segment in segments) {
            val existingVf = synchronized(currentManifest) {
                currentManifest.virtualFolders.values.firstOrNull { vf ->
                    !vf.name.startsWith("Recovered_") &&
                        !currentManifest.deletedFolderIds.containsKey(vf.id) &&
                        !currentManifest.trashedFolderIds.containsKey(vf.id) &&
                        vf.parentId.trim() == currentParentId &&
                        vf.name.trim().equals(segment, ignoreCase = true)
                }
            }
            if (existingVf != null) {
                currentParentId = existingVf.id
            } else {
                val newId = "vf_${System.nanoTime()}"
                val nowSec = System.currentTimeMillis() / 1000
                val newFolder = VirtualFolder(
                    id = newId,
                    name = segment,
                    parentId = currentParentId,
                    createdAt = nowSec,
                    type = "virtual_folder",
                    isSecure = false,
                    updatedAt = nowSec
                )
                synchronized(currentManifest) {
                    val updated = currentManifest.virtualFolders.toMutableMap()
                    updated[newId] = newFolder
                    currentManifest = currentManifest.copy(virtualFolders = updated, updatedAt = nowSec)
                    saveLocalManifest()
                }
                if (savedMessagesChatId != 0L) {
                    val metaJson = json.encodeToString(newFolder)
                    val text = "$VFOLDER_META_PREFIX\n\n$metaJson"
                    val msgObj = sendTdSuspend(
                        TdApi.SendMessage(
                            savedMessagesChatId,
                            null,
                            null,
                            null,
                            null,
                            TdApi.InputMessageText(TdApi.FormattedText(text, emptyArray()), null, true)
                        )
                    )
                    if (msgObj is TdApi.Message) {
                        synchronized(currentManifest) {
                            val updatedMeta = currentManifest.folderMetadataIds.toMutableMap()
                            updatedMeta[newId] = msgObj.id
                            currentManifest = currentManifest.copy(folderMetadataIds = updatedMeta)
                            saveLocalManifest()
                        }
                    }
                }
                currentParentId = newId
            }
        }
        val entities = buildVirtualFolderEntities()
        driveDao.syncVirtualFolders(entities)
        saveCloudManifest()
        return if (currentParentId.startsWith("vf_")) currentParentId else "0"
    }

    suspend fun getRemoteTreeFilesForSyncSuspend(
        baseChatId: Long,
        baseVirtualFolderId: String,
        includeSubfolders: Boolean
    ): List<Pair<String, DriveItemEntity>> {
        val rootChat = ensureReadyChatIdSuspend()
        val effectiveChatId = if (baseChatId == 0L) rootChat else baseChatId
        val trashedFileKeys = synchronized(currentManifest) {
            currentManifest.trashedFileIds.keys.toSet()
        }
        val allItems = driveDao.getAllItemsSync().filter { item ->
            !item.isTrashed &&
                !trashedFileKeys.contains("${item.parentChatId}_${item.id}") &&
                !trashedFileKeys.contains(item.id.toString())
        }
        val activeVfMap = synchronized(currentManifest) {
            currentManifest.virtualFolders.filterKeys { vid ->
                !currentManifest.deletedFolderIds.containsKey(vid) &&
                    !currentManifest.trashedFolderIds.containsKey(vid)
            }
        }

        val result = mutableListOf<Pair<String, DriveItemEntity>>()

        // Also include equivalent duplicate virtual folders sharing the same parent and trimmed name
        fun findEquivalentVfIds(vfId: String): Set<String> {
            if (vfId == "0" || !vfId.startsWith("vf_")) return setOf(vfId)
            val targetVf = activeVfMap[vfId] ?: return setOf(vfId)
            val targetName = targetVf.name.trim()
            val targetParent = targetVf.parentId.trim()
            return activeVfMap.values
                .filter {
                    it.parentId.trim() == targetParent &&
                        it.name.trim().equals(targetName, ignoreCase = true)
                }
                .map { it.id }
                .toSet() + vfId
        }

        fun collectAt(chatId: Long, vfId: String, relPrefix: String, visited: Set<String>) {
            val equivalentVfIds = findEquivalentVfIds(vfId)
            val filesHere = allItems.filter { item ->
                !item.isFolder && (
                    if (vfId != "0" && vfId.startsWith("vf_")) {
                        item.virtualParentId in equivalentVfIds
                    } else {
                        item.parentChatId == chatId &&
                            (item.virtualParentId.isNullOrEmpty() || item.virtualParentId == "0" || item.virtualParentId == chatId.toString())
                    }
                )
            }
            filesHere.forEach { fileEntity ->
                val cleanFileName = fileEntity.name.trim()
                if (cleanFileName.isNotEmpty()) {
                    val relPath = if (relPrefix.isEmpty()) cleanFileName else "$relPrefix/$cleanFileName"
                    result.add(relPath to fileEntity)
                }
            }

            if (includeSubfolders) {
                val expectedParentKeys = when {
                    vfId != "0" && vfId.startsWith("vf_") -> equivalentVfIds
                    chatId != rootChat && chatId != 0L -> setOf(chatId.toString())
                    else -> setOf("0", "")
                }
                val childVfs = activeVfMap.values.filter { vf ->
                    vf.parentId.trim() in expectedParentKeys &&
                        vf.id !in visited &&
                        !vf.name.startsWith("Recovered_")
                }
                // Group child VFs by trimmed lowercase name so duplicate subfolders are traversed cleanly together
                childVfs.groupBy { it.name.trim().lowercase() }.values.forEach { sameNameGroup ->
                    val primaryChild = sameNameGroup.first()
                    val cleanSubName = primaryChild.name.trim().trim('/')
                    if (cleanSubName.isNotEmpty()) {
                        val nextPrefix = if (relPrefix.isEmpty()) cleanSubName else "$relPrefix/$cleanSubName"
                        val groupIds = sameNameGroup.map { it.id }.toSet()
                        collectAt(chatId, primaryChild.id, nextPrefix, visited + groupIds)
                    }
                }
            }
        }

        collectAt(effectiveChatId, baseVirtualFolderId.ifBlank { "0" }, "", emptySet())
        return result
    }

    fun cancelAllSyncTransfers() {
        // 1. Immediately abort all active TDLib sync uploads & delete unsent temporary messages
        val uploadsSnapshot = activeSyncUploads.entries.toList()
        activeSyncUploads.clear()
        uploadsSnapshot.forEach { (syncKey, info) ->
            val (chatId, tempMsgId, tdFileId) = info
            if (tempMsgId != 0L && chatId != 0L) {
                pendingSendMetaMap.remove(tempMsgId)
                pendingMessageSends.remove(tempMsgId)?.complete(null)
                completedMessageSends.remove(tempMsgId)
                telegramClient.send(TdApi.DeleteMessages(chatId, longArrayOf(tempMsgId), true)) {}
                scope.launch {
                    try {
                        driveDao.deleteItemCompletely(tempMsgId, chatId)
                    } catch (_: Exception) {}
                }
            }
            if (tdFileId != 0) {
                telegramClient.send(TdApi.CancelPreliminaryUploadFile(tdFileId)) {}
            }
            transferRepository.removeTransfer(syncKey)
        }

        // 2. Immediately abort all active TDLib sync downloads
        val downloadsSnapshot = activeSyncDownloadFileIds.toList()
        activeSyncDownloadFileIds.clear()
        downloadsSnapshot.forEach { fileId ->
            if (fileId != 0) {
                telegramClient.send(TdApi.CancelDownloadFile(fileId, false)) {}
            }
        }
    }

    fun flushSyncManifest() {
        saveLocalManifest()
        saveCloudManifest()
    }

    suspend fun uploadFileForSyncSuspend(
        localFile: File,
        targetChatId: Long,
        targetVirtualFolderId: String,
        originalTimestampMillis: Long,
        onProgress: ((Float) -> Unit)? = null
    ): Long? {
        if (!localFile.exists() || !localFile.isFile) return null
        val rootChat = ensureReadyChatIdSuspend()
        val resolvedChatId = if (targetChatId == 0L) rootChat else targetChatId
        if (resolvedChatId == 0L) return null

        val resolvedVfId = targetVirtualFolderId.ifBlank { "0" }
        val origTs = if (originalTimestampMillis > 0L) originalTimestampMillis else FileUtils.extractOriginalTimestampMillis(localFile)
        val fileLength = localFile.length()

        // For files > 1.95 GB, delegate to standard split upload
        if (fileLength > FileUtils.MAX_FILE_SIZE) {
            uploadFile(
                filePath = localFile.absolutePath,
                originalFileName = localFile.name,
                chatId = resolvedChatId,
                virtualFolderId = resolvedVfId
            )
            return -1L
        }

        val vfTag = if (resolvedVfId != "0") "[VF:$resolvedVfId]" else ""
        val otimeTag = "[OTIME:$origTs]"
        val tags = listOf(vfTag, otimeTag).filter { it.isNotEmpty() }.joinToString("")
        val captionStr = if (tags.isNotEmpty()) "${localFile.name} $tags" else localFile.name

        val isSecure = isChatSecure(resolvedChatId) || (resolvedVfId != "0" && isVirtualFolderSecure(resolvedVfId))
        val tempId = "sync_up_${System.nanoTime()}"

        var tempEncFile: File? = null
        var tempMsgId: Long = 0L
        var tdFileId: Int = 0
        var activeUniqueId: String = tempId

        startTransferService()
        transferRepository.addTransfer(
            fileId = 0,
            remoteUniqueId = tempId,
            fileName = localFile.name,
            isDownload = false,
            totalSize = fileLength,
            isCompleted = false,
            status = TransferRepository.Status.UPLOADING,
            localPath = if (isSecure) null else localFile.absolutePath
        )

        try {
            kotlinx.coroutines.currentCoroutineContext().let { ctx ->
                if (!ctx[kotlinx.coroutines.Job]!!.isActive) throw kotlinx.coroutines.CancellationException()
            }

            var finalPath = localFile.absolutePath
            var finalCaption = captionStr
            if (isSecure) {
                val password = secureSessionManager.decryptedPassword.value
                    ?: masterPasswordService.getStoredPassword()?.also { secureSessionManager.setSessionPassword(it) }
                    ?: masterPasswordService.getOrCreateDefaultPassword().also { secureSessionManager.setSessionPassword(it) }
                val encDir = File(context.cacheDir, "enc_${System.nanoTime()}").apply { mkdirs() }
                tempEncFile = File(encDir, localFile.name)
                encryptionManager.encryptFile(localFile, tempEncFile!!, password) { }
                finalPath = tempEncFile!!.absolutePath
                finalCaption = "$captionStr [ENC]"
            }

            val mimeType = FileUtils.getMimeType(localFile.name)
            val generatedThumbInfo = if (!isSecure && (mimeType.startsWith("image/") || mimeType.startsWith("video/"))) {
                generatePreviewThumbnail(localFile.absolutePath, mimeType)
            } else null
            val tdInputThumb = generatedThumbInfo?.let { (thumbPath, w, h) ->
                TdApi.InputThumbnail(TdApi.InputFileLocal(thumbPath), w, h)
            }
            val formattedCaption = TdApi.FormattedText(finalCaption, emptyArray())
            val inputContent = TdApi.InputMessageDocument(TdApi.InputFileLocal(finalPath), tdInputThumb, true, formattedCaption)

            val sendRes = sendTdSuspend(TdApi.SendMessage(resolvedChatId, null, null, null, null, inputContent))
            if (sendRes !is TdApi.Message) {
                transferRepository.removeTransfer(tempId)
                return null
            }

            tempMsgId = sendRes.id
            val tdFile = (sendRes.content as? TdApi.MessageDocument)?.document?.document
            tdFileId = tdFile?.id ?: 0
            activeSyncUploads[tempId] = Triple(resolvedChatId, tempMsgId, tdFileId)

            saveLocalFileOriginalTime(tempMsgId, resolvedChatId, origTs)
            saveLocalFileRename(tempMsgId, resolvedChatId, localFile.name)
            saveLocalFileVirtualParent(tempMsgId, resolvedChatId, resolvedVfId)

            // Set handledByCaller = true so we don't spam 100+ redundant text messages during batch sync
            pendingSendMetaMap[tempMsgId] = PendingSendMeta(
                fileName = localFile.name,
                virtualFolderId = resolvedVfId,
                isEncrypted = isSecure,
                thumbnailPath = generatedThumbInfo?.first,
                localPath = if (isSecure) null else localFile.absolutePath,
                handledByCaller = true,
                originalTimestamp = origTs
            )

            if (tdFile != null) {
                val realUniqueId = tdFile.remote.uniqueId.ifEmpty { tempId }
                activeUniqueId = realUniqueId
                transferRepository.updateTransferBindingSync(tempId, realUniqueId, tdFile.id, finalPath)
            }

            // Wait for TDLib to complete message send with live progress & stall protection
            val msgDeferred: kotlinx.coroutines.CompletableDeferred<TdApi.Message?> =
                if (sendRes.sendingState != null) {
                    completedMessageSends.remove(tempMsgId)?.let { kotlinx.coroutines.CompletableDeferred<TdApi.Message?>(it) }
                        ?: pendingMessageSends.getOrPut(tempMsgId) { kotlinx.coroutines.CompletableDeferred<TdApi.Message?>() }
                } else {
                    kotlinx.coroutines.CompletableDeferred<TdApi.Message?>(sendRes)
                }

            var finalMsg: TdApi.Message? = null
            var lastProgressChangeMs = System.currentTimeMillis()
            var lastObservedBytes = -1L
            val startWaitMs = System.currentTimeMillis()

            while (!msgDeferred.isCompleted) {
                val doneEarly = completedMessageSends.remove(tempMsgId)
                if (doneEarly != null) {
                    msgDeferred.complete(doneEarly)
                    break
                }
                if (failedMessageSends.remove(tempMsgId) == true) {
                    msgDeferred.complete(null)
                    break
                }

                // Report live byte progress
                if (tdFileId != 0) {
                    val (upBytes, totalBytes) = liveFileUploadBytes[tdFileId] ?: (0L to fileLength)
                    val denom = if (totalBytes > 0L) totalBytes else fileLength
                    if (upBytes > lastObservedBytes) {
                        lastObservedBytes = upBytes
                        lastProgressChangeMs = System.currentTimeMillis()
                    }
                    if (denom > 0L) {
                        val frac = (upBytes.toFloat() / denom.toFloat()).coerceIn(0f, 0.99f)
                        onProgress?.invoke(frac)
                    }
                }

                val finishedInStep = withTimeoutOrNull(1200L) {
                    msgDeferred.await()
                }
                if (finishedInStep != null || msgDeferred.isCompleted) {
                    break
                }

                // Every ~3.6s, verify message state directly from TDLib in case update event was already processed
                val elapsedMs = System.currentTimeMillis() - startWaitMs
                if (elapsedMs > 3000L) {
                    val checkMsg = sendTdSuspend(TdApi.GetMessage(resolvedChatId, tempMsgId))
                    if (checkMsg is TdApi.Message) {
                        when (checkMsg.sendingState) {
                            null -> {
                                msgDeferred.complete(checkMsg)
                                break
                            }
                            is TdApi.MessageSendingStateFailed -> {
                                msgDeferred.complete(null)
                                break
                            }
                        }
                    } else if (checkMsg is TdApi.Error) {
                        // Message with tempMsgId no longer exists; check if UpdateMessageSendSucceeded already completed
                        val completed = completedMessageSends.remove(tempMsgId)
                        if (completed != null) {
                            msgDeferred.complete(completed)
                            break
                        } else if (elapsedMs > 8000L) {
                            // Check if file already landed in DAO under resolvedVfId
                            val landed = driveDao.getAllItemsSync().firstOrNull {
                                !it.isFolder &&
                                    it.parentChatId == resolvedChatId &&
                                    it.name == localFile.name &&
                                    it.size == fileLength
                            }
                            if (landed != null) {
                                activeSyncUploads.remove(tempId)
                                transferRepository.updateTransferManual(activeUniqueId, 1.0f, TransferRepository.Status.COMPLETED)
                                onProgress?.invoke(1.0f)
                                return landed.id
                            }
                            msgDeferred.complete(null)
                            break
                        }
                    }
                }

                // Stall guard: if zero upload progress for over 120 seconds, abort this file so queue never hangs
                if (System.currentTimeMillis() - lastProgressChangeMs > 120_000L) {
                    Log.w("DriveRepo", "Upload stalled for >120s on ${localFile.name}, aborting item")
                    telegramClient.send(TdApi.DeleteMessages(resolvedChatId, longArrayOf(tempMsgId), true)) {}
                    if (tdFileId != 0) {
                        telegramClient.send(TdApi.CancelPreliminaryUploadFile(tdFileId)) {}
                    }
                    msgDeferred.complete(null)
                    break
                }
            }

            finalMsg = if (msgDeferred.isCompleted) msgDeferred.getCompleted() else null
            activeSyncUploads.remove(tempId)
            if (tdFileId != 0) liveFileUploadBytes.remove(tdFileId)

            if (finalMsg != null) {
                saveLocalFileOriginalTime(finalMsg.id, finalMsg.chatId, origTs)
                saveLocalFileRename(finalMsg.id, finalMsg.chatId, localFile.name)
                saveLocalFileVirtualParent(finalMsg.id, finalMsg.chatId, resolvedVfId)

                synchronized(currentManifest) {
                    val updatedMappings = currentManifest.fileMappings.toMutableMap()
                    updatedMappings.remove(tempMsgId.toString())
                    updatedMappings[finalMsg.id.toString()] = resolvedVfId
                    currentManifest = currentManifest.copy(
                        fileMappings = updatedMappings,
                        updatedAt = System.currentTimeMillis() / 1000
                    )
                    saveLocalManifest()
                }

                val mapped = mapMessageToEntity(finalMsg.chatId, finalMsg)
                if (mapped != null) {
                    driveDao.insertItemsPreservingStarred(
                        listOf(
                            mapped.copy(
                                name = localFile.name,
                                size = fileLength,
                                virtualParentId = resolvedVfId,
                                isEncrypted = isSecure,
                                createdAt = origTs,
                                localPath = if (isSecure) null else localFile.absolutePath,
                                thumbnailPath = mapped.thumbnailPath ?: generatedThumbInfo?.first
                            )
                        )
                    )
                }
                onProgress?.invoke(1.0f)
                transferRepository.updateTransferManual(activeUniqueId, 1.0f, TransferRepository.Status.COMPLETED)
                if (activeUniqueId != tempId) {
                    transferRepository.removeTransfer(tempId)
                }
                return finalMsg.id
            } else {
                transferRepository.removeTransfer(activeUniqueId)
                if (activeUniqueId != tempId) transferRepository.removeTransfer(tempId)
                return null
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            // Immediate TDLib abort when user cancels sync!
            activeSyncUploads.remove(tempId)
            if (tdFileId != 0) liveFileUploadBytes.remove(tdFileId)
            if (tempMsgId != 0L) {
                pendingSendMetaMap.remove(tempMsgId)
                pendingMessageSends.remove(tempMsgId)?.cancel()
                completedMessageSends.remove(tempMsgId)
                telegramClient.send(TdApi.DeleteMessages(resolvedChatId, longArrayOf(tempMsgId), true)) {}
                scope.launch {
                    try {
                        driveDao.deleteItemCompletely(tempMsgId, resolvedChatId)
                    } catch (_: Exception) {}
                }
            }
            if (tdFileId != 0) {
                telegramClient.send(TdApi.CancelPreliminaryUploadFile(tdFileId)) {}
            }
            transferRepository.removeTransfer(activeUniqueId)
            if (activeUniqueId != tempId) transferRepository.removeTransfer(tempId)
            throw ce
        } catch (e: Exception) {
            Log.e("DriveRepo", "Sync upload failed for ${localFile.name}: ${e.message}")
            activeSyncUploads.remove(tempId)
            if (tdFileId != 0) liveFileUploadBytes.remove(tdFileId)
            transferRepository.removeTransfer(activeUniqueId)
            if (activeUniqueId != tempId) transferRepository.removeTransfer(tempId)
            return null
        } finally {
            tempEncFile?.let {
                try {
                    it.delete()
                    it.parentFile?.delete()
                } catch (_: Exception) {}
            }
        }
    }

    suspend fun downloadFileForSyncSuspend(
        fileEntity: DriveItemEntity,
        destLocalFile: File,
        onProgress: ((Float) -> Unit)? = null
    ): Boolean {
        val rootChat = ensureReadyChatIdSuspend()
        val chatId = if (fileEntity.parentChatId == 0L) rootChat else fileEntity.parentChatId
        if (chatId == 0L) return false

        val msgObj = sendTdSuspend(TdApi.GetMessage(chatId, fileEntity.id)) as? TdApi.Message ?: return false
        val tdFile = when (val content = msgObj.content) {
            is TdApi.MessageDocument -> content.document.document
            is TdApi.MessagePhoto -> content.photo.sizes.lastOrNull()?.photo
            is TdApi.MessageVideo -> content.video.video
            is TdApi.MessageAudio -> content.audio.audio
            else -> null
        } ?: return false

        var downloadedCachePath = tdFile.local.path
        val fileId = tdFile.id
        if (!tdFile.local.isDownloadingCompleted || downloadedCachePath.isEmpty() || !File(downloadedCachePath).exists()) {
            activeSyncDownloadFileIds.add(fileId)
            try {
                // Start non-blocking download and monitor progress so cancellation is immediate
                sendTdSuspend(TdApi.DownloadFile(fileId, 16, 0, 0, false))
                var lastProgressMs = System.currentTimeMillis()
                var lastBytes = -1L
                while (true) {
                    kotlinx.coroutines.currentCoroutineContext().let { ctx ->
                        if (!ctx[kotlinx.coroutines.Job]!!.isActive) throw kotlinx.coroutines.CancellationException()
                    }
                    val fObj = sendTdSuspend(TdApi.GetFile(fileId)) as? TdApi.File ?: break
                    if (fObj.local.isDownloadingCompleted && fObj.local.path.isNotEmpty() && File(fObj.local.path).exists()) {
                        downloadedCachePath = fObj.local.path
                        driveDao.updateLocalPathByUniqueId(fObj.remote.uniqueId, downloadedCachePath)
                        onProgress?.invoke(1.0f)
                        break
                    }
                    val dlBytes = fObj.local.downloadedSize
                    val total = maxOf(fObj.expectedSize, fObj.size, fileEntity.size)
                    if (dlBytes > lastBytes) {
                        lastBytes = dlBytes
                        lastProgressMs = System.currentTimeMillis()
                    }
                    if (total > 0L) {
                        onProgress?.invoke((dlBytes.toFloat() / total.toFloat()).coerceIn(0f, 0.99f))
                    }
                    if (!fObj.local.isDownloadingActive && !fObj.local.isDownloadingCompleted) {
                        // Re-trigger once if needed, or break if stalled
                        if (System.currentTimeMillis() - lastProgressMs > 15_000L) break
                        sendTdSuspend(TdApi.DownloadFile(fileId, 16, 0, 0, false))
                    }
                    if (System.currentTimeMillis() - lastProgressMs > 120_000L) {
                        telegramClient.send(TdApi.CancelDownloadFile(fileId, false)) {}
                        break
                    }
                    kotlinx.coroutines.delay(400L)
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                telegramClient.send(TdApi.CancelDownloadFile(fileId, false)) {}
                throw ce
            } finally {
                activeSyncDownloadFileIds.remove(fileId)
                liveFileDownloadBytes.remove(fileId)
            }
        }

        if (downloadedCachePath.isEmpty()) return false
        val srcFile = File(downloadedCachePath)
        if (!srcFile.exists()) return false

        return try {
            destLocalFile.parentFile?.mkdirs()
            if (fileEntity.isEncrypted) {
                val password = secureSessionManager.decryptedPassword.value
                    ?: masterPasswordService.getStoredPassword()?.also { secureSessionManager.setSessionPassword(it) }
                    ?: masterPasswordService.getOrCreateDefaultPassword().also { secureSessionManager.setSessionPassword(it) }
                encryptionManager.decryptFile(srcFile, destLocalFile, password) { }
            } else {
                srcFile.copyTo(destLocalFile, overwrite = true)
            }
            // Restore exact original creation/capture timestamp on local filesystem & MediaStore!
            FileUtils.restoreOriginalTimestampAndScan(context, destLocalFile, fileEntity.createdAt)
            onProgress?.invoke(1.0f)
            true
        } catch (e: Exception) {
            Log.e("DriveRepo", "Sync download copy failed: ${e.message}")
            false
        }
    }

    suspend fun deleteRemoteFileForSyncSuspend(messageId: Long, chatId: Long) {
        val rootChat = ensureReadyChatIdSuspend()
        val effectiveChat = if (chatId == 0L) rootChat else chatId
        if (effectiveChat == 0L) return

        sendTdSuspend(TdApi.DeleteMessages(effectiveChat, longArrayOf(messageId), true))
        driveDao.deleteItemCompletely(messageId, effectiveChat)

        var metaIdToDelete: Long? = null
        synchronized(currentManifest) {
            val updatedFileMetaIds = currentManifest.fileMetadataIds.toMutableMap()
            val updatedFileMappings = currentManifest.fileMappings.toMutableMap()
            val updatedTrashedFiles = currentManifest.trashedFileIds.toMutableMap()
            metaIdToDelete = updatedFileMetaIds.remove(messageId.toString())
            updatedFileMappings.remove(messageId.toString())
            updatedTrashedFiles.remove("${effectiveChat}_$messageId")
            updatedTrashedFiles.remove(messageId.toString())
            currentManifest = currentManifest.copy(
                fileMetadataIds = updatedFileMetaIds,
                fileMappings = updatedFileMappings,
                trashedFileIds = updatedTrashedFiles,
                updatedAt = System.currentTimeMillis() / 1000
            )
            saveLocalManifest()
        }
        if (metaIdToDelete != null && metaIdToDelete != 0L && rootChat != 0L) {
            sendTdSuspend(TdApi.DeleteMessages(rootChat, longArrayOf(metaIdToDelete!!), true))
        }
        saveCloudManifest()
    }
}
