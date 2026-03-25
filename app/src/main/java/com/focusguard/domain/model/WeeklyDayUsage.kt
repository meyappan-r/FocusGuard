package com.focusguard.domain.model

data class WeeklyDayUsage(
    val dayLabel: String,   // "Mon", "Tue", etc.
    val totalMillis: Long,
    val isToday: Boolean
)
