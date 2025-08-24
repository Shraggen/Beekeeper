package com.bachelorthesis.beekeeperMobile.assetManager

import android.app.DownloadManager
import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import com.bachelorthesis.beekeeperMobile.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * A data class to provide structured information about queued downloads.
 * This helps the MainActivity correctly identify which download corresponds to the Vosk model.
 */
data class DownloadRequestInfo(val allIds: List<Long>, val voskDownloadId: Long)

/**
 * Manages the lifecycle of large AI model assets for the Beekeeper application.
 *
 * This class is responsible for:
 * 1.  **Configuration:** Reads model URLs and filenames from BuildConfig to separate config from code.
 * 2.  **Prerequisite Checking:** Verifies if models are fully downloaded and correctly prepared.
 * 3.  **Robust Downloading:** If a model is missing or corrupted, it aggressively cleans the old state
 *     and queues a fresh download using Android's DownloadManager.
 * 4.  **Smart Unzipping:** Correctly unzips the Vosk model, handling zip files that contain a nested root directory.
 * 5.  **Cleanup:** Provides a method to allow the user to delete all downloaded models to free up space.
 * 6.  **Path Provisioning:** Offers clean methods to get the file paths for other components to use.
 */
class AssetManager(private val context: Context) {

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    // Base directory for all models within the app's private external storage.
    // This location is automatically deleted by Android when the app is uninstalled.
    private val baseModelDir = File(context.getExternalFilesDir(null), "models")
    private val voskModelDir = File(baseModelDir, "vosk/model-en-us")
    private val llmModelFile = File(baseModelDir, "llm/${BuildConfig.LLM_MODEL_FILENAME}")

    /**
     * Checks if the Vosk model is fully unzipped and ready.
     */
    private fun isVoskReady(): Boolean {
        val voskMarkerFile = File(voskModelDir, "am/final.mdl")
        return voskMarkerFile.exists() && voskMarkerFile.length() > 0
    }

    /**
     * Checks if the LLM model file is downloaded and not empty.
     */
    private fun isLlmReady(): Boolean {
        return llmModelFile.exists() && llmModelFile.length() > 0
    }

    /**
     * Public method to check if all required model assets are present and ready for use.
     * @return true if all models are present, false otherwise.
     */
    fun checkPrerequisites(): Boolean {
        val voskReady = isVoskReady()
        val llmReady = isLlmReady()
        Log.d(TAG, "Prerequisite check: Vosk ready? $voskReady, LLM ready? $llmReady")
        return voskReady && llmReady
    }

    /**
     * Initiates the download of required models if they are not in a ready state.
     * This method is robust against partially downloaded or corrupted files by cleaning
     * up the old state before starting a new download.
     *
     * @return A [DownloadRequestInfo] object containing the IDs of all queued downloads.
     */
    fun queueModelDownloads(): DownloadRequestInfo {
        val downloadIds = mutableListOf<Long>()
        var voskId = -1L

        // --- Vosk Model Check and Cleanup ---
        if (!isVoskReady()) {
            Log.w(TAG, "Vosk model is not ready. Cleaning up and queueing for download.")
            voskModelDir.deleteRecursively() // Aggressively delete the old state
            voskModelDir.mkdirs() // Re-create the directory for the download

            val voskRequest = DownloadManager.Request(BuildConfig.VOSK_MODEL_URL.toUri())
                .setTitle("Downloading Vosk Speech Model")
                .setDescription("Required for voice commands.")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, "models/vosk", BuildConfig.VOSK_ZIP_FILENAME)
                .setAllowedOverMetered(true)
            val id = downloadManager.enqueue(voskRequest)
            downloadIds.add(id)
            voskId = id
        }

        // --- LLM Model Check and Cleanup ---
        if (!isLlmReady()) {
            Log.w(TAG, "LLM model is not ready. Cleaning up and queueing for download.")
            llmModelFile.delete() // Aggressively delete the old state
            llmModelFile.parentFile?.mkdirs() // Re-create parent directory

            val llmRequest = DownloadManager.Request(BuildConfig.LLM_MODEL_URL.toUri())
                .setTitle("Downloading Language Model")
                .setDescription("Required for intent recognition.")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, "models/llm", BuildConfig.LLM_MODEL_FILENAME)
                .setAllowedOverMetered(true)
            downloadIds.add(downloadManager.enqueue(llmRequest))
        }

        if (downloadIds.isEmpty()) {
            Log.i(TAG, "All models are already present and ready. No downloads queued.")
        }

        return DownloadRequestInfo(allIds = downloadIds, voskDownloadId = voskId)
    }

    /**
     * Unzips the downloaded Vosk model archive. This is a blocking I/O operation
     * and must be called from a background thread. It intelligently handles zip archives
     * that contain a single nested root directory.
     */
    suspend fun unzipVoskModel() = withContext(Dispatchers.IO) {
        val zipFile = File(baseModelDir, "vosk/${BuildConfig.VOSK_ZIP_FILENAME}")
        if (!zipFile.exists()) {
            Log.e(TAG, "Vosk zip file not found, cannot unzip.")
            return@withContext
        }

        Log.i(TAG, "Starting to unzip Vosk model...")
        try {
            // Smartly handle zip files with a single root directory
            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                val firstEntryName = zis.nextEntry?.name ?: ""
                val rootDir = firstEntryName.substringBefore('/')
                val hasSingleRootDir = rootDir.isNotEmpty() && FileInputStream(zipFile).let { fis ->
                    ZipInputStream(fis).use { innerZis ->
                        generateSequence { innerZis.nextEntry }.all { it.name.startsWith("$rootDir/") }
                    }
                }

                // Unzip for real, stripping the root directory if it exists
                FileInputStream(zipFile).use { fis ->
                    ZipInputStream(fis).use { realZis ->
                        var entry = realZis.nextEntry
                        while (entry != null) {
                            val entryName = if (hasSingleRootDir) entry.name.substringAfter("$rootDir/") else entry.name
                            if (entryName.isNotBlank()) {
                                val newFile = File(voskModelDir, entryName)
                                if (!newFile.canonicalPath.startsWith(voskModelDir.canonicalPath)) {
                                    throw SecurityException("Zip Path Traversal Vulnerability detected.")
                                }
                                if (entry.isDirectory) {
                                    newFile.mkdirs()
                                } else {
                                    newFile.parentFile?.mkdirs()
                                    FileOutputStream(newFile).use { fos -> realZis.copyTo(fos) }
                                }
                            }
                            entry = realZis.nextEntry
                        }
                    }
                }
            }
            Log.i(TAG, "Vosk model unzipped successfully.")
            zipFile.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Error unzipping Vosk model", e)
            voskModelDir.deleteRecursively()
        }
    }

    /**
     * Deletes all downloaded model files from the device.
     * This allows the user to free up storage space via a settings menu.
     *
     * @return true if cleanup was successful, false otherwise.
     */
    suspend fun cleanup() = withContext(Dispatchers.IO) {
        if (baseModelDir.exists()) {
            val success = baseModelDir.deleteRecursively()
            Log.i(TAG, "Cleanup of all models ${if (success) "succeeded" else "failed"}.")
            return@withContext success
        }
        Log.i(TAG, "Cleanup unnecessary, model directory does not exist.")
        true
    }

    /**
     * Provides the absolute path to the initialized Vosk model directory.
     */
    fun getVoskModelPath(): File = voskModelDir

    /**
     * Provides the absolute path to the downloaded LLM model file.
     */
    fun getLlmModelPath(): File = llmModelFile

    companion object {
        private const val TAG = "AssetManager"
    }
}