package dev.corbyoliver.weather.ui.home

import dev.corbyoliver.weather.data.WeatherSnapshot
import dev.corbyoliver.weather.data.openmeteo.AIR_QUALITY_FIXTURE
import dev.corbyoliver.weather.data.openmeteo.FIXTURE_FETCHED_AT
import dev.corbyoliver.weather.data.openmeteo.FORECAST_FIXTURE
import dev.corbyoliver.weather.data.openmeteo.OpenMeteoMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration

class HomeLiveRenderTest {
    private val forecast = OpenMeteoMapper.map(FORECAST_FIXTURE, AIR_QUALITY_FIXTURE, FIXTURE_FETCHED_AT)

    @Test fun `messages for each repository state`() {
        assertEquals("Loading forecast…", liveMessage(WeatherSnapshot()))
        assertEquals("Could not load the forecast. Boom.", liveMessage(WeatherSnapshot(lastError = "Boom.")))
        assertNull(liveMessage(WeatherSnapshot(forecast = forecast)))
        assertEquals("Could not update. Boom.", liveMessage(WeatherSnapshot(forecast = forecast, lastError = "Boom.")))
    }

    @Test fun `live data from the captured response fills the home screen`() {
        val ui = renderLive(WeatherSnapshot(forecast = forecast), null, FIXTURE_FETCHED_AT.plus(Duration.ofMinutes(5)))
        val hero = ui.hero!!
        assertEquals("47°", hero.temperature)
        assertEquals("Columbia City, OR", hero.place)
        assertEquals(6, hero.hours.size)
        assertEquals(7, ui.days.size)
        assertEquals("Today", ui.days[0].label)
        assertNotNull(ui.details)
        assertEquals("54 · Moderate", ui.details!!.airQualityValue)
        // No NWS check yet: the screen must not imply there are no alerts.
        assertNull(ui.alert)
        assertEquals(ALERTS_CHECKING, ui.alertsNote)
        // No Pirate Weather data: the line and strip come from Open-Meteo's 15-minute steps.
        assertEquals("No rain expected in the next 12 hours", hero.rainLine)
        assertEquals(12, hero.rainStrip.size)
    }
}
