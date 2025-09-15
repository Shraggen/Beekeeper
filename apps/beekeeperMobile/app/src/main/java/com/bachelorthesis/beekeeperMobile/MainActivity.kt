package com.bachelorthesis.beekeeperMobile

import android.Manifest
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log as AndroidLog
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bachelorthesis.beekeeperMobile.assetManager.AssetManager
import com.bachelorthesis.beekeeperMobile.assetManager.DownloadRequestInfo
import com.bachelorthesis.beekeeperMobile.settings.SettingsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The main entry point of the application.
 *
 * This activity orchestrates the setup process:
 * 1.  Permissions: Verifies that necessary permissions are granted.
 * 2.  AI Models: Uses the AssetManager to check for, download, and prepare the models.
 *
 * NOTE: An explicit check for OS-level language packs was removed, as it proved to be
 * unreliable on some devices. We now rely on the SpeechEngine's own error handling to
 * manage speech recognition availability.
 */
class MainActivity : AppCompatActivity() {

    private val PERMISSIONS_REQUEST_CODE = 101
    private val activityScope = CoroutineScope(Dispatchers.Main)

    // UI Elements
    private lateinit var statusTextView: TextView
    private lateinit var downloadProgressBar: ProgressBar
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var clearModelsButton: Button

    // Core Logic Components
    private lateinit var assetManager: AssetManager
    private var voskDownloadId: Long = -1L
    private val pendingDownloadIds = mutableSetOf<Long>()

    private enum class AppState {
        INITIALIZING, DOWNLOADING, READY_TO_START, SERVICE_RUNNING
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.statusTextView)
        downloadProgressBar = findViewById(R.id.downloadProgressBar)
        startButton = findViewById(R.id.buttonStartService)
        stopButton = findViewById(R.id.buttonStopService)
        clearModelsButton = findViewById(R.id.buttonClearModels)

        assetManager = AssetManager(this)
        registerReceiver(downloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), RECEIVER_EXPORTED)

        startButton.setOnClickListener { startBeekeeperService() }
        stopButton.setOnClickListener { stopBeekeeperService() }
        clearModelsButton.setOnClickListener { showCleanupConfirmationDialog() }
    }

    override fun onResume() {
        super.onResume()
        initiateSetup()
    }

    /**
     * SIMPLIFIED: The setup flow is now just Permissions -> AI Models.
     */
    private fun initiateSetup() {
        updateUiForState(AppState.INITIALIZING, "Checking permissions...")
        if (!hasAllPermissions()) {
            requestPermissions()
        } else {
            checkAssets()
        }
    }

    //region Permission Handling
    private fun hasAllPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSIONS_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE && !hasAllPermissions()) {
            Toast.makeText(this, "Permissions were denied. The app cannot function.", Toast.LENGTH_LONG).show()
        }
        // onResume will re-trigger the setup flow.
    }
    //endregion

    //region Asset Management
    private fun checkAssets() {
        updateUiForState(AppState.INITIALIZING, "Checking for AI models...")
        if (assetManager.checkPrerequisites()) {
            AndroidLog.i(TAG, "All models are ready .")
            updateUiForState(AppState.READY_TO_START)
        } else {
            AndroidLog.w(TAG, "Models not found. Starting download.")
            updateUiForState(AppState.DOWNLOADING)
            val downloads: DownloadRequestInfo = assetManager.queueModelDownloads()
            voskDownloadId = downloads.voskDownloadId
            pendingDownloadIds.addAll(downloads.allIds)
        }
    }

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id in pendingDownloadIds) {
                pendingDownloadIds.remove(id)
                activityScope.launch {
                    if (id == voskDownloadId) {
                        assetManager.unzipVoskModel()
                    }
                    if (pendingDownloadIds.isEmpty()) {
                        if (assetManager.checkPrerequisites()) {
                            AndroidLog.i(TAG, "All models successfully downloaded and prepared.")
                            updateUiForState(AppState.READY_TO_START)
                        } else {
                            AndroidLog.e(TAG, "Download finished, but prerequisites check failed.")
                            updateUiForState(AppState.INITIALIZING, "Error during setup. Please use the menu to clear models and try again.")
                        }
                    }
                }
            }
        }
    }
    //endregion

    //region Menu and Cleanup
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showCleanupConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Clear All Downloaded Models?")
            .setMessage("This will delete the AI models from your device. They will be re-downloaded the next time you open the app.")
            .setPositiveButton("Confirm") { _, _ ->
                activityScope.launch {
                    val success = assetManager.cleanup()
                    if (success) {
                        Toast.makeText(this@MainActivity, "Models cleared successfully.", Toast.LENGTH_SHORT).show()
                        stopBeekeeperService()
                        initiateSetup()
                    } else {
                        Toast.makeText(this@MainActivity, "Failed to clear models.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    //endregion

    //region UI and Service Control
    private fun updateUiForState(state: AppState, message: String? = null) {
        when (state) {
            AppState.INITIALIZING -> {
                statusTextView.text = message ?: "Initializing..."
                startButton.isEnabled = false
                stopButton.isEnabled = false
                clearModelsButton.isEnabled = false
                downloadProgressBar.visibility = View.VISIBLE
                downloadProgressBar.isIndeterminate = true
            }
            AppState.DOWNLOADING -> {
                statusTextView.text = "Downloading required models..."
                startButton.isEnabled = false
                stopButton.isEnabled = false
                clearModelsButton.isEnabled = false
                downloadProgressBar.visibility = View.VISIBLE
                downloadProgressBar.isIndeterminate = true
            }
            AppState.READY_TO_START -> {
                statusTextView.text = "Ready to start listening session."
                startButton.isEnabled = true
                stopButton.isEnabled = false
                clearModelsButton.isEnabled = true
                downloadProgressBar.visibility = View.GONE
            }
            AppState.SERVICE_RUNNING -> {
                statusTextView.text = "Service is running..."
                startButton.isEnabled = false
                stopButton.isEnabled = true
                clearModelsButton.isEnabled = true
            }
        }
    }

    private fun startBeekeeperService() {
        val serviceIntent = Intent(this, BeekeeperService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        Toast.makeText(this, "Beekeeper service started", Toast.LENGTH_SHORT).show()
        updateUiForState(AppState.SERVICE_RUNNING)
    }

    private fun stopBeekeeperService() {
        val serviceIntent = Intent(this, BeekeeperService::class.java)
        stopService(serviceIntent)
        Toast.makeText(this, "Beekeeper service stopped", Toast.LENGTH_SHORT).show()
        updateUiForState(AppState.READY_TO_START)
    }
    //endregion

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(downloadReceiver)
        activityScope.cancel()
    }

    companion object {
        private const val TAG = "MainActivity"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    }
}