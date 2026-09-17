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
    val error: String? = null, val notice: String? = null, val reading: Boolean = false,
    val entries: List<PlaylistImporter.Entry> = emptyList(), val remaining: Int = 0,
    val unmatched: Int = 0, val duplicates: Int = 0, val stopped: Boolean = false)

data class SearchState(val query: String = "", val tracks: List<Track> = emptyList(), val loading: Boolean = false,
    val error: String? = null, val more: Boolean = false)
data class PlaylistContent(val id: Long? = null, val tracks: List<Track> = emptyList(),
    val loading: Boolean = false, val error: String? = null)
data class PlaybackState(val queue: List<Track> = emptyList(), val index: Int = 0, val playing: Boolean = false,
    val buffering: Boolean = false, val duration: Long = 0, val shuffle: Boolean = false,
    val repeat: Int = 0, val error: String? = null, val connected: Boolean = false) {
    val current get() = queue.getOrNull(index)
}
class StillViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as StillApp).graph
    private val _playlistImport = MutableStateFlow(PlaylistImportState())
    val playlistImport = _playlistImport.asStateFlow()
    private var importJob: Job? = null
    private var importPending = emptyList<IndexedValue<PlaylistImporter.Entry>>()
    private val importFailures = mutableListOf<IndexedValue<PlaylistImporter.Entry>>()
    private val importer = PlaylistImporter(graph.music)
    val importInput = MutableStateFlow("")
    val importName = MutableStateFlow("")
    private val searchRadio = SearchRadio(viewModelScope) { seed ->
        (graph.providers.forTrack(seed.id) as? YouTubeProvider)?.getRelatedTracks(seed).orEmpty()
    }
    private val radioPreferences = app.getSharedPreferences("auto_radio", android.content.Context.MODE_PRIVATE)
    private val _autoPlayRadio = MutableStateFlow(radioPreferences.getBoolean("enabled", true))
    val autoPlayRadio = _autoPlayRadio.asStateFlow()
    private var radioJob: Job? = null
    private var radioAttempt: Pair<String, List<String>>? = null
    private val _home = MutableStateFlow(HomeState())
    val home = _home.asStateFlow()
    private var homeJob: Job? = null
    private var homeHistoryKey: List<String>? = null
    fun loadHome(force: Boolean = false) {
        if (homeJob?.isActive == true) return
        homeJob = viewModelScope.launch {
            try {
                val history = graph.library.recents.first()
                val saved = graph.library.favourites.first()
                val historyKey = history.take(8).map(Track::id) + listOf("|") + saved.map(Track::id)
                if (!force && homeHistoryKey == historyKey && _home.value.tracks.isNotEmpty()) return@launch
                _home.update { it.copy(loading = true, error = null) }
                val result = com.still.music.provider.RecommendationLoader(
                    related = { seed -> (graph.music as? YouTubeProvider)?.getRelatedTracks(seed).orEmpty() },
                    search = { query -> graph.music.search(query) }
                ).load(history, saved)
                _home.update { it.copy(loading = false, tracks = result.tracks, subtitle = result.subtitle,
                    error = if (result.tracks.isEmpty()) "No recommendations found. Check your connection and retry." else null) }
                homeHistoryKey = historyKey
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                _home.update { it.copy(loading = false, error = "Couldn't load recommendations. Check your connection and retry.") }
            }
        }
    }
    private var homeMixJob: Job? = null
    fun openHomeMix(mix: HomeMix) {
        homeMixJob?.cancel()
        _home.update { it.copy(selectedMix = mix, mixTracks = emptyList(), mixLoading = true, mixError = null) }
        homeMixJob = viewModelScope.launch {
            try {
                val tracks = graph.music.search(mix.query).filter(Track::playable).distinctBy(Track::id).shuffled().take(20)
                _home.update { it.copy(mixLoading = false, mixTracks = tracks,
                    mixError = if (tracks.isEmpty()) "No songs found for this mix. Try again." else null) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                _home.update { it.copy(mixLoading = false, mixError = "Couldn't load this mix. Check your connection and retry.") }
            }
        }
    }
    fun closeHomeMix() {
        homeMixJob?.cancel()
        _home.update { it.copy(selectedMix = null, mixLoading = false) }
    }
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
    val playlistContent = selectedPlaylist.flatMapLatest { id ->
        if (id == null) flowOf(PlaylistContent()) else graph.library.tracks(id)
            .map { PlaylistContent(id, it) }
            .onStart { emit(PlaylistContent(id, loading = true)) }
            .catch { emit(PlaylistContent(id, error = "Could not open this playlist. Return to your library and try again.")) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlaylistContent())
    val equalizer = graph.equalizer
    private var searchJob: Job? = null
    private var nextOffset = 0
    private var controller: MediaController? = null
    private val clock = PlaybackClock(viewModelScope) { controller?.currentPosition ?: 0L }
    val playbackPosition = clock.position
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
        if (searchRadio.loading || !_autoPlayRadio.value || !graph.playbackReady.value || !player.playWhenReady ||
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
        val currentTrack = player.currentMediaItem?.track() ?: return
        radioAttempt = attempt
        radioJob = viewModelScope.launch {
            try {
                val related = provider.getRelatedTracks(currentTrack)
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
        if (query.isBlank()) return
        _search.update { it.copy(loading = true, error = null) }
        try {
            val offset = if (append) nextOffset else 0
            val found = graph.music.search(query, offset)
            currentCoroutineContext().ensureActive()
            if (_search.value.query != query) return
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
        val playable = tracks.filter(Track::playable).distinctBy(Track::id)
        val selected = playable.indexOfFirst { it.id == track.id }
        val queue = if (selected >= QueueRules.MAX_SIZE) playable.drop(selected).take(QueueRules.MAX_SIZE)
            else playable.take(QueueRules.MAX_SIZE)
        val index = queue.indexOfFirst { it.id == track.id }
        if (index < 0) { message.value = "Queue is full. Play this track from search."; return }
        radioJob?.cancel(); radioJob = null; radioAttempt = null
        searchRadio.cancel()
        act { it.shuffleModeEnabled = false; it.setMediaItems(queue.map(Track::mediaItem), index, 0); it.prepare(); it.play() }
    }
    fun playSearch(track: Track) {
        if (!track.playable) { message.value = track.unavailableReason; return }
        act { player ->
            radioJob?.cancel(); radioJob = null
            radioAttempt = track.id to listOf(track.id)
            // Mark recommendation loading before setting the timeline, which emits player events.
            searchRadio.start(track, deliver = { recommendations ->
                if (controller === player && player.currentMediaItem?.mediaId == track.id &&
                    player.mediaItemCount == 1) {
                    val ended = player.playbackState == Player.STATE_ENDED
                    player.addMediaItems(recommendations.map(Track::mediaItem))
                    if (ended && player.playWhenReady && recommendations.isNotEmpty()) {
                        player.seekToDefaultPosition(1); player.prepare()
                    }
                    if (recommendations.isEmpty()) message.value = "No recommendations found. Add songs from their options menu."
                }
            }, failed = { message.value = "Recommendations unavailable. Your selected song stays in the queue." })
            player.shuffleModeEnabled = false
            player.setMediaItems(listOf(track.mediaItem()), 0, 0)
            player.prepare(); player.play()
        }
    }
    fun playShuffled(tracks: List<Track>) {
        val playable = tracks.filter(Track::playable).distinctBy(Track::id).take(QueueRules.MAX_SIZE)
        if (playable.isEmpty()) return
        val startIndex = playable.indices.random()
        radioJob?.cancel(); radioJob = null; radioAttempt = null
        searchRadio.cancel()
        act { 
            it.setMediaItems(playable.map(Track::mediaItem), startIndex, 0)
            it.shuffleModeEnabled = true
            it.prepare()
            it.play() 
        }
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
    fun previousTrack() = act { if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() }
    fun seek(position: Long) = act { it.seekTo(position.coerceAtLeast(0)) }
    fun shuffle() = act { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    fun repeat() = act { it.repeatMode = QueueRules.nextRepeat(it.repeatMode) }
    fun enqueue(track: Track) {
        if (!track.playable) { message.value = track.unavailableReason; return }
        act {
            searchRadio.cancel()
            if (it.mediaItemCount >= QueueRules.MAX_SIZE) message.value = "Queue is full (300 tracks)."
            else { it.addMediaItem(track.mediaItem()); message.value = "Added to queue" }
        }
    }
    fun remove(index: Int) = act { searchRadio.cancel(); if (index in 0 until it.mediaItemCount) it.removeMediaItem(index) }
    fun move(from: Int, to: Int) = act { searchRadio.cancel(); if (QueueRules.canMove(it.mediaItemCount, from, to)) it.moveMediaItem(from, to) }
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
                    searchRadio.cancel()
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
    fun resetPlaylistImport() {
        if (_playlistImport.value.running || _playlistImport.value.reading) return
        importPending = emptyList()
        importFailures.clear()
        _playlistImport.value = PlaylistImportState()
    }
    fun readImportFile(uri: android.net.Uri) {
        if (_playlistImport.value.running || _playlistImport.value.reading) return
        resetPlaylistImport()
        _playlistImport.update { it.copy(reading = true) }
        importJob = viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (output.size() <= PlaylistImporter.MAX_INPUT_BYTES) {
                            ensureActive()
                            val count = stream.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        val bytes = output.toByteArray()
                        require(bytes.size <= PlaylistImporter.MAX_INPUT_BYTES) { "Choose a file smaller than 2 MB." }
                        val charset = when {
                            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> Charsets.UTF_16LE
                            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> Charsets.UTF_16BE
                            else -> Charsets.UTF_8
                        }
                        bytes.toString(charset).removePrefix("\uFEFF").also {
                            require('\u0000' !in it) { "Choose a text or CSV export, not a spreadsheet or audio file." }
                        }
                    } ?: error("This file could not be opened. Choose it again.")
                }
                importInput.value = text
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _playlistImport.update { it.copy(error = e.message ?: "Could not read this file.") } }
            finally { _playlistImport.update { it.copy(reading = false) } }
        }
    }
    fun previewPlaylistImport() {
        if (_playlistImport.value.running || _playlistImport.value.reading || importInput.value.isBlank()) return
        _playlistImport.value = PlaylistImportState(reading = true)
        importJob = viewModelScope.launch {
            try {
                val entries = importer.read(importInput.value)
                ensureActive()
                require(entries.isNotEmpty()) { "No songs found. Check the link or song list." }
                importPending = entries.withIndex().toList()
                importFailures.clear()
                _playlistImport.value = PlaylistImportState(total = entries.size, entries = entries,
                    remaining = entries.size, notice = importer.notice)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _playlistImport.update { it.copy(error = e.message ?: "Could not read this playlist.") } }
            finally { _playlistImport.update { it.copy(reading = false) } }
        }
    }
    fun importPlaylist() {
        if (_playlistImport.value.running || _playlistImport.value.reading || importName.value.isBlank()) return
        if (importPending.isEmpty()) {
            importPending = importFailures.sortedBy { it.index }.toList()
            importFailures.clear()
        }
        if (importPending.isEmpty()) return
        _playlistImport.update { it.copy(running = true, error = null, stopped = false, remaining = importPending.size) }
        importJob = viewModelScope.launch {
            try {
                val id = _playlistImport.value.playlistId ?: graph.library.create(importName.value).also { id ->
                    _playlistImport.update { it.copy(playlistId = id) }
                }
                val savedIds = graph.library.tracks(id).first().mapTo(hashSetOf(), Track::id)
                com.still.music.provider.ImportRunner(importer::match).run(importPending.toList()) { matched, failed ->
                    val completed = matched.map { it.first }.toSet() + failed.map { it.index }
                    val additions = matched.filter { it.second.id !in savedIds }.distinctBy { it.second.id }
                    // Once a transaction commits, record its progress even if Stop was tapped during the write.
                    withContext(NonCancellable) {
                        graph.library.importBatch(id, additions)
                        savedIds.addAll(additions.map { it.second.id })
                        importFailures.addAll(failed)
                        importPending = importPending.filterNot { it.index in completed }
                        _playlistImport.update { it.copy(imported = savedIds.size,
                            processed = it.total - importPending.size, remaining = importPending.size,
                            unmatched = importFailures.size, duplicates = it.duplicates + matched.size - additions.size) }
                    }
                }
                message.value = "Imported ${savedIds.size} songs."
            } catch (e: CancellationException) {
                _playlistImport.update { it.copy(stopped = true) }
                throw e
            } catch (e: Exception) {
                _playlistImport.update { it.copy(error = e.message ?: "Import failed. Saved songs are kept.") }
            } finally { _playlistImport.update { it.copy(running = false) } }
        }
    }
    fun cancelPlaylistImport() {
        importJob?.cancel()
        message.value = "Import stopped. Saved songs are kept; resume any time while Still is open."
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
    private val _downloadBatches = MutableStateFlow<Set<String>>(emptySet())
    val downloadBatches = _downloadBatches.asStateFlow()

    fun downloadPlaylist(id: Long) = queueDownloads("playlist:$id") { graph.library.tracks(id).first() }
    fun downloadMix(name: String, tracks: List<Track>) = queueDownloads("mix:$name") { tracks.toList() }

    private fun queueDownloads(key: String, load: suspend () -> List<Track>) {
        if (key in _downloadBatches.value) return
        _downloadBatches.update { it + key }
        viewModelScope.launch {
            try {
                val result = com.still.music.offline.enqueuePlaylist(load(), graph.downloads::enqueue)
                if (result.eligible == 0) {
                    message.value = "There are no downloadable songs in this playlist."
                    return@launch
                }
                message.value = "Playlist downloads scheduled. Already saved or queued songs were skipped." +
                    (if (result.unavailable > 0) " " + result.unavailable + " unavailable." else "") +
                    (if (result.failed > 0) " " + result.failed + " could not be queued; tap Download playlist to retry." else "") +
                    " See Library > Downloads."
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message.value = "Couldn't queue this playlist. Please retry." }
            finally { _downloadBatches.update { it - key } }
        }
    }
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
            duration, p.shuffleModeEnabled, p.repeatMode, error, graph.playbackReady.value)
        clock.refresh()
        continueRadio(p)
    }
    override fun onCleared() { controller?.removeListener(listener); MediaController.releaseFuture(future) }
}
