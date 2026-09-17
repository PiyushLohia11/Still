package com.still.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.still.music.ui.FullScreenSheet
import com.still.music.ui.StillTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class OverlayDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun defaultAndTranslucentGradientsHaveOpaqueBasesAndDismissCorrectly() {
        var open by mutableStateOf(true)
        var colors by mutableStateOf<List<Color>?>(null)
        compose.setContent { StillTheme {
            Box(Modifier.fillMaxSize().background(Color.Magenta)) {
                Text("Underlying screen")
                if (open) FullScreenSheet("Test overlay", { open = false }, backgroundColors = colors) {
                    Text("Overlay content")
                }
            }
        } }
        val sheet = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Test overlay"))
        compose.waitForIdle()
        fun assertOpaque() {
            val pixel = sheet.captureToImage().toPixelMap()[5, 5]
            assertEquals(1f, pixel.alpha, 0.01f)
            assertTrue("Underlying magenta must not bleed through: $pixel", pixel.red < 0.3f && pixel.blue < 0.3f)
        }
        assertOpaque()
        compose.runOnIdle { colors = listOf(Color.White.copy(alpha = 0.05f), Color.Transparent) }
        assertOpaque()
        compose.onNodeWithContentDescription("Close Test overlay").performClick()
        compose.onNodeWithText("Overlay content").assertDoesNotExist()
        compose.onNodeWithText("Underlying screen").assertIsDisplayed()
    }
}
