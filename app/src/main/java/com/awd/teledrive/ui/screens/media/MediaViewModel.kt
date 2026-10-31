package com.awd.teledrive.ui.screens.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.awd.teledrive.data.repository.DriveRepository
import com.awd.teledrive.data.secure.SecureSessionManager
import com.awd.teledrive.domain.model.DriveItem
import com.awd.teledrive.domain.model.MoveCopyDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MediaViewModel @Inject constructor(
    private val driveRepository: DriveRepository,
    private val secureSessionManager: SecureSessionManager
) : ViewModel() {

    private val isSecureModeActive = secureSessionManager.decryptedPassword.map { it != null }

    val mediaItems: StateFlow<List<DriveItem.File>> = combine(
        driveRepository.getAllFiles(),
        isSecureModeActive
    ) { items: List<DriveItem.File>, secureActive: Boolean ->
        items.filter {
            (it.mimeType.startsWith("image/") || it.mimeType.startsWith("video/")) &&
            (secureActive || !it.isEncrypted)
        }.sortedByDescending { it.createdAt }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<DriveItem.Folder>> = driveRepository.getAllAvailableFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun downloadFile(file: DriveItem.File) {
        viewModelScope.launch {
            driveRepository.downloadFile(file.id, file.parentChatId, file.name)
        }
    }

    fun deleteItems(files: List<DriveItem.File>) {
        viewModelScope.launch {
            driveRepository.moveToTrash(files)
        }
    }

    fun moveItems(fileIds: List<Long>, targetChatId: Long) {
        val destination = if (targetChatId == 0L) driveRepository.getSavedMessagesChatId() else targetChatId
        viewModelScope.launch {
            val files = mediaItems.value.filter { it.id in fileIds }
            files.groupBy { it.parentChatId }.forEach { (fromChatId, items) ->
                if (fromChatId != destination) {
                    driveRepository.moveItems(fromChatId, destination, items.map { it.id })
                }
            }
        }
    }

    fun moveOrCopyItems(
        files: List<DriveItem.File>,
        destination: MoveCopyDestination,
        isCopy: Boolean,
        onComplete: (Int) -> Unit = {}
    ) {
        driveRepository.moveOrCopyItems(files, destination, isCopy, onComplete)
    }

    fun createFolderForPicker(
        name: String,
        isVirtual: Boolean,
        parentVirtualId: String = "0",
        onCreated: (MoveCopyDestination?) -> Unit
    ) {
        driveRepository.createFolderForPicker(name, isVirtual, parentVirtualId, onCreated)
    }

    fun toggleStarred(file: DriveItem.File) {
        viewModelScope.launch {
            driveRepository.toggleStarred(file)
        }
    }
}
