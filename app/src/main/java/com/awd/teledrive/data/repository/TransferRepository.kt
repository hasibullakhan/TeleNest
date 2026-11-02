package com.awd.teledrive.data.repository

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.awd.teledrive.data.local.DriveDao
import com.awd.teledrive.data.local.TransferDao
import com.awd.teledrive.data.local.TransferEntity
import com.awd.teledrive.data.model.TransferInfo
import com.awd.teledrive.data.remote.TelegramClient
import com.awd.teledrive.data.secure.SecureSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransferRepository @Inject constructor(
    private val telegramClient: TelegramClient,
    private val secureSettings: SecureSettings,
    private val transferDao: TransferDao,
    private val driveDao: DriveDao,
    @param:ApplicationContext private val context: Context,
) {
    object Status {
        const val QUEUED = "Queued"
        const val DOWNLOADING = "Downloading"
        const val UPLOADING = "Uploading"
        const val COMPLETED = "Completed"
        const val FAILED = "Failed"
        const val CANCELLED = "Cancelled"
    }

    private data class PendingTransfer(
        val remoteUniqueId: String,
        val isDownload: Boolean,
        val block: suspend () -> Unit
    )

    private val queue = mutableListOf<PendingTransfer>()
    private var activeTransferId: String? = null
    private val completionDeferreds = mutableMapOf<String, CompletableDeferred<Unit>>()
    private val idMigrationMap = mutableMapOf<String, String>()
    private val lastUpdateMap = mutableMapOf<String, Long>()
    private val lastProgressMap = mutableMapOf<String, Float>()
    private val dbMutex = Mutex()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _cancelAllEvents = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val cancelAllEvents: kotlinx.coroutines.flow.SharedFlow<Unit> = _cancelAllEvents.asSharedFlow()

    // In-memory preview transfer state so opening files in PreviewScreen never triggers phantom notification bar downloads
    private val _previewTransfers = MutableStateFlow<Map<String, TransferInfo>>(emptyMap())
    val previewTransfers: StateFlow<Map<String, TransferInfo>> = _previewTransfers.asStateFlow()

    val transfers = transferDao.getAllTransfersFlow()
        .map { entities ->
            entities.associateBy({ it.remoteUniqueId }, { entity ->
                TransferInfo(
                    fileId = entity.fileId,
                    remoteUniqueId = entity.remoteUniqueId,
                    fileName = entity.fileName,
                    progress = entity.progress,
                    isDownload = entity.isDownload,
                    status = entity.status,
                    totalSize = entity.totalSize,
                    downloadedSize = entity.downloadedSize,
                    localPath = entity.localPath
                )
            })
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    init {
        scope.launch {
            // Clean up any stale/orphaned transfers from previous app sessions on startup
            dbMutex.withLock {
                try {
                    transferDao.clearAll()
                } catch (e: Exception) {
                    Log.e("TransferRepo", "Failed to clear stale transfers on startup: ${e.message}")
                }
            }

            telegramClient.fileUpdates.collect { update ->
                val file = update.file
                val uniqueId = file.remote.uniqueId
                val fileId = file.id
                val localPath = file.local.path

                // 1. Update in-memory preview transfers if applicable
                val currentPreviews = _previewTransfers.value
                val previewKey = when {
                    uniqueId.isNotEmpty() && currentPreviews.containsKey(uniqueId) -> uniqueId
                    else -> currentPreviews.values.find { it.fileId == fileId }?.remoteUniqueId
                }
                if (previewKey != null) {
                    val existingPreview = currentPreviews[previewKey]
                    if (existingPreview != null) {
                        val isDone = file.local.isDownloadingCompleted
                        val total = if (file.expectedSize > 0L) file.expectedSize else existingPreview.totalSize
                        val current = file.local.downloadedSize
                        val prog = if (isDone) 1f else if (total > 0L) (current.toFloat() / total.toFloat()).coerceIn(0f, 0.99f) else 0f
                        val newStatus = if (isDone) Status.COMPLETED else Status.DOWNLOADING
                        _previewTransfers.value = if (isDone) {
                            currentPreviews - previewKey
                        } else {
                            currentPreviews + (previewKey to existingPreview.copy(
                                progress = prog,
                                status = newStatus,
                                totalSize = total,
                                downloadedSize = current,
                                localPath = localPath.takeIf { it.isNotEmpty() }
                            ))
                        }
                    }
                }

                // Fast-path skip: do not lock dbMutex or query Room for background thumbnail downloads when no user transfer exists
                val hasAnyTrackedTransfer = transfers.value.isNotEmpty() || synchronized(queue) {
                    activeTransferId != null || queue.isNotEmpty()
                }
                if (!hasAnyTrackedTransfer) return@collect

                // 2. Update active user-initiated uploads/downloads in Room DB atomically
                dbMutex.withLock {
                    val entity = (if (localPath.isNotEmpty()) transferDao.getTransferByLocalPath(localPath) else null)
                        ?: (if (uniqueId.isNotEmpty()) transferDao.getTransferByUniqueId(uniqueId) else null)
                        ?: transferDao.getTransferByFileId(fileId)
                        ?: run {
                            val activeId = synchronized(queue) { activeTransferId }
                            if (activeId != null && !file.local.isDownloadingActive && (file.remote.isUploadingActive || file.remote.isUploadingCompleted)) {
                                transferDao.getTransferByUniqueId(activeId)
                            } else null
                        }
                        ?: return@withLock

                    val isDownload = entity.isDownload
                    val isCompleted = if (isDownload) file.local.isDownloadingCompleted else file.remote.isUploadingCompleted
                    val totalSize = when {
                        entity.totalSize > 0L -> entity.totalSize
                        file.expectedSize > 0L -> file.expectedSize
                        file.size > 0L -> file.size
                        else -> 0L
                    }

                    val currentSize = if (isDownload) file.local.downloadedSize else file.remote.uploadedSize
                    val rawProgress = if (totalSize > 0L) (currentSize.toFloat() / totalSize.toFloat()).coerceIn(0f, 1f) else entity.progress
                    val isEffectivelyCompleted = isCompleted || (!isDownload && totalSize > 0L && currentSize >= totalSize)
                    val progress = if (isEffectivelyCompleted) 1.0f else maxOf(entity.progress, rawProgress).coerceAtMost(0.99f)
                    val effectiveDownloadedSize = if (isEffectivelyCompleted) totalSize else maxOf(currentSize, (progress * totalSize).toLong())

                    val status = when {
                        isEffectivelyCompleted -> Status.COMPLETED
                        isDownload && file.local.isDownloadingActive -> Status.DOWNLOADING
                        !isDownload && (file.remote.isUploadingActive || file.remote.uploadedSize > 0L) -> Status.UPLOADING
                        file.local.canBeDownloaded.not() && isDownload && !file.local.isDownloadingCompleted -> Status.FAILED
                        else -> entity.status
                    }

                    // Synchronously migrate ID if TDLib assigned the real remote uniqueId, THEN update progress
                    var targetUniqueId = entity.remoteUniqueId
                    var idMigrated = false
                    if (uniqueId.isNotEmpty() && entity.remoteUniqueId != uniqueId) {
                        migrateUniqueIdLocked(entity.remoteUniqueId, uniqueId, fileId, localPath.takeIf { it.isNotEmpty() })
                        targetUniqueId = uniqueId
                        idMigrated = true
                    } else if (fileId != 0 && entity.fileId != fileId) {
                        transferDao.updateFileId(targetUniqueId, fileId)
                        idMigrated = true
                    }

                    val isTerminal = status == Status.COMPLETED || status == Status.FAILED || status == Status.CANCELLED
                    val now = System.currentTimeMillis()
                    val lastTime = lastUpdateMap[targetUniqueId] ?: 0L
                    val lastProg = lastProgressMap[targetUniqueId] ?: -1f
                    val shouldWriteProgress = isTerminal ||
                        idMigrated ||
                        status != entity.status ||
                        (now - lastTime >= 180L && kotlin.math.abs(progress - lastProg) >= 0.008f)

                    if (shouldWriteProgress) {
                        lastUpdateMap[targetUniqueId] = now
                        lastProgressMap[targetUniqueId] = progress
                        transferDao.updateProgress(targetUniqueId, progress, status, effectiveDownloadedSize)
                    }

                    if (isTerminal) {
                        lastUpdateMap.remove(targetUniqueId)
                        lastProgressMap.remove(targetUniqueId)
                        if (status == Status.COMPLETED && isDownload && localPath.isNotEmpty()) {
                            val driveItem = try {
                                driveDao.getItemByRemoteUniqueIdOrFileId(uniqueId, fileId)
                            } catch (_: Exception) {
                                null
                            }
                            saveToPublicDownloads(localPath, entity.fileName, driveItem?.createdAt ?: 0L)
                        }

                        synchronized(queue) {
                            if (uniqueId.isNotEmpty()) {
                                completionDeferreds[uniqueId]?.complete(Unit)
                            }
                            completionDeferreds[entity.remoteUniqueId]?.complete(Unit)
                            completionDeferreds[targetUniqueId]?.complete(Unit)
                            completionDeferreds[fileId.toString()]?.complete(Unit)

                            if (activeTransferId == uniqueId ||
                                activeTransferId == entity.remoteUniqueId ||
                                activeTransferId == targetUniqueId ||
                                activeTransferId == fileId.toString()
                            ) {
                                activeTransferId = null
                                processQueue()
                            }
                        }

                        // Only clear localPath in drive_items if it was a temporary encrypted or staging file
                        if (status == Status.COMPLETED && !isDownload && uniqueId.isNotEmpty()) {
                            if (localPath.contains("/enc_") || localPath.contains("/exact_")) {
                                scope.launch {
                                    driveDao.updateLocalPathByUniqueId(uniqueId, null)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun trackPreviewDownload(fileId: Int, remoteUniqueId: String, fileName: String, totalSize: Long) {
        val key = remoteUniqueId.ifEmpty { "prev_$fileId" }
        _previewTransfers.value = _previewTransfers.value + (key to TransferInfo(
            fileId = fileId,
            remoteUniqueId = key,
            fileName = fileName,
            progress = 0f,
            isDownload = true,
            status = Status.DOWNLOADING,
            totalSize = totalSize,
            downloadedSize = 0L,
            localPath = null
        ))
    }

    private suspend fun migrateUniqueIdLocked(oldId: String, newUniqueId: String, newFileId: Int, localPath: String? = null) {
        if (oldId == newUniqueId && localPath == null && newFileId == 0) return

        synchronized(queue) {
            if (oldId != newUniqueId) {
                idMigrationMap[oldId] = newUniqueId
            }
            if (activeTransferId == oldId) {
                activeTransferId = newUniqueId
            }
            completionDeferreds[oldId]?.let {
                completionDeferreds[newUniqueId] = it
                completionDeferreds.remove(oldId)
            }
        }

        try {
            if (oldId != newUniqueId) {
                val existing = transferDao.getTransferByUniqueId(newUniqueId)
                if (existing != null) {
                    transferDao.deleteTransfer(oldId)
                } else {
                    transferDao.updateUniqueId(oldId, newUniqueId)
                }
            }
            if (newFileId != 0) {
                transferDao.updateFileId(newUniqueId, newFileId)
            }
            if (localPath != null) {
                transferDao.updateLocalPath(newUniqueId, localPath)
            }
        } catch (e: Exception) {
            Log.e("TransferRepo", "Migration failed in DB: ${e.message}")
        }
    }

    fun saveToPublicDownloads(internalPath: String, fileName: String, originalTimestampMillis: Long = 0L) {
        try {
            val sourceFile = File(internalPath)
            if (!sourceFile.exists()) {
                Log.e("TransferRepo", "Source file not found: $internalPath")
                return
            }

            val resolvedTimestampMs = if (originalTimestampMillis > 0L) {
                originalTimestampMillis
            } else {
                com.awd.teledrive.core.utils.FileUtils.extractOriginalTimestampMillis(sourceFile)
            }

            val userDownloadUri = secureSettings.getDownloadUri()
            if (!userDownloadUri.isNullOrEmpty()) {
                saveToUserSelectedFolder(sourceFile, fileName, userDownloadUri)
                return
            }

            val extension = fileName.substringAfterLast('.', "").lowercase()
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"

            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir != null && (downloadsDir.exists() || downloadsDir.mkdirs()) && downloadsDir.canWrite()) {
                val destFile = File(downloadsDir, fileName)
                FileOutputStream(destFile).use { output ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(output)
                    }
                }
                com.awd.teledrive.core.utils.FileUtils.restoreOriginalTimestampAndScan(context, destFile, resolvedTimestampMs)
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    if (resolvedTimestampMs > 0L) {
                        put(MediaStore.MediaColumns.DATE_MODIFIED, resolvedTimestampMs / 1000L)
                        put(MediaStore.MediaColumns.DATE_TAKEN, resolvedTimestampMs)
                    }
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { output ->
                        FileInputStream(sourceFile).use { input ->
                            input.copyTo(output)
                        }
                    }
                    if (resolvedTimestampMs > 0L) {
                        try {
                            val tsValues = ContentValues().apply {
                                put(MediaStore.MediaColumns.DATE_MODIFIED, resolvedTimestampMs / 1000L)
                                put(MediaStore.MediaColumns.DATE_TAKEN, resolvedTimestampMs)
                            }
                            resolver.update(uri, tsValues, null, null)
                        } catch (_: Exception) {}
                    }
                    Log.d("TransferRepo", "Saved to Downloads folder: $fileName")
                }
            }
        } catch (e: Exception) {
            Log.e("TransferRepo", "Error saving file: ${e.message}")
        }
    }

    private fun saveToUserSelectedFolder(sourceFile: File, fileName: String, uriString: String) {
        try {
            val treeUri = android.net.Uri.parse(uriString)
            val docUri = DocumentFile.fromTreeUri(context, treeUri)

            if (docUri == null || !docUri.canWrite()) {
                secureSettings.saveDownloadUri("")
                return
            }

            val extension = fileName.substringAfterLast('.', "").lowercase()
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"

            val newFile = docUri.createFile(mimeType, fileName)
            if (newFile != null) {
                context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(output)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("TransferRepo", "Failed saving to custom folder: ${e.message}")
        }
    }

    fun updateRemoteUniqueId(oldId: String, newUniqueId: String, newFileId: Int, localPath: String? = null) {
        scope.launch {
            dbMutex.withLock {
                val resolvedOldId = synchronized(queue) { idMigrationMap[oldId] ?: oldId }
                val targetId = newUniqueId.ifEmpty { resolvedOldId }
                migrateUniqueIdLocked(resolvedOldId, targetId, newFileId, localPath)
            }
        }
    }

    suspend fun updateTransferBindingSync(oldId: String, newUniqueId: String, newFileId: Int, localPath: String? = null) {
        dbMutex.withLock {
            val resolvedOldId = synchronized(queue) { idMigrationMap[oldId] ?: oldId }
            val targetId = newUniqueId.ifEmpty { resolvedOldId }
            migrateUniqueIdLocked(resolvedOldId, targetId, newFileId, localPath)
        }
    }

    fun enqueueTransfer(
        fileId: Int,
        remoteUniqueId: String,
        fileName: String,
        isDownload: Boolean,
        totalSize: Long,
        localPath: String? = null,
        block: suspend () -> Unit
    ) {
        scope.launch {
            val key = if (remoteUniqueId.isNotEmpty()) remoteUniqueId else "temp_${System.currentTimeMillis()}_$fileId"

            val isStartingNewBatch = synchronized(queue) {
                if (activeTransferId == key || queue.any { it.remoteUniqueId == key }) return@launch
                val wasIdle = activeTransferId == null && queue.isEmpty()
                queue.add(PendingTransfer(key, isDownload, block))
                wasIdle
            }

            dbMutex.withLock {
                if (isStartingNewBatch) {
                    // Clear completed/cancelled/failed rows from earlier batches so the new batch count is 100% accurate
                    transferDao.clearCompleted()
                    if (!isDownload) {
                        transferDao.clearStaleDownloads()
                    }
                }
                val entity = TransferEntity(
                    remoteUniqueId = key,
                    fileId = fileId,
                    fileName = fileName,
                    progress = 0f,
                    isDownload = isDownload,
                    status = Status.QUEUED,
                    totalSize = totalSize,
                    downloadedSize = 0L,
                    localPath = localPath
                )
                transferDao.insertTransfer(entity)
            }

            processQueue()
        }
    }

    private fun processQueue() {
        synchronized(queue) {
            if (activeTransferId != null || queue.isEmpty()) return

            val next = queue.removeAt(0)
            activeTransferId = next.remoteUniqueId

            scope.launch {
                try {
                    dbMutex.withLock {
                        val targetId = synchronized(queue) { idMigrationMap[next.remoteUniqueId] ?: next.remoteUniqueId }
                        val entity = transferDao.getTransferByUniqueId(targetId)
                        if (entity != null) {
                            val startStatus = if (entity.isDownload) Status.DOWNLOADING else Status.UPLOADING
                            transferDao.updateProgress(targetId, 0f, startStatus, 0L)
                        }
                    }

                    next.block()
                } catch (e: Exception) {
                    Log.e("TransferRepo", "Error in queued task ${next.remoteUniqueId}: ${e.message}")
                    dbMutex.withLock {
                        val targetId = synchronized(queue) { idMigrationMap[next.remoteUniqueId] ?: next.remoteUniqueId }
                        transferDao.updateProgress(targetId, 0f, Status.FAILED, 0L)
                    }
                    synchronized(queue) {
                        val targetId = idMigrationMap[next.remoteUniqueId] ?: next.remoteUniqueId
                        if (activeTransferId == next.remoteUniqueId || activeTransferId == targetId) {
                            activeTransferId = null
                        }
                    }
                    processQueue()
                }
            }
        }
    }

    fun addTransfer(
        fileId: Int,
        remoteUniqueId: String,
        fileName: String,
        isDownload: Boolean,
        totalSize: Long = 0,
        isCompleted: Boolean = false,
        status: String? = null,
        localPath: String? = null
    ) {
        scope.launch {
            val key = if (remoteUniqueId.isNotEmpty()) remoteUniqueId else "temp_${System.currentTimeMillis()}_$fileId"
            val wasIdle = synchronized(queue) { activeTransferId == null && queue.isEmpty() }
            dbMutex.withLock {
                if (wasIdle && !isCompleted) {
                    transferDao.clearCompleted()
                }
                val entity = TransferEntity(
                    remoteUniqueId = key,
                    fileId = fileId,
                    fileName = fileName,
                    progress = if (isCompleted) 1f else 0f,
                    isDownload = isDownload,
                    status = status ?: if (isCompleted) Status.COMPLETED else (if (isDownload) Status.DOWNLOADING else Status.UPLOADING),
                    totalSize = totalSize,
                    downloadedSize = if (isCompleted) totalSize else 0L,
                    localPath = localPath
                )
                transferDao.insertTransfer(entity)

                if (entity.status == Status.DOWNLOADING || entity.status == Status.UPLOADING) {
                    synchronized(queue) {
                        activeTransferId = key
                    }
                }
            }
        }
    }

    fun updateTransferManual(remoteUniqueId: String, progress: Float, status: String, throttled: Boolean = false) {
        val targetId = synchronized(queue) { idMigrationMap[remoteUniqueId] ?: remoteUniqueId }

        if (throttled) {
            val now = System.currentTimeMillis()
            val lastTime = lastUpdateMap[targetId] ?: 0L
            val lastProgress = lastProgressMap[targetId] ?: -1f

            if (now - lastTime < 350 && kotlin.math.abs(progress - lastProgress) < 0.02f) {
                return
            }
            lastUpdateMap[targetId] = now
            lastProgressMap[targetId] = progress
        }

        scope.launch {
            dbMutex.withLock {
                try {
                    val resolvedId = synchronized(queue) { idMigrationMap[remoteUniqueId] ?: targetId }
                    val isTerminal = status == Status.COMPLETED ||
                        status == Status.FAILED ||
                        status == Status.CANCELLED ||
                        status.startsWith("Gagal") ||
                        status.startsWith("Failed")

                    val entity = transferDao.getTransferByUniqueId(resolvedId)
                        ?: transferDao.getTransferByUniqueId(remoteUniqueId)
                    val actualId = entity?.remoteUniqueId ?: resolvedId
                    val totalSize = entity?.totalSize ?: 0L
                    val clampedProgress = if (status == Status.COMPLETED) 1.0f else progress.coerceIn(0f, 1f)
                    val calculatedSize = if (status == Status.COMPLETED) totalSize else (clampedProgress * totalSize).toLong()

                    if (entity != null) {
                        transferDao.updateProgressFull(actualId, clampedProgress, status, calculatedSize)
                    } else {
                        transferDao.updateProgressOnly(actualId, clampedProgress, status)
                    }

                    if (isTerminal) {
                        synchronized(queue) {
                            completionDeferreds[actualId]?.complete(Unit)
                            completionDeferreds[resolvedId]?.complete(Unit)
                            completionDeferreds[remoteUniqueId]?.complete(Unit)
                            if (activeTransferId == actualId || activeTransferId == resolvedId || activeTransferId == remoteUniqueId) {
                                activeTransferId = null
                                processQueue()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("TransferRepo", "Failed to update progress for $targetId: ${e.message}")
                }
            }
        }
    }

    fun removeTransfer(remoteUniqueId: String) {
        scope.launch {
            dbMutex.withLock {
                transferDao.deleteTransfer(remoteUniqueId)
            }
        }
    }

    fun cancelTransfer(uniqueId: String) {
        if (uniqueId.startsWith("sync_")) {
            _cancelAllEvents.tryEmit(Unit)
        }
        scope.launch {
            var foundInQueue = false
            synchronized(queue) {
                val inQueue = queue.find { it.remoteUniqueId == uniqueId }
                if (inQueue != null) {
                    queue.remove(inQueue)
                    foundInQueue = true
                }
                completionDeferreds[uniqueId]?.complete(Unit)
            }

            dbMutex.withLock {
                if (foundInQueue) {
                    transferDao.updateProgress(uniqueId, 0f, Status.CANCELLED, 0L)
                    return@withLock
                }

                val entity = transferDao.getTransferByUniqueId(uniqueId) ?: return@withLock
                if (entity.fileId != 0) {
                    if (entity.isDownload) {
                        telegramClient.send(org.drinkless.tdlib.TdApi.CancelDownloadFile(entity.fileId, true))
                    } else {
                        telegramClient.send(org.drinkless.tdlib.TdApi.CancelPreliminaryUploadFile(entity.fileId))
                    }
                }
                transferDao.updateProgress(uniqueId, entity.progress, Status.CANCELLED, entity.downloadedSize)
            }

            synchronized(queue) {
                if (activeTransferId == uniqueId) {
                    activeTransferId = null
                    processQueue()
                }
            }
        }
    }

    fun cancelAllTransfers(notifyExternal: Boolean = true) {
        if (notifyExternal) {
            _cancelAllEvents.tryEmit(Unit)
        }
        synchronized(queue) {
            queue.clear()
            activeTransferId = null
            completionDeferreds.values.forEach { it.complete(Unit) }
            completionDeferreds.clear()
            idMigrationMap.clear()
        }
        scope.launch {
            dbMutex.withLock {
                try {
                    val activeItems = transfers.value.values.filter {
                        it.status == Status.DOWNLOADING ||
                            it.status == Status.UPLOADING ||
                            it.status == Status.QUEUED
                    }
                    activeItems.forEach { item ->
                        if (item.fileId != 0) {
                            if (item.isDownload) {
                                telegramClient.send(org.drinkless.tdlib.TdApi.CancelDownloadFile(item.fileId, true))
                            } else {
                                telegramClient.send(org.drinkless.tdlib.TdApi.CancelPreliminaryUploadFile(item.fileId))
                            }
                        }
                    }
                    transferDao.clearAll()
                } catch (e: Exception) {
                    Log.e("TransferRepo", "Error cancelling all transfers: ${e.message}")
                }
            }
        }
    }

    fun clearCompleted() {
        scope.launch {
            dbMutex.withLock {
                transferDao.clearCompleted()
            }
        }
    }

    suspend fun waitForCompletion(remoteUniqueId: String) {
        val finalId = synchronized(queue) { idMigrationMap[remoteUniqueId] ?: remoteUniqueId }

        val currentTransfer = transfers.value[finalId]
        val dbTransfer = dbMutex.withLock { transferDao.getTransferByUniqueId(finalId) }
        val status = dbTransfer?.status ?: currentTransfer?.status
        if (status != null) {
            if (status == Status.COMPLETED || status == Status.FAILED || status == Status.CANCELLED || status.startsWith("Failed") || status.startsWith("Gagal")) {
                return
            }
        }

        val deferred = synchronized(queue) {
            val targetId = idMigrationMap[remoteUniqueId] ?: remoteUniqueId
            completionDeferreds.getOrPut(targetId) {
                CompletableDeferred()
            }
        }
        deferred.await()
        synchronized(queue) {
            completionDeferreds.remove(finalId)
            val originalId = idMigrationMap.filterValues { it == finalId }.keys.firstOrNull()
            if (originalId != null) idMigrationMap.remove(originalId)
        }
    }
}
