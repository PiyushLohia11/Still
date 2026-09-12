package com.still.music

import com.still.music.data.*
import com.still.music.provider.Track
import org.junit.Assert.*
import org.junit.Test

class SnapshotTest {
    @Test fun roundTripsQueuePositionAndModesWithoutAudioUrls() {
        val value = QueueSnapshot(listOf(Track("audius:a", "One", "Artist"), Track("audius:b", "Two", "Artist")),
            1, 32100, true, 2, listOf(1, 0))
        assertEquals(value, SnapshotCodec.decode(SnapshotCodec.encode(value)))
        assertFalse(SnapshotCodec.encode(value).contains("playWhenReady"))
        assertFalse(SnapshotCodec.encode(value).contains("signed"))
    }
    @Test fun corruptSnapshotFallsBackToEmptyQueue() {
        assertEquals(QueueSnapshot(), SnapshotCodec.decode("broken"))
        assertEquals(QueueSnapshot(), SnapshotCodec.decode(null))
    }
    @Test fun clampsInvalidPositionIndexAndShufflePermutation() {
        val source = QueueSnapshot(listOf(Track("audius:a", "A", "B")), 40, -100, true, 8, listOf(9, 9))
        val restored = SnapshotCodec.decode(SnapshotCodec.encode(source))
        assertEquals(0, restored.index)
        assertEquals(0L, restored.positionMs)
        assertEquals(0, restored.repeat)
        assertEquals(listOf(0), restored.shuffleOrder)
    }
}
