package dev.corbyoliver.weather.sample

import dev.corbyoliver.weather.domain.AirQualityHour
import dev.corbyoliver.weather.domain.Condition
import dev.corbyoliver.weather.domain.Current
import dev.corbyoliver.weather.domain.Day
import dev.corbyoliver.weather.domain.Forecast
import dev.corbyoliver.weather.domain.HomeLocation
import dev.corbyoliver.weather.domain.Hour
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.sin

/**
 * Made-up weather for previews and for checking each frog state on a device.
 * Never shown when real data is available.
 */
enum class SampleScenario { RAIN, WINDY, COLD_SNAP, CLEAR_NIGHT, CLEAR_DAY, CLOUDY }

object SampleData {
    fun forecast(now: Instant, scenario: SampleScenario = SampleScenario.RAIN): Forecast {
        val zone = HomeLocation.ZONE
        val startHour = now.truncatedTo(ChronoUnit.HOURS)
        val today = now.atZone(zone).toLocalDate()

        val (temp, gust, cloud, cond) = when (scenario) {
            SampleScenario.RAIN -> Quad(47.0, 14.0, 95, Condition.RAIN)
            SampleScenario.WINDY -> Quad(52.0, 34.0, 70, Condition.PARTLY_CLOUDY)
            SampleScenario.COLD_SNAP -> Quad(31.0, 8.0, 90, Condition.SNOW)
            SampleScenario.CLEAR_NIGHT -> Quad(39.0, 5.0, 10, Condition.CLEAR)
            SampleScenario.CLEAR_DAY -> Quad(68.0, 6.0, 5, Condition.CLEAR)
            SampleScenario.CLOUDY -> Quad(55.0, 10.0, 80, Condition.OVERCAST)
        }
        val isDay = scenario != SampleScenario.CLEAR_NIGHT
        val rainy = scenario == SampleScenario.RAIN

        val hours = (-24 until 24 * 8).map { i ->
            val t = startHour.plus(Duration.ofHours(i.toLong()))
            val hourOfDay = t.atZone(zone).hour
            val chance = if (rainy) (85 - kotlin.math.abs(i - 2) * 9).coerceIn(5, 90) else (10 + (i * 7) % 25)
            Hour(
                time = t,
                temperatureF = temp + 6 * sin((hourOfDay - 9) / 24.0 * 2 * PI),
                condition = if (i in 0..3) cond else if (chance >= 50) Condition.RAIN else Condition.PARTLY_CLOUDY,
                isDay = if (i == 0) isDay else hourOfDay in 7..16,
                humidityPct = 70 + (i * 5) % 25,
                cloudCoverPct = cloud,
                windMph = 6.0 + (i * 3) % 12,
                windFromDeg = (200 + i * 10) % 360,
                precipChancePrevHourPct = if (i == 30) null else chance,
                precipPrevHourIn = if (chance > 50) 0.04 else 0.0,
                gustPrevHourMph = gust,
            )
        }
        val days = (0 until 7).map { d ->
            val date = today.plusDays(d.toLong())
            Day(
                date = date,
                condition = listOf(cond, Condition.RAIN, Condition.PARTLY_CLOUDY, Condition.OVERCAST, Condition.CLEAR, Condition.DRIZZLE, Condition.PARTLY_CLOUDY)[d],
                highF = temp + 5 + d,
                lowF = temp - 6 + d / 2.0,
                precipChanceMaxPct = listOf(90, 70, 30, 45, 5, 55, 20)[d],
                precipSumIn = listOf(0.42, 0.21, 0.02, 0.08, 0.0, 0.12, 0.0)[d],
                windMaxMph = 14.0 + d,
                gustMaxMph = gust + d,
                windDominantFromDeg = 225,
                sunrise = null,
                sunset = null,
            )
        }
        return Forecast(
            fetchedAt = now.minus(Duration.ofMinutes(12)),
            current = Current(
                time = now,
                temperatureF = temp,
                feelsLikeF = temp - 4,
                condition = cond,
                isDay = isDay,
                cloudCoverPct = cloud,
                humidityPct = 86,
                windMph = 9.0,
                gustMph = gust,
                windFromDeg = 225,
            ),
            hours = hours,
            days = days,
            currentUsAqi = 22,
            airQuality = hours.take(24 * 4).map { AirQualityHour(it.time, 20 + (it.time.epochSecond / 3600 % 30).toInt()) },
        )
    }

    private data class Quad(val temp: Double, val gust: Double, val cloud: Int, val condition: Condition)
}
