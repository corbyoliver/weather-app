package dev.corbyoliver.weather.domain

import java.time.Instant
import java.time.LocalDate

/**
 * Our own weather models. API clients map into these, so a provider can be
 * swapped without touching the screens. Units: °F, mph, inches.
 *
 * Unknown is not zero: a value the provider did not send stays null.
 */

enum class Condition {
    CLEAR, MOSTLY_CLEAR, PARTLY_CLOUDY, OVERCAST, FOG,
    DRIZZLE, RAIN, HEAVY_RAIN, FREEZING_RAIN, SNOW, THUNDERSTORM, UNKNOWN,
}

data class Current(
    val time: Instant,
    val temperatureF: Double?,
    val feelsLikeF: Double?,
    val condition: Condition,
    val isDay: Boolean,
    val cloudCoverPct: Int?,
    val humidityPct: Int?,
    val windMph: Double?,
    val gustMph: Double?,
    /** Direction the wind comes FROM, degrees clockwise from north. */
    val windFromDeg: Int?,
)

/**
 * One hourly forecast step at [time].
 *
 * Instant values (temperature, humidity, wind speed and direction, cloud
 * cover) are valid AT [time]. Values named "prevHour" describe the hour that
 * ENDS at [time], that is [time - 1h, time). Getting this wrong shifts every
 * rain start and stop by an hour.
 */
data class Hour(
    val time: Instant,
    val temperatureF: Double?,
    val condition: Condition,
    val isDay: Boolean,
    val humidityPct: Int?,
    val cloudCoverPct: Int?,
    val windMph: Double?,
    val windFromDeg: Int?,
    val precipChancePrevHourPct: Int?,
    val precipPrevHourIn: Double?,
    val gustPrevHourMph: Double?,
)

data class Day(
    val date: LocalDate,
    val condition: Condition,
    val highF: Double?,
    val lowF: Double?,
    val precipChanceMaxPct: Int?,
    val precipSumIn: Double?,
    val windMaxMph: Double?,
    val gustMaxMph: Double?,
    val windDominantFromDeg: Int?,
    val sunrise: Instant?,
    val sunset: Instant?,
)

/** US AQI at [time] (an instant value). */
data class AirQualityHour(val time: Instant, val usAqi: Int?)

/**
 * A 15-minute step. [precipPrevQuarterIn] covers [time - 15 min, time).
 * Used as the fallback for rain timing when Pirate Weather is unavailable.
 */
data class QuarterHour(val time: Instant, val precipPrevQuarterIn: Double?)

data class Forecast(
    /** When this data was downloaded. Drives the "Updated" line. */
    val fetchedAt: Instant,
    val current: Current,
    val hours: List<Hour>,
    val days: List<Day>,
    val quarterHours: List<QuarterHour> = emptyList(),
    val currentUsAqi: Int? = null,
    val airQuality: List<AirQualityHour> = emptyList(),
)
