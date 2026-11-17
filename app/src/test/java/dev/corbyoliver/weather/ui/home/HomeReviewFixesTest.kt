package dev.corbyoliver.weather.ui.home

import dev.corbyoliver.weather.data.openmeteo.AIR_QUALITY_FIXTURE
import dev.corbyoliver.weather.data.openmeteo.FIXTURE_FETCHED_AT
import dev.corbyoliver.weather.data.openmeteo.FORECAST_FIXTURE
import dev.corbyoliver.weather.data.openmeteo.OpenMeteoMapper
import dev.corbyoliver.weather.domain.HomeLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Tests for the fixes from the step-1 code review (issue #3). */
class HomeReviewFixesTest {
    private val forecast = OpenMeteoMapper.map(FORECAST_FIXTURE, AIR_QUALITY_FIXTURE, FIXTURE_FETCHED_AT)
    private val tueMorning = FIXTURE_FETCHED_AT.plus(Duration.ofMinutes(5)) // Tue 2026-11-17 10:35 AM PST

    @Test fun `the picked day is kept by date across midnight`() {
        val thu = LocalDate.of(2026, 11, 19)
        val before = presentHome(HomeInputs(forecast, tueMorning, selectedDate = thu))
        assertEquals("Thu", before.days[before.selectedDay].label)
        val afterMidnight = Instant.parse("2026-11-18T08:05:00Z") // Wed 12:05 AM: Tuesday drops off the list
        val after = presentHome(HomeInputs(forecast, afterMidnight, selectedDate = thu))
        assertEquals("Thu", after.days[after.selectedDay].label)
        assertEquals(thu, after.days[after.selectedDay].date)
    }

    @Test fun `a picked day that has passed falls back to today`() {
        val ui = presentHome(HomeInputs(forecast, Instant.parse("2026-11-18T18:00:00Z"), selectedDate = LocalDate.of(2026, 11, 17)))
        assertEquals(0, ui.selectedDay)
        assertEquals("Today", ui.days[0].label)
    }

    @Test fun `every day has one rain bar per hour, the last day included`() {
        for (i in 0 until 7) {
            val date = LocalDate.of(2026, 11, 17).plusDays(i.toLong())
            val ui = presentHome(HomeInputs(forecast, tueMorning, selectedDate = date))
            assertEquals("day $i", 24, ui.details!!.precipBars.size)
            assertEquals("12a", ui.details!!.precipBars.first().label)
            assertEquals("11p", ui.details!!.precipBars.last().label)
        }
    }

    @Test fun `hours before the data starts are unknown, not dry`() {
        // The captured response starts at 7 AM, so midnight to 6 AM today are unknown.
        val bars = presentHome(HomeInputs(forecast, tueMorning)).details!!.precipBars
        assertNull(bars[0].chancePct)
        assertTrue(bars[10].chancePct != null)
    }

    @Test fun `a dry day says no rain instead of a peak of zero`() {
        val dry = forecast.copy(hours = forecast.hours.map { it.copy(precipChancePrevHourPct = 0) })
        val ui = presentHome(HomeInputs(dry, tueMorning))
        assertEquals("No rain expected: 0% chance every hour", ui.details!!.precipDescription)
    }

    @Test fun `updated wording is shared by the app and the widgets`() {
        val z = HomeLocation.ZONE
        assertEquals("Updated 10:30 AM", Format.updated(FIXTURE_FETCHED_AT, tueMorning, z))
        assertEquals("Updated Tue 10:30 AM", Format.updated(FIXTURE_FETCHED_AT, tueMorning.plus(Duration.ofDays(1)), z))
    }
}
