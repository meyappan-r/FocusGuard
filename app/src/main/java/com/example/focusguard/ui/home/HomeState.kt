package com.focusguard.ui.home

import com.focusguard.domain.model.AppUsage
import com.focusguard.domain.model.PeakDistraction
import com.focusguard.domain.model.ScreenTimeSummary

data class HomeState(
    val appUsage: List<AppUsage> = emptyList(),
    val screenTimeSummary: ScreenTimeSummary = ScreenTimeSummary(0L, 0L),
    val peakDistraction: PeakDistraction? = null
)