package com.joaohouto.clusterlauncher.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.tanh

class VolumeNormalizerAudioProcessor : BaseAudioProcessor() {

    companion object {
        private const val MIN_GAIN = 0.3548134f
        private const val MAX_GAIN = 2.818383f

        private const val LIMITER_THRESHOLD_NORM = 0.8709636f
        private const val LIMITER_CEILING_NORM = 0.9885531f
        private const val LIMITER_HEADROOM_NORM = LIMITER_CEILING_NORM - LIMITER_THRESHOLD_NORM

        const val BOUNDARY_RAMP_MS = 15
        const val ASYNC_SCAN_RAMP_MS = 400
    }

    @Volatile
    var isNormalizerEnabled: Boolean = true

    @Volatile
    private var isTagGainLocked: Boolean = false

    @Volatile
    private var targetGainLinear: Float = 1.0f

    @Volatile
    private var pendingRampDurationMs: Int = BOUNDARY_RAMP_MS

    @Volatile
    private var rampNeedsUpdate: Boolean = false

    private var currentGainLinear: Float = 1.0f
    private var activeTargetGainLinear: Float = 1.0f
    private var gainStepPerFrame: Float = 0f
    private var remainingRampFrames: Int = 0

    fun setTrackGainDb(
        gainDb: Float,
        rampDurationMs: Int = BOUNDARY_RAMP_MS,
        fromTag: Boolean = false
    ) {
        if (!fromTag && isTagGainLocked) {
            return
        }
        if (fromTag) {
            isTagGainLocked = true
        }
        val linear = if (gainDb.isFinite()) {
            10.0.pow(gainDb.toDouble() / 20.0).toFloat()
        } else {
            1.0f
        }
        targetGainLinear = linear.coerceIn(MIN_GAIN, MAX_GAIN)
        pendingRampDurationMs = rampDurationMs.coerceAtLeast(0)
        rampNeedsUpdate = true
    }

    fun setTrackReplayGain(gainDb: Float, rampDurationMs: Int = BOUNDARY_RAMP_MS) {
        setTrackGainDb(gainDb = gainDb, rampDurationMs = rampDurationMs, fromTag = true)
    }

    fun resetTrackGain(rampDurationMs: Int = BOUNDARY_RAMP_MS) {
        isTagGainLocked = false
        targetGainLinear = 1.0f
        pendingRampDurationMs = rampDurationMs.coerceAtLeast(0)
        rampNeedsUpdate = true
    }

    fun getCurrentTargetGainDb(): Float {
        return (20.0 * kotlin.math.log10(targetGainLinear.toDouble())).toFloat()
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    private fun syncRampStateIfNeeded() {
        if (!rampNeedsUpdate) return
        rampNeedsUpdate = false

        val newTarget = targetGainLinear
        activeTargetGainLinear = newTarget

        val sr = if (inputAudioFormat.sampleRate > 0) inputAudioFormat.sampleRate else 44100
        val rampMs = pendingRampDurationMs
        val totalFrames = (sr.toLong() * rampMs.toLong() / 1000L).toInt()

        if (totalFrames <= 1 || abs(newTarget - currentGainLinear) < 1e-5f) {
            currentGainLinear = newTarget
            remainingRampFrames = 0
            gainStepPerFrame = 0f
        } else {
            remainingRampFrames = totalFrames
            gainStepPerFrame = (newTarget - currentGainLinear) / totalFrames.toFloat()
        }
    }

    private fun nextFrameGain(): Float {
        if (remainingRampFrames > 0) {
            currentGainLinear += gainStepPerFrame
            remainingRampFrames--
            if (remainingRampFrames == 0) {
                currentGainLinear = activeTargetGainLinear
            }
        } else {
            currentGainLinear = activeTargetGainLinear
        }
        return currentGainLinear
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val outputBuffer = replaceOutputBuffer(remaining)
        outputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)

        if (!isNormalizerEnabled) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        syncRampStateIfNeeded()

        val channels = max(1, inputAudioFormat.channelCount)
        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> processPcm16(inputBuffer, outputBuffer, channels)
            C.ENCODING_PCM_FLOAT -> processPcmFloat(inputBuffer, outputBuffer, channels)
            else -> outputBuffer.put(inputBuffer)
        }

        outputBuffer.flip()
    }

    private fun processPcm16(inputBuffer: ByteBuffer, outputBuffer: ByteBuffer, channels: Int) {
        val bytesPerFrame = channels * 2
        val frameCount = inputBuffer.remaining() / bytesPerFrame
        val invScale = 1f / 32768f

        for (f in 0 until frameCount) {
            val frameGain = nextFrameGain()
            for (ch in 0 until channels) {
                val rawShort = inputBuffer.short
                val normalized = rawShort.toFloat() * invScale
                val amplified = normalized * frameGain
                val limited = applyNormalizedSoftLimiter(amplified)
                val outInt = (limited * 32767f).toInt().coerceIn(-32768, 32767)
                outputBuffer.putShort(outInt.toShort())
            }
        }

        while (inputBuffer.hasRemaining()) {
            outputBuffer.put(inputBuffer.get())
        }
    }

    private fun processPcmFloat(inputBuffer: ByteBuffer, outputBuffer: ByteBuffer, channels: Int) {
        val bytesPerFrame = channels * 4
        val frameCount = inputBuffer.remaining() / bytesPerFrame

        for (f in 0 until frameCount) {
            val frameGain = nextFrameGain()
            for (ch in 0 until channels) {
                val sample = inputBuffer.float
                if (!sample.isFinite()) {
                    outputBuffer.putFloat(0f)
                } else {
                    val amplified = sample * frameGain
                    val limited = applyNormalizedSoftLimiter(amplified)
                    outputBuffer.putFloat(limited)
                }
            }
        }

        while (inputBuffer.hasRemaining()) {
            outputBuffer.put(inputBuffer.get())
        }
    }

    internal fun applyNormalizedSoftLimiter(sample: Float): Float {
        if (!sample.isFinite()) return 0f
        val absVal = abs(sample)
        if (absVal <= LIMITER_THRESHOLD_NORM) {
            return sample
        }
        val excess = (absVal - LIMITER_THRESHOLD_NORM) / LIMITER_HEADROOM_NORM
        val compressed = LIMITER_THRESHOLD_NORM + LIMITER_HEADROOM_NORM * tanh(excess.toDouble()).toFloat()
        val clamped = compressed.coerceAtMost(LIMITER_CEILING_NORM)
        return if (sample >= 0f) clamped else -clamped
    }

    override fun onFlush() {
        syncRampStateIfNeeded()
    }

    override fun onReset() {
        isTagGainLocked = false
        targetGainLinear = 1.0f
        currentGainLinear = 1.0f
        activeTargetGainLinear = 1.0f
        remainingRampFrames = 0
        gainStepPerFrame = 0f
        rampNeedsUpdate = false
    }
}
