@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.still.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.still.music.data.Playlist
import com.still.music.provider.Track

@Composable fun StillTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFFA4B8A5), onPrimary = Color(0xFF152019),
        primaryContainer = Color(0xFF303D33), onPrimaryContainer = Color(0xFFD7E5D7),
        background = Color(0xFF151718), onBackground = Color(0xFFF0EEE8),
        surface = Color(0xFF151718), onSurface = Color(0xFFF0EEE8),
        surfaceVariant = Color(0xFF242728), onSurfaceVariant = Color(0xFFB8BCB6),
        secondaryContainer = Color(0xFF303D33), onSecondaryContainer = Color(0xFFD7E5D7),
        outline = Color(0xFF727971)), content = content)
}
@Composable fun StillScreen(vm: StillViewModel) {
    val search by vm.search.collectAsStateWithLifecycle()
    val player by vm.playback.collectAsStateWithLifecycle()
    val favourites by vm.favourites.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val playlistTracks by vm.playlistTracks.collectAsStateWithLifecycle()
    val preload by vm.preload.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var libraryTab by rememberSaveable { mutableIntStateOf(0) }
    var nowPlaying by rememberSaveable { mutableStateOf(false) }
    var queue by rememberSaveable { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var playlistId by rememberSaveable { mutableStateOf<Long?>(null) }
    var addTrack by remember { mutableStateOf<Track?>(null) }
    var naming by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf<Playlist?>(null) }
    var delete by remember { mutableStateOf<Playlist?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(playlistId) { vm.selectPlaylist(playlistId) }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value = null } }
    BackHandler(playlistId != null && !nowPlaying && !queue) { playlistId = null }
    Scaffold(
        topBar = {
            TopAppBar(title = { Column {
                Text("Still", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.headlineMedium)
                Text("Music, with room to breathe.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }, actions = { Action(Icons.Default.Tune, "Preferences", onClick = { settings = true }) })
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column {
                player.current?.let { current ->
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(horizontal = 12.dp).clip(RoundedCornerShape(16.dp))) {
                        Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Open Now Playing") { nowPlaying = true }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Artwork(current, Modifier.size(48.dp))
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(current.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(current.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (player.buffering) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Action(if (player.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                if (player.playing) "Pause" else "Play", enabled = player.connected, onClick = vm::toggle)
                            Action(Icons.Default.SkipNext, "Next track", onClick = vm::next)
                        }
                    }
                }
                NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                    NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.Search, null) }, label = { Text("Search") })
                    NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.LibraryMusic, null) }, label = { Text("Library") })
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize().imePadding()) {
            if (tab == 0) {
                OutlinedTextField(value = search.query, onValueChange = vm::query,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    label = { Text("Search Audius") }, placeholder = { Text("Tracks or artists") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (search.query.isNotEmpty()) Action(Icons.Default.Close, "Clear search", onClick = { vm.query("") }) },
                    singleLine = true, shape = RoundedCornerShape(16.dp))
                Text("Music from Audius · availability varies", modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (search.query.isBlank()) item { EmptyState("Find your next favourite", "Search Audius’s independent catalog by track or artist.", Icons.Default.Search) }
                    if (search.loading && search.tracks.isEmpty()) item { Loading() }
                    search.error?.let { error -> item { ErrorState(error, vm::retrySearch) } }
                    if (!search.loading && search.query.isNotBlank() && search.tracks.isEmpty() && search.error == null)
                        item { EmptyState("No tracks found", "Try another title or artist. Audius does not have every song.", Icons.Default.MusicNote) }
                    items(search.tracks, key = Track::id) { track ->
                        TrackRow(track, favourites.any { it.id == track.id }, player.current?.id == track.id,
                            onPlay = { vm.play(track, search.tracks) }, onFavourite = { vm.favourite(track) },
                            onQueue = { vm.enqueue(track) }, onPlaylist = { addTrack = track })
                    }
                    if (search.more) item {
                        TextButton(onClick = vm::more, enabled = !search.loading, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(if (search.loading) "Loading…" else "Load more")
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Favourites", "Playlists", "Recently played").forEachIndexed { index, label ->
                        FilterChip(selected = libraryTab == index, onClick = { libraryTab = index; playlistId = null }, label = { Text(label) })
                    }
                }
                val selected = playlists.firstOrNull { it.id == playlistId }
                if (libraryTab == 1 && selected == null) {
                    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { OutlinedButton(onClick = { naming = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text("New playlist", Modifier.padding(start = 8.dp)) } }
                        if (playlists.isEmpty()) item { EmptyState("Make a little collection", "Create a playlist, then add tracks from search.", Icons.Default.LibraryMusic) }
                        items(playlists, key = Playlist::id) { list ->
                            var menu by remember { mutableStateOf(false) }
                            ListItem(headlineContent = { Text(list.name) }, leadingContent = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                                trailingContent = { Box {
                                    Action(Icons.Default.MoreVert, "Playlist options for ${list.name}", onClick = { menu = true })
                                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; rename = list; naming = true })
                                        DropdownMenuItem(text = { Text("Delete playlist") }, onClick = { menu = false; delete = list })
                                    }
                                } }, modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { playlistId = list.id })
                        }
                    }
                } else {
                    val tracks = if (libraryTab == 0) favourites else if (libraryTab == 2) recents else playlistTracks
                    if (selected != null) Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Action(Icons.AutoMirrored.Filled.ArrowBack, "Back to playlists", onClick = { playlistId = null })
                        Text(selected.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { tracks.firstOrNull { it.playable }?.let { vm.play(it, tracks) } }, enabled = tracks.any { it.playable }) { Text("Play") }
                    }
                    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                        if (tracks.isEmpty()) item { EmptyState(
                            if (libraryTab == 0) "Keep what you love" else if (libraryTab == 2) "Your listening starts here" else "A quiet playlist",
                            if (libraryTab == 0) "Tap a track’s heart to save it here." else if (libraryTab == 2) "Tracks appear after playback begins." else "Add tracks using their options menu.",
                            Icons.Default.MusicNote) }
                        items(tracks, key = Track::id) { track ->
                            TrackRow(track, favourites.any { it.id == track.id }, player.current?.id == track.id,
                                { vm.play(track, tracks) }, { vm.favourite(track) }, { vm.enqueue(track) }, { addTrack = track },
                                onRemove = if (selected != null) ({ vm.removeFromPlaylist(selected.id, track) }) else null)
                        }
                    }
                }
            }
        }
    }
    if (nowPlaying && player.current != null) FullScreenSheet("Now playing", { nowPlaying = false }) {
        NowPlaying(player, favourites.any { it.id == player.current?.id }, vm, { queue = true })
    }
    if (queue) FullScreenSheet("Queue", { queue = false }) {
        Text(if (player.shuffle) "Shuffle is on. This list shows your editable queue order." else "Plays from top to bottom.",
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (player.queue.isEmpty()) item { EmptyState("The queue is empty", "Choose a track from search or your library.", Icons.AutoMirrored.Filled.QueueMusic) }
            itemsIndexed(player.queue) { index, track ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth().clickable { vm.jump(index) }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (index == player.index) "•" else "${index + 1}", Modifier.width(32.dp), color = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(track.artist, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Row(Modifier.align(Alignment.End)) {
                        Action(Icons.Default.ArrowUpward, "Move ${track.title} up", index > 0) { vm.move(index, index - 1) }
                        Action(Icons.Default.ArrowDownward, "Move ${track.title} down", index < player.queue.lastIndex) { vm.move(index, index + 1) }
                        Action(Icons.Default.Close, "Remove ${track.title} from queue") { vm.remove(index) }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
    if (settings) AlertDialog(onDismissRequest = { settings = false }, title = { Text("Preferences") },
        text = { Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Preload next track", Modifier.weight(1f))
                Switch(preload, onCheckedChange = vm::setPreload, modifier = Modifier.semantics { contentDescription = "Preload next track" })
            }
            Text("Buffers up to five seconds ahead in memory. Uses more mobile data. No audio is saved for offline listening.")
            Spacer(Modifier.height(16.dp))
            Text("Still is free and ad-free. Music is supplied by Audius; catalog and access may change. Your library stays on this device.")
        } }, confirmButton = { TextButton(onClick = { settings = false }) { Text("Done") } })
    if (addTrack != null && !naming) AlertDialog(onDismissRequest = { addTrack = null }, title = { Text("Add to playlist") },
        text = { LazyColumn {
            if (playlists.isEmpty()) item { Text("Create your first playlist.") }
            items(playlists, key = Playlist::id) { list ->
                TextButton(onClick = { addTrack?.let { vm.addToPlaylist(list.id, it) }; addTrack = null }, modifier = Modifier.fillMaxWidth()) { Text(list.name) }
            }
        } }, confirmButton = { TextButton(onClick = { naming = true }) { Text("New playlist") } },
        dismissButton = { TextButton(onClick = { addTrack = null }) { Text("Cancel") } })
    if (naming) {
        var name by remember { mutableStateOf(rename?.name ?: "") }
        AlertDialog(onDismissRequest = { naming = false; rename = null }, title = { Text(if (rename == null) "New playlist" else "Rename playlist") },
            text = { OutlinedTextField(name, { name = it.take(100) }, label = { Text("Playlist name") }, singleLine = true) },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
                val list = rename
                if (list == null) vm.createPlaylist(name, addTrack) else vm.renamePlaylist(list.id, name)
                naming = false; rename = null; addTrack = null
            }) { Text("Save") } }, dismissButton = { TextButton(onClick = { naming = false; rename = null }) { Text("Cancel") } })
    }
    delete?.let { list ->
        AlertDialog(onDismissRequest = { delete = null }, title = { Text("Delete ${list.name}?") },
            text = { Text("This removes the playlist from this device. Your favourites remain.") },
            confirmButton = { TextButton(onClick = { vm.deletePlaylist(list.id); delete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { delete = null }) { Text("Cancel") } })
    }
}
@Composable private fun NowPlaying(state: PlaybackState, favourite: Boolean, vm: StillViewModel, queue: () -> Unit) {
    val track = state.current ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Artwork(track, Modifier.widthIn(max = 360.dp).fillMaxWidth().aspectRatio(1f))
        Spacer(Modifier.height(28.dp))
        Text(track.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
        Text(track.artist, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        Text("Audius", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(20.dp))
        var dragging by remember(track.id) { mutableStateOf<Float?>(null) }
        Slider(value = dragging ?: state.position.toFloat().coerceIn(0f, state.duration.coerceAtLeast(1).toFloat()),
            onValueChange = { dragging = it }, onValueChangeFinished = { dragging?.let { vm.seek(it.toLong()) }; dragging = null },
            valueRange = 0f..state.duration.coerceAtLeast(1).toFloat(), enabled = state.duration > 0,
            modifier = Modifier.semantics { contentDescription = "Playback position" })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(duration((dragging?.toLong() ?: state.position)), style = MaterialTheme.typography.labelMedium)
            Text(duration(state.duration), style = MaterialTheme.typography.labelMedium)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Action(Icons.Default.Shuffle, if (state.shuffle) "Turn shuffle off" else "Turn shuffle on", tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, onClick = vm::shuffle)
            Action(Icons.Default.SkipPrevious, "Previous track", onClick = vm::previous)
            FilledIconButton(onClick = vm::toggle, enabled = state.connected, modifier = Modifier.size(68.dp)) {
                Icon(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (state.playing) "Pause" else "Play", Modifier.size(32.dp))
            }
            Action(Icons.Default.SkipNext, "Next track", onClick = vm::next)
            Action(if (state.repeat == 1) Icons.Default.RepeatOne else Icons.Default.Repeat,
                "Repeat ${when (state.repeat) { 1 -> "one"; 2 -> "all"; else -> "off" }}. Change repeat mode",
                tint = if (state.repeat != 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, onClick = vm::repeat)
        }
        if (state.buffering) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Connecting to Audius…", Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall) }
        state.error?.let { ErrorState(it, vm::toggle) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Action(if (favourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                if (favourite) "Remove from favourites" else "Add to favourites",
                tint = if (favourite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, onClick = { vm.favourite(track) })
            TextButton(onClick = queue) { Icon(Icons.AutoMirrored.Filled.QueueMusic, null); Text("Queue", Modifier.padding(start = 8.dp)) }
        }
        Spacer(Modifier.height(24.dp))
    }
}
@Composable private fun FullScreenSheet(title: String, dismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Action(Icons.Default.KeyboardArrowDown, "Close $title", onClick = dismiss)
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
                }
                content()
            }
        }
    }
}
@Composable private fun TrackRow(track: Track, favourite: Boolean, current: Boolean, onPlay: () -> Unit,
    onFavourite: () -> Unit, onQueue: () -> Unit, onPlaylist: () -> Unit, onRemove: (() -> Unit)? = null) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable(onClickLabel = "Play ${track.title}", role = Role.Button, onClick = onPlay)
        .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(track, Modifier.size(56.dp))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(track.unavailableReason ?: duration(track.durationMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            Action(Icons.Default.MoreVert, "Options for ${track.title}", onClick = { menu = true })
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(if (favourite) "Remove favourite" else "Add favourite") },
                    leadingIcon = { Icon(if (favourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null) },
                    onClick = { menu = false; onFavourite() })
                DropdownMenuItem(text = { Text("Add to queue") }, enabled = track.playable, onClick = { menu = false; onQueue() })
                DropdownMenuItem(text = { Text("Add to playlist") }, onClick = { menu = false; onPlaylist() })
                onRemove?.let { DropdownMenuItem(text = { Text("Remove from playlist") }, onClick = { menu = false; it() }) }
            }
        }
    }
}
@Composable private fun Artwork(track: Track, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.MusicNote, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
        AsyncImage(model = track.artwork, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}
@Composable private fun Action(icon: ImageVector, description: String, enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
        Icon(icon, description, tint = if (enabled) tint else MaterialTheme.colorScheme.outline)
    }
}
@Composable private fun EmptyState(title: String, detail: String, icon: ImageVector) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun Loading() { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
@Composable private fun ErrorState(message: String, retry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Text(message, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = retry) { Text("Retry") }
    }
}
private fun duration(ms: Long): String = if (ms <= 0) "—" else {
    val seconds = ms / 1000
    if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)
}
