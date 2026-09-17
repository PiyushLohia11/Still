package com.still.music

import com.still.music.provider.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImportRunnerTest {
    private val entries = List(8) { PlaylistImporter.Entry("$it", "Artist") }.withIndex().toList()

    @Test fun concurrentMatchingCommitsInSourceOrderAndReportsFailures() = runTest {
        var active = 0
        var peak = 0
        val committed = mutableListOf<Int>()
        val failed = mutableListOf<Int>()
        ImportRunner { entry ->
            active++; peak = maxOf(peak, active)
            delay((9 - entry.title.toInt()) * 100L)
            active--
            if (entry.title == "4") null else Track(entry.title, entry.title, entry.artist)
        }.run(entries) { tracks, unmatched ->
            committed += tracks.map { it.first }
            failed += unmatched.map { it.index }
        }
        assertEquals(3, peak)
        assertEquals(listOf(0, 1, 2, 3, 5, 6, 7), committed)
        assertEquals(listOf(4), failed)
    }

    @Test fun cancellationKeepsCompletedBatchAndDoesNotCommitIncompleteBatch() = runTest {
        val committed = mutableListOf<Int>()
        val job = launch {
            ImportRunner { entry ->
                if (entry.title.toInt() >= 3) delay(10_000)
                Track(entry.title, entry.title, entry.artist)
            }.run(entries) { tracks, _ -> committed += tracks.map { it.first } }
        }
        runCurrent()
        assertEquals(listOf(0, 1, 2), committed)
        job.cancelAndJoin()
        advanceUntilIdle()
        assertEquals(listOf(0, 1, 2), committed)
    }

    @Test fun timeoutIsRetryableWithoutBlockingOtherSongs() = runTest {
        val unmatched = mutableListOf<Int>()
        ImportRunner { entry ->
            if (entry.title == "0") delay(30_000)
            Track(entry.title, entry.title, entry.artist)
        }.run(entries.take(3)) { tracks, failures ->
            assertEquals(listOf(1, 2), tracks.map { it.first })
            unmatched += failures.map { it.index }
        }
        assertEquals(listOf(0), unmatched)
    }
}
