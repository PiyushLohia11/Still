package com.still.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.still.music.data.Playlist
import com.still.music.provider.Track

@Composable internal fun PlaylistPage(playlist: Playlist, tracks: List<Track>,
    favourites: List<Track>, player: PlaybackState, vm: StillViewModel,
    onBack: () -> Unit, onAdd: () -> Unit, onPlaylist: (Track) -> Unit,
    onRename: () -> Unit, onDelete: () -> Unit) {
    var query by rememberSaveable(playlist.id) { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    val visible = remember(tracks, query) {
        val term = query.trim()
        tracks.filter { it.title.contains(term, true) || it.artist.contains(term, true) }
    }
    val favouriteIds = remember(favourites) { favourites.mapTo(hashSetOf(), Track::id) }
    val playable = tracks.any(Track::playable) && player.connected
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "header") {
            Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(
                MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.background)))
                .padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to playlists") }
                    Text("YOUR PLAYLIST", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreHoriz, "Playlist options") }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Rename playlist") }, onClick = { menu = false; onRename() })
                            DropdownMenuItem(text = { Text("Delete playlist") }, onClick = { menu = false; onDelete() })
                        }
                    }
                }
                Box(Modifier.padding(vertical = 16.dp).size(192.dp).align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center) {
                    val covers = tracks.take(4)
                    if (covers.size >= 4) Column {
                        covers.chunked(2).forEach { pair -> Row(Modifier.weight(1f)) {
                            pair.forEach { Artwork(it, Modifier.weight(1f).fillMaxHeight(), cornerRadius = 0.dp) }
                        } }
                    } else if (covers.isNotEmpty()) Artwork(covers.first(), Modifier.fillMaxSize(), cornerRadius = 0.dp)
                    else Icon(Icons.Default.LibraryMusic, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Text(playlist.name, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(playlistSummary(tracks), Modifier.padding(top = 6.dp, bottom = 16.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { tracks.firstOrNull(Track::playable)?.let { vm.play(it, tracks) } },
                        enabled = playable, modifier = Modifier.weight(1f).height(52.dp)) {
                        Icon(Icons.Default.PlayArrow, null); Text("Play", Modifier.padding(start = 8.dp))
                    }
                    FilledTonalButton(onClick = { vm.playShuffled(tracks) }, enabled = playable,
                        modifier = Modifier.weight(1f).height(52.dp)) {
                        Icon(Icons.Default.Shuffle, null); Text("Shuffle", Modifier.padding(start = 8.dp))
                    }
                    IconButton(onClick = onAdd, modifier = Modifier.size(52.dp)) { Icon(Icons.Default.Add, "Find songs to add") }
                }
                PlaylistDownloadButton(tracks, "playlist:${playlist.id}", vm) { vm.downloadPlaylist(playlist.id) }
                if (tracks.isNotEmpty()) {
                    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(top = 12.dp),
                        placeholder = { Text("Find in playlist") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear filter") } },
                        singleLine = true, shape = RoundedCornerShape(16.dp))
                    Text("Swipe right to queue · left to favourite", Modifier.padding(vertical = 14.dp),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (tracks.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Make it yours", style = MaterialTheme.typography.titleLarge)
                Text("Find a song, then choose Add to playlist from its menu.", Modifier.padding(vertical = 12.dp))
                FilledTonalButton(onClick = onAdd) { Text("Find music") }
            }
        } else if (visible.isEmpty()) item {
            Text("No songs match this search.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(visible, key = Track::id) { track ->
            TrackRow(track, (track.id in favouriteIds), player.current?.id == track.id,
                { vm.play(track, tracks) }, { vm.favourite(track) }, { vm.enqueue(track) }, { onPlaylist(track) },
                onRemove = { vm.removeFromPlaylist(playlist.id, track) }, onDownload = { vm.download(track) })
        }
    }
}

internal fun playlistSummary(tracks: List<Track>): String {
    val minutes = tracks.sumOf { it.durationMs.coerceAtLeast(0) } / 60_000
    val length = if (minutes >= 60) "${minutes / 60} hr ${minutes % 60} min" else "$minutes min"
    return "${tracks.size} ${if (tracks.size == 1) "song" else "songs"}" +
        if (minutes > 0) " · $length" else ""
}
