package dev.corbyoliver.weather

import android.app.Application
import dev.corbyoliver.weather.data.WeatherRepository
import dev.corbyoliver.weather.data.cache.DataStoreForecastCache
import dev.corbyoliver.weather.data.newHttpClient
import dev.corbyoliver.weather.data.openmeteo.OpenMeteoClient
import dev.corbyoliver.weather.work.ForecastRefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** Holds the app-wide objects. One of each per process. */
class WeatherApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val repository: WeatherRepository by lazy {
        WeatherRepository(
            openMeteo = OpenMeteoClient(newHttpClient()),
            cache = DataStoreForecastCache.create(File(filesDir, "datastore/openmeteo-cache.json"), appScope),
        )
    }

    override fun onCreate() {
        super.onCreate()
        ForecastRefreshWorker.schedule(this)
    }
}
