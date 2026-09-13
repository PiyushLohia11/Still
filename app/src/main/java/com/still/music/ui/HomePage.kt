package com.still.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.still.music.provider.Track

data class HomeMix(val title: String, val detail: String, val query: String, val color: Long)
data class HomeState(
    val loading: Boolean = false, val tracks: List<Track> = emptyList(),
    val subtitle: String = "A little discovery to start your day", val error: String? = null,
    val selectedMix: HomeMix? = null, val mixTracks: List<Track> = emptyList(),
    val mixLoading: Boolean = false, val mixError: String? = null
)
private val homeMixes = listOf(
    HomeMix("Slow mornings", "Ease into the day", "acoustic indie mellow songs", 0xFF526C58),
    HomeMix("After hours", "Soft lights. Good music.", "late night rnb songs", 0xFF65517E),
    HomeMix("Focus flow", "Find your rhythm", "lofi instrumental chill beats", 0xFF3D6875),
    HomeMix("Golden era", "Songs to come back to", "classic hit songs 2000s", 0xFF8A6543),
    HomeMix("Turn it up", "A little extra energy", "workout electronic dance songs", 0xFF80505E),
    HomeMix("Indie escape", "Take the scenic route", "indie alternative songs", 0xFF456E69),
    HomeMix("Desi favourites", "A familiar kind of magic", "Bollywood Hindi hit songs", 0xFF886044),
    HomeMix("Rainy day", "Stay a little longer", "rainy day jazz songs", 0xFF4E5F80)
)

