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
import kotlinx.coroutines.CancellationException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
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
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw SourceException(unreachableMessage(source, e), cause = e)
    }
    // The body can fail too (connection dropped mid-download): same handling.
    val body = try {
        response.bodyAsText()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw SourceException(unreachableMessage(source, e), cause = e)
    }
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

/** Plain words for a request that never got an answer. Safe to show on screen. */
internal fun unreachableMessage(source: String, e: Throwable): String {
    val causes = generateSequence(e) { it.cause }.take(8).toList()
    return when {
        causes.any { it is UnknownHostException || it is NoRouteToHostException || it is ConnectException } ->
            "No internet connection."
        causes.any { it is SocketTimeoutException || it.javaClass.simpleName.contains("Timeout") } ->
            "$source did not answer in time."
        else -> "$source could not be reached."
    }
}
