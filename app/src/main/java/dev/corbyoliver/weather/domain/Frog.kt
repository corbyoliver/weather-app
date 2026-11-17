package dev.corbyoliver.weather.domain

/** The frog's six looks, in priority order (first match wins). */
enum class FrogState { RAIN, WINDY, COLD_SNAP, CLEAR_NIGHT, CLEAR_DAY, CLOUDY }

/**
 * Every frog threshold in one place. These are starting suggestions from the
 * spec; change them here and nowhere else.
 */
data class FrogThresholds(
    val rainChancePct: Int = 40,
    val gustMph: Double = 25.0,
    val coldTempF: Double = 34.0,
    val clearNightMaxCloudPct: Int = 60,
    val clearDayMaxCloudPct: Int = 30,
)

data class FrogInputs(
    /** Highest rain chance for the next hour; null if unknown. */
    val nextHourRainChancePct: Int?,
    val gustMph: Double?,
    val temperatureF: Double?,
    val isDay: Boolean,
    val cloudCoverPct: Int?,
)

/**
 * Picks the frog's look. An unknown (null) input never triggers its state:
 * the frog does not claim rain, wind, cold or clear skies it cannot see.
 */
fun selectFrogState(inputs: FrogInputs, t: FrogThresholds = FrogThresholds()): FrogState {
    val rain = inputs.nextHourRainChancePct
    if (rain != null && rain >= t.rainChancePct) return FrogState.RAIN
    val gust = inputs.gustMph
    if (gust != null && gust >= t.gustMph) return FrogState.WINDY
    val temp = inputs.temperatureF
    if (temp != null && temp <= t.coldTempF) return FrogState.COLD_SNAP
    val cloud = inputs.cloudCoverPct
    if (cloud != null) {
        if (!inputs.isDay && cloud < t.clearNightMaxCloudPct) return FrogState.CLEAR_NIGHT
        if (inputs.isDay && cloud < t.clearDayMaxCloudPct) return FrogState.CLEAR_DAY
    }
    return FrogState.CLOUDY
}
