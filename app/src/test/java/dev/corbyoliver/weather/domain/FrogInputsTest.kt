package dev.corbyoliver.weather.domain

import dev.corbyoliver.weather.data.openmeteo.AIR_QUALITY_FIXTURE
import dev.corbyoliver.weather.data.openmeteo.FIXTURE_FETCHED_AT
import dev.corbyoliver.weather.data.openmeteo.FORECAST_FIXTURE
import dev.corbyoliver.weather.data.openmeteo.OpenMeteoMapper
import dev.corbyoliver.weather.data.pirate.PIRATE_LIVE_FIXTURE
import dev.corbyoliver.weather.data.pirate.PirateMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class FrogInputsTest {
    private val now = Instant.parse("2026-11-18T00:00:00Z")

    private fun hour(offsetH: Long, chance: Int?, gust: Double? = 10.0) =
        Hour(now.plus(Duration.ofHours(offsetH)), 50.0, Condition.OVERCAST, false, 80, 90, 5.0, 180, chance, 0.0, gust)

    private fun minutes(source: NowcastSource, rate: Double, prob: Double?) = Nowcast(
        source,
        (0 until 60).map { RainMinute(now.plus(Duration.ofMinutes(it.toLong())), rate, prob, null) },
    )

    // The step at now+1h covers the next hour; now+2h covers the hour after it, so it is not counted.
    private val hours = listOf(hour(1, 20), hour(2, 30), hour(3, 90))

    @Test fun `hourly only`() = assertEquals(20, nextHourRainChanceAll(hours, null, now))

    @Test fun `Pirate probability counts when higher`() {
        assertEquals(60, nextHourRainChanceAll(hours, minutes(NowcastSource.PIRATE_WEATHER, 0.05, 0.6), now))
        assertEquals(20, nextHourRainChanceAll(hours, minutes(NowcastSource.PIRATE_WEATHER, 0.0, 0.05), now))
    }

    @Test fun `rain in 15-minute amounts without a probability counts as certain`() {
        assertEquals(100, nextHourRainChanceAll(hours, minutes(NowcastSource.OPEN_METEO, 0.04, null), now))
        assertEquals(20, nextHourRainChanceAll(hours, minutes(NowcastSource.OPEN_METEO, 0.0, null), now))
    }

    @Test fun `nothing known stays unknown`() = assertNull(nextHourRainChanceAll(emptyList(), null, now))

    @Test fun `minute rain makes the frog hold its umbrella even when the hourly chance is low`() {
        val f = OpenMeteoMapper.map(FORECAST_FIXTURE, AIR_QUALITY_FIXTURE, FIXTURE_FETCHED_AT)
        val t = FIXTURE_FETCHED_AT.plus(Duration.ofMinutes(5))
        val wet = Nowcast(NowcastSource.PIRATE_WEATHER, (0 until 60).map { RainMinute(t.plus(Duration.ofMinutes(it.toLong())), 0.08, 0.85, PrecipKind.RAIN) })
        assertEquals(FrogState.RAIN, selectFrogState(frogInputsFor(f, wet, t)))
    }

    @Test fun `real captured data gives the cloudy frog`() {
        // 2026-11-17 10:30 AM: 46.8°F, cloud 87%, gusts 11.2 mph, daytime, low rain chance.
        val f = OpenMeteoMapper.map(FORECAST_FIXTURE, AIR_QUALITY_FIXTURE, FIXTURE_FETCHED_AT)
        val t = FIXTURE_FETCHED_AT.plus(Duration.ofMinutes(5))
        val inputs = frogInputsFor(f, null, t)
        assertEquals(11.2, inputs.gustMph!!, 0.0)
        assertEquals(87, inputs.cloudCoverPct)
        assertEquals(FrogState.CLOUDY, selectFrogState(inputs))
    }

    @Test fun `the live dry Pirate hour does not make it rain`() {
        val m = PirateMapper.map(PIRATE_LIVE_FIXTURE)
        val t = m.first().time
        val n = RainTiming.chooseNowcast(m, emptyList(), t)
        val chance = nextHourRainChanceAll(emptyList(), n, t)
        assert(chance != null && chance < FrogThresholds().rainChancePct) { "chance was $chance" }
    }

    @Test fun `a missing current gust falls back to the coming hour`() {
        val f = OpenMeteoMapper.map(FORECAST_FIXTURE, null, FIXTURE_FETCHED_AT)
        val noGust = f.copy(current = f.current.copy(gustMph = null))
        val t = FIXTURE_FETCHED_AT.plus(Duration.ofMinutes(5))
        val expected = f.hours.filter { it.time.isAfter(t) && it.time.isBefore(t.plus(Duration.ofHours(2))) }.mapNotNull { it.gustPrevHourMph }.max()
        assertEquals(expected, frogInputsFor(noGust, null, t).gustMph!!, 0.0)
    }
}
