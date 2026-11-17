package dev.corbyoliver.weather.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.corbyoliver.weather.WeatherApp
import dev.corbyoliver.weather.data.RefreshResult
import java.time.Duration

/**
 * Background forecast refresh, about every 30 minutes (~48 Open-Meteo calls a
 * day, far under the 10,000/day free limit). It goes through the same
 * repository as the screen, so an open screen updates at once and the cache
 * is fresh the next time the app opens. It never posts a notification.
 */
class ForecastRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = (applicationContext as WeatherApp).repository
        return workResultFor(repository.refresh())
    }

    companion object {
        const val UNIQUE_NAME = "forecast-refresh"
        val INTERVAL: Duration = Duration.ofMinutes(30)
        /** First retry after a failed run; WorkManager doubles it each time. */
        val RETRY_BACKOFF: Duration = Duration.ofMinutes(5)

        /** Safe to call on every app start: an existing schedule is kept, with these settings. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ForecastRefreshWorker>(INTERVAL)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RETRY_BACKOFF)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}

/**
 * A failed download is retried with backoff. The repository refuses to try
 * again within 5 minutes of a failure, which matches [ForecastRefreshWorker.RETRY_BACKOFF].
 * "Skipped" means the data is recent enough, which is a success.
 */
internal fun workResultFor(result: RefreshResult): ListenableWorker.Result = when (result) {
    RefreshResult.UPDATED, RefreshResult.SKIPPED -> ListenableWorker.Result.success()
    RefreshResult.FAILED -> ListenableWorker.Result.retry()
}
