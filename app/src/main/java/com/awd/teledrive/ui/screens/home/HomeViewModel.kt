package com.awd.teledrive.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.awd.teledrive.core.ConnectivityObserver
import com.awd.teledrive.data.model.TransferInfo
import com.awd.teledrive.data.repository.AuthRepository
import com.awd.teledrive.data.repository.DriveRepository
import com.awd.teledrive.data.repository.ShareRepository
import com.awd.teledrive.data.repository.TelegramUserProfile
import com.awd.teledrive.data.repository.TransferRepository
import com.awd.teledrive.data.secure.MasterPasswordService
import com.awd.teledrive.data.secure.SecureSessionManager
import com.awd.teledrive.domain.model.DriveItem
import com.awd.teledrive.domain.model.MoveCopyDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

enum class SortOrder {
    NAME, DATE, SIZE
}

enum class FilterType {
    ALL, PHOTOS, VIDEOS, AUDIO, DOCUMENTS, APKS
}

enum class HomeSection {
    HOME, RECENT, OFFLINE, TRASH
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val driveRepository: DriveRepository,
    private val shareRepository: ShareRepository,
    private val transferRepository: TransferRepository,
    private val connectivityObserver: ConnectivityObserver,
    private val masterPasswordService: MasterPasswordService,
    private val secureSessionManager: SecureSessionManager
) : ViewModel() {

    val userProfile: StateFlow<TelegramUserProfile?> = authRepository.userProfile

    val connectivityStatus = connectivityObserver.status
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectivityObserver.Status.Available)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.DATE)
    val sortOrder = _sortOrder.asStateFlow()

    private val _sortAscending = MutableStateFlow(false)
    val sortAscending = _sortAscending.asStateFlow()

    private val _filterType = MutableStateFlow(FilterType.ALL)
    val filterType = _filterType.asStateFlow()

    private val _currentSection = MutableStateFlow(HomeSection.HOME)
    val currentSection = _currentSection.asStateFlow()

    private val _currentFolderId = MutableStateFlow<Long?>(null)
    val currentFolderId = _currentFolderId.asStateFlow()

    private val _currentVirtualFolderId = MutableStateFlow("0")
    val currentVirtualFolderId = _currentVirtualFolderId.asStateFlow()

    private val _currentFolderName = MutableStateFlow<String?>(null)
    val currentFolderName = _currentFolderName.asStateFlow()

    private val _isGridView = MutableStateFlow(value = false)
    val isGridView = _isGridView.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private val _isInitialLoading = MutableStateFlow(true)
    val isInitialLoading = _isInitialLoading.asStateFlow()

    private val _isSyncingMetadata = MutableStateFlow(false)
    val isSyncingMetadata = _isSyncingMetadata.asStateFlow()

    val totalStorageUsed: StateFlow<Long> = driveRepository.getTotalStorageUsed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val allFolders: StateFlow<List<DriveItem.Folder>> = driveRepository.getAllAvailableFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeTransfers: StateFlow<List<TransferInfo>> = transferRepository.transfers
        .map { map ->
            map.values.filter {
                it.status == TransferRepository.Status.DOWNLOADING ||
                    it.status == TransferRepository.Status.UPLOADING ||
                    it.status == TransferRepository.Status.QUEUED
            }
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val suggestedFiles: StateFlow<List<DriveItem.File>> = driveRepository.getRecentFilesFlow()
        .map { files ->
            files.sortedWith(
                compareByDescending<DriveItem.File> { it.isStarred }
                    .thenByDescending { it.createdAt }
            ).take(8)
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val items: StateFlow<List<DriveItem>> = combine<Any?, kotlinx.coroutines.flow.Flow<List<DriveItem>>>(
        listOf<kotlinx.coroutines.flow.Flow<Any?>>(
            _currentSection,
            _currentFolderId,
            _currentVirtualFolderId,
            _searchQuery,
            _sortOrder,
            _sortAscending,
            _filterType,
            driveRepository.getSavedMessagesChatIdFlow()
        )
    ) { arr ->
        val section = arr[0] as HomeSection
        val folderId = arr[1] as? Long
        val virtualId = arr[2] as String
        val query = arr[3] as String
        val order = arr[4] as SortOrder
        val ascending = arr[5] as Boolean
        val filter = arr[6] as FilterType

        val baseFlow = when (section) {
            HomeSection.HOME -> driveRepository.getItems(folderId, virtualId, query)
            HomeSection.RECENT -> driveRepository.getRecentFilesFlow().map { list ->
                if (query.isBlank()) list else list.filter { it.name.contains(query, ignoreCase = true) }
            }
            HomeSection.OFFLINE -> driveRepository.getOfflineItemsFlow().map { list ->
                if (query.isBlank()) list else list.filter { it.name.contains(query, ignoreCase = true) }
            }
            HomeSection.TRASH -> driveRepository.getTrashedItemsFlow().map { list ->
                if (query.isBlank()) list else list.filter { it.name.contains(query, ignoreCase = true) }
            }
        }

        baseFlow.map { allItems ->
            val items = allItems.filter { item ->
                !(item is DriveItem.Folder && item.isSecure)
            }

            val filteredByType = when (filter) {
                FilterType.ALL -> items
                FilterType.PHOTOS -> items.filter { it is DriveItem.File && it.mimeType.startsWith("image/") }
                FilterType.VIDEOS -> items.filter { it is DriveItem.File && it.mimeType.startsWith("video/") }
                FilterType.AUDIO -> items.filter { it is DriveItem.File && it.mimeType.startsWith("audio/") }
                FilterType.DOCUMENTS -> items.filter {
                    it is DriveItem.File &&
                        !it.mimeType.startsWith("image/") &&
                        !it.mimeType.startsWith("video/") &&
                        !it.mimeType.startsWith("audio/") &&
                        !it.isApkFile()
                }
                FilterType.APKS -> items.filter { it is DriveItem.File && it.isApkFile() }
            }

            val (folders, files) = filteredByType.partition { it is DriveItem.Folder }

            val sortedFolders = when (order) {
                SortOrder.NAME -> if (ascending) folders.sortedBy { it.name.lowercase() } else folders.sortedByDescending { it.name.lowercase() }
                SortOrder.SIZE -> if (ascending) {
                    folders.sortedBy { (it as? DriveItem.Folder)?.totalSize ?: 0L }
                } else {
                    folders.sortedByDescending { (it as? DriveItem.Folder)?.totalSize ?: 0L }
                }
                SortOrder.DATE -> {
                    if (section == HomeSection.TRASH) {
                        if (ascending) folders.sortedBy { it.trashedAt ?: it.createdAt }
                        else folders.sortedByDescending { it.trashedAt ?: it.createdAt }
                    } else {
                        if (ascending) folders.sortedBy { it.createdAt.takeIf { ts -> ts > 0L } ?: it.id }
                        else folders.sortedByDescending { it.createdAt.takeIf { ts -> ts > 0L } ?: it.id }
                    }
                }
            }

            val sortedFiles = when (order) {
                SortOrder.NAME -> if (ascending) files.sortedBy { it.name.lowercase() } else files.sortedByDescending { it.name.lowercase() }
                SortOrder.DATE -> if (section == HomeSection.TRASH) {
                    if (ascending) files.sortedBy { it.trashedAt ?: it.createdAt }
                    else files.sortedByDescending { it.trashedAt ?: it.createdAt }
                } else {
                    if (ascending) files.sortedBy { it.createdAt }
                    else files.sortedByDescending { it.createdAt }
                }
                SortOrder.SIZE -> if (ascending) {
                    files.sortedBy { (it as? DriveItem.File)?.size ?: 0L }
                } else {
                    files.sortedByDescending { (it as? DriveItem.File)?.size ?: 0L }
                }
            }

            sortedFolders + sortedFiles
        }
    }.flatMapLatest { it }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        authRepository.fetchUserProfile()
        fetchItems()
        var lastObservedSlot = authRepository.activeSlotId.value
        viewModelScope.launch {
            authRepository.activeSlotId.collect { slot ->
                if (slot != lastObservedSlot) {
                    lastObservedSlot = slot
                    _currentSection.value = HomeSection.HOME
                    _currentFolderId.value = null
                    _currentVirtualFolderId.value = "0"
                    _currentFolderName.value = null
                    _searchQuery.value = ""
                    _filterType.value = FilterType.ALL
                    secureSessionManager.clearSession()
                    fetchItems()
                }
            }
        }
    }

    val savedAccounts: StateFlow<List<TelegramUserProfile>> = authRepository.savedAccounts
    val activeSlotId: StateFlow<String> = authRepository.activeSlotId

    fun switchAccount(slotId: String) {
        authRepository.switchToAccount(slotId)
    }

    fun startAddNewAccount() {
        authRepository.startAddNewAccount()
    }

    fun setSection(section: HomeSection) {
        _currentSection.value = section
        if (section != HomeSection.HOME) {
            _currentFolderId.value = null
            _currentVirtualFolderId.value = "0"
            _currentFolderName.value = null
        }
    }

    fun setSortOrder(order: SortOrder) {
        if (_sortOrder.value == order) {
            _sortAscending.value = !_sortAscending.value
        } else {
            _sortOrder.value = order
            _sortAscending.value = (order == SortOrder.NAME)
        }
    }

    fun toggleSortDirection() {
        _sortAscending.value = !_sortAscending.value
    }

    fun setFilterType(type: FilterType) {
        _filterType.value = type
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun toggleViewMode() {
        _isGridView.value = !_isGridView.value
    }

    fun cancelTransfer(remoteUniqueId: String) {
        transferRepository.cancelTransfer(remoteUniqueId)
    }

    fun createManualCloudBackup() {
        driveRepository.createManualCloudBackup()
    }

    fun navigateToFolder(folderId: Long?, virtualId: String?, folderName: String?) {
        _currentSection.value = HomeSection.HOME
        val previousFolderId = _currentFolderId.value
        _currentFolderId.value = folderId
        _currentVirtualFolderId.value = virtualId ?: "0"
        _currentFolderName.value = folderName
        if (folderId != previousFolderId) {
            fetchItems()
        }
    }

    fun navigateBack() {
        if (_currentSection.value != HomeSection.HOME) {
            _currentSection.value = HomeSection.HOME
            return
        }
        if (_currentVirtualFolderId.value != "0") {
            val parentId = driveRepository.getParentVirtualId(_currentVirtualFolderId.value)
            val parentName = if (parentId != "0") {
                driveRepository.getVirtualFolderName(parentId)
            } else if (_currentFolderId.value != null) {
                allFolders.value.find { !it.isVirtual && it.telegramChatId == _currentFolderId.value }?.name
            } else {
                null
            }
            navigateToFolder(_currentFolderId.value, parentId, parentName)
        } else if (_currentFolderId.value != null) {
            navigateToFolder(null, "0", null)
        }
    }

    fun createFolder(name: String, isVirtual: Boolean = true, isSecure: Boolean = false) {
        viewModelScope.launch {
            if (isSecure) {
                driveRepository.createSecureFolder(name)
            } else if (isVirtual) {
                val parentId = if (_currentVirtualFolderId.value != "0") {
                    _currentVirtualFolderId.value
                } else if (_currentFolderId.value != null) {
                    _currentFolderId.value.toString()
                } else {
                    "0"
                }

                val isParentSecure = if (parentId.startsWith("vf_")) {
                    driveRepository.isVirtualFolderSecure(parentId)
                } else {
                    driveRepository.isChatSecure(parentId.toLongOrNull() ?: 0L)
                }

                driveRepository.createVirtualFolder(name, parentId, isSecure = isParentSecure)
            } else {
                if (_currentFolderId.value == null && _currentVirtualFolderId.value == "0") {
                    driveRepository.createFolder(name)
                } else {
                    val parentId = if (_currentVirtualFolderId.value != "0") {
                        _currentVirtualFolderId.value
                    } else {
                        _currentFolderId.value?.toString() ?: "0"
                    }
                    driveRepository.createVirtualFolder(name, parentId, isSecure = false)
                }
            }
        }
    }

    fun isPasswordSet() = masterPasswordService.isPasswordSet()

    fun setAndUnlockMasterPassword(password: String) {
        if (password.isNotBlank()) {
            if (!masterPasswordService.isPasswordSet()) {
                masterPasswordService.setPassword(password)
            }
            secureSessionManager.setSessionPassword(password)
        }
    }

    fun fetchItems() {
        viewModelScope.launch {
            if (items.value.isEmpty()) _isInitialLoading.value = true
            else _isRefreshing.value = true
            _isSyncingMetadata.value = true

            driveRepository.fetchFiles(_currentFolderId.value)

            if (_currentFolderId.value == null) {
                kotlinx.coroutines.withTimeoutOrNull(30000) {
                    driveRepository.getSavedMessagesChatIdFlow().collect { id ->
                        if (id != 0L) {
                            kotlinx.coroutines.delay(3000)
                            _isSyncingMetadata.value = false
                            _isInitialLoading.value = false
                            _isRefreshing.value = false
                            throw kotlinx.coroutines.CancellationException()
                        }
                    }
                }
            } else {
                kotlinx.coroutines.delay(1000)
            }
            _isSyncingMetadata.value = false
            _isInitialLoading.value = false
            _isRefreshing.value = false
        }
    }

    fun uploadFile(
        filePath: String,
        fileName: String,
        encryptFile: Boolean = false,
        sendAsDirectMedia: Boolean = false
    ) {
        viewModelScope.launch {
            driveRepository.uploadFile(
                filePath = filePath,
                originalFileName = fileName,
                chatId = _currentFolderId.value,
                virtualFolderId = _currentVirtualFolderId.value,
                encryptFile = encryptFile,
                sendAsDirectMedia = sendAsDirectMedia
            )
        }
    }

    fun uploadFileToDestination(
        filePath: String,
        fileName: String,
        destination: MoveCopyDestination,
        encryptFile: Boolean = false,
        sendAsDirectMedia: Boolean = false
    ) {
        viewModelScope.launch {
            val targetChatId = when {
                destination.isRoot -> null
                destination.isVirtual -> if (destination.targetChatId != 0L) destination.targetChatId else null
                else -> if (destination.targetChatId != 0L) destination.targetChatId else null
            }
            val targetVirtualId = when {
                destination.isRoot -> "0"
                destination.isVirtual -> destination.targetVirtualId.ifBlank { "0" }
                else -> "0"
            }
            driveRepository.uploadFile(
                filePath = filePath,
                originalFileName = fileName,
                chatId = targetChatId,
                virtualFolderId = targetVirtualId,
                encryptFile = encryptFile,
                sendAsDirectMedia = sendAsDirectMedia
            )
        }
    }

    fun downloadFile(messageId: Long, chatId: Long, fileName: String) {
        viewModelScope.launch {
            driveRepository.downloadFile(messageId, chatId, fileName)
        }
    }

    fun downloadFolderContents(folder: DriveItem.Folder) {
        viewModelScope.launch {
            driveRepository.downloadFolderContents(folder)
        }
    }

    fun deleteItems(itemsToDelete: List<DriveItem>) {
        viewModelScope.launch {
            driveRepository.moveToTrash(itemsToDelete)
        }
    }

    fun permanentlyDeleteItems(itemsToDelete: List<DriveItem>) {
        viewModelScope.launch {
            driveRepository.permanentlyDeleteItems(itemsToDelete)
        }
    }

    fun restoreItemsFromTrash(itemsToRestore: List<DriveItem>) {
        viewModelScope.launch {
            driveRepository.restoreFromTrash(itemsToRestore)
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            driveRepository.emptyTrash()
        }
    }

    fun setItemsOfflineAvailable(items: List<DriveItem>, makeOffline: Boolean) {
        viewModelScope.launch {
            driveRepository.setItemsOfflineAvailable(items, makeOffline)
        }
    }

    fun moveItems(ids: Set<Long>, targetChatId: Long) {
        val fromChatId = _currentFolderId.value ?: driveRepository.getSavedMessagesChatId()
        val destination = if (targetChatId == 0L) driveRepository.getSavedMessagesChatId() else targetChatId

        if (fromChatId != 0L && fromChatId != destination) {
            viewModelScope.launch {
                driveRepository.moveItems(fromChatId, destination, ids.toList())
            }
        }
    }

    fun moveOrCopyItems(
        itemsToProcess: List<DriveItem>,
        destination: MoveCopyDestination,
        isCopy: Boolean,
        onComplete: (Int) -> Unit = {}
    ) {
        driveRepository.moveOrCopyItems(itemsToProcess, destination, isCopy, onComplete)
    }

    fun createFolderForPicker(
        name: String,
        isVirtual: Boolean,
        parentVirtualId: String = "0",
        onCreated: (MoveCopyDestination?) -> Unit
    ) {
        driveRepository.createFolderForPicker(name, isVirtual, parentVirtualId, onCreated)
    }

    fun moveFolderContentsAndDelete(folderChatId: Long, targetChatId: Long) {
        val destination = if (targetChatId == 0L) driveRepository.getSavedMessagesChatId() else targetChatId
        viewModelScope.launch {
            driveRepository.moveFolderContentsAndDelete(folderChatId, destination)
        }
    }

    fun toggleStarred(item: DriveItem) {
        viewModelScope.launch {
            driveRepository.toggleStarred(item)
        }
    }

    fun renameItem(item: DriveItem, newName: String) {
        viewModelScope.launch {
            driveRepository.renameItem(item, newName)
        }
    }

    fun setFolderColor(folder: DriveItem.Folder, colorHex: String?) {
        driveRepository.setFolderColor(folder, colorHex)
    }

    fun getFolderInviteLink(chatId: Long, onResult: (String?) -> Unit) {
        shareRepository.getFolderInviteLink(chatId, onResult)
    }

    fun getFolderMembers(chatId: Long, onResult: (List<TdApi.User>) -> Unit) {
        shareRepository.getChatMembers(chatId, onResult)
    }

    fun shareFileToPhone(phoneNumber: String, messageId: Long, onResult: (Boolean, String?) -> Unit) {
        val chatId = _currentFolderId.value ?: driveRepository.getSavedMessagesChatId()
        shareRepository.shareFileToPhone(phoneNumber, messageId, chatId, onResult)
    }
}
