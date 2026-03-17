package com.focusguard.domain.usecase

import com.focusguard.data.repository.UsageRepositoryImpl
import com.focusguard.domain.model.AppUsage

class GetDailyUsageUseCase(
    private val repository: UsageRepositoryImpl
) {

    fun execute(): List<AppUsage> {
        return repository.getDailyUsage()
    }
}