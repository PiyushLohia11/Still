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
            e.printStackTrace()
        }
    }
    
    fun setEnabled(enable: Boolean) {
        equalizer?.enabled = enable
        enabled.value = enable
    }
    
    fun setBandLevel(band: Short, level: Short) {
        equalizer?.setBandLevel(band, level)
        bands.update { current ->
            current.toMutableList().apply { 
                if (band.toInt() in indices) this[band.toInt()] = this[band.toInt()].first to level.toInt() 
            }
        }
    }
    
    fun usePreset(preset: Short) {
        equalizer?.usePreset(preset)
        currentPreset.value = preset
        
        // Update bands
        equalizer?.let { eq ->
            val numBands = eq.numberOfBands.toInt()
            val newBands = mutableListOf<Pair<Int, Int>>()
            for (i in 0 until numBands) {
                newBands.add(eq.getCenterFreq(i.toShort()) to eq.getBandLevel(i.toShort()).toInt())
            }
            bands.value = newBands
        }
    }
    
    fun getBandLevelRange(): ShortArray {
        return equalizer?.bandLevelRange ?: shortArrayOf(-1500, 1500)
    }
    
    fun release() {
        try {
            equalizer?.release()
        } catch (e: Exception) {}
        equalizer = null
    }
}
