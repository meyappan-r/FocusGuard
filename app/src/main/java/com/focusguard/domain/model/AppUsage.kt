package com.focusguard.domain.model

data class AppUsage(
    val packageName: String,
    val appName: String,
    val usageTimeMillis: Long,
    /** False for system-essential apps (dialer, SMS, contacts) that must never be blocked. */
    val isBlockable: Boolean = true
)