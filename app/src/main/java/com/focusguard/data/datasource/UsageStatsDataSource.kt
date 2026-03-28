package com.focusguard.data.datasource

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.*

class UsageStatsDataSource(
    private val context: Context
) {
    private val usm: UsageStatsManager
        get() = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    fun getSotByPackage(startTime: Long, endTime: Long): Map<String, Long> {
        val events = usm.queryEvents(startTime, endTime)
        val usageMap = mutableMapOf<String, Long>()
        
        var lastResumedPkg: String? = null
        var lastResumedTime = 0L
        var isScreenOn = true

        val event = android.app.usage.UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue

            when (event.eventType) {
                android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (isScreenOn) {
                        lastResumedPkg?.let { prevPkg ->
                            val duration = event.timeStamp - lastResumedTime
                            if (duration > 0) {
                                usageMap[prevPkg] = (usageMap[prevPkg] ?: 0L) + duration
                            }
                        }
                        lastResumedPkg = pkg
                        lastResumedTime = event.timeStamp
                    }
                }
                android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED,
                android.app.usage.UsageEvents.Event.ACTIVITY_STOPPED -> {
                    if (lastResumedPkg == pkg) {
                        val duration = event.timeStamp - lastResumedTime
                        if (duration > 0) {
                            usageMap[pkg] = (usageMap[pkg] ?: 0L) + duration
                        }
                        lastResumedPkg = null
                    }
                }
                android.app.usage.UsageEvents.Event.SCREEN_INTERACTIVE -> {
                    isScreenOn = true
                }
                android.app.usage.UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                android.app.usage.UsageEvents.Event.DEVICE_SHUTDOWN -> {
                    isScreenOn = false
                    lastResumedPkg?.let { prevPkg ->
                        val duration = event.timeStamp - lastResumedTime
                        if (duration > 0) {
                            usageMap[prevPkg] = (usageMap[prevPkg] ?: 0L) + duration
                        }
                        lastResumedPkg = null
                    }
                }
            }
        }

        val end = minOf(endTime, System.currentTimeMillis())
        lastResumedPkg?.let { prevPkg ->
            val duration = end - lastResumedTime
            if (duration > 0) {
                usageMap[prevPkg] = (usageMap[prevPkg] ?: 0L) + duration
            }
        }
        return usageMap
    }
}