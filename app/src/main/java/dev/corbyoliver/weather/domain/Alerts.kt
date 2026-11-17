package dev.corbyoliver.weather.domain

import java.time.Instant

/** How serious an alert is, as NWS (CAP) rates it. Ordered most serious first. */
enum class AlertSeverity { EXTREME, SEVERE, MODERATE, MINOR, UNKNOWN }

/** One official weather alert (watch, warning or advisory) for the home location. */
data class Alert(
    val id: String,
    /** Short name, for example "Wind Advisory". */
    val event: String,
    val severity: AlertSeverity,
    /** When the weather itself starts; null if not given. */
    val onset: Instant?,
    /** When the weather itself ends; null if not given. */
    val ends: Instant?,
    /** When this alert message stops being valid. */
    val expires: Instant?,
    val headline: String?,
    val description: String?,
    val instruction: String?,
    val senderName: String?,
) {
    /** The time to show as "until": the event end, or the message expiry if NWS gave no end. */
    val until: Instant? get() = ends ?: expires
}
