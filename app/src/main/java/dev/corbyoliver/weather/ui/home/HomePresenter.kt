package dev.corbyoliver.weather.ui.home

import dev.corbyoliver.weather.domain.Forecast
import dev.corbyoliver.weather.domain.FrogThresholds
import dev.corbyoliver.weather.domain.HomeLocation
import dev.corbyoliver.weather.domain.Hour
import dev.corbyoliver.weather.domain.Nowcast
import dev.corbyoliver.weather.domain.frogInputsFor
import dev.corbyoliver.weather.domain.selectFrogState
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Inputs for one render of the home screen. */
data class HomeInputs(
    val forecast: Forecast?,
    val now: Instant,
    /** The day the user picked, by date, so it survives midnight; null means today. */
    val selectedDate: LocalDate? = null,
    val zone: ZoneId = HomeLocation.ZONE,
    val rainLine: String? = null,
    val rainStrip: List<Float?> = emptyList(),
    /** Minute-by-minute rain for the next hour, if any; the frog uses it too. */
    val nowcast: Nowcast? = null,
    val alert: AlertBannerUi? = null,
    val alertsNote: String? = null,
    val message: String? = null,
)

/** A forecast older than this is marked out of date on screen. */
val STALE_AFTER: Duration = Duration.ofMinutes(90)

fun presentHome(input: HomeInputs, thresholds: FrogThresholds = FrogThresholds()): HomeUiState {
    val f = input.forecast ?: return HomeUiState(
        alert = input.alert,
        alertsNote = input.alertsNote,
        message = input.message ?: "Loading forecast…",
    )
    val zone = input.zone
    val now = input.now
    val today = now.atZone(zone).toLocalDate()
    val c = f.current

    val frog = selectFrogState(frogInputsFor(f, input.nowcast, now), thresholds)

    val upcoming = f.hours.filter { it.time.isAfter(now) }.take(5)
    val hourCells = listOf(HourCellUi("Now", Format.temp(c.temperatureF), c.condition, c.isDay)) +
        upcoming.map { HourCellUi(Format.hour(it.time, zone), Format.temp(it.temperatureF), it.condition, it.isDay) }

    val hero = HeroUi(
        place = HomeLocation.NAME,
        temperature = Format.temp(c.temperatureF),
        condition = c.condition,
        isDay = c.isDay,
        conditionText = Format.conditionText(c.condition, c.isDay),
        feelsLike = "Feels like ${Format.temp(c.feelsLikeF)}",
        rainLine = input.rainLine,
        hours = hourCells,
        frog = frog,
        rainStrip = input.rainStrip,
    )

    // Never show days that are already over (an old cached forecast).
    val days = f.days.filter { !it.date.isBefore(today) }
    // A picked day that has passed falls back to today.
    val selected = days.indexOfFirst { it.date == input.selectedDate }.takeIf { it >= 0 } ?: 0
    val chips = days.map { d ->
        val label = if (d.date == today) "Today" else d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)
        val full = if (d.date == today) "Today" else d.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)
        DayChipUi(
            date = d.date,
            label = label,
            condition = d.condition,
            high = Format.temp(d.highF),
            low = Format.temp(d.lowF),
            description = "$full, ${Format.conditionText(d.condition, true)}, high ${Format.temp(d.highF)}, low ${Format.temp(d.lowF)}",
        )
    }

    val details = days.getOrNull(selected)?.let { day -> presentDay(f, day.date, today, now, zone) }

    val age = Duration.between(f.fetchedAt, now)
    val stale = age > STALE_AFTER
    val updatedText = Format.updated(f.fetchedAt, now, zone)
    val updated = UpdatedUi(
        text = if (stale) "$updatedText · out of date" else updatedText,
        isStale = stale,
    )

    return HomeUiState(
        alert = input.alert,
        alertsNote = input.alertsNote,
        hero = hero,
        days = chips,
        selectedDay = selected,
        details = details,
        updated = updated,
        message = input.message,
    )
}

private fun presentDay(f: Forecast, date: LocalDate, today: LocalDate, now: Instant, zone: ZoneId): DayDetailsUi {
    val day = f.days.first { it.date == date }
    val isToday = date == today

    // Rain bars: one per local hour of the day. A step at t covers the hour that
    // starts at t - 1h. An hour with no step (the last day's 11 PM, which needs
    // the next day's midnight step) is shown as unknown, never dropped.
    val byEnd = f.hours.associateBy { it.time }
    val dayStart = date.atStartOfDay(zone).toInstant()
    val nextStart = date.plusDays(1).atStartOfDay(zone).toInstant()
    val starts = generateSequence(dayStart) { it.plus(Duration.ofHours(1)) }.takeWhile { it.isBefore(nextStart) }.toList()
    val bars = starts.map { start ->
        val end = start.plus(Duration.ofHours(1))
        BarUi(
            label = Format.shortHour(start, zone),
            chancePct = byEnd[end]?.precipChancePrevHourPct,
            isPast = end <= now,
            showLabel = start.atZone(zone).hour % 6 == 0,
        )
    }
    val known = starts.mapNotNull { start -> byEnd[start.plus(Duration.ofHours(1))]?.precipChancePrevHourPct?.let { start to it } }
    val peak = known.maxByOrNull { it.second }
    val precipDescription = when {
        peak == null -> "Hourly rain chance not available"
        peak.second == 0 -> "No rain expected: 0% chance every hour"
        else -> "Hourly rain chance peaks at ${peak.second}% around ${Format.hour(peak.first, zone)}"
    }

    val dayHours = f.hours.filter { it.time.atZone(zone).toLocalDate() == date }
    val windHours = dayHours.filter { it.time.atZone(zone).hour % 3 == 0 }.map { h ->
        WindHourUi(
            label = Format.shortHour(h.time, zone),
            speed = Format.mph(h.windMph),
            fromDeg = h.windFromDeg,
            description = "${Format.hour(h.time, zone)}: ${Format.mph(h.windMph)} miles per hour from the ${Format.compass(h.windFromDeg).ifEmpty { "unknown direction" }}",
        )
    }

    val humidity = if (isToday && f.current.humidityPct != null) {
        Format.pct(f.current.humidityPct)
    } else {
        val values = dayHours.mapNotNull { it.humidityPct }
        if (values.isEmpty()) "--" else if (values.min() == values.max()) "${values.min()}%" else "${values.min()}–${values.max()}%"
    }

    val aqi: Int? = if (isToday && f.currentUsAqi != null) f.currentUsAqi
    else f.airQuality.filter { it.time.atZone(zone).toLocalDate() == date }.mapNotNull { it.usAqi }.maxOrNull()
    val aqiText = when {
        aqi == null -> "No forecast"
        isToday -> "$aqi · ${Format.aqiCategory(aqi)}"
        else -> "Up to $aqi · ${Format.aqiCategory(aqi)}"
    }

    val dir = Format.compass(day.windDominantFromDeg)
    val windValue = buildString {
        append("${Format.mph(day.windMaxMph)} mph")
        if (dir.isNotEmpty()) append(" $dir")
        if (day.gustMaxMph != null) append(" · gusts ${day.gustMaxMph.roundToInt()}")
    }

    return DayDetailsUi(
        precipValue = "${Format.pct(day.precipChanceMaxPct)} · ${Format.inches(day.precipSumIn)}",
        precipBars = bars,
        precipDescription = precipDescription,
        windValue = windValue,
        windHours = windHours,
        humidityValue = humidity,
        airQualityValue = aqiText,
    )
}
