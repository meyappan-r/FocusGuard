package com.focusguard.data.local

import android.content.Context
import android.content.SharedPreferences

class SessionStorage(private val context: Context) {

    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "session_preferences"
    }

    // A simple representation of a Session Profile for storage
    // You could map this to a JSON string or complex Domain Model later using Gson/Moshi
    data class SessionProfileData(
        val profileName: String,
        val startTimeMillis: Long,
        val endTimeMillis: Long,
        val isEnabled: Boolean,
        val blockedPackages: Set<String>
    )

    // Save a simple session profile mapping property to strings
    // In a production app, converting the `SessionProfileData` object to JSON is preferred
    fun saveProfile(profileId: String, name: String, startMillis: Long, endMillis: Long, enabled: Boolean, blocked: Set<String>) {
        sharedPreferences.edit()
            .putString("${profileId}_name", name)
            .putLong("${profileId}_start", startMillis)
            .putLong("${profileId}_end", endMillis)
            .putBoolean("${profileId}_enabled", enabled)
            .putStringSet("${profileId}_blocked", blocked)
            .apply()
    }

    // Retrieve a profile string set
    fun getProfileBlockedApps(profileId: String): Set<String> {
        return sharedPreferences.getStringSet("${profileId}_blocked", emptySet()) ?: emptySet()
    }

    fun isProfileEnabled(profileId: String): Boolean {
        return sharedPreferences.getBoolean("${profileId}_enabled", false)
    }

    fun clearProfile(profileId: String) {
        sharedPreferences.edit()
            .remove("${profileId}_name")
            .remove("${profileId}_start")
            .remove("${profileId}_end")
            .remove("${profileId}_enabled")
            .remove("${profileId}_blocked")
            .apply()
    }
}
