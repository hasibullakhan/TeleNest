package com.awd.teledrive.ui.screens.home

import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.OfflinePin
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.awd.teledrive.R
import com.awd.teledrive.core.ConnectivityObserver
import com.awd.teledrive.core.utils.FileUtils
import com.awd.teledrive.data.model.TransferInfo
import com.awd.teledrive.data.repository.TelegramUserProfile
import com.awd.teledrive.domain.model.DriveItem
import com.awd.teledrive.domain.model.MoveCopyDestination
import com.awd.teledrive.ui.screens.auth.TeleNestWelcomeSplashScreen
import com.awd.teledrive.ui.theme.TeledriveTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onNavigateToTransfers: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToProfile: () -> Unit = {},
    onNavigateToCloudAnalysis: () -> Unit = {},
    onNavigateToPreview: (DriveItem.File) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsState()
    val allFolders by viewModel.allFolders.collectAsState()
    val suggestedFiles by viewModel.suggestedFiles.collectAsState()
    val activeTransfers by viewModel.activeTransfers.collectAsState()
    val totalStorageUsed by viewModel.totalStorageUsed.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val sortAscending by viewModel.sortAscending.collectAsState()
    val filterType by viewModel.filterType.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isInitialLoading by viewModel.isInitialLoading.collectAsState()
    val currentFolderId by viewModel.currentFolderId.collectAsState()
    val currentFolderName by viewModel.currentFolderName.collectAsState()
    val currentVirtualFolderId by viewModel.currentVirtualFolderId.collectAsState()
    val currentSection by viewModel.currentSection.collectAsState()
    val isGridView by viewModel.isGridView.collectAsState()
    val connectivityStatus by viewModel.connectivityStatus.collectAsState()
    val pendingShortcut by com.awd.teledrive.MainActivity.pendingShortcutFolder.collectAsState()
    val pendingSharedUris by com.awd.teledrive.MainActivity.pendingSharedUris.collectAsState()
    val pendingSharedFiles by com.awd.teledrive.MainActivity.pendingSharedFiles.collectAsState()
    val isStagingSharedFiles by com.awd.teledrive.MainActivity.isStagingSharedFiles.collectAsState()

    LaunchedEffect(pendingShortcut) {
        pendingShortcut?.let { target ->
            viewModel.navigateToFolder(target.chatId, target.virtualId, target.folderName)
            com.awd.teledrive.MainActivity.clearPendingShortcutFolder()
        }
    }

    HomeContent(
        items = items,
        allFolders = allFolders,
        suggestedFiles = suggestedFiles,
        activeTransfers = activeTransfers,
        totalStorageUsed = totalStorageUsed,
        userProfile = userProfile,
        searchQuery = searchQuery,
        sortOrder = sortOrder,
        sortAscending = sortAscending,
        filterType = filterType,
        isRefreshing = isRefreshing,
        isInitialLoading = isInitialLoading,
        currentFolderId = currentFolderId,
        currentVirtualFolderId = currentVirtualFolderId,
        currentFolderName = currentFolderName,
        currentSection = currentSection,
        isGridView = isGridView,
        connectivityStatus = connectivityStatus,
        pendingSharedUris = pendingSharedUris,
        onClearPendingSharedUris = com.awd.teledrive.MainActivity::clearPendingSharedUris,
        pendingSharedFiles = pendingSharedFiles,
        onClearPendingSharedFiles = com.awd.teledrive.MainActivity::clearPendingSharedFiles,
        isStagingSharedFiles = isStagingSharedFiles,
        onCancelStagingSharedFiles = com.awd.teledrive.MainActivity::cancelStagingSharedFiles,
        onSelectSection = viewModel::setSection,
        onNavigateToTransfers = onNavigateToTransfers,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToProfile = onNavigateToProfile,
        onNavigateToCloudAnalysis = onNavigateToCloudAnalysis,
        onNavigateToPreview = onNavigateToPreview,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onToggleViewMode = viewModel::toggleViewMode,
        onSetSortOrder = viewModel::setSortOrder,
        onToggleSortDirection = viewModel::toggleSortDirection,
        onSetFilterType = viewModel::setFilterType,
        onCreateFolder = viewModel::createFolder,
        onCreateFolderForPicker = viewModel::createFolderForPicker,
        isPasswordSet = viewModel.isPasswordSet(),
        onSetMasterPassword = viewModel::setAndUnlockMasterPassword,
        onUploadFile = viewModel::uploadFile,
        onUploadFileToDestination = viewModel::uploadFileToDestination,
        onDeleteItems = viewModel::deleteItems,
        onPermanentlyDeleteItems = viewModel::permanentlyDeleteItems,
        onRestoreItems = viewModel::restoreItemsFromTrash,
        onEmptyTrash = viewModel::emptyTrash,
        onSetItemsOffline = viewModel::setItemsOfflineAvailable,
        onMoveItems = viewModel::moveItems,
        onMoveOrCopyItems = viewModel::moveOrCopyItems,
        onMoveFolderContents = viewModel::moveFolderContentsAndDelete,
        onDownloadFile = viewModel::downloadFile,
        onDownloadFolderContents = viewModel::downloadFolderContents,
        onToggleStarred = viewModel::toggleStarred,
        onNavigateToFolder = { chatId, vId, name -> viewModel.navigateToFolder(chatId, vId, name) },
        onNavigateBack = viewModel::navigateBack,
        onRefresh = viewModel::fetchItems,
        onRenameItem = viewModel::renameItem,
        onSetFolderColor = viewModel::setFolderColor,
        onCancelTransfer = viewModel::cancelTransfer,
        onCreateCloudBackup = viewModel::createManualCloudBackup,
        onGetInviteLink = viewModel::getFolderInviteLink,
        onGetMembers = viewModel::getFolderMembers,
        onShareFile = viewModel::shareFileToPhone,
        onLogout = {}
    )
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HomeContent(
    items: List<DriveItem>,
    allFolders: List<DriveItem.Folder> = emptyList(),
    suggestedFiles: List<DriveItem.File> = emptyList(),
    activeTransfers: List<TransferInfo> = emptyList(),
    totalStorageUsed: Long,
    userProfile: TelegramUserProfile? = null,
    searchQuery: String,
    sortOrder: SortOrder,
    sortAscending: Boolean = false,
    filterType: FilterType,
    isRefreshing: Boolean,
    isInitialLoading: Boolean,
    currentFolderId: Long?,
    currentVirtualFolderId: String,
    currentFolderName: String?,
    currentSection: HomeSection = HomeSection.HOME,
    isGridView: Boolean,
    connectivityStatus: ConnectivityObserver.Status,
    pendingSharedUris: List<android.net.Uri> = emptyList(),
    onClearPendingSharedUris: () -> Unit = {},
    pendingSharedFiles: List<Pair<File, String>> = emptyList(),
    onClearPendingSharedFiles: () -> Unit = {},
    isStagingSharedFiles: Boolean = false,
    onCancelStagingSharedFiles: () -> Unit = {},
    onSelectSection: (HomeSection) -> Unit = {},
    onNavigateToTransfers: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToProfile: () -> Unit = {},
    onNavigateToCloudAnalysis: () -> Unit = {},
    onNavigateToPreview: (DriveItem.File) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleViewMode: () -> Unit,
    onSetSortOrder: (SortOrder) -> Unit,
    onToggleSortDirection: () -> Unit = {},
    onSetFilterType: (FilterType) -> Unit,
    onCreateFolder: (String, Boolean, Boolean) -> Unit,
    onCreateFolderForPicker: (String, Boolean, String, (MoveCopyDestination?) -> Unit) -> Unit = { _, _, _, cb -> cb(null) },
    isPasswordSet: Boolean,
    onSetMasterPassword: (String) -> Unit = {},
    onUploadFile: (String, String, Boolean, Boolean) -> Unit,
    onUploadFileToDestination: (String, String, MoveCopyDestination, Boolean, Boolean) -> Unit = { path, name, _, enc, dm -> onUploadFile(path, name, enc, dm) },
    onDeleteItems: (List<DriveItem>) -> Unit,
    onPermanentlyDeleteItems: (List<DriveItem>) -> Unit = {},
    onRestoreItems: (List<DriveItem>) -> Unit = {},
    onEmptyTrash: () -> Unit = {},
    onSetItemsOffline: (List<DriveItem>, Boolean) -> Unit = { _, _ -> },
    onMoveItems: (Set<Long>, Long) -> Unit,
    onMoveOrCopyItems: (List<DriveItem>, MoveCopyDestination, Boolean, (Int) -> Unit) -> Unit = { _, _, _, cb -> cb(0) },
    onMoveFolderContents: (Long, Long) -> Unit,
    onDownloadFile: (Long, Long, String) -> Unit,
    onDownloadFolderContents: (DriveItem.Folder) -> Unit,
    onToggleStarred: (DriveItem) -> Unit,
    onNavigateToFolder: (Long?, String?, String?) -> Unit,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    onRenameItem: (DriveItem, String) -> Unit,
    onSetFolderColor: (DriveItem.Folder, String?) -> Unit = { _, _ -> },
    onCancelTransfer: (String) -> Unit = {},
    onCreateCloudBackup: () -> Unit = {},
    onGetInviteLink: (Long, (String?) -> Unit) -> Unit,
    onGetMembers: (Long, (List<TdApi.User>) -> Unit) -> Unit,
    onShareFile: (String, Long, (Boolean, String?) -> Unit) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val sheetState = rememberModalBottomSheetState()
    var showNewSheet by remember { mutableStateOf(false) }
    var activeMenuDriveItem by remember { mutableStateOf<DriveItem?>(null) }
    var infoDialogItem by remember { mutableStateOf<DriveItem?>(null) }
    var isSuggestedExpanded by remember { mutableStateOf(true) }
    var nextPickerDefaultEncrypt by remember { mutableStateOf(false) }

    val isOffline = connectivityStatus == ConnectivityObserver.Status.Unavailable || 
                    connectivityStatus == ConnectivityObserver.Status.Lost

    var selectedItems by remember { mutableStateOf(setOf<Long>()) }
    val isSelectionMode = selectedItems.isNotEmpty()
    var showFolderDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var isCopyOperation by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<List<DriveItem>?>(null) }
    var showPermanentDeleteConfirm by remember { mutableStateOf<List<DriveItem>?>(null) }
    var showEmptyTrashConfirm by remember { mutableStateOf(false) }
    var itemToRename by remember { mutableStateOf<DriveItem?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var folderToChangeColor by remember { mutableStateOf<DriveItem.Folder?>(null) }

    if (folderToChangeColor != null) {
        FolderColorPickerDialog(
            folder = folderToChangeColor!!,
            onDismiss = { folderToChangeColor = null },
            onSelectColor = { colorHex ->
                onSetFolderColor(folderToChangeColor!!, colorHex)
                folderToChangeColor = null
            }
        )
    }

    if (itemToRename != null) {
        AlertDialog(
            onDismissRequest = { itemToRename = null },
            title = { Text(stringResource(R.string.rename)) },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    label = { Text(stringResource(R.string.folder_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    itemToRename?.let { onRenameItem(it, renameValue) }
                    itemToRename = null
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { itemToRename = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
    var shareItem by remember { mutableStateOf<DriveItem?>(null) }
    var folderToMove by remember { mutableStateOf<DriveItem.Folder?>(null) }
    var folderToDownload by remember { mutableStateOf<DriveItem.Folder?>(null) }
    var largeFileToUpload by remember { mutableStateOf<Pair<File, String>?>(null) }
    var pendingUploadFiles by remember { mutableStateOf<List<Pair<File, String>>>(emptyList()) }
    var uploadTargetDestination by remember { mutableStateOf<MoveCopyDestination?>(null) }
    var showUploadDestinationPicker by remember { mutableStateOf(false) }
    var encryptBeforeUpload by remember { mutableStateOf(false) }
    var sendAsDirectMedia by remember { mutableStateOf(false) }
    var inlineMasterPassword by remember { mutableStateOf("") }
    var hasMasterPasswordSet by remember(isPasswordSet) { mutableStateOf(isPasswordSet) }
    var isPreparingFile by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var isVirtualFolder by remember { mutableStateOf(true) }
    var isSecureFolder by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }

    // Handle files shared from external apps (Gallery, File Manager, Google Photos, etc.)
    LaunchedEffect(pendingSharedFiles) {
        if (pendingSharedFiles.isNotEmpty()) {
            val readyFiles = pendingSharedFiles.toList()
            onClearPendingSharedFiles()
            encryptBeforeUpload = false
            sendAsDirectMedia = false
            inlineMasterPassword = ""
            uploadTargetDestination = MoveCopyDestination(
                label = currentFolderName ?: context.getString(R.string.my_drive),
                isRoot = currentFolderId == null && currentVirtualFolderId == "0",
                isVirtual = currentVirtualFolderId != "0",
                targetChatId = currentFolderId ?: 0L,
                targetVirtualId = currentVirtualFolderId
            )
            pendingUploadFiles = readyFiles
            // Open the folder selector immediately so user can pick where to upload shared files
            showUploadDestinationPicker = true
        }
    }

    LaunchedEffect(pendingSharedUris) {
        if (pendingSharedUris.isNotEmpty()) {
            val urisToProcess = pendingSharedUris.toList()
            onClearPendingSharedUris()
            isPreparingFile = true
            try {
                val preparedFiles = withContext(Dispatchers.IO) {
                    urisToProcess.mapNotNull { uri ->
                        try {
                            val fileName = FileUtils.resolveUriFileName(context, uri)
                            val inputStream = context.contentResolver.openInputStream(uri)
                            val stageDir = File(context.cacheDir, "stage_${System.nanoTime()}").apply { mkdirs() }
                            val tempFile = File(stageDir, fileName)
                            inputStream?.use { input ->
                                FileOutputStream(tempFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            if (tempFile.exists() && tempFile.length() > 0L) tempFile to fileName else null
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
                if (preparedFiles.isNotEmpty()) {
                    encryptBeforeUpload = false
                    sendAsDirectMedia = false
                    inlineMasterPassword = ""
                    uploadTargetDestination = MoveCopyDestination(
                        label = currentFolderName ?: context.getString(R.string.my_drive),
                        isRoot = currentFolderId == null && currentVirtualFolderId == "0",
                        isVirtual = currentVirtualFolderId != "0",
                        targetChatId = currentFolderId ?: 0L,
                        targetVirtualId = currentVirtualFolderId
                    )
                    pendingUploadFiles = preparedFiles
                    showUploadDestinationPicker = true
                } else {
                    Toast.makeText(context, "Unable to read shared file(s)", Toast.LENGTH_SHORT).show()
                }
            } finally {
                isPreparingFile = false
            }
        }
    }

    val visualMediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        
        scope.launch {
            isPreparingFile = true
            try {
                val preparedFiles = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri ->
                        try {
                            val fileName = FileUtils.resolveUriFileName(context, uri)
                            val inputStream = context.contentResolver.openInputStream(uri)
                            val stageDir = File(context.cacheDir, "stage_${System.nanoTime()}").apply { mkdirs() }
                            val tempFile = File(stageDir, fileName)
                            inputStream?.use { input ->
                                FileOutputStream(tempFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            if (tempFile.exists() && tempFile.length() > 0L) tempFile to fileName else null
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
                if (preparedFiles.isNotEmpty()) {
                    encryptBeforeUpload = nextPickerDefaultEncrypt
                    nextPickerDefaultEncrypt = false
                    sendAsDirectMedia = false
                    inlineMasterPassword = ""
                    uploadTargetDestination = null
                    pendingUploadFiles = preparedFiles
                }
            } finally {
                isPreparingFile = false
            }
        }
    }

    val multiFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        
        scope.launch {
            isPreparingFile = true
            try {
                val preparedFiles = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri ->
                        try {
                            val fileName = FileUtils.resolveUriFileName(context, uri)
                            val inputStream = context.contentResolver.openInputStream(uri)
                            val stageDir = File(context.cacheDir, "stage_${System.nanoTime()}").apply { mkdirs() }
                            val tempFile = File(stageDir, fileName)
                            inputStream?.use { input ->
                                FileOutputStream(tempFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            if (tempFile.exists() && tempFile.length() > 0L) tempFile to fileName else null
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
                if (preparedFiles.isNotEmpty()) {
                    encryptBeforeUpload = nextPickerDefaultEncrypt
                    nextPickerDefaultEncrypt = false
                    sendAsDirectMedia = false
                    inlineMasterPassword = ""
                    uploadTargetDestination = null
                    pendingUploadFiles = preparedFiles
                }
            } finally {
                isPreparingFile = false
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            scope.launch {
                isPreparingFile = true
                try {
                    val preparedPhoto = withContext(Dispatchers.IO) {
                        val fileName = "IMG_${System.currentTimeMillis()}.jpg"
                        val stageDir = File(context.cacheDir, "stage_${System.nanoTime()}").apply { mkdirs() }
                        val file = File(stageDir, fileName)
                        FileOutputStream(file).use { out ->
                            it.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, out)
                        }
                        file to fileName
                    }
                    encryptBeforeUpload = false
                    sendAsDirectMedia = false
                    inlineMasterPassword = ""
                    uploadTargetDestination = null
                    pendingUploadFiles = listOf(preparedPhoto)
                } finally {
                    isPreparingFile = false
                }
            }
        }
    }

    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    var isFabVisible by remember { mutableStateOf(true) }

    // Folder scroll preservation map (key: "slotId_folderId_virtualId_section")
    val folderScrollPositions = remember { mutableMapOf<String, Pair<Int, Int>>() }
    val currentFolderKey = remember(userProfile?.slotId, currentFolderId, currentVirtualFolderId, currentSection) {
        "${userProfile?.slotId ?: "default"}_${currentFolderId ?: "root"}_${currentVirtualFolderId}_${currentSection.name}"
    }

    // Continuously preserve scroll state of current active folder
    LaunchedEffect(listState, currentFolderKey) {
        snapshotFlow { Pair(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
            .collect { pos ->
                if (items.isNotEmpty()) {
                    folderScrollPositions["list_$currentFolderKey"] = pos
                }
            }
    }
    LaunchedEffect(gridState, currentFolderKey) {
        snapshotFlow { Pair(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset) }
            .collect { pos ->
                if (items.isNotEmpty()) {
                    folderScrollPositions["grid_$currentFolderKey"] = pos
                }
            }
    }

    // State-preserving folder navigation helpers
    val navigateToFolderWithState: (Long?, String?, String?) -> Unit = remember(currentFolderKey, onNavigateToFolder) {
        { targetFolderId, targetVirtualId, targetName ->
            folderScrollPositions["list_$currentFolderKey"] = Pair(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            folderScrollPositions["grid_$currentFolderKey"] = Pair(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset)
            onNavigateToFolder(targetFolderId, targetVirtualId, targetName)
        }
    }

    val navigateBackWithState: () -> Unit = remember(currentFolderKey, onNavigateBack) {
        {
            folderScrollPositions["list_$currentFolderKey"] = Pair(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            folderScrollPositions["grid_$currentFolderKey"] = Pair(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset)
            onNavigateBack()
        }
    }

    // Restore scroll state when entering or returning to a folder
    var lastRestoredKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(currentFolderKey) {
        lastRestoredKey = null
        isFabVisible = true
    }

    LaunchedEffect(currentFolderKey, items.size, isGridView) {
        if (lastRestoredKey != currentFolderKey) {
            val savedPos = if (isGridView) folderScrollPositions["grid_$currentFolderKey"] else folderScrollPositions["list_$currentFolderKey"]
            if (savedPos != null) {
                val maxIdx = (items.size - 1).coerceAtLeast(0)
                val targetIdx = savedPos.first.coerceAtMost(maxIdx)
                if (isGridView) {
                    gridState.scrollToItem(targetIdx, savedPos.second)
                } else {
                    listState.scrollToItem(targetIdx, savedPos.second)
                }
                if (items.isNotEmpty()) {
                    lastRestoredKey = currentFolderKey
                }
            } else {
                if (isGridView) {
                    gridState.scrollToItem(0, 0)
                } else {
                    listState.scrollToItem(0, 0)
                }
                lastRestoredKey = currentFolderKey
            }
        }
    }

    val pressBackMsg = stringResource(R.string.press_back_again)
    var backPressedOnce by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            activeMenuDriveItem != null -> activeMenuDriveItem = null
            showNewSheet -> showNewSheet = false
            isSearchActive -> isSearchActive = false
            isSelectionMode -> selectedItems = emptySet()
            currentSection != HomeSection.HOME -> onSelectSection(HomeSection.HOME)
            (currentFolderId != null || currentVirtualFolderId != "0") -> navigateBackWithState()
            else -> {
                if (backPressedOnce) {
                    (context as? android.app.Activity)?.finish()
                } else {
                    backPressedOnce = true
                    Toast.makeText(context, pressBackMsg, Toast.LENGTH_SHORT).show()
                    scope.launch {
                        delay(2.seconds)
                        backPressedOnce = false
                    }
                }
            }
        }
    }

    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Move to Trash?") },
            text = {
                Text(
                    "Selected item(s) will be moved to Trash. You can restore them anytime within 30 days before they are permanently deleted from Telegram."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm?.let {
                            onDeleteItems(it)
                            Toast.makeText(context, "Moved to Trash (kept for 30 days)", Toast.LENGTH_SHORT).show()
                        }
                        showDeleteConfirm = null
                        selectedItems = emptySet()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Move to Trash")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showPermanentDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showPermanentDeleteConfirm = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete Forever?") },
            text = {
                Text(
                    "Selected item(s) will be permanently deleted from Telegram and TeleNest immediately. This action cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermanentDeleteConfirm?.let {
                            onPermanentlyDeleteItems(it)
                            Toast.makeText(context, "Permanently deleted", Toast.LENGTH_SHORT).show()
                        }
                        showPermanentDeleteConfirm = null
                        selectedItems = emptySet()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Forever")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermanentDeleteConfirm = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showEmptyTrashConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashConfirm = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Empty Trash?") },
            text = {
                Text("All items in Trash will be permanently deleted from Telegram and cannot be recovered.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onEmptyTrash()
                        showEmptyTrashConfirm = false
                        selectedItems = emptySet()
                        Toast.makeText(context, "Trash emptied", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Empty Trash")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyTrashConfirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (shareItem != null) {
        ShareDialog(
            item = shareItem!!,
            onDismiss = { shareItem = null },
            onGetInviteLink = onGetInviteLink,
            onGetMembers = onGetMembers,
            onShareFile = onShareFile
        )
    }

    if (folderToMove != null) {
        AlertDialog(
            onDismissRequest = { folderToMove = null },
            title = { Text(stringResource(R.string.folder_move_warning_title)) },
            text = { Text(stringResource(R.string.folder_move_warning_message, folderToMove?.name ?: "")) },
            confirmButton = {
                Button(onClick = {
                    folderToMove?.let { folder ->
                        onMoveFolderContents(folder.telegramChatId, currentFolderId ?: 0L)
                    }
                    folderToMove = null
                }) { Text(stringResource(R.string.move_contents)) }
            },
            dismissButton = {
                TextButton(onClick = { folderToMove = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (folderToDownload != null) {
        AlertDialog(
            onDismissRequest = { folderToDownload = null },
            title = { Text(stringResource(R.string.folder_download_warning_title)) },
            text = { Text(stringResource(R.string.folder_download_warning_message, folderToDownload?.name ?: "")) },
            confirmButton = {
                Button(onClick = {
                    folderToDownload?.let { folder ->
                        onDownloadFolderContents(folder)
                    }
                    folderToDownload = null
                }) { Text(stringResource(R.string.download_contents)) }
            },
            dismissButton = {
                TextButton(onClick = { folderToDownload = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showUploadDestinationPicker && pendingUploadFiles.isNotEmpty()) {
        val availableFolders = if (allFolders.isNotEmpty()) allFolders else items.filterIsInstance<DriveItem.Folder>()
        MoveCopyFolderDialog(
            isCopy = true,
            isUploadDestinationPicker = true,
            folders = availableFolders,
            currentFolderId = null,
            currentVirtualFolderId = "0",
            itemCount = pendingUploadFiles.size,
            onDismiss = { showUploadDestinationPicker = false },
            onCreateNewFolder = { folderName, isVirtual, parentVfId, onResult ->
                onCreateFolderForPicker(folderName, isVirtual, parentVfId, onResult)
            },
            onSelectDestination = { destination ->
                uploadTargetDestination = destination
                showUploadDestinationPicker = false
            }
        )
    }

    if (pendingUploadFiles.isNotEmpty() && !showUploadDestinationPicker) {
        val totalBytes = remember(pendingUploadFiles) { pendingUploadFiles.sumOf { it.first.length() } }
        val needsNewPassword = encryptBeforeUpload && !hasMasterPasswordSet
        val activeDestLabel = uploadTargetDestination?.label
            ?: currentFolderName
            ?: stringResource(R.string.my_drive)

        AlertDialog(
            onDismissRequest = {
                pendingUploadFiles.forEach { (file, _) -> try { file.delete() } catch (_: Exception) {} }
                pendingUploadFiles = emptyList()
                uploadTargetDestination = null
            },
            icon = {
                Icon(
                    imageVector = if (encryptBeforeUpload) Icons.Default.Lock else Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "Upload ${pendingUploadFiles.size} File${if (pendingUploadFiles.size > 1) "s" else ""}",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // File Summary Card
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            pendingUploadFiles.take(3).forEach { (file, name) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = formatSize(file.length()),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (pendingUploadFiles.size > 3) {
                                Text(
                                    text = "+${pendingUploadFiles.size - 3} more files...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total Size", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                Text(formatSize(totalBytes), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Destination Folder Selector Row
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showUploadDestinationPicker = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Upload Destination",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = activeDestLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "Change",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = "Select Upload Mode",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Option 1: Original Lossless Upload Card
                    val isOriginalSelected = !encryptBeforeUpload
                    Surface(
                        color = if (isOriginalSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            width = if (isOriginalSelected) 2.dp else 1.dp,
                            color = if (isOriginalSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { encryptBeforeUpload = false }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            RadioButton(
                                selected = isOriginalSelected,
                                onClick = { encryptBeforeUpload = false }
                            )
                            Spacer(Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDone,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Original Lossless Upload",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "100% original quality, exact file size & metadata preserved. Viewable in TeleNest & Telegram.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Option 2: Encrypted Upload (E2EE) Card
                    val isEncryptedSelected = encryptBeforeUpload
                    Surface(
                        color = if (isEncryptedSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            width = if (isEncryptedSelected) 2.dp else 1.dp,
                            color = if (isEncryptedSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { encryptBeforeUpload = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            RadioButton(
                                selected = isEncryptedSelected,
                                onClick = { encryptBeforeUpload = true }
                            )
                            Spacer(Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Encrypted Upload (E2EE)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "100% original quality preserved with zero quality or data loss. Locked with your AES-256 Master Password before upload & hidden from Telegram.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (needsNewPassword) {
                        OutlinedTextField(
                            value = inlineMasterPassword,
                            onValueChange = { inlineMasterPassword = it },
                            label = { Text("Set Master Password for Encryption") },
                            placeholder = { Text("Enter at least 4 characters") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !needsNewPassword || inlineMasterPassword.length >= 4,
                    onClick = {
                        if (needsNewPassword && inlineMasterPassword.isNotBlank()) {
                            onSetMasterPassword(inlineMasterPassword)
                            hasMasterPasswordSet = true
                        }
                        val filesToProcess = pendingUploadFiles
                        val shouldEncrypt = encryptBeforeUpload
                        val directMedia = sendAsDirectMedia
                        val customDest = uploadTargetDestination
                        pendingUploadFiles = emptyList()
                        uploadTargetDestination = null
                        filesToProcess.forEach { (file, name) ->
                            if (customDest != null) {
                                onUploadFileToDestination(file.absolutePath, name, customDest, shouldEncrypt, directMedia)
                            } else {
                                onUploadFile(file.absolutePath, name, shouldEncrypt, directMedia)
                            }
                        }
                        Toast.makeText(
                            context,
                            "Uploading ${filesToProcess.size} file(s) to $activeDestLabel",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.testTag("confirm_upload_button")
                ) {
                    Text(if (encryptBeforeUpload) "Encrypt & Upload" else "Upload Now")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingUploadFiles.forEach { (file, _) -> try { file.delete() } catch (_: Exception) {} }
                        pendingUploadFiles = emptyList()
                        uploadTargetDestination = null
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (largeFileToUpload != null) {
        AlertDialog(
            onDismissRequest = { largeFileToUpload = null },
            title = { Text(stringResource(R.string.large_file_title)) },
            text = { Text(stringResource(R.string.large_file_message, largeFileToUpload?.second ?: "")) },
            confirmButton = {
                Button(onClick = {
                    largeFileToUpload?.let { (file, name) ->
                        onUploadFile(file.absolutePath, name, encryptBeforeUpload, sendAsDirectMedia)
                    }
                    largeFileToUpload = null
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { largeFileToUpload = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (isPreparingFile || isStagingSharedFiles) {
        AlertDialog(
            onDismissRequest = {
                isPreparingFile = false
                onCancelStagingSharedFiles()
            },
            title = { Text(stringResource(R.string.preparing_file)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(stringResource(R.string.copying_to_temp))
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = {
                        isPreparingFile = false
                        onCancelStagingSharedFiles()
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (infoDialogItem != null) {
        InfoDialog(item = infoDialogItem!!, onDismiss = { infoDialogItem = null })
    }

    if (activeMenuDriveItem != null) {
        val menuItem = activeMenuDriveItem!!
        val isTrashSection = currentSection == HomeSection.TRASH
        GoogleDriveItemActionBottomSheet(
            item = menuItem,
            isTrashMode = isTrashSection,
            onDismiss = { activeMenuDriveItem = null },
            onShareClick = {
                activeMenuDriveItem = null
                shareItem = menuItem
            },
            onStarClick = {
                onToggleStarred(menuItem)
                activeMenuDriveItem = null
            },
            onInfoClick = {
                activeMenuDriveItem = null
                infoDialogItem = menuItem
            },
            onToggleOfflineClick = {
                val makeOffline = !menuItem.isOffline
                onSetItemsOffline(listOf(menuItem), makeOffline)
                Toast.makeText(
                    context,
                    if (makeOffline) "Making \"${menuItem.name}\" available offline"
                    else "Removed \"${menuItem.name}\" from offline",
                    Toast.LENGTH_SHORT
                ).show()
                activeMenuDriveItem = null
            },
            onRenameClick = {
                activeMenuDriveItem = null
                itemToRename = menuItem
                renameValue = menuItem.name
            },
            onDownloadClick = {
                activeMenuDriveItem = null
                if (menuItem is DriveItem.File) {
                    onDownloadFile(menuItem.id, menuItem.parentChatId, menuItem.name)
                } else if (menuItem is DriveItem.Folder) {
                    folderToDownload = menuItem
                }
            },
            onMoveClick = {
                activeMenuDriveItem = null
                selectedItems = setOf(menuItem.id)
                isCopyOperation = false
                showMoveDialog = true
            },
            onCopyClick = {
                activeMenuDriveItem = null
                selectedItems = setOf(menuItem.id)
                isCopyOperation = true
                showMoveDialog = true
            },
            onChangeColorClick = {
                if (menuItem is DriveItem.Folder) {
                    activeMenuDriveItem = null
                    folderToChangeColor = menuItem
                }
            },
            onAddToHomeScreenClick = {
                if (menuItem is DriveItem.Folder) {
                    activeMenuDriveItem = null
                    pinFolderToHomeScreen(context, menuItem, currentFolderId)
                }
            },
            onDeleteClick = {
                activeMenuDriveItem = null
                showDeleteConfirm = listOf(menuItem)
            },
            onRestoreClick = {
                activeMenuDriveItem = null
                onRestoreItems(listOf(menuItem))
                Toast.makeText(context, "Restored \"${menuItem.name}\"", Toast.LENGTH_SHORT).show()
            },
            onDeleteForeverClick = {
                activeMenuDriveItem = null
                showPermanentDeleteConfirm = listOf(menuItem)
            }
        )
    }

    if (showNewSheet) {
        ModalBottomSheet(
            onDismissRequest = { showNewSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp, start = 20.dp, end = 20.dp)
            ) {
                Text(
                    text = stringResource(R.string.create_new),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp, start = 4.dp)
                )
                val isAtRoot = currentFolderId == null && currentVirtualFolderId == "0"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    NewActionItem(Icons.Default.CreateNewFolder, stringResource(R.string.folder)) {
                        isVirtualFolder = !isAtRoot || isVirtualFolder
                        isSecureFolder = false
                        showNewSheet = false
                        showFolderDialog = true
                    }
                    NewActionItem(Icons.Default.FileUpload, stringResource(R.string.upload)) {
                        nextPickerDefaultEncrypt = false
                        showNewSheet = false
                        multiFilePickerLauncher.launch("*/*")
                    }
                    NewActionItem(Icons.Default.CameraAlt, "Scan / Camera") {
                        showNewSheet = false
                        cameraLauncher.launch(null)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    NewActionItem(Icons.Default.PermMedia, "Photos & Videos") {
                        nextPickerDefaultEncrypt = false
                        showNewSheet = false
                        try {
                            visualMediaPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        } catch (_: Exception) {
                            multiFilePickerLauncher.launch("image/*,video/*")
                        }
                    }
                    NewActionItem(Icons.Default.Lock, "Encrypted Upload") {
                        nextPickerDefaultEncrypt = true
                        showNewSheet = false
                        multiFilePickerLauncher.launch("*/*")
                    }
                    NewActionItem(Icons.Default.CloudDone, "Cloud Backup") {
                        showNewSheet = false
                        onCreateCloudBackup()
                        Toast.makeText(context, "Cloud metadata backup saved to Telegram", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    if (showFolderDialog) {
        AlertDialog(
            onDismissRequest = { showFolderDialog = false },
            title = { Text(stringResource(R.string.create_folder)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text(stringResource(R.string.folder_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    val isAtRoot = currentFolderId == null && currentVirtualFolderId == "0"
                    
                    if (isAtRoot) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material3.Switch(
                                    checked = isVirtualFolder,
                                    onCheckedChange = { isVirtualFolder = it }
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(if (isVirtualFolder) stringResource(R.string.virtual_folder) else stringResource(R.string.channel_folder), style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        if (isVirtualFolder) stringResource(R.string.sync_across_devices_desc) else stringResource(R.string.create_channel_telegram_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        // Reset to virtual if not at root
                        isVirtualFolder = true
                        isSecureFolder = false
                        Text(
                            stringResource(R.string.create_new) + " " + stringResource(R.string.virtual_folder) + " " + stringResource(R.string.in_label) + " " + (currentFolderName ?: stringResource(R.string.this_folder)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            onCreateFolder(newFolderName, isVirtualFolder, isSecureFolder)
                            newFolderName = ""
                            isSecureFolder = false
                            showFolderDialog = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.create))
                }
            },
            dismissButton = {
                TextButton(onClick = { showFolderDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showMoveDialog) {
        val availableFolders = if (allFolders.isNotEmpty()) allFolders else items.filterIsInstance<DriveItem.Folder>()
        val targetFiles = items.filter { it.id in selectedItems }
        MoveCopyFolderDialog(
            isCopy = isCopyOperation,
            folders = availableFolders,
            currentFolderId = currentFolderId,
            currentVirtualFolderId = currentVirtualFolderId,
            itemCount = targetFiles.size,
            onDismiss = { showMoveDialog = false },
            onCreateNewFolder = { folderName, isVirtual, parentVfId, onResult ->
                onCreateFolderForPicker(folderName, isVirtual, parentVfId, onResult)
            },
            onSelectDestination = { destination ->
                val selectedFolders = targetFiles.filterIsInstance<DriveItem.Folder>()
                val selectedFileObjects = targetFiles.filterIsInstance<DriveItem.File>()
                if (selectedFileObjects.isNotEmpty()) {
                    onMoveOrCopyItems(selectedFileObjects, destination, isCopyOperation) { count ->
                        if (count > 0) {
                            val msgRes = if (isCopyOperation) R.string.copied_items_toast else R.string.moved_items_toast
                            Toast.makeText(
                                context,
                                context.getString(msgRes, count, destination.label),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
                if (!isCopyOperation && selectedFolders.isNotEmpty()) {
                    val targetChat = if (destination.isRoot || destination.isVirtual) 0L else destination.targetChatId
                    selectedFolders.forEach { sf ->
                        if (!sf.isVirtual) {
                            onMoveFolderContents(sf.telegramChatId, targetChat)
                        }
                    }
                }
                selectedItems = emptySet()
                showMoveDialog = false
            }
        )
    }

    val fabNestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -4f) {
                    isFabVisible = false
                } else if (available.y > 4f) {
                    isFabVisible = true
                }
                return Offset.Zero
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !isSelectionMode && currentFolderId == null && currentVirtualFolderId == "0",
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(310.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(vertical = 12.dp)
                ) {
                    // Google Drive-style Brand Header with TeleNest Cloud Logo
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 14.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White,
                            tonalElevation = 1.dp,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_telenest_logo),
                                contentDescription = stringResource(R.string.telenest_app_name),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(2.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Tele",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                        Text(
                            text = "Nest",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // 1. My Drive / Home
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = stringResource(R.string.my_drive)
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.my_drive),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        selected = currentSection == HomeSection.HOME,
                        onClick = {
                            selectedItems = emptySet()
                            onSelectSection(HomeSection.HOME)
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier
                            .padding(NavigationDrawerItemDefaults.ItemPadding)
                            .testTag("drawer_home_item")
                    )

                    // 2. Recent (Last 7 Days Uploads)
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.Schedule,
                                contentDescription = "Recent"
                            )
                        },
                        label = {
                            Text(
                                text = "Recent",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        selected = currentSection == HomeSection.RECENT,
                        onClick = {
                            selectedItems = emptySet()
                            onSelectSection(HomeSection.RECENT)
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier
                            .padding(NavigationDrawerItemDefaults.ItemPadding)
                            .testTag("drawer_recent_item")
                    )

                    // 3. Offline (Available Offline Files)
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.OfflinePin,
                                contentDescription = "Offline"
                            )
                        },
                        label = {
                            Text(
                                text = "Offline",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        selected = currentSection == HomeSection.OFFLINE,
                        onClick = {
                            selectedItems = emptySet()
                            onSelectSection(HomeSection.OFFLINE)
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier
                            .padding(NavigationDrawerItemDefaults.ItemPadding)
                            .testTag("drawer_offline_item")
                    )

                    // 4. Trash / Recycle Bin (30-Day Retention)
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Trash"
                            )
                        },
                        label = {
                            Text(
                                text = "Trash",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        selected = currentSection == HomeSection.TRASH,
                        onClick = {
                            selectedItems = emptySet()
                            onSelectSection(HomeSection.TRASH)
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier
                            .padding(NavigationDrawerItemDefaults.ItemPadding)
                            .testTag("drawer_trash_item")
                    )

                    // 5. Transfers Menu Item (Active & completed file transfers)
                    NavigationDrawerItem(
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (activeTransfers.isNotEmpty()) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ) {
                                            Text(activeTransfers.size.toString())
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapVert,
                                    contentDescription = stringResource(R.string.transfers)
                                )
                            }
                        },
                        label = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.transfers),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                if (activeTransfers.isNotEmpty()) {
                                    Text(
                                        text = "${activeTransfers.size} active",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        selected = false,
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                onNavigateToTransfers()
                            }
                        },
                        modifier = Modifier
                            .padding(NavigationDrawerItemDefaults.ItemPadding)
                            .testTag("drawer_transfers_item")
                    )

                    // 6. Settings Menu Item (right below Transfers)
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.settings)
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.settings),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        selected = false,
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                onNavigateToSettings()
                            }
                        },
                        modifier = Modifier
                            .padding(NavigationDrawerItemDefaults.ItemPadding)
                            .testTag("drawer_settings_item")
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    // 6. Google Drive-style Storage Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    drawerState.close()
                                    onNavigateToCloudAnalysis()
                                }
                            }
                            .padding(horizontal = 28.dp, vertical = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Cloud,
                                contentDescription = stringResource(R.string.storage),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = stringResource(R.string.storage),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        val visualProgress = remember(totalStorageUsed) {
                            if (totalStorageUsed <= 0L) 0.04f
                            else (log10(totalStorageUsed.toDouble().coerceAtLeast(1.0)).toFloat() / 13f).coerceIn(0.08f, 0.45f)
                        }
                        LinearProgressIndicator(
                            progress = { visualProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "${formatSize(totalStorageUsed)} of Unlimited used",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    drawerState.close()
                                    onNavigateToCloudAnalysis()
                                }
                            },
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                        ) {
                            Text(
                                text = "Unlimited Storage",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    ) {
    Scaffold(
        modifier = Modifier.nestedScroll(fabNestedScrollConnection),
        topBar = {
            AnimatedContent(
                targetState = when {
                    isSelectionMode -> "selection"
                    currentSection != HomeSection.HOME && !isSearchActive -> "section_${currentSection.name}"
                    (currentFolderId != null || currentVirtualFolderId != "0") && !isSearchActive -> "folder"
                    else -> "main"
                },
                transitionSpec = {
                    (fadeIn() + slideInVertically { -it / 2 }).togetherWith(fadeOut() + slideOutVertically { -it / 2 })
                },
                label = "TopBarTransition"
            ) { state ->
                when {
                    state == "selection" -> {
                        TopAppBar(
                            title = { Text(stringResource(R.string.selected_count, selectedItems.size)) },
                            modifier = Modifier.statusBarsPadding(),
                            navigationIcon = {
                                IconButton(onClick = { selectedItems = emptySet() }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = {
                                        val selectable = if (currentSection == HomeSection.TRASH) items else items.filterIsInstance<DriveItem.File>()
                                        if (selectedItems.size >= selectable.size && selectable.isNotEmpty()) {
                                            selectedItems = emptySet()
                                        } else {
                                            selectedItems = selectable.map { it.id }.toSet()
                                        }
                                    }
                                ) {
                                    val selectable = if (currentSection == HomeSection.TRASH) items else items.filterIsInstance<DriveItem.File>()
                                    Icon(
                                        if (selectedItems.size >= selectable.size && selectable.isNotEmpty()) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                        contentDescription = stringResource(R.string.select_all)
                                    )
                                }

                                if (currentSection == HomeSection.TRASH) {
                                    // Restore Selected Items from Trash
                                    IconButton(
                                        onClick = {
                                            val toRestore = items.filter { it.id in selectedItems }
                                            if (toRestore.isNotEmpty()) {
                                                onRestoreItems(toRestore)
                                                Toast.makeText(context, "Restored ${toRestore.size} item(s)", Toast.LENGTH_SHORT).show()
                                            }
                                            selectedItems = emptySet()
                                        }
                                    ) {
                                        Icon(Icons.Default.RestoreFromTrash, contentDescription = stringResource(R.string.restore))
                                    }
                                    // Permanently Delete Selected Items
                                    IconButton(
                                        onClick = {
                                            val toDelete = items.filter { it.id in selectedItems }
                                            if (toDelete.isNotEmpty()) {
                                                showPermanentDeleteConfirm = toDelete
                                            }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteForever,
                                            contentDescription = "Delete Forever",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                } else {
                                    // Make Selected Items Available Offline / Remove from Offline
                                    val selectedList = items.filter { it.id in selectedItems }
                                    val allSelectedOffline = selectedList.isNotEmpty() && selectedList.all { it.isOffline }
                                    IconButton(
                                        onClick = {
                                            val makeOffline = !allSelectedOffline
                                            onSetItemsOffline(selectedList, makeOffline)
                                            Toast.makeText(
                                                context,
                                                if (makeOffline) "Making ${selectedList.size} item(s) available offline"
                                                else "Removed ${selectedList.size} item(s) from offline",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            selectedItems = emptySet()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (allSelectedOffline) Icons.Default.OfflinePin else Icons.Default.DownloadForOffline,
                                            contentDescription = stringResource(R.string.available_offline),
                                            tint = if (allSelectedOffline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            if (isOffline) {
                                                Toast.makeText(context, context.getString(R.string.offline_msg), Toast.LENGTH_SHORT).show()
                                                return@IconButton
                                            }
                                            val selectedFiles = items.filter { it.id in selectedItems }.filterIsInstance<DriveItem.File>()
                                            val selectedFolders = items.filter { it.id in selectedItems }.filterIsInstance<DriveItem.Folder>()
                                            
                                            selectedFiles.forEach { onDownloadFile(it.id, it.parentChatId, it.name) }
                                            selectedFolders.forEach { onDownloadFolderContents(it) }
                                            
                                            selectedItems = emptySet()
                                            Toast.makeText(context, context.getString(R.string.starting_downloads, selectedFiles.size + selectedFolders.size), Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = stringResource(R.string.download))
                                    }
                                    val selectedContainsFolder = items.filter { it.id in selectedItems }.any { it is DriveItem.Folder }
                                    if (!selectedContainsFolder) {
                                        IconButton(
                                            onClick = {
                                                if (isOffline) {
                                                    Toast.makeText(context, context.getString(R.string.offline_msg), Toast.LENGTH_SHORT).show()
                                                    return@IconButton
                                                }
                                                isCopyOperation = true
                                                showMoveDialog = true
                                            }
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copy))
                                        }
                                        IconButton(
                                            onClick = {
                                                if (isOffline) {
                                                    Toast.makeText(context, context.getString(R.string.offline_msg), Toast.LENGTH_SHORT).show()
                                                    return@IconButton
                                                }
                                                isCopyOperation = false
                                                showMoveDialog = true
                                            }
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = stringResource(R.string.move))
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            val itemsToDelete = items.filter { it.id in selectedItems }
                                            showDeleteConfirm = itemsToDelete
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                                    }
                                }
                            }
                        )
                    }
                    state.startsWith("section_") -> {
                        val sectionTitle = when (currentSection) {
                            HomeSection.RECENT -> "Recent"
                            HomeSection.OFFLINE -> "Offline"
                            HomeSection.TRASH -> "Trash"
                            HomeSection.HOME -> stringResource(R.string.my_drive)
                        }
                        Column {
                            TopAppBar(
                                title = {
                                    Text(
                                        text = sectionTitle,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                navigationIcon = {
                                    IconButton(
                                        onClick = { scope.launch { drawerState.open() } },
                                        modifier = Modifier.testTag("open_drawer_button")
                                    ) {
                                        Icon(Icons.Default.Menu, contentDescription = null)
                                    }
                                },
                                actions = {
                                    if (currentSection == HomeSection.TRASH && items.isNotEmpty()) {
                                        TextButton(
                                            onClick = { showEmptyTrashConfirm = true }
                                        ) {
                                            Text(
                                                text = "Empty trash",
                                                color = MaterialTheme.colorScheme.error,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                    IconButton(onClick = { isSearchActive = true }) {
                                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_telenest))
                                    }
                                    IconButton(onClick = { onToggleViewMode() }) {
                                        Icon(if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView, contentDescription = null)
                                    }
                                },
                                modifier = Modifier.statusBarsPadding()
                            )
                            if (currentSection == HomeSection.RECENT || currentSection == HomeSection.OFFLINE) {
                                FilterSortRow(
                                    filterType = filterType,
                                    sortOrder = sortOrder,
                                    onSetFilterType = onSetFilterType,
                                    onSetSortOrder = onSetSortOrder,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    state == "folder" -> {
                        val breadcrumbFolders = remember(currentFolderId, currentVirtualFolderId, allFolders, currentFolderName) {
                            val trail = mutableListOf<DriveItem.Folder>()
                            if (currentFolderId != null) {
                                allFolders.find { !it.isVirtual && it.telegramChatId == currentFolderId }?.let { chFolder ->
                                    trail.add(chFolder)
                                }
                            }
                            if (currentVirtualFolderId != "0") {
                                val vfChain = mutableListOf<DriveItem.Folder>()
                                var cursorId: String? = currentVirtualFolderId
                                val visited = mutableSetOf<String>()
                                var depth = 0
                                while (cursorId != null && cursorId != "0" && depth < 250 && visited.add(cursorId)) {
                                    val found = allFolders.find { it.isVirtual && it.virtualId == cursorId }
                                    if (found != null) {
                                        vfChain.add(0, found)
                                        cursorId = found.virtualParentId
                                    } else {
                                        break
                                    }
                                    depth++
                                }
                                trail.addAll(vfChain)
                            }
                            trail
                        }

                        Column {
                            TopAppBar(
                                title = {
                                    Text(
                                        text = currentFolderName ?: stringResource(R.string.my_drive),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                },
                                navigationIcon = {
                                    IconButton(onClick = { navigateBackWithState() }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                                    }
                                },
                                actions = {
                                    IconButton(
                                        onClick = {
                                            isVirtualFolder = true
                                            isSecureFolder = false
                                            showFolderDialog = true
                                        }
                                    ) {
                                        Icon(Icons.Default.CreateNewFolder, contentDescription = stringResource(R.string.create_folder))
                                    }
                                    IconButton(onClick = { isSearchActive = true }) {
                                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_telenest))
                                    }
                                    IconButton(onClick = { onToggleViewMode() }) {
                                        Icon(if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView, contentDescription = null)
                                    }
                                },
                                modifier = Modifier.statusBarsPadding()
                            )
                            // Interactive Google Drive-style Breadcrumb Path Bar
                            androidx.compose.foundation.lazy.LazyRow(
                                verticalAlignment = Alignment.CenterVertically,
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                item {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.clickable {
                                            navigateToFolderWithState(null, "0", null)
                                        }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Home,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = stringResource(R.string.my_drive),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                                items(breadcrumbFolders.size) { idx ->
                                    val crumb = breadcrumbFolders[idx]
                                    val isLast = idx == breadcrumbFolders.lastIndex
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "›",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                        Surface(
                                            color = if (isLast) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.clickable(enabled = !isLast) {
                                                if (crumb.isVirtual) {
                                                    navigateToFolderWithState(currentFolderId, crumb.virtualId, crumb.name)
                                                } else {
                                                    navigateToFolderWithState(crumb.telegramChatId, "0", crumb.name)
                                                }
                                            }
                                        ) {
                                            Text(
                                                text = crumb.name,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (isLast) MaterialTheme.colorScheme.onPrimaryContainer
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            FilterSortRow(
                                filterType = filterType,
                                sortOrder = sortOrder,
                                sortAscending = sortAscending,
                                onSetFilterType = onSetFilterType,
                                onSetSortOrder = onSetSortOrder,
                                onToggleSortDirection = onToggleSortDirection,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                            )
                        }
                    }
                    else -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Left Menu Icon (opens Google Drive-style Drawer with Settings & Storage)
                                if (!isSearchActive) {
                                    IconButton(
                                        onClick = { scope.launch { drawerState.open() } },
                                        modifier = Modifier
                                            .size(44.dp)
                                            .testTag("open_drawer_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Menu,
                                            contentDescription = stringResource(R.string.settings),
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                // Compact Pill Search Bar in the middle
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(28.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .clickable { isSearchActive = true }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(start = 16.dp, end = 6.dp)
                                    ) {
                                        if (!isSearchActive) {
                                            Icon(
                                                imageVector = Icons.Default.Search,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = stringResource(R.string.search_telenest),
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(onClick = { onToggleViewMode() }) {
                                                Icon(
                                                    imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        } else {
                                            IconButton(onClick = { isSearchActive = false }) {
                                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                                BasicTextField(
                                                    value = searchQuery,
                                                    onValueChange = onSearchQueryChange,
                                                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                if (searchQuery.isEmpty()) {
                                                    Text(stringResource(R.string.search_telenest), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                            if (searchQuery.isNotEmpty()) {
                                                IconButton(onClick = { onSearchQueryChange("") }) {
                                                    Icon(Icons.Default.Close, contentDescription = null)
                                                }
                                            }
                                        }
                                    }
                                }

                                // Right Telegram Profile Avatar (Google Drive-style ring + user avatar)
                                if (!isSearchActive) {
                                    val avatarFile = remember(userProfile?.avatarPath) {
                                        userProfile?.avatarPath?.let { File(it) }?.takeIf { it.exists() }
                                    }
                                    val ringBrush = remember {
                                        Brush.sweepGradient(
                                            listOf(
                                                Color(0xFF4285F4),
                                                Color(0xFF34A853),
                                                Color(0xFFFBBC05),
                                                Color(0xFFEA4335),
                                                Color(0xFF4285F4)
                                            )
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .border(BorderStroke(2.dp, ringBrush), CircleShape)
                                            .padding(3.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                            .clickable { onNavigateToProfile() }
                                            .testTag("top_profile_avatar_button"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (avatarFile != null) {
                                            AsyncImage(
                                                model = avatarFile,
                                                contentDescription = stringResource(R.string.profile),
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            val initials = remember(userProfile) {
                                                val f = userProfile?.firstName?.firstOrNull()?.uppercaseChar()?.toString().orEmpty()
                                                val l = userProfile?.lastName?.firstOrNull()?.uppercaseChar()?.toString().orEmpty()
                                                (f + l).ifEmpty { "T" }
                                            }
                                            Text(
                                                text = initials,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }


                                }
                            }

                            if (!isSearchActive) {
                                Spacer(modifier = Modifier.height(10.dp))

                                FilterSortRow(
                                    filterType = filterType,
                                    sortOrder = sortOrder,
                                    sortAscending = sortAscending,
                                    onSetFilterType = onSetFilterType,
                                    onSetSortOrder = onSetSortOrder,
                                    onToggleSortDirection = onToggleSortDirection
                                )
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = !isOffline && currentSection == HomeSection.HOME && isFabVisible && !isSelectionMode,
                enter = slideInVertically(initialOffsetY = { it * 2 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it * 2 }) + fadeOut()
            ) {
                ExtendedFloatingActionButton(
                    onClick = { showNewSheet = true },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(R.string.new_label),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 6.dp,
                        pressedElevation = 10.dp,
                        focusedElevation = 8.dp,
                        hoveredElevation = 8.dp
                    ),
                    modifier = Modifier
                        .testTag("home_new_fab")
                        .padding(bottom = 8.dp, end = 4.dp)
                )
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (isInitialLoading && items.isEmpty()) {
                    TeleNestWelcomeSplashScreen(
                        subtitle = stringResource(R.string.preparing_drive),
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    if (isRefreshing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    if (isOffline) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.CloudOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (currentSection == HomeSection.OFFLINE) {
                                        "Offline mode • Viewing files saved on your device"
                                    } else {
                                        stringResource(R.string.offline_msg)
                                    },
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }

                    // Live Google Drive Transfer Progress Banner (when uploads/downloads are running)
                    AnimatedVisibility(
                        visible = activeTransfers.isNotEmpty(),
                        enter = fadeIn() + slideInVertically(),
                        exit = fadeOut() + slideOutVertically()
                    ) {
                        val primaryTransfer = activeTransfers.firstOrNull()
                        if (primaryTransfer != null) {
                            val pct = (primaryTransfer.progress * 100).toInt().coerceIn(0, 100)
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .clickable { onNavigateToTransfers() }
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = if (primaryTransfer.isDownload) Icons.Default.Download else Icons.Default.CloudUpload,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = buildString {
                                                    append(if (primaryTransfer.isDownload) "Downloading " else "Uploading ")
                                                    append(primaryTransfer.fileName)
                                                    if (activeTransfers.size > 1) {
                                                        append(" (+${activeTransfers.size - 1} more)")
                                                    }
                                                },
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                            Text(
                                                text = "$pct% • Tap to view all transfers",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                                            )
                                        }
                                        IconButton(
                                            onClick = { onCancelTransfer(primaryTransfer.remoteUniqueId) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Cancel transfer",
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { primaryTransfer.progress.coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Contextual Section Banner for Trash, Recent, or Offline
                    if (currentSection == HomeSection.TRASH) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "Items in Trash are automatically deleted forever after 30 days.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    } else if (currentSection == HomeSection.RECENT) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Showing files uploaded in the last 7 days",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else if (currentSection == HomeSection.OFFLINE) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OfflinePin,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Saved inside TeleNest offline storage • Available anytime without internet",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (items.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val emptyIcon = when {
                                    searchQuery.isNotEmpty() -> Icons.Default.SearchOff
                                    currentSection == HomeSection.TRASH -> Icons.Outlined.Delete
                                    currentSection == HomeSection.OFFLINE -> Icons.Outlined.OfflinePin
                                    currentSection == HomeSection.RECENT -> Icons.Outlined.Schedule
                                    else -> Icons.Default.CloudQueue
                                }
                                val emptyTitle = when {
                                    searchQuery.isNotEmpty() -> stringResource(R.string.no_results)
                                    currentSection == HomeSection.TRASH -> "Trash is empty"
                                    currentSection == HomeSection.OFFLINE -> "No offline files yet"
                                    currentSection == HomeSection.RECENT -> "No recent uploads in the last 7 days"
                                    else -> stringResource(R.string.drive_empty)
                                }
                                val emptySubtitle = when {
                                    searchQuery.isNotEmpty() -> ""
                                    currentSection == HomeSection.TRASH -> "Deleted files & folders stay here for 30 days before permanent removal"
                                    currentSection == HomeSection.OFFLINE -> "Tap the 3-dot menu on any file and select \"Make available offline\""
                                    currentSection == HomeSection.RECENT -> "Files you upload will appear here for 7 days"
                                    else -> stringResource(R.string.tap_to_add)
                                }
                                Icon(
                                    imageVector = emptyIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(80.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = emptyTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (emptySubtitle.isNotEmpty()) {
                                    Text(
                                        text = emptySubtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.padding(top = 4.dp, start = 32.dp, end = 32.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        val isTrashSection = currentSection == HomeSection.TRASH
                        val isAtRootMyDrive = currentSection == HomeSection.HOME &&
                            currentFolderId == null &&
                            currentVirtualFolderId == "0" &&
                            searchQuery.isEmpty() &&
                            filterType == FilterType.ALL
                        val folderItems = remember(items) { items.filterIsInstance<DriveItem.Folder>() }
                        val fileItems = remember(items) { items.filterIsInstance<DriveItem.File>() }

                        val handleItemClick: (DriveItem) -> Unit = { item ->
                            val isSelected = selectedItems.contains(item.id)
                            if (isSelectionMode) {
                                if (!isTrashSection) {
                                    val hasFiles = items.any { it.id in selectedItems && it is DriveItem.File }
                                    if (hasFiles && item is DriveItem.Folder) {
                                        Toast.makeText(context, context.getString(R.string.selection_folder_error), Toast.LENGTH_SHORT).show()
                                    } else {
                                        selectedItems = if (isSelected) selectedItems - item.id else selectedItems + item.id
                                    }
                                } else {
                                    selectedItems = if (isSelected) selectedItems - item.id else selectedItems + item.id
                                }
                            } else if (isTrashSection) {
                                activeMenuDriveItem = item
                            } else {
                                if (item is DriveItem.File) onNavigateToPreview(item)
                                else if (item is DriveItem.Folder) navigateToFolderWithState(if (item.isVirtual) currentFolderId else item.telegramChatId, item.virtualId, item.name)
                            }
                        }

                        if (isGridView) {
                            LazyVerticalGrid(
                                state = gridState,
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                if (isAtRootMyDrive && suggestedFiles.isNotEmpty()) {
                                    item(
                                        key = "header_suggested",
                                        span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
                                        contentType = "section_suggested"
                                    ) {
                                        SuggestedFilesSection(
                                            files = suggestedFiles,
                                            expanded = isSuggestedExpanded,
                                            onToggleExpand = { isSuggestedExpanded = !isSuggestedExpanded },
                                            onFileClick = onNavigateToPreview,
                                            onFileMoreClick = { activeMenuDriveItem = it }
                                        )
                                    }
                                }

                                if (folderItems.isNotEmpty()) {
                                    item(
                                        key = "header_folders",
                                        span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
                                        contentType = "section_header"
                                    ) {
                                        GoogleDriveSectionHeader(
                                            title = "Folders (${folderItems.size})",
                                            sortOrder = sortOrder,
                                            sortAscending = sortAscending,
                                            onToggleSortDirection = onToggleSortDirection
                                        )
                                    }
                                    items(
                                        items = folderItems,
                                        key = { it.uniqueKey },
                                        contentType = { "folder_grid" }
                                    ) { item ->
                                        val isSelected = selectedItems.contains(item.id)
                                        DriveGridItem(
                                            item = item,
                                            isSelected = isSelected,
                                            isTrashMode = isTrashSection,
                                            onClick = { handleItemClick(item) },
                                            onLongClick = { if (!isSelectionMode) selectedItems = setOf(item.id) },
                                            onStarClick = { onToggleStarred(item) },
                                            onShareClick = { shareItem = item },
                                            onDownloadClick = { folderToDownload = item },
                                            onMoreClick = { activeMenuDriveItem = item },
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                }

                                if (fileItems.isNotEmpty()) {
                                    item(
                                        key = "header_files",
                                        span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
                                        contentType = "section_header"
                                    ) {
                                        GoogleDriveSectionHeader(
                                            title = "Files (${fileItems.size})",
                                            sortOrder = if (folderItems.isEmpty()) sortOrder else null,
                                            sortAscending = sortAscending,
                                            onToggleSortDirection = onToggleSortDirection
                                        )
                                    }
                                    items(
                                        items = fileItems,
                                        key = { it.uniqueKey },
                                        contentType = { "file_grid" }
                                    ) { item ->
                                        val isSelected = selectedItems.contains(item.id)
                                        DriveGridItem(
                                            item = item,
                                            isSelected = isSelected,
                                            isTrashMode = isTrashSection,
                                            onClick = { handleItemClick(item) },
                                            onLongClick = { if (!isSelectionMode) selectedItems = setOf(item.id) },
                                            onStarClick = { onToggleStarred(item) },
                                            onShareClick = { shareItem = item },
                                            onDownloadClick = { onDownloadFile(item.id, item.parentChatId, item.name) },
                                            onMoreClick = { activeMenuDriveItem = item },
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                if (isAtRootMyDrive && suggestedFiles.isNotEmpty()) {
                                    item(
                                        key = "header_suggested",
                                        contentType = "section_suggested"
                                    ) {
                                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                            SuggestedFilesSection(
                                                files = suggestedFiles,
                                                expanded = isSuggestedExpanded,
                                                onToggleExpand = { isSuggestedExpanded = !isSuggestedExpanded },
                                                onFileClick = onNavigateToPreview,
                                                onFileMoreClick = { activeMenuDriveItem = it }
                                            )
                                        }
                                    }
                                }

                                if (folderItems.isNotEmpty()) {
                                    item(
                                        key = "header_folders",
                                        contentType = "section_header"
                                    ) {
                                        GoogleDriveSectionHeader(
                                            title = "Folders (${folderItems.size})",
                                            sortOrder = sortOrder,
                                            sortAscending = sortAscending,
                                            onToggleSortDirection = onToggleSortDirection,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                        )
                                    }
                                    items(
                                        items = folderItems,
                                        key = { it.uniqueKey },
                                        contentType = { "folder_list" }
                                    ) { item ->
                                        val isSelected = selectedItems.contains(item.id)
                                        DriveListItem(
                                            item = item,
                                            isSelected = isSelected,
                                            isTrashMode = isTrashSection,
                                            onClick = { handleItemClick(item) },
                                            onLongClick = { if (!isSelectionMode) selectedItems = setOf(item.id) },
                                            onStarClick = { onToggleStarred(item) },
                                            onShareClick = { shareItem = item },
                                            onDownloadClick = { folderToDownload = item },
                                            onMoreClick = { activeMenuDriveItem = item },
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                }

                                if (fileItems.isNotEmpty()) {
                                    item(
                                        key = "header_files",
                                        contentType = "section_header"
                                    ) {
                                        GoogleDriveSectionHeader(
                                            title = "Files (${fileItems.size})",
                                            sortOrder = if (folderItems.isEmpty()) sortOrder else null,
                                            sortAscending = sortAscending,
                                            onToggleSortDirection = onToggleSortDirection,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                        )
                                    }
                                    items(
                                        items = fileItems,
                                        key = { it.uniqueKey },
                                        contentType = { "file_list" }
                                    ) { item ->
                                        val isSelected = selectedItems.contains(item.id)
                                        DriveListItem(
                                            item = item,
                                            isSelected = isSelected,
                                            isTrashMode = isTrashSection,
                                            onClick = { handleItemClick(item) },
                                            onLongClick = { if (!isSelectionMode) selectedItems = setOf(item.id) },
                                            onStarClick = { onToggleStarred(item) },
                                            onShareClick = { shareItem = item },
                                            onDownloadClick = { onDownloadFile(item.id, item.parentChatId, item.name) },
                                            onMoreClick = { activeMenuDriveItem = item },
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Preview(showBackground = true)
@Composable
fun HomePreview() {
    TeledriveTheme {
        HomeContent(
            items = listOf(
                DriveItem.Folder(id = 1L, parentChatId = 0L, name = "Documents", telegramChatId = 123456L, isStarred = false),
                DriveItem.File(id = 2L, parentChatId = 0L, name = "image.jpg", size = 1024 * 1024, mimeType = "image/jpeg", telegramFileId = 1, thumbnailPath = null, localPath = null, isStarred = false)
            ),
            totalStorageUsed = 1024 * 1024,
            searchQuery = "",
            sortOrder = SortOrder.NAME,
            filterType = FilterType.ALL,
            isRefreshing = false,
            isInitialLoading = false,
            currentFolderId = null,
            currentVirtualFolderId = "0",
            currentFolderName = null,
            isGridView = false,
            connectivityStatus = ConnectivityObserver.Status.Available,
            onNavigateToTransfers = {},
            onNavigateToSettings = {},
            onNavigateToPreview = { _ -> },
            onSearchQueryChange = { _ -> },
            onToggleViewMode = {},
            onSetSortOrder = { _ -> },
            onSetFilterType = { _ -> },
            onCreateFolder = { _, _, _ -> },
            isPasswordSet = true,
            onSetMasterPassword = {},
            onUploadFile = { _, _, _, _ -> },
            onDeleteItems = { _ -> },
            onMoveItems = { _, _ -> },
            onMoveFolderContents = { _, _ -> },
            onDownloadFile = { _, _, _ -> },
            onDownloadFolderContents = { _ -> },
            onToggleStarred = { _ -> },
            onNavigateToFolder = { _, _, _ -> },
            onNavigateBack = {},
            onRefresh = {},
            onRenameItem = { _, _ -> },
            onGetInviteLink = { _, _ -> },
            onGetMembers = { _, _ -> },
            onShareFile = { _, _, _ -> },
            onLogout = {}
        )
    }
}



@Composable
fun NewActionItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(96.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f),
            shape = CircleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier.size(54.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun GoogleDriveSectionHeader(
    title: String,
    sortOrder: SortOrder? = null,
    sortAscending: Boolean = false,
    onToggleSortDirection: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (sortOrder != null && onToggleSortDirection != null) {
            Surface(
                color = Color.Transparent,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onToggleSortDirection)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = when (sortOrder) {
                            SortOrder.NAME -> stringResource(R.string.sort_name)
                            SortOrder.DATE -> stringResource(R.string.sort_date)
                            SortOrder.SIZE -> stringResource(R.string.sort_size)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                        contentDescription = "Toggle sort direction",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SuggestedFilesSection(
    files: List<DriveItem.File>,
    expanded: Boolean = true,
    onToggleExpand: () -> Unit = {},
    onFileClick: (DriveItem.File) -> Unit,
    onFileMoreClick: (DriveItem.File) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onToggleExpand)
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Suggested",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse suggested" else "Expand suggested",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                Spacer(Modifier.height(4.dp))
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(start = 2.dp, end = 2.dp, bottom = 4.dp)
                ) {
                    items(
                        items = files,
                        key = { "suggested_${it.uniqueKey}" },
                        contentType = { "suggested_card" }
                    ) { file ->
                        val (icon, color) = getFileIconAndColor(file)
                        val thumbPath = if (!file.isEncrypted) (file.thumbnailPath ?: file.localPath) else null
                        val imageRequest = remember(thumbPath) {
                            thumbPath?.let {
                                ImageRequest.Builder(context)
                                    .data(it)
                                    .size(220)
                                    .crossfade(true)
                                    .build()
                            }
                        }
                        OutlinedCard(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .width(152.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onFileClick(file) }
                        ) {
                            Column {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(86.dp)
                                        .background(color.copy(alpha = 0.08f))
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = color.copy(alpha = 0.75f),
                                        modifier = Modifier.size(30.dp)
                                    )
                                    if (imageRequest != null) {
                                        AsyncImage(
                                            model = imageRequest,
                                            contentDescription = file.name,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    if (file.isStarred) {
                                        Surface(
                                            color = Color.Black.copy(alpha = 0.45f),
                                            shape = CircleShape,
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(6.dp)
                                                .size(20.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Star,
                                                    contentDescription = null,
                                                    tint = Color(0xFFFFC107),
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = color,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = file.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { onFileMoreClick(file) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "More",
                                            modifier = Modifier.size(15.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FilterSortRow(
    filterType: FilterType,
    sortOrder: SortOrder,
    sortAscending: Boolean = false,
    onSetFilterType: (FilterType) -> Unit,
    onSetSortOrder: (SortOrder) -> Unit,
    onToggleSortDirection: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
            modifier = Modifier.weight(1f)
        ) {
            val categories = FilterType.entries
            items(categories) { category ->
                FilterChip(
                    selected = filterType == category,
                    onClick = { onSetFilterType(category) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    label = {
                        Text(
                            when (category) {
                                FilterType.ALL -> stringResource(R.string.filter_all)
                                FilterType.PHOTOS -> stringResource(R.string.filter_photos)
                                FilterType.VIDEOS -> stringResource(R.string.filter_videos)
                                FilterType.AUDIO -> stringResource(R.string.filter_audio)
                                FilterType.DOCUMENTS -> stringResource(R.string.filter_documents)
                                FilterType.APKS -> stringResource(R.string.filter_apks)
                            }
                        )
                    },
                    leadingIcon = if (filterType == category) {
                        {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    } else if (category == FilterType.APKS) {
                        {
                            Icon(
                                Icons.Default.Android,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                                tint = Color(0xFF3DDC84)
                            )
                        }
                    } else null
                )
            }
        }

        Surface(
            onClick = onToggleSortDirection,
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            tonalElevation = 2.dp,
            shadowElevation = 1.dp,
            modifier = Modifier
                .size(36.dp)
                .testTag("sort_direction_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = "Sort direction",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        var showSortMenu by remember { mutableStateOf(false) }
        Box {
            Surface(
                onClick = { showSortMenu = true },
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                tonalElevation = 2.dp,
                shadowElevation = 1.dp,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("sort_menu_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = "Sort",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { showSortMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sort_name)) },
                    onClick = { onSetSortOrder(SortOrder.NAME); showSortMenu = false },
                    leadingIcon = { if (sortOrder == SortOrder.NAME) Icon(Icons.Default.Check, null) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sort_date)) },
                    onClick = { onSetSortOrder(SortOrder.DATE); showSortMenu = false },
                    leadingIcon = { if (sortOrder == SortOrder.DATE) Icon(Icons.Default.Check, null) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sort_size)) },
                    onClick = { onSetSortOrder(SortOrder.SIZE); showSortMenu = false },
                    leadingIcon = { if (sortOrder == SortOrder.SIZE) Icon(Icons.Default.Check, null) }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                DropdownMenuItem(
                    text = { Text(if (sortAscending) "Ascending (A→Z / Oldest)" else "Descending (Z→A / Newest)") },
                    onClick = { onToggleSortDirection(); showSortMenu = false },
                    leadingIcon = {
                        Icon(
                            imageVector = if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = null
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun resolveFolderColor(folder: DriveItem.Folder): Color {
    val customHex = folder.colorHex?.trim()
    if (!customHex.isNullOrEmpty()) {
        try {
            return Color(android.graphics.Color.parseColor(customHex))
        } catch (_: Exception) { }
    }
    return when {
        folder.isSecure -> Color(0xFF14B8A6)
        folder.isVirtual -> Color(0xFF5F6368)
        else -> Color(0xFF4285F4)
    }
}

@Composable
fun GoogleDriveFolderIcon(
    color: Color,
    isChannel: Boolean = false,
    isSecure: Boolean = false,
    modifier: Modifier = Modifier
) {
    val tabColor = remember(color) {
        Color(
            red = (color.red * 0.82f).coerceIn(0f, 1f),
            green = (color.green * 0.82f).coerceIn(0f, 1f),
            blue = (color.blue * 0.82f).coerceIn(0f, 1f),
            alpha = 1f
        )
    }
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val padX = w * 0.06f
            val padY = h * 0.12f
            val fw = w - padX * 2f
            val fh = h - padY * 2f
            val corner = fw * 0.11f
            val tabH = fh * 0.17f
            val tabW = fw * 0.42f

            // Back plate + top-left Google Drive folder tab
            val tabPath = Path().apply {
                moveTo(padX, padY + corner)
                quadraticTo(padX, padY, padX + corner, padY)
                lineTo(padX + tabW - corner * 0.5f, padY)
                quadraticTo(padX + tabW, padY, padX + tabW + corner * 0.65f, padY + tabH)
                lineTo(padX + fw - corner, padY + tabH)
                quadraticTo(padX + fw, padY + tabH, padX + fw, padY + tabH + corner)
                lineTo(padX + fw, padY + fh - corner)
                quadraticTo(padX + fw, padY + fh, padX + fw - corner, padY + fh)
                lineTo(padX + corner, padY + fh)
                quadraticTo(padX, padY + fh, padX, padY + fh - corner)
                close()
            }
            drawPath(path = tabPath, color = tabColor)

            // Main front Google Drive folder body
            val bodyTop = padY + tabH
            drawRoundRect(
                color = color,
                topLeft = Offset(padX, bodyTop),
                size = Size(fw, fh - tabH),
                cornerRadius = CornerRadius(corner, corner)
            )
        }
        if (isSecure) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(14.dp).padding(top = 3.dp)
            )
        } else if (isChannel) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(15.dp).padding(top = 3.dp)
            )
        }
    }
}

@Composable
fun SculptedFolderGraphic(
    isVirtual: Boolean,
    isSecure: Boolean = false,
    customColorHex: String? = null,
    modifier: Modifier = Modifier
) {
    val parsedCustom = remember(customColorHex) {
        customColorHex?.takeIf { it.isNotBlank() }?.let {
            try { Color(android.graphics.Color.parseColor(it)) } catch (_: Exception) { null }
        }
    }
    val baseColor = parsedCustom ?: when {
        isSecure -> Color(0xFF14B8A6)
        isVirtual -> Color(0xFF5F6368)
        else -> Color(0xFF4285F4)
    }
    GoogleDriveFolderIcon(
        color = baseColor,
        isChannel = !isVirtual,
        isSecure = isSecure,
        modifier = modifier
    )
}
// Google Drive Folder Icon replaces old multi-part canvas body

@Composable
fun MoveCopyFolderDialog(
    isCopy: Boolean,
    folders: List<DriveItem.Folder>,
    currentFolderId: Long?,
    currentVirtualFolderId: String,
    itemCount: Int,
    onDismiss: () -> Unit,
    onCreateNewFolder: (String, Boolean, String, (MoveCopyDestination?) -> Unit) -> Unit,
    onSelectDestination: (MoveCopyDestination) -> Unit,
    isUploadDestinationPicker: Boolean = false
) {
    var showInlineCreate by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var newFolderIsVirtual by remember { mutableStateOf(true) }
    var isCreating by remember { mutableStateOf(false) }

    // Hierarchical navigation stack inside the Move/Copy dialog (empty = Root / My Drive)
    var browsedFolderStack by remember { mutableStateOf<List<DriveItem.Folder>>(emptyList()) }
    // Local cache of newly created folders during this dialog session so they appear & can be navigated into immediately
    var sessionCreatedFolders by remember { mutableStateOf<List<DriveItem.Folder>>(emptyList()) }

    val rootLabel = stringResource(R.string.root_storage)
    val myDriveShort = stringResource(R.string.my_drive)

    // Strictly deduplicate folders by virtualId (for virtual folders) and telegramChatId (for channel folders)
    val cleanFolders = remember(folders, sessionCreatedFolders) {
        val combined = folders + sessionCreatedFolders
        val seenVirtual = mutableSetOf<String>()
        val seenChannel = mutableSetOf<Long>()
        combined.filter { folder ->
            if (folder.isSecure) {
                false
            } else if (folder.isVirtual) {
                val vid = folder.virtualId
                vid != null && !folder.name.startsWith("Recovered_") && seenVirtual.add(vid)
            } else {
                seenChannel.add(folder.telegramChatId)
            }
        }
    }

    val channelChatIds = remember(cleanFolders) {
        cleanFolders.filter { !it.isVirtual }.map { it.telegramChatId }.toSet()
    }

    // Keep browsedFolderStack items updated with latest folder stats if available
    val currentBrowsedFolder = browsedFolderStack.lastOrNull()?.let { last ->
        if (last.isVirtual) {
            cleanFolders.find { it.isVirtual && it.virtualId == last.virtualId } ?: last
        } else {
            cleanFolders.find { !it.isVirtual && it.telegramChatId == last.telegramChatId } ?: last
        }
    }

    BackHandler(enabled = showInlineCreate || browsedFolderStack.isNotEmpty()) {
        if (showInlineCreate) {
            showInlineCreate = false
            newFolderName = ""
        } else if (browsedFolderStack.isNotEmpty()) {
            browsedFolderStack = browsedFolderStack.dropLast(1)
        }
    }

    // Compute ONLY the folders that belong at the currently browsed hierarchy level (Google Drive / MEGA style)
    val currentLevelFolders = remember(cleanFolders, currentBrowsedFolder, channelChatIds) {
        when {
            currentBrowsedFolder == null -> {
                // At Root (Home): ONLY show top-level folders (never dump nested subfolders at Root!)
                cleanFolders.filter { folder ->
                    if (folder.isVirtual) {
                        val pid = folder.virtualParentId
                        val isTopVirtual = pid == null || pid == "0" || pid.isBlank()
                        val isInsideChannel = folder.parentChatId in channelChatIds
                        isTopVirtual && !isInsideChannel
                    } else {
                        true
                    }
                }
            }
            currentBrowsedFolder.isVirtual -> {
                // Inside a Virtual Folder: ONLY show direct child subfolders of this Virtual Folder
                val parentVid = currentBrowsedFolder.virtualId
                cleanFolders.filter { folder ->
                    folder.isVirtual && folder.virtualParentId == parentVid
                }
            }
            else -> {
                // Inside a Channel Folder: ONLY show direct child virtual subfolders created inside this Channel Folder
                val chId = currentBrowsedFolder.telegramChatId
                cleanFolders.filter { folder ->
                    val pid = folder.virtualParentId
                    folder.isVirtual &&
                        folder.parentChatId == chId &&
                        (pid == null || pid == "0" || pid.isBlank())
                }
            }
        }
    }

    // Determine whether the currently browsed location is the exact same folder where the items already reside
    val isSameAsSourceLocation = remember(currentBrowsedFolder, currentFolderId, currentVirtualFolderId) {
        when {
            currentBrowsedFolder == null -> {
                currentFolderId == null && (currentVirtualFolderId == "0" || currentVirtualFolderId.isBlank())
            }
            currentBrowsedFolder.isVirtual -> {
                currentBrowsedFolder.virtualId == currentVirtualFolderId
            }
            else -> {
                currentBrowsedFolder.telegramChatId == currentFolderId &&
                    (currentVirtualFolderId == "0" || currentVirtualFolderId.isBlank())
            }
        }
    }

    val canConfirmCurrentLocation = isUploadDestinationPicker || isCopy || !isSameAsSourceLocation

    val currentDestinationPathLabel = remember(browsedFolderStack, myDriveShort) {
        if (browsedFolderStack.isEmpty()) {
            myDriveShort
        } else {
            (listOf(myDriveShort) + browsedFolderStack.map { it.name }).joinToString(" › ")
        }
    }

    val currentDestinationObject = remember(currentBrowsedFolder, rootLabel, currentDestinationPathLabel) {
        when {
            currentBrowsedFolder == null -> MoveCopyDestination(
                label = rootLabel,
                isRoot = true,
                isVirtual = false,
                targetChatId = 0L,
                targetVirtualId = "0"
            )
            currentBrowsedFolder.isVirtual -> MoveCopyDestination(
                label = currentDestinationPathLabel,
                isRoot = false,
                isVirtual = true,
                targetChatId = currentBrowsedFolder.parentChatId,
                targetVirtualId = currentBrowsedFolder.virtualId ?: "0"
            )
            else -> MoveCopyDestination(
                label = currentDestinationPathLabel,
                isRoot = false,
                isVirtual = false,
                targetChatId = currentBrowsedFolder.telegramChatId,
                targetVirtualId = "0"
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (browsedFolderStack.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    showInlineCreate = false
                                    browsedFolderStack = browsedFolderStack.dropLast(1)
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.back),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentBrowsedFolder?.name
                                    ?: when {
                                        isUploadDestinationPicker -> "Select Upload Folder"
                                        isCopy -> stringResource(R.string.copy_to_folder)
                                        else -> stringResource(R.string.move_to_folder)
                                    },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = buildString {
                                    append(
                                        when {
                                            isUploadDestinationPicker -> "Upload"
                                            isCopy -> "Copy"
                                            else -> "Move"
                                        }
                                    )
                                    if (itemCount > 0) {
                                        append(" $itemCount item${if (itemCount > 1) "s" else ""}")
                                    }
                                    if (currentBrowsedFolder == null) {
                                        append(" • $myDriveShort")
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.clickable {
                            if (currentBrowsedFolder != null) {
                                newFolderIsVirtual = true
                            }
                            showInlineCreate = !showInlineCreate
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CreateNewFolder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.new_folder),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Interactive Breadcrumb Bar inside the dialog (like Google Drive / MEGA)
                androidx.compose.foundation.lazy.LazyRow(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        val isRootSelected = browsedFolderStack.isEmpty()
                        Surface(
                            color = if (isRootSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable(enabled = !isRootSelected) {
                                showInlineCreate = false
                                browsedFolderStack = emptyList()
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = null,
                                    tint = if (isRootSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = myDriveShort,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isRootSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                                    fontWeight = if (isRootSelected) FontWeight.Bold else FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    items(browsedFolderStack.size) { index ->
                        val crumbFolder = browsedFolderStack[index]
                        val isLastCrumb = index == browsedFolderStack.lastIndex
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "›",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 3.dp)
                            )
                            Surface(
                                color = if (isLastCrumb) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable(enabled = !isLastCrumb) {
                                    showInlineCreate = false
                                    browsedFolderStack = browsedFolderStack.take(index + 1)
                                }
                            ) {
                                Text(
                                    text = crumbFolder.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isLastCrumb) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isLastCrumb) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showInlineCreate) {
                    val parentLocationName = currentBrowsedFolder?.name ?: myDriveShort
                    val effectiveIsVirtual = if (currentBrowsedFolder != null) true else newFolderIsVirtual
                    val targetParentVfId = when {
                        currentBrowsedFolder == null -> "0"
                        currentBrowsedFolder.isVirtual -> currentBrowsedFolder.virtualId ?: "0"
                        else -> currentBrowsedFolder.telegramChatId.toString()
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${stringResource(R.string.create_folder)} in \"$parentLocationName\"",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedTextField(
                                value = newFolderName,
                                onValueChange = { newFolderName = it },
                                label = { Text(stringResource(R.string.folder_name)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (currentBrowsedFolder == null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = newFolderIsVirtual,
                                        onClick = { newFolderIsVirtual = true },
                                        label = { Text(stringResource(R.string.virtual_folder), maxLines = 1) },
                                        leadingIcon = if (newFolderIsVirtual) {
                                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                                        } else null
                                    )
                                    FilterChip(
                                        selected = !newFolderIsVirtual,
                                        onClick = { newFolderIsVirtual = false },
                                        label = { Text(stringResource(R.string.channel_folder), maxLines = 1) },
                                        leadingIcon = if (!newFolderIsVirtual) {
                                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                                        } else null
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        showInlineCreate = false
                                        newFolderName = ""
                                    }
                                ) {
                                    Text(stringResource(R.string.cancel))
                                }
                                Spacer(Modifier.width(4.dp))
                                // Button 1: Create & Open (creates folder and enters it so user can nest more folders or tap Move/Copy Here)
                                androidx.compose.material3.FilledTonalButton(
                                    enabled = newFolderName.isNotBlank() && !isCreating,
                                    onClick = {
                                        val createdName = newFolderName.trim()
                                        isCreating = true
                                        onCreateNewFolder(createdName, effectiveIsVirtual, targetParentVfId) { createdDest ->
                                            isCreating = false
                                            showInlineCreate = false
                                            newFolderName = ""
                                            if (createdDest != null) {
                                                val newFolderItem = DriveItem.Folder(
                                                    id = if (createdDest.isVirtual) createdDest.targetVirtualId.hashCode().toLong() else createdDest.targetChatId,
                                                    parentChatId = createdDest.targetChatId,
                                                    name = createdName,
                                                    telegramChatId = createdDest.targetChatId,
                                                    isStarred = false,
                                                    isVirtual = createdDest.isVirtual,
                                                    virtualId = if (createdDest.isVirtual) createdDest.targetVirtualId else null,
                                                    virtualParentId = if (currentBrowsedFolder?.isVirtual == true) currentBrowsedFolder.virtualId else "0",
                                                    isSecure = false,
                                                    createdAt = System.currentTimeMillis(),
                                                    itemCount = 0,
                                                    totalSize = 0L
                                                )
                                                sessionCreatedFolders = sessionCreatedFolders + newFolderItem
                                                browsedFolderStack = browsedFolderStack + newFolderItem
                                            }
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(stringResource(R.string.create), style = MaterialTheme.typography.labelMedium)
                                }
                                Spacer(Modifier.width(6.dp))
                                // Button 2: Create & Select/Move/Copy Here directly
                                Button(
                                    enabled = newFolderName.isNotBlank() && !isCreating,
                                    onClick = {
                                        val createdName = newFolderName.trim()
                                        isCreating = true
                                        onCreateNewFolder(createdName, effectiveIsVirtual, targetParentVfId) { createdDest ->
                                            isCreating = false
                                            showInlineCreate = false
                                            newFolderName = ""
                                            if (createdDest != null) {
                                                onSelectDestination(createdDest)
                                            }
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    if (isCreating) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = when {
                                            isUploadDestinationPicker -> "${stringResource(R.string.create)} & Select"
                                            isCopy -> "${stringResource(R.string.create)} & ${stringResource(R.string.copy)}"
                                            else -> "${stringResource(R.string.create)} & ${stringResource(R.string.move)}"
                                        },
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                if (currentLevelFolders.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (currentBrowsedFolder == null) stringResource(R.string.no_folders_found)
                                else "No subfolders inside \"${currentBrowsedFolder.name}\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = when {
                                    isUploadDestinationPicker -> "Tap \"Select Folder\" below or \"+ New Folder\" above"
                                    isCopy -> "Tap \"Copy Here\" below or \"+ New Folder\" above"
                                    else -> "Tap \"Move Here\" below or \"+ New Folder\" above"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 310.dp)
                    ) {
                        items(currentLevelFolders) { folder ->
                            val accentColor = if (folder.isVirtual) Color(0xFFF59E0B) else Color(0xFF3B82F6)
                            val subfolderCount = remember(folder, cleanFolders) {
                                if (folder.isVirtual) {
                                    cleanFolders.count { it.isVirtual && it.virtualParentId == folder.virtualId }
                                } else {
                                    cleanFolders.count {
                                        val pid = it.virtualParentId
                                        it.isVirtual &&
                                            it.parentChatId == folder.telegramChatId &&
                                            (pid == null || pid == "0" || pid.isBlank())
                                    }
                                }
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showInlineCreate = false
                                        browsedFolderStack = browsedFolderStack + folder
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    SculptedFolderGraphic(
                                        isVirtual = folder.isVirtual,
                                        isSecure = folder.isSecure,
                                        modifier = Modifier.size(width = 40.dp, height = 34.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = folder.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val folderTypeLabel = if (folder.isVirtual) {
                                            stringResource(R.string.virtual_folder)
                                        } else {
                                            stringResource(R.string.channel_folder)
                                        }
                                        val subLabel = if (subfolderCount > 0) {
                                            "$subfolderCount subfolder${if (subfolderCount > 1) "s" else ""} • "
                                        } else ""
                                        Text(
                                            text = "$folderTypeLabel • $subLabel${folder.itemCount} files • ${formatSize(folder.totalSize)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "Open ›",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Current Destination Info Footer inside Dialog Body
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (currentBrowsedFolder == null) Icons.Default.Home else Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Destination: $currentDestinationPathLabel",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!canConfirmCurrentLocation) {
                                Text(
                                    text = "Files are already in this folder. Open a folder above or create a new folder.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canConfirmCurrentLocation,
                onClick = {
                    onSelectDestination(currentDestinationObject)
                }
            ) {
                Icon(
                    imageVector = when {
                        isUploadDestinationPicker -> Icons.Default.Check
                        isCopy -> Icons.Default.ContentCopy
                        else -> Icons.AutoMirrored.Filled.DriveFileMove
                    },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = when {
                        isUploadDestinationPicker -> "Select Folder"
                        isCopy -> "${stringResource(R.string.copy)} Here"
                        else -> "${stringResource(R.string.move)} Here"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DriveListItem(
    item: DriveItem,
    isSelected: Boolean,
    isTrashMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onStarClick: () -> Unit,
    onShareClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onToggleOfflineClick: (() -> Unit)? = null,
    onRenameClick: (() -> Unit)? = null,
    onMoveClick: (() -> Unit)? = null,
    onCopyClick: (() -> Unit)? = null,
    onChangeColorClick: (() -> Unit)? = null,
    onAddToHomeScreenClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null,
    onRestoreClick: (() -> Unit)? = null,
    onDeleteForeverClick: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val (icon, color) = getFileIconAndColor(item)
    val isSecure = (item is DriveItem.Folder && item.isSecure) || (item is DriveItem.File && item.isEncrypted)
    var showItemMenu by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }

    if (showInfoDialog) {
        InfoDialog(item = item, onDismiss = { showInfoDialog = false })
    }

    if (showItemMenu && onMoreClick == null) {
        GoogleDriveItemActionBottomSheet(
            item = item,
            isTrashMode = isTrashMode,
            onDismiss = { showItemMenu = false },
            onShareClick = onShareClick,
            onStarClick = onStarClick,
            onInfoClick = { showInfoDialog = true },
            onToggleOfflineClick = onToggleOfflineClick,
            onRenameClick = onRenameClick,
            onDownloadClick = onDownloadClick,
            onMoveClick = onMoveClick,
            onCopyClick = onCopyClick,
            onChangeColorClick = onChangeColorClick,
            onAddToHomeScreenClick = onAddToHomeScreenClick,
            onDeleteClick = onDeleteClick,
            onRestoreClick = onRestoreClick,
            onDeleteForeverClick = onDeleteForeverClick
        )
    }

    // Google Drive-style List View row for Folders
    if (item is DriveItem.Folder) {
        val folderColor = resolveFolderColor(item)
        val dateText = if (item.isTrashed && item.trashedAt != null) {
            val daysLeft = (((item.trashedAt + 30L * 24 * 60 * 60 * 1000L) - System.currentTimeMillis()) / (24L * 60 * 60 * 1000L)).coerceAtLeast(1L)
            " • $daysLeft days left in Trash"
        } else if (item.createdAt > 0L) {
            " • ${formatShortDate(item.createdAt)}"
        } else ""

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .fillMaxWidth()
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    else Color.Transparent
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(42.dp)
            ) {
                GoogleDriveFolderIcon(
                    color = folderColor,
                    isChannel = !item.isVirtual,
                    isSecure = item.isSecure,
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isSecure) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = folderColor
                        )
                    }
                    if (item.isOffline) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.OfflinePin,
                            contentDescription = "Available offline",
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (item.isStarred && !isTrashMode) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${item.itemCount} files • ${formatSize(item.totalSize)}$dateText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
            }

            IconButton(onClick = {
                if (onMoreClick != null) onMoreClick() else showItemMenu = true
            }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "More",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val thumbnailModel = if (item is DriveItem.File && !item.isEncrypted) {
        item.thumbnailPath ?: item.localPath
    } else null

    val listImageRequest = remember(thumbnailModel) {
        thumbnailModel?.let {
            ImageRequest.Builder(context)
                .data(it)
                .size(140)
                .crossfade(true)
                .build()
        }
    }

    ListItem(
        headlineContent = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (isSecure) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.Lock, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.outline)
                }
                if (item.isOffline) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Default.OfflinePin,
                        contentDescription = "Available offline",
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                if (item.isStarred && !isTrashMode) {
                    Spacer(Modifier.width(5.dp))
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        },
        supportingContent = {
            if (item is DriveItem.File) {
                val dateStr = if (item.isTrashed && item.trashedAt != null) {
                    val daysLeft = (((item.trashedAt + 30L * 24 * 60 * 60 * 1000L) - System.currentTimeMillis()) / (24L * 60 * 60 * 1000L)).coerceAtLeast(1L)
                    " • $daysLeft days left in Trash"
                } else if (item.createdAt > 0L) {
                    " • ${formatShortDate(item.createdAt)}"
                } else ""
                Text("${formatSize(item.size)}$dateStr", style = MaterialTheme.typography.bodySmall)
            }
        },
        leadingContent = {
            val isVideo = item is DriveItem.File && item.mimeType.startsWith("video/")
            Surface(
                color = color.copy(alpha = 0.1f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (listImageRequest != null) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.size(22.dp)
                            )
                            AsyncImage(
                                model = listImageRequest,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            if (isVideo) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = color
                        )
                    }
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSelected) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                IconButton(onClick = {
                    if (onMoreClick != null) onMoreClick() else showItemMenu = true
                }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                }
            }
        },
        modifier = modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleDriveItemActionBottomSheet(
    item: DriveItem,
    isTrashMode: Boolean = false,
    onDismiss: () -> Unit,
    onShareClick: () -> Unit,
    onStarClick: () -> Unit,
    onInfoClick: () -> Unit,
    onToggleOfflineClick: (() -> Unit)? = null,
    onRenameClick: (() -> Unit)? = null,
    onDownloadClick: () -> Unit,
    onMoveClick: (() -> Unit)? = null,
    onCopyClick: (() -> Unit)? = null,
    onChangeColorClick: (() -> Unit)? = null,
    onAddToHomeScreenClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null,
    onRestoreClick: (() -> Unit)? = null,
    onDeleteForeverClick: (() -> Unit)? = null
) {
    val isVirtual = item is DriveItem.Folder && item.isVirtual
    val isSecure = (item is DriveItem.Folder && item.isSecure) || (item is DriveItem.File && item.isEncrypted)
    val (icon, color) = getFileIconAndColor(item)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // Top Item Header (Icon + Title + Subtitle) — Matches Google Drive Bottom Sheet
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                if (item is DriveItem.Folder) {
                    GoogleDriveFolderIcon(
                        color = resolveFolderColor(item),
                        isChannel = !item.isVirtual,
                        isSecure = item.isSecure,
                        modifier = Modifier.size(38.dp)
                    )
                } else {
                    Surface(
                        color = color.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                        }
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitle = when (item) {
                        is DriveItem.Folder -> "${item.itemCount} files • ${formatSize(item.totalSize)}"
                        is DriveItem.File -> {
                            val d = if (item.createdAt > 0L) " • ${formatShortDate(item.createdAt)}" else ""
                            "${formatSize(item.size)}$d"
                        }
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                modifier = Modifier.padding(vertical = 6.dp)
            )

            if (isTrashMode) {
                onRestoreClick?.let {
                    BottomSheetActionRow(
                        icon = Icons.Default.RestoreFromTrash,
                        label = "Restore",
                        iconTint = MaterialTheme.colorScheme.primary,
                        onClick = { it(); onDismiss() }
                    )
                }
                BottomSheetActionRow(
                    icon = Icons.Default.Info,
                    label = stringResource(R.string.info),
                    onClick = { onInfoClick(); onDismiss() }
                )
                onDeleteForeverClick?.let {
                    BottomSheetActionRow(
                        icon = Icons.Default.DeleteForever,
                        label = "Delete forever",
                        iconTint = MaterialTheme.colorScheme.error,
                        textColor = MaterialTheme.colorScheme.error,
                        onClick = { it(); onDismiss() }
                    )
                }
            } else {
                if (!isVirtual && !isSecure) {
                    BottomSheetActionRow(
                        icon = Icons.Default.Share,
                        label = stringResource(R.string.share),
                        onClick = { onShareClick(); onDismiss() }
                    )
                }
                BottomSheetActionRow(
                    icon = if (item.isStarred) Icons.Default.Star else Icons.Outlined.StarOutline,
                    label = if (item.isStarred) stringResource(R.string.remove_star) else stringResource(R.string.add_star),
                    iconTint = if (item.isStarred) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { onStarClick(); onDismiss() }
                )
                onToggleOfflineClick?.let {
                    BottomSheetActionRow(
                        icon = if (item.isOffline) Icons.Default.OfflinePin else Icons.Outlined.OfflinePin,
                        label = if (item.isOffline) "Remove from offline" else "Make available offline",
                        iconTint = if (item.isOffline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { it(); onDismiss() }
                    )
                }
                onRenameClick?.let {
                    BottomSheetActionRow(
                        icon = Icons.Default.Edit,
                        label = stringResource(R.string.rename),
                        onClick = { it(); onDismiss() }
                    )
                }
                if (item is DriveItem.Folder) {
                    onChangeColorClick?.let {
                        BottomSheetActionRow(
                            icon = Icons.Default.Palette,
                            label = stringResource(R.string.change_color),
                            onClick = { it(); onDismiss() }
                        )
                    }
                    onAddToHomeScreenClick?.let {
                        BottomSheetActionRow(
                            icon = Icons.Default.PhoneAndroid,
                            label = stringResource(R.string.add_to_home_screen),
                            onClick = { it(); onDismiss() }
                        )
                    }
                }
                if (item is DriveItem.File) {
                    onCopyClick?.let {
                        BottomSheetActionRow(
                            icon = Icons.Default.ContentCopy,
                            label = stringResource(R.string.copy),
                            onClick = { it(); onDismiss() }
                        )
                    }
                    onMoveClick?.let {
                        BottomSheetActionRow(
                            icon = Icons.AutoMirrored.Filled.DriveFileMove,
                            label = stringResource(R.string.move),
                            onClick = { it(); onDismiss() }
                        )
                    }
                }
                BottomSheetActionRow(
                    icon = Icons.Default.Download,
                    label = if (item is DriveItem.Folder) stringResource(R.string.download_contents) else stringResource(R.string.download),
                    onClick = { onDownloadClick(); onDismiss() }
                )
                BottomSheetActionRow(
                    icon = Icons.Default.Info,
                    label = stringResource(R.string.info),
                    onClick = { onInfoClick(); onDismiss() }
                )
                onDeleteClick?.let {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    BottomSheetActionRow(
                        icon = Icons.Default.Delete,
                        label = "Move to Trash",
                        iconTint = MaterialTheme.colorScheme.error,
                        textColor = MaterialTheme.colorScheme.error,
                        onClick = { it(); onDismiss() }
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomSheetActionRow(
    icon: ImageVector,
    label: String,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 13.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(18.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Normal,
            color = textColor
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DriveGridItem(
    item: DriveItem,
    isSelected: Boolean,
    isTrashMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onStarClick: () -> Unit,
    onShareClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onToggleOfflineClick: (() -> Unit)? = null,
    onRenameClick: (() -> Unit)? = null,
    onMoveClick: (() -> Unit)? = null,
    onCopyClick: (() -> Unit)? = null,
    onChangeColorClick: (() -> Unit)? = null,
    onAddToHomeScreenClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null,
    onRestoreClick: (() -> Unit)? = null,
    onDeleteForeverClick: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showItemMenu by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    val (icon, color) = getFileIconAndColor(item)
    val isSecure = (item is DriveItem.Folder && item.isSecure) || (item is DriveItem.File && item.isEncrypted)

    if (showInfoDialog) {
        InfoDialog(item = item, onDismiss = { showInfoDialog = false })
    }

    if (showItemMenu && onMoreClick == null) {
        GoogleDriveItemActionBottomSheet(
            item = item,
            isTrashMode = isTrashMode,
            onDismiss = { showItemMenu = false },
            onShareClick = onShareClick,
            onStarClick = onStarClick,
            onInfoClick = { showInfoDialog = true },
            onToggleOfflineClick = onToggleOfflineClick,
            onRenameClick = onRenameClick,
            onDownloadClick = onDownloadClick,
            onMoveClick = onMoveClick,
            onCopyClick = onCopyClick,
            onChangeColorClick = onChangeColorClick,
            onAddToHomeScreenClick = onAddToHomeScreenClick,
            onDeleteClick = onDeleteClick,
            onRestoreClick = onRestoreClick,
            onDeleteForeverClick = onDeleteForeverClick
        )
    }

    // Google Drive-style Folder Card Design for Grid View (keeps file count & total size visible at bottom)
    if (item is DriveItem.Folder) {
        val folderColor = resolveFolderColor(item)
        val containerColor = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        }
        val borderColor = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        }

        Surface(
            color = containerColor,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
            modifier = modifier
                .fillMaxWidth()
                .height(138.dp)
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 10.dp)
                ) {
                    // 1. Top Header Row (Small Folder Icon + Folder Title + 3-Dot Menu) — Matches Google Drive Grid View
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = folderColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (item.isOffline) {
                            Icon(
                                Icons.Default.OfflinePin,
                                contentDescription = "Available offline",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                        }
                        if (item.isStarred && !isTrashMode) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                        }
                        if (isSecure) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = folderColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                        }
                        IconButton(
                            onClick = {
                                if (onMoreClick != null) onMoreClick() else showItemMenu = true
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // 2. Center Area: Large Google Drive Folder Icon
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        GoogleDriveFolderIcon(
                            color = folderColor,
                            isChannel = !item.isVirtual,
                            isSecure = item.isSecure,
                            modifier = Modifier.size(60.dp)
                        )
                    }

                    // 3. Bottom Metadata Row: File count & Total size (explicitly kept per user request)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = "${item.itemCount} files • ${formatSize(item.totalSize)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (isSelected) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .size(18.dp)
                    )
                }
            }
        }
        return
    }

    val thumbnailModel = if (item is DriveItem.File && !item.isEncrypted) {
        item.thumbnailPath ?: item.localPath
    } else null

    val gridImageRequest = remember(thumbnailModel) {
        thumbnailModel?.let {
            ImageRequest.Builder(context)
                .data(it)
                .size(320)
                .crossfade(true)
                .build()
        }
    }

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Box {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                val isVideo = item is DriveItem.File && item.mimeType.startsWith("video/")

                // Google Drive-style full-bleed top preview area filling the card edge-to-edge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(color.copy(alpha = 0.08f))
                ) {
                    if (gridImageRequest != null) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = color.copy(alpha = 0.6f)
                            )
                            AsyncImage(
                                model = gridImageRequest,
                                contentDescription = item.name,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Transparent),
                                contentScale = ContentScale.Crop
                            )
                            if (isVideo) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                            tint = color
                        )
                    }
                }

                // Compact single-line Google Drive-style footer with small filename and 3-dot menu
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 6.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (item.isOffline) {
                        Icon(
                            Icons.Default.OfflinePin,
                            contentDescription = "Available offline",
                            modifier = Modifier
                                .size(13.dp)
                                .padding(horizontal = 2.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (isSecure) {
                        Icon(
                            Icons.Default.Lock,
                            null,
                            modifier = Modifier
                                .size(12.dp)
                                .padding(horizontal = 2.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                    IconButton(
                        onClick = {
                            if (onMoreClick != null) onMoreClick() else showItemMenu = true
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                )
            }
        }
    }
}

@Composable
fun InfoDialog(item: DriveItem, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item is DriveItem.Folder) {
                    SculptedFolderGraphic(
                        isVirtual = item.isVirtual,
                        isSecure = item.isSecure,
                        modifier = Modifier.size(width = 32.dp, height = 26.dp)
                    )
                } else {
                    val (icon, color) = getFileIconAndColor(item)
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.item_info), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(14.dp)
                ) {
                    InfoRow(label = stringResource(R.string.name), value = item.name)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    if (item is DriveItem.File) {
                        InfoRow(label = stringResource(R.string.size), value = formatSize(item.size))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        InfoRow(label = stringResource(R.string.type), value = item.mimeType)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        InfoRow(
                            label = stringResource(R.string.sort_date),
                            value = formatFullDateTime(item.createdAt)
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        val statusText = buildString {
                            append(if (item.isOffline || item.localPath != null) stringResource(R.string.available_offline) else stringResource(R.string.cloud_only))
                            if (item.isEncrypted) append(" • Encrypted")
                        }
                        InfoRow(label = stringResource(R.string.status), value = statusText)
                    } else if (item is DriveItem.Folder) {
                        val folderTypeStr = if (item.isVirtual) {
                            "${stringResource(R.string.virtual_folder)} (Saved Messages)"
                        } else {
                            "${stringResource(R.string.channel_folder)} (Telegram Channel)"
                        }
                        InfoRow(label = stringResource(R.string.type), value = folderTypeStr)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        InfoRow(
                            label = "Created Date",
                            value = formatFullDateTime(item.createdAt)
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        InfoRow(
                            label = "Total Files",
                            value = "${item.itemCount} files"
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        InfoRow(
                            label = "Total Size",
                            value = formatSize(item.totalSize)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatFullDateTime(timestampMs: Long): String {
    if (timestampMs <= 0L) return "Unknown"
    return try {
        val sdf = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
        sdf.format(Date(timestampMs))
    } catch (_: Exception) {
        "Unknown"
    }
}

private val shortDateCache = android.util.LruCache<Long, String>(512)

private fun formatShortDate(timestampMs: Long): String {
    if (timestampMs <= 0L) return ""
    val dayBucket = timestampMs / (60_000L * 30L)
    shortDateCache.get(dayBucket)?.let { return it }
    return try {
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val formatted = sdf.format(Date(timestampMs))
        shortDateCache.put(dayBucket, formatted)
        formatted
    } catch (_: Exception) {
        ""
    }
}

@Composable
fun ShareDialog(
    item: DriveItem,
    onDismiss: () -> Unit,
    onGetInviteLink: (Long, (String?) -> Unit) -> Unit,
    onGetMembers: (Long, (List<TdApi.User>) -> Unit) -> Unit,
    onShareFile: (String, Long, (Boolean, String?) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var inviteLink by remember { mutableStateOf<String?>(null) }
    var members by remember { mutableStateOf<List<TdApi.User>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var shareToPhone by remember { mutableStateOf("") }
    var isSharingFile by remember { mutableStateOf(false) }

    LaunchedEffect(item) {
        if (item is DriveItem.Folder) {
            onGetInviteLink(item.telegramChatId) { link ->
                inviteLink = link
                onGetMembers(item.telegramChatId) { userList ->
                    members = userList
                    isLoading = false
                }
            }
        } else {
            isLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (item is DriveItem.Folder) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.share_item, item.name), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (item is DriveItem.Folder) {
                    if (isLoading) {
                        Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Text(stringResource(R.string.private_share_link), style = MaterialTheme.typography.labelLarge)
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    inviteLink ?: stringResource(R.string.link_gen_failed),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                IconButton(onClick = {
                                    inviteLink?.let {
                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Link Folder", it))
                                        Toast.makeText(context, context.getString(R.string.copy_success), Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Copy")
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.people_with_access), style = MaterialTheme.typography.labelLarge)
                        if (members.isEmpty()) {
                            Text(stringResource(R.string.no_one_joined), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        } else {
                            LazyColumn(Modifier.heightIn(max = 150.dp)) {
                                items(members) { user ->
                                    ListItem(
                                        headlineContent = { Text("${user.firstName} ${user.lastName}") },
                                        supportingContent = { Text(user.phoneNumber.ifEmpty { "@${user.usernames?.activeUsernames?.firstOrNull() ?: "no_username"}" }) },
                                        leadingContent = { Icon(Icons.Default.Person, null) }
                                    )
                                }
                            }
                        }
                    }
                } else if (item is DriveItem.File) {
                    Text(stringResource(R.string.share_to_telegram), style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(
                        value = shareToPhone,
                        onValueChange = { shareToPhone = it },
                        placeholder = { Text("+62...") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Phone, null) }
                    )
                    
                    if (isSharingFile) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            if (item is DriveItem.File) {
                Button(
                    onClick = {
                        if (shareToPhone.isNotBlank()) {
                            isSharingFile = true
                            onShareFile(shareToPhone, item.id) { success, error ->
                                isSharingFile = false
                                if (success) {
                                    Toast.makeText(context, context.getString(R.string.share_success), Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                } else {
                                    Toast.makeText(context, context.getString(R.string.failed_with_error, error ?: ""), Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    enabled = !isSharingFile && shareToPhone.isNotBlank()
                ) {
                    Text(stringResource(R.string.send))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}

@Composable
private fun getFileIconAndColor(item: DriveItem): Pair<ImageVector, Color> {
    if (item is DriveItem.Folder) {
        return when {
            item.isSecure -> Icons.Default.Folder to Color(0xFF2196F3) // Blue for secure
            item.isVirtual -> Icons.Default.Folder to MaterialTheme.colorScheme.tertiary
            else -> Icons.Default.Folder to MaterialTheme.colorScheme.primary
        }
    } else {
        val file = item as DriveItem.File
        return when {
            file.mimeType.startsWith("image/") -> Icons.Default.Image to Color(0xFF4CAF50)
            file.mimeType.startsWith("video/") -> Icons.Default.VideoFile to Color(0xFFE91E63)
            file.mimeType.startsWith("audio/") -> Icons.Default.AudioFile to Color(0xFFFF9800)
            file.mimeType == "application/pdf" -> Icons.Default.PictureAsPdf to Color(0xFFF44336)
            file.mimeType.contains("zip") || file.mimeType.contains("rar") || file.mimeType.contains("7z") -> Icons.Default.FolderZip to Color(0xFF9C27B0)
            file.mimeType.contains("text/") -> Icons.AutoMirrored.Filled.Article to Color(0xFF607D8B)
            file.isApkFile() -> Icons.Default.Android to Color(0xFF3DDC84)
            else -> Icons.AutoMirrored.Filled.InsertDriveFile to MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
}

private fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(size.toDouble()) / log10(1000.0)).toInt().coerceIn(0, units.lastIndex)
    return String.format(Locale.getDefault(), "%.1f %s", size / 1000.0.pow(digitGroups.toDouble()), units[digitGroups])
}

@Composable
fun FolderColorPickerDialog(
    folder: DriveItem.Folder,
    onDismiss: () -> Unit,
    onSelectColor: (String?) -> Unit
) {
    // Authentic Google Drive 20-color palette
    val googleDriveColors = remember {
        listOf(
            "#5F6368" to "Mouse Grey",
            "#AC725E" to "Chocolate",
            "#D06B64" to "Brick Red",
            "#F83A22" to "Cardinal",
            "#FA573C" to "Strawberry",
            "#FF7537" to "Mars Orange",
            "#FFAD46" to "Amber",
            "#FAD165" to "Macaroni",
            "#FBE983" to "Sand",
            "#B3DC6C" to "Lime",
            "#7BD148" to "Asparagus",
            "#16A765" to "Fern Green",
            "#42D692" to "Spearmint",
            "#92E1C0" to "Sea Foam",
            "#9FE1E7" to "Pool",
            "#9FC6E7" to "Denim",
            "#4986E7" to "Rainy Sky",
            "#9A9CFF" to "Blue Velvet",
            "#B99AFF" to "Lavender",
            "#A47AE2" to "Eggplant",
            "#CD74E6" to "Purple",
            "#F691B2" to "Bubble Gum",
            "#CCA6AC" to "Blush",
            "#CABDBF" to "Mountain"
        )
    }
    val currentHex = folder.colorHex?.uppercase()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GoogleDriveFolderIcon(
                    color = resolveFolderColor(folder),
                    isChannel = !folder.isVirtual,
                    isSecure = folder.isSecure,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = stringResource(R.string.change_color),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = folder.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
            ) {
                items(googleDriveColors) { (hex, _) ->
                    val parsedColor = remember(hex) { Color(android.graphics.Color.parseColor(hex)) }
                    val isSelected = currentHex == hex.uppercase()
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(parsedColor, CircleShape)
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Black.copy(alpha = 0.12f),
                                shape = CircleShape
                            )
                            .clickable { onSelectColor(hex) }
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelectColor(null) }) {
                Text("Default")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private fun pinFolderToHomeScreen(
    context: android.content.Context,
    folder: DriveItem.Folder,
    currentFolderId: Long?
) {
    try {
        val shortcutId = "telenest_folder_${if (folder.isVirtual) (folder.virtualId ?: folder.id) else folder.telegramChatId}"
        val targetChatId = if (folder.isVirtual) (currentFolderId ?: folder.parentChatId) else folder.telegramChatId
        val launchIntent = android.content.Intent(context, com.awd.teledrive.MainActivity::class.java).apply {
            action = android.content.Intent.ACTION_VIEW
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(com.awd.teledrive.MainActivity.EXTRA_SHORTCUT_IS_VIRTUAL, folder.isVirtual)
            putExtra(com.awd.teledrive.MainActivity.EXTRA_SHORTCUT_CHAT_ID, targetChatId)
            putExtra(com.awd.teledrive.MainActivity.EXTRA_SHORTCUT_VIRTUAL_ID, folder.virtualId ?: "0")
            putExtra(com.awd.teledrive.MainActivity.EXTRA_SHORTCUT_FOLDER_NAME, folder.name)
        }

        val folderColorInt = try {
            if (!folder.colorHex.isNullOrBlank()) android.graphics.Color.parseColor(folder.colorHex)
            else if (folder.isSecure) android.graphics.Color.parseColor("#14B8A6")
            else if (folder.isVirtual) android.graphics.Color.parseColor("#5F6368")
            else android.graphics.Color.parseColor("#4285F4")
        } catch (_: Exception) {
            android.graphics.Color.parseColor("#4285F4")
        }

        // Create a crisp 192x192 Google Drive-style folder icon bitmap for the Home Screen shortcut
        val sizePx = 192
        val bitmap = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

        // Soft rounded white/dark-tinted card background
        paint.color = android.graphics.Color.parseColor("#F1F3F4")
        canvas.drawRoundRect(12f, 12f, 180f, 180f, 42f, 42f, paint)

        // Darker folder tab
        val r = ((folderColorInt shr 16 and 0xFF) * 0.82f).toInt()
        val g = ((folderColorInt shr 8 and 0xFF) * 0.82f).toInt()
        val b = ((folderColorInt and 0xFF) * 0.82f).toInt()
        paint.color = android.graphics.Color.rgb(r, g, b)
        canvas.drawRoundRect(36f, 46f, 108f, 92f, 14f, 14f, paint)

        // Main folder body
        paint.color = folderColorInt
        canvas.drawRoundRect(36f, 62f, 156f, 146f, 16f, 16f, paint)

        var pinned = false
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val shortcutManager = context.getSystemService(android.content.pm.ShortcutManager::class.java)
            if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported) {
                val shortcutInfo = android.content.pm.ShortcutInfo.Builder(context, shortcutId)
                    .setShortLabel(folder.name.take(18).ifBlank { "Folder" })
                    .setLongLabel(folder.name.ifBlank { "Folder" })
                    .setIcon(android.graphics.drawable.Icon.createWithBitmap(bitmap))
                    .setIntent(launchIntent)
                    .build()
                pinned = shortcutManager.requestPinShortcut(shortcutInfo, null)
            }
        }
        if (!pinned) {
            @Suppress("DEPRECATION")
            val installIntent = android.content.Intent("com.android.launcher.action.INSTALL_SHORTCUT").apply {
                putExtra(android.content.Intent.EXTRA_SHORTCUT_INTENT, launchIntent)
                putExtra(android.content.Intent.EXTRA_SHORTCUT_NAME, folder.name.ifBlank { "Folder" })
                putExtra(android.content.Intent.EXTRA_SHORTCUT_ICON, bitmap)
                putExtra("duplicate", false)
            }
            context.sendBroadcast(installIntent)
        }
        Toast.makeText(
            context,
            context.getString(R.string.shortcut_added_toast, folder.name),
            Toast.LENGTH_SHORT
        ).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to add shortcut: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
