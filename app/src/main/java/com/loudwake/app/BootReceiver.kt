package com.loudwake.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.loudwake.app.data.DayStartStore
import com.loudwake.app.engine.WakeEngine

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i("DayStartBoot", "action=$action")
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> {
                val pending = goAsync()
                try {
                    DayStartStore.ensurePersisted(context)
                    WakeEngine.rescheduleEnabledAlarms(context)
                } finally {
                    pending.finish()
                }
            }
        }
    }
}
