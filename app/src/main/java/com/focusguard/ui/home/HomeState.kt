package com.focusguard.ui.home

import com.focusguard.domain.model.AppUsage
import com.focusguard.domain.model.PeakDistraction
import com.focusguard.domain.model.ScreenTimeSummary
import com.focusguard.domain.model.WeeklyDayUsage

enum class HomeTab { TODAY, WEEKLY }

/**
 * One arc segment in the segmented ring — pre-computed sweep angle and color index.
 * colorIndex 0 (darkest) … 3 (medium), 4 = "Other" / track.
 */
data class RingSegment(
    val label: String,
    val usageMillis: Long,
    val sweepAngle: Float,
    val colorIndex: Int
)

data class HomeState(
    val appUsage: List<AppUsage> = emptyList(),
    val screenTimeSummary: ScreenTimeSummary = ScreenTimeSummary(0L, 0L),
    val peakDistraction: PeakDistraction? = null,
    val selectedTab: HomeTab = HomeTab.TODAY,
    val ringSegments: List<RingSegment> = emptyList(),
    val weeklyData: List<WeeklyDayUsage> = emptyList(),
    val weeklyAverage: Long = 0L,
    val showAll: Boolean = false,
    /** Index into weeklyData that is currently selected (null = none). */
    val selectedDayIndex: Int? = null,
    /** Per-app breakdown for the tapped day bar. */
    val selectedDayApps: List<AppUsage> = emptyList()
)