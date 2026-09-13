package com.still.music.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.*
import androidx.media3.datasource.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.still.music.MainActivity
import com.still.music.StillApp
import com.still.music.data.QueueSnapshot
import com.still.music.provider.ProviderException
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import java.io.IOException

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class StreamingRetryPolicy : DefaultLoadErrorHandlingPolicy(3) {
    override fun getRetryDelayMsFor(info: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val error = info.exception
        val status = when (error) {
            is HttpDataSource.InvalidResponseCodeException -> error.responseCode
            is ProviderException -> error.status
            else -> 0
        }
        if (info.errorCount > 3 || status in listOf(400, 401, 403, 404, 410, 416)) return C.TIME_UNSET
        return (1000L shl (info.errorCount - 1).coerceIn(0, 2)).coerceAtMost(4000)
    }
}
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private val graph get() = (application as StillApp).graph
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private var restored = false
    private val saves = Channel<QueueSnapshot>(Channel.CONFLATED)
    private var lastRecorded: String? = null
    private var audioStarted = false

    override fun onCreate() {
        super.onCreate()
        val http = DefaultHttpDataSource.Factory().setUserAgent(PlaybackResolver.USER_AGENT)
            .setConnectTimeoutMs(15000).setReadTimeoutMs(20000).setAllowCrossProtocolRedirects(false)
        val resolving = graph.resolver.dataSourceFactory(DefaultDataSource.Factory(this, http))
        player = ExoPlayer.Builder(this)
            .setLoadControl(androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(30_000, 90_000, 2_500, 5_000)
                .setPrioritizeTimeOverSizeThresholds(true).build())
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolving).setLoadErrorHandlingPolicy(StreamingRetryPolicy()))
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        graph.playbackHealth.value = com.still.music.PlaybackHealth()
        player.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onAudioUnderrun(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) {
                graph.playbackHealth.update { it.copy(audioUnderruns = it.audioUnderruns + 1) }
            }
        })
        val intent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaSession.Builder(this, player).setSessionActivity(intent)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                    return if (controller.packageName == packageName || controller.isTrusted)
                        super.onConnect(session, controller) else MediaSession.ConnectionResult.reject()
                }
                override fun onAddMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>): ListenableFuture<MutableList<MediaItem>> {
                    return try {
                        val safe = mediaItems.take(QueueRules.MAX_SIZE).map { item ->
                            graph.providers.forTrack(item.mediaId)
                            item.track().mediaItem()
                        }.toMutableList()
                        Futures.immediateFuture(safe)
                    } catch (e: Exception) { Futures.immediateFailedFuture(e) }
                }
            }).build()
        if (player.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            graph.equalizer.attach(player.audioSessionId)
        }

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    graph.equalizer.attach(audioSessionId)
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { if (isPlaying) audioStarted = true }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_BUFFERING && audioStarted)
                    graph.playbackHealth.update { it.copy(bufferWaits = it.bufferWaits + 1) }
            }
            override fun onPlayerError(error: PlaybackException) {
                val id = player.currentMediaItem?.mediaId ?: return
                scope.launch { graph.resolver.invalidate(id) }
            }
            override fun onEvents(player: Player, events: Player.Events) {
                if (!restored) return
                snapshot()
                if (player.isPlaying && player.currentMediaItem != null) {
                    val item = player.currentMediaItem!!
                    if (lastRecorded != item.mediaId) {
                        lastRecorded = item.mediaId
                        scope.launch {
                            try { graph.library.record(item.track()) }
                            catch (e: Exception) {
                                if (e is CancellationException) throw e
                                graph.storageError.value = "Could not save recent listening."
                            }
                        }
                    }
                }
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { lastRecorded = null; audioStarted = false }
        })
        scope.launch {
            for (saved in saves) {
                try { graph.preferences.save(saved) }
                catch (e: Exception) {
                    if (e is CancellationException) throw e
                    graph.storageError.value = "Could not save the playback queue."
                }
            }
        }
        scope.launch {
            try {
                val saved = graph.preferences.snapshots.first()
                if (player.mediaItemCount == 0 && saved.tracks.isNotEmpty()) {
                    player.setMediaItems(saved.tracks.map { it.mediaItem() }, saved.index, saved.positionMs)
                    player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(saved.shuffleOrder.toIntArray(), System.nanoTime()))
                    player.shuffleModeEnabled = saved.shuffle
                    player.repeatMode = saved.repeat
                    // Restore metadata and position only. Prepare when the listener explicitly presses play.
                    player.playWhenReady = false
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                graph.storageError.value = "The previous queue could not be restored."
            } finally { restored = true; graph.playbackReady.value = true }
            while (isActive) { delay(3000); snapshot() }
        }
        scope.launch {
            graph.preferences.preloading.collect { enabled ->
                player.setPreloadConfiguration(ExoPlayer.PreloadConfiguration(if (enabled) 5_000_000 else C.TIME_UNSET))
            }
        }
    }
    private fun capture(): QueueSnapshot {
        val timeline = player.currentTimeline
        val order = mutableListOf<Int>()
        var index = timeline.getFirstWindowIndex(true)
        while (index != C.INDEX_UNSET && order.size < player.mediaItemCount) {
            order.add(index)
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, true)
        }
        return QueueSnapshot((0 until player.mediaItemCount).map { player.getMediaItemAt(it).track() },
            player.currentMediaItemIndex.coerceAtLeast(0), player.currentPosition.coerceAtLeast(0),
            player.shuffleModeEnabled, player.repeatMode, order)
    }
    private fun snapshot() { if (restored) saves.trySend(capture()) }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onTaskRemoved(rootIntent: Intent?) {
        snapshot()
        if (!player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) stopSelf()
    }
    override fun onDestroy() {
        // Periodic/event writes cover process death. A final bounded write covers orderly service shutdown.
        if (::player.isInitialized && restored) {
            val saved = capture()
            scope.cancel()
            runBlocking(Dispatchers.IO) { withTimeoutOrNull(1000) { runCatching { graph.preferences.save(saved) } } }
        } else scope.cancel()
        graph.playbackReady.value = false
        session?.release()
        if (::player.isInitialized) player.release()
        super.onDestroy()
    }
}
