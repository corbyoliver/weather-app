package dev.corbyoliver.weather.domain

import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

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

/** Highest hourly rain chance for any forecast hour that overlaps [now, now + 1h). */
fun nextHourRainChance(hours: List<Hour>, now: Instant): Int? {
    // A "prevHour" value at t covers [t - 1h, t). It overlaps [now, now + 1h)
    // when now < t < now + 2h.
    val end = now.plus(Duration.ofHours(2))
    return hours.filter { it.time.isAfter(now) && it.time.isBefore(end) }
        .mapNotNull { it.precipChancePrevHourPct }
        .maxOrNull()
}

/**
 * Rain chance for the next hour from every source the app has, taking the
 * highest: the hourly forecast, Pirate Weather's minute probabilities, and
 * minute data that shows rain but has no probability (Open-Meteo's
 * 15-minute amounts), which counts as 100%.
 */
fun nextHourRainChanceAll(hours: List<Hour>, nowcast: Nowcast?, now: Instant, wetInPerHr: Double = RainTimingConfig().wetInPerHr): Int? {
    val hourly = nextHourRainChance(hours, now)
    val minutes = nowcast?.minutes.orEmpty()
    val fromProbability = minutes.mapNotNull { it.probability }.maxOrNull()?.let { (it * 100).roundToInt() }
    val wetWithoutProbability = minutes.any { it.probability == null && (it.intensityInPerHr ?: 0.0) >= wetInPerHr }
    val fromMinutes = if (wetWithoutProbability) 100 else fromProbability
    return listOfNotNull(hourly, fromMinutes).maxOrNull()
}

/** The frog's inputs from real data. A missing current gust falls back to the coming hour's gusts. */
fun frogInputsFor(forecast: Forecast, nowcast: Nowcast?, now: Instant): FrogInputs {
    val c = forecast.current
    val nextHourGust = forecast.hours
        .filter { it.time.isAfter(now) && it.time.isBefore(now.plus(Duration.ofHours(2))) }
        .mapNotNull { it.gustPrevHourMph }
        .maxOrNull()
    return FrogInputs(
        nextHourRainChancePct = nextHourRainChanceAll(forecast.hours, nowcast, now),
        gustMph = c.gustMph ?: nextHourGust,
        temperatureF = c.temperatureF,
        isDay = c.isDay,
        cloudCoverPct = c.cloudCoverPct,
    )
}
