package com.still.music.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable internal fun ImportPanel(vm: StillViewModel, openPlaylist: (Long) -> Unit) {
    val state by vm.playlistImport.collectAsStateWithLifecycle()
    val name by vm.importName.collectAsStateWithLifecycle()
    val input by vm.importInput.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::readImportFile)
    }
    val busy = state.running || state.reading
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Text("Bring your music.", style = MaterialTheme.typography.headlineLarge)
            Text("A link, a file, a new collection.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("01  /  SOURCE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text("Public Spotify & YouTube playlists", style = MaterialTheme.typography.titleMedium)
                Text("Or choose a CSV, TSV or text export. Text format: Song title – Artist. Up to 2,000 songs / 2 MB.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.entries.isEmpty()) {
                    OutlinedTextField(input, { vm.importInput.value = it.take(2 * 1024 * 1024) },
                        label = { Text("Playlist link or song list") }, modifier = Modifier.fillMaxWidth(),
                        minLines = 3, maxLines = 6, enabled = !busy)
                    OutlinedButton(onClick = { picker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel")) }, enabled = !busy) {
                        Icon(Icons.Default.UploadFile, null); Text("Choose file", Modifier.padding(start = 8.dp))
                    }
                    Button(onClick = vm::previewPlaylistImport, enabled = input.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.reading) "Reading source…" else "Preview songs")
                    }
                } else {
                    Text("${state.total} songs found", style = MaterialTheme.typography.titleLarge)
                    if (state.playlistId == null) TextButton(onClick = vm::resetPlaylistImport, enabled = !busy) { Text("Change source") }
                }
                if (state.reading) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
        if (state.entries.isNotEmpty()) item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("02  /  YOUR COLLECTION", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(name, { vm.importName.value = it.take(100) }, label = { Text("Playlist name") },
                    singleLine = true, enabled = !busy && state.playlistId == null, modifier = Modifier.fillMaxWidth())
                if (state.playlistId != null) {
                    Text("${state.imported} songs saved", style = MaterialTheme.typography.headlineMedium)
                    LinearProgressIndicator(progress = { state.processed.toFloat() / state.total.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
                    Text("${state.remaining} remaining · ${state.unmatched} unmatched · ${state.duplicates} duplicates",
                        style = MaterialTheme.typography.bodySmall)
                    Text(when {
                        state.running -> "Matching songs. You can close this screen while the import continues."
                        state.stopped -> "Stopped. Saved songs are kept; resume below."
                        else -> "Saved to your library. Retry unmatched songs or open your playlist."
                    }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.running) OutlinedButton(onClick = vm::cancelPlaylistImport) { Text("Stop import") }
                else if (state.remaining > 0 || state.unmatched > 0) Button(onClick = vm::importPlaylist,
                    enabled = name.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(when {
                        state.playlistId == null -> "Import ${state.total} songs"
                        state.remaining > 0 -> "Resume import"
                        else -> "Retry ${state.unmatched} unmatched songs"
                    })
                }
                state.playlistId?.let { id ->
                    FilledTonalButton(onClick = { openPlaylist(id) }, modifier = Modifier.fillMaxWidth()) { Text("Open playlist") }
                    if (!busy) TextButton(onClick = { vm.resetPlaylistImport(); vm.importInput.value = ""; vm.importName.value = "" }) { Text("Start another import") }
                }
            }
        }
        state.error?.let { error -> item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(error, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        } }
        if (state.entries.isNotEmpty()) {
            item { Text("Source preview", style = MaterialTheme.typography.titleLarge) }
            itemsIndexed(state.entries.take(30)) { index, entry ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text((index + 1).toString().padStart(2, '0'), color = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(entry.artist.ifBlank { "Artist not provided" }, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (state.total > 30) item { Text("+ ${state.total - 30} more songs", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
