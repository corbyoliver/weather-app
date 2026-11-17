package dev.corbyoliver.weather.data.cache

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import dev.corbyoliver.weather.data.AppJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
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

/** What is written to disk. An empty file means "nothing cached yet". */
@Serializable
data class OpenMeteoCacheFile(val entry: CachedOpenMeteo? = null)

interface ForecastCache {
    suspend fun load(): CachedOpenMeteo?
    suspend fun save(value: CachedOpenMeteo)
}

object OpenMeteoCacheSerializer : Serializer<OpenMeteoCacheFile> {
    override val defaultValue = OpenMeteoCacheFile()

    override suspend fun readFrom(input: InputStream): OpenMeteoCacheFile = try {
        AppJson.decodeFromString(OpenMeteoCacheFile.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Cached forecast could not be read.", e)
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("Cached forecast could not be read.", e)
    }

    override suspend fun writeTo(t: OpenMeteoCacheFile, output: OutputStream) {
        output.write(AppJson.encodeToString(OpenMeteoCacheFile.serializer(), t).encodeToByteArray())
    }
}

class DataStoreForecastCache(private val store: DataStore<OpenMeteoCacheFile>) : ForecastCache {
    override suspend fun load(): CachedOpenMeteo? = store.data.first().entry
    override suspend fun save(value: CachedOpenMeteo) {
        store.updateData { OpenMeteoCacheFile(value) }
    }

    companion object {
        /**
         * Only one DataStore may be open per file, so create this once (the
         * Application does). A corrupt file is replaced with an empty cache.
         */
        fun create(file: File, scope: CoroutineScope): DataStoreForecastCache = DataStoreForecastCache(
            DataStoreFactory.create(
                serializer = OpenMeteoCacheSerializer,
                corruptionHandler = ReplaceFileCorruptionHandler { OpenMeteoCacheSerializer.defaultValue },
                scope = scope,
                produceFile = { file },
            ),
        )
    }
}
