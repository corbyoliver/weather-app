package dev.corbyoliver.weather.data.openmeteo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMeteoRequestsTest {
    private fun params(url: String): Map<String, String> =
        url.substringAfter("?").split("&").associate { it.substringBefore("=") to it.substringAfter("=") }

    @Test fun `forecast request asks for Columbia City in local time, US units and unixtime`() {
        val url = OpenMeteoRequests.forecastUrl()
        assertTrue(url.startsWith("https://api.open-meteo.com/v1/forecast?"))
        val p = params(url)
        assertEquals("45.8901", p["latitude"])
        assertEquals("-122.8071", p["longitude"])
        assertEquals("America%2FLos_Angeles", p["timezone"])
        assertEquals("unixtime", p["timeformat"])
        assertEquals("fahrenheit", p["temperature_unit"])
        assertEquals("mph", p["wind_speed_unit"])
        assertEquals("inch", p["precipitation_unit"])
        assertEquals("7", p["forecast_days"])
        assertEquals("24", p["forecast_minutely_15"])
        assertEquals("precipitation", p["minutely_15"])
    }

    @Test fun `forecast request has no past_hours, so hourly data starts at midnight today`() {
        assertFalse(params(OpenMeteoRequests.forecastUrl()).containsKey("past_hours"))
    }

    @Test fun `every field the response models read is requested`() {
        val p = params(OpenMeteoRequests.forecastUrl())
        assertEquals(OpenMeteoRequests.CURRENT, p["current"]!!.split(","))
        assertEquals(OpenMeteoRequests.HOURLY, p["hourly"]!!.split(","))
        assertEquals(OpenMeteoRequests.DAILY, p["daily"]!!.split(","))
        assertTrue("precipitation_probability" in OpenMeteoRequests.HOURLY)
        assertTrue("sunrise" in OpenMeteoRequests.DAILY)
    }

    @Test fun `air quality request asks for US AQI over the 5 days it covers`() {
        val url = OpenMeteoRequests.airQualityUrl()
        assertTrue(url.startsWith("https://air-quality-api.open-meteo.com/v1/air-quality?"))
        val p = params(url)
        assertEquals("us_aqi", p["current"])
        assertEquals("us_aqi", p["hourly"])
        assertEquals("5", p["forecast_days"])
        assertEquals("America%2FLos_Angeles", p["timezone"])
    }
}
