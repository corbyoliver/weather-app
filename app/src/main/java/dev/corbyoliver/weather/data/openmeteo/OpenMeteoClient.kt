package dev.corbyoliver.weather.data.openmeteo

import dev.corbyoliver.weather.data.getText
import dev.corbyoliver.weather.domain.HomeLocation
import io.ktor.client.HttpClient

/** Where the raw Open-Meteo JSON comes from. The repository only sees this. */
interface OpenMeteoSource {
    /** Throws [dev.corbyoliver.weather.data.SourceException] on any failure. */
    suspend fun forecastJson(): String

    /** Throws [dev.corbyoliver.weather.data.SourceException] on any failure. */
    suspend fun airQualityJson(): String
}

/**
 * Open-Meteo requests, checked against https://open-meteo.com/en/docs and
 * https://open-meteo.com/en/docs/air-quality-api on 2026-11-17.
 *
 * No `past_hours`: hourly data then starts at 00:00 today (local time), which
 * is enough for today's rain bars and keeps the response small.
 */
object OpenMeteoRequests {
    const val FORECAST_BASE = "https://api.open-meteo.com/v1/forecast"
    const val AIR_QUALITY_BASE = "https://air-quality-api.open-meteo.com/v1/air-quality"

    const val FORECAST_DAYS = 7
    /** 24 steps of 15 minutes = the next 6 hours. */
    const val MINUTELY_15_STEPS = 24
    /** The air quality forecast covers 5 days at most. */
    const val AIR_QUALITY_DAYS = 5

    val CURRENT = listOf(
        "temperature_2m", "apparent_temperature", "relative_humidity_2m", "cloud_cover", "weather_code",
        "is_day", "wind_speed_10m", "wind_direction_10m", "wind_gusts_10m", "precipitation",
    )
    val HOURLY = listOf(
        "temperature_2m", "relative_humidity_2m", "cloud_cover", "weather_code", "is_day",
        "wind_speed_10m", "wind_direction_10m", "wind_gusts_10m", "precipitation", "precipitation_probability",
    )
    val DAILY = listOf(
        "weather_code", "temperature_2m_max", "temperature_2m_min", "precipitation_sum",
        "precipitation_probability_max", "wind_speed_10m_max", "wind_gusts_10m_max",
        "wind_direction_10m_dominant", "sunrise", "sunset",
    )

    private fun common(): List<Pair<String, String>> = listOf(
        "latitude" to HomeLocation.LATITUDE.toString(),
        "longitude" to HomeLocation.LONGITUDE.toString(),
        "timezone" to HomeLocation.ZONE.id,
        "timeformat" to "unixtime",
    )

    fun forecastUrl(): String = url(
        FORECAST_BASE,
        common() + listOf(
            "temperature_unit" to "fahrenheit",
            "wind_speed_unit" to "mph",
            "precipitation_unit" to "inch",
            "forecast_days" to FORECAST_DAYS.toString(),
            "forecast_minutely_15" to MINUTELY_15_STEPS.toString(),
            "current" to CURRENT.joinToString(","),
            "minutely_15" to "precipitation",
            "hourly" to HOURLY.joinToString(","),
            "daily" to DAILY.joinToString(","),
        ),
    )

    fun airQualityUrl(): String = url(
        AIR_QUALITY_BASE,
        common() + listOf(
            "forecast_days" to AIR_QUALITY_DAYS.toString(),
            "current" to "us_aqi",
            "hourly" to "us_aqi",
        ),
    )

    private fun url(base: String, params: List<Pair<String, String>>): String =
        base + "?" + params.joinToString("&") { (k, v) -> "$k=${encode(v)}" }

    // Values here are numbers, field names, commas and one time zone id.
    // Only "/" needs escaping; commas are left readable (Open-Meteo accepts both).
    private fun encode(v: String) = v.replace("/", "%2F")
}

class OpenMeteoClient(private val http: HttpClient) : OpenMeteoSource {
    override suspend fun forecastJson(): String = http.getText(OpenMeteoRequests.forecastUrl(), "Open-Meteo")
    override suspend fun airQualityJson(): String = http.getText(OpenMeteoRequests.airQualityUrl(), "Open-Meteo air quality")
}
