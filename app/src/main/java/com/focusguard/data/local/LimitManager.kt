package com.focusguard.data.local

import android.content.Context
import android.content.SharedPreferences

class LimitManager(private val context: Context) {

    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "limit_preferences"
    }

    // Save limit for a specific package (in milliseconds)
    fun saveLimit(packageName: String, limitMillis: Long) {
        sharedPreferences.edit().putLong(packageName, limitMillis).apply()
    }

    // Get limit for a specific package. Returns -1 if no limit is set.
    fun getLimit(packageName: String): Long {
        return sharedPreferences.getLong(packageName, -1)
    }

    // Remove limit for a package
    fun removeLimit(packageName: String) {
        sharedPreferences.edit().remove(packageName).apply()
    }
    
    // Check if an app has a limit
    fun hasLimit(packageName: String): Boolean {
        return sharedPreferences.contains(packageName)
    }

    // Get all defined limits (returns a map of packageName to limitMillis)
    fun getAllLimits(): Map<String, Long> {
        val allEntries = sharedPreferences.all
        val limits = mutableMapOf<String, Long>()
        for ((key, value) in allEntries) {
            if (value is Long) {
                limits[key] = value
            }
        }
        return limits
    }

    // Clear all limits
    fun clearAllLimits() {
        sharedPreferences.edit().clear().apply()
    }
}
