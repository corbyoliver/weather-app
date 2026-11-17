package dev.corbyoliver.weather.data.openmeteo

import dev.corbyoliver.weather.data.SourceException
import dev.corbyoliver.weather.domain.Condition
import dev.corbyoliver.weather.domain.HomeLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

fun fixture(name: String): String =
    requireNotNull(OpenMeteoMapperTest::class.java.getResource("/fixtures/$name")) { "missing fixture $name" }.readText()

val FORECAST_FIXTURE by lazy { fixture("openmeteo-forecast-captured-2026-11-17.json") }
val AIR_QUALITY_FIXTURE by lazy { fixture("openmeteo-airquality-captured-2026-11-17.json") }

/** The live responses were captured at 2026-11-17 10:30 AM Pacific. */
val FIXTURE_FETCHED_AT: Instant = Instant.ofEpochSecond(1794940200)

class OpenMeteoMapperTest {
    private val forecast = OpenMeteoMapper.map(FORECAST_FIXTURE, AIR_QUALITY_FIXTURE, FIXTURE_FETCHED_AT)

    @Test fun `current conditions come through unchanged`() {
        val c = forecast.current
        assertEquals(Instant.ofEpochSecond(1794940200), c.time)
        assertEquals(46.8, c.temperatureF!!, 0.0)
        assertEquals(43.0, c.feelsLikeF!!, 0.0)
        assertEquals(89, c.humidityPct)
        assertEquals(87, c.cloudCoverPct)
        assertEquals(Condition.OVERCAST, c.condition)
        assertTrue(c.isDay)
        assertEquals(5.4, c.windMph!!, 0.0)
        assertEquals(11.2, c.gustMph!!, 0.0)
        assertEquals(167, c.windFromDeg)
        assertEquals(FIXTURE_FETCHED_AT, forecast.fetchedAt)
    }

    @Test fun `hourly nulls near the end stay null and are never turned into zero`() {
        assertEquals(387, forecast.hours.size)
        assertEquals(43.4, forecast.hours[0].temperatureF!!, 0.0)
        assertEquals(1, forecast.hours[0].precipChancePrevHourPct)
        assertEquals((382..386).toList(), forecast.hours.indices.filter { forecast.hours[it].temperatureF == null })
        assertEquals((376..386).toList(), forecast.hours.indices.filter { forecast.hours[it].gustPrevHourMph == null })
        val last = forecast.hours.last()
        assertNull(last.windMph)
        assertNull(last.humidityPct)
        assertNull(last.precipPrevHourIn)
        assertEquals(Condition.UNKNOWN, last.condition)
    }

    @Test fun `hours are one hour apart`() {
        forecast.hours.zipWithNext().forEach { (a, b) -> assertEquals(Duration.ofHours(1), Duration.between(a.time, b.time)) }
    }

    @Test fun `daily unixtime becomes the local date in Columbia City, not the UTC date`() {
        assertEquals(7, forecast.days.size)
        assertEquals(LocalDate.of(2026, 11, 17), forecast.days[0].date)
        assertEquals(LocalDate.of(2026, 11, 23), forecast.days[6].date)
        assertEquals(48.8, forecast.days[0].highF!!, 0.0)
        assertEquals(Instant.ofEpochSecond(1794928468), forecast.days[0].sunrise)
    }

    @Test fun `fifteen minute steps are mapped`() {
        assertEquals(24, forecast.quarterHours.size)
    }

    @Test fun `air quality keeps its nulls and a fresh current value`() {
        assertEquals(54, forecast.currentUsAqi)
        assertEquals(120, forecast.airQuality.size)
        assertEquals((114..119).toList(), forecast.airQuality.indices.filter { forecast.airQuality[it].usAqi == null })
    }

    @Test fun `a current air quality value from an old cached copy is not shown as current`() {
        val later = OpenMeteoMapper.map(FORECAST_FIXTURE, AIR_QUALITY_FIXTURE, FIXTURE_FETCHED_AT.plus(Duration.ofHours(3)))
        assertNull(later.currentUsAqi)
        assertEquals(120, later.airQuality.size)
    }

