package com.still.music.ui

import android.media.AudioManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlin.math.roundToInt

/**
 * A sleek, modern capsule volume control panel featuring:
 * - An interactive capsule pill slider with dynamic volume fill and drag/tap gestures
 * - Adaptive volume icon state (Muted, Low, Medium, High) with click-to-mute
 * - Fast preset selector buttons (0%, 25%, 50%, 75%, 100%)
 * - Seamless hardware key synchronization and error safety
 */
@Composable
fun VolumeControlBar(
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val audio = remember(context) { context.getSystemService(AudioManager::class.java) }
    val view = LocalView.current

    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val previousStream = window?.volumeControlStream
        window?.volumeControlStream = AudioManager.STREAM_MUSIC
        onDispose { if (previousStream != null) window.volumeControlStream = previousStream }
    }

    fun readVolume() = audio.getStreamVolume(AudioManager.STREAM_MUSIC) to
        audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    // Poll the stream volume while visible to stay synchronized with hardware keys
    val volumeChanges = remember(audio) {
        flow {
            while (true) {
                emit(readVolume())
                delay(200)
            }
        }
    }
    val observed by volumeChanges.collectAsStateWithLifecycle(initialValue = remember(audio) { readVolume() })
    var volume by remember(audio) { mutableIntStateOf(observed.first) }
    val maximum = observed.second.coerceAtLeast(1)
    var lastAudible by rememberSaveable { mutableIntStateOf(volume.coerceAtLeast(1)) }

    LaunchedEffect(observed) {
        volume = observed.first
        if (volume > 0) lastAudible = volume
    }

    val enabled = maximum > 0 && !audio.isVolumeFixed
    val percentage = if (maximum > 0) (volume * 100f / maximum).roundToInt().coerceIn(0, 100) else 0

    fun setVolume(value: Int) {
        try {
            val clamped = value.coerceIn(0, maximum)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, clamped, 0)
            volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (volume > 0) lastAudible = volume
        } catch (_: SecurityException) {
            onError("Media volume could not be changed. Use your device's volume controls.")
        }
    }

    val volumeFraction by animateFloatAsState(
        targetValue = (volume.toFloat() / maximum.toFloat()).coerceIn(0f, 1f),
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "VolumeFillFraction"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row: Label & Percentage badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Media Volume",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = if (volume > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = if (volume > 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    AnimatedContent(
                        targetState = percentage,
                        transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(120)) },
                        label = "PercentageBadge"
                    ) { targetPct ->
                        Text(
                            text = if (targetPct == 0) "Muted" else "$targetPct%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Interactive Capsule Volume Track
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Music volume"
                        stateDescription = "$percentage percent"
                        progressBarRangeInfo = ProgressBarRangeInfo(volumeFraction, 0f..1f)
                        if (enabled) {
                            setProgress { target ->
                                setVolume((target * maximum).roundToInt())
                                true
                            }
                        }
                    }
                    .pointerInput(enabled, maximum) {
                        if (!enabled) return@pointerInput
                        detectTapGestures(
                            onTap = { offset ->
                                val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                setVolume((fraction * maximum).roundToInt())
                            }
                        )
                    }
                    .pointerInput(enabled, maximum) {
                        if (!enabled) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                setVolume((fraction * maximum).roundToInt())
                            },
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                val fraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                setVolume((fraction * maximum).roundToInt())
                            }
                        )
                    }
            ) {
                val fullWidth = maxWidth

                // Active Fill Pill with Gradient
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(fullWidth * volumeFraction)
                        .clip(RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp,
                            topEnd = if (volumeFraction > 0.95f) 26.dp else 8.dp,
                            bottomEnd = if (volumeFraction > 0.95f) 26.dp else 8.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                                    MaterialTheme.colorScheme.primary
                                )
                            )
                        )
                )

                // Foreground Content inside Capsule: Dynamic Icon & Level Indication
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Mute / Unmute Button
                    val iconInteraction = remember { MutableInteractionSource() }
                    val isIconPressed by iconInteraction.collectIsPressedAsState()
                    val iconScale by animateFloatAsState(
                        if (isIconPressed) 0.85f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "MuteIconScale"
                    )

                    val volumeIcon: ImageVector = when {
                        volume == 0 -> Icons.AutoMirrored.Filled.VolumeOff
                        percentage < 33 -> Icons.AutoMirrored.Filled.VolumeMute
                        percentage < 66 -> Icons.AutoMirrored.Filled.VolumeDown
                        else -> Icons.AutoMirrored.Filled.VolumeUp
                    }

                    Surface(
                        modifier = Modifier
                            .size(36.dp)
                            .scale(iconScale)
                            .clickable(
                                enabled = enabled,
                                role = Role.Button,
                                onClickLabel = if (volume == 0) "Unmute music" else "Mute music",
                                interactionSource = iconInteraction,
                                indication = null
                            ) {
                                if (volume > 0) {
                                    lastAudible = volume
                                    setVolume(0)
                                } else {
                                    setVolume(lastAudible.coerceIn(1, maximum))
                                }
                            },
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.2f),
                        contentColor = if (volumeFraction > 0.15f) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = volumeIcon,
                                contentDescription = if (volume == 0) "Unmute" else "Mute",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Level feedback text on the right
                    Text(
                        text = "$volume / $maximum",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        ),
                        color = if (volumeFraction > 0.85f) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Quick Preset Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val presets = listOf(0 to "Mute", 25 to "25%", 50 to "50%", 75 to "75%", 100 to "Max")
                presets.forEach { (targetPct, label) ->
                    val targetVol = (targetPct * maximum / 100f).roundToInt()
                    val isSelected = when (targetPct) {
                        0 -> volume == 0
                        100 -> volume == maximum
                        else -> {
                            val lowerBound = ((targetPct - 12) * maximum / 100f).roundToInt()
                            val upperBound = ((targetPct + 12) * maximum / 100f).roundToInt()
                            volume in lowerBound..upperBound && volume > 0 && volume < maximum
                        }
                    }

                    val chipBg by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        animationSpec = tween(150),
                        label = "PresetChipBg"
                    )
                    val chipTextColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(150),
                        label = "PresetChipText"
                    )

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .clip(CircleShape)
                            .clickable(enabled = enabled) { setVolume(targetVol) },
                        shape = CircleShape,
                        color = chipBg,
                        border = if (isSelected) null else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 11.sp
                                ),
                                color = chipTextColor
                            )
                        }
                    }
                }
            }
        }
    }
}
