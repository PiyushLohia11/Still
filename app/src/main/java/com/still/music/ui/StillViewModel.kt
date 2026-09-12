package com.still.music.ui

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.*
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.still.music.StillApp
import com.still.music.data.Playlist
import com.still.music.playback.*
import com.still.music.provider.Track
import com.still.music.provider.YouTubeProvider
import com.still.music.provider.PlaylistImporter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class LyricsState(val trackId: String = "", val loading: Boolean = false,
    val value: com.still.music.lyrics.Lyrics? = null, val error: String? = null)

data class PlaylistImportState(val running: Boolean = false, val total: Int = 0,
    val processed: Int = 0, val imported: Int = 0, val playlistId: Long? = null,
    val error: String? = null)

data class SearchState(val query: String = "", val tracks: List<Track> = emptyList(), val loading: Boolean = false,
    val error: String? = null, val more: Boolean = false)
data class PlaybackState(val queue: List<Track> = emptyList(), val index: Int = 0, val playing: Boolean = false,
    val buffering: Boolean = false, val position: Long = 0, val duration: Long = 0, val shuffle: Boolean = false,
    val repeat: Int = 0, val error: String? = null, val connected: Boolean = false) {
    val current get() = queue.getOrNull(index)
}
class StillViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as StillApp).graph
    private val _playlistImport = MutableStateFlow(PlaylistImportState())
    val playlistImport = _playlistImport.asStateFlow()
    private var importJob: Job? = null
    private val radioPreferences = app.getSharedPreferences("auto_radio", android.content.Context.MODE_PRIVATE)
    private val _autoPlayRadio = MutableStateFlow(radioPreferences.getBoolean("enabled", true))
    val autoPlayRadio = _autoPlayRadio.asStateFlow()
    private var radioJob: Job? = null
    private var radioAttempt: Pair<String, List<String>>? = null
    private val _search = MutableStateFlow(SearchState())
    val search = _search.asStateFlow()
    private val _playback = MutableStateFlow(PlaybackState())
    val playback = _playback.asStateFlow()
    val playbackHealth = graph.playbackHealth.asStateFlow()
    val downloads = graph.downloads.entries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val lyrics = MutableStateFlow(LyricsState())
    private var lyricsJob: Job? = null
    private var queueDirty = true
    private var cachedQueue = emptyList<Track>()
    val favourites = graph.library.favourites.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recents = graph.library.recents.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val playlists = graph.library.playlists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val preload = graph.preferences.preloading.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val message = MutableStateFlow<String?>(null)
    private val _mixLoading = MutableStateFlow(false)
    val mixLoading = _mixLoading.asStateFlow()
    private val selectedPlaylist = MutableStateFlow<Long?>(null)
    @OptIn(ExperimentalCoroutinesApi::class)
    val playlistTracks = selectedPlaylist.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else graph.library.tracks(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private var searchJob: Job? = null
    private var nextOffset = 0
    private var controller: MediaController? = null
    private val future = MediaController.Builder(app,
        SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_TIMELINE_CHANGED) || events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)) queueDirty = true
            refresh()
        }
    }
    init {
        future.addListener({
            try {
                controller = future.get().also { it.addListener(listener) }
                refresh()
            } catch (e: Exception) { message.value = "Playback service could not connect. Reopen Still to retry." }
        }, androidx.core.content.ContextCompat.getMainExecutor(app))
        viewModelScope.launch { while (isActive) { refresh(); delay(500) } }
        viewModelScope.launch { graph.storageError.collect { it?.let { error -> message.value = error } } }
        viewModelScope.launch { graph.playbackReady.collect { refresh() } }
    }
    fun setAutoPlayRadio(enabled: Boolean) {
        _autoPlayRadio.value = enabled
        radioPreferences.edit().putBoolean("enabled", enabled).apply()
        radioJob?.cancel()
        radioJob = null
        radioAttempt = null
        refresh()
    }

    private fun continueRadio(player: MediaController) {
        if (!_autoPlayRadio.value || !graph.playbackReady.value || !player.playWhenReady ||
            player.playerError != null || player.repeatMode != Player.REPEAT_MODE_OFF ||
            player.playbackState !in listOf(Player.STATE_READY, Player.STATE_ENDED) ||
            player.mediaItemCount >= QueueRules.MAX_SIZE || radioJob?.isActive == true) return
        val seed = player.currentMediaItem?.mediaId ?: return
        if (!seed.startsWith("youtube:")) return
        if (player.currentTimeline.getNextWindowIndex(player.currentMediaItemIndex,
                Player.REPEAT_MODE_OFF, player.shuffleModeEnabled) != C.INDEX_UNSET) return
        val ids = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        val attempt = seed to ids
        if (radioAttempt == attempt) return
        val provider = graph.providers.forTrack(seed) as? YouTubeProvider ?: return
        radioAttempt = attempt
        radioJob = viewModelScope.launch {
            try {
                val related = provider.getRelatedTracks(seed)
                ensureActive()
                // Discard results if the user replaced, reordered, or extended the queue.
                if (!_autoPlayRadio.value || controller !== player ||
                    !graph.playbackReady.value || player.currentMediaItem?.mediaId != seed ||
                    player.repeatMode != Player.REPEAT_MODE_OFF || player.playerError != null ||
                    (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId } != ids ||
                    player.currentTimeline.getNextWindowIndex(player.currentMediaItemIndex,
                        Player.REPEAT_MODE_OFF, player.shuffleModeEnabled) != C.INDEX_UNSET) return@launch
                val existing = ids.toHashSet()
                val additions = related.filter { it.playable && it.id !in existing }
                    .distinctBy(Track::id).take(minOf(10, QueueRules.MAX_SIZE - ids.size))
                if (additions.isEmpty()) return@launch
                val ended = player.playbackState == Player.STATE_ENDED
                player.addMediaItems(additions.map(Track::mediaItem))
                if (ended && player.playWhenReady) {
                    player.seekToDefaultPosition(ids.size)
                    player.prepare()
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message.value = "Auto-Radio could not load suggestions. Your queue is unchanged." }
        }
    }

    fun query(value: String) {
        searchJob?.cancel()
        nextOffset = 0
        _search.value = SearchState(query = value, loading = value.isNotBlank())
        if (value.isBlank()) return
        searchJob = viewModelScope.launch { delay(350); performSearch(value, false) }
    }
    fun retrySearch() {
        searchJob?.cancel()
        val query = search.value.query
        searchJob = viewModelScope.launch { performSearch(query, false) }
    }
    fun more() {
        if (search.value.loading || !search.value.more) return
        searchJob = viewModelScope.launch { performSearch(search.value.query, true) }
    }
    private suspend fun performSearch(query: String, append: Boolean) {
        _search.update { it.copy(loading = true, error = null) }
        try {
            val offset = if (append) nextOffset else 0
            val found = graph.music.search(query, offset)
            nextOffset = offset + found.size
            _search.update { it.copy(loading = false, tracks = (if (append) it.tracks + found else found).distinctBy(Track::id), more = found.size == 30) }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { _search.update { it.copy(loading = false, error = e.message ?: "Check your connection and try again.") } }
    }
    private fun act(action: (MediaController) -> Unit) {
        val player = controller
        if (player == null || !graph.playbackReady.value) { message.value = "Connecting to playback…"; return }
        try { action(player); refresh() }
        catch (e: Exception) { message.value = "Playback could not be updated. Please try again." }
    }
    fun play(track: Track, tracks: List<Track>) {
        if (!track.playable) { message.value = track.unavailableReason; return }
        val queue = tracks.filter(Track::playable).distinctBy(Track::id).take(QueueRules.MAX_SIZE)
        val index = queue.indexOfFirst { it.id == track.id }
        if (index < 0) { message.value = "Queue is full. Play this track from search."; return }
        radioJob?.cancel(); radioJob = null; radioAttempt = null
        act { it.setMediaItems(queue.map(Track::mediaItem), index, 0); it.prepare(); it.play() }
    }
    fun toggle() = act {
        if (it.playerError != null) { it.prepare(); it.play() } else if (it.playWhenReady) it.pause() else {
            if (it.playbackState == Player.STATE_IDLE || it.playerError != null) it.prepare()
            if (it.playbackState == Player.STATE_ENDED) it.seekToDefaultPosition(0)
            it.play()
        }
    }
    fun next() = act { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }
    fun previous() = act { it.seekToPrevious() }
    fun seek(position: Long) = act { it.seekTo(position.coerceAtLeast(0)) }
    fun shuffle() = act { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    fun repeat() = act { it.repeatMode = QueueRules.nextRepeat(it.repeatMode) }
    fun enqueue(track: Track) {
        if (!track.playable) { message.value = track.unavailableReason; return }
        act {
            if (it.mediaItemCount >= QueueRules.MAX_SIZE) message.value = "Queue is full (300 tracks)."
            else it.addMediaItem(track.mediaItem())
        }
    }
    fun remove(index: Int) = act { if (index in 0 until it.mediaItemCount) it.removeMediaItem(index) }
    fun move(from: Int, to: Int) = act { if (QueueRules.canMove(it.mediaItemCount, from, to)) it.moveMediaItem(from, to) }
    fun jump(index: Int) = act {
        if (index in 0 until it.mediaItemCount) { it.seekToDefaultPosition(index); it.prepare(); it.play() }
    }
    private fun library(action: suspend () -> Unit) { viewModelScope.launch {
        try { action() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { message.value = e.message ?: "Could not update your library." }
    } }
    fun favourite(track: Track) = library { graph.library.toggle(track) }
    fun playSmartMix() {
        if (_mixLoading.value) return
        _mixLoading.value = true
        viewModelScope.launch {
            try {
                val mix = graph.library.generateSmartMix()
                if (mix.isEmpty()) {
                    message.value = "Listen to a few songs or add favourites to build your mix."
                } else act { player ->
                    radioJob?.cancel(); radioJob = null; radioAttempt = null
                    player.shuffleModeEnabled = false
                    player.setMediaItems(mix.map(Track::mediaItem), 0, 0)
                    player.prepare()
                    player.play()
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message.value = "Could not create your mix. Please try again." }
            finally { _mixLoading.value = false }
        }
    }
    fun selectPlaylist(id: Long?) { selectedPlaylist.value = id }
    fun importPlaylist(name: String, rawInput: String) {
        if (_playlistImport.value.running) return
        if (name.isBlank() || rawInput.isBlank()) {
            message.value = "Enter a playlist name and paste a URL or song list."
            return
        }
        _playlistImport.value = PlaylistImportState(running = true)
        importJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val importer = PlaylistImporter(graph.music)
                val entries = importer.read(rawInput)
                require(entries.isNotEmpty()) { "No songs were found in this input." }
                val id = graph.library.create(name)
                _playlistImport.update { it.copy(total = entries.size, playlistId = id) }
                val importedIds = hashSetOf<String>()
                entries.forEachIndexed { index, entry ->
                    ensureActive()
                    val track = importer.match(entry)
                    if (track != null && track.id !in importedIds) {
                        graph.library.add(id, track)
                        importedIds.add(track.id)
                    }
                    _playlistImport.update { it.copy(processed = index + 1, imported = importedIds.size) }
                    if (index < entries.lastIndex) delay(500)
                }
                val state = _playlistImport.value
                message.value = "Imported ${state.imported} of ${state.total}. ${state.total - state.imported} unmatched, failed, or duplicate songs skipped."
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val error = e.message ?: "Import failed. Already imported songs are saved."
                _playlistImport.update { it.copy(error = error) }
                message.value = error
            } finally { _playlistImport.update { it.copy(running = false) } }
        }
    }
    fun cancelPlaylistImport() {
        importJob?.cancel()
        message.value = "Import stopped. Already imported songs are saved."
    }
    fun createPlaylist(name: String, track: Track? = null) = library {
        val id = graph.library.create(name)
        if (track != null) {
            graph.library.add(id, track)
            message.value = "Added to playlist"
        }
    }
    fun renamePlaylist(id: Long, name: String) = library { graph.library.rename(id, name) }
    fun deletePlaylist(id: Long) = library { graph.library.delete(id) }
    fun addTrackToPlaylist(playlistId: Long, track: Track) = library {
        graph.library.add(playlistId, track)
        message.value = "Added to playlist"
    }
    fun removeFromPlaylist(id: Long, track: Track) = library { graph.library.remove(id, track.id) }
    fun download(track: Track) = library { graph.downloads.enqueue(track); message.value = "Download queued. See Library > Downloads." }
    fun cancelDownload(id: String) = library { graph.downloads.cancel(id) }
    fun removeDownload(id: String) = library { graph.downloads.remove(id) }
    fun loadLyrics(track: Track, force: Boolean = false) {
        if (!force && lyrics.value.trackId == track.id) return
        lyricsJob?.cancel()
        lyrics.value = LyricsState(track.id, loading = true)
        lyricsJob = viewModelScope.launch {
            try { lyrics.value = LyricsState(track.id, value = graph.lyrics.load(track, force)) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { lyrics.value = LyricsState(track.id, error = "Lyrics unavailable. Connect to the internet and retry.") }
        }
    }
    fun setPreload(value: Boolean) = library { graph.preferences.preload(value) }
    private fun refresh() {
        val p = controller ?: return
        val duration = p.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: p.currentMediaItem?.track()?.durationMs ?: 0
        val error = p.playerError?.let {
            when (it.errorCode) {
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                    "Connection interrupted. Check your network and press play to retry."
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                    "This track is unavailable from its provider. Try another track."
                else -> "Audio could not be loaded. Retry playback or choose another track."
            }
        }
        if (queueDirty || cachedQueue.size != p.mediaItemCount) {
            cachedQueue = (0 until p.mediaItemCount).map { p.getMediaItemAt(it).track() }
            queueDirty = false
        }
        _playback.value = PlaybackState(cachedQueue,
            p.currentMediaItemIndex.coerceAtLeast(0), p.playWhenReady, p.playbackState == Player.STATE_BUFFERING,
            p.currentPosition.coerceAtLeast(0), duration, p.shuffleModeEnabled, p.repeatMode, error, graph.playbackReady.value)
        continueRadio(p)
    }
    override fun onCleared() { controller?.removeListener(listener); MediaController.releaseFuture(future) }
}
