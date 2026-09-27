package com.loudwake.app.data

enum class ChallengeKind {
    MATH,
    PHRASE,
    SHAKE,
    STEPS,
    MEMORY
}

enum class WakeMode(
    val title: String,
    val allowsSafeSilence: Boolean,
    val challenges: List<ChallengeKind>
) {
    MATH("Math Challenge", true, listOf(ChallengeKind.MATH)),
    SHAKE("Shake to Wake", true, listOf(ChallengeKind.SHAKE)),
    PHRASE("Type Phrase", true, listOf(ChallengeKind.PHRASE)),
    STEPS("Walk It Off", true, listOf(ChallengeKind.STEPS)),
    MEMORY("Memory Match", true, listOf(ChallengeKind.MEMORY)),
    GAUNTLET(
        "Morning Gauntlet",
        false,
        listOf(
            ChallengeKind.MATH,
            ChallengeKind.PHRASE,
            ChallengeKind.SHAKE,
            ChallengeKind.STEPS
        )
    ),
    STRICT(
        "Strict Wake Gate",
        false,
        listOf(
            ChallengeKind.MATH,
            ChallengeKind.MEMORY
        )
    );

    companion object {
        fun fromName(raw: String?): WakeMode = when (raw) {
            // Removed modes
            "SCHEDULE", "MATH_SCHEDULE", "TOILET" -> MATH
            else -> entries.firstOrNull { it.name == raw } ?: MATH
        }
    }
}

enum class AlarmTone(val title: String, val rawName: String) {
    // Loud classics
    SHOCKWAVE("Shockwave", "shockwave"),
    AIR_HORN("Air Horn", "air_horn"),
    KLAXON("Klaxon", "klaxon"),
    SIREN("Siren", "siren"),
    PULSE_STORM("Pulse Storm", "pulse_storm"),
    BUZZSAW("Buzzsaw", "buzzsaw"),
    RADAR("Radar", "radar"),
    REVEILLE("Reveille", "reveille"),
    // Generated funny
    BOING_WAKE("Boing Wake", "boing_wake"),
    ROOSTER_CHAOS("Rooster Chaos", "rooster_chaos"),
    WAKE_WHISTLE("Wake Whistle", "wake_whistle"),
    COMEDY_HONK("Comedy Honk", "comedy_honk"),
    // Free Mixkit funny / wake SFX (Mixkit License)
    CLOWN_HORN("Clown Horn", "clown_horn"),
    CLOWN_HONKS("Clown Honks", "clown_honks"),
    SAD_TROMBONE("Sad Trombone", "sad_trombone"),
    TROMBONE_WAH("Trombone Wah", "trombone_wah"),
    TOY_WHISTLE("Toy Whistle", "toy_whistle"),
    FALLING_WHISTLE("Falling Whistle", "falling_whistle"),
    CARTOON_BOING("Cartoon Boing", "cartoon_boing"),
    FUNNY_GIGGLE("Funny Giggle", "funny_giggle"),
    CREATURE_LAUGH("Creature Laugh", "creature_laugh"),
    GAME_SLIDE("Game Slide", "game_slide"),
    CLOWN_TOOT("Clown Toot", "clown_toot"),
    FAIL_BUZZER("Fail Buzzer", "fail_buzzer"),
    PARTY_HORN("Party Horn", "party_horn"),
    FUNNY_MELODY("Funny Melody", "funny_melody"),
    ROOSTER_MORNING("Rooster Morning", "rooster_morning"),
    CLASSIC_ALARM("Classic Alarm", "classic_alarm"),
    RETRO_ALARM("Retro Alarm", "retro_alarm"),
    WINNER_ALARM("Winner Alarm", "winner_alarm"),
    // Mixkit loud / annoying (Mixkit Sound Effects Free License)
    FACILITY_ALARM("Facility Alarm", "mixkit_facility_alarm_sound"),
    FACILITY_ALARM_SHORT("Facility Blare", "mixkit_facility_alarm"),
    RETRO_EMERGENCY("Retro Emergency", "mixkit_retro_game_emergency_alarm"),
    DIGITAL_BUZZER("Digital Buzzer", "mixkit_digital_alarm_buzzer"),
    ALERT_ALARM("Alert Alarm", "mixkit_alert_alarm"),
    VINTAGE_WARNING("Vintage Warning", "mixkit_vintage_warning_alarm"),
    EMERGENCY_ALERT("Emergency Alert", "mixkit_emergency_alert_alarm"),
    WARNING_BUZZER("Warning Buzzer", "mixkit_warning_alarm_buzzer"),
    CITY_SIREN("City Alert Siren", "mixkit_city_alert_siren_loop"),
    POLICE_SIREN("Police Siren", "mixkit_police_siren_us"),
    AMBULANCE_SIREN("Ambulance Siren", "mixkit_ambulance_siren_us"),
    AMBULANCE_UK("Ambulance UK", "mixkit_ambulance_siren_uk"),
    AMBULANCE_PASS("Ambulance Pass", "mixkit_ambulance_siren_passing_by"),
    SCI_FI_SIREN("Sci-Fi Ship Siren", "mixkit_sci_fi_ship_siren_alert"),
    BURGLAR_SIREN("Burglar Siren", "mixkit_residential_burglar_siren"),
    FIRE_TRUCK_SIREN("Fire Truck Siren", "mixkit_europe_fire_truck_siren"),
    MANUAL_FIRE_SIREN("Manual Fire Siren", "mixkit_manual_fire_siren"),
    CLASSIC_ALARM_CLOCK("Alarm Clock", "mixkit_classic_alarm_clock"),
    CLASSIC_CAR_HORN("Car Horn", "mixkit_classic_car_horn"),
    CAR_DOUBLE_HORN("Double Car Horn", "mixkit_car_double_horn"),
    WARFARE_HORN("Warfare Horn", "mixkit_warfare_horn");

    companion object {
        fun fromName(raw: String?): AlarmTone =
            entries.firstOrNull { it.name == raw } ?: SIREN
    }
}
