package com.loudwake.app.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.loudwake.app.data.AlarmItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Calendar
import java.util.Date

object DayStartScheduler {
    private const val TAG = "DayStartScheduler"
    private const val REQ_CLOCK_BASE = 10_000
    private const val REQ_PRESSURE = 20_001
    private const val REQ_RELAPSE = 20_002
    private const val REQ_RELAPSE_TIMEOUT = 20_003

    fun canExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    fun scheduleAlarmClock(context: Context, alarm: AlarmItem) {
        if (!alarm.enabled) {
            cancelAlarmClock(context, alarm.id)
            return
        }
        val trigger = nextTriggerMs(alarm)
        if (trigger == null) {
            cancelAlarmClock(context, alarm.id)
            Log.w(TAG, "skip schedule ${alarm.id}: one-shot date/time already passed")
            return
        }
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = clockPending(context, alarm.id)
        val show = clockShowPending(context, alarm.id)
        try {
            setWakeAlarm(am, trigger, pi, show)
            Log.i(TAG, "scheduled ${alarm.name} id=${alarm.id} at ${Date(trigger)}")
        } catch (t: Throwable) {
            Log.e(TAG, "schedule failed for ${alarm.id}", t)
            // Fallback exact
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, trigger, pi)
                }
            } catch (t2: Throwable) {
                Log.e(TAG, "fallback schedule failed", t2)
            }
        }
    }

    fun nextAlarmLabel(context: Context): String? {
        val next = com.loudwake.app.data.DayStartStore.alarms(context)
            .filter { it.enabled }
            .mapNotNull { alarm -> nextTriggerMs(alarm)?.let { alarm to it } }
            .minByOrNull { it.second }
            ?: return null
        val day = dayLabel(next.second)
        val repeat = if (next.first.repeatDaily) "daily" else "once"
        return "%s %s %02d:%02d · %s".format(
            next.first.name,
            day,
            next.first.hour,
            next.first.minute,
            repeat
        )
    }

    fun dayLabel(triggerMs: Long): String {
        val zone = ZoneId.systemDefault()
        val date = Instant.ofEpochMilli(triggerMs).atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        return when (date) {
            today -> "today"
            today.plusDays(1) -> "tomorrow"
            else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        }
    }

    /** Default one-shot calendar day for [hour]:[minute] (today, or tomorrow if already past). */
    fun defaultOneShotDate(hour: Int, minute: Int): String {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val todayMs = today.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
        val date =
            if (todayMs <= System.currentTimeMillis() + 2_000L) today.plusDays(1) else today
        return date.toString()
    }

    fun cancelAlarmClock(context: Context, alarmId: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(clockPending(context, alarmId))
    }

    fun cancelAllAlarmClocks(context: Context) {
        com.loudwake.app.data.DayStartStore.alarms(context).forEach {
            cancelAlarmClock(context, it.id)
        }
    }

    fun schedulePressure(context: Context, atMs: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(context, REQ_PRESSURE, DayStartReceiver.ACTION_PRESSURE)
        setWakeAlarm(am, atMs, pi, activityPending(context))
    }

    fun scheduleRelapse(context: Context, atMs: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(context, REQ_RELAPSE, DayStartReceiver.ACTION_RELAPSE)
        setWakeAlarm(am, atMs, pi, relapsePending(context))
    }

    fun scheduleRelapseTimeout(context: Context, atMs: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(context, REQ_RELAPSE_TIMEOUT, DayStartReceiver.ACTION_RELAPSE_TIMEOUT)
        setWakeAlarm(am, atMs, pi, activityPending(context))
    }

    fun cancelAllDynamic(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(context, REQ_PRESSURE, DayStartReceiver.ACTION_PRESSURE))
        // Cancel legacy burst action so old pending alarms don't fire after upgrade
        am.cancel(pending(context, REQ_PRESSURE, DayStartReceiver.ACTION_BURST_LEGACY))
        am.cancel(pending(context, REQ_RELAPSE, DayStartReceiver.ACTION_RELAPSE))
        am.cancel(pending(context, REQ_RELAPSE_TIMEOUT, DayStartReceiver.ACTION_RELAPSE_TIMEOUT))
    }

    /**
     * Next fire time for [alarm], or null if a dated one-shot is already in the past.
     * Daily / undated once → next matching clock time (today or tomorrow).
     */
    fun nextTriggerMs(alarm: AlarmItem): Long? {
        if (!alarm.repeatDaily) {
            val date = alarm.oneShotLocalDate()
            if (date != null) {
                val trigger = date
                    .atTime(alarm.hour, alarm.minute)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                return trigger.takeIf { it > System.currentTimeMillis() + 2_000L }
            }
        }
        return nextTriggerMs(alarm.hour, alarm.minute)
    }

    fun nextTriggerMs(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            if (timeInMillis <= System.currentTimeMillis() + 2_000L) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return cal.timeInMillis
    }

    private fun setWakeAlarm(
        am: AlarmManager,
        triggerAt: Long,
        operation: PendingIntent,
        showIntent: PendingIntent
    ) {
        am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), operation)
    }

    private fun clockPending(context: Context, alarmId: String): PendingIntent {
        val intent = Intent(context, DayStartReceiver::class.java).apply {
            action = DayStartReceiver.ACTION_ALARM
            putExtra(DayStartReceiver.EXTRA_ALARM_ID, alarmId)
            // Explicit component — required for reliability on OEM builds
            setClass(context, DayStartReceiver::class.java)
        }
        val req = REQ_CLOCK_BASE + (alarmId.hashCode() and 0xFFFF)
        return PendingIntent.getBroadcast(
            context,
            req,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun clockShowPending(context: Context, alarmId: String): PendingIntent {
        val intent = Intent(context, com.loudwake.app.AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(DayStartReceiver.EXTRA_ALARM_ID, alarmId)
        }
        return PendingIntent.getActivity(
            context,
            REQ_CLOCK_BASE + 5000 + (alarmId.hashCode() and 0xFFF),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun activityPending(context: Context): PendingIntent {
        val intent = Intent(context, com.loudwake.app.AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            30_001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun relapsePending(context: Context): PendingIntent {
        val intent = Intent(context, com.loudwake.app.RelapseActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            30_002,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun pending(context: Context, req: Int, action: String): PendingIntent {
        val intent = Intent(context, DayStartReceiver::class.java).apply {
            this.action = action
            setClass(context, DayStartReceiver::class.java)
        }
        return PendingIntent.getBroadcast(
            context,
            req,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
