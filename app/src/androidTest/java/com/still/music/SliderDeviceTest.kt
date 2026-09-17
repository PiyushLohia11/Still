package com.still.music

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.still.music.ui.SquigglySlider
import com.still.music.ui.StillTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SliderDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tappingAndDraggingCommitTheDisplayedPositionOnce() {
        var value by mutableFloatStateOf(0f)
        val commits = mutableListOf<Float>()
        compose.setContent { StillTheme {
            SquigglySlider(value, { value = it }, Modifier.testTag("seek"),
                onValueChangeFinished = { commits += value }, waveAmplitude = 0.dp)
        } }
        compose.onNodeWithTag("seek").performTouchInput { click(Offset(width * 0.5f, height * 0.5f)) }
        compose.runOnIdle { assertEquals(1, commits.size); assertEquals(0.5f, commits.last(), 0.03f) }
        compose.onNodeWithTag("seek").performTouchInput {
            swipe(Offset(width * 0.3f, height * 0.5f), Offset(width * 0.8f, height * 0.5f))
        }
        compose.runOnIdle { assertEquals(2, commits.size); assertEquals(0.8f, commits.last(), 0.03f) }
    }

    @Test fun cancelledTouchDoesNotSeek() {
        var commits = 0
        compose.setContent { StillTheme {
            SquigglySlider(0.2f, {}, Modifier.testTag("seek"),
                onValueChangeFinished = { commits++ }, waveAmplitude = 0.dp)
        } }
        compose.onNodeWithTag("seek").performTouchInput { down(center); cancel() }
        compose.runOnIdle { assertEquals(0, commits) }
    }
}
