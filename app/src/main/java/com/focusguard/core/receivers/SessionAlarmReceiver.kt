package com.focusguard.core.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.focusguard.core.scheduling.SessionScheduler
import com.focusguard.data.local.SessionDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class SessionAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return

        // Read the DataStore off the main thread; goAsync keeps the receiver alive until we finish.
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val session = SessionDataStore(appContext).sessionsFlow.firstOrNull()
                    ?.find { it.id == sessionId } ?: return@launch

                // Reschedule for next occurrence without mutating system states
                // since the accessibility engine enforces via purely mathematical timeline tracking
                SessionScheduler(appContext).scheduleSession(session)
            } catch (e: Exception) {
                Log.e("SessionAlarmReceiver", "Failed to reschedule session $sessionId", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_IS_START = "extra_is_start"
    }
}
