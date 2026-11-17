package dev.corbyoliver.weather.data.pirate

import dev.corbyoliver.weather.data.AppJson
import dev.corbyoliver.weather.data.SourceException
import dev.corbyoliver.weather.data.getText
import dev.corbyoliver.weather.domain.HomeLocation
import dev.corbyoliver.weather.domain.PrecipKind
import dev.corbyoliver.weather.domain.RainMinute
import io.ktor.client.HttpClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.time.Instant

/*
 * Pirate Weather, checked against https://docs.pirateweather.net/en/latest/API/
 * on 2026-11-17. GET /forecast/{key}/{lat},{lon}. The "minutely" block holds
 * 60 one-minute points; time is UNIX seconds (start of the minute);
 * precipIntensity is in/hr with units=us; precipProbability is 0..1.
 * Free plan: 10,000 calls a month. 401 = bad key, 429 = monthly quota used.
 */

interface PirateSource {
    /** Throws [SourceException] on any failure. The message never contains the API key. */
    suspend fun forecastJson(): String
}

object PirateRequests {
    const val BASE = "https://api.pirateweather.net/forecast"

    /** Only the minutely block is needed; everything else comes from Open-Meteo. */
    fun forecastUrl(apiKey: String): String =
        "$BASE/$apiKey/${HomeLocation.LATITUDE},${HomeLocation.LONGITUDE}?units=us&version=2&exclude=currently,hourly,daily,alerts"
}

class PirateWeatherClient(private val http: HttpClient, private val apiKey: String) : PirateSource {
    override suspend fun forecastJson(): String = try {
        http.getText(PirateRequests.forecastUrl(apiKey), "Pirate Weather")
    } catch (e: SourceException) {
        // Drop the cause: network exceptions can quote the URL, which holds the key.
        throw SourceException(e.message ?: "Pirate Weather failed.", httpStatus = e.httpStatus)
    }
}

@Serializable
data class PwResponse(val minutely: PwMinutely? = null)

@Serializable
data class PwMinutely(val summary: String? = null, val data: List<PwMinute>? = null)

@Serializable
data class PwMinute(
    val time: Long,
    val precipIntensity: Double? = null,
    val precipProbability: Double? = null,
    val precipType: String? = null,
)

object PirateMapper {
    /** @throws SourceException if the body cannot be read or has no minute data. */
    fun map(json: String): List<RainMinute> {
        val r = try {
            AppJson.decodeFromString(PwResponse.serializer(), json)
        } catch (e: SerializationException) {
            throw SourceException("Pirate Weather sent data the app could not read.", cause = e)
        } catch (e: IllegalArgumentException) {
            throw SourceException("Pirate Weather sent data the app could not read.", cause = e)
        }
        val data = r.minutely?.data
        if (data.isNullOrEmpty()) throw SourceException("Pirate Weather sent no minute-by-minute data.")
        return data.map {
            RainMinute(
                time = Instant.ofEpochSecond(it.time),
                intensityInPerHr = it.precipIntensity,
                probability = it.precipProbability,
                kind = kindOf(it.precipType),
            )
        }.sortedBy { it.time }
    }

    fun kindOf(s: String?): PrecipKind? = when (s?.lowercase()) {
        null -> null
        "rain" -> PrecipKind.RAIN
        "snow" -> PrecipKind.SNOW
        "sleet", "ice" -> PrecipKind.SLEET
        "mixed" -> PrecipKind.MIXED
        "none" -> PrecipKind.NONE
        else -> null
    }
}
