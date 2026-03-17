package com.focusguard.data.datasource

import android.app.usage.UsageStatsManager
import android.content.Context
import android.app.usage.UsageStats
import java.util.*

class UsageStatsDataSource(
    private val context: Context
) {

    fun getTodayUsage(): List<UsageStats> {
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val startTime = calendar.timeInMillis

        return usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )
    }
}