package com.still.music

import com.still.music.playback.SearchRadio
import com.still.music.provider.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchRadioTest {
    private val seed = Track("youtube:seed", "Selected song", "Artist")

    @Test fun onlyPlayableDistinctRecommendationsAreDelivered() = runTest {
        val related = Track("youtube:related", "Related song", "Artist")
        val radio = SearchRadio(this) { requested ->
            assertEquals(seed, requested)
            listOf(seed, related, related, related.copy(id = "blocked", unavailableReason = "Unavailable"))
        }
        var queue = listOf(seed)
        radio.start(seed, { queue += it }, { fail("Unexpected failure") })
        assertTrue(radio.loading)
        advanceUntilIdle()
        assertEquals(listOf(seed, related), queue)
        assertFalse(radio.loading)
    }

    @Test fun replacedOrEditedQueueRejectsLateResultsEvenIfSourceIgnoresCancellation() = runTest {
        val radio = SearchRadio(this) { withContext(NonCancellable) { delay(1000); listOf(seed.copy(id = "late")) } }
        var deliveries = 0
        radio.start(seed, { deliveries++ }, { fail("Unexpected failure") })
        runCurrent()
        radio.cancel()
        advanceUntilIdle()
        assertEquals(0, deliveries)
        assertFalse(radio.loading)
    }

    @Test fun aSecondSelectionWinsAndFailureKeepsSelectedSong() = runTest {
        val newer = seed.copy(id = "new")
        val radio = SearchRadio(this) { requested ->
            if (requested == seed) withContext(NonCancellable) { delay(1000); listOf(seed.copy(id = "old-result")) }
            else listOf(newer.copy(id = "new-result"))
        }
        val delivered = mutableListOf<Track>()
        radio.start(seed, { delivered += it }, {})
        runCurrent()
        radio.start(newer, { delivered += it }, {})
        advanceUntilIdle()
        assertEquals(listOf("new-result"), delivered.map { it.id })
        var failed = false
        SearchRadio(this) { error("offline") }.start(seed, { fail("No raw search results may be substituted") }, { failed = true })
        advanceUntilIdle()
        assertTrue(failed)
    }
}
