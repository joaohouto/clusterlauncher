package com.joaohouto.clusterlauncher.player

import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import com.joaohouto.clusterlauncher.data.model.EqualizerPreset

class AudioEffectsManager {
    companion object {
        private const val TAG = "AudioEffectsManager"
        private const val DEFAULT_LOUDNESS_GAIN_MB = 250
    }

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var equalizer: Equalizer? = null
    private var currentSessionId: Int = 0
    private var isLoudnessEnabled: Boolean = false

    private var isEqualizerEnabled: Boolean = false
    private var currentPreset: EqualizerPreset = EqualizerPreset.FLAT
    private var currentCustomBandsMb: List<Int> = listOf(0, 0, 0, 0, 0)

    fun updateAudioSession(audioSessionId: Int) {
        if (audioSessionId <= 0 || audioSessionId == currentSessionId) return
        currentSessionId = audioSessionId

        release()

        try {
            val enhancer = LoudnessEnhancer(audioSessionId)
            enhancer.setTargetGain(DEFAULT_LOUDNESS_GAIN_MB)
            enhancer.enabled = isLoudnessEnabled
            loudnessEnhancer = enhancer
            Log.d(TAG, "LoudnessEnhancer attached to session $audioSessionId (enabled=$isLoudnessEnabled)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize LoudnessEnhancer on session $audioSessionId: ${e.message}")
        }

        try {
            val eq = Equalizer(0, audioSessionId)
            equalizer = eq
            applyEqualizerToHardware(eq)
            Log.d(TAG, "Equalizer attached to session $audioSessionId with ${eq.numberOfBands} bands (enabled=$isEqualizerEnabled)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize Equalizer on session $audioSessionId: ${e.message}")
        }
    }

    fun applyEqualizerConfig(
        enabled: Boolean,
        preset: EqualizerPreset,
        customBandsMb: List<Int>
    ) {
        isEqualizerEnabled = enabled
        currentPreset = preset
        currentCustomBandsMb = customBandsMb
        equalizer?.let { applyEqualizerToHardware(it) }
    }

    private fun applyEqualizerToHardware(eq: Equalizer) {
        try {
            val bandCount = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            val minMb = range.getOrNull(0)?.toInt() ?: EqualizerPreset.DEFAULT_MIN_MB
            val maxMb = range.getOrNull(1)?.toInt() ?: EqualizerPreset.DEFAULT_MAX_MB
            val levels = EqualizerPreset.computeBandLevelsForHardware(
                preset = currentPreset,
                customBandsMb = currentCustomBandsMb,
                hardwareBandCount = bandCount,
                minLevelMb = minMb,
                maxLevelMb = maxMb
            )
            for (i in levels.indices) {
                eq.setBandLevel(i.toShort(), levels[i])
            }
            eq.enabled = isEqualizerEnabled
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply Equalizer config: ${e.message}")
        }
    }

    fun setLoudnessEnabled(enabled: Boolean) {
        isLoudnessEnabled = enabled
        try {
            loudnessEnhancer?.enabled = enabled
        } catch (e: Exception) {
            Log.w(TAG, "Failed to toggle LoudnessEnhancer: ${e.message}")
        }
    }

    fun setLoudnessGain(gainMb: Int) {
        try {
            loudnessEnhancer?.setTargetGain(gainMb)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set LoudnessEnhancer gain: ${e.message}")
        }
    }

    fun release() {
        try {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing LoudnessEnhancer", e)
        } finally {
            loudnessEnhancer = null
        }

        try {
            equalizer?.enabled = false
            equalizer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing Equalizer", e)
        } finally {
            equalizer = null
        }
    }
}
