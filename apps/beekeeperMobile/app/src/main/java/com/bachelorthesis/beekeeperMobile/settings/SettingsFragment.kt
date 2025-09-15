package com.bachelorthesis.beekeeperMobile.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.bachelorthesis.beekeeperMobile.R

/**
 * A PreferenceFragmentCompat to display the application settings.
 * Currently, it hosts the language selection preference.
 */
class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Load the preferences from an XML resource
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }
}