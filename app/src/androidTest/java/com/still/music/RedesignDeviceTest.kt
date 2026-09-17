package com.still.music

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class RedesignDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun capture(name: String) {
        compose.waitForIdle()
        assertTrue(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(
            File(compose.activity.getExternalFilesDir(null), "$name.png")))
    }

    @Test fun preferencesEqualizerImportAndLibraryNavigation() {
        capture("redesign-home")
        compose.onNodeWithContentDescription("Preferences").performClick()
        compose.onNodeWithText("Your listening space.").assertIsDisplayed()
        capture("redesign-preferences")
        compose.onNodeWithText("Open equalizer").performScrollTo().performClick()
        compose.onNodeWithText("Shape your sound.").assertIsDisplayed()
        capture("redesign-equalizer")
        compose.onNodeWithContentDescription("Close Equalizer").performClick()
        compose.onNodeWithText("Your listening space.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close Preferences").performClick()
        compose.onNodeWithText("Search", substring = false).performClick()
        compose.onNodeWithText("Search music").assertIsDisplayed()
        capture("redesign-search")
        compose.onNodeWithText("Library", substring = false).performClick()
        capture("redesign-library")
        compose.onNodeWithText("Import", substring = false).performClick()
        compose.onNodeWithText("Playlist link or song list").performTextInput("Title,Artist\nOne,U2\nYellow,Coldplay")
        compose.onNodeWithText("Preview songs").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("2 songs found").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Playlist name").performScrollTo().performTextInput("Preview only")
        compose.onNodeWithText("Import 2 songs").performScrollTo().assertIsEnabled()
        capture("redesign-import")
        compose.onNodeWithContentDescription("Close Import playlist").performClick()
        compose.onNodeWithText("Downloads", substring = false).performClick()
        compose.onNodeWithText("Ready when you are.").assertExists()
        capture("redesign-downloads")
    }

    @Test fun playerQueueAndEqualizerShareOneRouteInPortraitAndLandscape() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = compose.activity.application as StillApp
        lateinit var future: com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.MediaController>
        instrumentation.runOnMainSync {
            future = androidx.media3.session.MediaController.Builder(app, androidx.media3.session.SessionToken(app,
                android.content.ComponentName(app, com.still.music.playback.PlaybackService::class.java))).buildAsync()
        }
        val controller = future.get(15, java.util.concurrent.TimeUnit.SECONDS)
        val device = UiDevice.getInstance(instrumentation)
        val original = mutableListOf<androidx.media3.common.MediaItem>()
        var originalIndex = 0
        var originalPosition = 0L
        compose.waitUntil(15_000) { app.graph.playbackReady.value }
        instrumentation.runOnMainSync {
            repeat(controller.mediaItemCount) { original += controller.getMediaItemAt(it) }
            originalIndex = controller.currentMediaItemIndex.coerceAtLeast(0)
            originalPosition = controller.currentPosition
            controller.pause()
            controller.setMediaItems(List(3) { index -> androidx.media3.common.MediaItem.Builder()
                .setMediaId("audius:design$index").setUri("still://track/design$index")
                .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder()
                    .setTitle("Design check $index").setArtist("Still studio")
                    .setExtras(android.os.Bundle().apply { putLong("durationMs", 180_000) }).build()).build()
            })
        }
        try {
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Design check 0").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Design check 0").performClick()
            compose.onNodeWithText("NOW PLAYING").assertIsDisplayed()
            capture("redesign-player")
            compose.onNodeWithContentDescription("Queue", substring = false).performScrollTo().performClick()
            compose.onNodeWithText("Playing Next").assertIsDisplayed()
            capture("redesign-queue")
            compose.onAllNodesWithContentDescription("Close Queue").onFirst().performClick()
            compose.onNodeWithText("NOW PLAYING").assertIsDisplayed()
            compose.onNodeWithContentDescription("Options for Design check 0").performScrollTo().performClick()
            compose.onNodeWithText("Equalizer", substring = false).performClick()
            compose.onNodeWithText("Shape your sound.").assertIsDisplayed()
            compose.onNodeWithContentDescription("Close Equalizer").performClick()
            device.setOrientationLeft()
            compose.onNodeWithContentDescription("Queue", substring = false).performScrollTo().assertIsDisplayed()
            capture("redesign-player-landscape")
            compose.onNodeWithContentDescription("Close Now playing").performScrollTo().performClick()
        } finally {
            device.setOrientationNatural()
            device.unfreezeRotation()
            instrumentation.runOnMainSync {
                if (original.isEmpty()) controller.clearMediaItems()
                else controller.setMediaItems(original, originalIndex, originalPosition)
                androidx.media3.session.MediaController.releaseFuture(future)
            }
        }
    }
}
