package com.focusguard.ui.extensions

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattingTest {

    @Test fun formatClock_24Hour() {
        assertEquals("00:00", formatClock(0, 0, true))
        assertEquals("09:05", formatClock(9, 5, true))
        assertEquals("23:59", formatClock(23, 59, true))
    }

    @Test fun formatClock_12Hour() {
        assertEquals("12:00 AM", formatClock(0, 0, false))
        assertEquals("9:05 AM", formatClock(9, 5, false))
        assertEquals("12:30 PM", formatClock(12, 30, false))
        assertEquals("11:59 PM", formatClock(23, 59, false))
    }

    @Test fun timeRangeLabel_usesSameStyleOnBothEnds() {
        assertEquals("9:00 AM – 11:00 AM", timeRangeLabel(9, 0, 11, 0, false))
        assertEquals("09:00 – 23:59", timeRangeLabel(9, 0, 23, 59, true))
    }

    @Test fun appsBlockedLabel_pluralises() {
        assertEquals("1 app blocked", appsBlockedLabel(1))
        assertEquals("2 apps blocked", appsBlockedLabel(2))
        assertEquals("0 apps blocked", appsBlockedLabel(0))
    }
}
