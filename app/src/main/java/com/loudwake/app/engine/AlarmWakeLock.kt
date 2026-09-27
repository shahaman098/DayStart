package com.loudwake.app.engine

import android.content.Context
import android.os.PowerManager
import android.util.Log

/**
 * Keeps CPU awake during fire, and briefly forces the screen on so ColorOS
 * will show the full-screen alarm UI.
 */
object AlarmWakeLock {
    private const val TAG = "AlarmWakeLock"
    private var cpuLock: PowerManager.WakeLock? = null
    private var screenLock: PowerManager.WakeLock? = null

    fun acquire(context: Context, ms: Long = 3 * 60_000L) {
        val app = context.applicationContext
        val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        release()
        try {
            cpuLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "daystart:cpu").apply {
                setReferenceCounted(false)
                acquire(ms)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "cpu wake lock failed", t)
        }
        // Deprecated flags still required on many OEM builds to light the panel
        @Suppress("DEPRECATION")
        try {
            screenLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "daystart:screen"
            ).apply {
                setReferenceCounted(false)
                acquire(60_000L)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "screen wake lock failed", t)
        }
    }

    fun release() {
        releaseOne(cpuLock)
        cpuLock = null
        releaseOne(screenLock)
        screenLock = null
    }

    private fun releaseOne(lock: PowerManager.WakeLock?) {
        try {
            if (lock?.isHeld == true) lock.release()
        } catch (_: Exception) {
        }
    }
}
