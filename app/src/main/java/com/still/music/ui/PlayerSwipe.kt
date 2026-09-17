package com.still.music.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Horizontal gestures leave vertical scrolling and taps to their existing handlers. */
internal fun Modifier.swipeTracks(trackId: String, enabled: Boolean,
    onPrevious: () -> Unit, onNext: () -> Unit): Modifier = composed {
    val previous by rememberUpdatedState(onPrevious)
    val next by rememberUpdatedState(onNext)
    val haptic = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    var distance by remember(trackId) { mutableFloatStateOf(0f) }
    val offset by animateFloatAsState(distance * 0.25f, label = "Song swipe")
    this.graphicsLayer { translationX = offset }
        .semantics {
            if (enabled) customActions = listOf(
                CustomAccessibilityAction("Previous song") { previous(); true },
                CustomAccessibilityAction("Next song") { next(); true })
        }
        .pointerInput(trackId, enabled, threshold) {
            if (!enabled) return@pointerInput
            detectHorizontalDragGestures(
                onDragStart = { distance = 0f },
                onDragCancel = { distance = 0f },
                onDragEnd = {
                    val direction = swipeDirection(distance, threshold)
                    distance = 0f
                    if (direction > 0) next() else if (direction < 0) previous()
                },
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    val before = kotlin.math.abs(distance) >= threshold
                    distance = (distance + amount).coerceIn(-threshold * 2f, threshold * 2f)
                    if (!before && kotlin.math.abs(distance) >= threshold)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                })
        }
}

internal fun swipeDirection(distance: Float, threshold: Float): Int = when {
    distance <= -threshold -> 1
    distance >= threshold -> -1
    else -> 0
}
