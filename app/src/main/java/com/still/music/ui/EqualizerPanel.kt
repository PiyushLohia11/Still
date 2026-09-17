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
import com.still.music.audio.EqualizerManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun EqualizerPanel(equalizer: EqualizerManager) {
    val enabled by equalizer.enabled.collectAsStateWithLifecycle()
    val bands by equalizer.bands.collectAsStateWithLifecycle()
    val presets by equalizer.presets.collectAsStateWithLifecycle()
    val currentPreset by equalizer.currentPreset.collectAsStateWithLifecycle()
    val error by equalizer.error.collectAsStateWithLifecycle()
    val range = equalizer.getBandLevelRange()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Shape your sound.", style = MaterialTheme.typography.headlineLarge)
        Text("Fine-tune the details, from warm bass to clear highs.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Equalizer", style = MaterialTheme.typography.titleLarge)
                    Text(if (enabled) "Your sound is active" else "Original sound", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(enabled, equalizer::setEnabled, enabled = bands.isNotEmpty(),
                    modifier = Modifier.semantics { contentDescription = "Enable equalizer" })
            }
            if (bands.isEmpty()) Text("Play a song to connect the equalizer. Some devices do not support audio effects.")
            if (presets.isNotEmpty()) {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded, { expanded = it && enabled }) {
                    OutlinedTextField(presets.getOrNull(currentPreset.toInt()) ?: "Custom", {}, readOnly = true,
                        enabled = enabled, label = { Text("Sound preset") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth())
                    ExposedDropdownMenu(expanded, { expanded = false }) {
                        presets.forEachIndexed { index, name ->
                            DropdownMenuItem(text = { Text(name) }, onClick = { equalizer.usePreset(index.toShort()); expanded = false })
                        }
                    }
                }
            }
        }
        if (bands.isNotEmpty()) GlassCard(Modifier.fillMaxWidth()) {
            Text("FREQUENCY", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            bands.forEachIndexed { index, (frequency, level) ->
                val label = if (frequency >= 1_000_000) "${frequency / 1_000_000} kHz" else "${frequency / 1000} Hz"
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, style = MaterialTheme.typography.titleMedium)
                        Text("%.1f dB".format(level / 100f), color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(level.toFloat().coerceIn(range[0].toFloat(), range[1].toFloat()),
                        onValueChange = { equalizer.setBandLevel(index.toShort(), it.toInt().toShort()) },
                        valueRange = range[0].toFloat()..range[1].toFloat(), enabled = enabled,
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$label gain" })
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
