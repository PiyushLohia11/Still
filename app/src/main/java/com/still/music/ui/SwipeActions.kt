package com.still.music.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Actions return the row to its place; a swipe never deletes library content. */
@Composable internal fun SwipeActions(key: String, rightLabel: String, leftLabel: String,
    onRight: () -> Unit, onLeft: () -> Unit, enabled: Boolean = true, content: @Composable () -> Unit) {
    val right by rememberUpdatedState(onRight)
    val left by rememberUpdatedState(onLeft)
    val threshold = with(LocalDensity.current) { 88.dp.toPx() }
    val haptic = LocalHapticFeedback.current
    var drag by remember(key) { mutableFloatStateOf(0f) }
    var dragging by remember(key) { mutableStateOf(false) }
    val settled by animateFloatAsState(if (dragging) drag else 0f, label = "Swipe return")
    val offset = if (dragging) drag else settled
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
        .semantics {
            if (enabled) customActions = listOf(
                CustomAccessibilityAction(rightLabel) { right(); true },
                CustomAccessibilityAction(leftLabel) { left(); true })
        }
        .pointerInput(key, enabled, threshold) {
            if (!enabled) return@pointerInput
            detectHorizontalDragGestures(
                onDragStart = { drag = 0f; dragging = true },
                onDragCancel = { dragging = false; drag = 0f },
                onDragEnd = {
                    val direction = swipeDirection(drag, threshold)
                    dragging = false; drag = 0f
                    if (direction < 0) right() else if (direction > 0) left()
                },
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    val before = abs(drag) >= threshold
                    drag = (drag + amount).coerceIn(-threshold * 1.5f, threshold * 1.5f)
                    if (!before && abs(drag) >= threshold) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                })
        }) {
        if (abs(offset) > 1f) Box(Modifier.matchParentSize()
            .background(if (abs(drag) >= threshold) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 20.dp), contentAlignment = if (offset > 0) Alignment.CenterStart else Alignment.CenterEnd) {
            Text(if (offset > 0) rightLabel else leftLabel, style = MaterialTheme.typography.labelMedium)
        }
        Box(Modifier.graphicsLayer { translationX = offset }.background(MaterialTheme.colorScheme.background)) { content() }
    }
}

/** Attached to handles, not scrolling content, so list scrolling keeps its normal meaning. */
internal fun Modifier.swipeVerticalAction(up: Boolean, action: () -> Unit): Modifier = composed {
    val latest by rememberUpdatedState(action)
    val threshold = with(LocalDensity.current) { 56.dp.toPx() }
    var drag by remember { mutableFloatStateOf(0f) }
    pointerInput(up, threshold) {
        detectVerticalDragGestures(
            onDragStart = { drag = 0f }, onDragCancel = { drag = 0f },
            onDragEnd = {
                if ((up && drag <= -threshold) || (!up && drag >= threshold)) latest()
                drag = 0f
            },
            onVerticalDrag = { change, amount -> change.consume(); drag += amount })
    }
}
