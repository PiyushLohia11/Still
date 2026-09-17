package com.still.music.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

/**
 * An expressive wavy / squiggly seek bar for audio playback.
 * The played portion features a fluid sine wave that animates phase during playback,
 * tapering smoothly into the thumb position, while the unplayed portion is a clean line.
 */
@Composable
fun SquigglySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    isPlaying: Boolean = false,
    enabled: Boolean = true,
    activeTrackColor: Color = Color.White,
    inactiveTrackColor: Color = Color.White.copy(alpha = 0.28f),
    thumbColor: Color = Color.White,
    waveLength: Dp = 26.dp,
    waveAmplitude: Dp = 4.5.dp,
    trackStrokeWidth: Dp = 3.5.dp,
    thumbRadius: Dp = 8.dp,
    formatTime: ((Long) -> String)? = null,
    accessibilityLabel: String = "Playback position"
) {
    val density = LocalDensity.current
    val waveLengthPx = with(density) { waveLength.toPx() }
    val maxAmplitudePx = with(density) { waveAmplitude.toPx() }
    val strokeWidthPx = with(density) { trackStrokeWidth.toPx() }
    val baseThumbRadiusPx = with(density) { thumbRadius.toPx() }

    // Phase animation: cycles while playing, rests smoothly when paused
    val infiniteTransition = rememberInfiniteTransition(label = "SquigglyWavePhase")
    val animatedPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WavePhase"
    )

    // Amplitude transitions: gentle flattening when disabled/paused or scrubbing
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableStateOf<Float?>(null) }

    val amplitudeMultiplier by animateFloatAsState(
        targetValue = when {
            !enabled -> 0.2f
            isDragging -> 0.7f
            isPlaying -> 1.0f
            else -> 0.4f
        },
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "WaveAmplitudeMultiplier"
    )

    val thumbScale by animateFloatAsState(
        targetValue = if (isDragging) 1.35f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "ThumbScale"
    )

    val rangeSpan = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val currentFraction = ((value - valueRange.start) / rangeSpan).coerceIn(0f, 1f)
    val displayFraction = dragProgress ?: currentFraction

    val activeAmplitude = maxAmplitudePx * amplitudeMultiplier
    val phase = if (isPlaying) animatedPhase else 0f

    val path = remember { Path() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityLabel
                progressBarRangeInfo = ProgressBarRangeInfo(displayFraction, 0f..1f)
                if (enabled) {
                    setProgress { targetProgress ->
                        val targetVal = valueRange.start + targetProgress * rangeSpan
                        onValueChange(targetVal)
                        onValueChangeFinished?.invoke()
                        true
                    }
                }
            }
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = { offset ->
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val fraction = (offset.x / w).coerceIn(0f, 1f)
                        dragProgress = fraction
                        val targetVal = valueRange.start + fraction * rangeSpan
                        onValueChange(targetVal)
                        tryAwaitRelease()
                        onValueChangeFinished?.invoke()
                        dragProgress = null
                    }
                )
            }
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val fraction = (offset.x / w).coerceIn(0f, 1f)
                        dragProgress = fraction
                        val targetVal = valueRange.start + fraction * rangeSpan
                        onValueChange(targetVal)
                    },
                    onDragEnd = {
                        isDragging = false
                        onValueChangeFinished?.invoke()
                        dragProgress = null
                    },
                    onDragCancel = {
                        isDragging = false
                        dragProgress = null
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val fraction = (change.position.x / w).coerceIn(0f, 1f)
                        dragProgress = fraction
                        val targetVal = valueRange.start + fraction * rangeSpan
                        onValueChange(targetVal)
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val centerY = heightPx / 2f
        val thumbX = (displayFraction * widthPx).coerceIn(0f, widthPx)

        Canvas(modifier = Modifier.fillMaxSize()) {
            path.reset()

            // 1. Draw Active Squiggly Track (0 to thumbX)
            if (thumbX > 0f) {
                path.moveTo(0f, centerY)

                val step = 3f // Fine step size for high quality curve
                var x = 0f
                while (x <= thumbX) {
                    // Taper down near the thumb so the wave seamlessly enters the thumb at center line
                    val taperDistance = waveLengthPx * 0.8f
                    val taper = if (thumbX - x < taperDistance) {
                        ((thumbX - x) / taperDistance).coerceIn(0f, 1f)
                    } else if (x < taperDistance * 0.5f) {
                        (x / (taperDistance * 0.5f)).coerceIn(0f, 1f)
                    } else {
                        1f
                    }

                    // Sine wave calculation
                    val angle = (2 * PI * (x / waveLengthPx) - phase).toFloat()
                    val y = centerY + activeAmplitude * taper * sin(angle)

                    path.lineTo(x, y)
                    x += step
                }
                // Ensure endpoint reaches exactly thumbX at centerY
                path.lineTo(thumbX, centerY)

                drawPath(
                    path = path,
                    color = activeTrackColor,
                    style = Stroke(
                        width = strokeWidthPx,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // 2. Draw Inactive Track (thumbX to widthPx)
            if (thumbX < widthPx) {
                drawLine(
                    color = inactiveTrackColor,
                    start = Offset(thumbX, centerY),
                    end = Offset(widthPx, centerY),
                    strokeWidth = strokeWidthPx,
                    cap = StrokeCap.Round
                )
            }

            // 3. Draw Thumb at (thumbX, centerY)
            val currentThumbRadius = baseThumbRadiusPx * thumbScale
            if (isDragging) {
                // Outer glowing halo
                drawCircle(
                    color = activeTrackColor.copy(alpha = 0.22f),
                    radius = currentThumbRadius * 2.2f,
                    center = Offset(thumbX, centerY)
                )
            }

            // Main thumb solid circle
            drawCircle(
                color = thumbColor,
                radius = currentThumbRadius,
                center = Offset(thumbX, centerY)
            )

            // Center accent dot
            drawCircle(
                color = activeTrackColor.copy(alpha = 0.85f),
                radius = currentThumbRadius * 0.35f,
                center = Offset(thumbX, centerY)
            )
        }

        // 4. Floating Seek-Time Tooltip when dragging
        if (isDragging && formatTime != null) {
            val dragValue = valueRange.start + displayFraction * rangeSpan
            val formatted = formatTime(dragValue.toLong())
            val thumbXDp = with(density) { thumbX.toDp() }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .offset(
                        x = (thumbXDp - 24.dp).coerceIn(0.dp, with(density) { (widthPx - 48.dp.toPx()).toDp().coerceAtLeast(0.dp) }),
                        y = (-30).dp
                    )
            ) {
                Text(
                    text = formatted,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
