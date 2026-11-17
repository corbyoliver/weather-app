package dev.corbyoliver.weather.ui.home

import dev.corbyoliver.weather.domain.Condition
import dev.corbyoliver.weather.domain.FrogState

/** Everything the home screen draws, already formatted. No domain logic here. */
data class HomeUiState(
    val alert: AlertBannerUi? = null,
    /** "Alerts unavailable" when the NWS check failed. Never "No alerts". */
    val alertsNote: String? = null,
    val hero: HeroUi? = null,
    val days: List<DayChipUi> = emptyList(),
    val selectedDay: Int = 0,
    val details: DayDetailsUi? = null,
    val updated: UpdatedUi? = null,
    val message: String? = null,
)

data class AlertBannerUi(
    val text: String,
    val title: String,
    val body: String,
    val moreCount: Int,
)

data class HeroUi(
    val place: String,
    val temperature: String,
    val condition: Condition,
    val isDay: Boolean,
    val conditionText: String,
    val feelsLike: String,
    val rainLine: String?,
    val hours: List<HourCellUi>,
    val frog: FrogState,
    /** Mini 60-minute rain strip, 0..1 intensity per bucket; empty if none. */
    val rainStrip: List<Float?> = emptyList(),
)

data class HourCellUi(val label: String, val temperature: String, val condition: Condition, val isDay: Boolean)

data class DayChipUi(
    val label: String,
    val condition: Condition,
    val high: String,
    val low: String,
    val description: String,
)

data class DayDetailsUi(
    val precipValue: String,
    val precipBars: List<BarUi>,
    val precipDescription: String,
    val windValue: String,
    val windHours: List<WindHourUi>,
    val humidityValue: String,
    val airQualityValue: String,
)

data class BarUi(val label: String, val chancePct: Int?, val isPast: Boolean, val showLabel: Boolean)

data class WindHourUi(val label: String, val speed: String, val fromDeg: Int?, val description: String)

data class UpdatedUi(val text: String, val isStale: Boolean)
