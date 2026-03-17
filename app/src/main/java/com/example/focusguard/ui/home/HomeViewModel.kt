package com.focusguard.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.domain.model.AppUsage
import com.focusguard.domain.model.PeakDistraction
import com.focusguard.domain.model.ScreenTimeSummary
import com.focusguard.domain.usecase.GetDailyUsageUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val getDailyUsageUseCase: GetDailyUsageUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state

    fun loadUsage() {
        viewModelScope.launch {
            val usage = getDailyUsageUseCase.execute()
            
            val totalTime = usage.sumOf { it.usageTimeMillis }
            val screenTimeSummary = ScreenTimeSummary(totalScreenTime = totalTime)
            
            val peakApp = usage.maxByOrNull { it.usageTimeMillis }
            val peakDistraction = peakApp?.let {
                PeakDistraction(appName = it.appName, usageTime = it.usageTimeMillis)
            }
            
            _state.value = HomeState(
                appUsage = usage.sortedByDescending { it.usageTimeMillis },
                screenTimeSummary = screenTimeSummary,
                peakDistraction = peakDistraction
            )
        }
    }
}