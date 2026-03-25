package com.focusguard.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.data.repository.UsageRepositoryImpl
import com.focusguard.domain.model.PeakDistraction
import com.focusguard.domain.model.ScreenTimeSummary
import com.focusguard.domain.usecase.GetDailyUsageUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Max individual segments before aggregating the tail into "Other". */
private const val MAX_NAMED_SEGMENTS = 4
/** Total ring sweep (leave gap at bottom like a gauge). */
private const val TOTAL_SWEEP = 300f
/** Tiny gap between arcs in degrees. */
private const val GAP = 2f

class HomeViewModel(
    private val getDailyUsageUseCase: GetDailyUsageUseCase,
    private val repository: UsageRepositoryImpl
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state

    fun loadUsage() {
        viewModelScope.launch {
            val usage = getDailyUsageUseCase.execute()   // already on IO
            val totalTime = usage.sumOf { it.usageTimeMillis }

            // ── Ring segments ──────────────────────────────────────
            val sorted = usage.sortedByDescending { it.usageTimeMillis }
            val top    = sorted.take(MAX_NAMED_SEGMENTS)
            val other  = sorted.drop(MAX_NAMED_SEGMENTS).sumOf { it.usageTimeMillis }

            val allSegments = buildList {
                top.forEachIndexed { idx, app ->
                    add(Triple(app.appName, app.usageTimeMillis, idx))
                }
                if (other > 0) add(Triple("Other", other, MAX_NAMED_SEGMENTS))
            }

            val gapPool   = GAP * allSegments.size
            val available = (TOTAL_SWEEP - gapPool).coerceAtLeast(0f)

            val ringSegments = if (totalTime > 0) {
                allSegments.map { (label, millis, colorIdx) ->
                    RingSegment(
                        label       = label,
                        usageMillis = millis,
                        sweepAngle  = (millis.toFloat() / totalTime) * available,
                        colorIndex  = colorIdx
                    )
                }
            } else emptyList()

            // ── Peak distraction ───────────────────────────────────
            val peak = sorted.firstOrNull()?.let {
                PeakDistraction(appName = it.appName, usageTime = it.usageTimeMillis)
            }
            val appUsageList = if (sorted.isNotEmpty()) sorted.drop(1) else sorted

            _state.value = _state.value.copy(
                appUsage          = appUsageList,
                screenTimeSummary = ScreenTimeSummary(totalScreenTime = totalTime),
                peakDistraction   = peak,
                ringSegments      = ringSegments
            )
        }
    }

    fun loadWeeklyData() {
        viewModelScope.launch {
            val weekly = withContext(Dispatchers.IO) { repository.getWeeklyUsage() }
            val avg    = if (weekly.isNotEmpty()) weekly.sumOf { it.totalMillis } / weekly.size else 0L
            _state.value = _state.value.copy(
                weeklyData    = weekly,
                weeklyAverage = avg
            )
        }
    }

    fun selectTab(tab: HomeTab) {
        _state.value = _state.value.copy(selectedTab = tab)
        if (tab == HomeTab.WEEKLY && _state.value.weeklyData.isEmpty()) {
            loadWeeklyData()
        }
    }

    /**
     * Called when the user taps a bar in the weekly chart.
     * [index] is the position in weeklyData (0 = oldest, 6 = today).
     * The weeklyData list is reversed (oldest→newest), so dayOffset = 6 - index.
     */
    fun selectWeeklyDay(index: Int) {
        if (_state.value.selectedDayIndex == index) {
            // Tap again to deselect
            _state.value = _state.value.copy(selectedDayIndex = null, selectedDayApps = emptyList())
            return
        }
        _state.value = _state.value.copy(selectedDayIndex = index, selectedDayApps = emptyList())
        val dayOffset = 6 - index   // weeklyData[0]=oldest → offset 6, weeklyData[6]=today → offset 0
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) { repository.getDayAppUsage(dayOffset) }
            _state.value = _state.value.copy(selectedDayApps = apps)
        }
    }

    fun toggleShowAll() {
        _state.value = _state.value.copy(showAll = !_state.value.showAll)
    }
}