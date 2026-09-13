package com.still.music

import com.still.music.offline.enqueuePlaylist
import com.still.music.provider.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PlaylistDownloadsTest {
    @Test fun skipsDuplicatesAndUnavailableAndContinuesAfterFailure() = runTest {
        val first = Track("one", "One", "Artist")
        val failed = Track("two", "Two", "Artist")
        val last = Track("three", "Three", "Artist")
        val attempted = mutableListOf<String>()
        val result = enqueuePlaylist(listOf(first, first, failed, last,
            Track("unavailable", "Unavailable", "", unavailableReason = "Gone"))) {
            attempted += it.id
            if (it == failed) error("Cannot enqueue")
        }
        assertEquals(listOf("one", "two", "three"), attempted)
        assertEquals(3, result.eligible)
        assertEquals(1, result.unavailable)
        assertEquals(1, result.failed)
    }
    @Test fun cancellationStopsTheBatch() = runTest {
        var calls = 0
        try {
            enqueuePlaylist(listOf(Track("one", "", ""), Track("two", "", ""))) {
                calls++
                throw CancellationException()
            }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertEquals(1, calls)
    }
}
