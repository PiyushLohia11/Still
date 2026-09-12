@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.still.music.ui

import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.still.music.data.Playlist
import com.still.music.provider.Track
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlin.math.roundToInt
import kotlin.random.Random

@Composable fun StillTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFFA4B8A5), onPrimary = Color(0xFF152019),
        primaryContainer = Color(0xFF303D33), onPrimaryContainer = Color(0xFFD7E5D7),
        background = Color(0xFF121314), onBackground = Color(0xFFF5F5F5),
        surface = Color(0xFF121314), onSurface = Color(0xFFF5F5F5),
        surfaceVariant = Color(0xFF1E2022), onSurfaceVariant = Color(0xFFC2C5C8),
        surfaceContainer = Color(0xFF1E2022), surfaceContainerHigh = Color(0xFF252729),
        surfaceContainerHighest = Color(0xFF2B2D30), outlineVariant = Color(0xFF36393C),
        secondaryContainer = Color(0xFF303D33), onSecondaryContainer = Color(0xFFD7E5D7),
        outline = Color(0xFF727971)), shapes = Shapes(
            medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(20.dp)), content = content)
}
@Composable fun StillScreen(vm: StillViewModel) {
    val search by vm.search.collectAsStateWithLifecycle()
    val player by vm.playback.collectAsStateWithLifecycle()
    val favourites by vm.favourites.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val playlistTracks by vm.playlistTracks.collectAsStateWithLifecycle()
    val playbackHealth by vm.playbackHealth.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val preload by vm.preload.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val mixLoading by vm.mixLoading.collectAsStateWithLifecycle()
    val importState by vm.playlistImport.collectAsStateWithLifecycle()
    var importing by rememberSaveable { mutableStateOf(false) }
    var importName by rememberSaveable { mutableStateOf("") }
    var importInput by rememberSaveable { mutableStateOf("") }
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
            Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Still", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.headlineMedium)
                    Text("Music, with room to breathe.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Action(Icons.Default.Tune, "Preferences", onClick = { settings = true })
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column {
                player.current?.let { current ->
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(28.dp), shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Open Now Playing") { nowPlaying = true }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Artwork(current, Modifier.size(48.dp))
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(current.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.SemiBold, modifier = Modifier.basicMarquee())
                                Text(current.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (player.buffering) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else VisualizerBars(player.playing && player.connected && player.error == null,
                                Modifier.size(width = 20.dp, height = 20.dp), barCount = 4)
                            PlaybackButton(player.playing, player.connected, onClick = vm::toggle)
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
                    label = { Text("Search music") }, placeholder = { Text("Tracks or artists") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (search.query.isNotEmpty()) Action(Icons.Default.Close, "Clear search", onClick = { vm.query("") }) },
                    singleLine = true, shape = RoundedCornerShape(20.dp))
                Text("YouTube Music · experimental", modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (search.query.isBlank()) item { EmptyState("Find your next favourite", "Search songs by title or artist.", Icons.Default.Search) }
                    if (search.loading && search.tracks.isEmpty()) item { Loading() }
                    search.error?.let { error -> item { ErrorState(error, vm::retrySearch) } }
                    if (!search.loading && search.query.isNotBlank() && search.tracks.isEmpty() && search.error == null)
                        item { EmptyState("No tracks found", "Try another song title or artist.", Icons.Default.MusicNote) }
                    items(search.tracks, key = Track::id) { track ->
                        TrackRow(track, favourites.any { it.id == track.id }, player.current?.id == track.id,
                            onPlay = { vm.play(track, search.tracks) }, onFavourite = { vm.favourite(track) },
                            onQueue = { vm.enqueue(track) }, onPlaylist = { addTrack = track }, onDownload = { vm.download(track) })
                    }
                    if (search.more) item {
                        TextButton(onClick = vm::more, enabled = !search.loading, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(if (search.loading) "Loading…" else "Load more")
                        }
                    }
                }
            } else {
                TextButton(onClick = { importing = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (importState.running) "Imported ${importState.imported} of ${importState.total}" else "Import Playlist")
                }
                FilledTonalButton(onClick = vm::playSmartMix, enabled = !mixLoading && player.connected,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    if (mixLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.AutoAwesome, null)
                    Text(if (mixLoading) "Creating your mix..." else "Quick Mix / For You",
                        Modifier.padding(start = 8.dp))
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2 to "Tracks", 1 to "Playlists", 0 to "Favorites", 3 to "Downloads").forEach { (index, label) ->
                        FilterChip(selected = libraryTab == index, onClick = { libraryTab = index; playlistId = null },
                            shape = CircleShape, label = { Text(label, Modifier.padding(horizontal = 6.dp)) })
                    }
                }
                val selected = playlists.firstOrNull { it.id == playlistId }
                if (libraryTab == 3) {
                    DownloadsPanel(downloads, vm)
                } else if (libraryTab == 1 && selected == null) {
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
                                } }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { playlistId = list.id })
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
                                onDownload = { vm.download(track) }, onRemove = if (selected != null) ({ vm.removeFromPlaylist(selected.id, track) }) else null)
                        }
                    }
                }
            }
        }
    }
    FullScreenSheet("Now playing", { nowPlaying = false }, visible = nowPlaying && player.current != null, showHeader = false) {
        NowPlaying(player, favourites.any { it.id == player.current?.id }, vm,
            onClose = { nowPlaying = false }, onQueue = { queue = true }, onPlaylist = {
                addTrack = player.current
                nowPlaying = false
            })
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
    if (importing) AlertDialog(onDismissRequest = { importing = false }, title = { Text("Import Playlist") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (importState.running) {
                Text(if (importState.total == 0) "Reading playlist..." else "Imported ${importState.imported} of ${importState.total}")
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Checked ${importState.processed} songs. Unmatched and duplicate songs are skipped.")
            } else {
                OutlinedTextField(importName, { importName = it.take(100) }, label = { Text("Playlist name") }, singleLine = true)
                OutlinedTextField(importInput, { importInput = it }, label = { Text("Spotify or YouTube playlist link") },
                    placeholder = { Text("Paste a public playlist link, or a text/CSV song list") }, minLines = 4, maxLines = 8)
                if (importState.total > 0) Text("Imported ${importState.imported} of ${importState.total}")
                importState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            importState.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        } }, confirmButton = {
            if (importState.running) TextButton(onClick = vm::cancelPlaylistImport) { Text("Stop import") }
            else TextButton(enabled = importName.isNotBlank() && importInput.isNotBlank(),
                onClick = { vm.importPlaylist(importName, importInput) }) { Text("Import") }
        }, dismissButton = { TextButton(onClick = { importing = false }) { Text("Close") } })
    if (settings) AlertDialog(onDismissRequest = { settings = false }, title = { Text("Preferences") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Preload next track", Modifier.weight(1f))
                Switch(preload, onCheckedChange = vm::setPreload, modifier = Modifier.semantics { contentDescription = "Preload next track" })
            }
            Text("Buffers up to five seconds ahead in memory. Uses more mobile data. Use Download to save a full song for offline listening.")
            Spacer(Modifier.height(16.dp))
            Text("This session: ${playbackHealth.bufferWaits} buffer waits, ${playbackHealth.audioUnderruns} audio underruns.", style = MaterialTheme.typography.bodySmall)
            Text("Buffer waits include seeking. Underruns can indicate device or emulator load.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            Text("Still is free and ad-free. YouTube Music access is unofficial and may stop working when the service changes. Your library stays on this device. Powered by NewPipeExtractor (GPL-3.0-or-later).")
        } }, confirmButton = { TextButton(onClick = { settings = false }) { Text("Done") } })
    if (addTrack != null && !naming) AlertDialog(onDismissRequest = { addTrack = null }, title = { Text("Add to playlist") },
        text = { LazyColumn {
            if (playlists.isEmpty()) item { Text("Create your first playlist.") }
            items(playlists, key = Playlist::id) { list ->
                TextButton(onClick = { addTrack?.let { vm.addTrackToPlaylist(list.id, it) }; addTrack = null },
                    modifier = Modifier.fillMaxWidth()) { Text(list.name) }
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
@Composable private fun NowPlaying(state: PlaybackState, favourite: Boolean, vm: StillViewModel,
    onQueue: () -> Unit, onPlaylist: () -> Unit, onClose: () -> Unit) {
    val track = state.current ?: return
    var menu by remember(track.id) { mutableStateOf(false) }
    var volumeOpen by rememberSaveable { mutableStateOf(false) }
    var lyricsOpen by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val artSize = minOf(maxWidth - 64.dp, 360.dp, maxHeight * 0.46f)
    val breathingRoom = (maxHeight - artSize - 350.dp).coerceAtLeast(24.dp)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Action(Icons.Default.KeyboardArrowDown, "Close Now playing", onClick = onClose)
            Spacer(Modifier.weight(1f))
            Action(Icons.AutoMirrored.Filled.VolumeUp, "Music volume", onClick = { volumeOpen = !volumeOpen })
            AnimatedVisualizerBar(state.playing && state.connected && !state.buffering && state.error == null,
                Modifier.size(40.dp).padding(10.dp))
            Box {
                Action(Icons.Default.MoreVert, "Options for ${track.title}", onClick = { menu = true })
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Add to playlist") }, onClick = { menu = false; onPlaylist() })
                    DropdownMenuItem(text = { Text("Download") }, onClick = { menu = false; vm.download(track) })
                    DropdownMenuItem(text = { Text("Lyrics") }, onClick = { menu = false; lyricsOpen = !lyricsOpen })
                }
            }
        }
        AnimatedVisibility(volumeOpen, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            MusicVolumeControl(onError = { vm.message.value = it })
        }
        Artwork(track, Modifier.size(artSize).shadow(12.dp, RoundedCornerShape(28.dp)), cornerRadius = 28.dp)
        Spacer(Modifier.height(24.dp))
        Text(track.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Normal,
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(track.artist, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(breathingRoom))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Action(Icons.AutoMirrored.Filled.QueueMusic, "Queue", onClick = onQueue)
            Action(if (favourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                if (favourite) "Remove from favourites" else "Add to favourites",
                tint = if (favourite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                onClick = { vm.favourite(track) })
            Action(Icons.Default.Add, "Add to playlist", onClick = onPlaylist)
        }
        Spacer(Modifier.height(8.dp))
        var dragging by remember(track.id) { mutableStateOf<Float?>(null) }
        val smoothPosition by animateFloatAsState(state.position.toFloat(),
            animationSpec = tween(if (state.playing && dragging == null) 450 else 0, easing = LinearEasing),
            label = "Playback position")
        Slider(value = dragging ?: smoothPosition.coerceIn(0f, state.duration.coerceAtLeast(1).toFloat()),
            onValueChange = { dragging = it }, onValueChangeFinished = { dragging?.let { vm.seek(it.toLong()) }; dragging = null },
            valueRange = 0f..state.duration.coerceAtLeast(1).toFloat(), enabled = state.duration > 0,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)),
            thumb = { Box(Modifier.size(16.dp).background(Color.White, CircleShape)) },
            track = { slider ->
                SliderDefaults.Track(sliderState = slider, modifier = Modifier.height(3.dp),
                    colors = SliderDefaults.colors(activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)),
                    thumbTrackGapSize = 0.dp, drawStopIndicator = null)
            },
            modifier = Modifier.semantics { contentDescription = "Playback position" })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Crossfade(duration(dragging?.toLong() ?: state.position), animationSpec = tween(120), label = "Elapsed time") {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(duration(state.duration), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Action(Icons.Default.Shuffle, if (state.shuffle) "Turn shuffle off" else "Turn shuffle on", tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, onClick = vm::shuffle)
            Action(Icons.Default.SkipPrevious, "Previous track", onClick = vm::previous)
            PlaybackButton(state.playing, state.connected, size = 64.dp, prominent = false, onClick = vm::toggle)
            Action(Icons.Default.SkipNext, "Next track", onClick = vm::next)
            Action(if (state.repeat == 1) Icons.Default.RepeatOne else Icons.Default.Repeat,
                "Repeat ${when (state.repeat) { 1 -> "one"; 2 -> "all"; else -> "off" }}. Change repeat mode",
                tint = if (state.repeat != 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, onClick = vm::repeat)
        }
        if (state.buffering) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Connecting to music…", Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall) }
        state.error?.let { ErrorState(it, vm::toggle) }
        AnimatedVisibility(lyricsOpen) { LyricsPanel(track, state.position, vm) }
        Spacer(Modifier.height(16.dp))
    }
    }
}
@Composable fun AnimatedVisualizerBar(isPlaying: Boolean, modifier: Modifier = Modifier) {
    VisualizerBars(isPlaying, modifier, barCount = 5)
}

@Composable private fun VisualizerBars(isPlaying: Boolean, modifier: Modifier, barCount: Int) {
    val rhythms = remember(barCount) {
        List(barCount) { Random.nextInt(350, 850) to Random.nextDouble(0.55, 1.0).toFloat() }
    }
    val transition = rememberInfiniteTransition(label = "Visualizer rhythm")
    val levels = rhythms.mapIndexed { index, (duration, peak) ->
        transition.animateFloat(
            initialValue = 0.1f,
            targetValue = peak,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = duration, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(index * 137, StartOffsetType.FastForward)),
            label = "Visualizer bar $index")
    }
    val activity = animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "Visualizer activity")
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.size(width = 32.dp, height = 28.dp).clearAndSetSemantics { }) {
        val barWidth = size.width / (barCount * 2 - 1)
        val baseline = minOf(4.dp.toPx(), size.height)
        val amount = activity.value
        levels.forEachIndexed { index, level ->
            val height = baseline + (size.height - baseline) *
                if (amount > 0f) level.value * amount else 0f
            drawRoundRect(
                color = color,
                topLeft = Offset(index * barWidth * 2f, size.height - height),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f))
        }
    }
}

