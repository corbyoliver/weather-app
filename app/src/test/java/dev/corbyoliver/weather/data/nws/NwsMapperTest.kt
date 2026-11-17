package dev.corbyoliver.weather.data.nws

import dev.corbyoliver.weather.data.SourceException
import dev.corbyoliver.weather.domain.AlertSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private fun fixture(name: String): String =
    requireNotNull(NwsMapperTest::class.java.getResource("/fixtures/$name")) { "missing fixture $name" }.readText()

/** Two live Oregon advisories (Medford office), captured 2026-11-17 at 2:49 PM Pacific. */
val NWS_OREGON_FIXTURE by lazy { fixture("nws-alerts-oregon-captured-2026-11-17.json") }

/** The live answer for the home point at the same time: no active alerts. */
val NWS_POINT_EMPTY_FIXTURE by lazy { fixture("nws-alerts-point-empty-captured-2026-11-17.json") }

class NwsMapperTest {
    @Test fun `live alerts are mapped with their times in UTC`() {
        val alerts = NwsMapper.map(NWS_OREGON_FIXTURE)
        assertEquals(2, alerts.size)
        val wind = alerts.single { it.event == "Wind Advisory" }
        assertEquals(AlertSeverity.MODERATE, wind.severity)
        assertEquals(Instant.parse("2026-11-18T03:00:00Z"), wind.onset) // 7 PM PST
        assertEquals(Instant.parse("2026-11-18T15:00:00Z"), wind.ends) // 7 AM PST
        assertEquals(Instant.parse("2026-11-18T11:30:00Z"), wind.expires)
        assertEquals(wind.ends, wind.until)
        assertEquals("NWS Medford OR", wind.senderName)
        assertTrue(wind.description!!.isNotBlank())
        assertTrue(wind.id.startsWith("urn:oid:"))
    }

    @Test fun `no active alerts is an empty list`() {
        assertEquals(emptyList<Any>(), NwsMapper.map(NWS_POINT_EMPTY_FIXTURE))
    }

    @Test fun `a body that is not an alert list is a SourceException, never an empty list`() {
        assertThrows(SourceException::class.java) { NwsMapper.map("<html>busy</html>") }
        assertThrows(SourceException::class.java) { NwsMapper.map("""{"title":"Unexpected Problem","status":500}""") }
    }

    private fun one(props: String) = """{"features":[{"properties":{$props}}]}"""

    @Test fun `test, exercise and cancel messages are dropped`() {
        assertEquals(0, NwsMapper.map(one(""""id":"a","event":"Test","status":"Test","messageType":"Alert"""")).size)
        assertEquals(0, NwsMapper.map(one(""""id":"a","event":"X","status":"Exercise","messageType":"Alert"""")).size)
        assertEquals(0, NwsMapper.map(one(""""id":"a","event":"X","status":"Actual","messageType":"Cancel"""")).size)
        assertEquals(1, NwsMapper.map(one(""""id":"a","event":"X","status":"Actual","messageType":"Alert"""")).size)
    }

    @Test fun `an update replaces the alert it references`() {
        val json = """{"features":[
            {"properties":{"id":"old","event":"Wind Advisory","status":"Actual","messageType":"Alert"}},
            {"properties":{"id":"new","event":"Wind Advisory","status":"Actual","messageType":"Update","references":[{"identifier":"old"}]}}
        ]}"""
        assertEquals(listOf("new"), NwsMapper.map(json).map { it.id })
    }

    @Test fun `missing or odd fields give safe defaults`() {
        val a = NwsMapper.map(one(""""id":"a","status":"Actual","severity":"Bad","onset":"soon"""")).single()
        assertEquals("Weather alert", a.event)
        assertEquals(AlertSeverity.UNKNOWN, a.severity)
        assertNull(a.onset)
        assertNull(a.until)
    }

    @Test fun `request asks for the home point and always sends a User-Agent`() {
        assertEquals("https://api.weather.gov/alerts/active?point=45.8901,-122.8071", NwsRequests.activeAlertsUrl())
        assertEquals(NwsRequests.FALLBACK_USER_AGENT, NwsRequests.userAgent("  "))
        assertEquals("(app, me@example.com)", NwsRequests.userAgent(" (app, me@example.com) "))
    }
}
