package dev.corbyoliver.weather.ui.widget

import dev.corbyoliver.weather.data.WeatherSnapshot
import dev.corbyoliver.weather.domain.Condition
import dev.corbyoliver.weather.domain.HomeLocation
import dev.corbyoliver.weather.domain.nextHourRainChanceAll
import dev.corbyoliver.weather.ui.home.ALERTS_UNAVAILABLE
import dev.corbyoliver.weather.ui.home.Format
import dev.corbyoliver.weather.ui.home.STALE_AFTER
import dev.corbyoliver.weather.ui.home.presentAlerts
import dev.corbyoliver.weather.ui.home.presentRain
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Small 2x2: temperature and the rain chance for the next hour. */
data class TempWidgetUi(
    val temperature: String,
    val condition: Condition?,
    val isDay: Boolean,
    val conditionText: String,
    val rainText: String,
    val updated: String,
    val isStale: Boolean,
)

/** Small 2x2 alert widget. Amber only when an alert is active. */
data class AlertWidgetUi(val active: Boolean, val title: String, val detail: String)

/** Wide 4x2: the rain sentence and the 60-minute strip. */
data class RainWidgetUi(val line: String, val strip: List<Float?>, val updated: String)

data class WidgetsUi(val temp: TempWidgetUi, val alert: AlertWidgetUi, val rain: RainWidgetUi)

/** Everything the three widgets show, from the same data as the home screen. Pure. */
fun presentWidgets(snap: WeatherSnapshot, now: Instant, zone: ZoneId = HomeLocation.ZONE): WidgetsUi {
    val f = snap.forecast
    val rain = presentRain(snap, now, zone)
    val updated = f?.fetchedAt?.let { Format.updated(it, now, zone) } ?: "No forecast yet"
    val stale = f != null && Duration.between(f.fetchedAt, now) > STALE_AFTER

    val temp = if (f == null) {
        TempWidgetUi("--", null, true, "", "Open the app to load", updated, false)
    } else {
        val chance = nextHourRainChanceAll(f.hours, rain.nowcast, now)
        TempWidgetUi(
            temperature = Format.temp(f.current.temperatureF),
            condition = f.current.condition,
            isDay = f.current.isDay,
            conditionText = Format.conditionText(f.current.condition, f.current.isDay),
            rainText = if (chance == null) "Rain chance unknown" else "Rain $chance% next hour",
            updated = updated,
            isStale = stale,
        )
    }

    val alerts = presentAlerts(snap.alerts, now, zone)
    val banner = alerts.banner
    val alert = when {
        banner != null -> {
            val detail = banner.text.removePrefix(banner.title).removeSuffix("· NWS").trim()
            val more = if (banner.moreCount > 0) " · +${banner.moreCount} more" else ""
            AlertWidgetUi(true, banner.title, (detail + more).trim().removePrefix("· "))
        }
        // Never "no alerts" unless a recent check really found none.
        alerts.note != null ->
            if (alerts.note.startsWith(ALERTS_UNAVAILABLE)) AlertWidgetUi(false, ALERTS_UNAVAILABLE, alerts.note.substringAfter("· ", ""))
            else AlertWidgetUi(false, alerts.note, "") // "Checking alerts…"
        else -> AlertWidgetUi(false, "No active alerts", "NWS · checked ${Format.clock(snap.alerts.checkedAt!!, zone)}")
    }

    val rainUi = RainWidgetUi(
        line = rain.line ?: "Rain timing unavailable",
        strip = rain.strip,
        updated = updated,
    )
    return WidgetsUi(temp, alert, rainUi)
}
