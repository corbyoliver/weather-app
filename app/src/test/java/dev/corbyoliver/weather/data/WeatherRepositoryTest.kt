package dev.corbyoliver.weather.data

import dev.corbyoliver.weather.data.cache.AlertsCache
import dev.corbyoliver.weather.data.cache.CachedNws
import dev.corbyoliver.weather.data.cache.CachedOpenMeteo
import dev.corbyoliver.weather.data.cache.ForecastCache
import dev.corbyoliver.weather.data.nws.NWS_OREGON_FIXTURE
import dev.corbyoliver.weather.data.nws.NWS_POINT_EMPTY_FIXTURE
import dev.corbyoliver.weather.data.nws.NwsSource
import dev.corbyoliver.weather.data.openmeteo.AIR_QUALITY_FIXTURE
import dev.corbyoliver.weather.data.openmeteo.FIXTURE_FETCHED_AT
import dev.corbyoliver.weather.data.openmeteo.FORECAST_FIXTURE
import dev.corbyoliver.weather.data.openmeteo.OpenMeteoSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.IOException
import java.time.Duration
import java.time.Instant

private class FakeSource : OpenMeteoSource {
    var forecast: () -> String = { FORECAST_FIXTURE }
    var airQuality: () -> String = { AIR_QUALITY_FIXTURE }
    var forecastCalls = 0
    override suspend fun forecastJson(): String { forecastCalls++; return forecast() }
    override suspend fun airQualityJson(): String = airQuality()
}

private class FakeCache(var entry: CachedOpenMeteo? = null) : ForecastCache {
    var saves = 0
    var failLoad = false
    override suspend fun load(): CachedOpenMeteo? = if (failLoad) throw IOException("disk") else entry
    override suspend fun save(value: CachedOpenMeteo) { saves++; entry = value }
}

private class FakeNws : NwsSource {
    var body: () -> String = { NWS_OREGON_FIXTURE }
    var calls = 0
    override suspend fun activeAlertsJson(): String { calls++; return body() }
}

private class FakeAlertsCache(var entry: CachedNws? = null) : AlertsCache {
    var saves = 0
    override suspend fun load(): CachedNws? = entry
    override suspend fun save(value: CachedNws) { saves++; entry = value }
}

class WeatherRepositoryTest {
    private var now: Instant = FIXTURE_FETCHED_AT
    private val source = FakeSource()
    private val cache = FakeCache()
    private val nws = FakeNws()
    private val alertsCache = FakeAlertsCache()
    private fun repo() = WeatherRepository(source, cache, nws, alertsCache, clock = { now }, log = { _, _ -> })
    private fun offline() { source.forecast = { throw SourceException("Open-Meteo could not be reached (UnknownHostException).") } }
    private fun cachedEntry(at: Instant) = CachedOpenMeteo(at.toEpochMilli(), FORECAST_FIXTURE, AIR_QUALITY_FIXTURE)

    @Test fun `cached forecast shows before any download`() = runBlocking {
        val oldAt = now.minus(Duration.ofHours(5))
        cache.entry = cachedEntry(oldAt)
        val r = repo()
        r.loadCache()
        assertEquals(oldAt, r.state.value.forecast?.fetchedAt)
        assertEquals(0, source.forecastCalls)
    }

    @Test fun `a successful refresh shows and saves the new forecast`() = runBlocking {
        val r = repo()
        assertEquals(RefreshResult.UPDATED, r.refresh())
        val s = r.state.value
        assertEquals(now, s.forecast?.fetchedAt)
        assertNull(s.lastError)
        assertEquals(false, s.refreshing)
        assertEquals(now.toEpochMilli(), cache.entry?.fetchedAtEpochMs)
        assertEquals(FORECAST_FIXTURE, cache.entry?.forecastJson)
        assertEquals(54, s.forecast?.currentUsAqi)
    }

    @Test fun `no new download within 10 minutes of a success`() = runBlocking {
        val r = repo()
        r.refresh()
        now = now.plus(Duration.ofMinutes(9))
        assertEquals(RefreshResult.SKIPPED, r.refresh())
        now = now.plus(Duration.ofMinutes(1))
        assertEquals(RefreshResult.UPDATED, r.refresh())
        assertEquals(2, source.forecastCalls)
    }

    @Test fun `a fresh cache from an earlier run also counts as a recent success`() = runBlocking {
        cache.entry = cachedEntry(now.minus(Duration.ofMinutes(3)))
        assertEquals(RefreshResult.SKIPPED, repo().refresh())
        assertEquals(0, source.forecastCalls)
    }

    @Test fun `a failure keeps the cached forecast and reports the error`() = runBlocking {
        val oldAt = now.minus(Duration.ofHours(2))
        cache.entry = cachedEntry(oldAt)
        offline()
        val r = repo()
        assertEquals(RefreshResult.FAILED, r.refresh())
        val s = r.state.value
        assertEquals(oldAt, s.forecast?.fetchedAt)
        assertEquals("Open-Meteo could not be reached (UnknownHostException).", s.lastError)
        assertEquals(false, s.refreshing)
        assertEquals(0, cache.saves)
    }

    @Test fun `after a failure, wait 5 minutes before trying again`() = runBlocking {
        offline()
        val r = repo()
        assertEquals(RefreshResult.FAILED, r.refresh())
        now = now.plus(Duration.ofMinutes(4))
        assertEquals(RefreshResult.SKIPPED, r.refresh())
        source.forecast = { FORECAST_FIXTURE }
        now = now.plus(Duration.ofMinutes(1))
        assertEquals(RefreshResult.UPDATED, r.refresh())
        assertNull(r.state.value.lastError)
    }

