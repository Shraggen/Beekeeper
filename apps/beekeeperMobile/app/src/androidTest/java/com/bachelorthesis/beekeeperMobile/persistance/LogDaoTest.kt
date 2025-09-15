package com.bachelorthesis.beekeeperMobile.persistance

import androidx.test.filters.SmallTest
import com.bachelorthesis.beekeeperMobile.di.AppModule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/**
 * Integration tests for the LogDao.
 *
 * These tests run on an Android device/emulator and verify the correctness of the
 * Room database queries. Hilt is used to inject an in-memory version of the database
 * to ensure tests are isolated and do not affect the real application data.
 */
@HiltAndroidTest
@SmallTest
// [FIX] We now uninstall the production AppModule, not the TestDatabaseModule.
// Hilt will automatically find the TestDatabaseModule in the same (androidTest) source set.
@UninstallModules(AppModule::class)
class LogDaoTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var database: AppDatabase // Inject the in-memory database

    @Inject
    lateinit var logDao: LogDao // Inject the DAO that uses the in-memory database

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @After
    fun teardown() {
        database.close() // Close the database after each test to ensure a clean state.
    }

    @Test
    fun insertLog_and_getUnsyncedLogs_returnsCorrectLog() = runTest {
        // Arrange
        val logEntry = LocalLogEntry(id = 1, hiveId = 101, content = "Test Content", isSynced = false)

        // Act
        logDao.insertLog(logEntry)
        val unsyncedLogs = logDao.getUnsyncedLogs()

        // Assert
        assertEquals(1, unsyncedLogs.size)
        assertEquals(logEntry.content, unsyncedLogs[0].content)
        assertEquals(logEntry.hiveId, unsyncedLogs[0].hiveId)
    }

    @Test
    fun markLogsAsSynced_updatesStatusAndRemovesFromUnsyncedQuery() = runTest {
        // Arrange
        val logEntry = LocalLogEntry(id = 2, hiveId = 102, content = "Another Log", isSynced = false)
        logDao.insertLog(logEntry)
        val initialUnsynced = logDao.getUnsyncedLogs()
        assertEquals(1, initialUnsynced.size) // Pre-condition check

        // Act
        logDao.markLogsAsSynced(listOf(logEntry.id))
        val unsyncedLogsAfterSync = logDao.getUnsyncedLogs()

        // Assert
        assertTrue("Unsynced logs list should be empty after marking as synced.", unsyncedLogsAfterSync.isEmpty())

        // Optional: Verify that the log is still in the DB but is marked as synced
        val updatedLog = logDao.getLogById(logEntry.id)
        assertTrue("Updated log should be marked as synced.", updatedLog?.isSynced == true)
    }
}