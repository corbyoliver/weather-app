package dev.corbyoliver.weather.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class RainTimingTest {
    private val zone = HomeLocation.ZONE
    private val now = Instant.parse("2026-11-18T00:00:00Z") // 4:00 PM PST

    /** 60 minutes from [now]; [wet] gives the rate (in/hr) for minute i. */
    private fun pirate(kind: PrecipKind = PrecipKind.RAIN, wet: (Int) -> Double) = Nowcast(
        NowcastSource.PIRATE_WEATHER,
        (0 until 60).map { i ->
            val r = wet(i)
            RainMinute(now.plus(Duration.ofMinutes(i.toLong())), r, null, if (r > 0) kind else PrecipKind.NONE)
        },
    )

    /** Hourly steps; [chanceForStart] gives the chance for the hour that STARTS at now + h hours. */
    private fun hours(chanceForStart: (Int) -> Int?) = (0..14).map { h ->
        Hour(now.plus(Duration.ofHours(h + 1L)), 50.0, Condition.RAIN, false, 80, 90, 5.0, 180, chanceForStart(h), 0.0, 10.0)
    }

    private fun say(n: Nowcast?, h: List<Hour> = hours { 10 }) = RainTiming.sentence(n, h, now, zone)

    @Test fun `raining now, stopping, returning later from the hourly forecast`() {
        val n = pirate { if (it < 24) 0.08 else 0.0 }
        assertEquals("Rain eases in ~25 min · returns around 8 PM", say(n, hours { if (it == 4) 70 else 10 }))
    }

    @Test fun `raining now, stopping, no return in sight`() {
        assertEquals("Rain eases in ~25 min", say(pirate { if (it < 24) 0.08 else 0.0 }))
    }

    @Test fun `raining now, a short break is not easing`() {
        // 5 dry minutes then rain again: not "eases"; the real stop is at 40.
        val n = pirate { if (it < 20 || it in 25 until 40) 0.08 else 0.0 }
        assertEquals("Rain eases in ~40 min", say(n))
    }

    @Test fun `raining now, stops, then returns within the hour`() {
        val n = pirate { if (it < 10 || it >= 45) 0.08 else 0.0 }
        assertEquals("Rain eases in ~10 min · returns in ~45 min", say(n))
    }

    @Test fun `a dry tail shorter than the dry run is not easing`() {
        // Rain for 57 minutes, then 3 dry minutes at the end of the data.
        assertEquals("Rain for the next hour", say(pirate { if (it < 57) 0.08 else 0.0 }, hours { 90 }))
    }

    @Test fun `raining all hour, easing later from the hourly forecast`() {
        val n = pirate { 0.1 }
        assertEquals("Rain for the next hour · easing around 7 PM", say(n, hours { if (it < 3) 80 else 20 }))
        assertEquals("Rain for the next hour", say(n, hours { 90 }))
    }

    @Test fun `dry now, starting soon`() {
        assertEquals("Rain starting in ~15 min", say(pirate { if (it >= 14) 0.05 else 0.0 }))
        assertEquals("Rain starting in a few min", say(pirate { if (it >= 2) 0.05 else 0.0 }))
    }

    @Test fun `snow is called snow`() {
        assertEquals("Snow starting in ~30 min", say(pirate(PrecipKind.SNOW) { if (it >= 30) 0.05 else 0.0 }))
    }

    @Test fun `dry hour, rain likely later today, or none ahead`() {
        val dry = pirate { 0.0 }
        assertEquals("Dry for the next hour · rain likely around 9 PM", say(dry, hours { if (it == 5) 60 else 10 }))
        assertEquals("No rain expected in the next 12 hours", say(dry))
    }

    @Test fun `drizzle below the threshold counts as dry`() {
        assertEquals("No rain expected in the next 12 hours", say(pirate { 0.002 }))
    }

    @Test fun `no minute data uses the hourly forecast only`() {
        assertEquals("Rain likely around 6 PM", say(null, hours { if (it == 2) 55 else 0 }))
        assertNull(RainTiming.sentence(null, emptyList(), now, zone))
    }

    @Test fun `fresh Pirate data is used, stale data falls back to Open-Meteo 15-minute steps`() {
        val minutes = pirate { 0.08 }.minutes
        val quarters = (1..8).map { QuarterHour(now.plus(Duration.ofMinutes(15L * it)), 0.01) }
        assertEquals(NowcastSource.PIRATE_WEATHER, RainTiming.chooseNowcast(minutes, quarters, now.plusSeconds(20))?.source)
        // 45 minutes later only 15 Pirate minutes are left: not enough.
        val later = now.plus(Duration.ofMinutes(45))
        val n = RainTiming.chooseNowcast(minutes, quarters, later)
        assertEquals(NowcastSource.OPEN_METEO, n?.source)
        assertEquals(60, n?.minutes?.size)
        // 0.01 in per 15 min = 0.04 in/hr.
        assertEquals(0.04, n!!.minutes.first().intensityInPerHr!!, 1e-9)
        assertNull(RainTiming.chooseNowcast(null, emptyList(), now))
    }

    @Test fun `Open-Meteo steps are placed on the 15 minutes before their time`() {
        val quarters = listOf(
            QuarterHour(now.plus(Duration.ofMinutes(15)), 0.0), // covers 4:00-4:15
            QuarterHour(now.plus(Duration.ofMinutes(30)), 0.02), // covers 4:15-4:30
            QuarterHour(now.plus(Duration.ofMinutes(45)), 0.0),
            QuarterHour(now.plus(Duration.ofMinutes(60)), 0.0),
        )
        val n = RainTiming.fromQuarterHours(quarters, now)!!
        assertEquals(0.0, n.minutes[14].intensityInPerHr!!, 0.0)
        assertEquals(0.08, n.minutes[15].intensityInPerHr!!, 1e-9)
        assertEquals(0.0, n.minutes[30].intensityInPerHr!!, 0.0)
        assertEquals("Rain starting in ~15 min", say(n))
        // Not covering the whole hour: unusable.
        assertNull(RainTiming.fromQuarterHours(quarters.take(2), now))
    }

    @Test fun `strip has 12 five-minute bars scaled to heavy rain`() {
        val strip = RainTiming.strip(pirate { if (it < 5) 0.3 else if (it < 10) 0.15 else 0.0 })
        assertEquals(12, strip.size)
        assertEquals(1f, strip[0]!!, 1e-6f)
        assertEquals(0.5f, strip[1]!!, 1e-6f)
        assertEquals(0f, strip[2]!!, 0f)
        assertTrue(RainTiming.strip(null).isEmpty())
    }

    @Test fun `minutes are rounded to five`() {
        assertEquals("a few min", RainTiming.about(2))
        assertEquals("~5 min", RainTiming.about(3))
        assertEquals("~25 min", RainTiming.about(24))
        assertEquals("~45 min", RainTiming.about(44))
    }
}
