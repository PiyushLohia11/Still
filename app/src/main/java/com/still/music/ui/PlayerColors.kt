package com.still.music.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import coil3.Image
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Sample the displayed image without another download or a full-size pixel scan. */
@Composable
internal fun rememberPlayerGradient(image: Image?): List<Color> {
    val base by produceState(Color(0xFF414953), image) {
        value = if (image == null) Color(0xFF414953) else withContext(Dispatchers.Default) {
            val bitmap = image.toBitmap(64, 64)
            val pixels = IntArray(64 * 64)
            bitmap.getPixels(pixels, 0, 64, 0, 0, 64, 64)
            // Coil may own this bitmap, so don't recycle it.
            Color(posterColor(pixels))
        }
    }
    val top by animateColorAsState(shade(base, 0.72f), tween(800), label = "Poster gradient top")
    val middle by animateColorAsState(shade(base, 0.42f), tween(800), label = "Poster gradient middle")
    val bottom by animateColorAsState(shade(base, 0.24f), tween(800), label = "Poster gradient bottom")
    return listOf(top, middle, bottom)
}

private fun shade(color: Color, amount: Float) =
    Color(color.red * amount, color.green * amount, color.blue * amount)

/** Quantize colors so photographic noise doesn't drown out the dominant hue. */
internal fun posterColor(pixels: IntArray): Int {
    val counts = IntArray(4096)
    val reds = LongArray(4096)
    val greens = LongArray(4096)
    val blues = LongArray(4096)
    for (pixel in pixels) {
        if ((pixel ushr 24) < 128) continue
        val r = pixel ushr 16 and 255
        val g = pixel ushr 8 and 255
        val b = pixel and 255
        // Ignore letterboxing and white lettering.
        if (maxOf(r, g, b) < 30 || minOf(r, g, b) > 235) continue
        val bucket = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
        counts[bucket]++
        reds[bucket] += r.toLong()
        greens[bucket] += g.toLong()
        blues[bucket] += b.toLong()
    }
    var best = -1
    var bestScore = 0.0
    for (i in counts.indices) {
        if (counts[i] == 0) continue
        val r = reds[i].toDouble() / counts[i]
        val g = greens[i].toDouble() / counts[i]
        val b = blues[i].toDouble() / counts[i]
        val saturation = (maxOf(r, g, b) - minOf(r, g, b)) / maxOf(r, g, b)
        val score = counts[i] * (1.0 + saturation * 2.0)
        if (score > bestScore) { best = i; bestScore = score }
    }
    if (best < 0) return 0xFF414953.toInt()
    return (0xFF shl 24) or ((reds[best] / counts[best]).toInt() shl 16) or
        ((greens[best] / counts[best]).toInt() shl 8) or (blues[best] / counts[best]).toInt()
}