package com.focusguard.domain.rules

import com.focusguard.data.local.FocusSession
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private const val MILLIS_PER_DAY = 24 * 60 * 60_000L

/**
 * Single definition of "is this session running at [now]?", shared by the accessibility
 * service (enforcement) and the sessions screen (countdown card).
 *
 * @return milliseconds until the session window ends, or `null` if the session is not active.
 *
 * Behaviour notes (kept exactly as the two former copies behaved):
 *  - the day check uses the date/weekday of [now] itself, so a window that wraps past midnight
 *    is only matched on the day it started (see B-004 in docs/BUGS_AND_TECHNICAL_DEBT.md);
 *  - `from` is inclusive and `to` is exclusive; `to <= from` means the window wraps midnight;
 *  - weekday indices are 0 = Monday ... 6 = Sunday.
 */
fun FocusSession.remainingMillisAt(now: LocalDateTime): Long? {
    val dayOfWeek = now.dayOfWeek.value - 1
    val isToday = if (repeatEnabled) {
        repeatDays.contains(dayOfWeek)
    } else {
        oneTimeDate == now.format(DateTimeFormatter.ISO_LOCAL_DATE)
    }
    if (!isToday) return null

    val fromMs = (fromHour * 60 + fromMinute) * 60000L
    val toMs = (toHour * 60 + toMinute) * 60000L
    val currentMs = (now.hour * 60 + now.minute) * 60000L + now.second * 1000L

    val isActive = if (toMs > fromMs) {
        currentMs in fromMs until toMs
    } else {
        currentMs >= fromMs || currentMs < toMs
    }
    if (!isActive) return null

    return if (toMs > fromMs) {
        toMs - currentMs
    } else if (currentMs >= fromMs) {
        (MILLIS_PER_DAY - currentMs) + toMs
    } else {
        toMs - currentMs
    }
}

/** Default date and time window offered for a brand-new one-time session. */
data class NewSessionWindow(
    val date: LocalDate,
    val fromHour: Int,
    val fromMinute: Int,
    val toHour: Int,
    val toMinute: Int
)

/**
 * Starts at the next full hour (so it always passes the "start must be in the future" check) and
 * lasts two hours, clamped to 23:59 so it never crosses midnight. After 23:00 the next full hour
 * is tomorrow's 00:00, so the date moves to tomorrow.
 */
fun defaultNewSessionWindow(now: LocalDateTime): NewSessionWindow {
    val start = now.truncatedTo(ChronoUnit.HOURS).plusHours(1)
    val endHour = start.hour + 2
    return if (endHour >= 24) {
        NewSessionWindow(start.toLocalDate(), start.hour, 0, 23, 59)
    } else {
        NewSessionWindow(start.toLocalDate(), start.hour, 0, endHour, 0)
    }
}
