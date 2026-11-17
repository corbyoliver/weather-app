package dev.corbyoliver.weather.data.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DataStoreForecastCacheTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun <T> withCache(file: File, block: suspend (DataStoreForecastCache) -> T): T {
        val scope = CoroutineScope(Dispatchers.IO + Job())
        try {
            return runBlocking { block(DataStoreForecastCache.create(file, scope)) }
        } finally {
            scope.cancel()
        }
    }

    @Test fun `empty cache loads as nothing`() {
        assertNull(withCache(File(tmp.root, "c.json")) { it.load() })
    }

    @Test fun `saved forecast survives a restart`() {
        val file = File(tmp.root, "c.json")
        val entry = CachedOpenMeteo(1794940200000, """{"current":{"time":1}}""", """{"current":{"time":1,"us_aqi":54}}""")
        withCache(file) { it.save(entry) }
        // A new DataStore on the same file, as after the app restarts.
        assertEquals(entry, withCache(file) { it.load() })
    }

    @Test fun `a corrupt cache file is replaced with an empty cache`() {
        val file = File(tmp.root, "c.json")
        file.writeText("this is not json")
        assertNull(withCache(file) { it.load() })
    }
}
