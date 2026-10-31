package com.awd.teledrive.data.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.awd.teledrive.data.secure.SecureSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TeleDriveWorkerManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val secureSettings: SecureSettings,
    private val settingsRepository: com.awd.teledrive.data.repository.SettingsRepository
) {
    fun scheduleAutosyncWork(
        intervalMinutes: Int,
        wifiOnly: Boolean,
        chargingOnly: Boolean
    ) {
        try {
            if (intervalMinutes <= 0) {
                cancelPeriodicBackup()
                return
            }

            // WorkManager minimum periodic interval is 15 minutes
            val workIntervalMins = intervalMinutes.coerceAtLeast(15).toLong()
            val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .setRequiresCharging(chargingOnly)
                .build()

            val backupRequest = PeriodicWorkRequestBuilder<MediaBackupWorker>(
                workIntervalMins,
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "media_backup_worker",
                ExistingPeriodicWorkPolicy.UPDATE,
                backupRequest
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun cancelPeriodicBackup() {
        try {
            WorkManager.getInstance(context).cancelUniqueWork("media_backup_worker")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun scheduleBackup(enabled: Boolean) {
        if (!enabled) {
            cancelPeriodicBackup()
            return
        }
        scheduleAutosyncWork(
            intervalMinutes = 60,
            wifiOnly = settingsRepository.isBackupWifiOnlyEnabled.value,
            chargingOnly = secureSettings.getBoolean("backup_charging_only", false)
        )
    }

    fun triggerOneTimeBackup() {
        try {
            val isWifiOnly = settingsRepository.isBackupWifiOnlyEnabled.value
            val networkType = if (isWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED

            val backupRequest = OneTimeWorkRequestBuilder<MediaBackupWorker>()
                .setInputData(Data.Builder().putBoolean("is_manual", true).build())
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(networkType)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "manual_backup_worker",
                ExistingWorkPolicy.REPLACE,
                backupRequest
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun scheduleCoreRefresh() {
        val refreshRequest = PeriodicWorkRequestBuilder<BackupWorker>(6, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "core_refresh_worker",
            ExistingPeriodicWorkPolicy.KEEP,
            refreshRequest
        )
    }

    fun getBackupWorkInfo(): kotlinx.coroutines.flow.Flow<List<WorkInfo>> {
        return WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow("media_backup_worker")
    }
}
