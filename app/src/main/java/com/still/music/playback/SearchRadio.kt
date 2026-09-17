package com.still.music.playback

import com.still.music.provider.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Invalidated by explicit queue edits so slow recommendations cannot replace user intent. */
class SearchRadio(private val scope: CoroutineScope,
    private val related: suspend (Track) -> List<Track>) {
    private var generation = 0L
    private var job: Job? = null
    var loading = false
        private set

    fun cancel() { generation++; job?.cancel(); job = null; loading = false }

    fun start(seed: Track, deliver: (List<Track>) -> Unit, failed: () -> Unit) {
        cancel()
        val request = generation
        loading = true
        job = scope.launch {
            try {
                val tracks = related(seed).filter { it.playable && it.id != seed.id }
                    .distinctBy(Track::id).take(20)
                if (generation == request) deliver(tracks)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (generation == request) failed() }
            finally { if (generation == request) loading = false }
        }
    }
}
