package com.still.music.provider

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

data class Recommendations(val tracks: List<Track>, val subtitle: String)

/** Independent sources can fail without discarding the rest of the recommendations. */
class RecommendationLoader(
    private val related: suspend (Track) -> List<Track>,
    private val search: suspend (String) -> List<Track>
) {
    suspend fun load(history: List<Track>, favourites: List<Track>): Recommendations = coroutineScope {
        val seeds = (history.take(8) + favourites.shuffled().take(8))
            .filter { it.playable && it.id.startsWith("youtube:") }
            .distinctBy { it.artist.trim().lowercase(java.util.Locale.ROOT).ifBlank { it.id } }.take(3)
        suspend fun safely(block: suspend () -> List<Track>): List<Track> = try { block() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { emptyList() }
        val relatedTracks = seeds.map { seed -> async { safely { related(seed) } } }.awaitAll().flatten()
        val excluded = seeds.mapTo(hashSetOf(), Track::id)
        var pool = relatedTracks.filter { it.playable && it.id !in excluded }.distinctBy(Track::id)
        if (pool.size < 12) {
            val query = seeds.firstOrNull()?.artist?.takeIf(String::isNotBlank)?.let { "$it songs" }
                ?: listOf("indie pop songs", "popular music songs", "soul rnb songs").random()
            pool = (pool + safely { search(query) }).filter { it.playable && it.id !in excluded }.distinctBy(Track::id)
        }
        val known = (history + favourites).mapTo(hashSetOf(), Track::id)
        Recommendations(diverseRecommendations(pool, known), when {
            seeds.size > 1 -> "Inspired by " + seeds.take(2).joinToString(" & ") { it.artist } + " and your listening"
            seeds.size == 1 -> "Because you listened to " + seeds.first().title
            else -> "Fresh discoveries. Play and favourite songs to make this yours."
        })
    }
}

internal fun diverseRecommendations(pool: List<Track>, known: Set<String>, limit: Int = 18): List<Track> {
    val candidates = pool.filter(Track::playable).distinctBy(Track::id).shuffled()
        .sortedBy { it.id in known }
    val counts = mutableMapOf<String, Int>()
    // Keep each artist to two places so one related-video source cannot dominate.
    return candidates.filter { track ->
        val artist = track.artist.trim().lowercase(java.util.Locale.ROOT).ifBlank { track.id }
        val count = counts.getOrDefault(artist, 0)
        (count < 2).also { if (it) counts[artist] = count + 1 }
    }.take(limit)
}
