package com.focusguard.domain.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class NewSessionWindowTest {

    private fun at(text: String) = LocalDateTime.parse(text)

    @Test fun midday_startsAtNextFullHour_forTwoHours() {
        val w = defaultNewSessionWindow(at("2026-10-26T10:20:00"))
        assertEquals(LocalDate.of(2026, 10, 26), w.date)
        assertEquals(11 to 0, w.fromHour to w.fromMinute)
        assertEquals(13 to 0, w.toHour to w.toMinute)
    }

    @Test fun exactlyOnTheHour_stillMovesToNextHour() {
        val w = defaultNewSessionWindow(at("2026-10-26T10:00:00"))
        assertEquals(11, w.fromHour)
    }

    @Test fun lateEvening_isClampedToEndOfDay() {
        val w = defaultNewSessionWindow(at("2026-10-26T21:30:00"))
        assertEquals(22 to 0, w.fromHour to w.fromMinute)
        assertEquals(23 to 59, w.toHour to w.toMinute)
    }

    @Test fun afterEleven_movesToTomorrowMidnight() {
        val w = defaultNewSessionWindow(at("2026-10-26T23:10:00"))
        assertEquals(LocalDate.of(2026, 10, 27), w.date)
        assertEquals(0, w.fromHour)
        assertEquals(2, w.toHour)
    }

    @Test fun justAfterMidnight_startsAtOneAm_today() {
        val w = defaultNewSessionWindow(at("2026-10-26T00:04:00"))
        assertEquals(LocalDate.of(2026, 10, 26), w.date)
        assertEquals(1, w.fromHour)
        assertEquals(3, w.toHour)
    }

    @Test fun windowAlwaysEndsAfterItStarts() {
        for (hour in 0..23) {
            val w = defaultNewSessionWindow(LocalDateTime.of(2026, 10, 26, hour, 45))
            assertTrue("hour $hour", w.toHour * 60 + w.toMinute > w.fromHour * 60 + w.fromMinute)
        }
    }
}
