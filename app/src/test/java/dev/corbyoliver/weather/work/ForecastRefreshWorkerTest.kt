package dev.corbyoliver.weather.work

import androidx.work.ListenableWorker
import dev.corbyoliver.weather.data.RefreshResult
import dev.corbyoliver.weather.data.WeatherRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class ForecastRefreshWorkerTest {
    @Test fun `updated and skipped are successes, a failure is retried`() {
        assertTrue(workResultFor(RefreshResult.UPDATED) is ListenableWorker.Result.Success)
        assertTrue(workResultFor(RefreshResult.SKIPPED) is ListenableWorker.Result.Success)
        assertTrue(workResultFor(RefreshResult.FAILED) is ListenableWorker.Result.Retry)
    }

    @Test fun `runs about every 30 minutes, within the free daily limit`() {
        assertEquals(Duration.ofMinutes(30), ForecastRefreshWorker.INTERVAL)
        // Two Open-Meteo calls per run (forecast + air quality).
        val callsPerDay = 2 * Duration.ofDays(1).toMinutes() / ForecastRefreshWorker.INTERVAL.toMinutes()
        assertTrue(callsPerDay <= 10_000)
    }

    @Test fun `first retry waits no less than the repository's wait after a failure`() {
        // A shorter backoff would only be skipped by the repository, which ends the retries early.
        assertTrue(ForecastRefreshWorker.RETRY_BACKOFF >= WeatherRepository.MIN_AFTER_FAILURE)
        // The periodic run must not be cut short by the repository's wait after a success.
        assertTrue(ForecastRefreshWorker.INTERVAL > WeatherRepository.MIN_AFTER_SUCCESS)
    }
}
