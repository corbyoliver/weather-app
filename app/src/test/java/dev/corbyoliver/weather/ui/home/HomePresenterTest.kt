package dev.corbyoliver.weather.ui.home

import dev.corbyoliver.weather.domain.FrogState
import dev.corbyoliver.weather.domain.Hour
import dev.corbyoliver.weather.domain.Condition
import dev.corbyoliver.weather.sample.SampleData
import dev.corbyoliver.weather.sample.SampleScenario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class HomePresenterTest {
    private val now = Instant.parse("2026-11-17T18:20:00Z") // 10:20 AM in Columbia City

    private fun hour(t: Instant, chance: Int?) = Hour(t, 50.0, Condition.RAIN, true, 80, 90, 5.0, 180, chance, 0.0, 10.0)

    @Test fun `next hour rain uses the two hourly steps that overlap the next 60 minutes`() {
        val h = listOf(
            hour(Instant.parse("2026-11-17T18:00:00Z"), 99), // covers 9-10 AM, already past
            hour(Instant.parse("2026-11-17T19:00:00Z"), 20), // covers 10-11 AM
            hour(Instant.parse("2026-11-17T20:00:00Z"), 45), // covers 11 AM-12 PM, overlaps until 11:20
            hour(Instant.parse("2026-11-17T21:00:00Z"), 99), // covers 12-1 PM, outside
        )
        assertEquals(45, nextHourRainChance(h, now))
    }

    @Test fun `next hour rain is null when unknown, not zero`() {
        val h = listOf(hour(Instant.parse("2026-11-17T19:00:00Z"), null))
        assertNull(nextHourRainChance(h, now))
    }

    @Test fun `each sample scenario gives its frog`() {
        for (s in SampleScenario.entries) {
            val ui = presentHome(HomeInputs(SampleData.forecast(now, s), now))
            assertEquals(s.name, FrogState.valueOf(s.name), ui.hero!!.frog)
        }
    }

    @Test fun `hero has six hourly columns starting with Now`() {
        val ui = presentHome(HomeInputs(SampleData.forecast(now), now))
        assertEquals(6, ui.hero!!.hours.size)
        assertEquals("Now", ui.hero!!.hours[0].label)
        assertEquals("11 AM", ui.hero!!.hours[1].label)
    }

    @Test fun `days before today are dropped from an old forecast`() {
        val later = now.plus(Duration.ofDays(2))
        val ui = presentHome(HomeInputs(SampleData.forecast(now), later))
        assertEquals("Today", ui.days.first().label)
        assertEquals(5, ui.days.size)
    }

    @Test fun `an old forecast is marked out of date`() {
        val f = SampleData.forecast(now)
        val fresh = presentHome(HomeInputs(f, now))
        assertFalse(fresh.updated!!.isStale)
        val old = presentHome(HomeInputs(f, now.plus(Duration.ofHours(3))))
        assertTrue(old.updated!!.isStale)
        assertTrue(old.updated!!.text.contains("out of date"))
    }

    @Test fun `rain bars are labelled by the hour they start`() {
        val ui = presentHome(HomeInputs(SampleData.forecast(now), now))
        val bars = ui.details!!.precipBars
        assertEquals(24, bars.size)
        assertEquals("12a", bars.first().label)
        assertEquals("11p", bars.last().label)
    }

    @Test fun `no forecast shows a message instead of a hero`() {
        val ui = presentHome(HomeInputs(null, now, message = "No data yet"))
        assertNull(ui.hero)
        assertEquals("No data yet", ui.message)
    }
}
