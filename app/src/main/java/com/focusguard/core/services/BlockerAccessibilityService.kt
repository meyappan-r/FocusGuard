package com.focusguard.core.services

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.focusguard.data.local.LimitManager
import com.focusguard.data.local.SessionStorage

class BlockerAccessibilityService : AccessibilityService() {

    private lateinit var limitManager: LimitManager
    private lateinit var sessionStorage: SessionStorage
    
    // In a real implementation we would use Dagger/Hilt to inject these
    // For now we initialize them manually for simplicity
    override fun onServiceConnected() {
        super.onServiceConnected()
        limitManager = LimitManager(this)
        sessionStorage = SessionStorage(this)
        Log.d("BlockerService", "Accessibility Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }

        val packageName = event.packageName?.toString() ?: return
        Log.d("BlockerService", "Foreground App Changed to: $packageName")

        checkAppLimit(packageName)
    }

    private fun checkAppLimit(packageName: String) {
        // Skip checking ourselves to prevent locking the user out of FocusGuard
        if (packageName == this.packageName) return
        
        // 1. Check instant sessions & scheduled profiles (To be implemented)

        // 2. Check App Limits
        if (limitManager.hasLimit(packageName)) {
            val limitMillis = limitManager.getLimit(packageName)
            
            // Check actual daily usage
            val usageStatsManager = getSystemService(android.content.Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
            val calendar = java.util.Calendar.getInstance()
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)
            val startTime = calendar.timeInMillis
            val endTime = System.currentTimeMillis()

            val statsMap = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
            val usageStats = statsMap[packageName]
            val totalTimeInForeground = usageStats?.totalTimeInForeground ?: 0L

            if (totalTimeInForeground >= limitMillis) {
                Log.d("BlockerService", "App $packageName usage ($totalTimeInForeground ms) exceeded limit ($limitMillis ms), triggering block")
                
                val pm = packageManager
                val appName = try {
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
                    packageName
                }

                val intent = android.content.Intent(this, com.focusguard.ui.blocks.BlockActivity::class.java).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("BLOCKED_APP_NAME", appName)
                }
                startActivity(intent)
            } else {
                Log.d("BlockerService", "App $packageName usage ($totalTimeInForeground ms) is under limit ($limitMillis ms)")
            }
        }
    }

    override fun onInterrupt() {
        Log.d("BlockerService", "Accessibility Service Interrupted")
    }
}
