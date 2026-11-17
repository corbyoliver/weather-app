package dev.corbyoliver.weather.ui.home

import dev.corbyoliver.weather.data.AlertsSnapshot
import dev.corbyoliver.weather.domain.Alert
import dev.corbyoliver.weather.domain.HomeLocation
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Alerts not checked successfully for this long are marked unavailable. Polls run every 15 minutes. */
val ALERTS_STALE_AFTER: Duration = Duration.ofMinutes(60)

const val ALERTS_UNAVAILABLE = "Alerts unavailable"
const val ALERTS_CHECKING = "Checking alerts…"

data class AlertsUi(val banner: AlertBannerUi?, val note: String?)

/**
 * Decides what the alert banner and the alert note say.
 *
 * Rule from the spec: when the alert check failed, say "Alerts unavailable",
 * never "No alerts". An alert the app already knows about stays on screen
 * until it ends, even if later checks fail.
 */
fun presentAlerts(s: AlertsSnapshot, now: Instant, zone: ZoneId = HomeLocation.ZONE): AlertsUi {
    val known = s.alerts
    val checkedAt = s.checkedAt
    val note = when {
        known == null || checkedAt == null -> if (s.lastError == null) ALERTS_CHECKING else ALERTS_UNAVAILABLE
        s.lastError != null || Duration.between(checkedAt, now) > ALERTS_STALE_AFTER ->
            "$ALERTS_UNAVAILABLE · last checked ${whenText(checkedAt, now, zone)}"
        else -> null
    }
    val active = activeAlerts(known.orEmpty(), now)
    val banner = active.firstOrNull()?.let { top ->
        AlertBannerUi(
            text = "${top.event}${timePhrase(top, now, zone)} · NWS",
            title = top.event,
            body = active.joinToString("\n\n— — —\n\n") { detailText(it, now, zone) },
            moreCount = active.size - 1,
        )
    }
    return AlertsUi(banner, note)
}

/** Alerts that have not ended yet, most serious first, then the soonest. */
fun activeAlerts(alerts: List<Alert>, now: Instant): List<Alert> = alerts
    .filter { a -> a.until.let { it == null || it.isAfter(now) } }
    .sortedWith(compareBy<Alert>({ it.severity.ordinal }, { it.onset ?: Instant.MIN }))

private fun timePhrase(a: Alert, now: Instant, zone: ZoneId): String {
    val onset = a.onset
    val until = a.until
    return when {
        onset != null && onset.isAfter(now) -> " from ${whenText(onset, now, zone)}"
        until != null -> " until ${whenText(until, now, zone)}"
        else -> ""
    }
}

private fun detailText(a: Alert, now: Instant, zone: ZoneId): String = buildString {
    append(a.event.uppercase(Locale.US))
    val onset = a.onset
    val until = a.until
    when {
        onset != null && onset.isAfter(now) && until != null ->
            append("\n${whenText(onset, now, zone)} to ${whenText(until, now, zone)}")
        until != null -> append("\nUntil ${whenText(until, now, zone)}")
    }
    a.description?.takeIf { it.isNotBlank() }?.let { append("\n\n").append(unwrap(it)) }
    a.instruction?.takeIf { it.isNotBlank() }?.let { append("\n\n").append(unwrap(it)) }
    a.senderName?.takeIf { it.isNotBlank() }?.let { append("\n\n").append(it.trim()) }
}

/** NWS text arrives wrapped at about 70 characters. Join wrapped lines; keep blank lines between paragraphs. */
internal fun unwrap(text: String): String =
    text.trim().replace("\r\n", "\n").replace(Regex("(?<!\n)\n(?!\n)"), " ")

private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val dayFmt = DateTimeFormatter.ofPattern("EEE", Locale.US)

/** "9 PM" today, "Wed 7 AM" on another day; minutes only when not on the hour ("9:30 PM"). */
internal fun whenText(t: Instant, now: Instant, zone: ZoneId): String {
    val local = t.atZone(zone)
    val time = timeFmt.format(local).replace(":00", "")
    return if (local.toLocalDate() == now.atZone(zone).toLocalDate()) time else "${dayFmt.format(local)} $time"
}
