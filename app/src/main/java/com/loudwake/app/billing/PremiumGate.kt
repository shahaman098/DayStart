package com.loudwake.app.billing

import com.loudwake.app.data.WakeMode

object PremiumGate {
    const val ENTITLEMENT_ID = "premium"
    const val FREE_ALARM_LIMIT = 1

    private val premiumModes = setOf(
        WakeMode.GAUNTLET,
        WakeMode.STRICT,
        WakeMode.STEPS,
        WakeMode.MEMORY,
        WakeMode.SHAKE,
        WakeMode.PHRASE
    )

    fun requiresPremium(mode: WakeMode): Boolean = mode in premiumModes

    fun canAddAlarm(alarmCount: Int, isPremium: Boolean): Boolean =
        isPremium || alarmCount < FREE_ALARM_LIMIT
}
