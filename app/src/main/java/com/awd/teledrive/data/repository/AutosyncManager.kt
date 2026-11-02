package com.awd.teledrive.data.repository

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Environment
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import com.awd.teledrive.core.utils.FileUtils
import com.awd.teledrive.data.local.DriveItemEntity
import com.awd.teledrive.data.secure.SecureSettings
import com.awd.teledrive.data.worker.TeleDriveWorkerManager
import com.awd.teledrive.domain.model.AutosyncGlobalSettings
import com.awd.teledrive.domain.model.SyncActiveStatus
import com.awd.teledrive.domain.model.SyncFolderPair
import com.awd.teledrive.domain.model.SyncMethod
import com.awd.teledrive.domain.model.SyncStateStore
import com.awd.teledrive.domain.model.SyncedFileRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

import kotlin.coroutines.coroutineContext

@Singleton
class AutosyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val driveRepository: DriveRepository,
    private val transferRepository: TransferRepository,
    private val secureSettings: SecureSettings,
    private val workerManager: TeleDriveWorkerManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private val stateMutex = Mutex()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val prefs by lazy {
        context.getSharedPreferences("teledrive_autosync_store", Context.MODE_PRIVATE)
    }

    private val _folderPairs = MutableStateFlow<List<SyncFolderPair>>(emptyList())
    val folderPairs: StateFlow<List<SyncFolderPair>> = _folderPairs.asStateFlow()

    private val _globalSettings = MutableStateFlow(AutosyncGlobalSettings())
    val globalSettings: StateFlow<AutosyncGlobalSettings> = _globalSettings.asStateFlow()

    private val _activeStatus = MutableStateFlow(SyncActiveStatus())
    val activeStatus: StateFlow<SyncActiveStatus> = _activeStatus.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(0L)
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    @Volatile
    private var currentSyncJob: Job? = null
    @Volatile
    private var activeSyncRunJob: Job? = null
    private var periodicTickerJob: Job? = null
    private var instantDebounceJob: Job? = null
    private var mediaContentObserver: ContentObserver? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var observerSetupJob: Job? = null
    private val activeFileObservers = mutableListOf<FileObserver>()
    @Volatile
    private var pendingAutoDetectChanges: Boolean = false
    @Volatile
    private var lastManualCancelTime: Long = 0L
    @Volatile
    private var ignoreObserverEventsUntil: Long = 0L
    private val activelyModifiedLocalPaths = ConcurrentHashMap.newKeySet<String>()

    // In-memory cache of SyncStateStore to avoid decoding/encoding the entire JSON blob after every single file transfer
    @Volatile
    private var cachedSyncStateSlot: String? = null
    @Volatile
    private var cachedSyncStateStore: SyncStateStore? = null
    @Volatile
    private var lastSyncStateFlushMs: Long = 0L

    init {
        scope.launch {
            reloadFromStorage()
            ensureBackgroundMonitoringState()
        }
        scope.launch {
            transferRepository.cancelAllEvents.collect {
                cancelActiveSync(fromNotification = true)
            }
        }
    }

    private fun slotKey(base: String): String {
        val accountPrefs = context.getSharedPreferences("teledrive_accounts_store", Context.MODE_PRIVATE)
        val slot = accountPrefs.getString("active_account_slot", "default")?.takeIf { it.isNotBlank() } ?: "default"
        return "${base}_slot_$slot"
    }

    fun reloadFromStorage() {
        val loadedSettings = loadGlobalSettingsInternal()
        _globalSettings.value = loadedSettings

        var loadedPairs = loadFolderPairsInternal()
        if (loadedPairs.isEmpty()) {
            loadedPairs = migrateLegacyBackupFoldersIfNeeded()
        }
        _folderPairs.value = loadedPairs
        _lastSyncTime.value = prefs.getLong(slotKey("last_sync_time"), secureSettings.getLong("last_backup_time", 0L))
    }

    private fun migrateLegacyBackupFoldersIfNeeded(): List<SyncFolderPair> {
        val migratedFlagKey = slotKey("legacy_migrated_v1")
        if (prefs.getBoolean(migratedFlagKey, false)) return emptyList()

        val legacyFolders = secureSettings.getStringList("backup_folders")
        val pairs = mutableListOf<SyncFolderPair>()
        if (legacyFolders.isNotEmpty()) {
            legacyFolders.forEachIndexed { idx, path ->
                val folderName = File(path).name.ifBlank { "Backup_${idx + 1}" }
                pairs.add(
                    SyncFolderPair(
                        id = "pair_migrated_${idx}_${System.currentTimeMillis()}",
                        name = folderName,
                        localPath = path,
                        remotePathDisplay = "/$folderName",
                        remoteChatId = 0L,
                        remoteVirtualFolderId = "0",
                        syncMethod = SyncMethod.TWO_WAY,
                        excludeHiddenFiles = true,
                        excludeSubfolders = false,
                        instantUpload = true,
                        enabled = true
                    )
                )
            }
            saveFolderPairsInternal(pairs)
        }
        prefs.edit().putBoolean(migratedFlagKey, true).apply()
        return pairs
    }

    private fun loadGlobalSettingsInternal(): AutosyncGlobalSettings {
        val raw = prefs.getString(slotKey("global_settings"), null)
        if (!raw.isNullOrBlank()) {
            try {
                return json.decodeFromString<AutosyncGlobalSettings>(raw)
            } catch (e: Exception) {
                Log.w("AutosyncManager", "Failed decoding global settings: ${e.message}")
            }
        }
        // Fallback to existing SecureSettings values
        val legacyEnabled = secureSettings.getBoolean("auto_backup_enabled", false)
        val initial = AutosyncGlobalSettings(
            enableAutosync = legacyEnabled,
            scheduleIntervalMinutes = AutosyncGlobalSettings.SCHEDULE_AUTO_DETECT,
            wifiOnly = secureSettings.getBoolean("backup_wifi_only", true),
            onlyWhileCharging = secureSettings.getBoolean("backup_charging_only", false),
            minBatteryLevel = 30
        )
        saveGlobalSettingsInternal(initial)
        return initial
    }

    private fun saveGlobalSettingsInternal(settings: AutosyncGlobalSettings) {
        prefs.edit().putString(slotKey("global_settings"), json.encodeToString(settings)).apply()
        secureSettings.saveBoolean("auto_backup_enabled", settings.enableAutosync)
        secureSettings.saveBoolean("backup_wifi_only", settings.wifiOnly)
        secureSettings.saveBoolean("backup_charging_only", settings.onlyWhileCharging)
    }

    private fun loadFolderPairsInternal(): List<SyncFolderPair> {
        val raw = prefs.getString(slotKey("folder_pairs"), null) ?: return emptyList()
        return try {
            json.decodeFromString<List<SyncFolderPair>>(raw)
        } catch (e: Exception) {
            Log.w("AutosyncManager", "Failed decoding folder pairs: ${e.message}")
            emptyList()
        }
    }

    private fun saveFolderPairsInternal(pairs: List<SyncFolderPair>) {
        prefs.edit().putString(slotKey("folder_pairs"), json.encodeToString(pairs)).apply()
        secureSettings.saveStringList("backup_folders", pairs.filter { it.enabled }.map { it.localPath }.distinct())
    }

    private fun loadSyncStateStore(): SyncStateStore {
        val currentSlot = slotKey("sync_state_store")
        val existing = cachedSyncStateStore
        if (existing != null && cachedSyncStateSlot == currentSlot) {
            return existing
        }
        val raw = prefs.getString(currentSlot, null)
        val loaded = if (raw.isNullOrBlank()) {
            SyncStateStore()
        } else {
            try {
                json.decodeFromString<SyncStateStore>(raw)
            } catch (e: Exception) {
                SyncStateStore()
            }
        }
        cachedSyncStateSlot = currentSlot
        cachedSyncStateStore = loaded
        return loaded
    }

    private fun saveSyncStateStore(store: SyncStateStore, forceFlush: Boolean = true) {
        val currentSlot = slotKey("sync_state_store")
        cachedSyncStateSlot = currentSlot
        cachedSyncStateStore = store
        val now = System.currentTimeMillis()
        if (forceFlush || now - lastSyncStateFlushMs >= 2_500L) {
            lastSyncStateFlushMs = now
            prefs.edit().putString(currentSlot, json.encodeToString(store)).apply()
        }
    }

    fun updateGlobalSettings(transform: (AutosyncGlobalSettings) -> AutosyncGlobalSettings) {
        val updated = transform(_globalSettings.value)
        _globalSettings.value = updated
        saveGlobalSettingsInternal(updated)
        if (!updated.enableAutosync && _activeStatus.value.isRunning) {
            cancelActiveSync()
        }
        ensureBackgroundMonitoringState()
    }

    fun saveFolderPair(pair: SyncFolderPair) {
        // Cancel any active sync so an edited folder pair never keeps uploading to a stale target folder
        if (_activeStatus.value.isRunning) {
            cancelActiveSync()
        }
        val current = _folderPairs.value.toMutableList()
        val idx = current.indexOfFirst { it.id == pair.id }
        val normalizedPair = if (idx >= 0) {
            val old = current[idx]
            if (old.remotePathDisplay.trim() != pair.remotePathDisplay.trim()) {
                // Reset sync history if destination cloud folder changed so files sync cleanly to the new target
                scope.launch {
                    stateMutex.withLock {
                        val store = loadSyncStateStore()
                        val updatedMap = store.recordsByPair.toMutableMap()
                        updatedMap.remove(pair.id)
                        saveSyncStateStore(store.copy(recordsByPair = updatedMap))
                    }
                }
            }
            pair
        } else {
            pair
        }

        if (idx >= 0) {
            current[idx] = normalizedPair
        } else {
            current.add(normalizedPair)
        }
        _folderPairs.value = current
        saveFolderPairsInternal(current)
        ensureBackgroundMonitoringState()
        if (_globalSettings.value.enableAutosync && normalizedPair.enabled) {
            triggerInstantSyncDebounced(delayMs = 1200L)
        }
    }

    fun deleteFolderPair(pairId: String) {
        if (_activeStatus.value.isRunning) {
            cancelActiveSync()
        }
        val current = _folderPairs.value.filterNot { it.id == pairId }
        _folderPairs.value = current
        saveFolderPairsInternal(current)
        scope.launch {
            stateMutex.withLock {
                val store = loadSyncStateStore()
                val updatedMap = store.recordsByPair.toMutableMap()
                updatedMap.remove(pairId)
                saveSyncStateStore(store.copy(recordsByPair = updatedMap))
            }
        }
        ensureBackgroundMonitoringState()
    }

    fun toggleFolderPairEnabled(pairId: String, enabled: Boolean) {
        if (!enabled && _activeStatus.value.isRunning && _activeStatus.value.activePairId == pairId) {
            cancelActiveSync()
        }
        val current = _folderPairs.value.map {
            if (it.id == pairId) it.copy(enabled = enabled) else it
        }
        _folderPairs.value = current
        saveFolderPairsInternal(current)
        ensureBackgroundMonitoringState()
        if (enabled && _globalSettings.value.enableAutosync) {
            triggerInstantSyncDebounced(delayMs = 1200L)
        }
    }

    fun resetFolderPairSyncState(pairId: String) {
        if (_activeStatus.value.isRunning && _activeStatus.value.activePairId == pairId) {
            cancelActiveSync()
        }
        scope.launch {
            stateMutex.withLock {
                val store = loadSyncStateStore()
                val updatedMap = store.recordsByPair.toMutableMap()
                updatedMap.remove(pairId)
                saveSyncStateStore(store.copy(recordsByPair = updatedMap))
            }
            val updatedPairs = _folderPairs.value.map {
                if (it.id == pairId) {
                    it.copy(lastSyncTime = 0L, lastSyncStatus = "Sync history reset", totalSyncedFiles = 0)
                } else it
            }
            _folderPairs.value = updatedPairs
            saveFolderPairsInternal(updatedPairs)
        }
    }

    fun ensureBackgroundMonitoringState() {
        val settings = _globalSettings.value
        val enabledPairs = _folderPairs.value.filter { it.enabled && it.localPath.isNotBlank() }

        if (settings.enableAutosync && enabledPairs.isNotEmpty()) {
            val isAutoDetectMode = settings.scheduleIntervalMinutes == AutosyncGlobalSettings.SCHEDULE_AUTO_DETECT
            if (isAutoDetectMode) {
                workerManager.scheduleAutosyncWork(
                    intervalMinutes = 60,
                    wifiOnly = settings.wifiOnly,
                    chargingOnly = settings.onlyWhileCharging
                )
                periodicTickerJob?.cancel()
                periodicTickerJob = null
                registerInstantUploadObservers(enabledPairs)
            } else if (settings.scheduleIntervalMinutes > 0) {
                workerManager.scheduleAutosyncWork(
                    intervalMinutes = settings.scheduleIntervalMinutes,
                    wifiOnly = settings.wifiOnly,
                    chargingOnly = settings.onlyWhileCharging
                )
                startPeriodicTicker(settings.scheduleIntervalMinutes)
                if (enabledPairs.any { it.instantUpload }) {
                    registerInstantUploadObservers(enabledPairs.filter { it.instantUpload })
                } else {
                    unregisterInstantUploadObservers()
                }
            } else {
                // Manual only (0)
                workerManager.cancelPeriodicBackup()
                periodicTickerJob?.cancel()
                periodicTickerJob = null
                unregisterInstantUploadObservers()
            }
        } else {
            workerManager.cancelPeriodicBackup()
            periodicTickerJob?.cancel()
            periodicTickerJob = null
            unregisterInstantUploadObservers()
        }
    }

    private fun startPeriodicTicker(intervalMinutes: Int) {
        periodicTickerJob?.cancel()
        if (intervalMinutes <= 0) return
        val delayMs = (intervalMinutes.coerceAtLeast(5)) * 60_000L
        periodicTickerJob = scope.launch {
            while (isActive) {
                delay(delayMs)
                if (_globalSettings.value.enableAutosync && !_activeStatus.value.isRunning) {
                    runScheduledSync(isManual = false, onlyInstantPairs = false)
                }
            }
        }
    }

    private fun shouldIgnoreObserverTrigger(fullPath: String? = null): Boolean {
        val now = System.currentTimeMillis()
        if (now < ignoreObserverEventsUntil) return true
        if (now - lastManualCancelTime < 5_000L) return true
        if (fullPath != null && activelyModifiedLocalPaths.contains(fullPath)) return true
        return false
    }

    private fun registerInstantUploadObservers(monitoredPairs: List<SyncFolderPair>) {
        unregisterInstantUploadObservers()
        observerSetupJob = scope.launch(Dispatchers.IO) {
            try {
                val handler = Handler(Looper.getMainLooper())
                val observer = object : ContentObserver(handler) {
                    override fun onChange(selfChange: Boolean, uri: Uri?) {
                        super.onChange(selfChange, uri)
                        if (shouldIgnoreObserverTrigger()) return
                        pendingAutoDetectChanges = true
                        triggerInstantSyncDebounced()
                    }
                }
                val resolver = context.contentResolver
                resolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
                resolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
                resolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
                runCatching {
                    resolver.registerContentObserver(MediaStore.Files.getContentUri("external"), true, observer)
                }
                mediaContentObserver = observer

                // Attach recursive FileObservers on paired local directories & their subdirectories (capped at 150) on IO thread
                val watchedPaths = LinkedHashSet<String>()
                fun collectWatchableDirs(dir: File, includeChildren: Boolean) {
                    if (!isActive || watchedPaths.size >= 150 || !dir.exists() || !dir.isDirectory) return
                    val abs = dir.absolutePath
                    if (abs == "/storage/emulated/0" || abs == "/storage/emulated/0/") {
                        val topDirs = listOf("DCIM", "Pictures", "Download", "Documents", "Movies", "Music", "Recordings", "Android/media")
                        topDirs.forEach { rel ->
                            val sub = File(dir, rel)
                            if (sub.exists() && sub.isDirectory) {
                                collectWatchableDirs(sub, includeChildren)
                            }
                        }
                        return
                    }
                    watchedPaths.add(abs)
                    if (includeChildren && watchedPaths.size < 150) {
                        val children = dir.listFiles() ?: return
                        for (child in children) {
                            if (!isActive || watchedPaths.size >= 150) break
                            if (child.isDirectory && !child.name.startsWith(".") &&
                                !child.name.equals(".thumbnails", ignoreCase = true)
                            ) {
                                collectWatchableDirs(child, true)
                            }
                        }
                    }
                }

                monitoredPairs.forEach { pair ->
                    if (!isActive) return@launch
                    val dir = File(pair.localPath)
                    if (dir.exists() && dir.isDirectory) {
                        collectWatchableDirs(dir, !pair.excludeSubfolders)
                    }
                }

                watchedPaths.forEach { dirPath ->
                    if (!isActive) return@launch
                    try {
                        @Suppress("DEPRECATION")
                        val fileObs = object : FileObserver(
                            dirPath,
                            CLOSE_WRITE or MOVED_TO or CREATE or DELETE or MOVED_FROM
                        ) {
                            override fun onEvent(event: Int, path: String?) {
                                if (!path.isNullOrBlank() && !path.startsWith(".") && !path.endsWith(".tmp_download")) {
                                    val fullFilePath = "$dirPath/$path"
                                    if (shouldIgnoreObserverTrigger(fullFilePath)) return
                                    pendingAutoDetectChanges = true
                                    triggerInstantSyncDebounced()
                                }
                            }
                        }
                        fileObs.startWatching()
                        synchronized(activeFileObservers) {
                            activeFileObservers.add(fileObs)
                        }
                    } catch (e: Exception) {
                        Log.w("AutosyncManager", "FileObserver error on $dirPath: ${e.message}")
                    }
                }

                // Register NetworkCallback so if files changed while offline, sync starts when network is ready
                try {
                    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    val cb = object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: android.net.Network) {
                            super.onAvailable(network)
                            if (pendingAutoDetectChanges && _globalSettings.value.enableAutosync && !shouldIgnoreObserverTrigger()) {
                                triggerInstantSyncDebounced()
                            }
                        }
                    }
                    cm.registerDefaultNetworkCallback(cb)
                    networkCallback = cb
                } catch (_: Exception) {}
            } catch (e: Exception) {
                Log.w("AutosyncManager", "Failed registering instant observers: ${e.message}")
            }
        }
    }

    private fun unregisterInstantUploadObservers() {
        observerSetupJob?.cancel()
        observerSetupJob = null
        try {
            mediaContentObserver?.let { context.contentResolver.unregisterContentObserver(it) }
        } catch (_: Exception) {}
        mediaContentObserver = null

        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            networkCallback?.let { cm.unregisterNetworkCallback(it) }
        } catch (_: Exception) {}
        networkCallback = null

        synchronized(activeFileObservers) {
            activeFileObservers.forEach {
                try {
                    it.stopWatching()
                } catch (_: Exception) {}
            }
            activeFileObservers.clear()
        }
    }

    private fun triggerInstantSyncDebounced(delayMs: Long = 2800L) {
        if (!_globalSettings.value.enableAutosync) return
        instantDebounceJob?.cancel()
        instantDebounceJob = scope.launch {
            delay(delayMs)
            if (System.currentTimeMillis() - lastManualCancelTime < 5_000L) return@launch
            val isAutoDetectSchedule = _globalSettings.value.scheduleIntervalMinutes == AutosyncGlobalSettings.SCHEDULE_AUTO_DETECT
            val ok = runScheduledSync(isManual = false, onlyInstantPairs = !isAutoDetectSchedule)
            if (ok) {
                pendingAutoDetectChanges = false
            }
        }
    }

    fun triggerSyncAllNow() {
        if (_activeStatus.value.isRunning) return
        currentSyncJob = scope.launch {
            runScheduledSync(isManual = true, onlyInstantPairs = false)
        }
    }

    fun triggerSyncSinglePairNow(pairId: String) {
        if (_activeStatus.value.isRunning) return
        currentSyncJob = scope.launch {
            runSinglePairSyncSuspend(pairId = pairId, isManual = true)
        }
    }

    fun cancelActiveSync(fromNotification: Boolean = false) {
        val now = System.currentTimeMillis()
        lastManualCancelTime = now
        ignoreObserverEventsUntil = now + 5_000L
        pendingAutoDetectChanges = false

        instantDebounceJob?.cancel()
        instantDebounceJob = null

        currentSyncJob?.cancel()
        currentSyncJob = null

        activeSyncRunJob?.cancel()
        activeSyncRunJob = null

        workerManager.cancelRunningManualBackup()

        scope.launch {
            runCatching { driveRepository.cancelAllSyncTransfers() }
            runCatching { transferRepository.cancelAllTransfers(notifyExternal = !fromNotification) }
        }

        _activeStatus.value = SyncActiveStatus(
            isRunning = false,
            statusMessage = "Sync cancelled"
        )
    }

    suspend fun runScheduledSync(isManual: Boolean = false, onlyInstantPairs: Boolean = false): Boolean {
        if (syncMutex.isLocked && !isManual) return false
        return syncMutex.withLock {
            val myJob = coroutineContext[Job]
            activeSyncRunJob = myJob
            try {
                val settings = _globalSettings.value
                if (!isManual && !settings.enableAutosync) return@withLock false

                val pairsToRun = _folderPairs.value.filter { pair ->
                    if (pair.localPath.isBlank()) {
                        false
                    } else if (isManual) {
                        pair.enabled || _folderPairs.value.size == 1
                    } else {
                        pair.enabled && (!onlyInstantPairs || pair.instantUpload)
                    }
                }
                if (pairsToRun.isEmpty()) return@withLock false

                if (!isManual) {
                    val constraintError = checkAutomaticSyncConstraints(settings)
                    if (constraintError != null) {
                        Log.d("AutosyncManager", "Skipping automatic sync due to constraint: $constraintError")
                        return@withLock false
                    }
                }

                var allSucceeded = true
                for (pair in pairsToRun) {
                    if (myJob?.isActive == false) break
                    val latestPair = _folderPairs.value.firstOrNull { it.id == pair.id } ?: continue
                    if (!isManual && !latestPair.enabled) continue
                    val ok = executePairWithRetry(latestPair, settings, isManual)
                    if (!ok) allSucceeded = false
                }

                val now = System.currentTimeMillis()
                _lastSyncTime.value = now
                prefs.edit().putLong(slotKey("last_sync_time"), now).apply()
                secureSettings.saveLong("last_backup_time", now)

                _activeStatus.value = SyncActiveStatus(
                    isRunning = false,
                    statusMessage = if (allSucceeded) "All folders synced" else "Completed with some skipped/failed items"
                )
                allSucceeded
            } catch (ce: CancellationException) {
                _activeStatus.value = SyncActiveStatus(
                    isRunning = false,
                    statusMessage = "Sync cancelled"
                )
                throw ce
            } finally {
                if (activeSyncRunJob === myJob) {
                    activeSyncRunJob = null
                }
            }
        }
    }

    suspend fun runSinglePairSyncSuspend(pairId: String, isManual: Boolean = true): Boolean {
        return syncMutex.withLock {
            val myJob = coroutineContext[Job]
            activeSyncRunJob = myJob
            try {
                val pair = _folderPairs.value.firstOrNull { it.id == pairId } ?: return@withLock false
                val settings = _globalSettings.value
                val ok = executePairWithRetry(pair, settings, isManual)
                val now = System.currentTimeMillis()
                _lastSyncTime.value = now
                prefs.edit().putLong(slotKey("last_sync_time"), now).apply()
                secureSettings.saveLong("last_backup_time", now)
                _activeStatus.value = SyncActiveStatus(
                    isRunning = false,
                    statusMessage = if (ok) "Synced ${pair.effectiveTitle}" else "Sync finished with errors"
                )
                ok
            } catch (ce: CancellationException) {
                _activeStatus.value = SyncActiveStatus(
                    isRunning = false,
                    statusMessage = "Sync cancelled"
                )
                throw ce
            } finally {
                if (activeSyncRunJob === myJob) {
                    activeSyncRunJob = null
                }
            }
        }
    }

    private suspend fun executePairWithRetry(
        pair: SyncFolderPair,
        settings: AutosyncGlobalSettings,
        isManual: Boolean
    ): Boolean {
        val maxAttempts = if (settings.retryAutomatically) {
            (1 + settings.maxRetryAttempts).coerceAtLeast(1)
        } else {
            1
        }
        var attempt = 1
        while (attempt <= maxAttempts) {
            try {
                val latestPair = _folderPairs.value.firstOrNull { it.id == pair.id } ?: return false
                val success = executeFolderPairSyncCore(latestPair, settings, isManual)
                if (success) return true
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e("AutosyncManager", "Sync error on ${pair.effectiveTitle} (attempt $attempt): ${e.message}")
            }
            attempt++
            if (attempt <= maxAttempts) {
                val waitMinutes = settings.retryWaitMinutes.coerceAtLeast(1)
                _activeStatus.value = _activeStatus.value.copy(
                    isRunning = true,
                    activePairId = pair.id,
                    activePairName = pair.effectiveTitle,
                    currentAction = "Waiting to retry ($attempt/$maxAttempts)...",
                    statusMessage = "Retrying in ${ if (isManual) "5 seconds" else "$waitMinutes min" }"
                )
                delay(if (isManual) 5_000L else waitMinutes * 60_000L)
            }
        }
        return false
    }

    private data class NetworkStateInfo(
        val isConnected: Boolean,
        val isWifi: Boolean,
        val isCellular: Boolean,
        val isMetered: Boolean,
        val isRoaming: Boolean
    )

    private fun getActiveNetworkState(): NetworkStateInfo {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return NetworkStateInfo(false, false, false, false, false)
            val caps = cm.getNetworkCapabilities(net) ?: return NetworkStateInfo(false, false, false, false, false)
            val isConnected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val isCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            val isMetered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            val isRoaming = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
            NetworkStateInfo(isConnected, isWifi, isCellular, isMetered, isRoaming)
        } catch (e: Exception) {
            NetworkStateInfo(true, true, false, false, false)
        }
    }

    fun isCurrentlyOnMobileData(): Boolean {
        val state = getActiveNetworkState()
        return state.isConnected && state.isCellular && !state.isWifi
    }

    private fun checkAutomaticSyncConstraints(settings: AutosyncGlobalSettings): String? {
        // 1. Battery & Charging check
        try {
            val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
                context.registerReceiver(null, filter)
            }
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else 100

            if (settings.onlyWhileCharging && !isCharging) {
                return "Waiting for charger"
            }
            if (!isCharging && settings.minBatteryLevel > 0 && batteryPct < settings.minBatteryLevel) {
                return "Battery below ${settings.minBatteryLevel}% ($batteryPct%)"
            }
        } catch (_: Exception) {}

        // 2. Network check
        val net = getActiveNetworkState()
        if (!net.isConnected) return "No internet connection"
        if (settings.wifiOnly && !net.isWifi) return "Waiting for Wi-Fi"
        if (net.isWifi && net.isMetered && !settings.allowedOnMeteredWifi) return "Metered Wi-Fi not allowed"
        if (net.isCellular && net.isRoaming && !settings.allowedOnRoaming) return "Roaming not allowed"

        return null
    }

    private fun isFileSizeAllowed(fileSize: Long, isUpload: Boolean, settings: AutosyncGlobalSettings): Boolean {
        val net = getActiveNetworkState()
        val limit = if (net.isWifi) {
            if (isUpload) settings.wifiUploadSizeLimitBytes else settings.wifiDownloadSizeLimitBytes
        } else {
            if (isUpload) settings.mobileUploadSizeLimitBytes else settings.mobileDownloadSizeLimitBytes
        }
        return limit <= 0L || fileSize <= limit
    }

    private fun matchesFileNameFilter(fileName: String, pair: SyncFolderPair): Boolean {
        if (!pair.selectFilesByName || pair.nameFilterPattern.isBlank()) return true
        val rawPatterns = pair.nameFilterPattern
            .split(',', ';', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (rawPatterns.isEmpty()) return true

        return rawPatterns.any { pat ->
            if (pat.contains('*') || pat.contains('?')) {
                val regexStr = "^" + pat
                    .replace(".", "\\.")
                    .replace("*", ".*")
                    .replace("?", ".") + "$"
                runCatching { Regex(regexStr, RegexOption.IGNORE_CASE).matches(fileName) }.getOrDefault(false)
            } else {
                fileName.contains(pat, ignoreCase = true)
            }
        }
    }

    private fun shouldSkipDirectory(dir: File, rootDir: File, excludeHidden: Boolean): Boolean {
        val name = dir.name
        if (excludeHidden && name.startsWith(".")) return true
        if (name.equals(".thumbnails", ignoreCase = true) ||
            name.equals(".Trash", ignoreCase = true) ||
            name.equals(".trash", ignoreCase = true)
        ) return true

        val rootPath = rootDir.absolutePath.trimEnd('/')
        if (rootPath == "/storage/emulated/0") {
            val rel = dir.absolutePath.removePrefix("/storage/emulated/0").trimStart('/')
            if (rel.equals("Android/data", ignoreCase = true) ||
                rel.equals("Android/obb", ignoreCase = true) ||
                rel.startsWith("Android/data/", ignoreCase = true) ||
                rel.startsWith("Android/obb/", ignoreCase = true)
            ) {
                return true
            }
        }
        return false
    }

    private fun collectLocalFiles(
        rootDir: File,
        pair: SyncFolderPair
    ): Map<String, File> {
        if (!rootDir.exists() || !rootDir.isDirectory) return emptyMap()
        val result = LinkedHashMap<String, File>()

        fun walkDir(currentDir: File, relPrefix: String) {
            val children = currentDir.listFiles() ?: return
            for (child in children) {
                if (child.isDirectory) {
                    if (!pair.excludeSubfolders && !shouldSkipDirectory(child, rootDir, pair.excludeHiddenFiles)) {
                        val nextPrefix = if (relPrefix.isEmpty()) child.name else "$relPrefix/${child.name}"
                        walkDir(child, nextPrefix)
                    }
                } else if (child.isFile) {
                    if (child.name.endsWith(".tmp_download")) continue
                    if (pair.excludeHiddenFiles && child.name.startsWith(".")) continue
                    if (child.length() <= 0L) continue
                    if (!matchesFileNameFilter(child.name, pair)) continue
                    val relPath = if (relPrefix.isEmpty()) child.name else "$relPrefix/${child.name}"
                    result[relPath] = child
                }
            }
        }

        walkDir(rootDir, "")
        return result
    }

    private fun cleanupEmptyLocalSubfolders(rootDir: File) {
        if (!rootDir.exists() || !rootDir.isDirectory) return
        fun cleanRecursive(dir: File): Boolean {
            val children = dir.listFiles() ?: return false
            for (child in children) {
                if (child.isDirectory) {
                    cleanRecursive(child)
                }
            }
            val remaining = dir.listFiles()
            if (dir != rootDir && remaining != null && remaining.isEmpty()) {
                return dir.delete()
            }
            return false
        }
        cleanRecursive(rootDir)
    }

    private suspend fun executeFolderPairSyncCore(
        pair: SyncFolderPair,
        settings: AutosyncGlobalSettings,
        isManual: Boolean
    ): Boolean {
        val currentPair = _folderPairs.value.firstOrNull { it.id == pair.id } ?: return false
        if (!isManual && !currentPair.enabled) return false

        _activeStatus.value = SyncActiveStatus(
            isRunning = true,
            activePairId = currentPair.id,
            activePairName = currentPair.effectiveTitle,
            currentAction = "Scanning folders...",
            statusMessage = "Preparing ${currentPair.effectiveTitle}"
        )

        val rootChatId = driveRepository.ensureReadyChatIdSuspend()
        if (rootChatId == 0L) {
            updatePairStatus(currentPair.id, "Not logged in to Telegram", currentPair.totalSyncedFiles)
            return false
        }

        val localRoot = File(currentPair.localPath)
        if (!localRoot.exists()) {
            localRoot.mkdirs()
        }

        // Always resolve the exact target remote folder matching remotePathDisplay so stale folder IDs are never used
        val (baseChatId, baseVfId) = driveRepository.resolveSyncTargetFolderSuspend(
            remotePathDisplay = currentPair.remotePathDisplay,
            preferredChatId = currentPair.remoteChatId,
            preferredVfId = currentPair.remoteVirtualFolderId
        )

        if (baseChatId != currentPair.remoteChatId || baseVfId != currentPair.remoteVirtualFolderId) {
            val updatedPair = currentPair.copy(remoteChatId = baseChatId, remoteVirtualFolderId = baseVfId)
            val currentList = _folderPairs.value.map { if (it.id == currentPair.id) updatedPair else it }
            _folderPairs.value = currentList
            saveFolderPairsInternal(currentList)
        }

        val includeSubfolders = !currentPair.excludeSubfolders
        val localFilesMap = collectLocalFiles(localRoot, currentPair)
        val remoteFilesList = driveRepository.getRemoteTreeFilesForSyncSuspend(
            baseChatId = baseChatId,
            baseVirtualFolderId = baseVfId,
            includeSubfolders = includeSubfolders
        ).filter { (_, entity) ->
            val fileName = entity.name
            !(currentPair.excludeHiddenFiles && fileName.startsWith(".")) &&
                matchesFileNameFilter(fileName, currentPair)
        }

        // Group all remote entities by relative path (so deleting a file deletes all duplicate copies on remote)
        val remoteEntitiesByPath = LinkedHashMap<String, MutableList<DriveItemEntity>>()
        val remoteFilesMap = LinkedHashMap<String, DriveItemEntity>()
        remoteFilesList.forEach { (relPath, entity) ->
            remoteEntitiesByPath.getOrPut(relPath) { mutableListOf() }.add(entity)
            val existing = remoteFilesMap[relPath]
            if (existing == null || entity.createdAt >= existing.createdAt) {
                remoteFilesMap[relPath] = entity
            }
        }

        val previousRecords = stateMutex.withLock {
            loadSyncStateStore().recordsByPair[currentPair.id]?.toMutableMap() ?: mutableMapOf()
        }
        val newRecords = ConcurrentHashMap<String, SyncedFileRecord>(previousRecords)

        // Actions to perform
        val toUpload = mutableListOf<Pair<String, File>>()
        val toDownload = mutableListOf<Pair<String, DriveItemEntity>>()
        val toDeleteLocal = mutableListOf<Pair<String, File>>()
        val toDeleteRemote = mutableListOf<Pair<String, List<DriveItemEntity>>>()

        when (currentPair.syncMethod) {
            SyncMethod.UPLOAD_ONLY -> {
                for ((relPath, localFile) in localFilesMap) {
                    val remoteEntity = remoteFilesMap[relPath]
                    val prev = previousRecords[relPath]
                    val isAlreadySynced = (remoteEntity != null && remoteEntity.size == localFile.length()) ||
                        (prev != null && prev.localSize == localFile.length() &&
                            kotlin.math.abs(prev.localLastModified - localFile.lastModified()) <= 2000L &&
                            remoteEntity != null)
                    if (!isAlreadySynced) {
                        if (isFileSizeAllowed(localFile.length(), isUpload = true, settings = settings)) {
                            toUpload.add(relPath to localFile)
                        }
                    } else if (remoteEntity != null) {
                        newRecords[relPath] = SyncedFileRecord(
                            relativePath = relPath,
                            localSize = localFile.length(),
                            localLastModified = localFile.lastModified(),
                            originalTimestamp = remoteEntity.createdAt,
                            remoteMessageId = remoteEntity.id,
                            remoteChatId = remoteEntity.parentChatId,
                            remoteVirtualFolderId = remoteEntity.virtualParentId ?: baseVfId
                        )
                    }
                }
            }

            SyncMethod.UPLOAD_THEN_DELETE -> {
                for ((relPath, localFile) in localFilesMap) {
                    val remoteEntity = remoteFilesMap[relPath]
                    if (remoteEntity != null && remoteEntity.size == localFile.length()) {
                        toDeleteLocal.add(relPath to localFile)
                    } else if (isFileSizeAllowed(localFile.length(), isUpload = true, settings = settings)) {
                        toUpload.add(relPath to localFile)
                    }
                }
            }

            SyncMethod.UPLOAD_MIRROR -> {
                for ((relPath, localFile) in localFilesMap) {
                    val remoteEntity = remoteFilesMap[relPath]
                    if (remoteEntity == null || remoteEntity.size != localFile.length()) {
                        if (isFileSizeAllowed(localFile.length(), isUpload = true, settings = settings)) {
                            if (remoteEntity != null) {
                                toDeleteRemote.add(relPath to (remoteEntitiesByPath[relPath] ?: listOf(remoteEntity)))
                            }
                            toUpload.add(relPath to localFile)
                        }
                    } else {
                        newRecords[relPath] = SyncedFileRecord(
                            relativePath = relPath,
                            localSize = localFile.length(),
                            localLastModified = localFile.lastModified(),
                            originalTimestamp = remoteEntity.createdAt,
                            remoteMessageId = remoteEntity.id,
                            remoteChatId = remoteEntity.parentChatId,
                            remoteVirtualFolderId = remoteEntity.virtualParentId ?: baseVfId
                        )
                    }
                }
                for ((relPath, remoteEntity) in remoteFilesMap) {
                    if (!localFilesMap.containsKey(relPath)) {
                        toDeleteRemote.add(relPath to (remoteEntitiesByPath[relPath] ?: listOf(remoteEntity)))
                    }
                }
            }

            SyncMethod.DOWNLOAD_ONLY -> {
                for ((relPath, remoteEntity) in remoteFilesMap) {
                    val localFile = localFilesMap[relPath]
                    if (localFile == null || localFile.length() != remoteEntity.size) {
                        if (isFileSizeAllowed(remoteEntity.size, isUpload = false, settings = settings)) {
                            toDownload.add(relPath to remoteEntity)
                        }
                    } else {
                        newRecords[relPath] = SyncedFileRecord(
                            relativePath = relPath,
                            localSize = localFile.length(),
                            localLastModified = localFile.lastModified(),
                            originalTimestamp = remoteEntity.createdAt,
                            remoteMessageId = remoteEntity.id,
                            remoteChatId = remoteEntity.parentChatId,
                            remoteVirtualFolderId = remoteEntity.virtualParentId ?: baseVfId
                        )
                    }
                }
            }

            SyncMethod.DOWNLOAD_THEN_DELETE -> {
                for ((relPath, remoteEntity) in remoteFilesMap) {
                    val localFile = localFilesMap[relPath]
                    if (localFile != null && localFile.length() == remoteEntity.size) {
                        toDeleteRemote.add(relPath to (remoteEntitiesByPath[relPath] ?: listOf(remoteEntity)))
                    } else if (isFileSizeAllowed(remoteEntity.size, isUpload = false, settings = settings)) {
                        toDownload.add(relPath to remoteEntity)
                    }
                }
            }

            SyncMethod.DOWNLOAD_MIRROR -> {
                for ((relPath, remoteEntity) in remoteFilesMap) {
                    val localFile = localFilesMap[relPath]
                    if (localFile == null || localFile.length() != remoteEntity.size) {
                        if (isFileSizeAllowed(remoteEntity.size, isUpload = false, settings = settings)) {
                            toDownload.add(relPath to remoteEntity)
                        }
                    } else {
                        newRecords[relPath] = SyncedFileRecord(
                            relativePath = relPath,
                            localSize = localFile.length(),
                            localLastModified = localFile.lastModified(),
                            originalTimestamp = remoteEntity.createdAt,
                            remoteMessageId = remoteEntity.id,
                            remoteChatId = remoteEntity.parentChatId,
                            remoteVirtualFolderId = remoteEntity.virtualParentId ?: baseVfId
                        )
                    }
                }
                for ((relPath, localFile) in localFilesMap) {
                    if (!remoteFilesMap.containsKey(relPath)) {
                        toDeleteLocal.add(relPath to localFile)
                    }
                }
            }

            SyncMethod.TWO_WAY -> {
                val allRelPaths = (localFilesMap.keys + remoteFilesMap.keys + previousRecords.keys).distinct()
                for (relPath in allRelPaths) {
                    val localFile = localFilesMap[relPath]
                    val remoteEntity = remoteFilesMap[relPath]
                    val prev = previousRecords[relPath]
                    val expectedLocalPath = File(localRoot, relPath).absolutePath

                    when {
                        localFile != null && remoteEntity == null -> {
                            if (prev != null) {
                                // Was previously synced, now deleted on remote -> delete locally
                                toDeleteLocal.add(relPath to localFile)
                            } else if (isFileSizeAllowed(localFile.length(), isUpload = true, settings = settings)) {
                                // New local file -> upload to cloud
                                toUpload.add(relPath to localFile)
                            }
                        }
                        localFile == null && remoteEntity != null -> {
                            val wasUploadedFromThisLocalPath = !remoteEntity.localPath.isNullOrBlank() &&
                                File(remoteEntity.localPath).absolutePath == expectedLocalPath
                            if (prev != null || wasUploadedFromThisLocalPath) {
                                // Was previously synced (or uploaded from this exact local path), now deleted locally -> delete on remote
                                toDeleteRemote.add(relPath to (remoteEntitiesByPath[relPath] ?: listOf(remoteEntity)))
                            } else if (isFileSizeAllowed(remoteEntity.size, isUpload = false, settings = settings)) {
                                // New cloud file -> download to device
                                toDownload.add(relPath to remoteEntity)
                            }
                        }
                        localFile != null && remoteEntity != null -> {
                            if (localFile.length() != remoteEntity.size) {
                                val localOrigTime = FileUtils.extractOriginalTimestampMillis(localFile)
                                if (localOrigTime >= remoteEntity.createdAt) {
                                    if (isFileSizeAllowed(localFile.length(), isUpload = true, settings = settings)) {
                                        toDeleteRemote.add(relPath to (remoteEntitiesByPath[relPath] ?: listOf(remoteEntity)))
                                        toUpload.add(relPath to localFile)
                                    }
                                } else {
                                    if (isFileSizeAllowed(remoteEntity.size, isUpload = false, settings = settings)) {
                                        toDownload.add(relPath to remoteEntity)
                                    }
                                }
                            } else {
                                newRecords[relPath] = SyncedFileRecord(
                                    relativePath = relPath,
                                    localSize = localFile.length(),
                                    localLastModified = localFile.lastModified(),
                                    originalTimestamp = remoteEntity.createdAt,
                                    remoteMessageId = remoteEntity.id,
                                    remoteChatId = remoteEntity.parentChatId,
                                    remoteVirtualFolderId = remoteEntity.virtualParentId ?: baseVfId
                                )
                            }
                        }
                        else -> {
                            newRecords.remove(relPath)
                        }
                    }
                }
            }
        }

        // Persist matched baseline records immediately so even if sync is interrupted later, existing matched files are remembered
        savePairRecords(currentPair.id, newRecords, forceFlush = true)

        val totalOperations = toUpload.size + toDownload.size + toDeleteLocal.size + toDeleteRemote.size
        val completedOps = AtomicInteger(0)
        val failedCount = AtomicInteger(0)

        if (totalOperations == 0) {
            val totalFilesCount = maxOf(localFilesMap.size, remoteFilesMap.size)
            val timeStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date())
            updatePairStatus(currentPair.id, "Up to date ($timeStr)", totalFilesCount)
            return true
        }

        // Cache of ensured remote virtual folders for relative directory paths
        val remoteSubfolderCache = ConcurrentHashMap<String, String>()
        remoteSubfolderCache[""] = baseVfId
        val subfolderMutex = Mutex()

        suspend fun resolveRemoteVfForRelativePath(relFilePath: String): String {
            val parentRelDir = relFilePath.substringBeforeLast('/', "").trim('/')
            if (parentRelDir.isEmpty()) return baseVfId
            remoteSubfolderCache[parentRelDir]?.let { return it }
            return subfolderMutex.withLock {
                remoteSubfolderCache[parentRelDir]?.let { return@withLock it }
                val createdVfId = driveRepository.ensureVirtualFolderPathSuspend(
                    baseChatId = baseChatId,
                    baseVirtualFolderId = baseVfId,
                    relativeFolderPath = parentRelDir
                )
                remoteSubfolderCache[parentRelDir] = createdVfId
                createdVfId
            }
        }

        fun ensurePairStillActive(): Boolean {
            val latest = _folderPairs.value.firstOrNull { it.id == currentPair.id } ?: return false
            if (!isManual && !latest.enabled) return false
            if (latest.remotePathDisplay.trim() != currentPair.remotePathDisplay.trim()) return false
            return true
        }

        // 1. Execute Remote Deletions first (for Mirror / Two-way)
        for ((relPath, remoteEntities) in toDeleteRemote) {
            if (!ensurePairStillActive()) throw CancellationException("Folder pair modified or disabled")
            val step = completedOps.incrementAndGet()
            val primaryName = remoteEntities.firstOrNull()?.name ?: relPath.substringAfterLast('/')
            _activeStatus.value = SyncActiveStatus(
                isRunning = true,
                activePairId = currentPair.id,
                activePairName = currentPair.effectiveTitle,
                currentFileName = primaryName,
                currentAction = "Removing from cloud ($step/$totalOperations)",
                processedCount = step,
                totalCount = totalOperations,
                progress = step.toFloat() / totalOperations.toFloat()
            )
            var deletedAny = false
            for (entity in remoteEntities) {
                try {
                    driveRepository.deleteRemoteFileForSyncSuspend(entity.id, entity.parentChatId)
                    deletedAny = true
                } catch (_: Exception) {}
            }
            if (deletedAny) {
                newRecords.remove(relPath)
                savePairRecords(currentPair.id, newRecords)
            } else {
                failedCount.incrementAndGet()
            }
        }

        // 2. Execute Local Deletions
        for ((relPath, localFile) in toDeleteLocal) {
            if (!ensurePairStillActive()) throw CancellationException("Folder pair modified or disabled")
            val step = completedOps.incrementAndGet()
            _activeStatus.value = SyncActiveStatus(
                isRunning = true,
                activePairId = currentPair.id,
                activePairName = currentPair.effectiveTitle,
                currentFileName = localFile.name,
                currentAction = "Removing from device ($step/$totalOperations)",
                processedCount = step,
                totalCount = totalOperations,
                progress = step.toFloat() / totalOperations.toFloat()
            )
            val absPath = localFile.absolutePath
            activelyModifiedLocalPaths.add(absPath)
            ignoreObserverEventsUntil = System.currentTimeMillis() + 4_000L
            try {
                if (!localFile.exists() || localFile.delete()) {
                    FileUtils.restoreOriginalTimestampAndScan(context, localFile, 0L)
                    newRecords.remove(relPath)
                    savePairRecords(currentPair.id, newRecords)
                } else {
                    failedCount.incrementAndGet()
                }
            } catch (e: Exception) {
                failedCount.incrementAndGet()
            } finally {
                activelyModifiedLocalPaths.remove(absPath)
            }
        }

        // 3. Execute Uploads (bounded parallelism with live file progress & incremental record saving)
        val uploadPermits = if (settings.allowParallelUploads) 2 else 1
        val uploadSemaphore = Semaphore(uploadPermits)
        val startedUploads = AtomicInteger(0)
        coroutineScope {
            toUpload.map { (relPath, localFile) ->
                async {
                    uploadSemaphore.withPermit {
                        if (!ensurePairStillActive()) throw CancellationException("Folder pair modified or disabled")
                        val currentIndex = ( completedOps.get() + startedUploads.incrementAndGet() ).coerceAtMost(totalOperations)
                        _activeStatus.value = SyncActiveStatus(
                            isRunning = true,
                            activePairId = currentPair.id,
                            activePairName = currentPair.effectiveTitle,
                            currentFileName = localFile.name,
                            currentAction = "Uploading ($currentIndex/$totalOperations)",
                            processedCount = completedOps.get(),
                            totalCount = totalOperations,
                            progress = (completedOps.get().toFloat() / totalOperations.toFloat()).coerceIn(0f, 1f)
                        )
                        val targetVfId = resolveRemoteVfForRelativePath(relPath)
                        val origTs = FileUtils.extractOriginalTimestampMillis(localFile)
                        var lastProgressEmitMs = 0L
                        val msgId = driveRepository.uploadFileForSyncSuspend(
                            localFile = localFile,
                            targetChatId = baseChatId,
                            targetVirtualFolderId = targetVfId,
                            originalTimestampMillis = origTs,
                            onProgress = { fileFraction ->
                                val nowMs = System.currentTimeMillis()
                                if (nowMs - lastProgressEmitMs >= 200L || fileFraction >= 0.99f) {
                                    lastProgressEmitMs = nowMs
                                    val doneBefore = completedOps.get().toFloat()
                                    val combinedProgress = ((doneBefore + fileFraction) / totalOperations.toFloat()).coerceIn(0f, 0.99f)
                                    val pct = (fileFraction * 100f).toInt().coerceIn(0, 100)
                                    _activeStatus.value = SyncActiveStatus(
                                        isRunning = true,
                                        activePairId = currentPair.id,
                                        activePairName = currentPair.effectiveTitle,
                                        currentFileName = "${localFile.name} ($pct%)",
                                        currentAction = "Uploading ($currentIndex/$totalOperations)",
                                        processedCount = currentIndex,
                                        totalCount = totalOperations,
                                        progress = combinedProgress
                                    )
                                }
                            }
                        )
                        val finishedStep = completedOps.incrementAndGet()
                        startedUploads.decrementAndGet()
                        if (msgId != null) {
                            newRecords[relPath] = SyncedFileRecord(
                                relativePath = relPath,
                                localSize = localFile.length(),
                                localLastModified = localFile.lastModified(),
                                originalTimestamp = origTs,
                                remoteMessageId = msgId,
                                remoteChatId = baseChatId,
                                remoteVirtualFolderId = targetVfId
                            )
                            if (currentPair.syncMethod == SyncMethod.UPLOAD_THEN_DELETE) {
                                val absPath = localFile.absolutePath
                                activelyModifiedLocalPaths.add(absPath)
                                ignoreObserverEventsUntil = System.currentTimeMillis() + 4_000L
                                try {
                                    if (localFile.delete()) {
                                        FileUtils.restoreOriginalTimestampAndScan(context, localFile, 0L)
                                    }
                                } catch (_: Exception) {
                                } finally {
                                    activelyModifiedLocalPaths.remove(absPath)
                                }
                            }
                            savePairRecords(currentPair.id, newRecords)
                        } else {
                            failedCount.incrementAndGet()
                        }
                        _activeStatus.value = SyncActiveStatus(
                            isRunning = true,
                            activePairId = currentPair.id,
                            activePairName = currentPair.effectiveTitle,
                            currentFileName = localFile.name,
                            currentAction = "Uploaded ($finishedStep/$totalOperations)",
                            processedCount = finishedStep,
                            totalCount = totalOperations,
                            progress = (finishedStep.toFloat() / totalOperations.toFloat()).coerceIn(0f, 1f)
                        )
                    }
                }
            }.awaitAll()
        }

        // 4. Execute Downloads (bounded parallelism with live file progress & observer guard)
        val downloadPermits = if (settings.allowParallelDownloads) 2 else 1
        val downloadSemaphore = Semaphore(downloadPermits)
        val startedDownloads = AtomicInteger(0)
        coroutineScope {
            toDownload.map { (relPath, remoteEntity) ->
                async {
                    downloadSemaphore.withPermit {
                        if (!ensurePairStillActive()) throw CancellationException("Folder pair modified or disabled")
                        val currentIndex = (completedOps.get() + startedDownloads.incrementAndGet()).coerceAtMost(totalOperations)
                        _activeStatus.value = SyncActiveStatus(
                            isRunning = true,
                            activePairId = currentPair.id,
                            activePairName = currentPair.effectiveTitle,
                            currentFileName = remoteEntity.name,
                            currentAction = "Downloading ($currentIndex/$totalOperations)",
                            processedCount = completedOps.get(),
                            totalCount = totalOperations,
                            progress = (completedOps.get().toFloat() / totalOperations.toFloat()).coerceIn(0f, 1f)
                        )
                        val destFile = File(localRoot, relPath)
                        val absPath = destFile.absolutePath
                        activelyModifiedLocalPaths.add(absPath)
                        ignoreObserverEventsUntil = System.currentTimeMillis() + 5_000L
                        var lastDlProgressEmitMs = 0L
                        val ok = try {
                            driveRepository.downloadFileForSyncSuspend(
                                fileEntity = remoteEntity,
                                destLocalFile = destFile,
                                onProgress = { fileFraction ->
                                    val nowMs = System.currentTimeMillis()
                                    if (nowMs - lastDlProgressEmitMs >= 200L || fileFraction >= 0.99f) {
                                        lastDlProgressEmitMs = nowMs
                                        val doneBefore = completedOps.get().toFloat()
                                        val combinedProgress = ((doneBefore + fileFraction) / totalOperations.toFloat()).coerceIn(0f, 0.99f)
                                        val pct = (fileFraction * 100f).toInt().coerceIn(0, 100)
                                        _activeStatus.value = SyncActiveStatus(
                                            isRunning = true,
                                            activePairId = currentPair.id,
                                            activePairName = currentPair.effectiveTitle,
                                            currentFileName = "${remoteEntity.name} ($pct%)",
                                            currentAction = "Downloading ($currentIndex/$totalOperations)",
                                            processedCount = currentIndex,
                                            totalCount = totalOperations,
                                            progress = combinedProgress
                                        )
                                    }
                                }
                            )
                        } finally {
                            ignoreObserverEventsUntil = System.currentTimeMillis() + 4_000L
                            activelyModifiedLocalPaths.remove(absPath)
                        }
                        val finishedStep = completedOps.incrementAndGet()
                        startedDownloads.decrementAndGet()
                        if (ok) {
                            newRecords[relPath] = SyncedFileRecord(
                                relativePath = relPath,
                                localSize = destFile.length(),
                                localLastModified = destFile.lastModified(),
                                originalTimestamp = remoteEntity.createdAt,
                                remoteMessageId = remoteEntity.id,
                                remoteChatId = remoteEntity.parentChatId,
                                remoteVirtualFolderId = remoteEntity.virtualParentId ?: baseVfId
                            )
                            if (currentPair.syncMethod == SyncMethod.DOWNLOAD_THEN_DELETE) {
                                val allCopies = remoteEntitiesByPath[relPath] ?: listOf(remoteEntity)
                                for (copy in allCopies) {
                                    try {
                                        driveRepository.deleteRemoteFileForSyncSuspend(copy.id, copy.parentChatId)
                                    } catch (_: Exception) {}
                                }
                            }
                            savePairRecords(currentPair.id, newRecords)
                        } else {
                            failedCount.incrementAndGet()
                        }
                        _activeStatus.value = SyncActiveStatus(
                            isRunning = true,
                            activePairId = currentPair.id,
                            activePairName = currentPair.effectiveTitle,
                            currentFileName = remoteEntity.name,
                            currentAction = "Downloaded ($finishedStep/$totalOperations)",
                            processedCount = finishedStep,
                            totalCount = totalOperations,
                            progress = (finishedStep.toFloat() / totalOperations.toFloat()).coerceIn(0f, 1f)
                        )
                    }
                }
            }.awaitAll()
        }

        if (currentPair.deleteEmptySubfolders) {
            ignoreObserverEventsUntil = System.currentTimeMillis() + 4_000L
            cleanupEmptyLocalSubfolders(localRoot)
        }

        savePairRecords(currentPair.id, newRecords, forceFlush = true)
        if (toUpload.isNotEmpty() || toDeleteRemote.isNotEmpty() || currentPair.syncMethod == SyncMethod.DOWNLOAD_THEN_DELETE) {
            runCatching { driveRepository.flushSyncManifest() }
        }

        val timeStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date())
        val finalStatus = if (failedCount.get() == 0) {
            val delCount = toDeleteLocal.size + toDeleteRemote.size
            val delSuffix = if (delCount > 0) " ✕$delCount" else ""
            "Synced ($timeStr) • ↑${toUpload.size} ↓${toDownload.size}$delSuffix"
        } else {
            "Partial sync ($timeStr) • ${failedCount.get()} failed"
        }
        updatePairStatus(currentPair.id, finalStatus, newRecords.size)
        return failedCount.get() == 0
    }

    private suspend fun savePairRecords(pairId: String, records: Map<String, SyncedFileRecord>, forceFlush: Boolean = false) {
        stateMutex.withLock {
            val store = loadSyncStateStore()
            val updated = store.recordsByPair.toMutableMap()
            updated[pairId] = records.toMap()
            saveSyncStateStore(store.copy(recordsByPair = updated), forceFlush = forceFlush)
        }
    }

    private fun updatePairStatus(pairId: String, statusText: String, syncedCount: Int) {
        val now = System.currentTimeMillis()
        val updated = _folderPairs.value.map {
            if (it.id == pairId) {
                it.copy(
                    lastSyncTime = now,
                    lastSyncStatus = statusText,
                    totalSyncedFiles = syncedCount
                )
            } else it
        }
        _folderPairs.value = updated
        saveFolderPairsInternal(updated)
    }

    fun isAllFilesAccessGranted(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }
}
