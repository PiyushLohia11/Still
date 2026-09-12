package com.still.music.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AudiusProvider(
    private val client: OkHttpClient,
    private val apiKey: String = "",
    private val baseUrl: String = "https://api.audius.co/v1/"
) : MusicProvider {
    override val id = "audius"
    private fun url(path: String, params: Map<String, String> = emptyMap()): HttpUrl =
        (baseUrl + path).toHttpUrl().newBuilder().apply {
            addQueryParameter("app_name", "Still")
            if (apiKey.isNotBlank()) addQueryParameter("api_key", apiKey)
            params.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()

    private suspend fun request(path: String, params: Map<String, String> = emptyMap()): JsonElement =
        withContext(Dispatchers.IO) {
            val call = client.newCall(Request.Builder().url(url(path, params)).build())
            val body = suspendCancellableCoroutine<String> { continuation ->
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            try {
                                if (!it.isSuccessful) throw ProviderException(when (it.code) {
                                    401, 403 -> "Audius does not allow access to this track."
                                    404 -> "This track is no longer available."
                                    429 -> "Audius is busy. Please try again shortly."
                                    else -> "Audius could not respond (HTTP ${it.code})."
                                }, it.code)
                                val text = it.body.string()
                                if (continuation.isActive) continuation.resume(text)
                            } catch (e: Exception) {
                                if (continuation.isActive) continuation.resumeWithException(e)
                            }
                        }
                    }
                })
            }
            try { Json.parseToJsonElement(body).jsonObject["data"] ?: throw IllegalArgumentException() }
            catch (e: Exception) { throw ProviderException("Audius returned an unreadable response.") }
        }

    override suspend fun search(query: String, offset: Int): List<Track> {
        if (query.isBlank()) return emptyList()
        val data = request("tracks/search", mapOf("query" to query.trim(), "limit" to "30", "offset" to offset.toString()))
        val array = data as? JsonArray ?: throw ProviderException("Audius returned an unreadable track list.")
        return array.mapNotNull { (it as? JsonObject)?.let { row -> AudiusParser.track(row, apiKey) } }
    }
    private fun rawId(qualifiedId: String): String {
        val raw = qualifiedId.removePrefix("audius:")
        if (!qualifiedId.startsWith("audius:") || !raw.matches(Regex("[a-zA-Z0-9]+"))) {
            throw ProviderException("Invalid Audius track identifier.")
        }
        return raw
    }
    override suspend fun trackDetails(qualifiedId: String): Track {
        val data = request("tracks/${rawId(qualifiedId)}") as? JsonObject
            ?: throw ProviderException("Track details are unavailable.")
        return AudiusParser.track(data, apiKey) ?: throw ProviderException("Track details are incomplete.")
    }
    override suspend fun resolvePlayback(qualifiedId: String): PlaybackAccess {
        val track = trackDetails(qualifiedId)
        track.unavailableReason?.let { throw ProviderException(it, 403) }
        val data = request("tracks/${rawId(qualifiedId)}/stream", mapOf("no_redirect" to "true"))
        val raw = when (data) {
            is JsonPrimitive -> data.contentOrNull
            is JsonObject -> data.string("url")
            else -> null
        }
        val stream = raw?.let { runCatching { it.toHttpUrl() }.getOrNull() }
        if (stream == null || !stream.isHttps) throw ProviderException("Audius did not provide a secure playback URL.")
        return PlaybackAccess(stream.toString())
    }
}
internal fun JsonObject.string(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull
internal fun JsonObject.boolean(key: String) = (get(key) as? JsonPrimitive)?.booleanOrNull
object AudiusParser {
    fun track(row: JsonObject, apiKey: String = ""): Track? {
        val id = row.string("id")?.takeIf { it.matches(Regex("[A-Za-z0-9]+")) } ?: return null
        val title = row.string("title")?.takeIf { it.isNotBlank() } ?: return null
        val user = row["user"] as? JsonObject
        val artist = user?.string("name") ?: user?.string("handle") ?: return null
        val allowed = (row["allowed_api_keys"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
        val access = row["access"] as? JsonObject
        val reason = when {
            row.boolean("is_delete") == true || row.boolean("is_available") == false -> "No longer available on Audius"
            row.boolean("is_streamable") == false -> "Streaming is unavailable"
            row.boolean("is_stream_gated") == true -> "Requires access on Audius"
            (row["access_authorities"] as? JsonArray)?.isNotEmpty() == true -> "Requires provider authorization"
            allowed.isNotEmpty() && apiKey !in allowed -> "Unavailable to this application"
            access?.boolean("stream") == false -> "Audius has restricted this track"
            else -> null
        }
        val artwork = row["artwork"] as? JsonObject
        return Track("audius:$id", title.trim(), artist,
            (artwork?.string("480x480") ?: artwork?.string("150x150"))?.takeIf { it.startsWith("https://") },
            ((row["duration"] as? JsonPrimitive)?.doubleOrNull?.times(1000)?.toLong() ?: 0).coerceAtLeast(0), reason)
    }
}
