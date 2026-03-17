package com.focusguard.ui.sessions

import androidx.lifecycle.ViewModel
import com.focusguard.data.local.SessionStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class SessionProfile(
    val id: String,
    val name: String,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val enabled: Boolean
)

data class SessionsState(
    val instantTimerActive: Boolean = false,
    val instantTimerRemainingMillis: Long = 0L,
    val profiles: List<SessionProfile> = emptyList()
)

class SessionsViewModel(
    private val sessionStorage: SessionStorage
) : ViewModel() {

    private val _state = MutableStateFlow(SessionsState())
    val state: StateFlow<SessionsState> = _state
    
    // In a full implementation, we would load saved profiles from SessionStorage
    init {
        // Load dummy profiles for MVP display
        val dummyProfiles = listOf(
            SessionProfile("1", "Deep Work", 9, 0, 11, 30, true),
            SessionProfile("2", "Wind Down", 22, 0, 23, 59, false)
        )
        _state.value = _state.value.copy(profiles = dummyProfiles)
    }

    fun toggleProfile(profileId: String, enabled: Boolean) {
        val updatedProfiles = _state.value.profiles.map {
            if (it.id == profileId) it.copy(enabled = enabled) else it
        }
        _state.value = _state.value.copy(profiles = updatedProfiles)
        // In real app, save to sessionStorage
    }
    
    fun startInstantSession(durationMinutes: Int) {
        _state.value = _state.value.copy(
            instantTimerActive = true,
            instantTimerRemainingMillis = durationMinutes * 60 * 1000L
        )
        // In real app, start a foreground service or WorkManager for the timer tick
    }
    
    fun stopInstantSession() {
        _state.value = _state.value.copy(
            instantTimerActive = false,
            instantTimerRemainingMillis = 0L
        )
    }
}
