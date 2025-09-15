package com.bachelorthesis.beekeeperMobile.sync

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.bachelorthesis.beekeeperMobile.persistance.LocalLogEntry
import com.bachelorthesis.beekeeperMobile.persistance.LogRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Inject

/**
 * Unit tests for the SyncWorker.
 *
 * This test class uses Hilt for dependency injection to provide a mock LogRepository
 * to the worker. It leverages the WorkManager testing library to run the worker
 * synchronously and verify its logic under different conditions.
 */
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(RobolectricTestRunner::class)
class SyncWorkerTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var logRepository: LogRepository // Hilt will inject the mock from TestAppModule

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun `doWork returns success when there are no logs to sync`() = runTest {
        // Arrange
        // Mock the repository to return an empty list.
        coEvery { logRepository.getUnsyncedLogs() } returns emptyList()

        val worker = TestListenableWorkerBuilder<SyncWorker>(context)
            .setWorkerFactory(workerFactory)
            .build()

        // Act
        val result = worker.doWork()

        // Assert
        assertEquals(ListenableWorker.Result.success(), result)
        // Verify that no network calls were made.
        coVerify(exactly = 0) { logRepository.uploadLogToRemote(any()) }
    }

    @Test
    fun `doWork returns success and syncs logs when upload succeeds`() = runTest {
        // Arrange
        val unsyncedLogs = listOf(
            LocalLogEntry(id = 1, hiveId = 1, content = "Log 1"),
            LocalLogEntry(id = 2, hiveId = 2, content = "Log 2")
        )
        val syncedIdsSlot = slot<List<Long>>()

        coEvery { logRepository.getUnsyncedLogs() } returns unsyncedLogs
        coEvery { logRepository.uploadLogToRemote(any()) } returns true // Simulate successful upload
        coEvery { logRepository.markLogsAsSynced(capture(syncedIdsSlot)) } returns Unit

        val worker = TestListenableWorkerBuilder<SyncWorker>(context)
            .setWorkerFactory(workerFactory)
            .build()

        // Act
        val result = worker.doWork()

        // Assert
        assertEquals(ListenableWorker.Result.success(), result)
        // Verify that upload was attempted for all logs.
        coVerify(exactly = unsyncedLogs.size) { logRepository.uploadLogToRemote(any()) }
        // Verify that the correct log IDs were marked as synced.
        assertEquals(unsyncedLogs.map { it.id }, syncedIdsSlot.captured)
    }

    @Test
    fun `doWork returns retry when upload fails`() = runTest {
        // Arrange
        val unsyncedLogs = listOf(LocalLogEntry(id = 3, hiveId = 3, content = "Log 3"))
        coEvery { logRepository.getUnsyncedLogs() } returns unsyncedLogs
        coEvery { logRepository.uploadLogToRemote(any()) } returns false // Simulate failed upload

        val worker = TestListenableWorkerBuilder<SyncWorker>(context)
            .setWorkerFactory(workerFactory)
            .build()

        // Act
        val result = worker.doWork()

        // Assert
        assertEquals(ListenableWorker.Result.retry(), result)
        // Verify that NO logs were marked as synced.
        coVerify(exactly = 0) { logRepository.markLogsAsSynced(any()) }
    }
}