package com.awd.teledrive.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.awd.teledrive.data.repository.DriveRepository
import com.awd.teledrive.domain.model.AutosyncGlobalSettings
import com.awd.teledrive.domain.model.SyncActiveStatus
import com.awd.teledrive.domain.model.SyncFolderPair
import com.awd.teledrive.domain.model.SyncMethod
import java.io.File

private enum class AutosyncSubScreen {
    SYNCED_FOLDERS,
    EDIT_PAIR,
    PICK_REMOTE_FOLDER,
    PICK_LOCAL_FOLDER,
    SYNC_SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSettingsScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val folderPairs by viewModel.folderPairs.collectAsState()
    val globalSettings by viewModel.globalSettings.collectAsState()
    val activeStatus by viewModel.activeStatus.collectAsState()
    val remoteFolders by viewModel.remoteFolderOptions.collectAsState()
    val isLoadingRemoteFolders by viewModel.isLoadingRemoteFolders.collectAsState()

    var currentSubScreen by remember { mutableStateOf(AutosyncSubScreen.SYNCED_FOLDERS) }
    var draftPair by remember { mutableStateOf(SyncFolderPair()) }
    var isCreatingNewPair by remember { mutableStateOf(false) }
    var syncSettingsReturnScreen by remember { mutableStateOf(AutosyncSubScreen.SYNCED_FOLDERS) }

    var permissionRefreshTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionRefreshTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isAllFilesAccessGranted = remember(permissionRefreshTick) {
        viewModel.isAllFilesAccessGranted()
    }
    val isBatteryOptimizationIgnored = remember(permissionRefreshTick) {
        isIgnoringBatteryOptimizations(context)
    }

    var pendingSyncAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showMobileWarnDialog by remember { mutableStateOf(false) }

    fun executeWithMobileDataCheck(action: () -> Unit) {
        if (globalSettings.warnOnMobileNetwork && viewModel.isCurrentlyOnMobileData()) {
            pendingSyncAction = action
            showMobileWarnDialog = true
        } else {
            action()
        }
    }

