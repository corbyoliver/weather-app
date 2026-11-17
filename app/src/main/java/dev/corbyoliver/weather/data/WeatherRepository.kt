package dev.corbyoliver.weather.data

import android.util.Log
import dev.corbyoliver.weather.data.cache.AlertsCache
import dev.corbyoliver.weather.data.cache.CachedNws
import dev.corbyoliver.weather.data.cache.CachedOpenMeteo
import dev.corbyoliver.weather.data.cache.CachedPirate
import dev.corbyoliver.weather.data.cache.ForecastCache
import dev.corbyoliver.weather.data.cache.RainCache
import dev.corbyoliver.weather.data.pirate.PirateMapper
import dev.corbyoliver.weather.data.pirate.PirateSource
import dev.corbyoliver.weather.data.nws.NwsMapper
import dev.corbyoliver.weather.data.nws.NwsSource
import dev.corbyoliver.weather.data.openmeteo.OpenMeteoMapper
import dev.corbyoliver.weather.data.openmeteo.OpenMeteoSource
import dev.corbyoliver.weather.domain.Alert
import dev.corbyoliver.weather.domain.Forecast
import dev.corbyoliver.weather.domain.HomeLocation
import dev.corbyoliver.weather.domain.RainMinute
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

/**
 * NWS alerts as last known.
 *
 * [alerts] is null until one check has succeeded (from the network or the
 * cache): "not known" is never shown as "no alerts".
 */
data class AlertsSnapshot(
    val alerts: List<Alert>? = null,
    /** When [alerts] was downloaded. */
    val checkedAt: Instant? = null,
    /** Set when the most recent check failed. Safe to show on screen. */
    val lastError: String? = null,
)

/**
 * Pirate Weather minute-by-minute rain, as last known. [minutes] is null
 * until one download succeeded. When it is missing, old or failing, the
 * screen falls back to Open-Meteo's 15-minute data.
 */
data class RainSnapshot(
    /** False when no API key is set: the app then always uses the fallback. */
    val configured: Boolean = true,
    val minutes: List<RainMinute>? = null,
    val fetchedAt: Instant? = null,
    val lastError: String? = null,
)

/** What the screens see. [forecast] is the last good data, even after a failed refresh. */
data class WeatherSnapshot(
    val forecast: Forecast? = null,
    /** Set when the most recent forecast refresh failed. Safe to show on screen. */
    val lastError: String? = null,
    val refreshing: Boolean = false,
    val alerts: AlertsSnapshot = AlertsSnapshot(),
    val rain: RainSnapshot = RainSnapshot(),
)

enum class RefreshResult { UPDATED, SKIPPED, FAILED }

/**
 * The one place screens get weather from: Open-Meteo for the forecast, NWS
 * for alerts, Pirate Weather for minute-by-minute rain timing.
 *
 * The last good download from each source is cached on disk, so the app
 * opens with data even when offline. A failed download never replaces good
 * cached data.
 */
