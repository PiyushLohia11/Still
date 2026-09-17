package com.still.music

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.still.music.ui.StillTheme
import com.still.music.ui.SwipeActions
import com.still.music.ui.swipeVerticalAction
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SwipeActionsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun swipesTriggerOnceAndReturnForAnotherGesture() {
        var right = 0
        var left = 0
        compose.setContent { StillTheme {
            SwipeActions("song", "Queue", "Favourite", { right++ }, { left++ }) {
                Box(Modifier.fillMaxWidth().height(80.dp).testTag("row")) { Text("Song") }
            }
        } }
        compose.onNodeWithTag("row").performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, right); assertEquals(0, left) }
        compose.onNodeWithTag("row").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, right); assertEquals(1, left) }
    }

    @Test fun smallDragsAndVerticalScrollingDoNotTriggerRowActions() {
        var calls = 0
        compose.setContent { StillTheme {
            SwipeActions("song", "Queue", "Favourite", { calls++ }, { calls++ }) {
                Box(Modifier.fillMaxWidth().height(160.dp).testTag("row"))
            }
        } }
        compose.onNodeWithTag("row").performTouchInput { swipe(center, center + Offset(20f, 0f)) }
        compose.onNodeWithTag("row").performTouchInput { swipeUp() }
        compose.runOnIdle { assertEquals(0, calls) }
    }

    @Test fun disabledRowsIgnoreSwipesAndHandleOnlyAcceptsDownwardSwipe() {
        var calls = 0
        var closed = 0
        compose.setContent { StillTheme { Column {
            SwipeActions("song", "Queue", "Favourite", { calls++ }, { calls++ }, enabled = false) {
                Box(Modifier.fillMaxWidth().height(80.dp).testTag("row"))
            }
            Box(Modifier.fillMaxWidth().height(200.dp).testTag("handle")
                .swipeVerticalAction(up = false) { closed++ })
        } } }
        compose.onNodeWithTag("row").performTouchInput { swipeRight() }
        compose.onNodeWithTag("handle").performTouchInput { swipeUp() }
        compose.runOnIdle { assertEquals(0, calls); assertEquals(0, closed) }
        compose.onNodeWithTag("handle").performTouchInput { swipeDown() }
        compose.runOnIdle { assertEquals(1, closed) }
    }
}
