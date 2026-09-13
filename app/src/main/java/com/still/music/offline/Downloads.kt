package com.still.music.offline

import android.content.Context
import androidx.work.*
import com.still.music.StillApp
import com.still.music.provider.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

data class SavedDownload(val track: Track, val ready: Boolean, val bytes: Long,
    val active: Boolean, val progress: Int, val status: String)

class Downloads(context: Context, private val providers: ProviderRegistry, http: OkHttpClient) {
    private val store = OfflineStore(File(context.filesDir, "downloads"))
    private val manager = WorkManager.getInstance(context)
    private val revision = MutableStateFlow(0)
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val transferSlots = Semaphore(1)
    private val client = http.newBuilder().callTimeout(8, TimeUnit.MINUTES).readTimeout(30, TimeUnit.SECONDS).build()
    private fun lock(id: String) = locks.getOrPut(id) { Mutex() }
    private fun name(id: String) = "download-" + store.key(id)
    val entries = combine(revision, manager.getWorkInfosByTagFlow(TAG)) { _, work ->
        store.tracks().map { track ->
            val workId = store.workId(track.id)
            val info = work.firstOrNull { it.id.toString() == workId }
            val file = store.audio(track.id)
            val active = info != null && !info.state.isFinished
            val percent = info?.progress?.getInt("percent", -1) ?: -1
            val status = when {
                file != null -> "Downloaded"
                info?.state == WorkInfo.State.RUNNING -> if (percent >= 0) "Downloading $percent%" else "Downloading..."
                active -> "Queued - waiting for network or Android"
                info?.state == WorkInfo.State.FAILED -> info.outputData.getString("error") ?: "Download failed. Retry."
                else -> "Not downloaded"
            }
            SavedDownload(track, file != null, file?.length() ?: 0, active, percent, status)
        }.sortedBy { it.track.title }
    }.flowOn(Dispatchers.IO)

    fun localFile(id: String): File? = store.audio(id)
    suspend fun enqueue(track: Track) = withContext(Dispatchers.IO) {
        // A transfer holds the per-track lock; don't make a playlist wait for it to finish.
        if (store.audio(track.id) != null ||
            manager.getWorkInfosForUniqueWork(name(track.id)).get().any { !it.state.isFinished }) return@withContext
        lock(track.id).withLock {
            if (store.audio(track.id) != null) return@withLock
            providers.forTrack(track.id)
            if (manager.getWorkInfosForUniqueWork(name(track.id)).get().any { !it.state.isFinished }) return@withLock
            val request = OneTimeWorkRequestBuilder<MusicDownloadWorker>()
                    .setInputData(workDataOf("id" to track.id))
                    .addTag(TAG).addTag(name(track.id))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresStorageNotLow(true).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                    .build()
            store.remember(track, request.id.toString())
            manager.enqueueUniqueWork(name(track.id), ExistingWorkPolicy.KEEP, request).result.get()
            revision.update { it + 1 }
        }
    }
    suspend fun cancel(id: String) = withContext(Dispatchers.IO) {
        manager.cancelUniqueWork(name(id)).result.get()
    }
    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        this@Downloads.cancel(id)
        lock(id).withLock { store.remove(id); revision.update { it + 1 } }
    }
    suspend fun transfer(id: String, progress: suspend (Int) -> Unit): Track = withContext(Dispatchers.IO) {
        transferSlots.withPermit {
            lock(id).withLock {
                val track = store.tracks().firstOrNull { it.id == id } ?: throw IOException("Download was removed.")
                if (store.audio(id) != null) return@withLock track
                val part = store.partial(id)
                try {
                    val access = providers.forTrack(id).resolvePlayback(id)
                    val received = AudioTransfer(client).copy(access.url, part, progress)
                    coroutineContext.ensureActive()
                    store.commit(id, received, received)
                    revision.update { it + 1 }
                    track
                } finally { part.delete() }
            }
        }
    }
    companion object {
        const val TAG = "still-download"
        const val MAX_BYTES = 256L * 1024 * 1024
    }
}

class MusicDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("id") ?: return Result.failure()
        val graph = (applicationContext as StillApp).graph
        return try {
            val track = graph.downloads.transfer(id) { setProgress(workDataOf("percent" to it)) }
            try { graph.lyrics.load(track) } catch (e: CancellationException) { throw e } catch (_: Exception) { }
            Result.success()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            android.util.Log.w("StillDownloads", e.message ?: "Download failed.")
            if (e is java.net.SocketTimeoutException && runAttemptCount < 2) Result.retry()
            else Result.failure(workDataOf("error" to (e.message ?: "Download failed. Retry.").take(200)))
        }
    }
}
