package com.focusguard.ui.sessions

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.data.local.FocusSession
import com.focusguard.data.local.SessionStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

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
    val weekOffset: Int = 0,              // 0 = current week

    // Active session
    val isSessionActive: Boolean = false,
    val activeSessionName: String = "",
    val activeSessionEmoji: String = "",
    val activeSessionRemainingMillis: Long = 0L,
    val activeSessionBlockedCount: Int = 0,

    // Sessions list
    val sessions: List<FocusSession> = emptyList(),
    val suggestedSessions: List<SuggestedSession> = PREDEFINED_SESSIONS,

    // App picker
    val installedApps: List<AppInfo> = emptyList()
)

class SessionsViewModel(
    private val sessionStorage: SessionStorage,
    private val sessionScheduler: com.focusguard.core.scheduling.SessionScheduler
) : ViewModel() {

    private val _state = MutableStateFlow(SessionsState())
    val state: StateFlow<SessionsState> = _state

    private var countdownJob: Job? = null

    init {
        loadSessions()
        restoreActiveSessionIfNeeded()
    }

    // ── Sessions CRUD ─────────────────────────────────────────

    private fun loadSessions() {
        _state.value = _state.value.copy(sessions = sessionStorage.getSessions())
    }

    fun saveSession(session: FocusSession) {
        val current = sessionStorage.getSessions().toMutableList()
        val idx = current.indexOfFirst { it.id == session.id }
        if (idx >= 0) current[idx] = session else current.add(session)
        sessionStorage.saveSessions(current)
        _state.value = _state.value.copy(sessions = current.toList())
        sessionScheduler.scheduleSession(session)
    }

    fun deleteSession(id: String) {
        val sessionToDelete = sessionStorage.getSessions().find { it.id == id }
        val updated = sessionStorage.getSessions().filter { it.id != id }
        sessionStorage.saveSessions(updated)
        _state.value = _state.value.copy(sessions = updated)
        sessionToDelete?.let { sessionScheduler.cancelSession(it) }
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
            oneTimeDate     = LocalDate.now()
                .format(DateTimeFormatter.ISO_LOCAL_DATE),
            breakMinutes    = 5,
            blockedPackages = emptySet(),
            description     = ""
        )
    }

    // ── Active session control ────────────────────────────────

    fun startSession(session: FocusSession) {
        val durationMs = session.durationMillis()
        val endTime    = System.currentTimeMillis() + durationMs

        sessionStorage.saveActiveSession(session.id, endTime, session.blockedPackages)

        _state.value = _state.value.copy(
            isSessionActive              = true,
            activeSessionName            = session.name,
            activeSessionEmoji           = session.emoji,
            activeSessionRemainingMillis = durationMs,
            activeSessionBlockedCount    = session.blockedPackages.size
        )

        startCountdown(durationMs)
    }

    fun stopSession() {
        sessionStorage.clearActiveSession()
        countdownJob?.cancel()
        _state.value = _state.value.copy(
            isSessionActive              = false,
            activeSessionName            = "",
            activeSessionEmoji           = "",
            activeSessionRemainingMillis = 0L,
            activeSessionBlockedCount    = 0
        )
    }

    private fun startCountdown(durationMs: Long) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var remaining = durationMs
            while (remaining > 0) {
                delay(1_000L)
                remaining -= 1_000L
                _state.value = _state.value.copy(
                    activeSessionRemainingMillis = remaining.coerceAtLeast(0L)
                )
            }
            // Auto-expire
            sessionStorage.clearActiveSession()
            _state.value = _state.value.copy(
                isSessionActive              = false,
                activeSessionName            = "",
                activeSessionEmoji           = "",
                activeSessionRemainingMillis = 0L,
                activeSessionBlockedCount    = 0
            )
        }
    }

    private fun restoreActiveSessionIfNeeded() {
        if (!sessionStorage.isSessionActive()) return
        val endTime  = sessionStorage.getActiveEndTimeMillis()
        val remaining = endTime - System.currentTimeMillis()
        if (remaining <= 0) {
            sessionStorage.clearActiveSession()
            return
        }
        val sessionId = sessionStorage.getActiveSessionId()
        val session   = sessionStorage.getSessions().firstOrNull { it.id == sessionId }
        _state.value = _state.value.copy(
            isSessionActive              = true,
            activeSessionName            = session?.name ?: "Focus Session",
            activeSessionEmoji           = session?.emoji ?: "⏱️",
            activeSessionRemainingMillis = remaining,
            activeSessionBlockedCount    = sessionStorage.getActiveBlockedPackages().size
        )
        startCountdown(remaining)
    }

    // ── Calendar ──────────────────────────────────────────────

    fun selectDate(date: LocalDate) {
        _state.value = _state.value.copy(selectedDate = date)
    }

    fun navigateWeek(offset: Int) {
        val newOffset = _state.value.weekOffset + offset
        _state.value = _state.value.copy(weekOffset = newOffset)
    }

    /** Sessions that match the currently selected date */
    fun sessionsForSelectedDate(): List<FocusSession> {
        val date       = _state.value.selectedDate
        val dayOfWeek  = date.dayOfWeek.value - 1  // Mon=0 … Sun=6
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
                    .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }   // user apps only
                    .filter { it.packageName != context.packageName }            // exclude FocusGuard
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
            _state.value = _state.value.copy(installedApps = apps)
        }
    }
}
