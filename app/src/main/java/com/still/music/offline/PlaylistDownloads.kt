package com.still.music.offline

import com.still.music.provider.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

data class PlaylistDownloadResult(val eligible: Int, val unavailable: Int, val failed: Int)

/** One failed song must not prevent the rest of the playlist from being queued. */
internal suspend fun enqueuePlaylist(tracks: List<Track>, enqueue: suspend (Track) -> Unit): PlaylistDownloadResult {
    val unique = tracks.distinctBy(Track::id)
    val playable = unique.filter(Track::playable)
    var failed = 0
    for (track in playable) {
        coroutineContext.ensureActive()
        try { enqueue(track) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { failed++ }
    }
    return PlaylistDownloadResult(playable.size, unique.size - playable.size, failed)
}
