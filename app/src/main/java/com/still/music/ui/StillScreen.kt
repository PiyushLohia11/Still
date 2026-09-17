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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
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
import coil3.Image
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.still.music.provider.artworkUrls
import com.still.music.data.Playlist
import com.still.music.provider.Track
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlin.math.roundToInt
import kotlin.random.Random

internal enum class Overlay { Player, Queue, Equalizer, Preferences, Import }

@Composable fun StillScreen(vm: StillViewModel) {
    val search by vm.search.collectAsStateWithLifecycle()
    val player by vm.playback.collectAsStateWithLifecycle()
    val favourites by vm.favourites.collectAsStateWithLifecycle()
    val favouriteIds = remember(favourites) { favourites.mapTo(hashSetOf(), Track::id) }
    val recents by vm.recents.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val playlistContent by vm.playlistContent.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val mixLoading by vm.mixLoading.collectAsStateWithLifecycle()
    val importState by vm.playlistImport.collectAsStateWithLifecycle()
    var overlay by rememberSaveable { mutableStateOf<Overlay?>(null) }
    var equalizerParent by rememberSaveable { mutableStateOf(Overlay.Preferences) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var libraryTab by rememberSaveable { mutableIntStateOf(1) }
    var playlistId by rememberSaveable { mutableStateOf<Long?>(null) }
    var addTrack by remember { mutableStateOf<Track?>(null) }
    var naming by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf<Playlist?>(null) }
    var delete by remember { mutableStateOf<Playlist?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val tabState = rememberSaveableStateHolder()
    val overlayState = rememberSaveableStateHolder()
    LaunchedEffect(tab) { focusManager.clearFocus() }
    LaunchedEffect(playlistId) { vm.selectPlaylist(playlistId) }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value = null } }
    BackHandler(tab != 0 && (tab != 1 || playlistId == null) && overlay == null) { tab = 0 }
    BackHandler(tab == 1 && playlistId != null && overlay == null) { playlistId = null }
    val dismissOverlay: () -> Unit = {
        overlay = when (overlay) {
            Overlay.Queue -> if (player.current != null) Overlay.Player else null
            Overlay.Equalizer -> equalizerParent
            else -> null
        }
    }
    LaunchedEffect(player.current, overlay) {
        if (player.current == null && overlay in listOf(Overlay.Player, Overlay.Queue)) overlay = null
    }
    var playerPoster by remember(player.current?.id, player.current?.artwork) { mutableStateOf<Image?>(null) }
    val playerGradient = rememberPlayerGradient(playerPoster)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (tab != 1 || playlistId == null) {
                Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.GraphicEq, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Text("still", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).padding(start = 10.dp))
                    Action(Icons.Default.Tune, "Preferences", onClick = { overlay = Overlay.Preferences })
                }
            } else {
                Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars))
            }
        },
        snackbarHost = { if (overlay == null) SnackbarHost(snackbar) },
        bottomBar = {
            Column {
                player.current?.let { current ->
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(20.dp), shadowElevation = 0.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(Modifier.fillMaxWidth().swipeVerticalAction(up = true) { overlay = Overlay.Player }.swipeTracks(current.id, player.connected, vm::previousTrack, vm::next).clickable(role = Role.Button, onClickLabel = "Open Now Playing") { overlay = Overlay.Player }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Artwork(current, Modifier.size(48.dp), cornerRadius = 14.dp)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(current.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.SemiBold, modifier = Modifier.basicMarquee())
                                Text(current.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (player.buffering) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            else VisualizerBars(player.playing && player.connected && player.error == null,
                                Modifier.size(width = 24.dp, height = 24.dp), barCount = 4)
                            Spacer(Modifier.width(12.dp))
                            PlaybackButton(player.playing, player.connected, onClick = vm::toggle)
                            Action(Icons.Default.SkipNext, "Next track", enabled = player.connected && player.queue.size > 1, onClick = vm::next)
                        }
                    }
                }
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest, tonalElevation = 0.dp) {
                    NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Home") })
                    NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Default.Search, null) }, label = { Text("Search") })
                    NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.LibraryMusic, null) }, label = { Text("Library") })
                }
            }
        }
    ) { padding ->
        tabState.SaveableStateProvider(tab) {
        Column(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize().imePadding()) {
            if (tab == 0) {
                HomePage(vm, onPlaylist = { addTrack = it },
                    onLibraryPlaylist = { playlistId = it; libraryTab = 1; tab = 1 })
            } else if (tab == 2) {
                PageHeading("Find your sound.", "Play a song. Discover what belongs beside it.")
                OutlinedTextField(value = search.query, onValueChange = vm::query,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    label = { Text("Search music") }, placeholder = { Text("Tracks or artists") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (search.query.isNotEmpty()) Action(Icons.Default.Close, "Clear search", onClick = { vm.query("") }) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        if (search.query.isNotBlank()) vm.retrySearch()
                        focusManager.clearFocus()
                    }),
                    singleLine = true, shape = RoundedCornerShape(20.dp))
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (search.query.isBlank()) item { EmptyState("Find your next favourite", "Search songs by title or artist.", Icons.Default.Search) }
                    if (search.loading && search.tracks.isEmpty()) item { Loading() }
                    search.error?.let { error -> item { ErrorState(error, vm::retrySearch) } }
                    if (!search.loading && search.query.isNotBlank() && search.tracks.isEmpty() && search.error == null)
                        item { EmptyState("No tracks found", "Try another song title or artist.", Icons.Default.MusicNote) }
                    items(search.tracks, key = Track::id) { track ->
                        TrackRow(track, (track.id in favouriteIds), player.current?.id == track.id,
                            onPlay = { focusManager.clearFocus(); vm.playSearch(track) }, onFavourite = { vm.favourite(track) },
                            onQueue = { vm.enqueue(track) }, onPlaylist = { addTrack = track }, onDownload = { vm.download(track) })
                    }
                    if (search.more) item {
                        TextButton(onClick = vm::more, enabled = !search.loading, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(if (search.loading) "Loading…" else "Load more")
                        }
                    }
                }
            } else {
                val selected = playlists.firstOrNull { it.id == playlistId }
                if (selected == null) {
                    PageHeading("Your collection.", "The music you come back to.")
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(onClick = vm::playSmartMix, enabled = !mixLoading && player.connected && (favourites.isNotEmpty() || recents.isNotEmpty()), modifier = Modifier.weight(1f).height(48.dp)) {
                            if (mixLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.AutoAwesome, null)
                            Text("Quick Mix", Modifier.padding(start = 8.dp))
                        }
                        OutlinedButton(onClick = { overlay = Overlay.Import }, modifier = Modifier.weight(1f).height(48.dp)) {
                            Icon(Icons.Default.Download, null)
                            Text(if (importState.running) "${importState.imported}/${importState.total}" else "Import", Modifier.padding(start = 8.dp))
                        }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1 to "Playlists", 0 to "Favorites", 2 to "Recently played", 3 to "Downloads").forEach { (index, label) ->
                            FilterChip(selected = libraryTab == index, onClick = { libraryTab = index; playlistId = null },
                                shape = CircleShape, label = { Text(label, Modifier.padding(horizontal = 6.dp)) })
                        }
                    }
                }
                if (libraryTab == 3) {
                    DownloadsPanel(downloads, vm)
                } else if (libraryTab == 1 && selected == null) {
                    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Your playlists", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                    Text("${playlists.size} ${if (playlists.size == 1) "collection" else "collections"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        item { OutlinedButton(onClick = { naming = true }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Icon(Icons.Default.Add, null); Text("New playlist", Modifier.padding(start = 8.dp), fontWeight = FontWeight.SemiBold) } }
                        if (playlists.isEmpty()) item { EmptyState("Make a little collection", "Create a playlist, then add tracks from search.", Icons.Default.LibraryMusic) }
                        items(playlists, key = Playlist::id) { list ->
                            var menu by remember { mutableStateOf(false) }
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { playlistId = list.id }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(
                                    androidx.compose.ui.graphics.Brush.linearGradient(listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        Color.hsv((list.id * 67 % 360).toFloat(), 0.35f, 0.32f)))), contentAlignment = Alignment.Center) {
                                    Text(list.name.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                                    Text(list.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("Playlist", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                                }
                                Box {
                                    Action(Icons.Default.MoreVert, "Playlist options for ${list.name}", onClick = { menu = true })
                                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                                        DropdownMenuItem(text = { Text("Download playlist") },
                                            onClick = { menu = false; vm.downloadPlaylist(list.id) })
                                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; rename = list; naming = true })
                                        DropdownMenuItem(text = { Text("Delete playlist") }, onClick = { menu = false; delete = list })
                                    }
                                }
                            }
                        }
                    }
                } else if (selected != null) {
                    key(selected.id) {
                        PlaylistPage(selected, playlistContent.tracks.takeIf { playlistContent.id == selected.id }.orEmpty(), favourites, player, vm,
                            loading = playlistContent.id != selected.id || playlistContent.loading,
                            error = playlistContent.error.takeIf { playlistContent.id == selected.id },
                            onBack = { playlistId = null }, onAdd = { tab = 2 },
                            onPlaylist = { addTrack = it },
                            onRename = { rename = selected; naming = true }, onDelete = { delete = selected })
                    }
                } else {
                    val tracks = if (libraryTab == 0) favourites else recents
                    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                        item { Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if (libraryTab == 0) "Favourites" else "Recently played", style = MaterialTheme.typography.titleLarge)
                                Text("${tracks.size} songs", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            FilledTonalIconButton(onClick = { vm.playShuffled(tracks) }, enabled = tracks.any(Track::playable) && player.connected) {
                                Icon(Icons.Default.Shuffle, "Shuffle collection")
                            }
                        } }
                        if (tracks.isEmpty()) item { EmptyState(
                            if (libraryTab == 0) "Keep what you love" else if (libraryTab == 2) "Your listening starts here" else "A quiet playlist",
                            if (libraryTab == 0) "Tap a track’s heart to save it here." else if (libraryTab == 2) "Tracks appear after playback begins." else "Add tracks using their options menu.",
                            Icons.Default.MusicNote) }
                        items(tracks, key = Track::id) { track ->
                            TrackRow(track, (track.id in favouriteIds), player.current?.id == track.id,
                                { vm.play(track, tracks) }, { vm.favourite(track) }, { vm.enqueue(track) }, { addTrack = track },
                                onDownload = { vm.download(track) })
                        }
                    }
                }
            }
        }
    }
    }
    overlay?.let { destination ->
        val isPlayer = destination == Overlay.Player || destination == Overlay.Queue
        FullScreenSheet(title = when (destination) {
            Overlay.Player -> "Now playing"
            Overlay.Queue -> "Queue"
            Overlay.Equalizer -> "Equalizer"
            Overlay.Preferences -> "Preferences"
            Overlay.Import -> "Import playlist"
        }, dismiss = dismissOverlay, showHeader = !isPlayer,
            backgroundColors = if (isPlayer) playerGradient else null, snackbar = snackbar) {
            overlayState.SaveableStateProvider(destination) {
            when (destination) {
                Overlay.Player -> NowPlaying(player, player.current?.id in favouriteIds, vm,
                    onArtworkLoaded = { playerPoster = it }, onClose = dismissOverlay,
                    onQueue = { overlay = Overlay.Queue },
                    onEqualizer = { equalizerParent = Overlay.Player; overlay = Overlay.Equalizer },
                    onPlaylist = { addTrack = player.current; overlay = null })
                Overlay.Queue -> QueuePlayer(player, player.current?.id in favouriteIds, vm,
                    onClose = dismissOverlay, onArtworkLoaded = { playerPoster = it })
                Overlay.Equalizer -> EqualizerPanel(vm.equalizer)
                Overlay.Preferences -> PreferencesPanel(vm) {
                    equalizerParent = Overlay.Preferences; overlay = Overlay.Equalizer
                }
                Overlay.Import -> ImportPanel(vm) { id ->
                    playlistId = id; libraryTab = 1; tab = 1; overlay = null
                }
            }
            }
        }
    }
    if (addTrack != null && !naming) AlertDialog(onDismissRequest = { addTrack = null },
        icon = { Icon(Icons.Default.PlaylistAdd, null) }, title = { Text("Add to playlist") },
        text = { LazyColumn {
            if (playlists.isEmpty()) item { Text("Create your first playlist.") }
            items(playlists, key = Playlist::id) { list ->
                ListItem(headlineContent = { Text(list.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { Icon(Icons.Default.LibraryMusic, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = { Icon(Icons.Default.Add, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable {
                        addTrack?.let { vm.addTrackToPlaylist(list.id, it) }; addTrack = null
                    })
            }
        } }, confirmButton = { TextButton(onClick = { naming = true }) { Text("New playlist") } },
        dismissButton = { TextButton(onClick = { addTrack = null }) { Text("Cancel") } })
    if (naming) {
        var name by rememberSaveable(rename?.id) { mutableStateOf(rename?.name ?: "") }
        AlertDialog(onDismissRequest = { naming = false; rename = null },
            icon = { Icon(Icons.Default.LibraryMusic, null) }, title = { Text(if (rename == null) "New playlist" else "Rename playlist") },
            text = { OutlinedTextField(name, { name = it.take(100) }, label = { Text("Playlist name") },
                supportingText = { Text("${name.length}/100 · Give your collection a name") }, singleLine = true) },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
                val list = rename
                if (list == null) vm.createPlaylist(name, addTrack) else vm.renamePlaylist(list.id, name)
                naming = false; rename = null; addTrack = null
            }) { Text("Save") } }, dismissButton = { TextButton(onClick = { naming = false; rename = null }) { Text("Cancel") } })
    }
    delete?.let { list ->
        AlertDialog(onDismissRequest = { delete = null }, title = { Text("Delete ${list.name}?") },
            icon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
            text = { Text("This removes the playlist from this device. Your favourites remain.") },
            confirmButton = { TextButton(onClick = { vm.deletePlaylist(list.id); if (playlistId == list.id) playlistId = null; delete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { delete = null }) { Text("Cancel") } })
    }
}
@Composable private fun PlayerHandle(description: String, onClose: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(48.dp).swipeVerticalAction(up = false, action = onClose).clickable(role = Role.Button,
        onClickLabel = description, onClick = onClose).semantics { contentDescription = description },
        contentAlignment = Alignment.Center) {
        Box(Modifier.size(32.dp, 4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)))
    }
}

@Composable private fun Transport(state: PlaybackState, vm: StillViewModel) {
    Row(Modifier.fillMaxWidth().height(88.dp), horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically) {
        Action(Icons.Default.SkipPrevious, "Previous track", enabled = state.connected,
            size = 64.dp, iconSize = 36.dp, onClick = vm::previous)
        PlaybackButton(state.playing, state.connected, size = 76.dp, prominent = true, onClick = vm::toggle)
        Action(Icons.Default.SkipNext, "Next track", enabled = state.connected,
            size = 64.dp, iconSize = 36.dp, onClick = vm::next)
    }
}

@Composable private fun QueueModes(state: PlaybackState, vm: StillViewModel) {
    Action(Icons.Default.Shuffle, if (state.shuffle) "Turn shuffle off" else "Turn shuffle on",
        enabled = state.connected, tint = Color.White.copy(alpha = if (state.shuffle) 1f else 0.45f), onClick = vm::shuffle)
    Action(if (state.repeat == 1) Icons.Default.RepeatOne else Icons.Default.Repeat,
        "Repeat ${when (state.repeat) { 1 -> "one"; 2 -> "all"; else -> "off" }}. Change repeat mode",
        enabled = state.connected, tint = Color.White.copy(alpha = if (state.repeat != 0) 1f else 0.45f), onClick = vm::repeat)
}

@Composable private fun NowPlaying(state: PlaybackState, favourite: Boolean, vm: StillViewModel,
    onQueue: () -> Unit, onPlaylist: () -> Unit, onClose: () -> Unit, onEqualizer: () -> Unit, onArtworkLoaded: (Image?) -> Unit) {
    val track = state.current ?: return
    var menu by remember(track.id) { mutableStateOf(false) }
    var lyricsOpen by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val artSize = minOf(maxWidth - 56.dp, maxHeight * 0.43f, 380.dp).coerceAtLeast(140.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            PlayerHandle("Close Now playing", onClose)
            Text("NOW PLAYING", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 24.dp))
            Artwork(track, Modifier.size(artSize).shadow(20.dp, RoundedCornerShape(28.dp))
                .swipeTracks(track.id, state.connected, vm::previousTrack, vm::next),
                cornerRadius = 28.dp, highQuality = true, onLoaded = onArtworkLoaded)
            Column(Modifier.widthIn(max = 620.dp).fillMaxWidth().padding(horizontal = 28.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(track.title, style = MaterialTheme.typography.headlineSmall,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(track.artist, style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box {
                        Action(Icons.Default.MoreHoriz, "Options for ${track.title}", onClick = { menu = true })
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Add to playlist") }, onClick = { menu = false; onPlaylist() })
                            DropdownMenuItem(text = { Text("Equalizer") }, onClick = { menu = false; onEqualizer() })
                        }
                    }
                }
                PlaybackProgress(track.id, state.duration, state.connected, vm)
                Transport(state, vm)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    QueueModes(state, vm)
                    Action(if (favourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        if (favourite) "Remove from favourites" else "Add to favourites",
                        tint = if (favourite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        onClick = { vm.favourite(track) })
                    Action(Icons.AutoMirrored.Filled.QueueMusic, "Queue", onClick = onQueue)
                }
                VolumeControlBar(onError = { vm.message.value = it }, compact = true)
                GlassCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { lyricsOpen = !lyricsOpen }) {
                            Icon(Icons.AutoMirrored.Filled.Subject, null)
                            Text(if (lyricsOpen) "Hide lyrics" else "Show lyrics", Modifier.padding(start = 8.dp))
                        }
                        DownloadButton(track, vm)
                    }
                    if (lyricsOpen) PlayingLyrics(track, vm)
                }
                if (state.buffering) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
                state.error?.let { ErrorState(it, vm::toggle) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable internal fun PlaybackProgress(trackId: String, durationMs: Long, connected: Boolean, vm: StillViewModel) {
    val position by vm.playbackPosition.collectAsStateWithLifecycle()
    Column {
        var dragging by remember(trackId) { mutableStateOf<Float?>(null) }
        SquigglySlider(value = dragging ?: position.toFloat().coerceIn(0f, durationMs.coerceAtLeast(1).toFloat()),
            onValueChange = { dragging = it },
            onValueChangeFinished = { dragging?.let { vm.seek(it.toLong()) }; dragging = null },
            onValueChangeCanceled = { dragging = null },
            valueRange = 0f..durationMs.coerceAtLeast(1).toFloat(), enabled = connected && durationMs > 0,
            waveAmplitude = 0.dp, thumbRadius = 5.dp, trackStrokeWidth = 4.dp,
            activeTrackColor = MaterialTheme.colorScheme.primary, formatTime = ::duration)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(playerTime(dragging?.toLong() ?: position), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
            Text("−" + playerTime((durationMs - (dragging?.toLong() ?: position)).coerceAtLeast(0)),
                style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
        }
    }
}

@Composable private fun PlayingLyrics(track: Track, vm: StillViewModel) {
    val position by vm.playbackPosition.collectAsStateWithLifecycle()
    LyricsPanel(track, position, vm)
}

private fun playerTime(ms: Long): String = "${ms.coerceAtLeast(0) / 60000}:${((ms.coerceAtLeast(0) / 1000) % 60).toString().padStart(2, '0')}"

@Composable private fun ColumnScope.QueuePlayer(state: PlaybackState, favourite: Boolean, vm: StillViewModel,
    onClose: () -> Unit, onArtworkLoaded: (Image?) -> Unit) {
    PlayerHandle("Close Queue", onClose)
    state.current?.let { track ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(track, Modifier.size(52.dp).clickable(onClick = onClose), cornerRadius = 6.dp,
                highQuality = true, onLoaded = onArtworkLoaded)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.55f))
            }
            Action(if (favourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                if (favourite) "Remove from favourites" else "Add to favourites", onClick = { vm.favourite(track) })
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Playing Next", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(if (state.shuffle) "Shuffle on · Editable queue order" else "${state.queue.size} tracks",
                style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
        }
        QueueModes(state, vm)
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(state.index) { if (state.queue.isNotEmpty()) listState.animateScrollToItem(state.index.coerceIn(0, state.queue.lastIndex)) }
    LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.queue.isEmpty()) item { Text("The queue is empty", Modifier.padding(vertical = 24.dp)) }
        itemsIndexed(state.queue) { index, track ->
            var menu by remember(index, track.id) { mutableStateOf(false) }
            SwipeActions("$index:${track.id}", "Move up", "Move down",
                onRight = { if (index > 0) vm.move(index, index - 1) },
                onLeft = { if (index < state.queue.lastIndex) vm.move(index, index + 1) }, enabled = state.connected) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(if (index == state.index) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.45f))
                    .clickable(onClickLabel = "Play ${track.title}") { vm.jump(index) }.padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Artwork(track, Modifier.size(48.dp), cornerRadius = 12.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f))
                    }
                    if (index == state.index) AnimatedVisualizerBar(state.playing, Modifier.size(16.dp))
                    Box {
                        Action(Icons.Default.MoreHoriz, "Queue options for ${track.title}", tint = Color.White.copy(alpha = 0.4f), onClick = { menu = true })
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Move up") }, enabled = index > 0,
                                onClick = { menu = false; vm.move(index, index - 1) })
                            DropdownMenuItem(text = { Text("Move down") }, enabled = index < state.queue.lastIndex,
                                onClick = { menu = false; vm.move(index, index + 1) })
                            DropdownMenuItem(text = { Text("Remove from queue") }, onClick = { menu = false; vm.remove(index) })
                        }
                    }
                }
            }
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp)) {
        Transport(state, vm)
        VolumeControlBar(onError = { vm.message.value = it }, compact = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Action(Icons.AutoMirrored.Filled.QueueMusic, "Close Queue", onClick = onClose)
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
    val levels = if (isPlaying) {
    val transition = rememberInfiniteTransition(label = "Visualizer rhythm")
    rhythms.mapIndexed { index, (duration, peak) ->
        transition.animateFloat(
            initialValue = 0.1f,
            targetValue = peak,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = duration, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(index * 137, StartOffsetType.FastForward)),
            label = "Visualizer bar $index")
    }
    } else emptyList()
    val activity = animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "Visualizer activity")
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.size(width = 32.dp, height = 28.dp).clearAndSetSemantics { }) {
        val barWidth = size.width / (barCount * 2 - 1)
        val baseline = minOf(4.dp.toPx(), size.height)
        val amount = activity.value
        repeat(barCount) { index ->
            val height = baseline + (size.height - baseline) *
                (levels.getOrNull(index)?.value ?: 0f) * amount
            drawRoundRect(
                color = color,
                topLeft = Offset(index * barWidth * 2f, size.height - height),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f))
        }
    }
}

