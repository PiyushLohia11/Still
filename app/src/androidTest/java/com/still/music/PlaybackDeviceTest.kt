package com.still.music

import android.content.ComponentName
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.common.util.concurrent.ListenableFuture
import com.still.music.playback.PlaybackService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** Live Audius test: no fixtures or substituted audio. Requires internet and public track availability. */
@RunWith(AndroidJUnit4::class)
class PlaybackDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun <T> main(block: () -> T): T {
        var result: T? = null
        instrumentation.runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST") return result as T
    }
    private fun await(timeoutMs: Long = 60000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (main(condition)) return
            SystemClock.sleep(200)
        }
        assertTrue("Condition not met within $timeoutMs ms", main(condition))
    }
    @Test fun realSearchPlaybackSystemControlsAndTransition() {
        val app = compose.activity.application as StillApp
        val tracks = runBlocking { app.graph.audius.search("ambient").filter { it.playable } }
        assertTrue("Need at least two public Audius tracks", tracks.size >= 2)
        val first = tracks.first()
        compose.onNodeWithText("Search Audius").performTextInput("ambient")
        compose.waitUntil(60000) { compose.onAllNodesWithText(first.title).fetchSemanticsNodes().isNotEmpty() }
        lateinit var future: ListenableFuture<MediaController>
        main { future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync() }
        val controller = future.get(20, TimeUnit.SECONDS)
        val device = UiDevice.getInstance(instrumentation)
        try {
            await { app.graph.playbackReady.value }
            compose.onAllNodesWithText(first.title).onFirst().performClick()
            await { controller.isPlaying && controller.currentPosition > 1500 }
            assertEquals(first.id, main { controller.currentMediaItem!!.mediaId })
            assertTrue(main { controller.mediaItemCount >= 2 })
            main { controller.pause() }
            await { !controller.isPlaying }
            val paused = main { controller.currentPosition }
            SystemClock.sleep(700)
            assertTrue(kotlin.math.abs(main { controller.currentPosition } - paused) < 200)
            main { controller.seekTo(10000); controller.play() }
            await { controller.isPlaying && controller.currentPosition >= 10500 }

            device.pressHome()
            val background = main { controller.currentPosition }
            await(15000) { controller.currentPosition > background + 1500 && controller.isPlaying }
            device.sleep()
            val locked = main { controller.currentPosition }
            await(15000) { controller.currentPosition > locked + 1500 && controller.isPlaying }

            device.executeShellCommand("input keyevent 127")
            await(10000) { !controller.playWhenReady }
            device.executeShellCommand("input keyevent 126")
            await(15000) { controller.isPlaying }

            device.wakeUp()
            device.executeShellCommand("wm dismiss-keyguard")
            val manager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
                .setOnAudioFocusChangeListener({ }, android.os.Handler(android.os.Looper.getMainLooper())).build()
            val granted = main { manager.requestAudioFocus(request) }
            assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, granted)
            await(10000) { !controller.isPlaying }
            main { manager.abandonAudioFocusRequest(request) }
            await(15000) { controller.isPlaying }

            main {
                controller.shuffleModeEnabled = false
                controller.repeatMode = Player.REPEAT_MODE_OFF
                controller.seekTo(controller.duration - 1800)
            }
            await(60000) { controller.currentMediaItemIndex == 1 && controller.isPlaying && controller.currentPosition > 300 }
            main { controller.pause() }
            val recents = runBlocking { app.graph.library.recents.first() }
            // Only tracks that really started playing should be recorded.
            assertTrue(recents.any { it.id == first.id })
            device.executeShellCommand("am start -n com.still.music/.MainActivity")
        } finally {
            main { controller.pause(); controller.release() }
            device.wakeUp()
        }
    }
}
