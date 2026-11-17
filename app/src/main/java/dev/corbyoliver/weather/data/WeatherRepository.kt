package dev.corbyoliver.weather.data

import android.util.Log
import dev.corbyoliver.weather.data.cache.CachedOpenMeteo
import dev.corbyoliver.weather.data.cache.ForecastCache
import dev.corbyoliver.weather.data.openmeteo.OpenMeteoMapper
import dev.corbyoliver.weather.data.openmeteo.OpenMeteoSource
import dev.corbyoliver.weather.domain.Forecast
import dev.corbyoliver.weather.domain.HomeLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** What the screens see. [forecast] is the last good data, even after a failed refresh. */
data class WeatherSnapshot(
    val forecast: Forecast? = null,
    /** Set when the most recent refresh failed. Safe to show on screen. */
    val lastError: String? = null,
    val refreshing: Boolean = false,
)

enum class RefreshResult { UPDATED, SKIPPED, FAILED }

/**
 * The one place screens get weather from. Today it has one source
 * (Open-Meteo); Pirate Weather and NWS join it in later steps.
 *
 * The last good download is cached on disk, so the app opens with data even
 * when offline. A failed download never replaces good cached data.
 */
class WeatherRepository(
    private val openMeteo: OpenMeteoSource,
    private val cache: ForecastCache,
    private val clock: () -> Instant = Instant::now,
    private val zone: ZoneId = HomeLocation.ZONE,
    private val log: (String, Throwable?) -> Unit = { msg, e -> Log.w(TAG, msg, e) },
) {
    private val _state = MutableStateFlow(WeatherSnapshot())
    val state: StateFlow<WeatherSnapshot> = _state.asStateFlow()

    private val mutex = Mutex()
    private var cacheLoaded = false
    private var cached: CachedOpenMeteo? = null
    private var lastFailureAt: Instant? = null

    /** Shows the cached forecast, if any. Safe to call more than once. */
    suspend fun loadCache() = mutex.withLock { loadCacheLocked() }

    /**
     * Downloads a new forecast unless one was fetched less than
     * [MIN_AFTER_SUCCESS] ago, or the last try failed less than
     * [MIN_AFTER_FAILURE] ago. [force] skips both waits.
     */
    suspend fun refresh(force: Boolean = false): RefreshResult = mutex.withLock {
        loadCacheLocked()
        val now = clock()
        if (!force) {
            val lastSuccess = _state.value.forecast?.fetchedAt
            if (lastSuccess != null && isWithin(lastSuccess, now, MIN_AFTER_SUCCESS)) return RefreshResult.SKIPPED
            val lastFailure = lastFailureAt
            if (lastFailure != null && isWithin(lastFailure, now, MIN_AFTER_FAILURE)) return RefreshResult.SKIPPED
        }

        _state.update { it.copy(refreshing = true) }
        try {
            val forecastJson = openMeteo.forecastJson()
            val airQualityJson = try {
                openMeteo.airQualityJson()
            } catch (e: SourceException) {
                // Air quality is a side detail: keep the older copy and carry on.
                log("Air quality download failed; keeping the cached copy", e)
                cached?.airQualityJson
            }
            // Map before saving, so data the app cannot read never replaces good data.
            val forecast = OpenMeteoMapper.map(forecastJson, airQualityJson, now, zone)
            val entry = CachedOpenMeteo(now.toEpochMilli(), forecastJson, airQualityJson)
            try {
                cache.save(entry)
            } catch (e: IOException) {
                log("Could not save the forecast cache", e)
            }
            cached = entry
            lastFailureAt = null
            _state.value = WeatherSnapshot(forecast = forecast)
            return RefreshResult.UPDATED
        } catch (e: SourceException) {
            log("Forecast refresh failed", e)
            lastFailureAt = now
            _state.update { it.copy(refreshing = false, lastError = e.message) }
            return RefreshResult.FAILED
        }
    }

    private suspend fun loadCacheLocked() {
        if (cacheLoaded) return
        cacheLoaded = true
        val entry = try {
            cache.load()
        } catch (e: IOException) {
            log("Could not read the forecast cache", e)
            null
        } ?: return
        val forecast = try {
            OpenMeteoMapper.map(entry.forecastJson, entry.airQualityJson, Instant.ofEpochMilli(entry.fetchedAtEpochMs), zone)
        } catch (e: SourceException) {
            log("Cached forecast could not be read; ignoring it", e)
            return
        }
        cached = entry
        // Never let an old cache overwrite a newer forecast already on screen.
        _state.update { if (it.forecast == null) it.copy(forecast = forecast) else it }
    }

    /** True if [then] is less than [window] before [now]. A time in the future (clock changed) never counts. */
    private fun isWithin(then: Instant, now: Instant, window: Duration): Boolean {
        val age = Duration.between(then, now)
        return !age.isNegative && age < window
    }

    companion object {
        private const val TAG = "WeatherRepository"
        val MIN_AFTER_SUCCESS: Duration = Duration.ofMinutes(10)
        val MIN_AFTER_FAILURE: Duration = Duration.ofMinutes(5)
    }
}
