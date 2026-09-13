package com.still.music

import com.still.music.provider.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RecommendationsTest {
    private fun track(id: String, artist: String = id) = Track("youtube:" + id, id, artist)
    @Test fun blendsSourcesAndKeepsWorkingWhenOneFails() = runTest {
        val seeds = listOf(track("seed1"), track("seed2"), track("seed3"))
        val called = mutableListOf<String>()
        val loader = RecommendationLoader(related = { seed ->
            called += seed.id
            if (seed == seeds[1]) error("Offline source")
            listOf(seed, track("shared"), track(seed.title + "-new"))
        }, search = { listOf(track("fallback")) })
        val result = loader.load(seeds, emptyList())
        assertEquals(3, called.size)
        assertTrue(result.tracks.any { it.title == "fallback" })
        assertTrue(result.tracks.any { it.title == "seed1-new" })
        assertTrue(result.tracks.any { it.title == "seed3-new" })
        assertFalse(result.tracks.any { it in seeds })
        assertEquals(result.tracks.size, result.tracks.distinctBy(Track::id).size)
    }

    @Test fun favoursDiscoveryAndLimitsArtistRepetition() {
        val pool = (0..8).map { track("same" + it, "Same artist") } +
            (0..8).map { track("fresh" + it) } + track("known") +
            track("blocked").copy(unavailableReason = "Unavailable")
        val result = diverseRecommendations(pool + pool, setOf("youtube:known"), 10)
        assertEquals(10, result.size)
        assertTrue(result.count { it.artist == "Same artist" } <= 2)
        assertFalse(result.any { it.title == "known" || !it.playable })
        assertEquals(result.size, result.distinctBy(Track::id).size)
    }

    @Test fun newListenerGetsSearchDiscoveries() = runTest {
        var query = ""
        val result = RecommendationLoader(related = { error("No seeds expected") },
            search = { query = it; listOf(track("discovery")) }).load(emptyList(), emptyList())
        assertTrue(query.isNotBlank())
        assertEquals("discovery", result.tracks.single().title)
    }

    @Test fun cancellationIsNotConvertedToEmptyRecommendations() = runTest {
        try {
            RecommendationLoader(related = { throw CancellationException() },
                search = { emptyList() }).load(listOf(track("seed")), emptyList())
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }
}
