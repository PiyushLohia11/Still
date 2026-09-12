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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class SearchState(val query: String = "", val tracks: List<Track> = emptyList(), val loading: Boolean = false,
    val error: String? = null, val more: Boolean = false)
data class PlaybackState(val queue: List<Track> = emptyList(), val index: Int = 0, val playing: Boolean = false,
    val buffering: Boolean = false, val position: Long = 0, val duration: Long = 0, val shuffle: Boolean = false,
    val repeat: Int = 0, val error: String? = null, val connected: Boolean = false) {
    val current get() = queue.getOrNull(index)
}
class StillViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as StillApp).graph
    private val _search = MutableStateFlow(SearchState())
    val search = _search.asStateFlow()
    private val _playback = MutableStateFlow(PlaybackState())
    val playback = _playback.asStateFlow()
    val favourites = graph.library.favourites.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recents = graph.library.recents.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val playlists = graph.library.playlists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val preload = graph.preferences.preloading.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val message = MutableStateFlow<String?>(null)
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
        override fun onEvents(player: Player, events: Player.Events) { refresh() }
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
            val found = graph.audius.search(query, offset)
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
    fun selectPlaylist(id: Long?) { selectedPlaylist.value = id }
    fun createPlaylist(name: String, track: Track? = null) = library {
        val id = graph.library.create(name); if (track != null) graph.library.add(id, track)
    }
    fun renamePlaylist(id: Long, name: String) = library { graph.library.rename(id, name) }
    fun deletePlaylist(id: Long) = library { graph.library.delete(id) }
    fun addToPlaylist(id: Long, track: Track) = library { graph.library.add(id, track); message.value = "Added to playlist" }
    fun removeFromPlaylist(id: Long, track: Track) = library { graph.library.remove(id, track.id) }
    fun setPreload(value: Boolean) = library { graph.preferences.preload(value) }
    private fun refresh() {
        val p = controller ?: return
        val duration = p.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: p.currentMediaItem?.track()?.durationMs ?: 0
        val error = p.playerError?.let {
            when (it.errorCode) {
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                    "Connection interrupted. Check your network and press play to retry."
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                    "This track is unavailable from Audius. Try another track."
                else -> "Audio could not be loaded. Retry playback or choose another track."
            }
        }
        _playback.value = PlaybackState((0 until p.mediaItemCount).map { p.getMediaItemAt(it).track() },
            p.currentMediaItemIndex.coerceAtLeast(0), p.playWhenReady, p.playbackState == Player.STATE_BUFFERING,
            p.currentPosition.coerceAtLeast(0), duration, p.shuffleModeEnabled, p.repeatMode, error, graph.playbackReady.value)
    }
    override fun onCleared() { controller?.removeListener(listener); MediaController.releaseFuture(future) }
}
