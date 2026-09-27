package com.loudwake.app

import kotlin.random.Random

data class MathChallenge(
    val prompt: String,
    val answer: Int
)

object MathChallengeFactory {
    fun next(random: Random = Random.Default): MathChallenge {
        return when (random.nextInt(3)) {
            0 -> {
                val a = random.nextInt(12, 49)
                val b = random.nextInt(12, 49)
                MathChallenge("$a + $b", a + b)
            }
            1 -> {
                val a = random.nextInt(20, 90)
                val b = random.nextInt(8, 35)
                MathChallenge("$a - $b", a - b)
            }
            else -> {
                val a = random.nextInt(6, 14)
                val b = random.nextInt(6, 14)
                MathChallenge("$a × $b", a * b)
            }
        }
    }
}
