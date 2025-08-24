package com.bachelorthesis.beekeeperMobile.persistance

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a log entry stored locally in the Room database.
 * This entity is designed for offline-first persistence and includes a sync status.
 */
@Entity(tableName = "logs") // Specify the table name for clarity
data class LocalLogEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0, // Unique ID for the local database, auto-generated
    val hiveId: Int,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(), // Timestamp of local creation
    var isSynced: Boolean = false // Flag to track if this log has been synced to the backend
)