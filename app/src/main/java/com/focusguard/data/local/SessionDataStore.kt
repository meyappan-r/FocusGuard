package com.focusguard.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.MutablePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

// ──────────────────────────────────────────────────────────────
//  Domain model for a saved focus session
// ──────────────────────────────────────────────────────────────
data class FocusSession(
    val id: String,
    val name: String,
    val emoji: String,
    val tag: String,
    val fromHour: Int,
    val fromMinute: Int,
    val toHour: Int,
    val toMinute: Int,
    val repeatEnabled: Boolean,
    val repeatDays: Set<Int>, // 0=Mon … 6=Sun
    val oneTimeDate: String,  // "YYYY-MM-DD"
    val breakMinutes: Int,
    val blockedPackages: Set<String>,
    val description: String
) {
    fun durationMillis(): Long {
        val fromMs = (fromHour * 60L + fromMinute) * 60_000L
        var toMs   = (toHour   * 60L + toMinute)   * 60_000L
        if (toMs <= fromMs) toMs += 24 * 60 * 60_000L // crosses midnight
        return toMs - fromMs
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("emoji", emoji)
        put("tag", tag)
        put("fromHour", fromHour)
        put("fromMinute", fromMinute)
        put("toHour", toHour)
        put("toMinute", toMinute)
        put("repeatEnabled", repeatEnabled)
        put("repeatDays", JSONArray(repeatDays.toList()))
        put("oneTimeDate", oneTimeDate)
        put("breakMinutes", breakMinutes)
        put("blockedPackages", JSONArray(blockedPackages.toList()))
        put("description", description)
    }

    companion object {
        fun fromJson(obj: JSONObject): FocusSession? = runCatching {
            val days = mutableSetOf<Int>()
            val daysArr = obj.getJSONArray("repeatDays")
            repeat(daysArr.length()) { days.add(daysArr.getInt(it)) }

            val pkgs = mutableSetOf<String>()
            val pkgsArr = obj.getJSONArray("blockedPackages")
            repeat(pkgsArr.length()) { pkgs.add(pkgsArr.getString(it)) }

            FocusSession(
                id              = obj.getString("id"),
                name            = obj.getString("name"),
                emoji           = obj.optString("emoji", "⏱️"),
                tag             = obj.optString("tag", "Untagged"),
                fromHour        = obj.getInt("fromHour"),
                fromMinute      = obj.getInt("fromMinute"),
                toHour          = obj.getInt("toHour"),
                toMinute        = obj.getInt("toMinute"),
                repeatEnabled   = obj.getBoolean("repeatEnabled"),
                repeatDays      = days,
                oneTimeDate     = obj.optString("oneTimeDate", ""),
                breakMinutes    = obj.optInt("breakMinutes", 5),
                blockedPackages = pkgs,
                description     = obj.optString("description", "")
            )
        }.getOrNull()
    }
}

// ──────────────────────────────────────────────────────────────
//  Storage class using Preferences DataStore
// ──────────────────────────────────────────────────────────────

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "focus_sessions_datastore")

class SessionDataStore(private val context: Context) {

    companion object {
        private val KEY_SESSIONS = stringPreferencesKey("sessions_json_v2")
        private val KEY_BREAK_EXPIRY = longPreferencesKey("active_break_expiry_millis")
    }

    val sessionsFlow: Flow<List<FocusSession>> = context.dataStore.data
        .map { preferences ->
            val raw = preferences[KEY_SESSIONS] ?: return@map emptyList()
            try {
                val arr = JSONArray(raw)
                (0 until arr.length()).mapNotNull { FocusSession.fromJson(arr.getJSONObject(it)) }
            } catch (e: Exception) {
                emptyList()
            }
        }

    suspend fun saveSessions(sessions: List<FocusSession>) {
        val arr = JSONArray()
        sessions.forEach { arr.put(it.toJson()) }
        context.dataStore.edit { preferences ->
            preferences[KEY_SESSIONS] = arr.toString()
        }
    }

    // ── Break Tracking ─────────────────────────────────────────

    val breakExpiryFlow: Flow<Long> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_BREAK_EXPIRY] ?: 0L
        }

    suspend fun setBreakExpiry(expiryMillis: Long) {
        context.dataStore.edit { preferences ->
            preferences[KEY_BREAK_EXPIRY] = expiryMillis
        }
    }

    suspend fun clearBreak() {
        context.dataStore.edit { preferences ->
            (preferences as androidx.datastore.preferences.core.MutablePreferences).remove(KEY_BREAK_EXPIRY)
        }
    }
}
