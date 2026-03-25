package com.focusguard.core.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focusguard.core.scheduling.SessionScheduler
import com.focusguard.data.local.SessionStorage

class SessionAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return
        val isStart = intent.getBooleanExtra(EXTRA_IS_START, true)

        val storage = SessionStorage(context)
        val sessions = storage.getSessions()
        val session = sessions.find { it.id == sessionId } ?: return

        if (isStart) {
            val now = System.currentTimeMillis()
            val duration = session.durationMillis()
            storage.saveActiveSession(session.id, now + duration, session.blockedPackages)
        } else {
            if (storage.getActiveSessionId() == sessionId) {
                storage.clearActiveSession()
            }
        }

        // Reschedule for next occurrence
        val scheduler = SessionScheduler(context)
        scheduler.scheduleSession(session)
    }

    companion object {
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_IS_START = "extra_is_start"
    }
}
