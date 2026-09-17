package com.still.music.audio

import android.media.audiofx.Equalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class EqualizerManager {
    private var equalizer: Equalizer? = null
    
    val enabled = MutableStateFlow(false)
    val bands = MutableStateFlow<List<Pair<Int, Int>>>(emptyList())
    val presets = MutableStateFlow<List<String>>(emptyList())
    val currentPreset = MutableStateFlow<Short>(-1)
    val error = MutableStateFlow<String?>(null)
    
    fun attach(audioSessionId: Int) {
        release()
        try {
            val eq = Equalizer(0, audioSessionId)
            equalizer = eq
            enabled.value = eq.enabled
            
            val numBands = eq.numberOfBands.toInt()
            val newBands = mutableListOf<Pair<Int, Int>>()
            for (i in 0 until numBands) {
                newBands.add(eq.getCenterFreq(i.toShort()) to eq.getBandLevel(i.toShort()).toInt())
            }
            bands.value = newBands
            
            val numPresets = eq.numberOfPresets.toInt()
            val newPresets = mutableListOf<String>()
            for (i in 0 until numPresets) {
                newPresets.add(eq.getPresetName(i.toShort()))
            }
            presets.value = newPresets
            currentPreset.value = eq.currentPreset
            
        } catch (e: Exception) {
            release()
            error.value = "Audio effects are unavailable on this output."
        }
    }
    
    fun setEnabled(enable: Boolean) {
        updateEffect { eq -> eq.enabled = enable; enabled.value = eq.enabled }
    }
    
    fun setBandLevel(band: Short, level: Short) {
        if (band.toInt() !in bands.value.indices) return
        updateEffect { eq ->
            val range = eq.bandLevelRange
            val value = level.coerceIn(range[0], range[1])
            eq.setBandLevel(band, value)
            currentPreset.value = -1
            bands.update { current ->
                current.toMutableList().apply {
                    if (band.toInt() in indices) this[band.toInt()] = this[band.toInt()].first to value.toInt()
                }
            }
        }
    }
    
    fun usePreset(preset: Short) {
        if (preset.toInt() !in presets.value.indices) return
        updateEffect { eq ->
            eq.usePreset(preset)
            currentPreset.value = preset
            bands.value = List(eq.numberOfBands.toInt()) { index ->
                eq.getCenterFreq(index.toShort()) to eq.getBandLevel(index.toShort()).toInt()
            }
        }
    }
    
    fun getBandLevelRange(): ShortArray {
        return try { equalizer?.bandLevelRange ?: shortArrayOf(-1500, 1500) }
        catch (_: RuntimeException) { shortArrayOf(-1500, 1500) }
    }
    private fun updateEffect(action: (Equalizer) -> Unit) {
        val eq = equalizer ?: return
        try { action(eq); error.value = null }
        catch (_: RuntimeException) { error.value = "Could not update this audio effect. Try another output." }
    }
    
    fun release() {
        try {
            equalizer?.release()
        } catch (e: Exception) {}
        equalizer = null
        bands.value = emptyList()
        presets.value = emptyList()
        enabled.value = false
        currentPreset.value = -1
        error.value = null
    }
}
