package com.focusguard.core.services

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.focusguard.data.local.BlockingPreference
import com.focusguard.data.local.FocusSession
import com.focusguard.data.local.LimitManager
import com.focusguard.data.local.SessionDataStore
import com.focusguard.domain.rules.remainingMillisAt
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

class BlockerAccessibilityService : AccessibilityService() {

    private lateinit var limitManager: LimitManager
    private lateinit var sessionDataStore: SessionDataStore
    private lateinit var settingsPrefs: SharedPreferences
    
    // The core of the new system: in-memory cache directly mirrored from DataStore 
    // This allows absolutely zero-latency blocks on app switch.
    @Volatile
    private var activeSessionsCache: List<FocusSession> = emptyList()

    @Volatile
    private var activeBreakExpiryCache: Long = 0L

    @Volatile
    private var limitReachedCache: Set<String> = emptySet()

    private val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        Log.e(TAG, "Coroutine crash: ${exception.message}", exception)
    }
    
    // Launch an observation scope bound to the service boundary lifecycle
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)

    override fun onServiceConnected() {
        super.onServiceConnected()
        limitManager   = LimitManager(this)
        sessionDataStore = SessionDataStore(this)
        settingsPrefs  = getSharedPreferences(BlockingPreference.PREFS_NAME, Context.MODE_PRIVATE)


        // Hook up the live pipe to the DataStore to continually preload our memory
        serviceScope.launch {
            sessionDataStore.sessionsFlow.collect { sessionsList ->

                activeSessionsCache = sessionsList
            }
        }

        serviceScope.launch {
            sessionDataStore.breakExpiryFlow.collect { expiry ->
                activeBreakExpiryCache = expiry
            }
        }

        // Poll UsageStats safely in the background instead of on every UI event
        serviceScope.launch {
            while (true) {
                updateLimitReachedCache()
                kotlinx.coroutines.delay(10000L) // Check every 10 seconds
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
            val packageName = event.packageName?.toString() ?: return
            
            if (!::limitManager.isInitialized || !::sessionDataStore.isInitialized) return

            // Settings > "Enable App Blocking" pauses all blocking (sessions and limits)
            if (!BlockingPreference.isEnabled(settingsPrefs)) return

            // 1. Perform Sessions check strictly synchronously (zero yield)
            val blockingSessionName = shouldBlockSynchronously(packageName)
            if (blockingSessionName != null) {

                launchBlockActivity(packageName, "session", blockingSessionName)
                return
            }
            
            // 2. Perform Limit check strictly synchronously from in-memory cache
            if (packageName in limitReachedCache) {

                launchBlockActivity(packageName, "limit")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Crash trapped in onAccessibilityEvent", e)
        }
    }

    private fun shouldBlockSynchronously(packageName: String): String? {
        // Core exceptions
        if (packageName == this.packageName) return null
        if (packageName in com.focusguard.data.repository.UsageRepositoryImpl.NEVER_BLOCKABLE) return null
        if (packageName in com.focusguard.data.repository.UsageRepositoryImpl.ALWAYS_HIDDEN) return null

        val now = LocalDateTime.now()

        if (System.currentTimeMillis() < activeBreakExpiryCache) {
            return null // Currently in a temporary break
        }

        for (session in activeSessionsCache) {
            if (session.remainingMillisAt(now) != null && packageName in session.blockedPackages) {
                return session.name
            }
        }
        return null
    }

    private fun updateLimitReachedCache() {
        val limits = limitManager.getAllLimits()
        if (limits.isEmpty()) {
            limitReachedCache = emptySet()
            return
        }
        val usm = getSystemService(USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        try {
            val stats = usm.queryAndAggregateUsageStats(cal.timeInMillis, System.currentTimeMillis())
            val newReached = mutableSetOf<String>()
            for ((pkg, limit) in limits) {
                val usage = stats[pkg]?.totalTimeInForeground ?: 0L
                if (usage >= limit) {
                    newReached.add(pkg)
                }
            }
            limitReachedCache = newReached
        } catch (e: Exception) {
            Log.e(TAG, "Failed to poll UsageStats for limits", e)
        }
    }

    private fun launchBlockActivity(packageName: String, source: String, sessionName: String? = null) {
        val intent = Intent(this, BlockActivity::class.java).apply {
            putExtra("blocked_package", packageName)
            putExtra("block_source", source)
            sessionName?.let { putExtra("session_name", it) }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or 
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        }
        startActivity(intent)
    }

    override fun onInterrupt() {

    }

    companion object {
        private const val TAG = "BlockerService"
    }
}