    @Test fun `missing or broken air quality does not hide the forecast`() {
        val none = OpenMeteoMapper.map(FORECAST_FIXTURE, null, FIXTURE_FETCHED_AT)
        val broken = OpenMeteoMapper.map(FORECAST_FIXTURE, "{not json", FIXTURE_FETCHED_AT)
        for (f in listOf(none, broken)) {
            assertNull(f.currentUsAqi)
            assertTrue(f.airQuality.isEmpty())
            assertEquals(387, f.hours.size)
        }
    }

    @Test fun `a forecast body the app cannot read is a SourceException`() {
        assertThrows(SourceException::class.java) { OpenMeteoMapper.map("<html>busy</html>", null, FIXTURE_FETCHED_AT) }
        assertThrows(SourceException::class.java) { OpenMeteoMapper.map("""{"current":{"time":"soon"}}""", null, FIXTURE_FETCHED_AT) }
    }

    @Test fun `a forecast without current conditions is a SourceException`() {
        assertThrows(SourceException::class.java) { OpenMeteoMapper.map("""{"hourly":{"time":[]}}""", null, FIXTURE_FETCHED_AT) }
    }

    @Test fun `when is_day is missing, daylight comes from sunrise and sunset`() {
        val midnight = ZonedDateTime.of(2026, 11, 17, 0, 0, 0, 0, HomeLocation.ZONE).toEpochSecond()
        val sunrise = midnight + 7 * 3600 + 20 * 60 // 7:20 AM
        val sunset = midnight + 16 * 3600 + 40 * 60 // 4:40 PM
        val json = """
            {"current":{"time":${midnight + 12 * 3600},"weather_code":0},
             "hourly":{"time":[${midnight + 7 * 3600},${midnight + 8 * 3600},${midnight + 17 * 3600}],"weather_code":[0,0,0]},
             "daily":{"time":[$midnight],"sunrise":[$sunrise],"sunset":[$sunset]}}
        """.trimIndent()
        val f = OpenMeteoMapper.map(json, null, FIXTURE_FETCHED_AT)
        assertTrue(f.current.isDay)
        assertEquals(listOf(false, true, false), f.hours.map { it.isDay })
    }

    @Test fun `short arrays give null, not a crash`() {
        val json = """{"current":{"time":1},"hourly":{"time":[1,3601],"temperature_2m":[50.0]}}"""
        val f = OpenMeteoMapper.map(json, null, FIXTURE_FETCHED_AT)
        assertEquals(50.0, f.hours[0].temperatureF!!, 0.0)
        assertNull(f.hours[1].temperatureF)
        assertNull(f.current.temperatureF)
        assertFalse(f.hours.isEmpty())
    }

    @Test fun `WMO codes map to conditions`() {
        val expected = mapOf(
            0 to Condition.CLEAR, 1 to Condition.MOSTLY_CLEAR, 2 to Condition.PARTLY_CLOUDY, 3 to Condition.OVERCAST,
            45 to Condition.FOG, 48 to Condition.FOG,
            51 to Condition.DRIZZLE, 53 to Condition.DRIZZLE, 55 to Condition.DRIZZLE,
            56 to Condition.FREEZING_RAIN, 57 to Condition.FREEZING_RAIN, 66 to Condition.FREEZING_RAIN, 67 to Condition.FREEZING_RAIN,
            61 to Condition.RAIN, 63 to Condition.RAIN, 80 to Condition.RAIN, 81 to Condition.RAIN,
            65 to Condition.HEAVY_RAIN, 82 to Condition.HEAVY_RAIN,
            71 to Condition.SNOW, 73 to Condition.SNOW, 75 to Condition.SNOW, 77 to Condition.SNOW, 85 to Condition.SNOW, 86 to Condition.SNOW,
            95 to Condition.THUNDERSTORM, 96 to Condition.THUNDERSTORM, 99 to Condition.THUNDERSTORM,
        )
        expected.forEach { (code, cond) -> assertEquals("code $code", cond, OpenMeteoMapper.conditionFor(code)) }
        assertEquals(Condition.UNKNOWN, OpenMeteoMapper.conditionFor(null))
        assertEquals(Condition.UNKNOWN, OpenMeteoMapper.conditionFor(4))
    }
}
