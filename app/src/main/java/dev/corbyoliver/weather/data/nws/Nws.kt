package dev.corbyoliver.weather.data.nws

import dev.corbyoliver.weather.data.AppJson
import dev.corbyoliver.weather.data.SourceException
import dev.corbyoliver.weather.data.getText
import dev.corbyoliver.weather.domain.Alert
import dev.corbyoliver.weather.domain.AlertSeverity
import dev.corbyoliver.weather.domain.HomeLocation
import io.ktor.client.HttpClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/*
 * NWS alerts API. Docs read 2026-11-17: https://www.weather.gov/documentation/services-web-api,
 * https://www.weather.gov/documentation/services-web-alerts and the API FAQ.
 * - A User-Agent is required (403 without it); contact details let NWS reach us.
 * - GET /alerts/active?point=lat,lon. Poll no more often than every 30 s (we poll
 *   every 15 min). Rate limits are not published; a blocked client should stop
 *   and wait. Errors are application/problem+json (title, detail).
 * The docs do not define the alert fields (they point to the CAP 1.2 spec), so
 * the shape below (GeoJSON FeatureCollection, properties.status / messageType /
 * onset / ends / expires / references) was confirmed from live responses captured
 * the same day (test fixtures nws-alerts-*-captured-2026-11-17.json).
 */

/** Where the raw NWS JSON comes from. */
interface NwsSource {
    /** Throws [SourceException] on any failure. */
    suspend fun activeAlertsJson(): String
}

object NwsRequests {
    const val BASE = "https://api.weather.gov"

    /** Used when local.properties has no nwsUserAgent. */
    const val FALLBACK_USER_AGENT = "(weather-app personal build)"

    fun activeAlertsUrl(): String = "$BASE/alerts/active?point=${HomeLocation.LATITUDE},${HomeLocation.LONGITUDE}"

    fun userAgent(configured: String): String = configured.trim().ifEmpty { FALLBACK_USER_AGENT }
}

class NwsClient(private val http: HttpClient, userAgent: String) : NwsSource {
    private val headers = mapOf("User-Agent" to NwsRequests.userAgent(userAgent))
    override suspend fun activeAlertsJson(): String =
        http.getText(NwsRequests.activeAlertsUrl(), "National Weather Service", headers)
}

@Serializable
data class NwsAlertCollection(val features: List<NwsFeature>? = null)

@Serializable
data class NwsFeature(val properties: NwsAlertProperties? = null)

@Serializable
data class NwsReference(val identifier: String? = null)

@Serializable
data class NwsAlertProperties(
    val id: String? = null,
    val event: String? = null,
    val headline: String? = null,
    val description: String? = null,
    val instruction: String? = null,
    val severity: String? = null,
    val status: String? = null,
    val messageType: String? = null,
    val onset: String? = null,
    val ends: String? = null,
    val expires: String? = null,
    val senderName: String? = null,
    val references: List<NwsReference>? = null,
)

/** Pure: raw NWS JSON in, domain alerts out. */
object NwsMapper {
    /**
     * @throws SourceException if the body cannot be read, or is not an alert
     *   collection. Never returns "no alerts" for a body it could not read.
     */
    fun map(json: String): List<Alert> {
        val collection = try {
            AppJson.decodeFromString(NwsAlertCollection.serializer(), json)
        } catch (e: SerializationException) {
            throw SourceException("National Weather Service sent data the app could not read.", cause = e)
        } catch (e: IllegalArgumentException) {
            throw SourceException("National Weather Service sent data the app could not read.", cause = e)
        }
        val features = collection.features
            ?: throw SourceException("National Weather Service sent no alert list.")
        val props = features.mapNotNull { it.properties }
        // An update lists the alerts it replaces; never show both.
        val replaced = props.flatMap { p -> p.references.orEmpty().mapNotNull { it.identifier } }.toSet()
        return props
            .filter { it.status.equals("Actual", ignoreCase = true) } // not Test, Exercise or Draft
            .filter { !it.messageType.equals("Cancel", ignoreCase = true) }
            .filter { it.id != null && it.id !in replaced }
            .map { p ->
                Alert(
                    id = p.id!!,
                    event = p.event?.takeIf { it.isNotBlank() } ?: "Weather alert",
                    severity = severityOf(p.severity),
                    onset = instantOrNull(p.onset),
                    ends = instantOrNull(p.ends),
                    expires = instantOrNull(p.expires),
                    headline = p.headline,
                    description = p.description,
                    instruction = p.instruction,
                    senderName = p.senderName,
                )
            }
            .distinctBy { it.id }
    }

    fun severityOf(s: String?): AlertSeverity = when (s?.lowercase()) {
        "extreme" -> AlertSeverity.EXTREME
        "severe" -> AlertSeverity.SEVERE
        "moderate" -> AlertSeverity.MODERATE
        "minor" -> AlertSeverity.MINOR
        else -> AlertSeverity.UNKNOWN
    }

    private fun instantOrNull(s: String?): Instant? = s?.let {
        try {
            OffsetDateTime.parse(it).toInstant()
        } catch (e: DateTimeParseException) {
            null
        }
    }
}
