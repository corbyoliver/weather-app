package dev.corbyoliver.weather.data.pirate

import dev.corbyoliver.weather.data.SourceException
import dev.corbyoliver.weather.domain.PrecipKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Made up, in the shape the Pirate Weather docs show (no API key yet for a
 * live capture): light rain 4:00-4:24 PM PST on 2026-11-17, then dry.
 */
val PIRATE_FIXTURE by lazy {
    requireNotNull(PirateMapperTest::class.java.getResource("/fixtures/pirate-minutely-synthetic-docs-shape.json")).readText()
}

/** A live response for the home point, captured 2026-11-17 at 3:08 PM Pacific: a dry hour. */
val PIRATE_LIVE_FIXTURE by lazy {
    requireNotNull(PirateMapperTest::class.java.getResource("/fixtures/pirate-minutely-captured-2026-11-17.json")).readText()
}

class PirateMapperTest {
    @Test fun `the live response maps to 61 dry minutes, one minute apart`() {
        val m = PirateMapper.map(PIRATE_LIVE_FIXTURE)
        assertEquals(61, m.size)
        m.zipWithNext().forEach { (a, b) -> assertEquals(60L, b.time.epochSecond - a.time.epochSecond) }
        assertTrue(m.all { it.intensityInPerHr == 0.0 && it.kind == PrecipKind.NONE })
        assertTrue(m.all { it.probability != null })
    }

    @Test fun `minute data is mapped in order with US units`() {
        val m = PirateMapper.map(PIRATE_FIXTURE)
        assertEquals(61, m.size)
        assertEquals(Instant.parse("2026-11-18T00:00:00Z"), m.first().time)
        assertEquals(0.08, m[0].intensityInPerHr!!, 0.0)
        assertEquals(0.9, m[0].probability!!, 0.0)
        assertEquals(PrecipKind.RAIN, m[0].kind)
        assertEquals(PrecipKind.NONE, m[30].kind)
    }

    @Test fun `no minute data is an error, not a dry hour`() {
        assertThrows(SourceException::class.java) { PirateMapper.map("""{"latitude":45.9}""") }
        assertThrows(SourceException::class.java) { PirateMapper.map("""{"minutely":{"data":[]}}""") }
        assertThrows(SourceException::class.java) { PirateMapper.map("Forbidden") }
    }

    @Test fun `missing values stay null`() {
        val m = PirateMapper.map("""{"minutely":{"data":[{"time":1}]}}""").single()
        assertNull(m.intensityInPerHr)
        assertNull(m.kind)
    }

    @Test fun `precip types`() {
        assertEquals(PrecipKind.SNOW, PirateMapper.kindOf("snow"))
        assertEquals(PrecipKind.SLEET, PirateMapper.kindOf("ice"))
        assertEquals(PrecipKind.MIXED, PirateMapper.kindOf("mixed"))
        assertNull(PirateMapper.kindOf("hail?"))
    }

    @Test fun `request asks only for minute data in US units for the home point`() {
        val url = PirateRequests.forecastUrl("KEY")
        assertTrue(url.startsWith("https://api.pirateweather.net/forecast/KEY/45.8901,-122.8071?"))
        assertTrue(url.contains("units=us"))
        assertTrue(url.contains("exclude=currently,hourly,daily,alerts"))
        assertFalse(url.contains("minutely"))
    }
}
