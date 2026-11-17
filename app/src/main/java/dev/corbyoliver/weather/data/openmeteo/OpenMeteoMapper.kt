package dev.corbyoliver.weather.data.openmeteo

import dev.corbyoliver.weather.data.AppJson
import dev.corbyoliver.weather.data.SourceException
import dev.corbyoliver.weather.domain.AirQualityHour
import dev.corbyoliver.weather.domain.Condition
import dev.corbyoliver.weather.domain.Current
import dev.corbyoliver.weather.domain.Day
import dev.corbyoliver.weather.domain.Forecast
import dev.corbyoliver.weather.domain.HomeLocation
import dev.corbyoliver.weather.domain.Hour
import dev.corbyoliver.weather.domain.QuarterHour
import kotlinx.serialization.SerializationException
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Turns raw Open-Meteo JSON into our domain [Forecast]. Pure: no network,
 * no clock, no Android. A null in the response stays null (unknown is not zero).
 */
object OpenMeteoMapper {

    /** A cached "current" air quality older than this is not shown as current. */
    val AQ_CURRENT_MAX_AGE: Duration = Duration.ofHours(2)

    /**
     * @param forecastJson body of the /v1/forecast response.
     * @param airQualityJson body of the air quality response, or null if it was not available.
     *   A broken air quality body is ignored; it never hides the forecast.
     * @param fetchedAt when [forecastJson] was downloaded.
     * @throws SourceException if the forecast body cannot be read.
     */
    fun map(forecastJson: String, airQualityJson: String?, fetchedAt: Instant, zone: ZoneId = HomeLocation.ZONE): Forecast {
        val fc = try {
            AppJson.decodeFromString(OmForecastResponse.serializer(), forecastJson)
        } catch (e: SerializationException) {
            throw SourceException("Open-Meteo sent data the app could not read.", cause = e)
        } catch (e: IllegalArgumentException) {
            throw SourceException("Open-Meteo sent data the app could not read.", cause = e)
        }
        val aq = airQualityJson?.let {
            try {
                AppJson.decodeFromString(OmAirQualityResponse.serializer(), it)
            } catch (e: SerializationException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        }
        return map(fc, aq, fetchedAt, zone)
    }

    fun map(fc: OmForecastResponse, aq: OmAirQualityResponse?, fetchedAt: Instant, zone: ZoneId = HomeLocation.ZONE): Forecast {
        val c = fc.current ?: throw SourceException("Open-Meteo sent no current conditions.")
        val days = mapDays(fc.daily, zone)
        val currentTime = Instant.ofEpochSecond(c.time)
        val current = Current(
            time = currentTime,
            temperatureF = c.temperature,
            feelsLikeF = c.apparentTemperature,
            condition = conditionFor(c.weatherCode),
            isDay = c.isDay?.let { it == 1 } ?: isDayFromSun(currentTime, days, zone),
            cloudCoverPct = c.cloudCover,
            humidityPct = c.humidity,
            windMph = c.windSpeed,
            gustMph = c.windGusts,
            windFromDeg = c.windDirection,
        )

        val h = fc.hourly
        val hours = h?.time?.mapIndexed { i, sec ->
            val t = Instant.ofEpochSecond(sec)
            Hour(
                time = t,
                temperatureF = h.temperature.getOrNull(i),
                condition = conditionFor(h.weatherCode.getOrNull(i)),
                isDay = h.isDay.getOrNull(i)?.let { it == 1 } ?: isDayFromSun(t, days, zone),
                humidityPct = h.humidity.getOrNull(i),
                cloudCoverPct = h.cloudCover.getOrNull(i),
                windMph = h.windSpeed.getOrNull(i),
                windFromDeg = h.windDirection.getOrNull(i),
                precipChancePrevHourPct = h.precipitationProbability.getOrNull(i),
                precipPrevHourIn = h.precipitation.getOrNull(i),
                gustPrevHourMph = h.windGusts.getOrNull(i),
            )
        }.orEmpty()

        val m = fc.minutely15
        val quarters = m?.time?.mapIndexed { i, sec ->
            QuarterHour(Instant.ofEpochSecond(sec), m.precipitation.getOrNull(i))
        }.orEmpty()

        val aqHours = aq?.hourly?.let { a ->
            a.time.mapIndexed { i, sec -> AirQualityHour(Instant.ofEpochSecond(sec), a.usAqi.getOrNull(i)) }
        }.orEmpty()
        // A "current" AQI from an older cached response is not current any more.
        val currentAqi = aq?.current?.takeIf {
            Duration.between(Instant.ofEpochSecond(it.time), fetchedAt).abs() <= AQ_CURRENT_MAX_AGE
        }?.usAqi

        return Forecast(
            fetchedAt = fetchedAt,
            current = current,
            hours = hours,
            days = days,
            quarterHours = quarters,
            currentUsAqi = currentAqi,
            airQuality = aqHours,
        )
    }

    /**
     * Daily times are local midnight (the request sets `timezone`), sent as
     * unixtime. Convert in the home zone, never in UTC, or every day shifts back by one.
     */
    private fun mapDays(d: OmDaily?, zone: ZoneId): List<Day> = d?.time?.mapIndexed { i, sec ->
        Day(
            date = Instant.ofEpochSecond(sec).atZone(zone).toLocalDate(),
            condition = conditionFor(d.weatherCode.getOrNull(i)),
            highF = d.tempMax.getOrNull(i),
            lowF = d.tempMin.getOrNull(i),
            precipChanceMaxPct = d.precipProbMax.getOrNull(i),
            precipSumIn = d.precipSum.getOrNull(i),
            windMaxMph = d.windMax.getOrNull(i),
            gustMaxMph = d.gustMax.getOrNull(i),
            windDominantFromDeg = d.windDirDominant.getOrNull(i),
            sunrise = d.sunrise.getOrNull(i)?.let(Instant::ofEpochSecond),
            sunset = d.sunset.getOrNull(i)?.let(Instant::ofEpochSecond),
        )
    }.orEmpty()

    /** Used only when `is_day` is missing: daylight is sunrise to sunset, or 6 AM to 6 PM if those are unknown. */
    internal fun isDayFromSun(t: Instant, days: List<Day>, zone: ZoneId): Boolean {
        val local = t.atZone(zone)
        val day = days.firstOrNull { it.date == local.toLocalDate() }
        val rise = day?.sunrise
        val set = day?.sunset
        return if (rise != null && set != null) !t.isBefore(rise) && t.isBefore(set) else local.hour in 6..17
    }

    /** WMO weather interpretation codes, as listed in the Open-Meteo docs. */
    fun conditionFor(code: Int?): Condition = when (code) {
        0 -> Condition.CLEAR
        1 -> Condition.MOSTLY_CLEAR
        2 -> Condition.PARTLY_CLOUDY
        3 -> Condition.OVERCAST
        45, 48 -> Condition.FOG
        51, 53, 55 -> Condition.DRIZZLE
        56, 57, 66, 67 -> Condition.FREEZING_RAIN
        61, 63, 80, 81 -> Condition.RAIN
        65, 82 -> Condition.HEAVY_RAIN
        71, 73, 75, 77, 85, 86 -> Condition.SNOW
        95, 96, 99 -> Condition.THUNDERSTORM
        else -> Condition.UNKNOWN
    }
}