@Composable private fun MusicVolumeControl(onError: (String) -> Unit) {
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

    // Poll the public API only while visible. This also catches hardware keys and
    // output-device changes without relying on undocumented volume broadcasts.
    val volumeChanges = remember(audio) {
        flow {
            while (true) {
                emit(readVolume())
                delay(250)
            }
        }
    }
    val observed by volumeChanges.collectAsStateWithLifecycle(initialValue = remember(audio) { readVolume() })
    var volume by remember(audio) { mutableIntStateOf(observed.first) }
    val maximum = observed.second.coerceAtLeast(0)
    var lastAudible by rememberSaveable { mutableIntStateOf(volume.coerceAtLeast(1)) }
    LaunchedEffect(observed) {
        volume = observed.first
        if (volume > 0) lastAudible = volume
    }
    val enabled = maximum > 0 && !audio.isVolumeFixed
    val percentage = if (maximum > 0) (volume * 100f / maximum).roundToInt() else 0

    fun setVolume(value: Int) {
        try {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, value.coerceIn(0, maximum), 0)
            volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (volume > 0) lastAudible = volume
        } catch (_: SecurityException) {
            onError("Media volume could not be changed. Use your device's volume controls.")
        }
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Action(if (volume == 0) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
            if (volume == 0) "Unmute music" else "Mute music", enabled = enabled) {
            if (volume > 0) {
                lastAudible = volume
                setVolume(0)
            } else setVolume(lastAudible.coerceIn(1, maximum))
        }
        Slider(value = volume.toFloat().coerceIn(0f, maximum.coerceAtLeast(1).toFloat()),
            onValueChange = { setVolume(it.roundToInt()) }, enabled = enabled,
            valueRange = 0f..maximum.coerceAtLeast(1).toFloat(),
            steps = (maximum - 1).coerceAtLeast(0),
            modifier = Modifier.weight(1f).semantics {
                contentDescription = "Music volume"
                stateDescription = "$percentage percent"
            })
        Text("$percentage%", style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.widthIn(min = 48.dp).padding(start = 8.dp))
    }
}
@Composable private fun FullScreenSheet(title: String, dismiss: () -> Unit, visible: Boolean = true, showHeader: Boolean = true,
    content: @Composable ColumnScope.() -> Unit) {
    val visibility = remember { MutableTransitionState(false) }
    LaunchedEffect(visible) { visibility.targetState = visible }
    if (!visibility.currentState && !visibility.targetState && visibility.isIdle) return
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        AnimatedVisibility(visibleState = visibility,
            enter = slideInVertically(tween(360, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(220)),
            exit = slideOutVertically(tween(280, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(220))) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(
                if (showHeader) listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background)
                else listOf(Color(0xFF070807), Color(0xFF100D09), Color(0xFF352016), Color(0xFF392021))))
                .safeDrawingPadding().imePadding()) {
                if (showHeader) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Action(Icons.Default.KeyboardArrowDown, "Close $title", onClick = dismiss)
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
                }
                content()
            }
        }
        }
    }
}
@Composable private fun TrackRow(track: Track, favourite: Boolean, current: Boolean, onPlay: () -> Unit,
    onFavourite: () -> Unit, onQueue: () -> Unit, onPlaylist: () -> Unit, onRemove: (() -> Unit)? = null, onDownload: (() -> Unit)? = null) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        .clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
        .clickable(onClickLabel = "Play ${track.title}", role = Role.Button, onClick = onPlay)
        .padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
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
                onDownload?.let { DropdownMenuItem(text = { Text("Download for offline") }, enabled = track.playable, onClick = { menu = false; it() }) }
                onRemove?.let { DropdownMenuItem(text = { Text("Remove from playlist") }, onClick = { menu = false; it() }) }
            }
        }
    }
}
@Composable private fun Artwork(track: Track, modifier: Modifier, cornerRadius: Dp = 20.dp) {
    Box(modifier.clip(RoundedCornerShape(cornerRadius)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.MusicNote, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
        AsyncImage(model = track.artwork, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}
@Composable private fun PlaybackButton(playing: Boolean, enabled: Boolean, size: Dp = 48.dp,
    prominent: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "Playback press")
    FilledIconButton(onClick = onClick, enabled = enabled, shape = CircleShape,
        colors = if (prominent) IconButtonDefaults.filledIconButtonColors() else
            IconButtonDefaults.filledIconButtonColors(containerColor = Color.Transparent, contentColor = Color.White),
        interactionSource = interaction, modifier = Modifier.size(size).scale(scale)
            .semantics { contentDescription = if (playing) "Pause" else "Play" }) {
        Crossfade(playing, animationSpec = tween(160), label = "Playback icon") { active ->
            Icon(if (active) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null, modifier = Modifier.size(size * 0.45f))
        }
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
