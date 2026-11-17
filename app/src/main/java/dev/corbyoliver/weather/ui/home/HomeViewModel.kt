package dev.corbyoliver.weather.ui.home

import androidx.lifecycle.ViewModel
import dev.corbyoliver.weather.sample.SampleData
import dev.corbyoliver.weather.sample.SampleScenario
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

class HomeViewModel(private val scenario: SampleScenario = SampleScenario.RAIN) : ViewModel() {
    private val selectedDay = MutableStateFlow(0)
    private val _state = MutableStateFlow(render())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    fun selectDay(i: Int) {
        selectedDay.value = i
        _state.value = render()
    }

    private fun render(): HomeUiState {
        val now = Instant.now()
        return presentHome(
            HomeInputs(
                forecast = SampleData.forecast(now, scenario),
                now = now,
                selectedDay = selectedDay.value,
                rainLine = "Rain eases in ~25 min · returns around 8 PM",
                alert = AlertBannerUi(
                    text = "Wind Advisory until 9 PM · NWS",
                    title = "Wind Advisory",
                    body = "Sample alert text. Real alerts come from the National Weather Service.",
                    moreCount = 0,
                ),
            ),
        )
    }
}
