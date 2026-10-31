package com.awd.teledrive.ui.screens.starred

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.awd.teledrive.R
import com.awd.teledrive.domain.model.DriveItem
import com.awd.teledrive.ui.screens.home.DriveGridItem
import com.awd.teledrive.ui.screens.home.DriveListItem
import com.awd.teledrive.ui.screens.home.ShareDialog
import com.awd.teledrive.ui.theme.TeledriveTheme
import org.drinkless.tdlib.TdApi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarredScreen(
    onBack: () -> Unit,
    onNavigateToPreview: (DriveItem.File) -> Unit,
    viewModel: StarredViewModel = hiltViewModel()
) {
    val items by viewModel.starredItems.collectAsState()
    val isGridView by viewModel.isGridView.collectAsState()
    
    var selectedItems by remember { mutableStateOf(setOf<Long>()) }

    StarredContent(
        items = items,
        isGridView = isGridView,
        selectedItems = selectedItems,
        onSelectionChange = { selectedItems = it },
        onToggleViewMode = viewModel::toggleViewMode,
        onBack = onBack,
        onNavigateToPreview = onNavigateToPreview,
        onToggleStarred = viewModel::toggleStarred,
        onDeleteItems = viewModel::deleteItems,
        onDownloadClick = { id, chatId, name -> viewModel.downloadFile(id, chatId, name) },
        onBulkDownload = {
            val selectedFiles = items.filter { it.id in selectedItems }.filterIsInstance<DriveItem.File>()
            val selectedFolders = items.filter { it.id in selectedItems }.filterIsInstance<DriveItem.Folder>()
            selectedFiles.forEach { viewModel.downloadFile(it.id, it.parentChatId, it.name) }
            selectedFolders.forEach { viewModel.downloadFolderContents(it) }
            selectedItems = emptySet()
        },
        onGetInviteLink = viewModel::getFolderInviteLink,
        onGetMembers = viewModel::getFolderMembers,
        onShareFile = viewModel::shareFileToPhone
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarredContent(
    items: List<DriveItem>,
    isGridView: Boolean,
    selectedItems: Set<Long>,
    onSelectionChange: (Set<Long>) -> Unit,
    onToggleViewMode: () -> Unit,
    onBack: () -> Unit,
    onNavigateToPreview: (DriveItem.File) -> Unit,
    onToggleStarred: (DriveItem) -> Unit,
    onDeleteItems: (List<DriveItem>) -> Unit = {},
    onDownloadClick: (Long, Long, String) -> Unit,
    onBulkDownload: () -> Unit,
    onGetInviteLink: (Long, (String?) -> Unit) -> Unit,
    onGetMembers: (Long, (List<TdApi.User>) -> Unit) -> Unit,
    onShareFile: (String, Long, (Boolean, String?) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val isSelectionMode = selectedItems.isNotEmpty()
    var shareItem by remember { mutableStateOf<DriveItem?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<List<DriveItem>?>(null) }

    if (shareItem != null) {
        ShareDialog(
            item = shareItem!!,
            onDismiss = { shareItem = null },
            onGetInviteLink = onGetInviteLink,
            onGetMembers = onGetMembers,
            onShareFile = onShareFile
        )
    }

    if (showDeleteConfirm != null) {
        val itemsToDelete = showDeleteConfirm!!
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Move to Trash?") },
            text = {
                Text(
                    if (itemsToDelete.size == 1) {
                        "\"${itemsToDelete.first().name}\" will be moved to Trash and kept for 30 days before permanent deletion."
                    } else {
                        "${itemsToDelete.size} selected items will be moved to Trash and kept for 30 days before permanent deletion."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteItems(itemsToDelete)
                    showDeleteConfirm = null
                    onSelectionChange(emptySet())
                    Toast.makeText(context, "Moved to Trash (kept for 30 days)", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Move to Trash", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text(stringResource(R.string.selected_count, selectedItems.size)) },
                    navigationIcon = {
                        IconButton(onClick = { onSelectionChange(emptySet()) }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                if (selectedItems.size >= items.size && items.isNotEmpty()) {
                                    onSelectionChange(emptySet())
                                } else {
                                    onSelectionChange(items.map { it.id }.toSet())
                                }
                            }
                        ) {
                            Icon(
                                if (selectedItems.size >= items.size && items.isNotEmpty()) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                contentDescription = stringResource(R.string.select_all)
                            )
                        }
                        IconButton(onClick = onBulkDownload) {
                            Icon(Icons.Default.Download, contentDescription = stringResource(R.string.download))
                        }
                        IconButton(onClick = {
                            val selected = items.filter { it.id in selectedItems }
                            if (selected.isNotEmpty()) {
                                showDeleteConfirm = selected
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                )
            } else {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(R.string.starred)) },
                    modifier = Modifier.statusBarsPadding(),
                    actions = {
                        IconButton(onClick = onToggleViewMode) {
                            Icon(if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView, contentDescription = null)
                        }
                    }
                )
            }
        }
    ) { padding ->
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_starred_items))
            }
        } else {
            if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize().padding(padding)
                ) {
                    items(
                        items = items,
                        key = { it.uniqueKey },
                        contentType = { if (it is DriveItem.Folder) "folder_grid" else "file_grid" }
                    ) { item ->
                        val isSelected = selectedItems.contains(item.id)
                        DriveGridItem(
                            item = item,
                            isSelected = isSelected,
                            onClick = {
                                if (isSelectionMode) {
                                    onSelectionChange(if (isSelected) selectedItems - item.id else selectedItems + item.id)
                                } else {
                                    if (item is DriveItem.File) onNavigateToPreview(item)
                                }
                            },
                            onLongClick = {
                                onSelectionChange(if (isSelected) selectedItems - item.id else selectedItems + item.id)
                            },
                            onStarClick = { onToggleStarred(item) },
                            onShareClick = { shareItem = item },
                            onDeleteClick = { showDeleteConfirm = listOf(item) },
                            onDownloadClick = {
                                if (item is DriveItem.File) onDownloadClick(item.id, item.parentChatId, item.name)
                            },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                    items(
                        items = items,
                        key = { it.uniqueKey },
                        contentType = { if (it is DriveItem.Folder) "folder_list" else "file_list" }
                    ) { item ->
                        val isSelected = selectedItems.contains(item.id)
                        DriveListItem(
                            item = item,
                            isSelected = isSelected,
                            onClick = {
                                if (isSelectionMode) {
                                    onSelectionChange(if (isSelected) selectedItems - item.id else selectedItems + item.id)
                                } else {
                                    if (item is DriveItem.File) onNavigateToPreview(item)
                                }
                            },
                            onLongClick = {
                                onSelectionChange(if (isSelected) selectedItems - item.id else selectedItems + item.id)
                            },
                            onStarClick = { onToggleStarred(item) },
                            onShareClick = { shareItem = item },
                            onDeleteClick = { showDeleteConfirm = listOf(item) },
                            onDownloadClick = {
                                if (item is DriveItem.File) onDownloadClick(item.id, item.parentChatId, item.name)
                            },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun StarredPreview() {
    TeledriveTheme {
        StarredContent(
            items = listOf(
                DriveItem.File(1L, 0L, "Starred File.pdf", 5000, "application/pdf", 1, null, null, true, "")
            ),
            isGridView = false,
            selectedItems = emptySet(),
            onSelectionChange = {},
            onToggleViewMode = {},
            onBack = {},
            onNavigateToPreview = {},
            onToggleStarred = {},
            onDeleteItems = {},
            onDownloadClick = { _, _, _ -> },
            onBulkDownload = {},
            onGetInviteLink = { _, _ -> },
            onGetMembers = { _, _ -> },
            onShareFile = { _, _, _ -> }
        )
    }
}
