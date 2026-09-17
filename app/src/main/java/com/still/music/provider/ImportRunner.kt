package com.still.music.provider

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext

/** Bounded parallel lookup; commits and progress remain in source order. */
class ImportRunner(private val match: suspend (PlaylistImporter.Entry) -> Track?) {
    suspend fun run(entries: List<IndexedValue<PlaylistImporter.Entry>>,
        commit: suspend (List<Pair<Int, Track>>, List<IndexedValue<PlaylistImporter.Entry>>) -> Unit
    ) = coroutineScope {
        entries.chunked(3).forEach { batch ->
            val matches = batch.map { entry -> async {
                entry.index to withTimeoutOrNull(20_000) { match(entry.value) }
            } }.awaitAll()
            coroutineContext.ensureActive()
            commit(matches.mapNotNull { (index, track) -> track?.let { index to it } },
                batch.filter { entry -> matches.any { it.first == entry.index && it.second == null } })
        }
    }
}
