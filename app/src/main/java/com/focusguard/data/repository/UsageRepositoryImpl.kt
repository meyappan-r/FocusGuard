package com.focusguard.data.repository

import android.content.Context
import android.content.pm.PackageManager
import com.focusguard.data.datasource.UsageStatsDataSource
import com.focusguard.domain.model.AppUsage
import com.focusguard.domain.model.WeeklyDayUsage
import java.text.SimpleDateFormat
import java.util.*

class UsageRepositoryImpl(
    private val context: Context,
    private val dataSource: UsageStatsDataSource
) {

    companion object {
        val ALWAYS_HIDDEN: Set<String> = setOf(
            "android",
            "com.android.systemui",
            "com.android.launcher", "com.android.launcher2", "com.android.launcher3",
            "com.google.android.launcher", "com.google.android.apps.nexuslauncher",
            "com.sec.android.app.launcher", "com.miui.home", "com.oneplus.launcher",
            "com.android.inputmethod.latin", "com.google.android.inputmethod.latin",
            "com.samsung.android.honeyboard", "com.swiftkey.swiftkeyapp", "com.touchtype.swiftkey",
            "com.android.settings", "com.android.packageinstaller",
            "com.android.bluetooth", "com.android.nfc",
            "com.android.server.telecom", "com.android.providers.telephony",
            "com.android.providers.contacts", "com.android.providers.media",
            "com.google.android.gms", "com.google.android.gsf",
            "com.google.android.syncadapters.contacts",
        )

        val NEVER_BLOCKABLE: Set<String> = setOf(
            "com.android.dialer", "com.google.android.dialer",
            "com.samsung.android.dialer", "com.android.phone",
            "com.android.mms", "com.android.messaging",
            "com.google.android.apps.messaging", "com.samsung.android.messaging",
            "com.android.contacts", "com.samsung.android.contacts",
        )
    }

    fun getDailyUsage(): List<AppUsage> {
        val packageManager = context.packageManager
        val end = System.currentTimeMillis()
        val start = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }.timeInMillis

        return dataSource.getSotByPackage(start, end)
            .filter { it.key !in ALWAYS_HIDDEN
                    && !it.key.startsWith("com.android.server")
                    && it.key != context.packageName }
            .map { (packageName, totalTime) ->
                val appName = try {
                    val info = packageManager.getApplicationInfo(packageName, 0)
                    packageManager.getApplicationLabel(info).toString()
                } catch (_: PackageManager.NameNotFoundException) { packageName }

                AppUsage(
                    packageName     = packageName,
                    appName         = appName,
                    usageTimeMillis = totalTime,
                    isBlockable     = packageName !in NEVER_BLOCKABLE
                )
            }
            .filter { it.usageTimeMillis > 0 }
    }

    /**
     * Returns per-day totals for the past 7 days (index 0 = today, 6 = six days ago).
     * Runs on the caller's dispatcher — call from IO.
     */
    fun getWeeklyUsage(): List<WeeklyDayUsage> {
        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
        return (0..6).map { offset ->
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_YEAR, -offset)
            }
            val start = cal.timeInMillis
            val dayLabel = dayFormat.format(cal.time)
            
            cal.add(Calendar.DAY_OF_YEAR, 1)
            val end = minOf(cal.timeInMillis, System.currentTimeMillis())

            val total = dataSource.getSotByPackage(start, end)
                .filter { it.key !in ALWAYS_HIDDEN
                        && !it.key.startsWith("com.android.server")
                        && it.key != context.packageName }
                .values.sum()

            WeeklyDayUsage(
                dayLabel   = dayLabel,
                totalMillis = total,
                isToday    = offset == 0
            )
        }.reversed() // oldest → newest left-to-right
    }

    /**
     * Returns per-app usage for a specific day.
     * @param dayOffset 0 = today, 1 = yesterday, …, 6 = six days ago.
     */
    fun getDayAppUsage(dayOffset: Int): List<AppUsage> {
        val packageManager = context.packageManager
        
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -dayOffset)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val end = minOf(cal.timeInMillis, System.currentTimeMillis())

        return dataSource.getSotByPackage(start, end)
            .filter { it.key !in ALWAYS_HIDDEN
                    && !it.key.startsWith("com.android.server")
                    && it.key != context.packageName
                    && it.value > 0 }
            .map { (packageName, totalTime) ->
                val appName = try {
                    val info = packageManager.getApplicationInfo(packageName, 0)
                    packageManager.getApplicationLabel(info).toString()
                } catch (_: PackageManager.NameNotFoundException) { packageName }

                AppUsage(
                    packageName     = packageName,
                    appName         = appName,
                    usageTimeMillis = totalTime,
                    isBlockable     = packageName !in NEVER_BLOCKABLE
                )
            }
            .sortedByDescending { it.usageTimeMillis }
    }
}