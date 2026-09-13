package com.still.music

import com.still.music.ui.swipeDirection
import org.junit.Assert.*
import org.junit.Test

class PlayerSwipeTest {
    @Test fun smallDragsDoNotSkipAndDirectionsMatchPlayer() {
        assertEquals(0, swipeDirection(63f, 64f))
        assertEquals(0, swipeDirection(-63f, 64f))
        assertEquals(1, swipeDirection(-64f, 64f))
        assertEquals(-1, swipeDirection(64f, 64f))
        assertEquals(0, swipeDirection(0f, 64f))
    }
}
