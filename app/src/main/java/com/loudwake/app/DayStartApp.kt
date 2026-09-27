package com.loudwake.app

import android.app.Application
import com.loudwake.app.data.DayStartStore
import com.loudwake.app.engine.WakeEngine

class DayStartApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DayStartStore.ensurePersisted(this)
        WakeEngine.rescheduleEnabledAlarms(this)
    }
}
