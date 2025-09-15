package com.bachelorthesis.beekeeperMobile.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bachelorthesis.beekeeperMobile.R

/**
 * Activity to display the application's settings.
 * It hosts a [SettingsFragment] to manage preferences.
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity) // Use a simple layout to host the fragment
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true) // Enable the Up button
        title = getString(R.string.settings_title)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed() // Handle Up button click
        return true
    }
}