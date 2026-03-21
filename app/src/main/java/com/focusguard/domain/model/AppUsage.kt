package com.focusguard.domain.model

data class AppUsage(
    val packageName: String,
    val appName: String,
    val usageTimeMillis: Long
)