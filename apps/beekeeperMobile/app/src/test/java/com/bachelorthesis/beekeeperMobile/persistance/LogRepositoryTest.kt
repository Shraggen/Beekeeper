package com.bachelorthesis.beekeeperMobile.persistance

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.io.IOException

/**
 * Unit tests for the LogRepository.
 *
 * These tests use MockK to create mock dependencies for the LogDao and ApiService.
 * This allows us to test the repository's logic in isolation, controlling the behavior
 * of the database and the remote API to verify all possible scenarios (success, failure, etc.).
 *
 * [FIX] This test now runs with Robolectric to provide a functional implementation
 * of Android framework classes like android.util.Log, preventing "not mocked" errors.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class LogRepositoryTest {

    // This rule initializes all the @MockK annotated fields automatically.
    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    // Create mock objects for the dependencies.
    @MockK
    private lateinit var logDao: LogDao

    @MockK
    private lateinit var apiService: ApiService

    // The class we are testing.
    private lateinit var logRepository: LogRepository

    @Before
    fun setUp() {
        hiltRule.inject()
        // Create a new instance of the repository before each test, injecting the mocks.
        logRepository = LogRepository(logDao, apiService)
    }

    @Test
    fun `createLog should insert a new log into the local DAO`() = runTest {
        // Arrange
        val hiveId = 101
        val content = "Queen spotted and healthy."
        val logEntrySlot = slot<LocalLogEntry>() // A slot to capture the argument passed to the DAO.

        // Define the behavior of the mock: when insertLog is called, just return 1L.
        // We also capture the object that was passed into the 'logEntrySlot'.
        coEvery { logDao.insertLog(capture(logEntrySlot)) } returns 1L

        // Act
        logRepository.createLog(hiveId, content)

        // Assert
        // Verify that the insertLog method on our mock DAO was called exactly once.
        coVerify(exactly = 1) { logDao.insertLog(any()) }

        // Check the captured object to make sure the repository created it correctly.
        assertEquals(hiveId, logEntrySlot.captured.hiveId)
        assertEquals(content, logEntrySlot.captured.content)
        assertEquals(false, logEntrySlot.captured.isSynced)
    }

    @Test
    fun `fetchLastLogFromRemote should return log on successful API call`() = runTest {
        // Arrange
        val hiveId = 102
        val fakeRemoteLog = Log(id = 1, hiveId = hiveId, content = "Test log", createdAt = "", updatedAt = "")
        val successResponse = Response.success(fakeRemoteLog)

        // Define the mock behavior: when the apiService is called, return our fake successful response.
        coEvery { apiService.getLastLogForHive(hiveId) } returns successResponse

        // Act
        val result = logRepository.fetchLastLogFromRemote(hiveId)

        // Assert
        assertEquals(fakeRemoteLog, result)
    }

    @Test
    fun `fetchLastLogFromRemote should return null on API exception`() = runTest {
        // Arrange
        val hiveId = 103

        // Define the mock behavior: when the apiService is called, throw an IOException.
        coEvery { apiService.getLastLogForHive(hiveId) } throws IOException("Network failed")

        // Act
        val result = logRepository.fetchLastLogFromRemote(hiveId)

        // Assert
        assertNull(result)
    }

    @Test
    fun `uploadLogToRemote should return true on successful API call`() = runTest {
        // Arrange
        val localLog = LocalLogEntry(id = 1, hiveId = 104, content = "To be uploaded", isSynced = false)
        val fakeApiResponse = Log(id = 2, hiveId = 104, content = "To be uploaded", createdAt = "", updatedAt = "")
        val successResponse = Response.success(fakeApiResponse)

        // Define mock behavior for the POST call.
        coEvery { apiService.createLog(any()) } returns successResponse

        // Act
        val result = logRepository.uploadLogToRemote(localLog)

        // Assert
        assertTrue(result)
        // Verify that the createLog method was indeed called on our mock apiService.
        coVerify(exactly = 1) { apiService.createLog(any()) }
    }
}