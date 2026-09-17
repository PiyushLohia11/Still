package com.still.music

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.still.music.provider.Track
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class LibraryDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun playlistFilterRenameDeleteAndNavigation() {
        val app = compose.activity.application as StillApp
        val name = "Still device check ${System.currentTimeMillis()}"
        val tracks = List(30) { index -> Track("test:$name:$index", "Check song $index", "Test artist",
            durationMs = 180_000, unavailableReason = "UI test only") }
        val id = runBlocking {
            app.graph.library.create(name).also { playlist -> tracks.forEach { app.graph.library.add(playlist, it) } }
        }
        try {
            compose.onNodeWithText("Library").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(name).performScrollTo().performClick()
            compose.onNodeWithText("30 songs · 1 hr 30 min").assertExists()
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            assertTrue(device.takeScreenshot(File(app.getExternalFilesDir(null), "playlist-device-check.png")))
            compose.onNodeWithText("Find in playlist").performScrollTo().performTextInput("Check song 29")
            compose.onNode(hasText("Check song 29") and !hasSetTextAction()).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Check song 0").assertDoesNotExist()
            compose.onNodeWithContentDescription("Clear filter").performScrollTo().performClick()
            compose.onNodeWithText("Check song 0").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("Playlist options").performScrollTo().performClick()
            compose.onNodeWithText("Rename playlist").performClick()
            compose.onNodeWithText("Playlist name").performTextReplacement("$name renamed")
            compose.onNodeWithText("Save").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("$name renamed").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Search").performClick()
            compose.onNodeWithText("Search music").assertExists()
            compose.onNodeWithContentDescription("Preferences").assertExists()
            compose.onNodeWithText("Library").performClick()
            compose.onNodeWithContentDescription("Playlist options").performScrollTo().performClick()
            compose.onNodeWithText("Delete playlist").performClick()
            compose.onNodeWithText("Delete", substring = false).performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Your playlists").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Preferences").assertExists()
        } finally {
            runBlocking { app.graph.library.delete(id) }
            tracks.forEach { app.graph.database.openHelper.writableDatabase.execSQL("DELETE FROM tracks WHERE id = ?", arrayOf(it.id)) }
        }
    }
}

