package com.focusguard.ui.blocks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.data.local.LimitManager
import com.focusguard.domain.model.AppUsage
import com.focusguard.domain.usecase.GetDailyUsageUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class BlockAppState(
    val appUsage: AppUsage,
    val limitMillis: Long = -1L // -1 means no limit
)

data class BlocksState(
    val apps: List<BlockAppState> = emptyList()
)

class BlocksViewModel(
    private val getDailyUsageUseCase: GetDailyUsageUseCase,
    private val limitManager: LimitManager
) : ViewModel() {

    private val _state = MutableStateFlow(BlocksState())
    val state: StateFlow<BlocksState> = _state

    fun loadApps() {
        viewModelScope.launch {
            val usageList = getDailyUsageUseCase.execute()
            val limits = limitManager.getAllLimits()

            val blockStates = usageList.map { usage ->
                BlockAppState(
                    appUsage = usage,
                    limitMillis = limits[usage.packageName] ?: -1L
                )
            }.sortedByDescending { it.appUsage.usageTimeMillis }

            _state.value = BlocksState(apps = blockStates)
        }
    }

    fun setLimit(packageName: String, limitMillis: Long) {
        if (limitMillis > 0) {
            limitManager.saveLimit(packageName, limitMillis)
        } else {
            limitManager.removeLimit(packageName)
        }
        
        // Reload after change to update UI
        loadApps()
    }
}
