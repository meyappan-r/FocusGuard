package com.focusguard.domain.usecase

import com.focusguard.data.repository.UsageRepositoryImpl
import com.focusguard.domain.model.AppUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GetDailyUsageUseCase(
    private val repository: UsageRepositoryImpl
) {
    /** Always executes on the IO dispatcher to avoid blocking the main thread. */
    suspend fun execute(): List<AppUsage> = withContext(Dispatchers.IO) {
        repository.getDailyUsage()
    }
}