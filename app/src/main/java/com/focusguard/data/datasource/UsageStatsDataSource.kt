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
        val lastResumedEvent = mutableMapOf<String, Long>()
        var isScreenOn = true

        val event = android.app.usage.UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue

            when (event.eventType) {
                android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (isScreenOn) {
                        lastResumedEvent[pkg] = event.timeStamp
                    }
                }
                android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED,
                android.app.usage.UsageEvents.Event.ACTIVITY_STOPPED -> {
                    lastResumedEvent[pkg]?.let { resumedTime ->
                        val duration = event.timeStamp - resumedTime
                        if (duration > 0) {
                            usageMap[pkg] = (usageMap[pkg] ?: 0L) + duration
                        }
                        lastResumedEvent.remove(pkg)
                    }
                }
                android.app.usage.UsageEvents.Event.SCREEN_INTERACTIVE -> {
                    isScreenOn = true
                }
                android.app.usage.UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                android.app.usage.UsageEvents.Event.DEVICE_SHUTDOWN -> {
                    isScreenOn = false
                    lastResumedEvent.forEach { (p, resumedTime) ->
                        val duration = event.timeStamp - resumedTime
                        if (duration > 0) {
                            usageMap[p] = (usageMap[p] ?: 0L) + duration
                        }
                    }
                    lastResumedEvent.clear()
                }
            }
        }

        val end = minOf(endTime, System.currentTimeMillis())
        lastResumedEvent.forEach { (p, resumedTime) ->
            val duration = end - resumedTime
            if (duration > 0) {
                usageMap[p] = (usageMap[p] ?: 0L) + duration
            }
        }
        return usageMap
    }
}