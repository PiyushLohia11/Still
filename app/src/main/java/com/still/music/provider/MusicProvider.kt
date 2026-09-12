package com.still.music.provider

import java.io.IOException

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val artwork: String? = null,
    val durationMs: Long = 0,
    val unavailableReason: String? = null
) {
    val playable: Boolean get() = unavailableReason == null
}
data class PlaybackAccess(val url: String)
interface MusicProvider {
    val id: String
    suspend fun search(query: String, offset: Int = 0): List<Track>
    suspend fun trackDetails(qualifiedId: String): Track
    suspend fun resolvePlayback(qualifiedId: String): PlaybackAccess
}
class ProviderException(message: String, val status: Int = 0) : IOException(message)
class ProviderRegistry(private vararg val providers: MusicProvider) {
    fun forTrack(id: String): MusicProvider = providers.firstOrNull { id.startsWith("${it.id}:") }
        ?: throw ProviderException("This music provider is not supported.")
}
