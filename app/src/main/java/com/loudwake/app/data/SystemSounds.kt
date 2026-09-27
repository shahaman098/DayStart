package com.loudwake.app.data

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings

data class SystemSound(
    val title: String,
    val uri: String
)

object SystemSounds {
    /** Defaults + a handful of installed alarm/notification/ringtone sounds. */
    fun list(context: Context, limitPerType: Int = 6): List<SystemSound> {
        val out = LinkedHashMap<String, SystemSound>()

        fun add(title: String, uri: Uri?) {
            if (uri == null) return
            val key = uri.toString()
            if (key.isBlank() || out.containsKey(key)) return
            out[key] = SystemSound(title, key)
        }

        add(
            "System Default Alarm",
            RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
                ?: Settings.System.DEFAULT_ALARM_ALERT_URI
        )
        add(
            "System Default Notification",
            RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_NOTIFICATION)
                ?: Settings.System.DEFAULT_NOTIFICATION_URI
        )
        add(
            "System Default Ringtone",
            RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
                ?: Settings.System.DEFAULT_RINGTONE_URI
        )

        collect(context, RingtoneManager.TYPE_ALARM, "Alarm", limitPerType, out)
        collect(context, RingtoneManager.TYPE_NOTIFICATION, "Notify", limitPerType, out)
        collect(context, RingtoneManager.TYPE_RINGTONE, "Ring", limitPerType.coerceAtMost(4), out)

        return out.values.toList()
    }

    private fun collect(
        context: Context,
        type: Int,
        prefix: String,
        limit: Int,
        out: LinkedHashMap<String, SystemSound>
    ) {
        runCatching {
            val mgr = RingtoneManager(context)
            mgr.setType(type)
            val cursor = mgr.cursor ?: return
            var n = 0
            while (cursor.moveToNext() && n < limit) {
                val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX) ?: "Sound"
                val uri = mgr.getRingtoneUri(cursor.position) ?: continue
                val key = uri.toString()
                if (!out.containsKey(key)) {
                    out[key] = SystemSound("$prefix: $title", key)
                    n++
                }
            }
        }
    }
}
