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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.still.music.lyrics.LrcParser
import com.still.music.offline.SavedDownload
import com.still.music.provider.Track

@Composable fun DownloadButton(track: Track, vm: StillViewModel) {
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val entry = downloads.firstOrNull { it.track.id == track.id }
    TextButton(onClick = { if (entry?.active == true) vm.cancelDownload(track.id) else vm.download(track) },
        enabled = entry?.ready != true) {
        Text(when { entry?.ready == true -> "Downloaded"; entry?.active == true -> "Cancel download"; else -> "Download" })
    }
    if (entry?.active == true) Text(entry.status, style = MaterialTheme.typography.labelSmall)
}

@Composable fun DownloadsPanel(entries: List<SavedDownload>, vm: StillViewModel) {
    var removing by remember { mutableStateOf<SavedDownload?>(null) }
    val playable = entries.filter { it.ready }.map { it.track }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Downloads", style = MaterialTheme.typography.headlineSmall)
            Text("Saved on this device in Still. Removed when you uninstall the app.",
                style = MaterialTheme.typography.bodySmall)
            Text("%.1f MB used".format(entries.sumOf { it.bytes } / 1048576.0), style = MaterialTheme.typography.labelMedium)
        }
        if (entries.isEmpty()) item { Text("Choose Download from a song's options or Now Playing.") }
        items(entries, key = { it.track.id }) { entry ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(entry.track.title, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth().clickable(enabled = entry.ready, role = Role.Button,
                            onClickLabel = "Play downloaded song") { vm.play(entry.track, playable) }.padding(vertical = 8.dp))
                    Text(entry.track.artist, style = MaterialTheme.typography.bodyMedium)
                    Text(entry.status, style = MaterialTheme.typography.labelMedium)
                    if (entry.active) {
                        if (entry.progress >= 0) LinearProgressIndicator(progress = { entry.progress / 100f }, modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    Row {
                        if (entry.ready) TextButton(onClick = { vm.play(entry.track, playable) }) { Text("Play offline") }
                        else if (entry.active) TextButton(onClick = { vm.cancelDownload(entry.track.id) }) { Text("Cancel") }
                        else TextButton(onClick = { vm.download(entry.track) }) { Text("Retry download") }
                        TextButton(onClick = { removing = entry }) { Text("Remove download") }
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
    Surface(Modifier.fillMaxWidth().padding(top = 16.dp), shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 2.dp) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Lyrics", style = MaterialTheme.typography.titleLarge)
        Text("LRCLIB", style = MaterialTheme.typography.labelSmall)
        when {
            lyrics == null || lyrics.loading -> CircularProgressIndicator(Modifier.padding(16.dp).size(24.dp))
            lyrics.error != null -> {
                Text(lyrics.error)
                TextButton(onClick = { vm.loadLyrics(track, true) }) { Text("Retry lyrics") }
            }
            lyrics.value?.instrumental == true -> Text("Instrumental - no lyrics.")
            lyrics.value == null || (lyrics.value.plain.isBlank() && lyrics.value.lines.isEmpty()) -> {
                Text("No lyrics found for this recording.")
                TextButton(onClick = { vm.loadLyrics(track, true) }) { Text("Check again") }
            }
            lyrics.value.lines.isNotEmpty() -> {
                val lines = lyrics.value.lines
                val active = LrcParser.active(lines, position)
                val listState = rememberLazyListState()
                LaunchedEffect(active, track.id) { if (active >= 0) listState.animateScrollToItem((active - 1).coerceAtLeast(0)) }
                LazyColumn(Modifier.fillMaxWidth().height(300.dp), state = listState) {
                    itemsIndexed(lines) { index, line ->
                        val lyricColor by animateColorAsState(
                            if (index == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            animationSpec = tween(250), label = "Active lyric")
                        Text(line.text.ifBlank { "..." }, style = MaterialTheme.typography.titleMedium,
                            color = lyricColor,
                            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Seek to lyric") {
                                vm.seek(line.timeMs)
                            }.padding(vertical = 10.dp))
                    }
                }
            }
            else -> Text(lyrics.value.plain, Modifier.padding(vertical = 12.dp))
        }
    }
    }
}
