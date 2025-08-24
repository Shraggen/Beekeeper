package com.bachelorthesis.beekeeperMobile.persistance

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log as AndroidLog // FIX: Alias android.util.Log

/**
 * Repository for managing log entries, acting as a single source of truth for UI/Service.
 * It abstracts away the data source (local Room DB and remote API).
 * Implements offline-first logic for creating logs.
 */
class LogRepository(
    private val logDao: LogDao,
    private val apiService: ApiService // Retrofit service for backend communication
) {
    private val TAG = "LogRepository"

    /**
     * Creates a new log entry.
     * It first saves the log locally (offline-first) and then attempts to sync it later.
     * @param hiveId The ID of the hive.
     * @param content The content of the log entry.
     * @return The locally saved LocalLogEntry.
     */
    suspend fun createLog(hiveId: Int, content: String): LocalLogEntry = withContext(Dispatchers.IO) {
        val newLocalLog = LocalLogEntry(hiveId = hiveId, content = content, isSynced = false)
        val localId = logDao.insertLog(newLocalLog) // Save locally
        AndroidLog.d(TAG, "Log saved locally with ID: $localId for hive $hiveId. Content: $content") // FIX: Use aliased Log

        // Return the locally saved log (with its generated ID)
        return@withContext newLocalLog.copy(id = localId)
    }

    /**
     * Retrieves all unsynced log entries from the local database.
     * This method is primarily used by the synchronization worker.
     * @return A list of unsynced LocalLogEntry objects.
     */
    suspend fun getUnsyncedLogs(): List<LocalLogEntry> = withContext(Dispatchers.IO) {
        logDao.getUnsyncedLogs()
    }

    /**
     * Marks a list of local log entries as synced with the backend.
     * @param logIds The list of local IDs to mark as synced.
     */
    suspend fun markLogsAsSynced(logIds: List<Long>) = withContext(Dispatchers.IO) {
        logDao.markLogsAsSynced(logIds)
    }

    // --- Remote API methods (for direct reads or sync logic) ---

    /**
     * Fetches the last log entry from the backend for a given hive.
     * @param hiveId The ID of the hive.
     * @return The remote Log object, or null if not found/error.
     */
    suspend fun fetchLastLogFromRemote(hiveId: Int): Log? = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getLastLogForHive(hiveId)
            if (response.isSuccessful) {
                return@withContext response.body()
            } else {
                AndroidLog.e(TAG, "Failed to fetch last log from remote for hive $hiveId: ${response.code()}") // FIX: Use aliased Log
            }
        } catch (e: Exception) {
            AndroidLog.e(TAG, "Network error fetching last log for hive $hiveId: ${e.message}", e) // FIX: Use aliased Log
        }
        return@withContext null
    }

    /**
     * Fetches the last task entry from the backend for a given hive.
     * @param hiveId The ID of the hive.
     * @return The remote Task object, or null if not found/error.
     */
    suspend fun fetchLastTaskFromRemote(hiveId: Int): Task? = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getLastTaskForHive(hiveId)
            if (response.isSuccessful) {
                return@withContext response.body()
            } else {
                AndroidLog.e(TAG, "Failed to fetch last task from remote for hive $hiveId: ${response.code()}") // FIX: Use aliased Log
            }
        } catch (e: Exception) {
            AndroidLog.e(TAG, "Network error fetching last task for hive $hiveId: ${e.message}", e) // FIX: Use aliased Log
        }
        return@withContext null
    }

    /**
     * Uploads a single log entry to the backend.
     * This is used by the SyncWorker.
     * @param logEntry The LocalLogEntry to upload.
     * @return true if successful, false otherwise.
     */
    suspend fun uploadLogToRemote(logEntry: LocalLogEntry): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = CreateLogRequest(hiveID = logEntry.hiveId, content = logEntry.content)
            val response = apiService.createLog(request)
            if (response.isSuccessful) {
                AndroidLog.d(TAG, "Successfully uploaded log ${logEntry.id} to backend.") // FIX: Use aliased Log
                return@withContext true
            } else {
                AndroidLog.e(TAG, "Failed to upload log ${logEntry.id} to backend: ${response.code()}") // FIX: Use aliased Log
            }
        } catch (e: Exception) {
            AndroidLog.e(TAG, "Network error uploading log ${logEntry.id}: ${e.message}", e) // FIX: Use aliased Log
        }
        return@withContext false
    }
}