package com.focusguard.ui.settings

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class SettingsState(
    val hasUsageStatsPermission: Boolean = false,
    val hasAccessibilityPermission: Boolean = false,
    val isBlockingEnabled: Boolean = true,
    val isDarkMode: Boolean = false
)

class SettingsViewModel(private val context: Context) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state
    private val prefs = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)

    init {
        val savedDarkMode = prefs.getBoolean("dark_mode", false)
        val savedBlocking = prefs.getBoolean("blocking_enabled", true)
        _state.value = _state.value.copy(
            isDarkMode = savedDarkMode,
            isBlockingEnabled = savedBlocking
        )
        checkPermissions()
    }

    fun checkPermissions() {
        val hasUsageStats = checkUsageStatsPermission()
        val hasAccessibility = checkAccessibilityPermission()
        
        _state.value = _state.value.copy(
            hasUsageStatsPermission = hasUsageStats,
            hasAccessibilityPermission = hasAccessibility
        )
    }

    private fun checkUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun checkAccessibilityPermission(): Boolean {
        var accessibilityEnabled = 0
        val service = "${context.packageName}/com.focusguard.core.services.BlockerAccessibilityService"
        
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                context.contentResolver,
                android.provider.Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Settings.SettingNotFoundException) {
            // Ignore
        }
        
        if (accessibilityEnabled == 1) {
            val settingValue = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (settingValue != null) {
                return settingValue.split(':').contains(service)
            }
        }
        return false
    }

    fun toggleBlocking(enabled: Boolean) {
        prefs.edit().putBoolean("blocking_enabled", enabled).apply()
        _state.value = _state.value.copy(isBlockingEnabled = enabled)
    }

    fun toggleDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean("dark_mode", enabled).apply()
        _state.value = _state.value.copy(isDarkMode = enabled)
    }
    
    fun requestUsageStatsPermission(context: Context) {
        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }
    
    fun requestAccessibilityPermission(context: Context) {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }
}

class SettingsViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
