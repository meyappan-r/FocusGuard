package com.focusguard.domain.model

data class ScreenTimeSummary(
    val totalScreenTime: Long,
    val focusTime: Long = 0L // Optional focus time as per spec
)
