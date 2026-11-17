package dev.corbyoliver.weather.data

import dev.corbyoliver.weather.data.cache.CachedOpenMeteo
import dev.corbyoliver.weather.data.cache.ForecastCache
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

class WeatherRepositoryTest {
    private var now: Instant = FIXTURE_FETCHED_AT
    private val source = FakeSource()
    private val cache = FakeCache()
    private fun repo() = WeatherRepository(source, cache, clock = { now }, log = { _, _ -> })
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
}
