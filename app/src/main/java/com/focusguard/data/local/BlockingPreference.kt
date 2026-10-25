package com.focusguard.data.local

import android.content.SharedPreferences

/**
 * The user-facing "Enable App Blocking" switch (Settings screen). It is written by
 * SettingsViewModel and read by BlockerAccessibilityService so that it actually pauses blocking.
 */
object BlockingPreference {
    const val PREFS_NAME = "settings_prefs"
    const val KEY_BLOCKING_ENABLED = "blocking_enabled"

    /** Blocking is on unless the user has switched it off. */
    fun isEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_BLOCKING_ENABLED, true)
}
