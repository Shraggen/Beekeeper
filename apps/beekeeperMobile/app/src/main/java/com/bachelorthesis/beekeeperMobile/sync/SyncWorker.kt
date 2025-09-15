package com.bachelorthesis.beekeeperMobile.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bachelorthesis.beekeeperMobile.persistance.LogRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import android.util.Log as AndroidLog

/**
 * A Worker that handles the synchronization of unsynced log entries from the local
 * Room database to the remote backend API.
 * It uses the LogRepository to manage data access.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val logRepository: LogRepository
) : CoroutineWorker(appContext, workerParams) {

    private val TAG = "SyncWorker"

    override suspend fun doWork(): Result {
        AndroidLog.i(TAG, "Starting log synchronization task...")

        val unsyncedLogs = logRepository.getUnsyncedLogs()
        if (unsyncedLogs.isEmpty()) {
            AndroidLog.i(TAG, "No unsynced logs found. Sync task finished.")
            return Result.success()
        }

        AndroidLog.d(TAG, "Found ${unsyncedLogs.size} unsynced logs to upload.")

        val successfullyUploadedLogIds = mutableListOf<Long>()
        var hasError = false

        for (logEntry in unsyncedLogs) {
            val uploaded = logRepository.uploadLogToRemote(logEntry)
            if (uploaded) {
                successfullyUploadedLogIds.add(logEntry.id)
            } else {
                hasError = true
                AndroidLog.w(TAG, "Failed to upload log with local ID: ${logEntry.id} to remote.")
            }
        }

        if (successfullyUploadedLogIds.isNotEmpty()) {
            logRepository.markLogsAsSynced(successfullyUploadedLogIds)
            AndroidLog.i(TAG, "Successfully synced ${successfullyUploadedLogIds.size} logs.")
        }

        return if (hasError) {
            AndroidLog.w(TAG, "Sync task completed with some failures. Retrying later.")
            Result.retry() // Retry if there were any failures
        } else {
            AndroidLog.i(TAG, "Sync task completed successfully.")
            Result.success()
        }
    }

    companion object {
        const val WORK_NAME = "LogSyncWorker"
    }
}