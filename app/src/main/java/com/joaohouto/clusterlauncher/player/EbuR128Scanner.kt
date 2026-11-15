package com.joaohouto.clusterlauncher.player

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.Log
import androidx.media3.common.C
import com.joaohouto.clusterlauncher.BuildConfig
import com.joaohouto.clusterlauncher.data.local.AppDatabase
import com.joaohouto.clusterlauncher.data.local.entity.TrackLoudnessEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.tan

data class LoudnessMeasurement(
    val integratedLufs: Float,
    val truePeak: Float
) {
    fun computeGainDb(
        targetLufs: Float = EbuR128Scanner.DEFAULT_TARGET_LUFS,
        preAmpDb: Float = 0f,
        minGainDb: Float = EbuR128Scanner.MIN_GAIN_DB,
        maxGainDb: Float = EbuR128Scanner.MAX_GAIN_DB
    ): Float {
        if (!integratedLufs.isFinite() || integratedLufs <= EbuR128Scanner.ABSOLUTE_GATE_LUFS) {
            return 0f
        }
        val rawGainDb = (targetLufs - integratedLufs) + preAmpDb
        return rawGainDb.coerceIn(minGainDb, maxGainDb)
    }
}

class EbuR128Meter(
    private val sampleRate: Int,
    private val channelCount: Int
) {
    private val s1B0: Double
    private val s1B1: Double
    private val s1B2: Double
    private val s1A1: Double
    private val s1A2: Double

    private val s2B0: Double
    private val s2B1: Double
    private val s2B2: Double
    private val s2A1: Double
    private val s2A2: Double

    private val s1Z1 = DoubleArray(channelCount)
    private val s1Z2 = DoubleArray(channelCount)
    private val s2Z1 = DoubleArray(channelCount)
    private val s2Z2 = DoubleArray(channelCount)

    private val stepFrames: Int = max(1, sampleRate / 10)
    private val subBlockEnergies = DoubleArray(4)
    private var subBlockCount = 0
    private var currentStepFrames = 0
    private var currentSubBlockSum = 0.0

    private var gatedBlockPowers = DoubleArray(2048)
    private var gatedBlockCount = 0

    private var totalWeightedEnergySum = 0.0
    private var totalFramesProcessed = 0L

    private var maxAbsSample = 0f

    init {
        val fs = sampleRate.toDouble()

        val f0Shelf = 1681.974450955533
        val gShelfDb = 3.999843853973347
        val qShelf = 0.7071752369554196
        val kShelf = tan(Math.PI * f0Shelf / fs)
        val vh = 10.0.pow(gShelfDb / 20.0)
        val vb = vh.pow(0.4996667741545416)
        val kShelfSq = kShelf * kShelf
        val a0Shelf = 1.0 + kShelf / qShelf + kShelfSq

        s1B0 = (vh + vb * kShelf / qShelf + kShelfSq) / a0Shelf
        s1B1 = 2.0 * (kShelfSq - vh) / a0Shelf
        s1B2 = (vh - vb * kShelf / qShelf + kShelfSq) / a0Shelf
        s1A1 = 2.0 * (kShelfSq - 1.0) / a0Shelf
        s1A2 = (1.0 - kShelf / qShelf + kShelfSq) / a0Shelf

        val f0Hp = 38.13547087602444
        val qHp = 0.5003270373238773
        val kHp = tan(Math.PI * f0Hp / fs)
        val kHpSq = kHp * kHp
        val a0Hp = 1.0 + kHp / qHp + kHpSq

        s2B0 = 1.0 / a0Hp
        s2B1 = -2.0 / a0Hp
        s2B2 = 1.0 / a0Hp
        s2A1 = 2.0 * (kHpSq - 1.0) / a0Hp
        s2A2 = (1.0 - kHp / qHp + kHpSq) / a0Hp
    }

    fun processPcm16(buffer: ByteBuffer) {
        val bytesPerFrame = channelCount * 2
        val availableFrames = buffer.remaining() / bytesPerFrame
        if (availableFrames <= 0) return

        val invScale = 1.0 / 32768.0
        val channelWeight = if (channelCount == 1) 2.0 else 1.0

        for (f in 0 until availableFrames) {
            var frameEnergy = 0.0
            for (ch in 0 until channelCount) {
                val rawShort = buffer.short.toInt()
                val normalized = (rawShort * invScale).toFloat()
                val absVal = abs(normalized)
                if (absVal > maxAbsSample) {
                    maxAbsSample = absVal
                }

                val weight = when {
                    channelCount == 1 -> channelWeight
                    ch < 3 -> 1.0
                    ch in 3..4 -> 1.41
                    else -> 0.0
                }
                if (weight > 0.0) {
                    val x = normalized.toDouble()
                    val y1 = s1B0 * x + s1Z1[ch]
                    s1Z1[ch] = s1B1 * x - s1A1 * y1 + s1Z2[ch]
                    s1Z2[ch] = s1B2 * x - s1A2 * y1

                    val y2 = s2B0 * y1 + s2Z1[ch]
                    s2Z1[ch] = s2B1 * y1 - s2A1 * y2 + s2Z2[ch]
                    s2Z2[ch] = s2B2 * y1 - s2A2 * y2

                    frameEnergy += weight * (y2 * y2)
                }
            }
            onFrameEnergy(frameEnergy)
        }
    }

    fun processPcmFloat(buffer: ByteBuffer) {
        val bytesPerFrame = channelCount * 4
        val availableFrames = buffer.remaining() / bytesPerFrame
        if (availableFrames <= 0) return

        val channelWeight = if (channelCount == 1) 2.0 else 1.0

        for (f in 0 until availableFrames) {
            var frameEnergy = 0.0
            for (ch in 0 until channelCount) {
                val sample = buffer.float
                if (!sample.isFinite()) continue
                val absVal = abs(sample)
                if (absVal > maxAbsSample) {
                    maxAbsSample = absVal
                }

                val weight = when {
                    channelCount == 1 -> channelWeight
                    ch < 3 -> 1.0
                    ch in 3..4 -> 1.41
                    else -> 0.0
                }
                if (weight > 0.0) {
                    val x = sample.toDouble()
                    val y1 = s1B0 * x + s1Z1[ch]
                    s1Z1[ch] = s1B1 * x - s1A1 * y1 + s1Z2[ch]
                    s1Z2[ch] = s1B2 * x - s1A2 * y1

                    val y2 = s2B0 * y1 + s2Z1[ch]
                    s2Z1[ch] = s2B1 * y1 - s2A1 * y2 + s2Z2[ch]
                    s2Z2[ch] = s2B2 * y1 - s2A2 * y2

                    frameEnergy += weight * (y2 * y2)
                }
            }
            onFrameEnergy(frameEnergy)
        }
    }

    private fun onFrameEnergy(frameEnergy: Double) {
        currentSubBlockSum += frameEnergy
        totalWeightedEnergySum += frameEnergy
        totalFramesProcessed++
        currentStepFrames++

        if (currentStepFrames >= stepFrames) {
            val meanSubBlockPower = currentSubBlockSum / currentStepFrames.toDouble()
            subBlockEnergies[subBlockCount % 4] = meanSubBlockPower
            subBlockCount++
            currentSubBlockSum = 0.0
            currentStepFrames = 0

            if (subBlockCount >= 4) {
                val blockPower = 0.25 * (
                    subBlockEnergies[0] +
                        subBlockEnergies[1] +
                        subBlockEnergies[2] +
                        subBlockEnergies[3]
                    )
                if (blockPower > EbuR128Scanner.ABSOLUTE_GATE_POWER_THRESHOLD) {
                    if (gatedBlockCount == gatedBlockPowers.size) {
                        gatedBlockPowers = gatedBlockPowers.copyOf(gatedBlockPowers.size * 2)
                    }
                    gatedBlockPowers[gatedBlockCount++] = blockPower
                }
            }
        }
    }

    fun finish(): LoudnessMeasurement {
        if (gatedBlockCount == 0) {
            if (totalFramesProcessed > 0L && totalWeightedEnergySum > 0.0) {
                val meanPower = totalWeightedEnergySum / totalFramesProcessed.toDouble()
                val lufs = (-0.691 + 10.0 * log10(meanPower)).toFloat()
                return LoudnessMeasurement(
                    integratedLufs = lufs.coerceAtLeast(EbuR128Scanner.ABSOLUTE_GATE_LUFS),
                    truePeak = maxAbsSample
                )
            }
            return LoudnessMeasurement(
                integratedLufs = EbuR128Scanner.ABSOLUTE_GATE_LUFS,
                truePeak = maxAbsSample
            )
        }

        var absGateSum = 0.0
        for (i in 0 until gatedBlockCount) {
            absGateSum += gatedBlockPowers[i]
        }
        val absGateMeanPower = absGateSum / gatedBlockCount.toDouble()

        val relativeGatePowerThreshold = absGateMeanPower * 0.1

        var relGateSum = 0.0
        var relGateCount = 0
        for (i in 0 until gatedBlockCount) {
            val p = gatedBlockPowers[i]
            if (p >= relativeGatePowerThreshold) {
                relGateSum += p
                relGateCount++
            }
        }

        val finalMeanPower = if (relGateCount > 0) {
            relGateSum / relGateCount.toDouble()
        } else {
            absGateMeanPower
        }

        val integratedLufs = (-0.691 + 10.0 * log10(finalMeanPower)).toFloat()
        return LoudnessMeasurement(
            integratedLufs = integratedLufs,
            truePeak = maxAbsSample
        )
    }
}

