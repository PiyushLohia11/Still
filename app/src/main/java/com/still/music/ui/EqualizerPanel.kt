package com.still.music.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.still.music.audio.EqualizerManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerPanel(equalizer: EqualizerManager, onClose: () -> Unit) {
    val enabled by equalizer.enabled.collectAsStateWithLifecycle()
    val bands by equalizer.bands.collectAsStateWithLifecycle()
    val presets by equalizer.presets.collectAsStateWithLifecycle()
    val currentPreset by equalizer.currentPreset.collectAsStateWithLifecycle()
    
    val range = remember { equalizer.getBandLevelRange() }
    
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, "Close Equalizer")
            }
            Text("Equalizer", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 16.dp))
            Switch(checked = enabled, onCheckedChange = equalizer::setEnabled)
        }
        
        if (bands.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Equalizer is not available right now. Please start playing a song first.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            if (presets.isNotEmpty()) {
                var presetExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = presetExpanded,
                    onExpandedChange = { presetExpanded = it },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                ) {
                    val presetName = if (currentPreset >= 0 && currentPreset < presets.size) presets[currentPreset.toInt()] else "Custom"
                    OutlinedTextField(
                        value = presetName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Preset") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = presetExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = presetExpanded,
                        onDismissRequest = { presetExpanded = false }
                    ) {
                        presets.forEachIndexed { index, name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    equalizer.usePreset(index.toShort())
                                    presetExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.SpaceEvenly) {
                bands.forEachIndexed { index, (freq, level) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (freq >= 1000000) "${freq / 1000000}kHz" else "${freq / 1000}Hz",
                            modifier = Modifier.width(64.dp),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Slider(
                            value = level.toFloat(),
                            onValueChange = { equalizer.setBandLevel(index.toShort(), it.toInt().toShort()) },
                            valueRange = range[0].toFloat()..range[1].toFloat(),
                            enabled = enabled,
                            modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}
