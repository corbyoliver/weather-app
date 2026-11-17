package dev.corbyoliver.weather.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.corbyoliver.weather.data.WeatherRepository
import dev.corbyoliver.weather.data.WeatherSnapshot
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

/** Where the home screen gets its weather. */
sealed interface HomeSource {
    /** Real data from the repository. */
    data class Live(val repository: WeatherRepository) : HomeSource

    /** Made-up data for checking each frog look (debug builds only). */
    data class Sample(val scenario: SampleScenario) : HomeSource
}

class HomeViewModel(private val source: HomeSource) : ViewModel() {
    private val selectedDay = MutableStateFlow(0)

    /** Ticks once a minute so "Now", the hourly row and "out of date" follow the clock. */
    private val minuteTicks = flow {
        while (true) {
            emit(Instant.now())
            delay(60_000)
        }
    }

    val state: StateFlow<HomeUiState> = when (source) {
        is HomeSource.Live -> combine(source.repository.state, selectedDay, minuteTicks) { snap, day, _ ->
            renderLive(snap, day, Instant.now())
        }
        is HomeSource.Sample -> combine(selectedDay, minuteTicks) { day, _ ->
            renderSample(source.scenario, day, Instant.now())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(message = "Loading forecast…"))

    init {
        if (source is HomeSource.Live) {
            viewModelScope.launch(Dispatchers.IO) { source.repository.loadCache() }
        }
    }

    fun selectDay(i: Int) {
        selectedDay.value = i
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

internal fun renderLive(snap: WeatherSnapshot, selectedDay: Int, now: Instant): HomeUiState {
    val alerts = presentAlerts(snap.alerts, now)
    val rain = presentRain(snap, now)
    return presentHome(
        HomeInputs(
            forecast = snap.forecast,
            now = now,
            selectedDay = selectedDay,
            rainLine = rain.line,
            rainStrip = rain.strip,
            alert = alerts.banner,
            alertsNote = alerts.note,
            message = liveMessage(snap),
        ),
    )
}

internal fun liveMessage(snap: WeatherSnapshot): String? {
    val error = snap.lastError
    return when {
        snap.forecast == null && error != null -> "Could not load the forecast. $error"
        snap.forecast == null -> "Loading forecast…"
        error != null -> "Could not update. $error"
        else -> null
    }
}

private fun renderSample(scenario: SampleScenario, selectedDay: Int, now: Instant): HomeUiState = presentHome(
    HomeInputs(
        forecast = SampleData.forecast(now, scenario),
        now = now,
        selectedDay = selectedDay,
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