    if (showMobileWarnDialog) {
        AlertDialog(
            onDismissRequest = {
                showMobileWarnDialog = false
                pendingSyncAction = null
            },
            title = { Text("Sync on mobile network?") },
            text = {
                Text("You are currently connected to mobile data. Syncing large files may consume your mobile data plan. Continue anyway?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showMobileWarnDialog = false
                        pendingSyncAction?.invoke()
                        pendingSyncAction = null
                    }
                ) {
                    Text("Sync now")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showMobileWarnDialog = false
                        pendingSyncAction = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    when (currentSubScreen) {
        AutosyncSubScreen.SYNCED_FOLDERS -> {
            SyncedFoldersMainView(
                folderPairs = folderPairs,
                globalSettings = globalSettings,
                activeStatus = activeStatus,
                isAllFilesAccessGranted = isAllFilesAccessGranted,
                isBatteryOptimizationIgnored = isBatteryOptimizationIgnored,
                onBack = onBack,
                onOpenSyncSettings = {
                    syncSettingsReturnScreen = AutosyncSubScreen.SYNCED_FOLDERS
                    currentSubScreen = AutosyncSubScreen.SYNC_SETTINGS
                },
                onAddFolderPair = {
                    viewModel.refreshRemoteFolders()
                    draftPair = SyncFolderPair(
                        id = "pair_${System.nanoTime()}",
                        name = "",
                        localPath = "",
                        remotePathDisplay = "",
                        remoteChatId = 0L,
                        remoteVirtualFolderId = "0",
                        syncMethod = SyncMethod.TWO_WAY,
                        excludeHiddenFiles = true,
                        excludeSubfolders = false,
                        deleteEmptySubfolders = false,
                        instantUpload = true,
                        selectFilesByName = false,
                        useDefaultSyncOptions = true,
                        enabled = true
                    )
                    isCreatingNewPair = true
                    currentSubScreen = AutosyncSubScreen.EDIT_PAIR
                },
                onEditFolderPair = { pair ->
                    viewModel.refreshRemoteFolders()
                    draftPair = pair
                    isCreatingNewPair = false
                    currentSubScreen = AutosyncSubScreen.EDIT_PAIR
                },
                onTogglePairEnabled = { pairId, enabled ->
                    viewModel.toggleFolderPairEnabled(pairId, enabled)
                },
                onSyncSinglePair = { pairId ->
                    executeWithMobileDataCheck {
                        viewModel.syncSinglePairNow(pairId)
                        Toast.makeText(context, "Sync started", Toast.LENGTH_SHORT).show()
                    }
                },
                onSyncAllPairs = {
                    if (folderPairs.isEmpty()) {
                        Toast.makeText(context, "Please add at least one folder pair first", Toast.LENGTH_SHORT).show()
                    } else {
                        executeWithMobileDataCheck {
                            viewModel.triggerManualBackup()
                            Toast.makeText(context, "Syncing all folders...", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onCancelActiveSync = {
                    viewModel.cancelActiveSync()
                },
                onResetPairState = { pairId ->
                    viewModel.resetFolderPairSyncState(pairId)
                    Toast.makeText(context, "Sync state reset", Toast.LENGTH_SHORT).show()
                },
                onDeletePair = { pairId ->
                    viewModel.deleteFolderPair(pairId)
                    Toast.makeText(context, "Folder pair deleted", Toast.LENGTH_SHORT).show()
                },
                onToggleMasterAutosync = { enabled ->
                    viewModel.setAutoBackupEnabled(enabled)
                },
                onRequestAllFilesAccess = {
                    requestAllFilesAccessPermission(context)
                },
                onRequestIgnoreBatteryOptimizations = {
                    requestIgnoreBatteryOptimizations(context)
                }
            )
        }

        AutosyncSubScreen.EDIT_PAIR -> {
            BackHandler {
                currentSubScreen = AutosyncSubScreen.SYNCED_FOLDERS
            }
            FolderPairEditorView(
                draftPair = draftPair,
                isNewPair = isCreatingNewPair,
                onUpdateDraft = { updated -> draftPair = updated },
                onOpenRemoteFolderPicker = {
                    viewModel.refreshRemoteFolders()
                    currentSubScreen = AutosyncSubScreen.PICK_REMOTE_FOLDER
                },
                onOpenLocalFolderPicker = {
                    currentSubScreen = AutosyncSubScreen.PICK_LOCAL_FOLDER
                },
                onSave = { savedPair ->
                    viewModel.saveFolderPair(savedPair)
                    Toast.makeText(context, "Folder pair saved", Toast.LENGTH_SHORT).show()
                    currentSubScreen = AutosyncSubScreen.SYNCED_FOLDERS
                },
                onDelete = { pairId ->
                    viewModel.deleteFolderPair(pairId)
                    Toast.makeText(context, "Folder pair deleted", Toast.LENGTH_SHORT).show()
                    currentSubScreen = AutosyncSubScreen.SYNCED_FOLDERS
                },
                onOpenSyncSettings = {
                    syncSettingsReturnScreen = AutosyncSubScreen.EDIT_PAIR
                    currentSubScreen = AutosyncSubScreen.SYNC_SETTINGS
                },
                onClose = {
                    currentSubScreen = AutosyncSubScreen.SYNCED_FOLDERS
                }
            )
        }

        AutosyncSubScreen.PICK_REMOTE_FOLDER -> {
            BackHandler {
                currentSubScreen = AutosyncSubScreen.EDIT_PAIR
            }
            FullScreenRemoteFolderPicker(
                remoteFolders = remoteFolders,
                isLoading = isLoadingRemoteFolders,
                currentPath = draftPair.remotePathDisplay,
                onRefresh = viewModel::refreshRemoteFolders,
                onCreateFolder = { name, parentOpt, onCreated ->
                    viewModel.createNewRemoteFolder(name, parentOpt, onCreated)
                },
                onSelect = { selected ->
                    draftPair = draftPair.copy(
                        remotePathDisplay = selected.fullPath,
                        remoteChatId = selected.chatId,
                        remoteVirtualFolderId = selected.virtualFolderId
                    )
                    currentSubScreen = AutosyncSubScreen.EDIT_PAIR
                },
                onCancel = {
                    currentSubScreen = AutosyncSubScreen.EDIT_PAIR
                }
            )
        }

        AutosyncSubScreen.PICK_LOCAL_FOLDER -> {
            BackHandler {
                currentSubScreen = AutosyncSubScreen.EDIT_PAIR
            }
            FullScreenLocalFolderPicker(
                initialPath = draftPair.localPath,
                onCreateLocalFolder = { parentDir, folderName ->
                    viewModel.createNewLocalFolder(parentDir, folderName)
                },
                onSelectPath = { chosenPath ->
                    draftPair = draftPair.copy(localPath = chosenPath)
                    currentSubScreen = AutosyncSubScreen.EDIT_PAIR
                },
                onCancel = {
                    currentSubScreen = AutosyncSubScreen.EDIT_PAIR
                }
            )
        }

        AutosyncSubScreen.SYNC_SETTINGS -> {
            BackHandler {
                currentSubScreen = syncSettingsReturnScreen
            }
            SynchronizationSettingsView(
                settings = globalSettings,
                isBatteryOptimizationIgnored = isBatteryOptimizationIgnored,
                onUpdateSettings = { transform ->
                    viewModel.updateGlobalSettings(transform)
                },
                onRequestBatteryExemption = {
                    requestIgnoreBatteryOptimizations(context)
                },
                onBack = {
                    currentSubScreen = syncSettingsReturnScreen
                }
            )
        }
    }
}

// =========================================================================
// 1. SYNCED FOLDERS MAIN VIEW
// =========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncedFoldersMainView(
    folderPairs: List<SyncFolderPair>,
    globalSettings: AutosyncGlobalSettings,
    activeStatus: SyncActiveStatus,
    isAllFilesAccessGranted: Boolean,
    isBatteryOptimizationIgnored: Boolean,
    onBack: () -> Unit,
    onOpenSyncSettings: () -> Unit,
    onAddFolderPair: () -> Unit,
    onEditFolderPair: (SyncFolderPair) -> Unit,
    onTogglePairEnabled: (String, Boolean) -> Unit,
    onSyncSinglePair: (String) -> Unit,
    onSyncAllPairs: () -> Unit,
    onCancelActiveSync: () -> Unit,
    onResetPairState: (String) -> Unit,
    onDeletePair: (String) -> Unit,
    onToggleMasterAutosync: (Boolean) -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onRequestIgnoreBatteryOptimizations: () -> Unit
) {
    var pairToDelete by remember { mutableStateOf<SyncFolderPair?>(null) }

    if (pairToDelete != null) {
        val target = pairToDelete!!
        AlertDialog(
            onDismissRequest = { pairToDelete = null },
            title = { Text("Delete folder pair?") },
            text = {
                Text("Remove '${target.effectiveTitle}' from Synced folders? Your actual files on phone and cloud will NOT be deleted.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePair(target.id)
                        pairToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pairToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Synced folders",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                modifier = Modifier.statusBarsPadding(),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = onSyncAllPairs,
                        enabled = !activeStatus.isRunning
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync all now")
                    }
                    IconButton(onClick = onOpenSyncSettings) {
                        Icon(Icons.Default.Tune, contentDescription = "Synchronization settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddFolderPair,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add folder pair")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Master Autosync Header Card
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Enable autosync",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (globalSettings.enableAutosync) {
                                        "${AutosyncGlobalSettings.formatSchedule(globalSettings.scheduleIntervalMinutes)} • ${if (globalSettings.wifiOnly) "Wi-Fi only" else "Wi-Fi & Mobile"} • Min battery ${globalSettings.minBatteryLevel}%"
                                    } else {
                                        "Monitor folders and sync in background"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = globalSettings.enableAutosync,
                                onCheckedChange = onToggleMasterAutosync
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = onOpenSyncSettings,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sync Settings", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Button(
                                onClick = onSyncAllPairs,
                                enabled = !activeStatus.isRunning && folderPairs.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sync All Now", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            // Permission Helper Banners
            if (!isAllFilesAccessGranted) {
                item {
                    OutlinedCard(
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRequestAllFilesAccess() }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Grant All Files Access for Full Folder Sync",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Required to sync all file types and subfolders seamlessly.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = onRequestAllFilesAccess) {
                                Text("Allow")
                            }
                        }
                    }
                }
            }

            if (globalSettings.enableAutosync && !isBatteryOptimizationIgnored) {
                item {
                    OutlinedCard(
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRequestIgnoreBatteryOptimizations() }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.BatteryChargingFull,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Disable Battery Saver Restriction",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Allow TeleNest to sync reliably in the background.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Active Sync Progress Banner
            if (activeStatus.isRunning) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (activeStatus.activePairName.isNotEmpty()) {
                                        "${activeStatus.currentAction} • ${activeStatus.activePairName}"
                                    } else {
                                        activeStatus.currentAction.ifEmpty { "Syncing..." }
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                if (activeStatus.totalCount > 0) {
                                    Text(
                                        text = "${activeStatus.processedCount}/${activeStatus.totalCount}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { activeStatus.progress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = activeStatus.currentFileName.ifEmpty { activeStatus.statusMessage },
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = onCancelActiveSync) {
                                    Text("Cancel")
                                }
                            }
                        }
                    }
                }
            }

            // Empty state or Folder Pair cards
            if (folderPairs.isEmpty()) {
                item {
                    OutlinedCard(
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No synced folder pairs yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap + below to pair a local folder on your device with a remote folder in your Telegram Cloud Drive.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(onClick = onAddFolderPair) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add folder pair")
                            }
                        }
                    }
                }
            } else {
                items(folderPairs, key = { it.id }) { pair ->
                    FolderPairCardItem(
                        pair = pair,
                        onEdit = { onEditFolderPair(pair) },
                        onSyncNow = { onSyncSinglePair(pair.id) },
                        onResetState = { onResetPairState(pair.id) },
                        onDelete = { pairToDelete = pair },
                        onToggleEnabled = { enabled -> onTogglePairEnabled(pair.id, enabled) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderPairCardItem(
    pair: SyncFolderPair,
    onEdit: () -> Unit,
    onSyncNow: () -> Unit,
    onResetState: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    OutlinedCard(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = pair.effectiveTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Sync now") },
                            leadingIcon = { Icon(Icons.Default.Sync, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onSyncNow()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Edit folder pair") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Reset sync history") },
                            leadingIcon = { Icon(Icons.Default.RestartAlt, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onResetState()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Remote",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = pair.formattedRemotePath.ifBlank { "/" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Local",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = pair.formattedLocalPath,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Sync method",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = pair.syncMethod.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (pair.lastSyncStatus.isNotBlank()) {
                            pair.lastSyncStatus
                        } else if (pair.enabled) {
                            "Folder pair enabled"
                        } else {
                            "Folder pair disabled"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = pair.enabled,
                    onCheckedChange = onToggleEnabled,
                    modifier = Modifier.scale(0.85f)
                )
            }
        }
    }
}

// =========================================================================
// 2. FOLDER PAIR EDITOR VIEW (Matches Screenshot 7 & Fits on 1 Screen)
// =========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderPairEditorView(
    draftPair: SyncFolderPair,
    isNewPair: Boolean,
    onUpdateDraft: (SyncFolderPair) -> Unit,
    onOpenRemoteFolderPicker: () -> Unit,
    onOpenLocalFolderPicker: () -> Unit,
    onSave: (SyncFolderPair) -> Unit,
    onDelete: (String) -> Unit,
    onOpenSyncSettings: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var showSyncMethodDialog by remember { mutableStateOf(false) }
    var showTopOverflowMenu by remember { mutableStateOf(false) }
    var showGuideDialog by remember { mutableStateOf(false) }

    if (showSyncMethodDialog) {
        SyncMethodBoxedPickerDialog(
            selectedMethod = draftPair.syncMethod,
            onSelect = { chosen ->
                onUpdateDraft(draftPair.copy(syncMethod = chosen))
                showSyncMethodDialog = false
            },
            onDismiss = { showSyncMethodDialog = false }
        )
    }

    if (showGuideDialog) {
        AlertDialog(
            onDismissRequest = { showGuideDialog = false },
            title = { Text("Online User's Guide") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SyncMethod.entries.forEach { method ->
                        OutlinedCard(
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = method.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = method.descriptionEn,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGuideDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Folder pair") },
                modifier = Modifier.statusBarsPadding(),
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (draftPair.remotePathDisplay.isBlank()) {
                                Toast.makeText(context, "Please select a remote folder in cloud storage", Toast.LENGTH_SHORT).show()
                                return@TextButton
                            }
                            if (draftPair.localPath.isBlank()) {
                                Toast.makeText(context, "Please select a local folder in device", Toast.LENGTH_SHORT).show()
                                return@TextButton
                            }
                            val resolvedName = draftPair.name.trim().ifBlank {
                                val leaf = draftPair.localPath.trimEnd('/').substringAfterLast('/')
                                if (leaf.isBlank() || leaf == "0") {
                                    draftPair.remotePathDisplay.trim('/').substringAfterLast('/').ifBlank { "Internal storage" }
                                } else {
                                    leaf
                                }
                            }
                            onSave(
                                draftPair.copy(
                                    name = resolvedName,
                                    excludeHiddenFiles = true,
                                    excludeSubfolders = false,
                                    instantUpload = true
                                )
                            )
                        }
                    ) {
                        Text(
                            text = "SAVE",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (!isNewPair) {
                        Box {
                            IconButton(onClick = { showTopOverflowMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(
                                expanded = showTopOverflowMenu,
                                onDismissRequest = { showTopOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Delete folder pair", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showTopOverflowMenu = false
                                        onDelete(draftPair.id)
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Folder pair name
            OutlinedTextField(
                value = draftPair.name,
                onValueChange = { onUpdateDraft(draftPair.copy(name = it)) },
                placeholder = { Text("Folder pair name") },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // 2. Remote folder in cloud storage selector box (Empty by default!)
            val remoteDisplayValue = when {
                draftPair.remotePathDisplay.isBlank() -> ""
                draftPair.remotePathDisplay == "/" -> "/ (Root - My Drive)"
                else -> draftPair.remotePathDisplay
            }
            SelectorBoxField(
                icon = Icons.Default.Dns,
                label = "Remote folder in cloud storage",
                value = remoteDisplayValue,
                showDropdownArrow = false,
                onClick = onOpenRemoteFolderPicker
            )

            // 3. Local folder in device selector box (Empty by default!)
            val formattedLocal = when {
                draftPair.localPath.isBlank() -> ""
                draftPair.localPath == "/storage/emulated/0" || draftPair.localPath == "/storage/emulated/0/" -> "[Internal storage]"
                draftPair.localPath.startsWith("/storage/emulated/0/") -> "[Internal storage]/${draftPair.localPath.removePrefix("/storage/emulated/0/")}"
                else -> draftPair.localPath
            }
            SelectorBoxField(
                icon = Icons.Default.PhoneAndroid,
                label = "Local folder in device",
                value = formattedLocal,
                showDropdownArrow = false,
                onClick = onOpenLocalFolderPicker
            )

            // 4. Sync method selector with dropdown arrow icon on right
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Sync method",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SelectorBoxField(
                    icon = Icons.Default.Sync,
                    label = "Sync method",
                    value = draftPair.syncMethod.displayName,
                    showLabelWhenPopulated = false,
                    showDropdownArrow = true,
                    onClick = { showSyncMethodDialog = true }
                )
            }

            // 5. Use default sync options
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onUpdateDraft(draftPair.copy(useDefaultSyncOptions = !draftPair.useDefaultSyncOptions))
                    }
                    .padding(vertical = 2.dp)
            ) {
                Checkbox(
                    checked = draftPair.useDefaultSyncOptions,
                    onCheckedChange = { checked ->
                        onUpdateDraft(draftPair.copy(useDefaultSyncOptions = checked))
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Use default sync options",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Default sync options are set in ",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "the app settings",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { onOpenSyncSettings() }
                )
            }

            HorizontalDivider()

            // 6. Folder pair enabled switch
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onUpdateDraft(draftPair.copy(enabled = !draftPair.enabled))
                    }
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "Folder pair enabled",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = draftPair.enabled,
                    onCheckedChange = { checked ->
                        onUpdateDraft(draftPair.copy(enabled = checked))
                    }
                )
            }

            HorizontalDivider()

            // 7. Online User's Guide
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showGuideDialog = true }
                    .padding(vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Online User's Guide",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline
                )
            }
        }
    }
}

@Composable
private fun SelectorBoxField(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    showLabelWhenPopulated: Boolean = true,
    showDropdownArrow: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (value.isNotBlank() && showLabelWhenPopulated) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = value.ifBlank { label },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (showDropdownArrow) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Select option",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// =========================================================================
// 2B. BOXED SYNC METHOD PICKER DIALOG (Each option in its own box!)
// =========================================================================

@Composable
private fun SyncMethodBoxedPickerDialog(
    selectedMethod: SyncMethod,
    onSelect: (SyncMethod) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Select sync method",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                SyncMethod.entries.forEach { method ->
                    val isSelected = method == selectedMethod
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(method) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onSelect(method) },
                                modifier = Modifier
                                    .size(22.dp)
                                    .scale(0.9f)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = method.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = method.shortSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// 3. FULL-SCREEN REMOTE CLOUD FOLDER PICKER & CREATOR
// =========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullScreenRemoteFolderPicker(
    remoteFolders: List<DriveRepository.RemoteFolderOption>,
    isLoading: Boolean,
    currentPath: String,
    onRefresh: () -> Unit,
    onCreateFolder: (String, DriveRepository.RemoteFolderOption?, (DriveRepository.RemoteFolderOption) -> Unit) -> Unit,
    onSelect: (DriveRepository.RemoteFolderOption) -> Unit,
    onCancel: () -> Unit
) {
    val rootOption = remember(remoteFolders) {
        remoteFolders.firstOrNull { it.folderKey == "ROOT" || it.fullPath == "/" }
            ?: DriveRepository.RemoteFolderOption(
                displayName = "/ (Root - My Drive)",
                fullPath = "/",
                chatId = 0L,
                virtualFolderId = "0",
                parentVirtualId = "0",
                folderKey = "ROOT",
                parentFolderKey = ""
            )
    }

    var searchQuery by remember { mutableStateOf("") }
    var currentFolder by remember {
        val cleanTarget = currentPath.trim()
        val initial = if (cleanTarget.isNotEmpty() && cleanTarget != "/") {
            remoteFolders.firstOrNull { it.fullPath.equals(cleanTarget, ignoreCase = true) } ?: rootOption
        } else {
            rootOption
        }
        mutableStateOf(initial)
    }
    var hasInitializedFromPath by remember { mutableStateOf(currentFolder.folderKey != "ROOT" || currentPath.isBlank() || currentPath == "/") }

    LaunchedEffect(remoteFolders, currentPath) {
        if (remoteFolders.isNotEmpty()) {
            // Keep currentFolder synced with refreshed remoteFolders
            val updatedCurrent = remoteFolders.firstOrNull { it.folderKey == currentFolder.folderKey }
                ?: remoteFolders.firstOrNull { it.fullPath.equals(currentFolder.fullPath, ignoreCase = true) }
            if (updatedCurrent != null) {
                currentFolder = updatedCurrent
            }
            if (!hasInitializedFromPath && currentPath.isNotBlank() && currentPath != "/") {
                val match = remoteFolders.firstOrNull { it.fullPath.equals(currentPath.trim(), ignoreCase = true) }
                if (match != null) {
                    currentFolder = match
                    hasInitializedFromPath = true
                }
            }
        }
    }

    val folderByKey = remember(remoteFolders, rootOption) {
        val map = LinkedHashMap<String, DriveRepository.RemoteFolderOption>()
        map["ROOT"] = rootOption
        remoteFolders.forEach { map[it.folderKey] = it }
        map
    }

    // Build breadcrumb chain from ROOT to currentFolder
    val breadcrumbs = remember(currentFolder, folderByKey, rootOption) {
        val chain = mutableListOf<DriveRepository.RemoteFolderOption>()
        var cursor: DriveRepository.RemoteFolderOption? = currentFolder
        val visited = HashSet<String>()
        while (cursor != null && visited.add(cursor.folderKey)) {
            chain.add(0, cursor)
            if (cursor.folderKey == "ROOT" || cursor.parentFolderKey.isBlank()) break
            cursor = folderByKey[cursor.parentFolderKey] ?: rootOption
        }
        if (chain.isEmpty() || chain.first().folderKey != "ROOT") {
            chain.add(0, rootOption)
        }
        chain
    }

    // Direct children of currentFolder (strictly 1 level deep!)
    val directChildFolders = remember(remoteFolders, currentFolder) {
        val seenNames = HashSet<String>()
        remoteFolders
            .filter { option ->
                option.folderKey != "ROOT" &&
                    option.fullPath != "/" &&
                    option.parentFolderKey == currentFolder.folderKey
            }
            .sortedBy { it.displayName.trim().lowercase() }
            .filter { option ->
                seenNames.add(option.displayName.trim().lowercase())
            }
    }

    // Count of direct subfolders per folderKey so user can see which folders contain inner subfolders
    val childCountByFolderKey = remember(remoteFolders) {
        val counts = HashMap<String, Int>()
        val seenPairs = HashSet<String>()
        remoteFolders.forEach { opt ->
            if (opt.folderKey != "ROOT" && opt.parentFolderKey.isNotBlank()) {
                val dedupKey = "${opt.parentFolderKey}::${opt.displayName.trim().lowercase()}"
                if (seenPairs.add(dedupKey)) {
                    counts[opt.parentFolderKey] = (counts[opt.parentFolderKey] ?: 0) + 1
                }
            }
        }
        counts
    }

    val searchResults = remember(remoteFolders, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            emptyList()
        } else {
            remoteFolders.filter {
                it.folderKey != "ROOT" &&
                    (it.displayName.contains(q, ignoreCase = true) || it.fullPath.contains(q, ignoreCase = true))
            }.sortedBy { it.fullPath.lowercase() }
        }
    }

    fun navigateUpOneLevel() {
        if (searchQuery.isNotBlank()) {
            searchQuery = ""
            return
        }
        if (currentFolder.folderKey != "ROOT") {
            val parent = folderByKey[currentFolder.parentFolderKey] ?: rootOption
            currentFolder = parent
        } else {
            onCancel()
        }
    }

    // Intercept system back button to go up one folder level when inside a subfolder
    BackHandler {
        navigateUpOneLevel()
    }

    var showCreateSubDialog by remember { mutableStateOf(false) }
    var newFolderNameInput by remember { mutableStateOf("") }

    val formattedCurrentFolder = remember(currentFolder) {
        if (currentFolder.folderKey == "ROOT" || currentFolder.fullPath == "/") {
            "/ (Root - My Drive)"
        } else {
            currentFolder.fullPath
        }
    }

    if (showCreateSubDialog) {
        AlertDialog(
            onDismissRequest = { showCreateSubDialog = false },
            title = { Text("Create New Cloud Folder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Inside: $formattedCurrentFolder",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newFolderNameInput,
                        onValueChange = { newFolderNameInput = it },
                        label = { Text("New folder name") },
                        placeholder = { Text("e.g. Camera, Documents, Backup") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newFolderNameInput.trim()
                        if (name.isNotEmpty()) {
                            showCreateSubDialog = false
                            onCreateFolder(name, currentFolder) { created ->
                                currentFolder = created
                            }
                            newFolderNameInput = ""
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateSubDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Select Cloud Folder",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formattedCurrentFolder,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                modifier = Modifier.statusBarsPadding(),
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { navigateUpOneLevel() },
                        enabled = currentFolder.folderKey != "ROOT"
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up Folder")
                    }
                    IconButton(onClick = { showCreateSubDialog = true }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "Create New Cloud Folder")
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 4.dp,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Selected: $formattedCurrentFolder",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = { onSelect(currentFolder) },
                            modifier = Modifier.weight(1.5f)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select This Folder")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search cloud folders...") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Clickable Breadcrumb bar (shows current folder hierarchy clearly)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                breadcrumbs.forEachIndexed { index, crumb ->
                    val isCurrent = crumb.folderKey == currentFolder.folderKey
                    val label = if (crumb.folderKey == "ROOT" || crumb.fullPath == "/") {
                        "My Drive (/)"
                    } else {
                        crumb.displayName
                    }
                    AssistChip(
                        onClick = {
                            searchQuery = ""
                            currentFolder = crumb
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (crumb.folderKey == "ROOT") Icons.Default.Cloud else Icons.Default.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                    if (index < breadcrumbs.lastIndex) {
                        Text(
                            text = "›",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Up Folder & New Folder action row (identical feel to Local Folder Picker)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { navigateUpOneLevel() },
                    enabled = currentFolder.folderKey != "ROOT" || searchQuery.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Up Folder")
                }
                OutlinedButton(
                    onClick = { showCreateSubDialog = true },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Folder")
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (searchQuery.isNotBlank()) {
                // Search mode: show matching folders with option to open or select
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    if (searchResults.isEmpty()) {
                        item {
                            OutlinedCard(
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "No cloud folders matching '$searchQuery'",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    } else {
                        items(searchResults, key = { it.folderKey }) { option ->
                            val subCount = childCountByFolderKey[option.folderKey] ?: 0
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        searchQuery = ""
                                        currentFolder = option
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 11.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = option.displayName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (subCount > 0) "${option.fullPath} • $subCount subfolder(s)" else option.fullPath,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    TextButton(
                                        onClick = { onSelect(option) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("Select", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Hierarchical directory mode: ONLY show direct subfolders of currentFolder!
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    if (directChildFolders.isEmpty()) {
                        item {
                            OutlinedCard(
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = if (currentFolder.folderKey == "ROOT") {
                                            "No cloud folders in My Drive yet"
                                        } else {
                                            "No subfolders inside '${currentFolder.displayName}'"
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Tap 'Select This Folder' below to sync here, or 'New Folder' to create a subfolder.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(directChildFolders, key = { it.folderKey }) { option ->
                            val subCount = childCountByFolderKey[option.folderKey] ?: 0
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // Navigate INSIDE this folder to show its subfolders!
                                        currentFolder = option
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 11.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = option.displayName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (subCount > 0) {
                                                "$subCount subfolder${if (subCount > 1) "s" else ""} • Tap to open"
                                            } else {
                                                "Tap to open folder"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    TextButton(
                                        onClick = { onSelect(option) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("Select", style = MaterialTheme.typography.labelMedium)
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

// =========================================================================
// 4. FULL-SCREEN LOCAL DEVICE FOLDER BROWSER & CREATOR
// =========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullScreenLocalFolderPicker(
    initialPath: String,
    onCreateLocalFolder: (File, String) -> File?,
    onSelectPath: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val storageRoot = remember {
        Environment.getExternalStorageDirectory()?.absolutePath?.takeIf { it.isNotBlank() }
            ?: "/storage/emulated/0"
    }

    var currentDir by remember {
        val startFile = if (initialPath.isNotBlank()) File(initialPath) else File(storageRoot)
        mutableStateOf(
            if (startFile.exists() && startFile.isDirectory) startFile
            else File(storageRoot)
        )
    }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newLocalFolderName by remember { mutableStateOf("") }

    val childDirectories = remember(currentDir, refreshTrigger) {
        try {
            currentDir.listFiles()
                ?.filter { it.isDirectory && !it.name.startsWith(".") }
                ?.sortedBy { it.name.lowercase() }
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    val formattedCurrentPath = remember(currentDir) {
        if (currentDir.absolutePath == storageRoot) {
            "[Internal storage]"
        } else {
            currentDir.absolutePath.replaceFirst(storageRoot, "[Internal storage]")
        }
    }

    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Create New Local Folder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Inside: $formattedCurrentPath",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newLocalFolderName,
                        onValueChange = { newLocalFolderName = it },
                        label = { Text("Folder name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val created = onCreateLocalFolder(currentDir, newLocalFolderName)
                        if (created != null) {
                            currentDir = created
                            refreshTrigger++
                            newLocalFolderName = ""
                            showCreateFolderDialog = false
                        } else {
                            Toast.makeText(context, "Could not create folder. Check storage permission.", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Select Local Folder",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formattedCurrentPath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                modifier = Modifier.statusBarsPadding(),
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val parent = currentDir.parentFile
                            if (parent != null && parent.absolutePath.startsWith("/storage")) {
                                currentDir = parent
                            } else {
                                currentDir = File(storageRoot)
                            }
                        },
                        enabled = currentDir.absolutePath != storageRoot
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up Folder")
                    }
                    IconButton(onClick = { showCreateFolderDialog = true }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 4.dp,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Selected: $formattedCurrentPath",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = { onSelectPath(currentDir.absolutePath) },
                            modifier = Modifier.weight(1.5f)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select This Folder")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Quick shortcut chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val presets = listOf(
                    "[Internal storage]" to storageRoot,
                    "Camera" to "$storageRoot/DCIM/Camera",
                    "Screenshots" to "$storageRoot/Pictures/Screenshots",
                    "Download" to "$storageRoot/Download",
                    "Pictures" to "$storageRoot/Pictures",
                    "Movies" to "$storageRoot/Movies",
                    "Documents" to "$storageRoot/Documents",
                    "Music" to "$storageRoot/Music",
                    "WhatsApp" to "$storageRoot/Android/media/com.whatsapp/WhatsApp/Media"
                )
                presets.forEach { (label, path) ->
                    AssistChip(
                        onClick = {
                            val f = File(path)
                            if (!f.exists()) f.mkdirs()
                            currentDir = f
                        },
                        label = { Text(label) }
                    )
                }
            }

            // Up folder & New folder bar
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = {
                        val parent = currentDir.parentFile
                        if (parent != null && parent.absolutePath.startsWith("/storage")) {
                            currentDir = parent
                        } else {
                            currentDir = File(storageRoot)
                        }
                    },
                    enabled = currentDir.absolutePath != storageRoot,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Up Folder")
                }
                OutlinedButton(
                    onClick = { showCreateFolderDialog = true },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Folder")
                }
            }

            // Full-height folder list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                if (childDirectories.isEmpty()) {
                    item {
                        OutlinedCard(
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No subfolders inside '${currentDir.name}'",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tap 'Select This Folder' below to sync this folder.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(childDirectories, key = { it.absolutePath }) { subDir ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { currentDir = subDir }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 11.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = subDir.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                TextButton(
                                    onClick = { onSelectPath(subDir.absolutePath) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Select", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// 5. SYNCHRONIZATION SETTINGS VIEW (Compact & Clean)
// =========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SynchronizationSettingsView(
    settings: AutosyncGlobalSettings,
    isBatteryOptimizationIgnored: Boolean,
    onUpdateSettings: ((AutosyncGlobalSettings) -> AutosyncGlobalSettings) -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onBack: () -> Unit
) {
    var showScheduleDialog by remember { mutableStateOf(false) }
    var showBatteryLevelDialog by remember { mutableStateOf(false) }
    var showInternetConnectionDialog by remember { mutableStateOf(false) }
    var showWifiUploadLimitDialog by remember { mutableStateOf(false) }
    var showWifiDownloadLimitDialog by remember { mutableStateOf(false) }
    var showMobileUploadLimitDialog by remember { mutableStateOf(false) }
    var showMobileDownloadLimitDialog by remember { mutableStateOf(false) }
    var showRetryWaitDialog by remember { mutableStateOf(false) }
    var showMaxRetryAttemptsDialog by remember { mutableStateOf(false) }

    if (showScheduleDialog) {
        SingleChoiceOptionDialog(
            title = "Autosync schedule",
            options = AutosyncGlobalSettings.SCHEDULE_OPTIONS_MINUTES,
            selectedOption = settings.scheduleIntervalMinutes,
            labelMapper = { AutosyncGlobalSettings.formatSchedule(it) },
            onSelect = { mins ->
                onUpdateSettings { it.copy(scheduleIntervalMinutes = mins) }
                showScheduleDialog = false
            },
            onDismiss = { showScheduleDialog = false }
        )
    }

    if (showBatteryLevelDialog) {
        SingleChoiceOptionDialog(
            title = "Battery level must be at least",
            options = AutosyncGlobalSettings.BATTERY_LEVEL_OPTIONS,
            selectedOption = settings.minBatteryLevel,
            labelMapper = { if (it == 0) "0% (No restriction)" else "$it%" },
            onSelect = { pct ->
                onUpdateSettings { it.copy(minBatteryLevel = pct) }
                showBatteryLevelDialog = false
            },
            onDismiss = { showBatteryLevelDialog = false }
        )
    }

    if (showInternetConnectionDialog) {
        SingleChoiceOptionDialog(
            title = "Internet connection",
            options = listOf(true, false),
            selectedOption = settings.wifiOnly,
            labelMapper = { wifiOnly ->
                if (wifiOnly) "Using Wi-Fi only" else "Using Wi-Fi or mobile data"
            },
            onSelect = { wifiOnly ->
                onUpdateSettings { it.copy(wifiOnly = wifiOnly) }
                showInternetConnectionDialog = false
            },
            onDismiss = { showInternetConnectionDialog = false }
        )
    }

    if (showWifiUploadLimitDialog) {
        SingleChoiceOptionDialog(
            title = "Wi-Fi upload size limit",
            options = AutosyncGlobalSettings.FILE_SIZE_LIMIT_OPTIONS_BYTES,
            selectedOption = settings.wifiUploadSizeLimitBytes,
            labelMapper = { AutosyncGlobalSettings.formatSizeLimit(it) },
            onSelect = { bytes ->
                onUpdateSettings { it.copy(wifiUploadSizeLimitBytes = bytes) }
                showWifiUploadLimitDialog = false
            },
            onDismiss = { showWifiUploadLimitDialog = false }
        )
    }

    if (showWifiDownloadLimitDialog) {
        SingleChoiceOptionDialog(
            title = "Wi-Fi download size limit",
            options = AutosyncGlobalSettings.FILE_SIZE_LIMIT_OPTIONS_BYTES,
            selectedOption = settings.wifiDownloadSizeLimitBytes,
            labelMapper = { AutosyncGlobalSettings.formatSizeLimit(it) },
            onSelect = { bytes ->
                onUpdateSettings { it.copy(wifiDownloadSizeLimitBytes = bytes) }
                showWifiDownloadLimitDialog = false
            },
            onDismiss = { showWifiDownloadLimitDialog = false }
        )
    }

    if (showMobileUploadLimitDialog) {
        SingleChoiceOptionDialog(
            title = "Mobile upload size limit",
            options = AutosyncGlobalSettings.FILE_SIZE_LIMIT_OPTIONS_BYTES,
            selectedOption = settings.mobileUploadSizeLimitBytes,
            labelMapper = { AutosyncGlobalSettings.formatSizeLimit(it) },
            onSelect = { bytes ->
                onUpdateSettings { it.copy(mobileUploadSizeLimitBytes = bytes) }
                showMobileUploadLimitDialog = false
            },
            onDismiss = { showMobileUploadLimitDialog = false }
        )
    }

    if (showMobileDownloadLimitDialog) {
        SingleChoiceOptionDialog(
            title = "Mobile download size limit",
            options = AutosyncGlobalSettings.FILE_SIZE_LIMIT_OPTIONS_BYTES,
            selectedOption = settings.mobileDownloadSizeLimitBytes,
            labelMapper = { AutosyncGlobalSettings.formatSizeLimit(it) },
            onSelect = { bytes ->
                onUpdateSettings { it.copy(mobileDownloadSizeLimitBytes = bytes) }
                showMobileDownloadLimitDialog = false
            },
            onDismiss = { showMobileDownloadLimitDialog = false }
        )
    }

    if (showRetryWaitDialog) {
        SingleChoiceOptionDialog(
            title = "Wait before each attempt",
            options = AutosyncGlobalSettings.RETRY_WAIT_OPTIONS_MINUTES,
            selectedOption = settings.retryWaitMinutes,
            labelMapper = { if (it == 1) "1 minute" else "$it minutes" },
            onSelect = { mins ->
                onUpdateSettings { it.copy(retryWaitMinutes = mins) }
                showRetryWaitDialog = false
            },
            onDismiss = { showRetryWaitDialog = false }
        )
    }

    if (showMaxRetryAttemptsDialog) {
        SingleChoiceOptionDialog(
            title = "Max number of attempts",
            options = AutosyncGlobalSettings.MAX_RETRY_ATTEMPTS_OPTIONS,
            selectedOption = settings.maxRetryAttempts,
            labelMapper = { if (it == 1) "1 time" else "$it times" },
            onSelect = { count ->
                onUpdateSettings { it.copy(maxRetryAttempts = count) }
                showMaxRetryAttemptsDialog = false
            },
            onDismiss = { showMaxRetryAttemptsDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Synchronization") },
                modifier = Modifier.statusBarsPadding(),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            // SECTION 1: Automatic background sync
            SyncSectionHeader("Automatic background sync")

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onUpdateSettings { it.copy(enableAutosync = !settings.enableAutosync) }
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Enable autosync",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Monitor folders and sync in background",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.enableAutosync,
                    onCheckedChange = { enabled ->
                        onUpdateSettings { it.copy(enableAutosync = enabled) }
                    }
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRequestBatteryExemption() }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (isBatteryOptimizationIgnored) {
                        "Battery optimization is disabled for TeleNest"
                    } else {
                        "The battery saving method in your device may affect automatic sync"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Configure battery optimization",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SyncClickableValueRow(
                title = "Autosync schedule",
                value = AutosyncGlobalSettings.formatSchedule(settings.scheduleIntervalMinutes),
                onClick = { showScheduleDialog = true }
            )

            SyncCheckboxRow(
                title = "Only while device is charging",
                checked = settings.onlyWhileCharging,
                onCheckedChange = { checked ->
                    onUpdateSettings { it.copy(onlyWhileCharging = checked) }
                }
            )

            SyncClickableValueRow(
                title = "Battery level must be at least",
                value = "${settings.minBatteryLevel}%",
                onClick = { showBatteryLevelDialog = true }
            )

            SyncClickableValueRow(
                title = "Internet connection",
                value = if (settings.wifiOnly) "Using Wi-Fi only" else "Using Wi-Fi or mobile data",
                onClick = { showInternetConnectionDialog = true }
            )

            SyncCheckboxRow(
                title = "Allowed on metered Wi-Fi",
                checked = settings.allowedOnMeteredWifi,
                onCheckedChange = { checked ->
                    onUpdateSettings { it.copy(allowedOnMeteredWifi = checked) }
                }
            )

            SyncCheckboxRow(
                title = "Allowed on mobile roaming",
                checked = settings.allowedOnRoaming,
                onCheckedChange = { checked ->
                    onUpdateSettings { it.copy(allowedOnRoaming = checked) }
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // SECTION 2: File size limits
            SyncSectionHeader("File size limits")

            SyncClickableValueRow(
                title = "Wi-Fi upload size limit",
                value = AutosyncGlobalSettings.formatSizeLimit(settings.wifiUploadSizeLimitBytes),
                onClick = { showWifiUploadLimitDialog = true }
            )

            SyncClickableValueRow(
                title = "Wi-Fi download size limit",
                value = AutosyncGlobalSettings.formatSizeLimit(settings.wifiDownloadSizeLimitBytes),
                onClick = { showWifiDownloadLimitDialog = true }
            )

            SyncClickableValueRow(
                title = "Mobile upload size limit",
                value = AutosyncGlobalSettings.formatSizeLimit(settings.mobileUploadSizeLimitBytes),
                onClick = { showMobileUploadLimitDialog = true }
            )

            SyncClickableValueRow(
                title = "Mobile download size limit",
                value = AutosyncGlobalSettings.formatSizeLimit(settings.mobileDownloadSizeLimitBytes),
                onClick = { showMobileDownloadLimitDialog = true }
            )

            SyncCheckboxRow(
                title = "Warn if sync on mobile network",
                checked = settings.warnOnMobileNetwork,
                onCheckedChange = { checked ->
                    onUpdateSettings { it.copy(warnOnMobileNetwork = checked) }
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // SECTION 3: After sync errors
            SyncSectionHeader("After sync errors")

            SyncCheckboxRow(
                title = "Try again automatically",
                checked = settings.retryAutomatically,
                onCheckedChange = { checked ->
                    onUpdateSettings { it.copy(retryAutomatically = checked) }
                }
            )

            SyncClickableValueRow(
                title = "Wait before each attempt",
                value = if (settings.retryWaitMinutes == 1) "1 minute" else "${settings.retryWaitMinutes} minutes",
                onClick = { showRetryWaitDialog = true }
            )

            SyncClickableValueRow(
                title = "Max number of attempts",
                value = if (settings.maxRetryAttempts == 1) "1 time" else "${settings.maxRetryAttempts} times",
                onClick = { showMaxRetryAttemptsDialog = true }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // SECTION 4: Parallel transfers
            SyncSectionHeader("Parallel transfers")

            SyncCheckboxRow(
                title = "Allow parallel uploads",
                checked = settings.allowParallelUploads,
                onCheckedChange = { checked ->
                    onUpdateSettings { it.copy(allowParallelUploads = checked) }
                }
            )

            SyncCheckboxRow(
                title = "Allow parallel downloads",
                checked = settings.allowParallelDownloads,
                onCheckedChange = { checked ->
                    onUpdateSettings { it.copy(allowParallelDownloads = checked) }
                }
            )
        }
    }
}

@Composable
private fun SyncSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp)
    )
}

@Composable
private fun SyncClickableValueRow(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 7.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SyncCheckboxRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

// =========================================================================
// 6. COMPACT SINGLE-SCREEN OPTION DIALOG (Fits all options without scroll!)
// =========================================================================

@Composable
private fun <T> SingleChoiceOptionDialog(
    title: String,
    options: List<T>,
    selectedOption: T,
    labelMapper: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .padding(vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    options.forEach { item ->
                        val isSelected = item == selectedOption
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(
                                width = if (isSelected) 1.2.dp else 0.5.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                            ),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .clickable { onSelect(item) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp)
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { onSelect(item) },
                                    modifier = Modifier
                                        .size(20.dp)
                                        .scale(0.85f)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = labelMapper(item),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    return try {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.isIgnoringBatteryOptimizations(context.packageName)
    } catch (_: Exception) {
        true
    }
}

private fun requestIgnoreBatteryOptimizations(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        } catch (_: Exception) {}
    }
}

private fun requestAllFilesAccessPermission(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            } catch (_: Exception) {}
        }
    }
}
