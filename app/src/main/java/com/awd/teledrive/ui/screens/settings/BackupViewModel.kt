package com.awd.teledrive.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.awd.teledrive.data.repository.AutosyncManager
import com.awd.teledrive.data.repository.DriveRepository
import com.awd.teledrive.data.repository.SettingsRepository
import com.awd.teledrive.data.worker.TeleDriveWorkerManager
import com.awd.teledrive.domain.model.AutosyncGlobalSettings
import com.awd.teledrive.domain.model.SyncActiveStatus
import com.awd.teledrive.domain.model.SyncFolderPair
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

sealed class BackupFeedback {
    object NoFolders : BackupFeedback()
    object Starting : BackupFeedback()
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val autosyncManager: AutosyncManager,
    private val driveRepository: DriveRepository,
    private val settingsRepository: SettingsRepository,
    private val workerManager: TeleDriveWorkerManager
) : ViewModel() {

    val folderPairs: StateFlow<List<SyncFolderPair>> = autosyncManager.folderPairs
    val globalSettings: StateFlow<AutosyncGlobalSettings> = autosyncManager.globalSettings
    val activeStatus: StateFlow<SyncActiveStatus> = autosyncManager.activeStatus
    val lastBackupTime: StateFlow<Long> = autosyncManager.lastSyncTime

    val isAutoBackupEnabled: StateFlow<Boolean> = autosyncManager.globalSettings
        .map { it.enableAutosync }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), autosyncManager.globalSettings.value.enableAutosync)

    val isBackupWifiOnly: StateFlow<Boolean> = autosyncManager.globalSettings
        .map { it.wifiOnly }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), autosyncManager.globalSettings.value.wifiOnly)

    val backupFolders: StateFlow<List<String>> = autosyncManager.folderPairs
        .map { pairs -> pairs.filter { it.enabled }.map { it.localPath } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _remoteFolderOptions = MutableStateFlow<List<DriveRepository.RemoteFolderOption>>(emptyList())
    val remoteFolderOptions: StateFlow<List<DriveRepository.RemoteFolderOption>> = _remoteFolderOptions.asStateFlow()

    private val _isLoadingRemoteFolders = MutableStateFlow(false)
    val isLoadingRemoteFolders: StateFlow<Boolean> = _isLoadingRemoteFolders.asStateFlow()

    private val _feedback = MutableSharedFlow<BackupFeedback>()
    val feedback: SharedFlow<BackupFeedback> = _feedback.asSharedFlow()

    val backupWorkInfo = workerManager.getBackupWorkInfo()

    init {
        autosyncManager.reloadFromStorage()
        refreshRemoteFolders()
    }

    fun refreshRemoteFolders() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingRemoteFolders.value = true
            try {
                _remoteFolderOptions.value = driveRepository.getAllSelectableRemoteFoldersSuspend()
            } catch (_: Exception) {
            } finally {
                _isLoadingRemoteFolders.value = false
            }
        }
    }

    fun createNewRemoteFolder(
        folderName: String,
        parentOption: DriveRepository.RemoteFolderOption?,
        onCreated: (DriveRepository.RemoteFolderOption) -> Unit
    ) {
        val cleanName = folderName.trim().trim('/')
        if (cleanName.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingRemoteFolders.value = true
            try {
                val rootChat = driveRepository.ensureReadyChatIdSuspend()
                val baseChatId = parentOption?.chatId?.takeIf { it != 0L } ?: rootChat
                val baseVfId = parentOption?.virtualFolderId?.ifBlank { "0" } ?: "0"
                val newVfId = driveRepository.ensureVirtualFolderPathSuspend(
                    baseChatId = baseChatId,
                    baseVirtualFolderId = baseVfId,
                    relativeFolderPath = cleanName
                )
                val allUpdated = driveRepository.getAllSelectableRemoteFoldersSuspend()
                _remoteFolderOptions.value = allUpdated
                val expectedFullPath = if (parentOption == null || parentOption.fullPath == "/") {
                    "/$cleanName"
                } else {
                    "${parentOption.fullPath.trimEnd('/')}/$cleanName"
                }
                val createdOption = allUpdated.firstOrNull {
                    it.virtualFolderId == newVfId || it.fullPath.equals(expectedFullPath, ignoreCase = true)
                } ?: DriveRepository.RemoteFolderOption(
                    displayName = cleanName,
                    fullPath = expectedFullPath,
                    chatId = baseChatId,
                    virtualFolderId = newVfId,
                    parentVirtualId = baseVfId,
                    folderKey = "vf_$newVfId",
                    parentFolderKey = parentOption?.folderKey ?: "ROOT"
                )
                withContext(Dispatchers.Main) {
                    onCreated(createdOption)
                }
            } finally {
                _isLoadingRemoteFolders.value = false
            }
        }
    }

    fun createNewLocalFolder(parentDir: File, folderName: String): File? {
        val clean = folderName.trim().trim('/', '\\')
        if (clean.isEmpty()) return null
        return try {
            val newDir = File(parentDir, clean)
            if (newDir.exists() || newDir.mkdirs()) newDir else null
        } catch (_: Exception) {
            null
        }
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        settingsRepository.setAutoBackupEnabled(enabled)
        autosyncManager.updateGlobalSettings { it.copy(enableAutosync = enabled) }
    }

    fun setBackupWifiOnly(enabled: Boolean) {
        settingsRepository.setBackupWifiOnlyEnabled(enabled)
        autosyncManager.updateGlobalSettings { it.copy(wifiOnly = enabled) }
    }

    fun updateGlobalSettings(transform: (AutosyncGlobalSettings) -> AutosyncGlobalSettings) {
        autosyncManager.updateGlobalSettings(transform)
        val current = autosyncManager.globalSettings.value
        settingsRepository.setAutoBackupEnabled(current.enableAutosync)
        settingsRepository.setBackupWifiOnlyEnabled(current.wifiOnly)
    }

    fun saveFolderPair(pair: SyncFolderPair) {
        autosyncManager.saveFolderPair(pair)
    }

    fun deleteFolderPair(pairId: String) {
        autosyncManager.deleteFolderPair(pairId)
    }

    fun toggleFolderPairEnabled(pairId: String, enabled: Boolean) {
        autosyncManager.toggleFolderPairEnabled(pairId, enabled)
    }

    fun resetFolderPairSyncState(pairId: String) {
        autosyncManager.resetFolderPairSyncState(pairId)
    }

    fun syncSinglePairNow(pairId: String) {
        viewModelScope.launch {
            _feedback.emit(BackupFeedback.Starting)
        }
        autosyncManager.triggerSyncSinglePairNow(pairId)
    }

    fun triggerManualBackup() {
        if (autosyncManager.folderPairs.value.isEmpty()) {
            viewModelScope.launch {
                _feedback.emit(BackupFeedback.NoFolders)
            }
            return
        }
        viewModelScope.launch {
            _feedback.emit(BackupFeedback.Starting)
        }
        autosyncManager.triggerSyncAllNow()
    }

    fun cancelActiveSync() {
        autosyncManager.cancelActiveSync()
    }

    fun isCurrentlyOnMobileData(): Boolean {
        return autosyncManager.isCurrentlyOnMobileData()
    }

    fun isAllFilesAccessGranted(): Boolean {
        return autosyncManager.isAllFilesAccessGranted()
    }
}
