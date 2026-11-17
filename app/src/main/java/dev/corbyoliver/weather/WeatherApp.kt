package dev.corbyoliver.weather

import android.app.Application
import dev.corbyoliver.weather.data.WeatherRepository
import dev.corbyoliver.weather.data.cache.DataStoreAlertsCache
import dev.corbyoliver.weather.data.cache.DataStoreForecastCache
import dev.corbyoliver.weather.data.cache.DataStoreRainCache
import dev.corbyoliver.weather.data.newHttpClient
import dev.corbyoliver.weather.data.nws.NwsClient
import dev.corbyoliver.weather.data.openmeteo.OpenMeteoClient
import dev.corbyoliver.weather.data.pirate.PirateWeatherClient
import dev.corbyoliver.weather.ui.widget.WidgetUpdater
import dev.corbyoliver.weather.work.AlertPollWorker
import dev.corbyoliver.weather.work.ForecastRefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.io.File

/** Holds the app-wide objects. One of each per process. */
class WeatherApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val repository: WeatherRepository by lazy {
        val http = newHttpClient()
        WeatherRepository(
            openMeteo = OpenMeteoClient(http),
            cache = DataStoreForecastCache.create(File(filesDir, "datastore/openmeteo-cache.json"), appScope),
            nws = NwsClient(http, BuildConfig.NWS_USER_AGENT),
            alertsCache = DataStoreAlertsCache.create(File(filesDir, "datastore/nws-alerts-cache.json"), appScope),
            pirate = BuildConfig.PIRATE_WEATHER_API_KEY.trim()
                .takeIf { it.isNotEmpty() && it != "REPLACE_ME" }
                ?.let { PirateWeatherClient(http, it) },
            rainCache = DataStoreRainCache.create(File(filesDir, "datastore/pirate-cache.json"), appScope),
        )
    }

    override fun onCreate() {
        super.onCreate()
        ForecastRefreshWorker.schedule(this)
        AlertPollWorker.schedule(this)
        redrawWidgetsOnChange()
    }

    /**
     * Redraws the home-screen widgets whenever the data changes: after each
     * background refresh, alert check or app download. Waits a second so a
     * burst of changes gives one redraw.
     */
    @OptIn(FlowPreview::class)
    private fun redrawWidgetsOnChange() {
        appScope.launch {
            repository.loadCache()
            WidgetUpdater.updateAll(this@WeatherApp)
            repository.state.drop(1).debounce(1_000).collect { WidgetUpdater.updateAll(this@WeatherApp) }
        }
    }
}
