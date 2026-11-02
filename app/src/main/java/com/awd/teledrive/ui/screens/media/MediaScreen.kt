package com.awd.teledrive.ui.screens.media

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.awd.teledrive.R
import com.awd.teledrive.domain.model.DriveItem
import com.awd.teledrive.domain.model.MoveCopyDestination
import com.awd.teledrive.ui.screens.home.MoveCopyFolderDialog
import com.awd.teledrive.ui.theme.TeledriveTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@androidx.compose.runtime.Immutable
data class MediaDateSection(
    val key: String,
    val title: String,
    val subtitle: String?,
    val isYearArchive: Boolean,
    val items: List<DriveItem.File>
)

internal fun groupMediaChronologicallyFast(items: List<DriveItem.File>): List<MediaDateSection> {
    if (items.isEmpty()) return emptyList()

    val nowCal = Calendar.getInstance()
    val currentYear = nowCal.get(Calendar.YEAR)
    val currentDayOfYear = nowCal.get(Calendar.DAY_OF_YEAR)

    val yesterdayCal = (nowCal.clone() as Calendar).apply {
        add(Calendar.DAY_OF_YEAR, -1)
    }
    val yesterdayYear = yesterdayCal.get(Calendar.YEAR)
    val yesterdayDayOfYear = yesterdayCal.get(Calendar.DAY_OF_YEAR)

    val dayFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
    val fullDateSubFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    val archiveMonthYearFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

    val grouped = LinkedHashMap<String, Triple<String, String?, Boolean>>()
    val itemsByKey = LinkedHashMap<String, MutableList<DriveItem.File>>()

    val itemCal = Calendar.getInstance()
    val dateObj = Date()

    for (file in items) {
        val ts = if (file.createdAt > 0L) file.createdAt else System.currentTimeMillis()
        itemCal.timeInMillis = ts
        val year = itemCal.get(Calendar.YEAR)
        val month = itemCal.get(Calendar.MONTH)
        val dayOfYear = itemCal.get(Calendar.DAY_OF_YEAR)
        dateObj.time = ts

        val sectionKey: String
        val title: String
        val subtitle: String?
        val isYearArchive: Boolean

        when {
            year == currentYear && dayOfYear == currentDayOfYear -> {
                sectionKey = "DAY_${year}_$dayOfYear"
                title = "Today"
                subtitle = dayFormat.format(dateObj)
                isYearArchive = false
            }
            year == yesterdayYear && dayOfYear == yesterdayDayOfYear -> {
                sectionKey = "DAY_${year}_$dayOfYear"
                title = "Yesterday"
                subtitle = dayFormat.format(dateObj)
                isYearArchive = false
            }
            year == currentYear -> {
                sectionKey = "DAY_${year}_$dayOfYear"
                title = dayFormat.format(dateObj)
                subtitle = fullDateSubFormat.format(dateObj)
                isYearArchive = false
            }
            else -> {
                // Previous years: Group by Month + Year (Google Photos archive style)
                sectionKey = "MONTH_${year}_$month"
                title = archiveMonthYearFormat.format(dateObj)
                subtitle = "$year"
                isYearArchive = true
            }
        }

        if (!grouped.containsKey(sectionKey)) {
            grouped[sectionKey] = Triple(title, subtitle, isYearArchive)
            itemsByKey[sectionKey] = mutableListOf()
        }
        itemsByKey[sectionKey]?.add(file)
    }

    return grouped.map { (key, meta) ->
        MediaDateSection(
            key = key,
            title = meta.first,
            subtitle = meta.second,
            isYearArchive = meta.third,
            items = itemsByKey[key] ?: emptyList()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaScreen(
    onNavigateToPreview: (DriveItem.File) -> Unit,
    viewModel: MediaViewModel = hiltViewModel()
) {
    val mediaItems by viewModel.mediaItems.collectAsState()
    val mediaSections by viewModel.mediaSections.collectAsState()
    val folders by viewModel.folders.collectAsState()

    MediaContent(
        mediaItems = mediaItems,
        precomputedSections = mediaSections,
        folders = folders,
        onNavigateToPreview = onNavigateToPreview,
        onDownload = viewModel::downloadFile,
        onDelete = viewModel::deleteItems,
        onMoveOrCopyItems = viewModel::moveOrCopyItems,
        onCreateFolderForPicker = viewModel::createFolderForPicker,
        onToggleStar = viewModel::toggleStarred
    )
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MediaContent(
    mediaItems: List<DriveItem.File>,
    precomputedSections: List<MediaDateSection>? = null,
    folders: List<DriveItem.Folder>,
    onNavigateToPreview: (DriveItem.File) -> Unit,
    onDownload: (DriveItem.File) -> Unit,
    onDelete: (List<DriveItem.File>) -> Unit,
    onMoveOrCopyItems: (List<DriveItem.File>, MoveCopyDestination, Boolean, (Int) -> Unit) -> Unit = { _, _, _, _ -> },
    onCreateFolderForPicker: (String, Boolean, String, (MoveCopyDestination?) -> Unit) -> Unit = { _, _, _, _ -> },
    onToggleStar: (DriveItem.File) -> Unit
) {
    val context = LocalContext.current
    var selectedItems by remember { mutableStateOf(setOf<Long>()) }
    val isSelectionMode = selectedItems.isNotEmpty()

    // Default to 4 columns per row as requested (Google Photos / Phone Gallery style)
    var gridColumns by remember { mutableIntStateOf(4) }

    var showMoveCopyDialog by remember { mutableStateOf(false) }
    var isCopyOperation by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val dateSections = remember(precomputedSections, mediaItems) {
        precomputedSections ?: groupMediaChronologicallyFast(mediaItems)
    }

    if (showDeleteConfirm) {
        val itemsToDelete = mediaItems.filter { it.id in selectedItems }
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
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
                    onDelete(itemsToDelete)
                    showDeleteConfirm = false
                    selectedItems = emptySet()
                    Toast.makeText(context, "Moved to Trash (kept for 30 days)", Toast.LENGTH_SHORT).show()
                }) { Text("Move to Trash", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showMoveCopyDialog) {
        val targetFiles = mediaItems.filter { it.id in selectedItems }
        MoveCopyFolderDialog(
            isCopy = isCopyOperation,
            folders = folders,
            currentFolderId = null,
            currentVirtualFolderId = "0",
            itemCount = targetFiles.size,
            onDismiss = { showMoveCopyDialog = false },
            onCreateNewFolder = { folderName, isVirtual, parentVfId, onResult ->
                onCreateFolderForPicker(folderName, isVirtual, parentVfId, onResult)
            },
            onSelectDestination = { destination ->
                if (targetFiles.isNotEmpty()) {
                    onMoveOrCopyItems(targetFiles, destination, isCopyOperation) { count ->
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
                selectedItems = emptySet()
                showMoveCopyDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text(stringResource(R.string.selected_count, selectedItems.size)) },
                    modifier = Modifier.statusBarsPadding(),
                    navigationIcon = {
                        IconButton(onClick = { selectedItems = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            selectedItems = if (selectedItems.size >= mediaItems.size && mediaItems.isNotEmpty()) {
                                emptySet()
                            } else {
                                mediaItems.map { it.id }.toSet()
                            }
                        }) {
                            Icon(
                                if (selectedItems.size >= mediaItems.size && mediaItems.isNotEmpty()) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                contentDescription = stringResource(R.string.select_all)
                            )
                        }
                        IconButton(onClick = {
                            selectedItems.forEach { id ->
                                mediaItems.find { it.id == id }?.let { onToggleStar(it) }
                            }
                            selectedItems = emptySet()
                        }) {
                            Icon(Icons.Default.Star, contentDescription = stringResource(R.string.add_star))
                        }
                        IconButton(onClick = {
                            val count = selectedItems.size
                            selectedItems.forEach { id ->
                                mediaItems.find { it.id == id }?.let { onDownload(it) }
                            }
                            selectedItems = emptySet()
                            Toast.makeText(context, context.getString(R.string.starting_downloads, count), Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.Download, contentDescription = stringResource(R.string.download))
                        }
                        IconButton(onClick = {
                            isCopyOperation = true
                            showMoveCopyDialog = true
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copy))
                        }
                        IconButton(onClick = {
                            isCopyOperation = false
                            showMoveCopyDialog = true
                        }) {
                            Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = stringResource(R.string.move))
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                )
            } else {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.media),
                                fontWeight = FontWeight.SemiBold
                            )
                            if (mediaItems.isNotEmpty()) {
                                Text(
                                    text = "${mediaItems.size} photos & videos",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            gridColumns = if (gridColumns == 4) 3 else 4
                        }) {
                            Icon(
                                imageVector = if (gridColumns == 4) Icons.Default.GridView else Icons.Default.GridOn,
                                contentDescription = "Toggle grid columns"
                            )
                        }
                    },
                    modifier = Modifier.statusBarsPadding(),
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    ) { padding ->
        if (mediaItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Text(
                        text = stringResource(R.string.no_results),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                contentPadding = PaddingValues(
                    start = 6.dp,
                    top = 4.dp,
                    end = 6.dp,
                    bottom = padding.calculateBottomPadding() + 88.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding())
            ) {
                dateSections.forEach { section ->
                    item(
                        key = "header_${section.key}",
                        contentType = "media_section_header",
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        val sectionIds = remember(section.items) { section.items.map { it.id }.toSet() }
                        val allInSectionSelected = sectionIds.isNotEmpty() && selectedItems.containsAll(sectionIds)

                        MediaSectionHeader(
                            title = section.title,
                            subtitle = section.subtitle,
                            itemCount = section.items.size,
                            isYearArchive = section.isYearArchive,
                            isSelectionMode = isSelectionMode,
                            allInSectionSelected = allInSectionSelected,
                            onToggleSectionSelection = {
                                selectedItems = if (allInSectionSelected) {
                                    selectedItems - sectionIds
                                } else {
                                    selectedItems + sectionIds
                                }
                            }
                        )
                    }

                    items(
                        items = section.items,
                        key = { it.uniqueKey },
                        contentType = { "media_grid_item" }
                    ) { item ->
                        val isSelected = selectedItems.contains(item.id)
                        MediaGridItem(
                            item = item,
                            isSelected = isSelected,
                            isSelectionMode = isSelectionMode,
                            onClick = {
                                if (isSelectionMode) {
                                    selectedItems = if (isSelected) selectedItems - item.id else selectedItems + item.id
                                } else {
                                    onNavigateToPreview(item)
                                }
                            },
                            onLongClick = {
                                selectedItems = if (isSelected) selectedItems - item.id else selectedItems + item.id
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaSectionHeader(
    title: String,
    subtitle: String?,
    itemCount: Int,
    isYearArchive: Boolean,
    isSelectionMode: Boolean,
    allInSectionSelected: Boolean,
    onToggleSectionSelection: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 6.dp, start = 6.dp, end = 6.dp)
            .clickable { onToggleSectionSelection() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            if (isYearArchive) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .padding(5.dp)
                            .size(16.dp)
                    )
                }
            }
            Column {
                Text(
                    text = title,
                    style = if (isYearArchive) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!subtitle.isNullOrBlank() && subtitle != title) {
                    Text(
                        text = "$subtitle • $itemCount items",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!isSelectionMode) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "$itemCount",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
            Icon(
                imageVector = if (allInSectionSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = "Select section",
                tint = if (allInSectionSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .size(22.dp)
                    .padding(2.dp)
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MediaGridItem(
    item: DriveItem.File,
    isSelected: Boolean,
    isSelectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val thumbPath = item.thumbnailPath ?: item.localPath
    val imageRequest = remember(thumbPath) {
        thumbPath?.let {
            val cacheKey = "media_thumb_$it"
            ImageRequest.Builder(context)
                .data(it)
                .size(220)
                .precision(coil.size.Precision.INEXACT)
                .allowRgb565(true)
                .memoryCacheKey(cacheKey)
                .diskCacheKey(cacheKey)
                .crossfade(100)
                .build()
        }
    }

    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(6.dp),
        border = if (isSelected) BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = imageRequest,
                contentDescription = item.name,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isSelected) 4.dp else 0.dp)
                    .clip(RoundedCornerShape(if (isSelected) 4.dp else 0.dp)),
                contentScale = ContentScale.Crop
            )

            // Subtle top-right gradient for selection badge visibility
            if (isSelectionMode || isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                            else Color.Black.copy(alpha = 0.08f)
                        )
                )
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(20.dp)
                        .background(
                            color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                            shape = CircleShape
                        ),
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f)
                )
            }

            // Icon overlay for video
            if (item.mimeType.startsWith("video/")) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                            )
                        )
                        .padding(4.dp)
                ) {
                    Icon(
                        Icons.Default.PlayCircle,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .size(18.dp),
                        tint = Color.White.copy(alpha = 0.92f)
                    )
                }
            }

            if (item.isStarred) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(14.dp),
                    tint = Color(0xFFFFC107)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MediaPreview() {
    TeledriveTheme {
        MediaContent(
            mediaItems = listOf(
                DriveItem.File(1L, 0L, "photo.jpg", 1024, "image/jpeg", 1, null, null, false, "")
            ),
            folders = emptyList(),
            onNavigateToPreview = {},
            onDownload = {},
            onDelete = {},
            onToggleStar = {}
        )
    }
}


