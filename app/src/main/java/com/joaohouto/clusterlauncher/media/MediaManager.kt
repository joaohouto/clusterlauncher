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
import com.joaohouto.clusterlauncher.data.local.PreferencesDataStore
import com.joaohouto.clusterlauncher.data.model.MediaState
import com.joaohouto.clusterlauncher.data.repository.AutomotivePackageResolver
import com.joaohouto.clusterlauncher.player.PlayerController
import com.joaohouto.clusterlauncher.ui.components.AudioArtExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object MediaManager {

    private const val EXTRA_HIGH_RES_ART = "cluster_high_res_art"
    private const val EXTRA_HIGH_RES_MEDIA_ID = "cluster_high_res_media_id"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _mediaState = MutableStateFlow(MediaState())
    val mediaState: StateFlow<MediaState> = _mediaState.asStateFlow()

    /**
     * Fluxo dedicado exclusivamente à posição de reprodução (ms).
     * Separado do [mediaState] para evitar que o CockpitScreen e o FullBleedMediaCard
     * inteiro sofram recomposição a cada 150ms durante a reprodução.
     */
    private val _mediaPositionMs = MutableStateFlow(0L)
    val mediaPositionMs: StateFlow<Long> = _mediaPositionMs.asStateFlow()

    private var appContext: Context? = null
    private var playerController: PlayerController? = null
    private var activeController: MediaController? = null
    private val highResBitmapCache = object : LruCache<String, Bitmap>(12) {}
    private var showLyricsInPlayer: Boolean = true

    fun init(context: Context) {
        if (appContext != null) return
        val ctx = context.applicationContext
        appContext = ctx
        val pc = PlayerController.getInstance(ctx)
        playerController = pc
        val prefs = PreferencesDataStore.getInstance(ctx)

        scope.launch {
            prefs.showLyricsFlow.collect { enabled ->
                showLyricsInPlayer = enabled
                syncState()
            }
        }

        scope.launch {
            pc.uiState.collect { state ->
                val track = state.currentTrack
                if (track != null) {
                    val artKey = track.path.ifEmpty { track.uri }
                    if (artKey.isNotEmpty() && AudioArtExtractor.getCachedBitmap(artKey) == null) {
                        launch(Dispatchers.IO) {
                            val bmp = AudioArtExtractor.preloadArtBitmap(ctx, artKey)
                            if (bmp != null) {
                                withContext(Dispatchers.Main) { syncState() }
                            }
                        }
                    }
                }
                syncState()
            }
        }

        scope.launch {
            pc.currentPosition.collect { posMs ->
                val current = _mediaState.value
                if (!current.isExternalSession && current.hasActiveSession) {
                    _mediaPositionMs.value = posMs
                }
            }
        }
    }

    private val progressTicker = object : Runnable {
        override fun run() {
            val controller = activeController
            val pbState = controller?.playbackState
            if (_mediaState.value.isExternalSession && controller != null && pbState?.state == PlaybackState.STATE_PLAYING) {
                val duration = _mediaState.value.durationMs.takeIf { it > 0L }
                    ?: (controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0L } ?: 0L)
                val pos = calculateCurrentPositionMs(pbState, duration)
                _mediaPositionMs.value = pos
                if (duration != _mediaState.value.durationMs) {
                    _mediaState.value = _mediaState.value.copy(durationMs = duration)
                }
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
        if (_mediaState.value.isPermissionGranted != granted) {
            _mediaState.value = _mediaState.value.copy(isPermissionGranted = granted)
        }
    }

    fun onSessionsChanged(controllers: List<MediaController>) {
        mainHandler.post {
            val ownPkg = appContext?.packageName ?: "com.joaohouto.clusterlauncher"
            val externalControllers = controllers.filter { it.packageName != ownPkg }

            val chosen = externalControllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: externalControllers.firstOrNull { it.metadata != null }
                ?: externalControllers.firstOrNull()

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

        val internalState = playerController?.uiState?.value
        val internalTrack = internalState?.currentTrack
        val internalPlaying = internalState?.isPlaying == true

        val extController = activeController
        val extPlaying = extController?.playbackState?.state == PlaybackState.STATE_PLAYING

        val useExternal = !internalPlaying && extController != null && (extPlaying || internalTrack == null)

        if (useExternal && extController != null) {
            val metadata = extController.metadata
            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                ?: ""

            val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_AUTHOR)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                ?: ""

            val description = metadata?.description

            val highResArt = resolveHighResBitmap(extController)
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

            val pbState = extController.playbackState
            val isPlaying = pbState?.state == PlaybackState.STATE_PLAYING
            val positionMs = if (title.isBlank() && !isPlaying) 0L else calculateCurrentPositionMs(pbState, durationMs)

            _mediaPositionMs.value = positionMs
            _mediaState.value = _mediaState.value.copy(
                title = title,
                artist = artist,
                albumArtBitmap = artBitmap,
                albumArtUri = artUri,
                isPlaying = isPlaying,
                positionMs = 0L,
                durationMs = durationMs,
                packageName = extController.packageName,
                hasActiveSession = title.isNotBlank() || isPlaying,
                lyricsLine = null,
                isExternalSession = true
            )

            if (isPlaying) {
                mainHandler.postDelayed(progressTicker, 500L)
            }
            return
        }

        if (internalTrack != null && internalState != null) {
            val posMs = playerController?.currentPosition?.value ?: 0L
            val durationMs = internalState.durationMs.takeIf { it > 0L } ?: internalTrack.durationMs
            val artKey = internalTrack.path.ifEmpty { internalTrack.uri }
            val artBitmap = AudioArtExtractor.getCachedBitmap(artKey)
            val activeLine = if (showLyricsInPlayer) internalState.currentLyricsLine else null

            _mediaPositionMs.value = posMs
            _mediaState.value = _mediaState.value.copy(
                title = internalTrack.title,
                artist = internalTrack.artist,
                albumArtBitmap = artBitmap,
                albumArtUri = null,
                isPlaying = internalState.isPlaying,
                positionMs = 0L,
                durationMs = durationMs,
                packageName = appContext?.packageName,
                hasActiveSession = true,
                lyricsLine = activeLine,
                isExternalSession = false
            )
            return
        }

        _mediaPositionMs.value = 0L
        _mediaState.value = _mediaState.value.copy(
            title = "",
            artist = "",
            albumArtBitmap = null,
            albumArtUri = null,
            isPlaying = false,
            positionMs = 0L,
            durationMs = 0L,
            packageName = null,
            hasActiveSession = false,
            lyricsLine = null,
            isExternalSession = false
        )
    }

    fun playPause() {
        if (_mediaState.value.isExternalSession) {
            val controller = activeController ?: return
            val isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING
            if (isPlaying) {
                controller.transportControls.pause()
            } else {
                controller.transportControls.play()
            }
        } else {
            playerController?.playPause()
        }
    }

    fun skipToPrevious() {
        if (_mediaState.value.isExternalSession) {
            activeController?.transportControls?.skipToPrevious()
        } else {
            playerController?.previous()
        }
    }

    fun skipToNext() {
        if (_mediaState.value.isExternalSession) {
            activeController?.transportControls?.skipToNext()
        } else {
            playerController?.next()
        }
    }

    /**
     * Encerra imediatamente a reprodução e limpa a fila de músicas (tanto interna quanto externa).
     */
    fun stopAndClearQueue() {
        mainHandler.removeCallbacks(progressTicker)
        try {
            activeController?.transportControls?.stop()
        } catch (_: Exception) {}
        playerController?.stopAndClearQueue()
        _mediaPositionMs.value = 0L
        _mediaState.value = _mediaState.value.copy(
            title = "",
            artist = "",
            albumArtBitmap = null,
            albumArtUri = null,
            isPlaying = false,
            positionMs = 0L,
            durationMs = 0L,
            packageName = null,
            hasActiveSession = false,
            lyricsLine = null,
            isExternalSession = false
        )
    }

    fun openExternalPlayerIfActive(context: Context): Boolean {
        if (!_mediaState.value.isExternalSession) return false
        val controller = activeController ?: return false
        val pm = context.packageManager
        try {
            controller.sessionActivity?.send()
            return true
        } catch (_: Exception) {
            val pkg = controller.packageName
            if (!pkg.isNullOrEmpty()) {
                val intent = AutomotivePackageResolver.getLaunchIntent(pm, pkg)
                if (intent != null) {
                    try {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        return true
                    } catch (_: Exception) {}
                }
            }
        }
        return false
    }
}
