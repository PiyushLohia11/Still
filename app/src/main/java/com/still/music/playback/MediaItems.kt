package com.still.music.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.still.music.provider.Track

fun Track.mediaItem(): MediaItem = MediaItem.Builder().setMediaId(id)
    .setUri(Uri.Builder().scheme("still").authority("track").appendPath(id).build())
    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist)
        .setArtworkUri(artwork?.let(Uri::parse))
        .setExtras(Bundle().apply { putLong("durationMs", durationMs); putString("unavailable", unavailableReason) })
        .build()).build()
fun MediaItem.track(): Track = Track(mediaId, mediaMetadata.title?.toString().orEmpty(),
    mediaMetadata.artist?.toString().orEmpty(), mediaMetadata.artworkUri?.toString(),
    mediaMetadata.extras?.getLong("durationMs") ?: 0, mediaMetadata.extras?.getString("unavailable"))

object QueueRules {
    const val MAX_SIZE = 300
    fun nextRepeat(current: Int): Int = when (current) { 0 -> 2; 2 -> 1; else -> 0 }
    fun canMove(size: Int, from: Int, to: Int) = from in 0 until size && to in 0 until size && from != to
}
