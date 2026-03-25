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

    /** Today's usage (last 24 hours). */
    fun getTodayUsage(): List<UsageStats> {
        val end = System.currentTimeMillis()
        val start = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }.timeInMillis
        return usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
    }

    /**
     * Usage for a single calendar day.
     * @param dayOffset 0 = today, 1 = yesterday, …, 6 = six days ago.
     */
    fun getDayUsage(dayOffset: Int): List<UsageStats> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -dayOffset)
        }
        val startTime = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val endTime = minOf(cal.timeInMillis, System.currentTimeMillis())
        return usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
    }
}