@Composable internal fun FullScreenSheet(title: String, dismiss: () -> Unit, showHeader: Boolean = true,
    backgroundColors: List<Color>? = null, snackbar: SnackbarHostState? = null,
    content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false)) {
        val base = MaterialTheme.colorScheme.background
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.rgb(12, 16, 15)))
        }
        // Keep the root opaque throughout transitions, including system-bar insets.
        Surface(Modifier.fillMaxSize().semantics { paneTitle = title }, color = base) {
            var entered by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { entered = true }
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(
                backgroundColors ?: listOf(MaterialTheme.colorScheme.surfaceContainer, base)))) {
                AnimatedVisibility(entered, enter = fadeIn(tween(180)) + slideInVertically(tween(260)) { it / 12 }) {
                    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                        if (showHeader) Row(Modifier.fillMaxWidth().swipeVerticalAction(up = false, action = dismiss)
                            .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Action(Icons.AutoMirrored.Filled.ArrowBack, "Close $title", onClick = dismiss)
                            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
                        }
                        Box(Modifier.weight(1f).fillMaxWidth()) { Column(Modifier.fillMaxSize(), content = content) }
                        snackbar?.let { SnackbarHost(it) }
                    }
                }
            }
        }
    }
}
@Composable internal fun TrackRow(track: Track, favourite: Boolean, current: Boolean, onPlay: () -> Unit,
    onFavourite: () -> Unit, onQueue: () -> Unit, onPlaylist: () -> Unit, onRemove: (() -> Unit)? = null, onDownload: (() -> Unit)? = null) {
    var menu by remember(track.id) { mutableStateOf(false) }
    SwipeActions(track.id, "Queue", if (favourite) "Unfavourite" else "Favourite",
        onRight = onQueue, onLeft = onFavourite, enabled = track.playable) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp)).background(if (current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .clickable(enabled = track.playable, onClickLabel = "Play ${track.title}", role = Role.Button, onClick = onPlay)
            .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(track, Modifier.size(56.dp), cornerRadius = 14.dp)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
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
}
@Composable internal fun Artwork(track: Track, modifier: Modifier, cornerRadius: Dp = 20.dp,
    highQuality: Boolean = false, onLoaded: (Image?) -> Unit = {}) {
    val context = LocalContext.current
    val targetSize = if (highQuality) 1200 else 480
    val urls = remember(track.id, track.artwork, targetSize) {
        artworkUrls(track.artwork, targetSize, track.id)
    }
    var candidate by remember(track.id, urls) { mutableIntStateOf(0) }
    val request = remember(context, urls, candidate, highQuality) {
        ImageRequest.Builder(context).data(urls.getOrNull(candidate))
            .size(targetSize).allowHardware(!highQuality).build()
    }
    Box(modifier.clip(RoundedCornerShape(cornerRadius)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.MusicNote, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
        key(track.id, urls, candidate) {
            AsyncImage(model = request, contentDescription = if (highQuality) "Cover art for " + track.title else null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                onSuccess = {
                    val image = it.result.image
                    // YouTube can return a tiny placeholder with HTTP 200 for unavailable sizes.
                    if (image.width <= 120 && image.height <= 120 && candidate < urls.lastIndex) candidate++
                    else onLoaded(image)
                },
                onError = {
                    if (candidate < urls.lastIndex) candidate++ else onLoaded(null)
                })
        }
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
    size: Dp = 48.dp, iconSize: Dp = 24.dp, tint: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.sizeIn(minWidth = size, minHeight = size)) {
        Icon(icon, description, tint = if (enabled) tint else MaterialTheme.colorScheme.outline, modifier = Modifier.size(iconSize))
    }
}
@Composable private fun EmptyState(title: String, detail: String, icon: ImageVector) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
