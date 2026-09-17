package com.still.music.ui

import androidx.compose.foundation.clickable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.still.music.lyrics.LrcParser
import com.still.music.offline.SavedDownload
import com.still.music.provider.Track

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

@Composable fun DownloadButton(track: Track, vm: StillViewModel) {
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val entry = downloads.firstOrNull { it.track.id == track.id }
    val icon = when {
        entry?.ready == true -> Icons.Default.DownloadDone
        entry?.active == true -> Icons.Default.Close
        else -> Icons.Default.Download
    }
    val contentDesc = when {
        entry?.ready == true -> "Downloaded"
        entry?.active == true -> "Cancel download"
        else -> "Download"
    }
    IconButton(
        onClick = { if (entry?.active == true) vm.cancelDownload(track.id) else vm.download(track) },
        enabled = entry?.ready != true
    ) {
        Icon(icon, contentDescription = contentDesc)
    }
    if (entry?.active == true) Text(entry.status, style = MaterialTheme.typography.labelSmall)
}

@Composable fun DownloadsPanel(entries: List<SavedDownload>, vm: StillViewModel) {
    var removing by remember { mutableStateOf<SavedDownload?>(null) }
    val favourites by vm.favourites.collectAsStateWithLifecycle()
    val playable = entries.filter { it.ready }.map { it.track }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Icon(Icons.Default.OfflinePin, null, tint = MaterialTheme.colorScheme.primary)
                Text("Ready when you are.", style = MaterialTheme.typography.headlineMedium)
                Text("${playable.size} songs offline · %.1f MB used".format(entries.sumOf { it.bytes } / 1048576.0),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Saved on this device. Take your music anywhere.", style = MaterialTheme.typography.bodySmall)
                if (playable.isNotEmpty()) FilledTonalButton(onClick = { vm.playShuffled(playable) }) {
                    Icon(Icons.Default.Shuffle, null); Text("Shuffle downloads", Modifier.padding(start = 8.dp))
                }
            }
        }
        if (entries.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Download, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Keep music close", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
                Text("Choose Download from a song’s options.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(entries, key = { it.track.id }) { entry ->
            SwipeActions(entry.track.id, "Queue", if (favourites.any { it.id == entry.track.id }) "Unfavourite" else "Favourite",
                onRight = { vm.enqueue(entry.track) }, onLeft = { vm.favourite(entry.track) }, enabled = entry.ready) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth().clickable(enabled = entry.ready, role = Role.Button,
                        onClickLabel = "Play downloaded song") { vm.play(entry.track, playable) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Artwork(entry.track, Modifier.size(56.dp), cornerRadius = 14.dp)
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(entry.track.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(entry.track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Text(entry.status, style = MaterialTheme.typography.labelMedium)
                    if (entry.active) {
                        if (entry.progress >= 0) LinearProgressIndicator(progress = { (entry.progress / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (entry.ready) TextButton(onClick = { vm.play(entry.track, playable) }) { Text("Play offline") }
                        else if (entry.active) TextButton(onClick = { vm.cancelDownload(entry.track.id) }) { Text("Cancel") }
                        else TextButton(onClick = { vm.download(entry.track) }) { Text("Retry download") }
                        TextButton(onClick = { removing = entry }) { Text("Remove download") }
                    }
                }
            }
        }
    }
    }
    removing?.let { entry ->
        AlertDialog(onDismissRequest = { removing = null }, shape = RoundedCornerShape(20.dp), title = { Text("Remove download?") },
            text = { Text("Remove the saved audio for ${entry.track.title}? Your playlists and favourites stay.") },
            confirmButton = { TextButton(onClick = { vm.removeDownload(entry.track.id); removing = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } })
    }
}

@Composable fun LyricsPanel(track: Track, position: Long, vm: StillViewModel) {
    val state by vm.lyrics.collectAsStateWithLifecycle()
    LaunchedEffect(track.id) { vm.loadLyrics(track) }
    val lyrics = state.takeIf { it.trackId == track.id }
    
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Lyrics", style = MaterialTheme.typography.headlineSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text("LRCLIB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        
        when {
            lyrics == null || lyrics.loading -> CircularProgressIndicator(Modifier.padding(16.dp).size(24.dp))
            lyrics.error != null -> {
                Text(lyrics.error, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { vm.loadLyrics(track, true) }) { Text("Retry lyrics") }
            }
            lyrics.value?.instrumental == true -> Text("Instrumental - no lyrics.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            lyrics.value == null || (lyrics.value.plain.isBlank() && lyrics.value.lines.isEmpty()) -> {
                Text("No lyrics found for this recording.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { vm.loadLyrics(track, true) }) { Text("Check again") }
            }
            lyrics.value.lines.isNotEmpty() -> {
                val lines = lyrics.value.lines
                val active = LrcParser.active(lines, position)
                val listState = rememberLazyListState()
                LaunchedEffect(active, track.id) { if (active >= 0 && !listState.isScrollInProgress) listState.animateScrollToItem((active - 1).coerceAtLeast(0)) }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), state = listState, contentPadding = PaddingValues(vertical = 16.dp)) {
                    itemsIndexed(lines) { index, line ->
                        val isActive = index == active
                        val lyricColor by animateColorAsState(
                            if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            animationSpec = tween(300), label = "Active lyric color"
                        )
                        Text(
                            text = line.text.ifBlank { "..." },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = if (isActive) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium,
                            color = lyricColor,
                            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Seek to lyric") {
                                vm.seek(line.timeMs)
                            }.padding(vertical = 12.dp)
                        )
                    }
                }
            }
            else -> Text(lyrics.value.plain, Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