object EbuR128Scanner {
    private const val TAG = "EbuR128Scanner"

    const val ALGORITHM_VERSION = 1
    const val DEFAULT_TARGET_LUFS = -14.0f
    const val ABSOLUTE_GATE_LUFS = -70.0f
    const val MIN_GAIN_DB = -9.0f
    const val MAX_GAIN_DB = 9.0f

    val ABSOLUTE_GATE_POWER_THRESHOLD: Double = 10.0.pow((-70.0 + 0.691) / 10.0)

    private const val CODEC_TIMEOUT_US = 10_000L
    private val DB_VALUE_REGEX = Regex("""([+-]?\d+(?:\.\d+)?)""")

    fun parseReplayGainOrR128Tag(
        key: String,
        rawValue: String,
        targetLufs: Float = DEFAULT_TARGET_LUFS
    ): Float? {
        val cleanKey = key.trim()
        val cleanVal = rawValue.trim()
        if (cleanVal.isEmpty()) return null

        if (cleanKey.equals("REPLAYGAIN_TRACK_GAIN", ignoreCase = true)) {
            val match = DB_VALUE_REGEX.find(cleanVal) ?: return null
            val db = match.groupValues[1].toFloatOrNull() ?: return null
            return db.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
        }

        if (cleanKey.equals("R128_TRACK_GAIN", ignoreCase = true)) {
            val r128RefOffset = targetLufs - (-23.0f)
            if (cleanVal.contains("dB", ignoreCase = true) || cleanVal.contains('.')) {
                val match = DB_VALUE_REGEX.find(cleanVal) ?: return null
                val dbAt23 = match.groupValues[1].toFloatOrNull() ?: return null
                return (dbAt23 + r128RefOffset).coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
            }
            val q78 = cleanVal.toIntOrNull() ?: return null
            val dbAt23 = q78.toFloat() / 256.0f
            return (dbAt23 + r128RefOffset).coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
        }

        return null
    }

