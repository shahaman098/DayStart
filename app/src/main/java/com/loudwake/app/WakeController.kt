package com.loudwake.app

import android.content.Context
import com.loudwake.app.engine.WakeEngine

object WakeController {
    fun haltAll(context: Context) {
        WakeEngine.emergencyHalt(context, "STOP DAYSTART")
    }
}
