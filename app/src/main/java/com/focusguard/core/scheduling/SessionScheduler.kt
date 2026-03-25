package com.focusguard.core.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.focusguard.core.receivers.SessionAlarmReceiver
import com.focusguard.data.local.FocusSession
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class SessionScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleSession(session: FocusSession) {
        val nextStart = calculateNextTrigger(session, isStart = true)
        val nextEnd = calculateNextTrigger(session, isStart = false)

        if (nextStart != null) {
            setExactAlarm(session.id.hashCode(), nextStart, session.id, true)
        }
        if (nextEnd != null) {
            setExactAlarm(session.id.hashCode() + 1, nextEnd, session.id, false)
        }
    }

    fun cancelSession(session: FocusSession) {
        val intentStart = Intent(context, SessionAlarmReceiver::class.java)
        val pendingIntentStart = PendingIntent.getBroadcast(
            context, session.id.hashCode(), intentStart,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntentStart)

        val intentEnd = Intent(context, SessionAlarmReceiver::class.java)
        val pendingIntentEnd = PendingIntent.getBroadcast(
            context, session.id.hashCode() + 1, intentEnd,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntentEnd)
    }

    private fun setExactAlarm(requestCode: Int, triggerTimeMillis: Long, sessionId: String, isStart: Boolean) {
        val intent = Intent(context, SessionAlarmReceiver::class.java).apply {
            putExtra(SessionAlarmReceiver.EXTRA_SESSION_ID, sessionId)
            putExtra(SessionAlarmReceiver.EXTRA_IS_START, isStart)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            // Fallback if permission revoked
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent)
        }
    }

    private fun calculateNextTrigger(session: FocusSession, isStart: Boolean): Long? {
        val now = LocalDateTime.now()
        val targetTime = if (isStart) {
            LocalTime.of(session.fromHour, session.fromMinute)
        } else {
            LocalTime.of(session.toHour, session.toMinute)
        }

        if (!session.repeatEnabled) {
            if (session.oneTimeDate.isEmpty()) return null
            val date = runCatching { LocalDate.parse(session.oneTimeDate) }.getOrNull() ?: return null
            val triggerDateTime = LocalDateTime.of(date, targetTime)
            if (triggerDateTime.isBefore(now)) return null
            return triggerDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }

        if (session.repeatDays.isEmpty()) return null

        var nextDateTime = LocalDateTime.of(now.toLocalDate(), targetTime)
        if (nextDateTime.isBefore(now)) {
            nextDateTime = nextDateTime.plusDays(1)
        }

        // Keep checking days until we hit one in repeatDays
        // repeatDays uses 0=Mon..6=Sun. DayOfWeek uses 1=Mon..7=Sun
        var checks = 0
        while (checks <= 7) {
            val dayIndex = nextDateTime.dayOfWeek.value - 1
            if (session.repeatDays.contains(dayIndex)) {
                return nextDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
            nextDateTime = nextDateTime.plusDays(1)
            checks++
        }
        return null
    }
}
