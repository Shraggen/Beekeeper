package com.bachelorthesis.beekeeperMobile.persistance

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Data Access Object (DAO) for the LocalLogEntry entity.
 * Defines the database operations for local log entries.
 */
@Dao
interface LogDao {
    /**
     * Inserts a new log entry into the local database.
     * If a log with the same primary key exists, it replaces it.
     * @param logEntry The LocalLogEntry to insert.
     * @return The row ID of the newly inserted log entry.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(logEntry: LocalLogEntry): Long

    /**
     * Retrieves all log entries that have not yet been synced to the backend.
     * @return A list of unsynced LocalLogEntry objects.
     */
    @Query("SELECT * FROM logs WHERE isSynced = 0")
    suspend fun getUnsyncedLogs(): List<LocalLogEntry>

    /**
     * Marks a list of log entries as synced in the local database.
     * @param logIds A list of primary keys (ids) of the log entries to mark as synced.
     */
    @Query("UPDATE logs SET isSynced = 1 WHERE id IN (:logIds)")
    suspend fun markLogsAsSynced(logIds: List<Long>)

    /**
     * Retrieves a single log entry by its local ID. Useful for testing or debugging.
     * @param id The local ID of the log entry.
     * @return The LocalLogEntry if found, null otherwise.
     */
    @Query("SELECT * FROM logs WHERE id = :id LIMIT 1")
    suspend fun getLogById(id: Long): LocalLogEntry?
}