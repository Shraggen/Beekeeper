package com.bachelorthesis.beekeeperMobile.persistance

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The Room database for the Beekeeper application.
 * It holds the local log entries for offline-first persistence.
 */
@Database(entities = [LocalLogEntry::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    // Expose the DAO for accessing log entries
    abstract fun logDao(): LogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Provides a singleton instance of the AppDatabase.
         * @param context The application context.
         * @return The singleton AppDatabase instance.
         */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "beekeeper_database" // Name of our database file
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}