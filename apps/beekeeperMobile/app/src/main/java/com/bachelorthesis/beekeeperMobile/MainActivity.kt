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
    private val RECORD_AUDIO_PERMISSION_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val startButton: Button = findViewById(R.id.buttonStartService)
        val stopButton: Button = findViewById(R.id.buttonStopService)

        startButton.setOnClickListener {
            if (checkAndRequestPermissions()) {
                startBeekeeperService()
            }
        }

        stopButton.setOnClickListener {
            stopBeekeeperService()
        }
    }

    private fun checkAndRequestPermissions(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_PERMISSION_CODE
            )
            return false
        }
        // You might need other permissions like INTERNET if STT is cloud-based
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Audio permission granted", Toast.LENGTH_SHORT).show()
                startBeekeeperService()
            } else {
                Toast.makeText(this, "Audio permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startBeekeeperService() {
        val serviceIntent = Intent(this, BeekeeperService::class.java)
        startForegroundService(serviceIntent)
        Toast.makeText(this, "Beekeeper service started", Toast.LENGTH_SHORT).show()
    }

    private fun stopBeekeeperService() {
        val serviceIntent = Intent(this, BeekeeperService::class.java)
        stopService(serviceIntent)
        Toast.makeText(this, "Beekeeper service stopped", Toast.LENGTH_SHORT).show()
    }
}
