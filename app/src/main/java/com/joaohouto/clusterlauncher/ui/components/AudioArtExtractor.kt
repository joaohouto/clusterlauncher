package com.joaohouto.clusterlauncher.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.util.LruCache
import com.joaohouto.clusterlauncher.BuildConfig
import com.joaohouto.clusterlauncher.lyrics.LrcLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

data class PreloadedTrackMetadata(
    val uri: String,
    val filePath: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val lyricsLines: List<LrcLine>? = null
)

object AudioArtExtractor {
    private const val TAG = "AudioArtExtractor"
    const val DISPLAY_ART_SIZE_PX = 256

    private val bitmapCache = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    private val metadataCache = LruCache<String, PreloadedTrackMetadata>(64)

    private val artCache = object : LruCache<String, ByteArray>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    fun getCachedBitmap(uriOrPath: String): Bitmap? {
        if (uriOrPath.isBlank()) return null
        synchronized(bitmapCache) {
            val bmp = bitmapCache.get(uriOrPath)
            return if (bmp != null && !bmp.isRecycled) bmp else null
        }
    }

    fun getCachedMetadata(uri: String): PreloadedTrackMetadata? {
        if (uri.isBlank()) return null
        synchronized(metadataCache) {
            return metadataCache.get(uri)
        }
    }

    fun putCachedMetadata(uri: String, metadata: PreloadedTrackMetadata) {
        if (uri.isBlank()) return
        synchronized(metadataCache) {
            metadataCache.put(uri, metadata)
        }
    }

    fun getCachedArt(uriOrPath: String): ByteArray? {
        if (uriOrPath.isBlank()) return null
        synchronized(artCache) {
            return artCache.get(uriOrPath)
        }
    }

    fun decodeAndResizeBitmap(bytes: ByteArray, targetSizePx: Int = DISPLAY_ART_SIZE_PX): Bitmap? {
        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
            val srcW = boundsOpts.outWidth
            val srcH = boundsOpts.outHeight
            if (srcW <= 0 || srcH <= 0) return null

            var inSampleSize = 1
            while ((srcW / inSampleSize) > targetSizePx * 2 || (srcH / inSampleSize) > targetSizePx * 2) {
                inSampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return null
            val maxDim = max(decoded.width, decoded.height)
            if (maxDim > targetSizePx) {
                val ratio = targetSizePx.toFloat() / maxDim.toFloat()
                val w = (decoded.width * ratio).roundToInt().coerceAtLeast(1)
                val h = (decoded.height * ratio).roundToInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(decoded, w, h, true)
                if (scaled !== decoded) {
                    decoded.recycle()
                }
                scaled
            } else {
                decoded
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun preloadArtBitmap(
        context: Context,
        uriOrPath: String,
        targetSizePx: Int = DISPLAY_ART_SIZE_PX
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (uriOrPath.isBlank()) return@withContext null
        getCachedBitmap(uriOrPath)?.let { return@withContext it }

        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        } catch (_: Throwable) {}

        val bytes = extractArtDirect(context, uriOrPath) ?: return@withContext null
        val bmp = decodeAndResizeBitmap(bytes, targetSizePx) ?: return@withContext null
        synchronized(bitmapCache) {
            bitmapCache.put(uriOrPath, bmp)
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "[SYNC_DIAG] Preloaded resized art (${bmp.width}x${bmp.height}) ts=${SystemClock.elapsedRealtime()} key=$uriOrPath")
        }
        bmp
    }

    fun extractArtDirect(context: Context, uriOrPath: String): ByteArray? {
        if (uriOrPath.isBlank()) return null
        synchronized(artCache) {
            val cached = artCache.get(uriOrPath)
            if (cached != null) return cached
        }

        val retriever = MediaMetadataRetriever()
        var picture: ByteArray? = null
        try {
            if (uriOrPath.startsWith("content://")) {
                retriever.setDataSource(context, Uri.parse(uriOrPath))
            } else {
                val file = File(uriOrPath)
                if (file.exists()) {
                    retriever.setDataSource(file.absolutePath)
                }
            }
            picture = retriever.embeddedPicture
            if (picture != null) {
                synchronized(artCache) {
                    artCache.put(uriOrPath, picture)
                }
            }
        } catch (_: Exception) {
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
        return picture
    }

    suspend fun getEmbeddedArt(context: Context, uriOrPath: String): ByteArray? = withContext(Dispatchers.IO) {
        extractArtDirect(context, uriOrPath)
    }
}
