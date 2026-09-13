package com.still.music.provider

import java.net.URI
import org.schabi.newpipe.extractor.Image

internal fun bestArtwork(images: List<Image>): String? = images.maxWithOrNull(
    compareBy<Image> {
        if (it.width > 0 && it.height > 0) minOf(it.width, it.height)
        else when (it.estimatedResolutionLevel) {
            Image.ResolutionLevel.HIGH -> 1000
            Image.ResolutionLevel.MEDIUM -> 400
            Image.ResolutionLevel.LOW -> 100
            else -> 0
        }
    }.thenBy { it.width.coerceAtLeast(0).toLong() * it.height.coerceAtLeast(0) }
)?.url

/** Only rewrite known thumbnail hosts; retain the exact original URL as a fallback. */
internal fun playerArtworkUrls(original: String?): List<String> = artworkUrls(original, 1200)

internal fun artworkUrls(original: String?, size: Int = 480, trackId: String = ""): List<String> {
    val videoId = trackId.removePrefix("youtube:").takeIf {
        trackId.startsWith("youtube:") && it.matches(Regex("[A-Za-z0-9_-]{11}"))
    }
    val fallback = videoId?.let { listOf(
        "https://i.ytimg.com/vi/$it/maxresdefault.jpg",
        "https://i.ytimg.com/vi/$it/hqdefault.jpg"
    ) }.orEmpty()
    if (original.isNullOrBlank()) return fallback
    val uri = runCatching { URI(original) }.getOrNull() ?: return listOf(original)
    val host = uri.host.orEmpty().lowercase()
    val pixels = size.coerceIn(240, 1600)
    val upgraded = when {
        host.matches(Regex("lh[0-9]+\\.(googleusercontent|ggpht)\\.com")) -> {
            // Size directives belong to the path, not to signed query parameters.
            val path = uri.rawPath.orEmpty()
            val basePath = path.replace(Regex("=(?:w\\d+|s\\d+)[^/]*$"), "")
            val query = uri.rawQuery?.let { "?$it" }.orEmpty()
            listOf("${uri.scheme}://${uri.rawAuthority}$basePath=w$pixels-h$pixels$query")
        }
        (host == "i.ytimg.com" || host == "img.youtube.com") &&
            Regex("/vi(?:_webp)?/[A-Za-z0-9_-]{11}/[^/]+").matches(uri.path.orEmpty()) -> {
            val id = uri.path.split('/')[2]
            // Canonical JPEG URLs also recover from expired/signed WebP thumbnail links.
            listOf("https://i.ytimg.com/vi/$id/maxresdefault.jpg",
                "https://i.ytimg.com/vi/$id/sddefault.jpg",
                "https://i.ytimg.com/vi/$id/hqdefault.jpg")
        }
        else -> emptyList()
    }
    return (upgraded + original + fallback).distinct()
}
