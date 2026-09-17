package com.still.music

import com.still.music.provider.Track
import com.still.music.ui.playlistSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistPageTest {
    @Test fun summaryHandlesEmptyUnknownAndLongDurations() {
        assertEquals("0 songs", playlistSummary(emptyList()))
        assertEquals("1 song", playlistSummary(listOf(Track("1", "Song", "Artist", durationMs = -1))))
        assertEquals("2 songs · 1 hr 5 min", playlistSummary(listOf(
            Track("1", "One", "Artist", durationMs = 3_600_000),
            Track("2", "Two", "Artist", durationMs = 300_000))))
    }
}
