@file:OptIn(androidx.media3.common.util.UnstableApi::class)
package com.still.music

import android.content.Context
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.test.core.app.ApplicationProvider
import com.still.music.playback.QueueRules
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class QueueTest {
    private fun withPlayer(block: (ExoPlayer) -> Unit) {
        val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext<Context>()).build()
        try {
            player.setMediaItems((0..3).map { MediaItem.Builder().setMediaId("audius:$it").setUri("https://example.org/$it.mp3").build() }, 1, 4200)
            block(player)
        } finally { player.release() }
    }
    @Test fun movingAndRemovingPreservesCurrentTrackAndPosition() = withPlayer { p ->
        p.moveMediaItem(1, 3)
        assertEquals("audius:1", p.currentMediaItem!!.mediaId)
        assertEquals(3, p.currentMediaItemIndex)
        assertEquals(4200L, p.currentPosition)
        p.removeMediaItem(0)
        assertEquals("audius:1", p.currentMediaItem!!.mediaId)
        p.removeMediaItem(p.currentMediaItemIndex)
        assertEquals(2, p.mediaItemCount)
        assertFalse(p.playWhenReady)
    }
    @Test fun repeatCycleAndAutomaticBoundarySemantics() = withPlayer { p ->
        assertEquals(2, QueueRules.nextRepeat(0)); assertEquals(1, QueueRules.nextRepeat(2)); assertEquals(0, QueueRules.nextRepeat(1))
        val timeline = p.currentTimeline
        assertEquals(C.INDEX_UNSET, timeline.getNextWindowIndex(3, Player.REPEAT_MODE_OFF, false))
        assertEquals(0, timeline.getNextWindowIndex(3, Player.REPEAT_MODE_ALL, false))
        assertEquals(3, timeline.getNextWindowIndex(3, Player.REPEAT_MODE_ONE, false))
    }
    @Test fun shuffleTraversesEveryItemOnceAndRepeatAllWraps() = withPlayer { p ->
        p.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(intArrayOf(2, 0, 3, 1), 42))
        p.shuffleModeEnabled = true
        val timeline = p.currentTimeline
        val visited = mutableListOf<Int>()
        var index = timeline.getFirstWindowIndex(true)
        while (index != C.INDEX_UNSET) { visited.add(index); index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, true) }
        assertEquals(listOf(2, 0, 3, 1), visited)
        assertEquals(2, timeline.getNextWindowIndex(1, Player.REPEAT_MODE_ALL, true))
        assertEquals("audius:1", p.currentMediaItem!!.mediaId)
    }
    @Test fun queueMoveValidatesBounds() {
        assertFalse(QueueRules.canMove(3, -1, 1)); assertFalse(QueueRules.canMove(3, 0, 3))
        assertFalse(QueueRules.canMove(3, 1, 1)); assertTrue(QueueRules.canMove(3, 0, 2))
    }
}
