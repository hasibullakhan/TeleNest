package com.awd.teledrive.ui.screens.settings

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.awd.teledrive.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    var showChangelogDialog by remember { mutableStateOf(false) }
    var showLicenseDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showChangelogDialog) {
        val currentVersionChangelog = remember(viewModel.versionName, viewModel.versionCode) {
            buildString {
                append("Version ${viewModel.versionName} (Build ${viewModel.versionCode}) — Latest Release\n")
                append("• 30-Day Recycle Bin (Trash): Soft-delete files & folders with instant 1-tap Restore to original folder or Delete Forever\n")
                append("• Available Offline Vault: Save single or batch files for permanent offline viewing without internet (protected from cache clearing)\n")
                append("• Recent Uploads Section: Quickly view all files uploaded in the last 7 days from the navigation drawer\n")
                append("• External Share to TeleNest: Share 1 to 1,000+ files directly from any Android app into any TeleNest folder\n")
                append("• Redesigned Upload Mode Selector: Distinct Original Lossless vs Encrypted (E2EE) cards with destination folder picker\n")
                append("• Smart In-App APK Updater: Automatic startup update popup with direct in-app download & package installer\n")
                append("• 3-Way Theme Mode: Switch seamlessly between Light, Dark, and Use System Default\n")
                append("• 100% Exact Decimal File Size & Zero-Compression Lossless Document Upload\n")
                append("• Smart Scroll-Aware '+ New' Button & Branded Status Bar Notification Icon")
            }
        }
        val historicalChangelogs = remember {
            listOf(
                "Version 2.8.0\n" +
                    "• Google Drive-style Grid & List layouts with custom 24-color folder palette\n" +
                    "• Pin any Channel or Virtual Folder directly to the Android Home Screen\n" +
                    "• Interactive Breadcrumb navigation & nested subfolder creation inside Move/Copy dialog\n" +
                    "• Dedicated Telegram Account Profile screen with high-res avatar preview\n" +
                    "• Multi-language localization including English, Bengali (বাংলা), Hindi, Arabic, Spanish & more",
                "Version 2.5.0\n" +
                    "• Redesigned Transfers Manager with real-time speed, byte progress, and batch controls\n" +
                    "• Built-in Media Player (ExoPlayer), PDF Viewer, APK Inspector, and Code/Text Previewer\n" +
                    "• Smart Search & Category Filters (Photos, Videos, Audio, Documents, APKs)",
                "Version 2.0.0\n" +
                    "• Complete Material You (Material 3) UI overhaul & TeleNest rebranding\n" +
                    "• Hybrid Cloud Architecture combining Telegram Channel Folders and Virtual Folders\n" +
                    "• Automated Cloud Metadata Backup & Restore via Saved Messages",
                context.getString(R.string.changelog_v130),
                context.getString(R.string.changelog_v120),
                context.getString(R.string.changelog_v110),
                context.getString(R.string.changelog_v100)
            )
        }
        val allChangelogs = remember(currentVersionChangelog, historicalChangelogs) {
            listOf(currentVersionChangelog) + historicalChangelogs
        }

        AlertDialog(
            onDismissRequest = { showChangelogDialog = false },
            title = { Text(stringResource(R.string.whats_new)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    allChangelogs.forEachIndexed { index, entryText ->
                        val lines = entryText.lines()
                        val headerLine = lines.firstOrNull().orEmpty()
                        val bodyLines = lines.drop(1).joinToString("\n")
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = headerLine,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                            if (bodyLines.isNotBlank()) {
                                Text(
                                    text = bodyLines,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (index != allChangelogs.lastIndex) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showChangelogDialog = false }) {
                    Text(stringResource(R.string.confirm))
                }
            }
        )
    }

    if (showLicenseDialog) {
        AlertDialog(
            onDismissRequest = { showLicenseDialog = false },
            title = { Text(stringResource(R.string.license)) },
            text = {
                Text(
                    text = stringResource(R.string.mit_license_text),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = { showLicenseDialog = false }) {
                    Text(stringResource(R.string.confirm))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.about_app)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                modifier = Modifier.statusBarsPadding()
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    tonalElevation = 2.dp,
                    shadowElevation = 2.dp,
                    modifier = Modifier.padding(4.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_telenest_logo),
                        contentDescription = stringResource(R.string.telenest_app_name),
                        modifier = Modifier
                            .padding(8.dp)
                            .size(72.dp)
                    )
                }
            }
            SettingsCategory(title = stringResource(R.string.version))
            SettingsClickableRow(
                icon = Icons.Default.Info,
                title = stringResource(R.string.telenest_app_name),
                subtitle = "v${viewModel.versionName} (${viewModel.versionCode}) • ${stringResource(R.string.version_history)}",
                onClick = { showChangelogDialog = true }
            )
            
            SettingsClickableRow(
                icon = Icons.Default.Refresh,
                title = stringResource(R.string.check_for_updates),
                onClick = viewModel::checkForUpdates
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SettingsCategory(title = stringResource(R.string.links))
            
            SettingsClickableRow(
                icon = Icons.Default.Public,
                title = stringResource(R.string.official_website),
                subtitle = "telenest.hasibulla.live",
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, "https://telenest.hasibulla.live/".toUri())
                    context.startActivity(intent)
                }
            )

            SettingsClickableRow(
                icon = Icons.Default.Code,
                title = stringResource(R.string.github_and_issues),
                subtitle = "github.com/Hasibulla-Khan/TeleNest",
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, "https://github.com/Hasibulla-Khan/TeleNest".toUri())
                    context.startActivity(intent)
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SettingsCategory(title = stringResource(R.string.legal))

            SettingsClickableRow(
                icon = Icons.Default.Person,
                title = stringResource(R.string.creator),
                subtitle = "Created by Hasibulla Khan",
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, "https://www.facebook.com/HasibullaKhanHK".toUri())
                    context.startActivity(intent)
                }
            )

            SettingsClickableRow(
                icon = Icons.Default.Gavel,
                title = stringResource(R.string.license),
                subtitle = stringResource(R.string.mit_license),
                onClick = { showLicenseDialog = true }
            )
        }
    }
}