    fun extractTagGainFromMetadata(
        metadata: androidx.media3.common.Metadata?,
        targetLufs: Float = DEFAULT_TARGET_LUFS
    ): Float? {
        if (metadata == null) return null
        for (i in 0 until metadata.length()) {
            val entry = metadata.get(i)
            when (entry) {
                is androidx.media3.extractor.metadata.id3.TextInformationFrame -> {
                    val desc = entry.description ?: ""
                    val value = entry.values.firstOrNull() ?: ""
                    parseReplayGainOrR128Tag(desc, value, targetLufs)?.let { return it }
                }
                is androidx.media3.extractor.metadata.vorbis.VorbisComment -> {
                    parseReplayGainOrR128Tag(entry.key, entry.value, targetLufs)?.let { return it }
                }
                is androidx.media3.extractor.metadata.flac.VorbisComment -> {
                    parseReplayGainOrR128Tag(entry.key, entry.value, targetLufs)?.let { return it }
                }
                else -> {
                    val text = entry.toString()
                    if (text.contains("REPLAYGAIN_TRACK_GAIN", ignoreCase = true)) {
                        val afterKey = text.substringAfter("REPLAYGAIN_TRACK_GAIN", "")
                        parseReplayGainOrR128Tag("REPLAYGAIN_TRACK_GAIN", afterKey, targetLufs)?.let { return it }
                    } else if (text.contains("R128_TRACK_GAIN", ignoreCase = true)) {
                        val afterKey = text.substringAfter("R128_TRACK_GAIN", "").trimStart('=', ':', ' ')
                        parseReplayGainOrR128Tag("R128_TRACK_GAIN", afterKey, targetLufs)?.let { return it }
                    }
                }
            }
        }
        return null
    }

