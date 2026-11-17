package dev.corbyoliver.weather.data.openmeteo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Open-Meteo response shapes, checked against https://open-meteo.com/en/docs
 * on 2026-11-17 and a live response captured the same day
 * (test fixture openmeteo-forecast-captured-2026-11-17.json).
 * Requested with timeformat=unixtime, °F, mph, inches.
 * Any array element can be null: that means unknown, never zero.
 */

@Serializable
data class OmForecastResponse(
    val timezone: String? = null,
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int? = null,
    val current: OmCurrent? = null,
    val hourly: OmHourly? = null,
    val daily: OmDaily? = null,
    @SerialName("minutely_15") val minutely15: OmMinutely15? = null,
)

@Serializable
data class OmCurrent(
    val time: Long,
    val interval: Int? = null,
    @SerialName("temperature_2m") val temperature: Double? = null,
    @SerialName("apparent_temperature") val apparentTemperature: Double? = null,
    @SerialName("relative_humidity_2m") val humidity: Int? = null,
    @SerialName("cloud_cover") val cloudCover: Int? = null,
    @SerialName("weather_code") val weatherCode: Int? = null,
    @SerialName("is_day") val isDay: Int? = null,
    @SerialName("wind_speed_10m") val windSpeed: Double? = null,
    @SerialName("wind_direction_10m") val windDirection: Int? = null,
    @SerialName("wind_gusts_10m") val windGusts: Double? = null,
    val precipitation: Double? = null,
)

/** Instant values at each time, except precipitation, its probability and gusts, which cover the PRECEDING hour. */
@Serializable
data class OmHourly(
    val time: List<Long> = emptyList(),
    @SerialName("temperature_2m") val temperature: List<Double?> = emptyList(),
    @SerialName("relative_humidity_2m") val humidity: List<Int?> = emptyList(),
    @SerialName("cloud_cover") val cloudCover: List<Int?> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
    @SerialName("is_day") val isDay: List<Int?> = emptyList(),
    @SerialName("wind_speed_10m") val windSpeed: List<Double?> = emptyList(),
    @SerialName("wind_direction_10m") val windDirection: List<Int?> = emptyList(),
    @SerialName("wind_gusts_10m") val windGusts: List<Double?> = emptyList(),
    val precipitation: List<Double?> = emptyList(),
    @SerialName("precipitation_probability") val precipitationProbability: List<Int?> = emptyList(),
)

@Serializable
data class OmDaily(
    val time: List<Long> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
    @SerialName("temperature_2m_max") val tempMax: List<Double?> = emptyList(),
    @SerialName("temperature_2m_min") val tempMin: List<Double?> = emptyList(),
    @SerialName("precipitation_sum") val precipSum: List<Double?> = emptyList(),
    @SerialName("precipitation_probability_max") val precipProbMax: List<Int?> = emptyList(),
    @SerialName("wind_speed_10m_max") val windMax: List<Double?> = emptyList(),
    @SerialName("wind_gusts_10m_max") val gustMax: List<Double?> = emptyList(),
    @SerialName("wind_direction_10m_dominant") val windDirDominant: List<Int?> = emptyList(),
    val sunrise: List<Long?> = emptyList(),
    val sunset: List<Long?> = emptyList(),
)

/** precipitation covers the PRECEDING 15 minutes. */
@Serializable
data class OmMinutely15(
    val time: List<Long> = emptyList(),
    val precipitation: List<Double?> = emptyList(),
)

@Serializable
data class OmAirQualityResponse(
    val current: OmAqCurrent? = null,
    val hourly: OmAqHourly? = null,
)

@Serializable
data class OmAqCurrent(val time: Long, @SerialName("us_aqi") val usAqi: Int? = null)

@Serializable
data class OmAqHourly(val time: List<Long> = emptyList(), @SerialName("us_aqi") val usAqi: List<Int?> = emptyList())
