package com.focusguard.domain.rules

import com.focusguard.data.local.FocusSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

/**
 * Characterization tests: they pin down what [remainingMillisAt] does today.
 * Cases marked "QUIRK" document behaviour that is questionable (see B-004 in
 * docs/BUGS_AND_TECHNICAL_DEBT.md); if that behaviour is deliberately changed,
 * update the expected value together with the code.
 */
class SessionRulesTest {

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE

        // 2026-10-26 is a Monday (weekday index 0 in this app), 2026-10-27 a Tuesday.
        const val MONDAY = "2026-10-26"
        const val TUESDAY = "2026-10-27"
    }

    private fun session(
        from: Pair<Int, Int>,
        to: Pair<Int, Int>,
        repeatEnabled: Boolean = false,
        repeatDays: Set<Int> = emptySet(),
        oneTimeDate: String = MONDAY
    ) = FocusSession(
        id = "s", name = "Test", emoji = "x", tag = "Untagged",
        fromHour = from.first, fromMinute = from.second,
        toHour = to.first, toMinute = to.second,
        repeatEnabled = repeatEnabled, repeatDays = repeatDays, oneTimeDate = oneTimeDate,
        breakMinutes = 5, blockedPackages = setOf("com.example.app"), description = ""
    )

    private fun at(date: String, hour: Int, minute: Int, second: Int = 0): LocalDateTime =
        LocalDateTime.parse("${date}T%02d:%02d:%02d".format(hour, minute, second))

    // ── one-time sessions ───────────────────────────────────────

    @Test fun oneTime_insideWindow_returnsRemaining() {
        val s = session(9 to 0, 11 to 0)
        assertEquals(90 * MINUTE, s.remainingMillisAt(at(MONDAY, 9, 30)))
    }

    @Test fun oneTime_startIsInclusive() {
        val s = session(9 to 0, 11 to 0)
        assertEquals(2 * HOUR, s.remainingMillisAt(at(MONDAY, 9, 0, 0)))
    }

    @Test fun oneTime_endIsExclusive() {
        val s = session(9 to 0, 11 to 0)
        assertNull(s.remainingMillisAt(at(MONDAY, 11, 0, 0)))
        assertEquals(1_000L, s.remainingMillisAt(at(MONDAY, 10, 59, 59)))
    }

    @Test fun oneTime_beforeWindow_isInactive() {
        assertNull(session(9 to 0, 11 to 0).remainingMillisAt(at(MONDAY, 8, 59, 59)))
    }

    @Test fun oneTime_otherDate_isInactive() {
        assertNull(session(9 to 0, 11 to 0).remainingMillisAt(at(TUESDAY, 10, 0)))
    }

    // ── repeating sessions (0 = Monday ... 6 = Sunday) ──────────

    @Test fun repeating_matchingWeekday_isActive() {
        val s = session(9 to 0, 11 to 0, repeatEnabled = true, repeatDays = setOf(0))
        assertNotNull(s.remainingMillisAt(at(MONDAY, 10, 0)))
    }

    @Test fun repeating_nonMatchingWeekday_isInactive() {
        val s = session(9 to 0, 11 to 0, repeatEnabled = true, repeatDays = setOf(0))
        assertNull(s.remainingMillisAt(at(TUESDAY, 10, 0)))
    }

    @Test fun repeating_ignoresOneTimeDate() {
        val s = session(9 to 0, 11 to 0, repeatEnabled = true, repeatDays = setOf(1), oneTimeDate = MONDAY)
        assertNull(s.remainingMillisAt(at(MONDAY, 10, 0)))
        assertNotNull(s.remainingMillisAt(at(TUESDAY, 10, 0)))
    }

    @Test fun repeating_emptyDays_neverActive() {
        val s = session(0 to 0, 23 to 59, repeatEnabled = true, repeatDays = emptySet())
        assertNull(s.remainingMillisAt(at(MONDAY, 12, 0)))
    }

    // ── windows that wrap past midnight ─────────────────────────

    @Test fun wrap_beforeMidnightOnStartDay_isActive() {
        val s = session(22 to 0, 2 to 0, repeatEnabled = true, repeatDays = setOf(0))
        // 23:00 Monday -> 1h to midnight + 2h = 3h left
        assertEquals(3 * HOUR, s.remainingMillisAt(at(MONDAY, 23, 0)))
    }

    @Test fun wrap_afterMidnight_dayAfterNotSelected_QUIRK_isInactive() {
        // QUIRK (B-004): the weekday check uses *today* (Tuesday), not the day the window started.
        val s = session(22 to 0, 2 to 0, repeatEnabled = true, repeatDays = setOf(0))
        assertNull(s.remainingMillisAt(at(TUESDAY, 1, 0)))
    }

    @Test fun wrap_afterMidnight_dayAfterSelected_isActive() {
        val s = session(22 to 0, 2 to 0, repeatEnabled = true, repeatDays = setOf(0, 1))
        assertEquals(HOUR, s.remainingMillisAt(at(TUESDAY, 1, 0)))
    }

    @Test fun wrap_afterMidnight_oneTimeSession_QUIRK_isInactiveOnNextDate() {
        // QUIRK (B-004): a one-time window dated Monday stops matching once the date becomes Tuesday.
        val s = session(22 to 0, 2 to 0, oneTimeDate = MONDAY)
        assertNull(s.remainingMillisAt(at(TUESDAY, 1, 0)))
    }

    @Test fun wrap_outsideWindow_isInactive() {
        val s = session(22 to 0, 2 to 0, repeatEnabled = true, repeatDays = setOf(0, 1))
        assertNull(s.remainingMillisAt(at(MONDAY, 12, 0)))
    }

    @Test fun equalFromAndTo_isTreatedAsWholeDay() {
        // from == to takes the "wrap" branch, which matches every second of a selected day.
        val s = session(9 to 0, 9 to 0, repeatEnabled = true, repeatDays = setOf(0))
        assertNotNull(s.remainingMillisAt(at(MONDAY, 3, 0)))
        assertNotNull(s.remainingMillisAt(at(MONDAY, 9, 0)))
    }

    // ── model helper ────────────────────────────────────────────

    @Test fun durationMillis_handlesWrapAround() {
        assertEquals(2 * HOUR, session(9 to 0, 11 to 0).durationMillis())
        assertEquals(4 * HOUR, session(22 to 0, 2 to 0).durationMillis())
    }
}
