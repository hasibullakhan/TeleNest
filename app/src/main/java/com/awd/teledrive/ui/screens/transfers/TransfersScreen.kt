package com.awd.teledrive.ui.screens.transfers

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.awd.teledrive.R
import com.awd.teledrive.data.model.TransferInfo
import com.awd.teledrive.ui.theme.TeledriveTheme
import kotlin.math.log10
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransfersScreen(
    onBack: () -> Unit,
    viewModel: TransfersViewModel = hiltViewModel()
) {
    val transfers by viewModel.transfers.collectAsState()

    TransfersContent(
        transfers = transfers.values.toList(),
        onBack = onBack,
        onCancelTransfer = viewModel::cancelTransfer,
        onRemoveTransfer = viewModel::removeTransfer,
        onClearCompleted = viewModel::clearCompleted
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransfersContent(
    transfers: List<TransferInfo>,
    onBack: () -> Unit,
    onCancelTransfer: (String) -> Unit,
    onRemoveTransfer: (String) -> Unit,
    onClearCompleted: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.transfers)) },
                modifier = Modifier.statusBarsPadding(),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel))
                    }
                },
                actions = {
                    IconButton(onClick = onClearCompleted) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.clear_selection))
                    }
                }
            )
        }
    ) { padding ->
        if (transfers.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.SwapVert,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    stringResource(R.string.no_results),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = transfers,
                    key = { it.remoteUniqueId },
                    contentType = { "transfer" }
                ) { transfer ->
                    TransferCard(
                        transfer = transfer,
                        onCancel = onCancelTransfer,
                        onRemove = onRemoveTransfer,
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}

@Composable
private fun TransferCard(
    transfer: TransferInfo,
    onCancel: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val status = com.awd.teledrive.data.repository.TransferRepository.Status
    val isFailed = transfer.status.startsWith(status.FAILED)
    val isTerminal = transfer.status == status.COMPLETED ||
        transfer.status == status.CANCELLED ||
        isFailed

    val animatedProgress by animateFloatAsState(
        targetValue = transfer.progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 300),
        label = "transfer_progress"
    )

    val sizeText = if (transfer.totalSize > 0) {
        "${formatSize(transfer.downloadedSize)} / ${formatSize(transfer.totalSize)}"
    } else {
        formatSize(transfer.downloadedSize)
    }
    val statusText = when (transfer.status) {
        status.QUEUED -> stringResource(R.string.status_queued)
        status.DOWNLOADING -> stringResource(R.string.downloading_file, "")
        status.UPLOADING -> stringResource(R.string.uploading_file, "")
        status.COMPLETED -> stringResource(R.string.completed)
        status.CANCELLED -> stringResource(R.string.cancel)
        else -> transfer.status
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (isFailed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (transfer.isDownload) Icons.Default.Download else Icons.Default.Upload,
                        contentDescription = null,
                        tint = if (isFailed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    transfer.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "$statusText - $sizeText (${(transfer.progress * 100).toInt()}%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!isTerminal) {
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(50))
                    )
                }
            }
            if (!isTerminal) {
                IconButton(onClick = { onCancel(transfer.remoteUniqueId) }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                }
            } else {
                // Allow individual removal for finished/failed tasks
                IconButton(onClick = { onRemove(transfer.remoteUniqueId) }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TransfersPreview() {
    TeledriveTheme {
        TransfersContent(
            transfers = listOf(
                TransferInfo(1, "unique_id_1", "Large_Movie.mkv", 0.45f, true, "Transferring")
            ),
            onBack = {},
            onCancelTransfer = {},
            onRemoveTransfer = {},
            onClearCompleted = {}
        )
    }
}

private fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(size.toDouble()) / log10(1000.0)).toInt().coerceIn(0, units.lastIndex)
    return String.format(java.util.Locale.getDefault(), "%.1f %s", size / 1000.0.pow(digitGroups.toDouble()), units[digitGroups])
}

