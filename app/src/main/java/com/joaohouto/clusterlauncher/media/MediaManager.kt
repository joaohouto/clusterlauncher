package com.joaohouto.clusterlauncher.media

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.LruCache
import com.joaohouto.clusterlauncher.data.model.DockSlotType
import com.joaohouto.clusterlauncher.data.model.MediaState
import com.joaohouto.clusterlauncher.data.repository.AutomotivePackageResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MediaManager {

    private const val CLUSTER_PLAYER_PACKAGE = "com.joaohouto.clusterplayer"
    private const val EXTRA_HIGH_RES_ART = "cluster_high_res_art"
    private const val EXTRA_HIGH_RES_MEDIA_ID = "cluster_high_res_media_id"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val _mediaState = MutableStateFlow(MediaState())
    val mediaState: StateFlow<MediaState> = _mediaState.asStateFlow()

    private var activeController: MediaController? = null
    private val highResBitmapCache = object : LruCache<String, Bitmap>(12) {}

    private val progressTicker = object : Runnable {
        override fun run() {
            val controller = activeController
            val pbState = controller?.playbackState
            if (controller != null && pbState?.state == PlaybackState.STATE_PLAYING) {
                val duration = _mediaState.value.durationMs.takeIf { it > 0L }
                    ?: (controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0L } ?: 0L)
                val pos = calculateCurrentPositionMs(pbState, duration)
                _mediaState.value = _mediaState.value.copy(
                    positionMs = pos,
                    durationMs = duration
                )
                mainHandler.postDelayed(this, 500L)
            }
        }
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            syncState()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            syncState()
        }

        override fun onExtrasChanged(extras: Bundle?) {
            syncState()
        }

        override fun onSessionDestroyed() {
            activeController = null
            syncState()
        }
    }

    fun setPermissionGranted(granted: Boolean) {
        _mediaState.value = _mediaState.value.copy(isPermissionGranted = granted)
    }

    fun onSessionsChanged(controllers: List<MediaController>) {
        mainHandler.post {
            // Pick currently playing controller first, or first with metadata, or first non-null
            val chosen = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: controllers.firstOrNull { it.metadata != null }
                ?: controllers.firstOrNull()

            if (chosen?.sessionToken != activeController?.sessionToken) {
                activeController?.unregisterCallback(controllerCallback)
                activeController = chosen
                activeController?.registerCallback(controllerCallback, mainHandler)
            }
            syncState()
        }
    }

    private fun calculateCurrentPositionMs(pbState: PlaybackState?, durationMs: Long): Long {
        if (pbState == null) return 0L
        val basePos = pbState.position
        if (basePos < 0L) return 0L
        val currentPos = if (pbState.state == PlaybackState.STATE_PLAYING && pbState.lastPositionUpdateTime > 0L) {
            val elapsed = (SystemClock.elapsedRealtime() - pbState.lastPositionUpdateTime).coerceAtLeast(0L)
            val speed = if (pbState.playbackSpeed > 0f) pbState.playbackSpeed else 1f
            basePos + (elapsed * speed).toLong()
        } else {
            basePos
        }
        return if (durationMs > 0L) currentPos.coerceIn(0L, durationMs) else currentPos.coerceAtLeast(0L)
    }

    private fun resolveHighResBitmap(controller: MediaController): Bitmap? {
        val extras = controller.extras ?: return null
        val bytes = extras.getByteArray(EXTRA_HIGH_RES_ART) ?: return null
        if (bytes.isEmpty()) return null
        val cacheKey = extras.getString(EXTRA_HIGH_RES_MEDIA_ID) ?: bytes.contentHashCode().toString()
        val cached = highResBitmapCache.get(cacheKey)
        if (cached != null && !cached.isRecycled) {
            return cached
        }
        return try {
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            if (decoded != null) {
                highResBitmapCache.put(cacheKey, decoded)
            }
            decoded
        } catch (_: Exception) {
            null
        }
    }

    private fun syncState() {
        mainHandler.removeCallbacks(progressTicker)
        val controller = activeController
        if (controller == null) {
            _mediaState.value = _mediaState.value.copy(
                title = "",
                artist = "",
                albumArtBitmap = null,
                albumArtUri = null,
                isPlaying = false,
                positionMs = 0L,
                durationMs = 0L,
                packageName = null,
                hasActiveSession = false
            )
            return
        }

        val metadata = controller.metadata
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: ""

        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_AUTHOR)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: ""

        val description = metadata?.description

        val highResArt = resolveHighResBitmap(controller)
        val artBitmap = highResArt
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: description?.iconBitmap

        val artUriStr = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
        val artUri = (artUriStr?.let { runCatching { Uri.parse(it) }.getOrNull() })
            ?: description?.iconUri

        val rawDuration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val durationMs = if (rawDuration > 0L) rawDuration else 0L

        val pbState = controller.playbackState
        val isPlaying = pbState?.state == PlaybackState.STATE_PLAYING
        val positionMs = if (title.isBlank() && !isPlaying) 0L else calculateCurrentPositionMs(pbState, durationMs)

        _mediaState.value = _mediaState.value.copy(
            title = title,
            artist = artist,
            albumArtBitmap = artBitmap,
            albumArtUri = artUri,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            packageName = controller.packageName,
            hasActiveSession = true
        )

        if (isPlaying) {
            mainHandler.postDelayed(progressTicker, 500L)
        }
    }

    fun playPause() {
        val controller = activeController ?: return
        val isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING
        if (isPlaying) {
            controller.transportControls.pause()
        } else {
            controller.transportControls.play()
        }
    }

    fun skipToPrevious() {
        activeController?.transportControls?.skipToPrevious()
    }

    fun skipToNext() {
        activeController?.transportControls?.skipToNext()
    }

    fun openPlayer(context: Context) {
        val pm = context.packageManager

        // 1. Por padrão, abre o ClusterPlayer se estiver instalado
        if (AutomotivePackageResolver.isPackageInstalled(pm, CLUSTER_PLAYER_PACKAGE)) {
            val clusterPlayerIntent = AutomotivePackageResolver.getLaunchIntent(pm, CLUSTER_PLAYER_PACKAGE)
            if (clusterPlayerIntent != null) {
                try {
                    clusterPlayerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(clusterPlayerIntent)
                    return
                } catch (_: Exception) {}
            }
        }

        // 2. Fallback: se houver uma MediaSession ativa de outro app, abre o app da sessão
        val controller = activeController
        if (controller != null) {
            try {
                controller.sessionActivity?.send()
                return
            } catch (_: Exception) {
                val pkg = controller.packageName
                if (!pkg.isNullOrEmpty()) {
                    val intent = AutomotivePackageResolver.getLaunchIntent(pm, pkg)
                    if (intent != null) {
                        try {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                            return
                        } catch (_: Exception) {}
                    }
                }
            }
        }

        // 3. Último fallback: resolve o player de música padrão instalado no sistema
        val defaultMusicPkg = AutomotivePackageResolver.resolveDefaultPackage(context, DockSlotType.Music)
        if (!defaultMusicPkg.isNullOrEmpty()) {
            val fallbackIntent = AutomotivePackageResolver.getLaunchIntent(pm, defaultMusicPkg)
            if (fallbackIntent != null) {
                try {
                    fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(fallbackIntent)
                } catch (_: Exception) {}
            }
        }
    }
}
