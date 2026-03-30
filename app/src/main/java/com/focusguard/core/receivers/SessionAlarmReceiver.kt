package com.focusguard.core.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focusguard.core.scheduling.SessionScheduler
import com.focusguard.data.local.SessionDataStore
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking

class SessionAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return

        // Fetch session cleanly using Coroutines from the new DataStore
        val sessionDataStore = SessionDataStore(context)
        val session = runBlocking {
            sessionDataStore.sessionsFlow.firstOrNull()?.find { it.id == sessionId }
        } ?: return

        // Reschedule for next occurrence without mutating system states 
        // since the accessibility engine enforces via purely mathematical timeline tracking
        val scheduler = SessionScheduler(context)
        scheduler.scheduleSession(session)
    }

    companion object {
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_IS_START = "extra_is_start"
    }
}