    @Test fun `force skips the waits`() = runBlocking {
        val r = repo()
        r.refresh()
        assertEquals(RefreshResult.UPDATED, r.refresh(force = true))
    }

    @Test fun `data the app cannot read never replaces good cached data`() = runBlocking {
        val oldAt = now.minus(Duration.ofHours(1))
        cache.entry = cachedEntry(oldAt)
        source.forecast = { "<html>Service unavailable</html>" }
        val r = repo()
        assertEquals(RefreshResult.FAILED, r.refresh())
        assertEquals(oldAt.toEpochMilli(), cache.entry?.fetchedAtEpochMs)
        assertEquals(oldAt, r.state.value.forecast?.fetchedAt)
        assertNotNull(r.state.value.lastError)
    }

    @Test fun `if only air quality fails, the forecast updates and the older air quality is kept`() = runBlocking {
        cache.entry = cachedEntry(now.minus(Duration.ofHours(1)))
        source.airQuality = { throw SourceException("Open-Meteo air quality error (HTTP 503).") }
        val r = repo()
        assertEquals(RefreshResult.UPDATED, r.refresh())
        assertEquals(AIR_QUALITY_FIXTURE, cache.entry?.airQualityJson)
        assertEquals(120, r.state.value.forecast?.airQuality?.size)
        assertNull(r.state.value.lastError)
    }

    @Test fun `an unreadable cache is ignored, not fatal`() = runBlocking {
        cache.entry = CachedOpenMeteo(now.toEpochMilli(), "garbage", null)
        val r = repo()
        r.loadCache()
        assertNull(r.state.value.forecast)
        assertEquals(RefreshResult.UPDATED, r.refresh())
    }

    @Test fun `a cache that cannot be read from disk is ignored, not fatal`() = runBlocking {
        cache.failLoad = true
        val r = repo()
        assertEquals(RefreshResult.UPDATED, r.refresh())
    }

    @Test fun `loading the cache again does not replace a newer forecast`() = runBlocking {
        cache.entry = cachedEntry(now.minus(Duration.ofHours(3)))
        val r = repo()
        r.refresh(force = true)
        val shown = r.state.value.forecast
        r.loadCache()
        assertSame(shown, r.state.value.forecast)
    }

    // ---- NWS alerts ----

    @Test fun `alerts are unknown, not empty, before any check`() = runBlocking {
        val r = repo()
        r.loadCache()
        assertNull(r.state.value.alerts.alerts)
    }

    @Test fun `a successful alerts check shows and saves the alerts`() = runBlocking {
        val r = repo()
        assertEquals(RefreshResult.UPDATED, r.refreshAlerts())
        val a = r.state.value.alerts
        assertEquals(listOf("Winter Weather Advisory", "Wind Advisory"), a.alerts?.map { it.event })
        assertEquals(now, a.checkedAt)
        assertNull(a.lastError)
        assertEquals(NWS_OREGON_FIXTURE, alertsCache.entry?.alertsJson)
    }

    @Test fun `an empty alert list is a real answer`() = runBlocking {
        nws.body = { NWS_POINT_EMPTY_FIXTURE }
        val r = repo()
        r.refreshAlerts()
        assertEquals(emptyList<Any>(), r.state.value.alerts.alerts)
    }

    @Test fun `a failed alerts check keeps known alerts and reports the error`() = runBlocking {
        val r = repo()
        r.refreshAlerts()
        nws.body = { throw SourceException("National Weather Service error (HTTP 503).") }
        now = now.plus(Duration.ofMinutes(20))
        assertEquals(RefreshResult.FAILED, r.refreshAlerts())
        val a = r.state.value.alerts
        assertEquals(2, a.alerts?.size)
        assertEquals("National Weather Service error (HTTP 503).", a.lastError)
        assertEquals(1, alertsCache.saves)
    }

    @Test fun `an unreadable alerts body never becomes no alerts`() = runBlocking {
        nws.body = { "<html>Service unavailable</html>" }
        val r = repo()
        assertEquals(RefreshResult.FAILED, r.refreshAlerts())
        assertNull(r.state.value.alerts.alerts)
        assertNotNull(r.state.value.alerts.lastError)
        assertEquals(0, alertsCache.saves)
    }

    @Test fun `alert checks wait 5 minutes after a success and 2 after a failure`() = runBlocking {
        val r = repo()
        r.refreshAlerts()
        now = now.plus(Duration.ofMinutes(4))
        assertEquals(RefreshResult.SKIPPED, r.refreshAlerts())
        now = now.plus(Duration.ofMinutes(1))
        nws.body = { throw SourceException("down") }
        assertEquals(RefreshResult.FAILED, r.refreshAlerts())
        now = now.plus(Duration.ofSeconds(119))
        assertEquals(RefreshResult.SKIPPED, r.refreshAlerts())
        now = now.plus(Duration.ofSeconds(1))
        assertEquals(RefreshResult.FAILED, r.refreshAlerts())
        assertEquals(3, nws.calls)
    }

    @Test fun `cached alerts load with their check time`() = runBlocking {
        val at = now.minus(Duration.ofMinutes(30))
        alertsCache.entry = CachedNws(at.toEpochMilli(), NWS_OREGON_FIXTURE)
        val r = repo()
        r.loadCache()
        assertEquals(at, r.state.value.alerts.checkedAt)
        assertEquals(2, r.state.value.alerts.alerts?.size)
        assertEquals(0, nws.calls)
    }

    @Test fun `a forecast refresh does not wipe the alerts`() = runBlocking {
        val r = repo()
        r.refreshAlerts()
        r.refresh(force = true)
        assertEquals(2, r.state.value.alerts.alerts?.size)
    }
}
