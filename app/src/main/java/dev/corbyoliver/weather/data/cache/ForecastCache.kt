package dev.corbyoliver.weather.data.cache

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import dev.corbyoliver.weather.data.AppJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * The last good Open-Meteo download, kept as the raw JSON. The app maps it
 * again on load, so a fix in the mapper also fixes old cached data.
 */
@Serializable
data class CachedOpenMeteo(
    val fetchedAtEpochMs: Long,
    val forecastJson: String,
    val airQualityJson: String? = null,
)

/** The last good NWS alerts download, kept as the raw JSON. */
@Serializable
data class CachedNws(val fetchedAtEpochMs: Long, val alertsJson: String)

/** The last good Pirate Weather download, kept as the raw JSON. */
@Serializable
data class CachedPirate(val fetchedAtEpochMs: Long, val json: String)

/** What is written to disk. An empty file means "nothing cached yet". */
@Serializable
data class OpenMeteoCacheFile(val entry: CachedOpenMeteo? = null)

@Serializable
data class NwsCacheFile(val entry: CachedNws? = null)

@Serializable
data class PirateCacheFile(val entry: CachedPirate? = null)

interface ForecastCache {
    suspend fun load(): CachedOpenMeteo?
    suspend fun save(value: CachedOpenMeteo)
}

interface AlertsCache {
    suspend fun load(): CachedNws?
    suspend fun save(value: CachedNws)
}

interface RainCache {
    suspend fun load(): CachedPirate?
    suspend fun save(value: CachedPirate)
}

/** Stores one @Serializable value as JSON. A file that cannot be read is reported as corrupt. */
class JsonFileSerializer<T>(private val serializer: KSerializer<T>, override val defaultValue: T) : Serializer<T> {
    override suspend fun readFrom(input: InputStream): T = try {
        AppJson.decodeFromString(serializer, input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Cached data could not be read.", e)
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("Cached data could not be read.", e)
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        output.write(AppJson.encodeToString(serializer, t).encodeToByteArray())
    }
}

val OpenMeteoCacheSerializer = JsonFileSerializer(OpenMeteoCacheFile.serializer(), OpenMeteoCacheFile())
val NwsCacheSerializer = JsonFileSerializer(NwsCacheFile.serializer(), NwsCacheFile())
val PirateCacheSerializer = JsonFileSerializer(PirateCacheFile.serializer(), PirateCacheFile())

/**
 * Only one DataStore may be open per file, so create each once (the
 * Application does). A corrupt file is replaced with an empty cache.
 */
fun <T> jsonDataStore(file: File, serializer: JsonFileSerializer<T>, scope: CoroutineScope): DataStore<T> =
    DataStoreFactory.create(
        serializer = serializer,
        corruptionHandler = ReplaceFileCorruptionHandler { serializer.defaultValue },
        scope = scope,
        produceFile = { file },
    )

class DataStoreForecastCache(private val store: DataStore<OpenMeteoCacheFile>) : ForecastCache {
    override suspend fun load(): CachedOpenMeteo? = store.data.first().entry
    override suspend fun save(value: CachedOpenMeteo) {
        store.updateData { OpenMeteoCacheFile(value) }
    }

    companion object {
        fun create(file: File, scope: CoroutineScope) = DataStoreForecastCache(jsonDataStore(file, OpenMeteoCacheSerializer, scope))
    }
}

class DataStoreAlertsCache(private val store: DataStore<NwsCacheFile>) : AlertsCache {
    override suspend fun load(): CachedNws? = store.data.first().entry
    override suspend fun save(value: CachedNws) {
        store.updateData { NwsCacheFile(value) }
    }

    companion object {
        fun create(file: File, scope: CoroutineScope) = DataStoreAlertsCache(jsonDataStore(file, NwsCacheSerializer, scope))
    }
}

class DataStoreRainCache(private val store: DataStore<PirateCacheFile>) : RainCache {
    override suspend fun load(): CachedPirate? = store.data.first().entry
    override suspend fun save(value: CachedPirate) {
        store.updateData { PirateCacheFile(value) }
    }

    companion object {
        fun create(file: File, scope: CoroutineScope) = DataStoreRainCache(jsonDataStore(file, PirateCacheSerializer, scope))
    }
}
