package com.still.music.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.still.music.provider.Track

@Composable internal fun PlaylistDownloadButton(tracks: List<Track>, batchKey: String,
    vm: StillViewModel, onDownload: () -> Unit) {
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val batches by vm.downloadBatches.collectAsStateWithLifecycle()
    val ids = tracks.filter(Track::playable).mapTo(hashSetOf(), Track::id)
    val entries = downloads.filter { it.track.id in ids }
    val ready = entries.count { it.ready }
    val active = entries.count { it.active && !it.ready }
    val allReady = ids.isNotEmpty() && ready == ids.size
    val scheduling = batchKey in batches
    val remaining = ids.size - ready - active
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        OutlinedButton(onClick = onDownload, modifier = Modifier.fillMaxWidth(),
            enabled = ids.isNotEmpty() && !scheduling && remaining > 0) {
            Icon(if (allReady) Icons.Default.DownloadDone else Icons.Default.Download, null)
            Text(when {
                allReady -> "Playlist downloaded"
                scheduling -> "Queuing playlist..."
                active > 0 && remaining == 0 -> "Downloading playlist"
                active > 0 -> "Download remaining songs"
                else -> "Download playlist"
            }, Modifier.padding(start = 8.dp))
        }
        if (ready > 0 || active > 0 || scheduling) {
            LinearProgressIndicator(progress = { if (ids.isEmpty()) 0f else ready.toFloat() / ids.size },
                modifier = Modifier.fillMaxWidth())
            Text("$ready of ${ids.size} songs offline" + if (active > 0) " / $active queued or downloading" else "",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        }
        if (tracks.any { !it.playable }) Text("Unavailable songs are skipped.",
            style = MaterialTheme.typography.bodySmall)
    }
}
