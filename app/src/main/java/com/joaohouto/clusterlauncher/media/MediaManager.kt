package com.joaohouto.clusterlauncher.media

import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.joaohouto.clusterlauncher.data.model.MediaState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MediaManager {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val _mediaState = MutableStateFlow(MediaState())
    val mediaState: StateFlow<MediaState> = _mediaState.asStateFlow()

    private var activeController: MediaController? = null

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            syncState()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
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

    private fun syncState() {
        val controller = activeController
        if (controller == null) {
            _mediaState.value = _mediaState.value.copy(
                title = "",
                artist = "",
                albumArtBitmap = null,
                albumArtUri = null,
                isPlaying = false,
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

        val artBitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: description?.iconBitmap

        val artUriStr = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
        val artUri = (artUriStr?.let { runCatching { Uri.parse(it) }.getOrNull() })
            ?: description?.iconUri

        val isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING

        _mediaState.value = _mediaState.value.copy(
            title = title,
            artist = artist,
            albumArtBitmap = artBitmap,
            albumArtUri = artUri,
            isPlaying = isPlaying,
            packageName = controller.packageName,
            hasActiveSession = true
        )
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
        val controller = activeController ?: return
        try {
            controller.sessionActivity?.send()
        } catch (_: Exception) {
            val pkg = controller.packageName
            if (!pkg.isNullOrEmpty()) {
                val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            }
        }
    }
}
