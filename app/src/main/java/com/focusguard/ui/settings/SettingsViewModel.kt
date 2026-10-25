package com.focusguard.ui.settings

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.focusguard.data.local.BlockingPreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class SettingsState(
    val hasUsageStatsPermission: Boolean = false,
    val hasAccessibilityPermission: Boolean = false,
    val isBlockingEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** True once the user has agreed to the accessibility disclosure. */
    val accessibilityDisclosureAccepted: Boolean = false
)

class SettingsViewModel(context: Context) : ViewModel() {

    // This ViewModel outlives Activity recreation, so it must never hold an Activity.
    // Only the application context is kept, which lint cannot tell apart from an Activity.
    @SuppressLint("StaticFieldLeak")
    private val context: Context = context.applicationContext

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state
    private val prefs = context.getSharedPreferences(BlockingPreference.PREFS_NAME, Context.MODE_PRIVATE)

    init {
        val savedThemeName = prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        val savedTheme = runCatching { ThemeMode.valueOf(savedThemeName) }.getOrDefault(ThemeMode.SYSTEM)
        val savedBlocking = BlockingPreference.isEnabled(prefs)
        _state.value = _state.value.copy(
            themeMode = savedTheme,
            isBlockingEnabled = savedBlocking,
            accessibilityDisclosureAccepted = prefs.getBoolean(KEY_DISCLOSURE_ACCEPTED, false)
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
            androidx.core.app.AppOpsManagerCompat.noteOpNoThrow(
                context,
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

    fun acceptAccessibilityDisclosure() {
        prefs.edit().putBoolean(KEY_DISCLOSURE_ACCEPTED, true).apply()
        _state.value = _state.value.copy(accessibilityDisclosureAccepted = true)
    }

    /** The disclosure is offered once on first use if the service is not enabled and consent is missing. */
    fun shouldPromptDisclosure(): Boolean =
        !_state.value.accessibilityDisclosureAccepted &&
            !_state.value.hasAccessibilityPermission &&
            !prefs.getBoolean(KEY_DISCLOSURE_PROMPTED, false)

    fun markDisclosurePrompted() {
        prefs.edit().putBoolean(KEY_DISCLOSURE_PROMPTED, true).apply()
    }

    fun toggleBlocking(enabled: Boolean) {
        prefs.edit().putBoolean(BlockingPreference.KEY_BLOCKING_ENABLED, enabled).apply()
        _state.value = _state.value.copy(isBlockingEnabled = enabled)
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _state.value = _state.value.copy(themeMode = mode)
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

private const val KEY_DISCLOSURE_ACCEPTED = "accessibility_disclosure_accepted"
private const val KEY_DISCLOSURE_PROMPTED = "accessibility_disclosure_prompted"

class SettingsViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
