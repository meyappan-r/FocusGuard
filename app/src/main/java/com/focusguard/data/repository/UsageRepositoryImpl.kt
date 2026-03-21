package com.focusguard.data.repository

import android.content.Context
import android.content.pm.PackageManager
import com.focusguard.data.datasource.UsageStatsDataSource
import com.focusguard.domain.model.AppUsage

class UsageRepositoryImpl(
    private val context: Context,
    private val dataSource: UsageStatsDataSource
) {

    companion object {
        /**
         * System internals that appear in usage stats due to OS mechanics but carry
         * zero user-meaningful information. Completely hidden from stats AND blocks.
         * Blocking any of these would break core phone functionality.
         */
        val ALWAYS_HIDDEN: Set<String> = setOf(
            // Android OS framework itself
            "android",
            // System UI: status bar, notification shade, nav bar
            "com.android.systemui",
            // Home screen / launchers
            "com.android.launcher",
            "com.android.launcher2",
            "com.android.launcher3",
            "com.google.android.launcher",
            "com.google.android.apps.nexuslauncher",
            "com.sec.android.app.launcher",          // Samsung
            "com.miui.home",                          // Xiaomi
            "com.oneplus.launcher",                   // OnePlus
            // Keyboards / input methods
            "com.android.inputmethod.latin",
            "com.google.android.inputmethod.latin",
            "com.samsung.android.honeyboard",
            "com.swiftkey.swiftkeyapp",
            "com.touchtype.swiftkey",
            // Core system services
            "com.android.settings",
            "com.android.packageinstaller",
            "com.android.bluetooth",
            "com.android.nfc",
            "com.android.server.telecom",
            "com.android.providers.telephony",
            "com.android.providers.contacts",
            "com.android.providers.media",
            // Google core services (not user-facing)
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.google.android.syncadapters.contacts",
        )

        /**
         * User-facing apps that are safe to show in usage stats (users may spend time here),
         * but MUST NOT be blockable — they handle emergency calls, SMS, and core communication.
         */
        val NEVER_BLOCKABLE: Set<String> = setOf(
            // Phone / dialer — emergency calls (911, 112, etc.)
            "com.android.dialer",
            "com.google.android.dialer",
            "com.samsung.android.dialer",
            "com.android.phone",
            // SMS / MMS — emergency messaging
            "com.android.mms",
            "com.android.messaging",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            // Contacts — needed to make calls
            "com.android.contacts",
            "com.samsung.android.contacts",
        )
    }

    fun getDailyUsage(): List<AppUsage> {
        val packageManager = context.packageManager

        return dataSource.getTodayUsage()
            // Remove system noise entirely
            .filter { usageStats ->
                usageStats.packageName !in ALWAYS_HIDDEN &&
                !usageStats.packageName.startsWith("com.android.server") &&
                usageStats.packageName != context.packageName
            }
            .map { usageStats ->
                val appName = try {
                    val appInfo = packageManager.getApplicationInfo(usageStats.packageName, 0)
                    packageManager.getApplicationLabel(appInfo).toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    usageStats.packageName
                }

                AppUsage(
                    packageName = usageStats.packageName,
                    appName = appName,
                    usageTimeMillis = usageStats.totalTimeInForeground,
                    isBlockable = usageStats.packageName !in NEVER_BLOCKABLE
                )
            }
            // Only keep entries with actual usage time
            .filter { it.usageTimeMillis > 0 }
    }
}