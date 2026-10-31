package com.awd.teledrive

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.compose.rememberNavController
import com.awd.teledrive.data.repository.UpdateRepository
import com.awd.teledrive.data.repository.UpdateState
import com.awd.teledrive.data.secure.SecureSettings
import com.awd.teledrive.data.worker.TeleDriveWorkerManager
import com.awd.teledrive.ui.navigation.NavGraph
import com.awd.teledrive.ui.screens.security.SecurityViewModel
import com.awd.teledrive.ui.screens.settings.SettingsViewModel
import com.awd.teledrive.ui.theme.TeledriveTheme
import com.awd.teledrive.ui.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShortcutFolderTarget(
    val isVirtual: Boolean,
    val chatId: Long?,
    val virtualId: String,
    val folderName: String,
    val timestamp: Long = System.currentTimeMillis()
)

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SHORTCUT_IS_VIRTUAL = "extra_shortcut_is_virtual"
        const val EXTRA_SHORTCUT_CHAT_ID = "extra_shortcut_chat_id"
        const val EXTRA_SHORTCUT_VIRTUAL_ID = "extra_shortcut_virtual_id"
        const val EXTRA_SHORTCUT_FOLDER_NAME = "extra_shortcut_folder_name"

        private val _pendingShortcutFolder = MutableStateFlow<ShortcutFolderTarget?>(null)
        val pendingShortcutFolder = _pendingShortcutFolder.asStateFlow()

        private val _pendingSharedUris = MutableStateFlow<List<Uri>>(emptyList())
        val pendingSharedUris = _pendingSharedUris.asStateFlow()

        private val _pendingSharedFiles = MutableStateFlow<List<Pair<java.io.File, String>>>(emptyList())
        val pendingSharedFiles = _pendingSharedFiles.asStateFlow()

        private val _isStagingSharedFiles = MutableStateFlow(false)
        val isStagingSharedFiles = _isStagingSharedFiles.asStateFlow()

        fun clearPendingShortcutFolder() {
            _pendingShortcutFolder.value = null
        }

        fun clearPendingSharedUris() {
            _pendingSharedUris.value = emptyList()
        }

        fun clearPendingSharedFiles() {
            _pendingSharedFiles.value = emptyList()
        }

        fun cancelStagingSharedFiles() {
            _isStagingSharedFiles.value = false
        }
    }

    @Inject
    lateinit var workerManager: TeleDriveWorkerManager

    @Inject
    lateinit var autosyncManager: com.awd.teledrive.data.repository.AutosyncManager

    @Inject
    lateinit var secureSettings: SecureSettings

    @Inject
    lateinit var updateRepository: UpdateRepository

    private val securityViewModel: SecurityViewModel by viewModels()
    private var lastPauseTime: Long = 0

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ -> }

    private fun handleShortcutIntent(intent: Intent?) {
        if (intent == null) return
        val folderName = intent.getStringExtra(EXTRA_SHORTCUT_FOLDER_NAME)
        if (!folderName.isNullOrBlank()) {
            val isVirtual = intent.getBooleanExtra(EXTRA_SHORTCUT_IS_VIRTUAL, true)
            val rawChatId = intent.getLongExtra(EXTRA_SHORTCUT_CHAT_ID, 0L)
            val virtualId = intent.getStringExtra(EXTRA_SHORTCUT_VIRTUAL_ID) ?: "0"
            val resolvedChatId = if (isVirtual) {
                if (rawChatId != 0L) rawChatId else null
            } else {
                rawChatId
            }
            _pendingShortcutFolder.value = ShortcutFolderTarget(
                isVirtual = isVirtual,
                chatId = resolvedChatId,
                virtualId = if (isVirtual) virtualId else "0",
                folderName = folderName
            )
            intent.removeExtra(EXTRA_SHORTCUT_FOLDER_NAME)
        }
    }

    private fun copyUriToTempFile(uri: Uri): Pair<java.io.File, String>? {
        return try {
            val resolver = applicationContext.contentResolver
            val queriedName = try {
                resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
                }
            } catch (_: Exception) {
                null
            }
            val rawName = queriedName?.takeIf { it.isNotBlank() }
                ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                ?: "shared_${System.currentTimeMillis()}"
            val safeFileName = rawName.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank {
                "shared_${System.currentTimeMillis()}"
            }

            // Ensure final filename has an extension if MIME type is known
            val mimeType = try { resolver.getType(uri) } catch (_: Exception) { null }
            val extFromMime = mimeType?.let {
                android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(it)
            }
            val finalFileName = if (!safeFileName.contains('.') && !extFromMime.isNullOrBlank()) {
                "$safeFileName.$extFromMime"
            } else {
                safeFileName
            }

            val stageDir = java.io.File(applicationContext.cacheDir, "stage_${System.nanoTime()}").apply { mkdirs() }
            val tempFile = java.io.File(stageDir, finalFileName)

            var copied = false
            try {
                resolver.openInputStream(uri)?.use { input ->
                    java.io.FileOutputStream(tempFile).use { output ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                        output.flush()
                    }
                    copied = tempFile.exists() && tempFile.length() > 0L
                }
            } catch (_: Exception) {
                copied = false
            }

            if (!copied) {
                try {
                    resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        java.io.FileInputStream(pfd.fileDescriptor).use { input ->
                            java.io.FileOutputStream(tempFile).use { output ->
                                input.copyTo(output, bufferSize = 64 * 1024)
                                output.flush()
                            }
                        }
                        copied = tempFile.exists() && tempFile.length() > 0L
                    }
                } catch (_: Exception) {
                    copied = false
                }
            }

            if (!copied && uri.scheme == "file") {
                val srcPath = uri.path
                if (!srcPath.isNullOrBlank()) {
                    val srcFile = java.io.File(srcPath)
                    if (srcFile.exists() && srcFile.canRead()) {
                        srcFile.copyTo(tempFile, overwrite = true)
                        copied = tempFile.exists() && tempFile.length() > 0L
                    }
                }
            }

            if (copied && tempFile.exists()) {
                tempFile to finalFileName
            } else {
                try { tempFile.delete() } catch (_: Exception) {}
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return

        val collectedUris = linkedSetOf<Uri>()
        when (action) {
            Intent.ACTION_SEND -> {
                val streamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                if (streamUri != null) collectedUris.add(streamUri)
                intent.data?.let { collectedUris.add(it) }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val streamUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                streamUris?.filterNotNull()?.let { collectedUris.addAll(it) }
            }
        }

        val clip = intent.clipData
        if (clip != null) {
            for (i in 0 until clip.itemCount) {
                clip.getItemAt(i)?.uri?.let { collectedUris.add(it) }
            }
        }

        // Prevent re-processing the same intent on configuration change while keeping clipData permissions intact during copy
        intent.action = Intent.ACTION_MAIN

        if (collectedUris.isNotEmpty()) {
            val urisList = collectedUris.toList()
            _isStagingSharedFiles.value = true
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()).launch {
                try {
                    val staged = urisList.mapNotNull { uri -> copyUriToTempFile(uri) }
                    if (staged.isNotEmpty()) {
                        _pendingSharedFiles.value = staged
                    } else {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            android.widget.Toast.makeText(
                                applicationContext,
                                "Unable to read shared file(s)",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } finally {
                    _isStagingSharedFiles.value = false
                    try {
                        intent.removeExtra(Intent.EXTRA_STREAM)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcutIntent(intent)
        handleShareIntent(intent)
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        permissions.add(Manifest.permission.CAMERA)

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "TeleNest"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                setTaskDescription(
                    android.app.ActivityManager.TaskDescription.Builder()
                        .setLabel("TeleNest")
                        .setIcon(R.drawable.ic_telenest_logo)
                        .setPrimaryColor(android.graphics.Color.WHITE)
                        .build()
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                @Suppress("DEPRECATION")
                setTaskDescription(
                    android.app.ActivityManager.TaskDescription(
                        "TeleNest",
                        R.drawable.ic_telenest_logo,
                        android.graphics.Color.WHITE
                    )
                )
            } else {
                val iconBitmap = android.graphics.BitmapFactory.decodeResource(resources, R.drawable.ic_telenest_logo)
                @Suppress("DEPRECATION")
                setTaskDescription(
                    android.app.ActivityManager.TaskDescription(
                        "TeleNest",
                        iconBitmap,
                        android.graphics.Color.WHITE
                    )
                )
            }
        } catch (_: Exception) {}
        handleShortcutIntent(intent)
        handleShareIntent(intent)
        checkAndRequestPermissions()

        try {
            autosyncManager.reloadFromStorage()
            autosyncManager.ensureBackgroundMonitoringState()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    title = "TeleNest"
                    val now = System.currentTimeMillis()
                    if (lastPauseTime != 0L && (now - lastPauseTime > 30_000)) {
                        securityViewModel.setLocked(true)
                    }
                } else if (event == Lifecycle.Event.ON_PAUSE) {
                    lastPauseTime = System.currentTimeMillis()
                }
            },
        )

        enableEdgeToEdge()
        @OptIn(ExperimentalMaterial3Api::class)
        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val settingsViewModel: SettingsViewModel = hiltViewModel()

            val isDarkModePref by themeViewModel.isDarkMode.collectAsState()
            val themeColor by themeViewModel.themeColor.collectAsState()
            val currentLanguage by settingsViewModel.currentLanguage.collectAsState()
            val updateState by updateRepository.updateState.collectAsState()

            val systemDark = isSystemInDarkTheme()
            val darkTheme = isDarkModePref ?: systemDark

            LaunchedEffect(Unit) {
                updateRepository.checkForUpdates(manual = false)
            }

            val configuration = LocalConfiguration.current

            CompositionLocalProvider(
                LocalConfiguration provides configuration
            ) {
                TeledriveTheme(darkTheme = darkTheme, primaryColor = themeColor) {
                    androidx.compose.material3.Surface(
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val navController = rememberNavController()
                        NavGraph(navController = navController)

                        GlobalUpdateDialog(
                            updateState = updateState,
                            onUpdateNow = { release -> updateRepository.downloadAndInstallUpdate(release) },
                            onDismiss = { updateRepository.resetState() },
                            onDontShowAgain = { tagName -> updateRepository.ignoreReleaseForever(tagName) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalUpdateDialog(
    updateState: UpdateState,
    onUpdateNow: (com.awd.teledrive.data.model.GitHubRelease) -> Unit,
    onDismiss: () -> Unit,
    onDontShowAgain: (String) -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(updateState) {
        when (updateState) {
            is UpdateState.UpToDate -> {
                android.widget.Toast.makeText(context, context.getString(R.string.up_to_date), android.widget.Toast.LENGTH_SHORT).show()
                onDismiss()
            }
            is UpdateState.Error -> {
                android.widget.Toast.makeText(context, context.getString(R.string.err_update_check, updateState.message), android.widget.Toast.LENGTH_SHORT).show()
                onDismiss()
            }
            else -> {}
        }
    }

    when (updateState) {
        is UpdateState.NewVersionAvailable -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.update_available, updateState.release.name.ifBlank { updateState.release.tag_name }),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = updateState.release.tag_name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.cancel)
                            )
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = updateState.release.body,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { onUpdateNow(updateState.release) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.update_now))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.later))
                            }
                            TextButton(
                                onClick = { onDontShowAgain(updateState.release.tag_name) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.dont_show_again))
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {}
            )
        }
        is UpdateState.Downloading -> {
            val pct = (updateState.progress * 100).toInt().coerceIn(0, 100)
            AlertDialog(
                onDismissRequest = {},
                title = {
                    Text(
                        text = stringResource(R.string.downloading_update_title, updateState.release.tag_name),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearProgressIndicator(
                            progress = { updateState.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.downloading_update_perc, pct),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                confirmButton = {}
            )
        }
        else -> {}
    }
}
