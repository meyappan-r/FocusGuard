package com.focusguard.ui.extensions

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Formats a time of day as "14:05" (24 h) or "2:05 PM" (12 h). */
fun formatClock(hour: Int, minute: Int, is24Hour: Boolean): String =
    if (is24Hour) {
        "%02d:%02d".format(hour, minute)
    } else {
        "%d:%02d %s".format(if (hour % 12 == 0) 12 else hour % 12, minute, if (hour < 12) "AM" else "PM")
    }

/** "9:00 AM – 11:00 AM" (or 24 h), in the style chosen by the device. */
fun timeRangeLabel(fromHour: Int, fromMinute: Int, toHour: Int, toMinute: Int, is24Hour: Boolean): String =
    "${formatClock(fromHour, fromMinute, is24Hour)} – ${formatClock(toHour, toMinute, is24Hour)}"

fun appsBlockedLabel(count: Int): String = if (count == 1) "1 app blocked" else "$count apps blocked"

/** Follows the device's 12/24-hour setting so every screen shows times the same way. */
@Composable
fun rememberIs24Hour(): Boolean {
    val context = LocalContext.current
    return remember { DateFormat.is24HourFormat(context) }
}
