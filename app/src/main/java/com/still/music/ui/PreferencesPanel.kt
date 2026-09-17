package com.still.music.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable internal fun PreferencesPanel(vm: StillViewModel, openEqualizer: () -> Unit) {
    val preload by vm.preload.collectAsStateWithLifecycle()
    val radio by vm.autoPlayRadio.collectAsStateWithLifecycle()
    val health by vm.playbackHealth.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Your listening space.", style = MaterialTheme.typography.headlineLarge)
        GlassCard {
            Text("PLAYBACK", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            PreferenceToggle("Auto-Radio", "Keep playing related music when your queue ends.", radio, vm::setAutoPlayRadio)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PreferenceToggle("Preload next track", "Buffer a few seconds ahead for smoother transitions. Uses mobile data.", preload, vm::setPreload)
            FilledTonalButton(onClick = openEqualizer, modifier = Modifier.fillMaxWidth()) { Text("Open equalizer") }
        }
        GlassCard {
            Text("ON THIS DEVICE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text("Your library stays with you", style = MaterialTheme.typography.titleLarge)
            Text("Playlists, favourites and downloaded audio are stored locally. Uninstalling Still removes them.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        GlassCard {
            Text("ABOUT STILL", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text("Music, with room to breathe.", style = MaterialTheme.typography.titleLarge)
            Text("Free and ad-free. YouTube access is unofficial and can change. Powered by NewPipeExtractor (GPL-3.0-or-later).",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("This session: ${health.bufferWaits} buffer waits · ${health.audioUnderruns} audio underruns",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun PreferenceToggle(title: String, detail: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, change, Modifier.semantics { contentDescription = title })
    }
}
