package com.awd.teledrive.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.awd.teledrive.R
import com.awd.teledrive.data.repository.AutosyncManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MediaBackupWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val NOTIFICATION_ID = 101
        private const val CHANNEL_ID = "backup_channel"
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface MediaBackupWorkerEntryPoint {
        fun autosyncManager(): AutosyncManager
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            setForeground(createForegroundInfo(0, 0, ""))

            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                MediaBackupWorkerEntryPoint::class.java
            )
            val autosyncManager = entryPoint.autosyncManager()
            val isManual = inputData.getBoolean("is_manual", false)

            val progressJob = launch {
                autosyncManager.activeStatus.collect { status ->
                    if (status.isRunning) {
                        val subtitle = if (status.currentFileName.isNotEmpty()) {
                            "${status.currentAction}: ${status.currentFileName}"
                        } else {
                            status.statusMessage
                        }
                        runCatching {
                            setForeground(
                                createForegroundInfo(
                                    current = status.processedCount,
                                    total = status.totalCount,
                                    detailText = subtitle
                                )
                            )
                        }
                    }
                }
            }

            autosyncManager.runScheduledSync(isManual = isManual, onlyInstantPairs = false)
            progressJob.cancel()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun createForegroundInfo(current: Int, total: Int, detailText: String): ForegroundInfo {
        createNotificationChannel()
        val title = "${applicationContext.getString(R.string.app_name)} • Autosync"
        val content = when {
            detailText.isNotEmpty() -> detailText
            total > 0 -> applicationContext.getString(R.string.backing_up_media_count, current, total)
            else -> applicationContext.getString(R.string.scanning_media)
        }

        val appLogoBitmap = try {
            android.graphics.BitmapFactory.decodeResource(applicationContext.resources, R.drawable.ic_telenest_logo)
        } catch (_: Exception) {
            null
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(title)
            .setTicker(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_stat_telenest)
            .setLargeIcon(appLogoBitmap)
            .setOngoing(true)
            .setProgress(total, current, total == 0)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "${applicationContext.getString(R.string.app_name)} Autosync"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
