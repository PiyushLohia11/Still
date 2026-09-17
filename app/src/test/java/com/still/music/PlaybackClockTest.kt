package com.still.music

import com.still.music.ui.PlaybackClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackClockTest {
    @Test fun pollsOnlyWhileProgressIsObservedAndResumesWithCurrentPosition() = runTest {
        var reads = 0
        var current = 100L
        val clock = PlaybackClock(backgroundScope) { reads++; current }
        runCurrent()
        advanceTimeBy(2_000)
        assertEquals(0, reads)
        val observer = backgroundScope.launch { clock.position.collect() }
        runCurrent()
        assertEquals(100L, clock.position.value)
        current = 600
        advanceTimeBy(500); runCurrent()
        assertEquals(600L, clock.position.value)
        observer.cancel(); runCurrent()
        val stoppedAt = reads
        advanceTimeBy(5_000); runCurrent()
        assertEquals(stoppedAt, reads)
        current = 9_000
        backgroundScope.launch { clock.position.collect() }
        runCurrent()
        assertEquals(9_000L, clock.position.value)
    }

    @Test fun seekAndTrackChangeRefreshImmediatelyWithoutWaitingForTick() = runTest {
        var current = 30_000L
        val clock = PlaybackClock(backgroundScope) { current }
        clock.refresh()
        assertEquals(30_000L, clock.position.value)
        current = -1
        clock.refresh()
        assertEquals(0L, clock.position.value)
    }
}