    fun resolveFileIdentity(context: Context, uriString: String, filePath: String): Pair<Long, Long> {
        if (filePath.isNotEmpty()) {
            try {
                val f = File(filePath)
                if (f.exists()) {
                    return Pair(f.length(), f.lastModified())
                }
            } catch (_: Exception) {}
        }
        if (uriString.startsWith("file://")) {
            try {
                val path = Uri.parse(uriString).path
                if (!path.isNullOrEmpty()) {
                    val f = File(path)
                    if (f.exists()) {
                        return Pair(f.length(), f.lastModified())
                    }
                }
            } catch (_: Exception) {}
        }
        if (uriString.startsWith("content://")) {
            try {
                val uri = Uri.parse(uriString)
                context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                        val size = if (sizeIdx != -1 && !c.isNull(sizeIdx)) c.getLong(sizeIdx) else 0L
                        return Pair(size, 0L)
                    }
                }
            } catch (_: Exception) {}
        }
        return Pair(0L, 0L)
    }

    private val activeScans = java.util.concurrent.atomic.AtomicInteger(0)

    suspend fun scanTrack(
        context: Context,
        uriString: String,
        filePath: String,
        shouldPause: (() -> Boolean)? = null
    ): LoudnessMeasurement? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var pfd: android.os.ParcelFileDescriptor? = null
        val scanStartMs = SystemClock.elapsedRealtime()
        val parallelCount = activeScans.incrementAndGet()
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "[SYNC_DIAG] ScanStart ts=$scanStartMs uri=$uriString activeParallel=$parallelCount"
            )
        }

        try {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            } catch (_: Throwable) {}

            var dataSourceSet = false
            if (uriString.startsWith("content://") || uriString.startsWith("file://")) {
                try {
                    pfd = context.contentResolver.openFileDescriptor(Uri.parse(uriString), "r")
                    if (pfd != null) {
                        extractor.setDataSource(pfd.fileDescriptor)
                        dataSourceSet = true
                    }
                } catch (_: Exception) {}
            }

            if (!dataSourceSet && filePath.isNotEmpty()) {
                val file = File(filePath)
                if (file.exists()) {
                    extractor.setDataSource(file.absolutePath)
                    dataSourceSet = true
                }
            }

            if (!dataSourceSet && uriString.startsWith("/")) {
                val file = File(uriString)
                if (file.exists()) {
                    extractor.setDataSource(file.absolutePath)
                    dataSourceSet = true
                }
            }

            if (!dataSourceSet) {
                return null
            }

            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            var mimeType: String? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    mimeType = mime
                    break
                }
            }

            if (audioTrackIndex < 0 || audioFormat == null || mimeType == null) {
                return null
            }

            extractor.selectTrack(audioTrackIndex)
            codec = MediaCodec.createDecoderByType(mimeType)
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            var sampleRate = if (audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100
            var channelCount = if (audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2
            var pcmEncoding = C.ENCODING_PCM_16BIT

            var meter = EbuR128Meter(sampleRate.coerceAtLeast(8000), channelCount.coerceAtLeast(1))

            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var decodedFrames = 0
            var chunkStartMs = SystemClock.elapsedRealtime()

            while (!outputDone) {
                coroutineContext.ensureActive()

                if (shouldPause != null && shouldPause()) {
                    while (shouldPause()) {
                        coroutineContext.ensureActive()
                        delay(200L)
                    }
                    chunkStartMs = SystemClock.elapsedRealtime()
                }

                if (!inputDone) {
                    val inputBufferIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inputBufferIndex,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                val presentationTimeUs = extractor.sampleTime
                                codec.queueInputBuffer(
                                    inputBufferIndex,
                                    0,
                                    sampleSize,
                                    presentationTimeUs.coerceAtLeast(0L),
                                    0
                                )
                                extractor.advance()
                            }
                        }
                    }
                }

                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
                when {
                    outputBufferIndex >= 0 -> {
                        val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            outputBuffer.order(ByteOrder.nativeOrder())

                            if (pcmEncoding == C.ENCODING_PCM_FLOAT) {
                                meter.processPcmFloat(outputBuffer)
                            } else {
                                meter.processPcm16(outputBuffer)
                            }
                        }
                        codec.releaseOutputBuffer(outputBufferIndex, false)
                        decodedFrames++
                        if (decodedFrames % 8 == 0) {
                            val nowMs = SystemClock.elapsedRealtime()
                            val workMs = (nowMs - chunkStartMs).coerceAtLeast(1L)
                            val sleepMs = (workMs / 2L).coerceIn(1L, 8L)
                            Thread.yield()
                            delay(sleepMs)
                            chunkStartMs = SystemClock.elapsedRealtime()
                        }
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = codec.outputFormat
                        val newSampleRate = if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        } else sampleRate
                        val newChannels = if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        } else channelCount
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                            newFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)
                        ) {
                            pcmEncoding = newFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }
                        if (newSampleRate != sampleRate || newChannels != channelCount) {
                            sampleRate = newSampleRate.coerceAtLeast(8000)
                            channelCount = newChannels.coerceAtLeast(1)
                            meter = EbuR128Meter(sampleRate, channelCount)
                        }
                    }
                }
            }

            return meter.finish()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to scan EBU R128 loudness for $uriString: ${e.message}")
            return null
        } finally {
            val remaining = activeScans.decrementAndGet()
            val scanEndMs = SystemClock.elapsedRealtime()
            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "[SYNC_DIAG] ScanEnd ts=$scanEndMs uri=$uriString durationMs=${scanEndMs - scanStartMs} activeParallelRemaining=$remaining"
                )
            }
            try {
                codec?.stop()
            } catch (_: Exception) {}
            try {
                codec?.release()
            } catch (_: Exception) {}
            try {
                extractor.release()
            } catch (_: Exception) {}
            try {
                pfd?.close()
            } catch (_: Exception) {}
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LoudnessAnalysisManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "LoudnessAnalysisMgr"

        @Volatile
        private var INSTANCE: LoudnessAnalysisManager? = null

        fun getInstance(context: Context): LoudnessAnalysisManager {
            return INSTANCE ?: synchronized(this) {
                val instance = LoudnessAnalysisManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    private val database = AppDatabase.getInstance(context)
    private val loudnessDao = database.trackLoudnessDao()
    private val trackDao = database.trackDao()

    private val memoryCache = ConcurrentHashMap<String, TrackLoudnessEntity>()

    private val analyzerExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread({
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            } catch (_: Throwable) {}
            runnable.run()
        }, "EbuR128Analyzer").apply {
            isDaemon = true
        }
    }
    private val analyzerDispatcher: CoroutineDispatcher = analyzerExecutor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var activePriorityJob: Job? = null
    private var batchLibraryJob: Job? = null

    @Volatile
    private var isPlayerBuffering: Boolean = false

    @Volatile
    private var isPlayerPlaying: Boolean = false

    @Volatile
    private var quietUntilElapsedMs: Long = 0L

    fun setBuffering(buffering: Boolean) {
        isPlayerBuffering = buffering
    }

    fun setPlaying(playing: Boolean) {
        isPlayerPlaying = playing
    }

    fun notifyTrackTransitionOrSeek() {
        quietUntilElapsedMs = SystemClock.elapsedRealtime() + 3000L
        activePriorityJob?.cancel()
    }

    private fun shouldPausePriorityScan(): Boolean {
        return isPlayerBuffering || SystemClock.elapsedRealtime() < quietUntilElapsedMs
    }

    private fun shouldPauseBatchScan(): Boolean {
        return isPlayerPlaying ||
            isPlayerBuffering ||
            SystemClock.elapsedRealtime() < quietUntilElapsedMs ||
            activePriorityJob?.isActive == true
    }

    suspend fun getValidCachedMeasurement(uri: String, filePath: String): LoudnessMeasurement? {
        if (uri.isEmpty()) return null
        val (fileSize, lastModified) = withContext(Dispatchers.IO) {
            EbuR128Scanner.resolveFileIdentity(context, uri, filePath)
        }

        val inMem = memoryCache[uri]
        if (inMem != null && isEntityValid(inMem, fileSize, lastModified)) {
            return LoudnessMeasurement(inMem.integratedLufs, inMem.truePeak)
        }

        val dbEntity = withContext(Dispatchers.IO) {
            loudnessDao.getLoudnessByUri(uri)
        }
        if (dbEntity != null && isEntityValid(dbEntity, fileSize, lastModified)) {
            memoryCache[uri] = dbEntity
            return LoudnessMeasurement(dbEntity.integratedLufs, dbEntity.truePeak)
        }

        return null
    }

    private fun isEntityValid(entity: TrackLoudnessEntity, currentSize: Long, currentLastModified: Long): Boolean {
        if (entity.algorithmVersion != EbuR128Scanner.ALGORITHM_VERSION) return false
        if (currentSize > 0L && entity.fileSize > 0L && entity.fileSize != currentSize) return false
        if (currentLastModified > 0L && entity.lastModified > 0L && entity.lastModified != currentLastModified) return false
        return entity.integratedLufs.isFinite()
    }

    fun analyzeCurrentAndNextTrack(
        currentUri: String,
        currentFilePath: String?,
        nextUri: String?,
        nextFilePath: String?,
        skipCurrentScan: Boolean = false,
        onCurrentTrackMeasured: (uri: String, measurement: LoudnessMeasurement, fromCache: Boolean) -> Unit
    ) {
        activePriorityJob?.cancel()
        quietUntilElapsedMs = SystemClock.elapsedRealtime() + 3000L
        activePriorityJob = scope.launch {
            val safeCurrentPath = currentFilePath ?: ""
            var currentNeedsScan = false

            if (currentUri.isNotEmpty() && !skipCurrentScan) {
                val cached = getValidCachedMeasurement(currentUri, safeCurrentPath)
                if (cached != null) {
                    onCurrentTrackMeasured(currentUri, cached, true)
                } else {
                    currentNeedsScan = true
                }
            }

            val nextCached = if (!nextUri.isNullOrEmpty() && nextUri != currentUri) {
                getValidCachedMeasurement(nextUri, nextFilePath ?: "")
            } else {
                null
            }
            val nextNeedsScan = !nextUri.isNullOrEmpty() && nextUri != currentUri && nextCached == null

            if (!currentNeedsScan && !nextNeedsScan) {
                return@launch
            }

            delay(5000L)

            if (currentNeedsScan) {
                while (shouldPausePriorityScan()) {
                    ensureActive()
                    delay(250L)
                }
                val measured = withContext(analyzerDispatcher) {
                    scanAndPersist(currentUri, safeCurrentPath) { shouldPausePriorityScan() }
                }
                if (measured != null) {
                    onCurrentTrackMeasured(currentUri, measured, false)
                }
            }

            if (nextNeedsScan && !nextUri.isNullOrEmpty()) {
                while (shouldPausePriorityScan()) {
                    ensureActive()
                    delay(250L)
                }
                withContext(analyzerDispatcher) {
                    scanAndPersist(nextUri, nextFilePath ?: "") { shouldPausePriorityScan() }
                }
            }
        }
    }

    fun triggerLibraryBatchAnalysis() {
        if (batchLibraryJob?.isActive == true) return
        batchLibraryJob = scope.launch {
            try {
                val allTracks = trackDao.getAllTracksSync()
                if (allTracks.isEmpty()) return@launch
                val cachedUris = loudnessDao.getAllCachedUrisForVersion(EbuR128Scanner.ALGORITHM_VERSION).toHashSet()
                val pendingTracks = allTracks.filter { it.uri !in cachedUris }
                if (pendingTracks.isEmpty()) return@launch

                for (track in pendingTracks) {
                    ensureActive()
                    while (shouldPauseBatchScan()) {
                        ensureActive()
                        delay(1000L)
                    }
                    activePriorityJob?.join()
                    val existing = getValidCachedMeasurement(track.uri, track.path)
                    if (existing == null) {
                        withContext(analyzerDispatcher) {
                            scanAndPersist(track.uri, track.path) { shouldPauseBatchScan() }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Error during library batch loudness analysis", e)
            }
        }
    }

    private suspend fun scanAndPersist(
        uri: String,
        filePath: String,
        shouldPause: (() -> Boolean)? = null
    ): LoudnessMeasurement? {
        val measurement = EbuR128Scanner.scanTrack(context, uri, filePath, shouldPause) ?: return null
        val (fileSize, lastModified) = EbuR128Scanner.resolveFileIdentity(context, uri, filePath)
        val entity = TrackLoudnessEntity(
            uri = uri,
            fileSize = fileSize,
            lastModified = lastModified,
            integratedLufs = measurement.integratedLufs,
            truePeak = measurement.truePeak,
            algorithmVersion = EbuR128Scanner.ALGORITHM_VERSION,
            updatedAt = System.currentTimeMillis()
        )
        memoryCache[uri] = entity
        loudnessDao.upsertLoudness(entity)
        return measurement
    }
}