@Composable internal fun HomePage(vm: StillViewModel, onPlaylist: (Track) -> Unit,
    onLibraryPlaylist: (Long) -> Unit) {
    val home by vm.home.collectAsStateWithLifecycle()
    val player by vm.playback.collectAsStateWithLifecycle()
    val favourites by vm.favourites.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val mixLoading by vm.mixLoading.collectAsStateWithLifecycle()
    var picks by remember { mutableStateOf(homeMixes.shuffled().take(4)) }
    LaunchedEffect(Unit) { vm.loadHome() }
    BackHandler(home.selectedMix != null) { vm.closeHomeMix() }
    val selected = home.selectedMix
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (selected != null) IconButton(onClick = vm::closeHomeMix) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Home")
                }
                Column(Modifier.weight(1f)) {
                    Text("MUSIC WITH ROOM TO BREATHE", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.2.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 6.dp))
                    Text(selected?.title ?: "Home", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text(selected?.detail ?: "Find something that feels like you.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (selected != null) {
            item {
                Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = home.mixTracks.isNotEmpty() && player.connected && !home.mixLoading,
                        onClick = { home.mixTracks.firstOrNull()?.let { vm.play(it, home.mixTracks) } }) {
                        Icon(Icons.Default.PlayArrow, null)
                        Text("Play mix")
                    }
                    OutlinedButton(onClick = { vm.openHomeMix(selected) }, enabled = !home.mixLoading) { Text("Reshuffle") }
                }
            }
            item {
                PlaylistDownloadButton(home.mixTracks, "mix:" + selected.title, vm,
                    onDownload = { vm.downloadMix(selected.title, home.mixTracks) })
            }
            if (home.mixLoading) item { HomeLoading() }
            home.mixError?.let { error -> item { HomeError(error) { vm.openHomeMix(selected) } } }
            items(home.mixTracks, key = Track::id) { track ->
                TrackRow(track, favourites.any { it.id == track.id }, player.current?.id == track.id,
                    { vm.play(track, home.mixTracks) }, { vm.favourite(track) }, { vm.enqueue(track) },
                    { onPlaylist(track) }, onDownload = { vm.download(track) })
            }
        } else {
            item {
                HomeHeading("Recommended for you", if (home.loading) "Loading..." else "Refresh", !home.loading) { vm.loadHome(true) }
                Text(home.subtitle, Modifier.padding(horizontal = 20.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (home.tracks.isNotEmpty()) TextButton(
                    enabled = player.connected,
                    onClick = { vm.play(home.tracks.first(), home.tracks) },
                    modifier = Modifier.padding(horizontal = 12.dp)) {
                    Icon(Icons.Default.PlayArrow, null)
                    Text("Play recommendations", Modifier.padding(start = 6.dp))
                }
            }
            if (home.loading && home.tracks.isEmpty()) item { HomeLoading() }
            home.error?.let { error -> item { HomeError(error) { vm.loadHome(true) } } }
            if (home.tracks.isNotEmpty()) item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(home.tracks, key = Track::id) { track ->
                        RecommendationCard(track, favourites.any { it.id == track.id }, player.current?.id == track.id,
                            onPlay = { vm.play(track, home.tracks) }, onFavourite = { vm.favourite(track) },
                            onQueue = { vm.enqueue(track) }, onPlaylist = { onPlaylist(track) },
                            onDownload = { vm.download(track) })
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth().clip(RoundedCornerShape(32.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF3E594C), Color(0xFF152019)))).padding(32.dp)) {
                    Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFFD7E5D7), modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Your kind of music", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("A fresh mix of your favourites and recent listens.",
                        Modifier.padding(top = 8.dp, bottom = 24.dp), color = Color(0xFFD7E5D7))
                    Button(onClick = vm::playSmartMix,
                        enabled = player.connected && !mixLoading && (favourites.isNotEmpty() || recents.isNotEmpty()),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        modifier = Modifier.height(48.dp)) {
                        Icon(Icons.Default.PlayArrow, null)
                        Text(if (mixLoading) "Creating mix..." else "Play my mix", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                    }
                    if (favourites.isEmpty() && recents.isEmpty()) Text("Play or favourite a few songs to make it yours.",
                        style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 16.dp), color = Color(0xFFD7E5D7).copy(alpha = 0.7f))
                }
            }
            item {
                HomeHeading("Made for the moment", "Shuffle picks") { picks = homeMixes.shuffled().take(4) }
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(picks, key = HomeMix::title) { mix ->
                        Column(Modifier.width(180.dp).aspectRatio(1f).clip(RoundedCornerShape(24.dp))
                            .background(Brush.linearGradient(listOf(Color(mix.color), Color(0xFF121212))))
                            .clickable(role = Role.Button, onClickLabel = "Open ${mix.title}") { vm.openHomeMix(mix) }
                            .padding(20.dp), verticalArrangement = Arrangement.Bottom) {
                            Text(mix.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = Color.White)
                            Text(mix.detail, style = MaterialTheme.typography.labelMedium, minLines = 2,
                                modifier = Modifier.padding(top = 6.dp), color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }
            }
            if (recents.isNotEmpty()) {
                item { Text("Recently played", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleLarge) }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(recents.take(10), key = Track::id) { track ->
                            Column(Modifier.width(140.dp).clip(RoundedCornerShape(20.dp))
                                .clickable(role = Role.Button, onClickLabel = "Play ${track.title}") { vm.play(track, recents) }) {
                                Artwork(track, Modifier.size(140.dp))
                                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                                Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            if (playlists.isNotEmpty()) {
                item { Text("Your playlists", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleLarge) }
                items(playlists.take(6), key = { "playlist:${it.id}" }) { playlist ->
                    OutlinedButton(onClick = { onLibraryPlaylist(playlist.id) },
                        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                        Icon(Icons.Default.LibraryMusic, null)
                        Text(playlist.name, Modifier.weight(1f).padding(12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Default.ChevronRight, null)
                    }
                }
            }
        }
    }
}

@Composable private fun HomeHeading(title: String, action: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = onClick, enabled = enabled) { Text(action) }
    }
}
@Composable private fun HomeLoading() {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}
@Composable private fun HomeError(message: String, retry: () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text(message, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = retry) { Text("Retry") }
    }
}
@Composable private fun RecommendationCard(track: Track, favourite: Boolean, current: Boolean,
    onPlay: () -> Unit, onFavourite: () -> Unit, onQueue: () -> Unit, onPlaylist: () -> Unit,
    onDownload: () -> Unit) {
    var menu by remember(track.id) { mutableStateOf(false) }
    Column(Modifier.width(200.dp).clip(RoundedCornerShape(24.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.size(200.dp).clickable(role = Role.Button, onClickLabel = "Play " + track.title, onClick = onPlay)) {
            Artwork(track, Modifier.fillMaxSize(), cornerRadius = 24.dp)
            FilledIconButton(onClick = onPlay, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)) {
                Icon(Icons.Default.PlayArrow, "Play " + track.title)
            }
        }
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp)) {
            Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, minLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Text(track.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onFavourite) {
                Icon(if (favourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    if (favourite) "Remove favourite" else "Add favourite",
                    tint = if (favourite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Options for " + track.title) }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Add to queue") }, onClick = { menu = false; onQueue() })
                    DropdownMenuItem(text = { Text("Add to playlist") }, onClick = { menu = false; onPlaylist() })
                    DropdownMenuItem(text = { Text("Download for offline") }, onClick = { menu = false; onDownload() })
                }
            }
        }
    }
}
