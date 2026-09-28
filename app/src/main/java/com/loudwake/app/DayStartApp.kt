package com.loudwake.app

import android.app.Application
import com.loudwake.app.billing.DayStartBilling
import com.loudwake.app.data.DayStartStore
import com.loudwake.app.engine.WakeEngine

class DayStartApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DayStartStore.ensurePersisted(this)
        DayStartBilling.configure(this)
        WakeEngine.rescheduleEnabledAlarms(this)
    }
}
