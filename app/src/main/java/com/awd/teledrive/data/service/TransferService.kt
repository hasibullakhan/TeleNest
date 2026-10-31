package com.awd.teledrive.data.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.awd.teledrive.MainActivity
import com.awd.teledrive.R
import com.awd.teledrive.data.repository.TransferRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class TransferService : Service() {

    @Inject
    lateinit var transferRepository: TransferRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val NOTIFICATION_ID = 1001
    private val COMPLETED_NOTIFICATION_ID = 1003
    private val CHANNEL_ID = "transfer_channel"

    companion object {
        const val ACTION_CANCEL_TRANSFER = "com.awd.teledrive.ACTION_CANCEL_TRANSFER"
        const val ACTION_CANCEL_ALL = "com.awd.teledrive.ACTION_CANCEL_ALL"
        const val EXTRA_UNIQUE_ID = "extra_unique_id"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL_TRANSFER -> {
                val uniqueId = intent.getStringExtra(EXTRA_UNIQUE_ID)
                if (uniqueId != null) {
                    transferRepository.cancelTransfer(uniqueId)
                }
            }
            ACTION_CANCEL_ALL -> {
                transferRepository.transfers.value.values
                    .filter {
                        it.status == TransferRepository.Status.DOWNLOADING ||
                            it.status == TransferRepository.Status.UPLOADING ||
                            it.status == TransferRepository.Status.QUEUED
                    }
                    .forEach { transferRepository.cancelTransfer(it.remoteUniqueId) }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Dismiss any previous completed summary notification when a new transfer service session starts
        notificationManager.cancel(COMPLETED_NOTIFICATION_ID)

        val notification = createNotification(getString(R.string.preparing), 0f, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        serviceScope.launch {
            delay(400)
            transferRepository.transfers.collectLatest { transfers ->
                val allValues = transfers.values.toList()
                val transferring = allValues.filter {
                    it.status != TransferRepository.Status.COMPLETED &&
                        it.status != TransferRepository.Status.CANCELLED &&
                        !it.status.startsWith(TransferRepository.Status.FAILED) &&
                        !it.status.startsWith("Gagal") &&
                        !it.status.startsWith("Failed")
                }
                val completed = allValues.filter { it.status == TransferRepository.Status.COMPLETED }

                if (transferring.isEmpty()) {
                    if (completed.isNotEmpty()) {
                        val completedUploads = completed.filter { !it.isDownload }
                        val completedDownloads = completed.filter { it.isDownload }

                        val summaryText = when {
                            completedUploads.isNotEmpty() -> {
                                if (completedUploads.size == 1) {
                                    getString(R.string.upload_completed_single)
                                } else {
                                    getString(R.string.uploads_completed_count, completedUploads.size)
                                }
                            }
                            completedDownloads.isNotEmpty() -> {
                                if (completedDownloads.size == 1) {
                                    getString(R.string.download_completed_single)
                                } else {
                                    getString(R.string.downloads_completed_count, completedDownloads.size)
                                }
                            }
                            else -> getString(R.string.all_transfers_completed)
                        }

                        // Briefly show 100% on the ongoing notification before detaching foreground
                        val fullProgressNotification = createNotification(summaryText, 1f, false)
                        notificationManager.notify(NOTIFICATION_ID, fullProgressNotification)
                        delay(250)

                        stopForeground(STOP_FOREGROUND_REMOVE)
                        // Post persistent summary notification with a separate ID so stopForeground never removes it
                        val completedNotification = createCompletedSummaryNotification(summaryText)
                        notificationManager.notify(COMPLETED_NOTIFICATION_ID, completedNotification)
                    } else {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    }
                    stopSelf()
                } else {
                    val activeUploads = transferring.filter { !it.isDownload }
                    val activeDownloads = transferring.filter { it.isDownload }

                    // Prioritize uploads if any uploads are active so stale downloads never hijack upload notifications
                    val isUploadingSession = activeUploads.isNotEmpty()
                    val activeSessionItems = if (isUploadingSession) activeUploads else activeDownloads
                    val completedSessionItems = if (isUploadingSession) {
                        completed.filter { !it.isDownload }
                    } else {
                        completed.filter { it.isDownload }
                    }

                    val totalBatchCount = activeSessionItems.size + completedSessionItems.size
                    val allBatchItems = completedSessionItems + activeSessionItems

                    val totalBatchBytes = allBatchItems.sumOf { it.totalSize }
                    val transferredBatchBytes = completedSessionItems.sumOf { it.totalSize } +
                        activeSessionItems.sumOf {
                            maxOf(it.downloadedSize, (it.progress.coerceIn(0f, 1f) * it.totalSize).toLong())
                        }

                    val progress = when {
                        totalBatchCount == 1 -> activeSessionItems.first().progress.coerceIn(0f, 1f)
                        totalBatchBytes > 0L -> (transferredBatchBytes.toFloat() / totalBatchBytes.toFloat()).coerceIn(0f, 0.99f)
                        totalBatchCount > 0 -> {
                            val sumProgress = completedSessionItems.size.toFloat() +
                                activeSessionItems.sumOf { it.progress.coerceIn(0f, 1f).toDouble() }.toFloat()
                            (sumProgress / totalBatchCount.toFloat()).coerceIn(0f, 0.99f)
                        }
                        else -> 0f
                    }

                    val percentage = (progress * 100f).roundToInt().coerceIn(0, 100)

                    val notification = if (totalBatchCount == 1 && activeSessionItems.size == 1) {
                        val transfer = activeSessionItems.first()
                        val text = when {
                            transfer.status.startsWith("Memecah") || transfer.status.startsWith("Splitting") ->
                                getString(R.string.splitting_file, transfer.fileName)
                            transfer.status.startsWith("Menggabungkan") || transfer.status.startsWith("Merging") ->
                                getString(R.string.merging_file, transfer.fileName)
                            transfer.isDownload -> getString(R.string.downloading_file, transfer.fileName)
                            else -> getString(R.string.uploading_file, transfer.fileName)
                        }
                        createNotification(
                            "$text ($percentage%)",
                            progress,
                            true,
                            transfer.remoteUniqueId
                        )
                    } else {
                        val currentIndex = (completedSessionItems.size + 1).coerceAtMost(totalBatchCount)
                        val text = if (isUploadingSession) {
                            getString(R.string.uploading_batch_progress, currentIndex, totalBatchCount)
                        } else {
                            getString(R.string.downloading_batch_progress, currentIndex, totalBatchCount)
                        }
                        createNotification("$text ($percentage%)", progress, true, null, true)
                    }

                    notificationManager.notify(NOTIFICATION_ID, notification)
                }
            }
        }
    }

    private fun createCompletedSummaryNotification(content: String): android.app.Notification {
        val appLogoBitmap = try {
            android.graphics.BitmapFactory.decodeResource(resources, R.drawable.ic_telenest_logo)
        } catch (_: Exception) {
            null
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_stat_telenest)
            .setLargeIcon(appLogoBitmap)
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
    }

    private fun createNotification(
        content: String,
        progress: Float,
        ongoing: Boolean,
        uniqueId: String? = null,
        isMulti: Boolean = false
    ): android.app.Notification {
        val appLogoBitmap = try {
            android.graphics.BitmapFactory.decodeResource(resources, R.drawable.ic_telenest_logo)
        } catch (_: Exception) {
            null
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_stat_telenest)
            .setLargeIcon(appLogoBitmap)
            .setProgress(100, (progress * 100f).roundToInt().coerceIn(0, 100), false)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )

        if (ongoing) {
            if (uniqueId != null) {
                val cancelIntent = Intent(this, TransferService::class.java).apply {
                    action = ACTION_CANCEL_TRANSFER
                    putExtra(EXTRA_UNIQUE_ID, uniqueId)
                }
                val pendingCancel = PendingIntent.getService(
                    this,
                    uniqueId.hashCode(),
                    cancelIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.cancel), pendingCancel)
            } else if (isMulti) {
                val cancelAllIntent = Intent(this, TransferService::class.java).apply {
                    action = ACTION_CANCEL_ALL
                }
                val pendingCancelAll = PendingIntent.getService(
                    this,
                    1002,
                    cancelAllIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.cancel_all), pendingCancelAll)
            }
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "${getString(R.string.app_name)} ${getString(R.string.transfers)}",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
