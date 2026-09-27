package com.loudwake.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object DayStartStore {
    private const val PREFS = "daystart_store"
    private const val KEY_ALARMS = "alarms_json"
    private const val KEY_SESSION = "session_json"
    private const val KEY_BATTERY_PROMPT_DISMISSED = "battery_prompt_dismissed"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isBatteryPromptDismissed(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BATTERY_PROMPT_DISMISSED, false)

    fun setBatteryPromptDismissed(context: Context, dismissed: Boolean) {
        prefs(context).edit().putBoolean(KEY_BATTERY_PROMPT_DISMISSED, dismissed).apply()
    }

    /**
     * Persist defaults once so alarm IDs stay stable across schedule/fire.
     * Must NOT call [alarms] (would recurse).
     */
    fun ensurePersisted(context: Context) {
        val p = prefs(context)
        if (p.contains(KEY_ALARMS)) {
            val raw = p.getString(KEY_ALARMS, null)
            if (raw.isNullOrBlank() || raw == "[]") {
                saveAlarms(context, defaultAlarms())
            }
            return
        }
        saveAlarms(context, defaultAlarms())
    }

    fun alarms(context: Context): List<AlarmItem> {
        ensurePersisted(context)
        val raw = prefs(context).getString(KEY_ALARMS, null)
        if (raw.isNullOrBlank()) {
            val defaults = defaultAlarms()
            saveAlarms(context, defaults)
            return defaults
        }
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) add(AlarmItem.fromJson(arr.getJSONObject(i)))
            }.ifEmpty {
                defaultAlarms().also { saveAlarms(context, it) }
            }
        }.getOrElse {
            defaultAlarms().also { saveAlarms(context, it) }
        }
    }

    fun saveAlarms(context: Context, alarms: List<AlarmItem>) {
        val arr = JSONArray()
        alarms.forEach { arr.put(it.toJson()) }
        prefs(context).edit().putString(KEY_ALARMS, arr.toString()).commit()
    }

    fun upsert(context: Context, alarm: AlarmItem) {
        val list = alarms(context).toMutableList()
        val idx = list.indexOfFirst { it.id == alarm.id }
        if (idx >= 0) list[idx] = alarm else list.add(alarm)
        saveAlarms(context, list)
    }

    fun delete(context: Context, id: String) {
        saveAlarms(context, alarms(context).filterNot { it.id == id })
    }

    fun alarm(context: Context, id: String): AlarmItem? =
        alarms(context).firstOrNull { it.id == id }

    fun session(context: Context): WakeSession {
        val raw = prefs(context).getString(KEY_SESSION, null) ?: return WakeSession()
        return runCatching { WakeSession.fromJson(JSONObject(raw)) }
            .getOrDefault(WakeSession())
    }

    fun saveSession(context: Context, session: WakeSession) {
        prefs(context).edit().putString(KEY_SESSION, session.toJson().toString()).commit()
    }

    fun clearSession(context: Context) {
        saveSession(context, WakeSession())
    }

    private fun defaultAlarms(): List<AlarmItem> = listOf(
        AlarmItem(
            id = "morning-default",
            name = "Morning",
            hour = 7,
            minute = 0,
            enabled = false
        )
    )
}
