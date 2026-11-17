package dev.corbyoliver.weather.ui.home

import dev.corbyoliver.weather.domain.Condition
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

object Format {
    private val hourFmt = DateTimeFormatter.ofPattern("h a", Locale.US)
    private val clockFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    private val dayClockFmt = DateTimeFormatter.ofPattern("EEE h:mm a", Locale.US)
    private val shortHourFmt = DateTimeFormatter.ofPattern("ha", Locale.US)

    fun temp(f: Double?): String = f?.let { "${it.roundToInt()}°" } ?: "--"
    fun mph(v: Double?): String = v?.let { "${it.roundToInt()}" } ?: "--"
    fun pct(v: Int?): String = v?.let { "$it%" } ?: "--"

    fun inches(v: Double?): String = when {
        v == null -> "--"
        v == 0.0 -> "0 in"
        v < 0.01 -> "<0.01 in"
        else -> String.format(Locale.US, "%.2f in", v)
    }

    /** "2 PM" */
    fun hour(t: Instant, zone: ZoneId): String = hourFmt.format(t.atZone(zone))

    /** "2p", "12a": compact labels under the rain bars. */
    fun shortHour(t: Instant, zone: ZoneId): String =
        shortHourFmt.format(t.atZone(zone)).lowercase(Locale.US).removeSuffix("m")

    /** "10:42 AM" */
    fun clock(t: Instant, zone: ZoneId): String = clockFmt.format(t.atZone(zone))

    /** "Mon 10:42 AM" */
    fun dayClock(t: Instant, zone: ZoneId): String = dayClockFmt.format(t.atZone(zone))

    /** "Updated 10:42 AM" today, "Updated Mon 10:42 AM" on another day. */
    fun updated(t: Instant, now: Instant, zone: ZoneId): String =
        if (t.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate()) "Updated ${clock(t, zone)}" else "Updated ${dayClock(t, zone)}"

    fun compass(deg: Int?): String {
        if (deg == null) return ""
        val names = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        return names[(((deg % 360) + 360) % 360 + 22) / 45 % 8]
    }

    fun conditionText(c: Condition, isDay: Boolean): String = when (c) {
        Condition.CLEAR -> if (isDay) "Sunny" else "Clear"
        Condition.MOSTLY_CLEAR -> if (isDay) "Mostly sunny" else "Mostly clear"
        Condition.PARTLY_CLOUDY -> "Partly cloudy"
        Condition.OVERCAST -> "Cloudy"
        Condition.FOG -> "Fog"
        Condition.DRIZZLE -> "Drizzle"
        Condition.RAIN -> "Rain"
        Condition.HEAVY_RAIN -> "Heavy rain"
        Condition.FREEZING_RAIN -> "Freezing rain"
        Condition.SNOW -> "Snow"
        Condition.THUNDERSTORM -> "Thunderstorms"
        Condition.UNKNOWN -> "Conditions unknown"
    }

    fun aqiCategory(aqi: Int): String = when {
        aqi <= 50 -> "Good"
        aqi <= 100 -> "Moderate"
        aqi <= 150 -> "Unhealthy for sensitive groups"
        aqi <= 200 -> "Unhealthy"
        aqi <= 300 -> "Very unhealthy"
        else -> "Hazardous"
    }
}
