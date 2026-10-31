package com.awd.teledrive.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.awd.teledrive.core.utils.VersionUtils
import com.awd.teledrive.data.model.GitHubRelease
import com.awd.teledrive.data.secure.SecureSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class NewVersionAvailable(val release: GitHubRelease) : UpdateState()
    data class Downloading(val release: GitHubRelease, val progress: Float) : UpdateState()
    object UpToDate : UpdateState()
    data class Error(val message: String) : UpdateState()
}

@Singleton
class UpdateRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val secureSettings: SecureSettings
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState = _updateState.asStateFlow()

    private var hasDismissedUpdateForSession = false

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    companion object {
        private const val KEY_IGNORED_UPDATE_TAG = "ignored_update_tag"
    }

    suspend fun checkForUpdates(manual: Boolean = false) = withContext(Dispatchers.IO) {
        if (_updateState.value is UpdateState.Checking || _updateState.value is UpdateState.Downloading) return@withContext
        if (!manual && hasDismissedUpdateForSession) return@withContext

        _updateState.value = UpdateState.Checking

        try {
            val url = URL("https://api.github.com/repos/hasibullakhan/TeleNest/releases/latest")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.setRequestProperty("User-Agent", "TeleNest-Android")

            if (connection.responseCode == 200) {
                val responseBody = connection.inputStream.bufferedReader().readText()
                val latestRelease = json.decodeFromString<GitHubRelease>(responseBody)

                val currentVersion = VersionUtils.getVersionName(context)
                val ignoredTag = secureSettings.getString(KEY_IGNORED_UPDATE_TAG, "") ?: ""

                if (isNewerVersion(latestRelease.tag_name, currentVersion)) {
                    if (!manual && ignoredTag.equals(latestRelease.tag_name, ignoreCase = true)) {
                        _updateState.value = UpdateState.Idle
                    } else {
                        _updateState.value = UpdateState.NewVersionAvailable(latestRelease)
                    }
                } else {
                    _updateState.value = if (manual) UpdateState.UpToDate else UpdateState.Idle
                }
            } else {
                _updateState.value = if (manual) UpdateState.Error("HTTP Error: ${connection.responseCode}") else UpdateState.Idle
            }
        } catch (e: Exception) {
            Log.e("UpdateRepository", "Failed to check for updates", e)
            _updateState.value = if (manual) UpdateState.Error(e.message ?: "Unknown error") else UpdateState.Idle
        }
    }

    fun resetState() {
        if (_updateState.value is UpdateState.NewVersionAvailable) {
            hasDismissedUpdateForSession = true
        }
        if (_updateState.value !is UpdateState.Downloading) {
            _updateState.value = UpdateState.Idle
        }
    }

    fun ignoreReleaseForever(tagName: String) {
        secureSettings.saveString(KEY_IGNORED_UPDATE_TAG, tagName)
        hasDismissedUpdateForSession = true
        _updateState.value = UpdateState.Idle
    }

    fun downloadAndInstallUpdate(release: GitHubRelease) {
        val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
        val apkUrl = apkAsset?.browser_download_url?.takeIf { it.isNotBlank() }
        if (apkUrl == null) {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(release.html_url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
            } catch (_: Exception) {}
            _updateState.value = UpdateState.Idle
            return
        }

        scope.launch {
            try {
                val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
                val safeTag = release.tag_name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val targetApk = File(updatesDir, "TeleNest-$safeTag.apk")
                val expectedSize = apkAsset.size

                if (targetApk.exists() && ((expectedSize > 0L && targetApk.length() == expectedSize) || (expectedSize <= 0L && targetApk.length() > 10_000_000L))) {
                    _updateState.value = UpdateState.Idle
                    withContext(Dispatchers.Main) {
                        launchApkInstaller(targetApk)
                    }
                    return@launch
                }

                val tempApk = File(updatesDir, "TeleNest-$safeTag.apk.part")
                if (tempApk.exists()) tempApk.delete()

                _updateState.value = UpdateState.Downloading(release, 0f)

                var currentUrl = apkUrl
                var redirects = 0
                var conn: HttpURLConnection
                while (true) {
                    conn = URL(currentUrl).openConnection() as HttpURLConnection
                    conn.instanceFollowRedirects = true
                    conn.connectTimeout = 15000
                    conn.readTimeout = 30000
                    conn.setRequestProperty("User-Agent", "TeleNest-Android")
                    val code = conn.responseCode
                    if (code in 300..399 && redirects < 5) {
                        val loc = conn.getHeaderField("Location")
                        conn.disconnect()
                        if (!loc.isNullOrBlank()) {
                            currentUrl = loc
                            redirects++
                            continue
                        }
                    }
                    break
                }

                if (conn.responseCode != 200) {
                    _updateState.value = UpdateState.Error("Download failed (HTTP ${conn.responseCode})")
                    return@launch
                }

                val totalBytes = conn.contentLengthLong.takeIf { it > 0L } ?: expectedSize
                var downloadedBytes = 0L
                val buffer = ByteArray(64 * 1024)

                conn.inputStream.use { input ->
                    FileOutputStream(tempApk).use { output ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            if (totalBytes > 0L) {
                                val progress = (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                _updateState.value = UpdateState.Downloading(release, progress)
                            }
                        }
                        output.flush()
                    }
                }

                if (targetApk.exists()) targetApk.delete()
                tempApk.renameTo(targetApk)

                _updateState.value = UpdateState.Idle
                withContext(Dispatchers.Main) {
                    launchApkInstaller(targetApk)
                }
            } catch (e: Exception) {
                Log.e("UpdateRepository", "Failed to download APK update", e)
                _updateState.value = UpdateState.Error(e.message ?: "Download failed")
            }
        }
    }

    private fun launchApkInstaller(apkFile: File) {
        try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e("UpdateRepository", "Failed to launch APK installer directly, requesting unknown sources permission", e)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val permIntent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(permIntent)
                } catch (permEx: Exception) {
                    Log.e("UpdateRepository", "Failed to open unknown app sources settings", permEx)
                }
            }
        }
    }

    private fun isNewerVersion(latestTag: String, currentVersion: String): Boolean {
        val latest = latestTag.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
        val current = currentVersion.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }

        for (i in 0 until minOf(latest.size, current.size)) {
            if (latest[i] > current[i]) return true
            if (latest[i] < current[i]) return false
        }
        return latest.size > current.size
    }
}
