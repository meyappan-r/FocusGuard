package com.focusguard.data.local

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Characterization tests for the JSON format persisted in DataStore (key `sessions_json_v2`). */
class FocusSessionJsonTest {

    private val sample = FocusSession(
        id = "id-1", name = "Deep Focus", emoji = "🎯", tag = "Work",
        fromHour = 10, fromMinute = 5, toHour = 11, toMinute = 30,
        repeatEnabled = true, repeatDays = setOf(0, 2, 4), oneTimeDate = "",
        breakMinutes = 10, blockedPackages = setOf("com.a", "com.b"), description = "notes"
    )

    @Test fun roundTrip_preservesEveryField() {
        assertEquals(sample, FocusSession.fromJson(JSONObject(sample.toJson().toString())))
    }

    @Test fun missingOptionalFields_useDefaults() {
        val obj = sample.toJson().apply {
            remove("emoji"); remove("tag"); remove("oneTimeDate"); remove("breakMinutes"); remove("description")
        }
        val parsed = FocusSession.fromJson(obj)
        assertNotNull(parsed)
        assertEquals("⏱️", parsed!!.emoji)
        assertEquals("Untagged", parsed.tag)
        assertEquals("", parsed.oneTimeDate)
        assertEquals(5, parsed.breakMinutes)
        assertEquals("", parsed.description)
    }

    @Test fun missingRequiredField_returnsNull_silently() {
        // Pins B-020: malformed entries vanish without any error being surfaced.
        val obj = sample.toJson().apply { remove("fromHour") }
        assertNull(FocusSession.fromJson(obj))
    }

    @Test fun wrongTypeForRequiredField_returnsNull() {
        val obj = sample.toJson().apply { put("repeatDays", "not-an-array") }
        assertNull(FocusSession.fromJson(obj))
    }

    @Test fun listSerialization_matchesStoredShape() {
        val arr = JSONArray().apply { put(sample.toJson()) }
        val restored = (0 until arr.length()).mapNotNull { FocusSession.fromJson(arr.getJSONObject(it)) }
        assertEquals(listOf(sample), restored)
    }
}
