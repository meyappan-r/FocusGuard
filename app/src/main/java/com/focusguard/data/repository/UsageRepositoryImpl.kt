package com.focusguard.data.repository

import android.content.Context
import android.content.pm.PackageManager
import com.focusguard.data.datasource.UsageStatsDataSource
import com.focusguard.domain.model.AppUsage

class UsageRepositoryImpl(
    private val context: Context,
    private val dataSource: UsageStatsDataSource
) {

    fun getDailyUsage(): List<AppUsage> {
        val packageManager = context.packageManager
        
        return dataSource.getTodayUsage().map { usageStats ->
            val appName = try {
                val appInfo = packageManager.getApplicationInfo(usageStats.packageName, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                usageStats.packageName
            }
            
            AppUsage(
                packageName = usageStats.packageName,
                appName = appName,
                usageTimeMillis = usageStats.totalTimeInForeground
            )
        }.filter { it.usageTimeMillis > 0 && it.packageName != context.packageName } // Clean up list & block loop
    }
}