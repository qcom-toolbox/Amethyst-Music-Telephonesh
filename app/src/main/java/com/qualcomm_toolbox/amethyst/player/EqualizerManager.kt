package com.qualcomm_toolbox.amethyst.player

import android.content.Context
import android.media.audiofx.Equalizer
import android.util.Log
import com.qualcomm_toolbox.amethyst.data.EqualizerPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class EqualizerManager(context: Context) {
    private val prefs = EqualizerPreferences(context)
    private var equalizer: Equalizer? = null
    private var currentSessionId: Int = 0

    private val _isEnabled = MutableStateFlow(prefs.enabled)
    val isEnabled = _isEnabled.asStateFlow()

    private val _bandLevels = MutableStateFlow<Map<Short, Short>>(emptyMap())
    val bandLevels = _bandLevels.asStateFlow()

    private val _currentPreset = MutableStateFlow(prefs.presetIndex.toShort())
    val currentPreset = _currentPreset.asStateFlow()

    fun onAudioSessionIdChanged(sessionId: Int) {
        if (sessionId == 0 || sessionId == currentSessionId) return
        Log.d(TAG, "Audio session ID changed: $sessionId")
        currentSessionId = sessionId
        
        try {
            release()
            equalizer = Equalizer(0, sessionId).apply {
                enabled = prefs.enabled
                applyStoredLevels(this)
            }
            updateState()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Equalizer", e)
        }
    }

    private fun applyStoredLevels(eq: Equalizer) {
        val preset = prefs.presetIndex
        if (preset >= 0 && preset < eq.numberOfPresets) {
            eq.usePreset(preset.toShort())
        } else {
            for (i in 0 until eq.numberOfBands) {
                val band = i.toShort()
                eq.setBandLevel(band, prefs.getBandLevel(band))
            }
        }
    }

    private fun updateState() {
        equalizer?.let { eq ->
            val levels = mutableMapOf<Short, Short>()
            for (i in 0 until eq.numberOfBands) {
                val band = i.toShort()
                levels[band] = eq.getBandLevel(band)
            }
            _bandLevels.value = levels
            _isEnabled.value = eq.enabled
            _currentPreset.value = prefs.presetIndex.toShort()
        }
    }

    fun setEnabled(enabled: Boolean) {
        prefs.enabled = enabled
        equalizer?.enabled = enabled
        _isEnabled.value = enabled
    }

    fun setBandLevel(band: Short, level: Short) {
        equalizer?.setBandLevel(band, level)
        prefs.setBandLevel(band, level)
        prefs.presetIndex = -1 // Moving a band breaks the preset
        updateState()
    }

    fun usePreset(preset: Short) {
        equalizer?.usePreset(preset)
        prefs.presetIndex = preset.toInt()
        updateState()
    }

    fun getBandFrequency(band: Short): Int {
        return equalizer?.getCenterFreq(band) ?: 0
    }

    fun getBandLevelRange(): Pair<Short, Short> {
        val range = equalizer?.bandLevelRange ?: shortArrayOf(-1500, 1500)
        return range[0] to range[1]
    }

    fun getPresets(): List<String> {
        return equalizer?.let { eq ->
            (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) }
        } ?: emptyList()
    }

    fun release() {
        equalizer?.release()
        equalizer = null
    }

    companion object {
        private const val TAG = "EqualizerManager"
    }
}
