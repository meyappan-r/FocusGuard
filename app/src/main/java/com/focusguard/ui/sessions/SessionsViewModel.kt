package com.focusguard.ui.sessions

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.core.scheduling.SessionScheduler
import com.focusguard.data.local.FocusSession
import com.focusguard.data.local.SessionDataStore
import com.focusguard.domain.rules.remainingMillisAt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.time.LocalTime

// ── App info for the picker ───────────────────────────────────
data class AppInfo(
    val packageName: String,
    val appName: String
)

// ── Suggested (predefined) session templates ──────────────────
data class SuggestedSession(
    val emoji: String,
    val name: String,
    val tag: String,
    val fromHour: Int,
    val fromMinute: Int,
    val toHour: Int,
    val toMinute: Int,
    val defaultBreakMinutes: Int
)

val PREDEFINED_SESSIONS = listOf(
    SuggestedSession("☀️", "Morning Study", "Study",  6,  0,  8,  0, 5),
    SuggestedSession("🌤️", "Afternoon Study", "Study", 14,  0, 16,  0, 5),
    SuggestedSession("🌙", "Night Study",    "Study", 20,  0, 22,  0, 5),
    SuggestedSession("💼", "Work Sprint",    "Work",   9,  0, 11,  0, 10),
    SuggestedSession("🎯", "Deep Focus",     "Work",  10,  0, 11, 25, 0)
)

// ── Tags with color codes ─────────────────────────────────────
data class SessionTag(val name: String, val colorHex: String)

val DEFAULT_TAGS = listOf(
    SessionTag("Study", "#F5C518"),
    SessionTag("Work",  "#4A90D9"),
    SessionTag("Untagged", "#888888")
)

// ── Main UI state ─────────────────────────────────────────────
data class SessionsState(
    // Calendar
    val selectedDate: LocalDate = LocalDate.now(),
    val weekOffset: Int = 0,

    // Active session dynamically computed
    val activeSession: FocusSession? = null,
    val activeSessionRemainingMillis: Long = 0L,
    val activeBreakExpiryMillis: Long = 0L,
    val allActiveSessions: List<FocusSession> = emptyList(),

    // Sessions list
    val sessions: List<FocusSession> = emptyList(),
    val suggestedSessions: List<SuggestedSession> = PREDEFINED_SESSIONS,

    // App picker
    val installedApps: List<AppInfo> = emptyList()
)

class SessionsViewModel(
    private val sessionDataStore: SessionDataStore,
    private val sessionScheduler: SessionScheduler
) : ViewModel() {

    private val _state = MutableStateFlow(SessionsState())
    val state: StateFlow<SessionsState> = _state

    init {
        // Collect sessions from the DataStore
        viewModelScope.launch {
            sessionDataStore.sessionsFlow.collect { sessionsList ->
                _state.update { it.copy(sessions = sessionsList) }
            }
        }
        
        // Collect break expiry
        viewModelScope.launch {
            sessionDataStore.breakExpiryFlow.collect { expiry ->
                _state.update { it.copy(activeBreakExpiryMillis = expiry) }
            }
        }
        
        // Start a lightweight loop that mathematically evaluates active sessions based on system clock
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                evaluateActiveSession()
                delay(1000L) // Refresh every second
            }
        }
    }

    fun clearBreak() {
        viewModelScope.launch {
            sessionDataStore.clearBreak()
        }
    }

    private fun evaluateActiveSession() {
        val now = LocalDateTime.now()
        val currentSessions = _state.value.sessions
        var primaryActive: FocusSession? = null
        var primaryRemaining = 0L
        val activeList = mutableListOf<FocusSession>()

        for (session in currentSessions) {
            val remainingMs = session.remainingMillisAt(now) ?: continue
            activeList.add(session)
            if (primaryActive == null || remainingMs < primaryRemaining) {
                primaryActive = session
                primaryRemaining = remainingMs
            }
        }

        _state.update {
            it.copy(
                activeSession = primaryActive,
                activeSessionRemainingMillis = primaryRemaining,
                allActiveSessions = activeList
            )
        }
    }

    // ── Sessions CRUD ─────────────────────────────────────────

    fun saveSession(session: FocusSession) {
        viewModelScope.launch {
            val current = _state.value.sessions.toMutableList()
            val idx = current.indexOfFirst { it.id == session.id }
            if (idx >= 0) current[idx] = session else current.add(session)
            
            // Push to DataStore
            sessionDataStore.saveSessions(current)
            
            // Update scheduling triggers
            sessionScheduler.scheduleSession(session)
        }
    }

    fun deleteSession(id: String) {
        viewModelScope.launch {
            val sessionToDelete = _state.value.sessions.find { it.id == id }
            val updated = _state.value.sessions.filter { it.id != id }
            
            // Push to DataStore
            sessionDataStore.saveSessions(updated)
            
            // Cancel background alarms
            sessionToDelete?.let { sessionScheduler.cancelSession(it) }
        }
    }

    fun newSessionFromSuggested(s: SuggestedSession): FocusSession = FocusSession(
        id              = UUID.randomUUID().toString(),
        name            = s.name,
        emoji           = s.emoji,
        tag             = s.tag,
        fromHour        = s.fromHour,
        fromMinute      = s.fromMinute,
        toHour          = s.toHour,
        toMinute        = s.toMinute,
        repeatEnabled   = true,
        repeatDays      = setOf(0, 1, 2, 3, 4), // Mon-Fri
        oneTimeDate     = "",
        breakMinutes    = s.defaultBreakMinutes,
        blockedPackages = emptySet(),
        description     = ""
    )

    fun blankSession(): FocusSession {
        val now = java.util.Calendar.getInstance()
        return FocusSession(
            id              = UUID.randomUUID().toString(),
            name            = "",
            emoji           = "⏱️",
            tag             = "Untagged",
            fromHour        = now.get(java.util.Calendar.HOUR_OF_DAY),
            fromMinute      = 0,
            toHour          = (now.get(java.util.Calendar.HOUR_OF_DAY) + 2) % 24,
            toMinute        = 0,
            repeatEnabled   = false,
            repeatDays      = emptySet(),
            oneTimeDate     = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
            breakMinutes    = 5,
            blockedPackages = emptySet(),
            description     = ""
        )
    }

    // ── Calendar ──────────────────────────────────────────────

    fun selectDate(date: LocalDate) {
        _state.update { it.copy(selectedDate = date) }
    }

    fun navigateWeek(offset: Int) {
        _state.update { it.copy(weekOffset = it.weekOffset + offset) }
    }

    fun sessionsForSelectedDate(): List<FocusSession> {
        val date       = _state.value.selectedDate
        val dayOfWeek  = date.dayOfWeek.value - 1 
        val dateString = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        return _state.value.sessions.filter { s ->
            if (s.repeatEnabled) dayOfWeek in s.repeatDays
            else s.oneTimeDate == dateString
        }
    }

    // ── App picker ────────────────────────────────────────────

    fun loadInstalledApps(context: Context) {
        if (_state.value.installedApps.isNotEmpty()) return
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }   
                    .filter { it.packageName != context.packageName }          
                    .mapNotNull { info ->
                        runCatching {
                            AppInfo(
                                packageName = info.packageName,
                                appName     = pm.getApplicationLabel(info).toString()
                            )
                        }.getOrNull()
                    }
                    .sortedBy { it.appName }
            }
            _state.update { it.copy(installedApps = apps) }
        }
    }
}
