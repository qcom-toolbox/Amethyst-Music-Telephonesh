package com.qualcomm_toolbox.amethyst.data

import android.content.Context
import android.content.SharedPreferences

class EqualizerPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var presetIndex: Int
        get() = prefs.getInt(KEY_PRESET, -1) // -1 means custom or no preset
        set(value) = prefs.edit().putInt(KEY_PRESET, value).apply()

    fun getBandLevel(band: Short): Short {
        return prefs.getInt(KEY_BAND_PREFIX + band, 0).toShort()
    }

    fun setBandLevel(band: Short, level: Short) {
        prefs.edit().putInt(KEY_BAND_PREFIX + band, level.toInt()).apply()
    }

    companion object {
        private const val PREFS_NAME = "equalizer_prefs"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_PRESET = "preset_index"
        private const val KEY_BAND_PREFIX = "band_"
    }
}
