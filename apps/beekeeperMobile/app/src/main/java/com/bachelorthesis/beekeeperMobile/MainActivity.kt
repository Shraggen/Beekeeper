package com.bachelorthesis.beekeeperMobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    // REFACTOR: Use a single request code for all permissions for simplicity.
    private val PERMISSIONS_REQUEST_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val startButton: Button = findViewById(R.id.buttonStartService)
        val stopButton: Button = findViewById(R.id.buttonStopService)

        startButton.setOnClickListener {
            // REFACTOR: The check function now handles all required permissions.
            checkAndRequestPermissions()
        }

        stopButton.setOnClickListener {
            stopBeekeeperService()
        }
    }

    private fun checkAndRequestPermissions() {
        // REFACTOR: Create a list of permissions your app needs.
        val requiredPermissions = mutableListOf<String>()
        requiredPermissions.add(Manifest.permission.RECORD_AUDIO)

        // REFACTOR: Conditionally add the notification permission ONLY for Android 13 (TIRAMISU) and up.
        requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)

        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isEmpty()) {
            // All permissions are already granted, start the service.
            startBeekeeperService()
        } else {
            // Request the permissions that have not been granted yet.
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                PERMISSIONS_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            // REFACTOR: Check if ALL requested permissions were granted.
            val allPermissionsGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            if (allPermissionsGranted) {
                Toast.makeText(this, "All permissions granted!", Toast.LENGTH_SHORT).show()
                startBeekeeperService()
            } else {
                Toast.makeText(this, "Audio and/or Notification permissions were denied.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startBeekeeperService() {
        val serviceIntent = Intent(this, BeekeeperService::class.java)
        // Correctly using startForegroundService for modern Android.
        ContextCompat.startForegroundService(this, serviceIntent)
        Toast.makeText(this, "Beekeeper service started", Toast.LENGTH_SHORT).show()
    }

    private fun stopBeekeeperService() {
        val serviceIntent = Intent(this, BeekeeperService::class.java)
        stopService(serviceIntent)
        Toast.makeText(this, "Beekeeper service stopped", Toast.LENGTH_SHORT).show()
    }
}