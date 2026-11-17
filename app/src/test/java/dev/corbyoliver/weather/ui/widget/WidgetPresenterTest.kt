package dev.corbyoliver.weather.ui.widget

import dev.corbyoliver.weather.data.AlertsSnapshot
import dev.corbyoliver.weather.data.RainSnapshot
import dev.corbyoliver.weather.data.WeatherSnapshot
import dev.corbyoliver.weather.data.nws.NWS_OREGON_FIXTURE
import dev.corbyoliver.weather.data.nws.NwsMapper
import dev.corbyoliver.weather.data.openmeteo.AIR_QUALITY_FIXTURE
import dev.corbyoliver.weather.data.openmeteo.FIXTURE_FETCHED_AT
import dev.corbyoliver.weather.data.openmeteo.FORECAST_FIXTURE
import dev.corbyoliver.weather.data.openmeteo.OpenMeteoMapper
import dev.corbyoliver.weather.domain.PrecipKind
import dev.corbyoliver.weather.domain.RainMinute
import dev.corbyoliver.weather.ui.home.ALERTS_CHECKING
import dev.corbyoliver.weather.ui.home.ALERTS_UNAVAILABLE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class WidgetPresenterTest {
    private val forecast = OpenMeteoMapper.map(FORECAST_FIXTURE, AIR_QUALITY_FIXTURE, FIXTURE_FETCHED_AT)
    private val now = FIXTURE_FETCHED_AT.plus(Duration.ofMinutes(5)) // 10:35 AM PST

    @Test fun `no data yet says so on every widget`() {
        val w = presentWidgets(WeatherSnapshot(), now)
        assertEquals("--", w.temp.temperature)
        assertEquals("Open the app to load", w.temp.rainText)
        assertEquals(ALERTS_CHECKING, w.alert.title)
        assertFalse(w.alert.active)
        assertEquals("Rain timing unavailable", w.rain.line)
        assertTrue(w.rain.strip.isEmpty())
    }

    @Test fun `real data fills the temperature widget`() {
        val w = presentWidgets(WeatherSnapshot(forecast = forecast), now)
        assertEquals("47°", w.temp.temperature)
        assertEquals("Updated 10:30 AM", w.temp.updated)
        assertTrue(w.temp.rainText.matches(Regex("Rain \\d+% next hour")))
        assertFalse(w.temp.isStale)
        assertTrue(presentWidgets(WeatherSnapshot(forecast = forecast), now.plus(Duration.ofHours(2))).temp.isStale)
    }

    @Test fun `minute rain raises the widget's rain chance`() {
        val minutes = (0 until 60).map { RainMinute(now.plus(Duration.ofMinutes(it.toLong())), 0.1, 0.9, PrecipKind.RAIN) }
        val w = presentWidgets(WeatherSnapshot(forecast = forecast, rain = RainSnapshot(minutes = minutes, fetchedAt = now)), now)
        assertEquals("Rain 90% next hour", w.temp.rainText)
        assertEquals("Rain for the next hour", w.rain.line.substringBefore(" ·"))
        assertEquals(12, w.rain.strip.size)
    }

    @Test fun `active alert turns the alert widget amber with its time`() {
        val checked = Instant.parse("2026-11-17T22:49:00Z")
        val snap = WeatherSnapshot(forecast = forecast, alerts = AlertsSnapshot(NwsMapper.map(NWS_OREGON_FIXTURE), checked))
        val a = presentWidgets(snap, checked).alert
        assertTrue(a.active)
        assertEquals("Wind Advisory", a.title)
        assertEquals("from 7 PM · +1 more", a.detail)
    }

    @Test fun `a fresh empty check says no active alerts, a failed one never does`() {
        val ok = presentWidgets(WeatherSnapshot(alerts = AlertsSnapshot(emptyList(), now)), now).alert
        assertEquals("No active alerts", ok.title)
        assertEquals("NWS · checked 10:35 AM", ok.detail)
        val failed = presentWidgets(WeatherSnapshot(alerts = AlertsSnapshot(emptyList(), now, lastError = "down")), now).alert
        assertEquals(ALERTS_UNAVAILABLE, failed.title)
        assertEquals("last checked 10:35 AM", failed.detail)
        val neverWorked = presentWidgets(WeatherSnapshot(alerts = AlertsSnapshot(lastError = "down")), now).alert
        assertEquals(ALERTS_UNAVAILABLE, neverWorked.title)
    }
}
