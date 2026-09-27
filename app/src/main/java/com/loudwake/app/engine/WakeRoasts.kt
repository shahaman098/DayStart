package com.loudwake.app.engine

object WakeRoasts {
    val wakeLines = listOf(
        "Wake up! The day already started without you!",
        "Hey sleepy head! Get out of bed right now!",
        "This is not a snooze. This is a full assault!",
        "Your pillow is lying to you. Stand up!",
        "Rise and grind! Or at least rise!",
        "Good morning! Bad news: you still have to wake up!",
        "Alarm saying: open Wake Gate or I come back louder!",
        "Get up! Coffee will not walk to the bed!",
        "You are not a burrito. Unwrap yourself and move!",
        "DayStart check: are you vertical yet? No? Then wake up!",
        "Stop negotiating with the mattress. Get up!",
        "Wake up champion! Victory starts with leaving the blanket!",
        "If you can hear this, you can open Wake Gate!",
        "Heavy sleeper detected. Deploying maximum annoyance!",
        "Up! Up! Up! The floor misses your feet!"
    )

    val chaseLines = listOf(
        "Chase mode! You left Wake Gate. I am coming back!",
        "Nice try. Finish Wake Gate or I keep ringing!",
        "You cannot ghost an alarm. Open Wake Gate!",
        "Chase alarm! Proof of being awake, please!",
        "Still horizontal? Chase says absolutely not!"
    )

    val relapseLines = listOf(
        "Relapse Guard! Are you still awake?",
        "Tap I am awake, or we start all over!",
        "Quick check: still up, or back to bed betrayal?",
        "Relapse window! Prove it before I restart Chase!",
        "Do not flop. Tap I am awake right now!"
    )

    fun randomWake(seed: Int = kotlin.random.Random.nextInt()): String =
        wakeLines[seed.mod(wakeLines.size).let { if (it < 0) it + wakeLines.size else it }]

    fun randomChase(seed: Int = kotlin.random.Random.nextInt()): String =
        chaseLines[seed.mod(chaseLines.size).let { if (it < 0) it + chaseLines.size else it }]

    fun randomRelapse(seed: Int = kotlin.random.Random.nextInt()): String =
        relapseLines[seed.mod(relapseLines.size).let { if (it < 0) it + relapseLines.size else it }]
}
