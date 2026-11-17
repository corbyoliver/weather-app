package dev.corbyoliver.weather.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.corbyoliver.weather.data.WeatherRepository
import dev.corbyoliver.weather.data.WeatherSnapshot
import dev.corbyoliver.weather.domain.HomeLocation
import dev.corbyoliver.weather.sample.SampleData
import dev.corbyoliver.weather.sample.SampleScenario
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

/** Where the home screen gets its weather. */
sealed interface HomeSource {
    /** Real data from the repository. */
    data class Live(val repository: WeatherRepository) : HomeSource

    /** Made-up data for checking each frog look (debug builds only). */
    data class Sample(val scenario: SampleScenario) : HomeSource
}

class HomeViewModel(private val source: HomeSource) : ViewModel() {
    /** Remembered by date, not position: the list loses a day at midnight. */
    private val selectedDate = MutableStateFlow<LocalDate?>(null)

    /** Ticks once a minute so "Now", the hourly row and "out of date" follow the clock. */
    private val minuteTicks = flow {
        while (true) {
            emit(Instant.now())
            delay(60_000)
        }
    }

    val state: StateFlow<HomeUiState> = when (source) {
        is HomeSource.Live -> combine(source.repository.state, selectedDate, minuteTicks) { snap, day, _ ->
            renderLive(snap, day, Instant.now())
        }
        is HomeSource.Sample -> combine(selectedDate, minuteTicks) { day, _ ->
            renderSample(source.scenario, day, Instant.now())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(message = "Loading forecast…"))

    init {
        if (source is HomeSource.Live) {
            viewModelScope.launch(Dispatchers.IO) { source.repository.loadCache() }
        }
    }

    fun selectDay(i: Int) {
        state.value.days.getOrNull(i)?.let { selectedDate.value = it.date }
    }

    /** Called each time the screen comes to the front. The repository skips the download if the data is recent. */
    fun onForeground() {
        if (source is HomeSource.Live) {
            viewModelScope.launch(Dispatchers.IO) { source.repository.refresh() }
            viewModelScope.launch(Dispatchers.IO) { source.repository.refreshAlerts() }
            viewModelScope.launch(Dispatchers.IO) { source.repository.refreshRain() }
        }
    }
}

internal fun renderLive(snap: WeatherSnapshot, selectedDate: LocalDate?, now: Instant): HomeUiState {
    val alerts = presentAlerts(snap.alerts, now)
    val rain = presentRain(snap, now)
    return presentHome(
        HomeInputs(
            forecast = snap.forecast,
            now = now,
            selectedDate = selectedDate,
            rainLine = rain.line,
            rainStrip = rain.strip,
            nowcast = rain.nowcast,
            alert = alerts.banner,
            alertsNote = alerts.note,
            message = liveMessage(snap, now),
        ),
    )
}

internal fun liveMessage(snap: WeatherSnapshot, now: Instant): String? {
    val error = snap.lastError
    return when {
        snap.forecast == null && error != null -> "Could not load the forecast. $error"
        snap.forecast == null -> "Loading forecast…"
        error != null -> "Could not update. $error Showing the forecast from ${Format.updated(snap.forecast.fetchedAt, now, HomeLocation.ZONE).removePrefix("Updated ")}."
        else -> null
    }
}

private fun renderSample(scenario: SampleScenario, selectedDate: LocalDate?, now: Instant): HomeUiState = presentHome(
    HomeInputs(
        forecast = SampleData.forecast(now, scenario),
        now = now,
        selectedDate = selectedDate,
        rainLine = "Rain eases in ~25 min · returns around 8 PM",
        alert = AlertBannerUi(
            text = "Wind Advisory until 9 PM · NWS",
            title = "Wind Advisory",
            body = "Sample alert text. Real alerts come from the National Weather Service.",
            moreCount = 0,
        ),
        message = "Sample data ($scenario)",
    ),
)
