package dev.corbyoliver.weather.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.TimeUnit

/** A download failed. [message] is safe to show on screen. */
class SourceException(message: String, val httpStatus: Int? = null, cause: Throwable? = null) : Exception(message, cause)

val AppJson = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

fun newHttpClient(): HttpClient = HttpClient(OkHttp) {
    expectSuccess = false
    engine {
        config {
            connectTimeout(15, TimeUnit.SECONDS)
            readTimeout(20, TimeUnit.SECONDS)
        }
    }
}

/**
 * GETs [url] and returns the body text. Non-2xx responses become a
 * [SourceException] carrying the service's own reason when it sends one.
 */
suspend fun HttpClient.getText(url: String, source: String, headers: Map<String, String> = emptyMap()): String {
    val response = try {
        get(url) {
            headers.forEach { (k, v) -> header(k, v) }
            header(HttpHeaders.Accept, "application/json, application/geo+json")
        }
    } catch (e: Exception) {
        throw SourceException("$source could not be reached (${e.javaClass.simpleName}).", cause = e)
    }
    val body = response.bodyAsText()
    if (response.status.isSuccess()) return body
    val code = response.status.value
    val reason = runCatching {
        val obj = AppJson.parseToJsonElement(body).jsonObject
        (obj["reason"] ?: obj["detail"] ?: obj["title"] ?: obj["message"])?.jsonPrimitive?.content
    }.getOrNull()
    val text = when {
        code == 429 -> "$source is limiting requests from this network (HTTP 429). It will try again later."
        reason != null -> "$source error (HTTP $code): $reason"
        else -> "$source error (HTTP $code)."
    }
    throw SourceException(text, httpStatus = code)
}
