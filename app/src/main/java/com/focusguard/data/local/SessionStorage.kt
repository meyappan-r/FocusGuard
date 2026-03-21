package com.focusguard.data.local

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

// ──────────────────────────────────────────────────────────────
//  Domain model for a saved focus session
// ──────────────────────────────────────────────────────────────
data class FocusSession(
    val id: String,
    val name: String,
    val emoji: String,
    val tag: String,          // "Study", "Work", "Untagged"
    val fromHour: Int,
    val fromMinute: Int,
    val toHour: Int,
    val toMinute: Int,
    val repeatEnabled: Boolean,
    val repeatDays: Set<Int>, // 0=Mon … 6=Sun; empty when repeatEnabled=false
    val oneTimeDate: String,  // "YYYY-MM-DD"; empty when repeatEnabled=true
    val breakMinutes: Int,
    val blockedPackages: Set<String>,
    val description: String
) {
    /** Duration in milliseconds from fromHour:fromMinute to toHour:toMinute (same day roll-over handled) */
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
//  Storage class
// ──────────────────────────────────────────────────────────────
class SessionStorage(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Session list ──────────────────────────────────────────

    fun saveSessions(sessions: List<FocusSession>) {
        val arr = JSONArray()
        sessions.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_SESSIONS, arr.toString()).apply()
    }

    fun getSessions(): List<FocusSession> {
        val raw = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { FocusSession.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    // ── Active session ────────────────────────────────────────

    fun saveActiveSession(sessionId: String, endTimeMillis: Long, blockedPackages: Set<String>) {
        val arr = JSONArray()
        blockedPackages.forEach { arr.put(it) }
        prefs.edit()
            .putBoolean(KEY_ACTIVE, true)
            .putString(KEY_ACTIVE_ID, sessionId)
            .putLong(KEY_ACTIVE_END, endTimeMillis)
            .putString(KEY_ACTIVE_PKGS, arr.toString())
            .apply()
    }

    fun clearActiveSession() {
        prefs.edit()
            .remove(KEY_ACTIVE)
            .remove(KEY_ACTIVE_ID)
            .remove(KEY_ACTIVE_END)
            .remove(KEY_ACTIVE_PKGS)
            .apply()
    }

    fun isSessionActive(): Boolean = prefs.getBoolean(KEY_ACTIVE, false)

    fun getActiveSessionId(): String = prefs.getString(KEY_ACTIVE_ID, "") ?: ""

    fun getActiveEndTimeMillis(): Long = prefs.getLong(KEY_ACTIVE_END, 0L)

    fun getActiveBlockedPackages(): Set<String> {
        val raw = prefs.getString(KEY_ACTIVE_PKGS, null) ?: return emptySet()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        }.getOrDefault(emptySet())
    }

    // ── Legacy stubs (kept for backward-compat calls) ─────────

    @Deprecated("Use saveActiveSession / sessions list instead")
    fun saveProfile(profileId: String, name: String, startMillis: Long, endMillis: Long, enabled: Boolean, blocked: Set<String>) {
        // no-op kept to avoid compile errors in old callers
    }

    @Deprecated("Use getActiveBlockedPackages instead")
    fun getProfileBlockedApps(profileId: String): Set<String> = emptySet()

    @Deprecated("Use isSessionActive instead")
    fun isProfileEnabled(profileId: String): Boolean = false

    companion object {
        private const val PREFS_NAME      = "session_preferences"
        private const val KEY_SESSIONS    = "sessions_json"
        private const val KEY_ACTIVE      = "active_session"
        private const val KEY_ACTIVE_ID   = "active_session_id"
        private const val KEY_ACTIVE_END  = "active_session_end"
        private const val KEY_ACTIVE_PKGS = "active_session_pkgs"
    }
}