class WeatherRepository(
    private val openMeteo: OpenMeteoSource,
    private val cache: ForecastCache,
    private val nws: NwsSource,
    private val alertsCache: AlertsCache,
    /** Null when no Pirate Weather API key is set. */
    private val pirate: PirateSource?,
    private val rainCache: RainCache,
    private val clock: () -> Instant = Instant::now,
    private val zone: ZoneId = HomeLocation.ZONE,
    private val log: (String, Throwable?) -> Unit = { msg, e -> Log.w(TAG, msg, e) },
) {
    private val _state = MutableStateFlow(WeatherSnapshot(rain = RainSnapshot(configured = pirate != null)))
    val state: StateFlow<WeatherSnapshot> = _state.asStateFlow()

    private val forecastMutex = Mutex()
    private var forecastCacheLoaded = false
    private var cached: CachedOpenMeteo? = null
    private var lastFailureAt: Instant? = null

    private val alertsMutex = Mutex()
    private var alertsCacheLoaded = false
    private var lastAlertsFailureAt: Instant? = null

    private val rainMutex = Mutex()
    private var rainCacheLoaded = false
    private var lastRainFailureAt: Instant? = null

    /** Shows the cached forecast and alerts, if any. Safe to call more than once. */
    suspend fun loadCache() {
        forecastMutex.withLock { loadForecastCacheLocked() }
        alertsMutex.withLock { loadAlertsCacheLocked() }
        rainMutex.withLock { loadRainCacheLocked() }
    }

    /**
     * Downloads a new forecast unless one was fetched less than
     * [MIN_AFTER_SUCCESS] ago, or the last try failed less than
     * [MIN_AFTER_FAILURE] ago. [force] skips both waits.
     */
    suspend fun refresh(force: Boolean = false): RefreshResult = forecastMutex.withLock {
        loadForecastCacheLocked()
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
            _state.update { it.copy(forecast = forecast, lastError = null, refreshing = false) }
            return RefreshResult.UPDATED
        } catch (e: SourceException) {
            log("Forecast refresh failed", e)
            lastFailureAt = now
            _state.update { it.copy(refreshing = false, lastError = e.message) }
            return RefreshResult.FAILED
        }
    }

    /**
     * Checks NWS for active alerts unless the last check succeeded less than
     * [ALERTS_MIN_AFTER_SUCCESS] ago or failed less than
     * [ALERTS_MIN_AFTER_FAILURE] ago. [force] skips both waits.
     */
    suspend fun refreshAlerts(force: Boolean = false): RefreshResult = alertsMutex.withLock {
        loadAlertsCacheLocked()
        val now = clock()
        if (!force) {
            val lastSuccess = _state.value.alerts.checkedAt
            if (lastSuccess != null && isWithin(lastSuccess, now, ALERTS_MIN_AFTER_SUCCESS)) return RefreshResult.SKIPPED
            val lastFailure = lastAlertsFailureAt
            if (lastFailure != null && isWithin(lastFailure, now, ALERTS_MIN_AFTER_FAILURE)) return RefreshResult.SKIPPED
        }
        try {
            val json = nws.activeAlertsJson()
            val alerts = NwsMapper.map(json) // throws before saving if unreadable
            try {
                alertsCache.save(CachedNws(now.toEpochMilli(), json))
            } catch (e: IOException) {
                log("Could not save the alerts cache", e)
            }
            lastAlertsFailureAt = null
            _state.update { it.copy(alerts = AlertsSnapshot(alerts = alerts, checkedAt = now)) }
            return RefreshResult.UPDATED
        } catch (e: SourceException) {
            log("Alerts check failed", e)
            lastAlertsFailureAt = now
            _state.update { it.copy(alerts = it.alerts.copy(lastError = e.message)) }
            return RefreshResult.FAILED
        }
    }

    /**
     * Downloads Pirate Weather's next 60 minutes, at most once every
     * [RAIN_MIN_AFTER_SUCCESS] (the spec caches it 5-10 minutes) and not
     * within [RAIN_MIN_AFTER_FAILURE] of a failure. Called when the app
     * opens, never in the background, to stay well inside the free plan.
     */
    suspend fun refreshRain(force: Boolean = false): RefreshResult = rainMutex.withLock {
        val source = pirate ?: return RefreshResult.SKIPPED
        loadRainCacheLocked()
        val now = clock()
        if (!force) {
            val lastSuccess = _state.value.rain.fetchedAt
            if (lastSuccess != null && isWithin(lastSuccess, now, RAIN_MIN_AFTER_SUCCESS)) return RefreshResult.SKIPPED
            val lastFailure = lastRainFailureAt
            if (lastFailure != null && isWithin(lastFailure, now, RAIN_MIN_AFTER_FAILURE)) return RefreshResult.SKIPPED
        }
        try {
            val json = source.forecastJson()
            val minutes = PirateMapper.map(json)
            try {
                rainCache.save(CachedPirate(now.toEpochMilli(), json))
            } catch (e: IOException) {
                log("Could not save the rain timing cache", e)
            }
            lastRainFailureAt = null
            _state.update { it.copy(rain = it.rain.copy(minutes = minutes, fetchedAt = now, lastError = null)) }
            return RefreshResult.UPDATED
        } catch (e: SourceException) {
            // No stack trace: it could quote the request URL, which holds the API key.
            log("Rain timing download failed: ${e.message}", null)
            lastRainFailureAt = now
            _state.update { it.copy(rain = it.rain.copy(lastError = e.message)) }
            return RefreshResult.FAILED
        }
    }

    private suspend fun loadRainCacheLocked() {
        if (rainCacheLoaded || pirate == null) return
        rainCacheLoaded = true
        val entry = try {
            rainCache.load()
        } catch (e: IOException) {
            log("Could not read the rain timing cache", e)
            null
        } ?: return
        val minutes = try {
            PirateMapper.map(entry.json)
        } catch (e: SourceException) {
            log("Cached rain timing could not be read; ignoring it", null)
            return
        }
        val at = Instant.ofEpochMilli(entry.fetchedAtEpochMs)
        _state.update { if (it.rain.fetchedAt == null) it.copy(rain = it.rain.copy(minutes = minutes, fetchedAt = at)) else it }
    }

    private suspend fun loadForecastCacheLocked() {
        if (forecastCacheLoaded) return
        forecastCacheLoaded = true
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

    private suspend fun loadAlertsCacheLocked() {
        if (alertsCacheLoaded) return
        alertsCacheLoaded = true
        val entry = try {
            alertsCache.load()
        } catch (e: IOException) {
            log("Could not read the alerts cache", e)
            null
        } ?: return
        val alerts = try {
            NwsMapper.map(entry.alertsJson)
        } catch (e: SourceException) {
            log("Cached alerts could not be read; ignoring them", e)
            return
        }
        val checkedAt = Instant.ofEpochMilli(entry.fetchedAtEpochMs)
        _state.update { if (it.alerts.checkedAt == null) it.copy(alerts = it.alerts.copy(alerts = alerts, checkedAt = checkedAt)) else it }
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
        val ALERTS_MIN_AFTER_SUCCESS: Duration = Duration.ofMinutes(5)
        val ALERTS_MIN_AFTER_FAILURE: Duration = Duration.ofMinutes(2)
        val RAIN_MIN_AFTER_SUCCESS: Duration = Duration.ofMinutes(10)
        val RAIN_MIN_AFTER_FAILURE: Duration = Duration.ofMinutes(5)
    }
}
