package com.focusguard.core.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.focusguard.data.local.LimitManager
import com.focusguard.data.local.SessionStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BlockerAccessibilityService : AccessibilityService() {

    private lateinit var limitManager: LimitManager
    private lateinit var sessionStorage: SessionStorage
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)


    override fun onServiceConnected() {
        super.onServiceConnected()
        limitManager   = LimitManager(this)
        sessionStorage = SessionStorage(this)
        Log.d(TAG, "Accessibility Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        Log.d("BlockerAccessibility", "Foreground App: $packageName")
        
        // Run blocking checks in the background to prevent ANRs
        serviceScope.launch {
            checkAndBlockIfNeeded(packageName)
        }
    }

    private suspend fun checkAndBlockIfNeeded(packageName: String) {
        // Never block FocusGuard itself
        if (packageName == this.packageName) return

        // Never block safety-critical apps (dialer, SMS, contacts — emergency access)
        if (packageName in com.focusguard.data.repository.UsageRepositoryImpl.NEVER_BLOCKABLE) return

        // ── 1. Session blocking ───────────────────────────────────────
        if (sessionStorage.isSessionActive()) {
            val endTime = sessionStorage.getActiveEndTimeMillis()
            if (System.currentTimeMillis() >= endTime) {
                // Session expired → auto-clear
                Log.d("BlockerAccessibility", "Session expired, clearing.")
                sessionStorage.clearActiveSession()
            } else {
                val blocked = sessionStorage.getActiveBlockedPackages()
                if (packageName in blocked) {
                    Log.d("BlockerAccessibility", "Session blocked: $packageName")
                    launchBlockActivity(packageName, "session")
                    return
                }
            }
        }

        // ── 2. Daily-limit blocking ───────────────────────────────────
        if (limitManager.hasLimit(packageName)) {
            val limitMillis = limitManager.getLimit(packageName)
            // UsageStatsManager causes ANRs if run on main thread
            val usageMillis = getTodayUsageMillis(packageName)
            
            if (usageMillis >= limitMillis) {
                Log.d("BlockerAccessibility", "Limit exceeded: $packageName ($usageMillis ms >= $limitMillis ms)")
                launchBlockActivity(packageName, "limit")
            }
        }
    }

    private fun getTodayUsageMillis(packageName: String): Long {
        return runCatching {
            val usm = getSystemService(USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
            val cal = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val stats = usm.queryAndAggregateUsageStats(cal.timeInMillis, System.currentTimeMillis())
            stats[packageName]?.totalTimeInForeground ?: 0L
        }.getOrDefault(0L)
    }

    private fun launchBlockActivity(packageName: String, source: String) {
        val appName = runCatching {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)

        val intent = Intent(this, com.focusguard.ui.blocks.BlockActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("BLOCKED_APP_NAME", appName)
            putExtra("BLOCKED_PACKAGE", packageName)
            putExtra("BLOCK_SOURCE", source)
        }
        startActivity(intent)
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted")
    }

    companion object {
        private const val TAG = "BlockerService"
    }
}
