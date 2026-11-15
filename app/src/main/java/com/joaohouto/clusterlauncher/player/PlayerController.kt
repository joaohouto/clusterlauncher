package com.joaohouto.clusterlauncher.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.joaohouto.clusterlauncher.BuildConfig
import com.joaohouto.clusterlauncher.R
import com.joaohouto.clusterlauncher.data.local.PreferencesDataStore
import com.joaohouto.clusterlauncher.data.model.Track
import com.joaohouto.clusterlauncher.data.repository.MusicRepository
import com.joaohouto.clusterlauncher.lyrics.LrcLine
import com.joaohouto.clusterlauncher.lyrics.LrcParser
import com.joaohouto.clusterlauncher.ui.components.AudioArtExtractor
import com.joaohouto.clusterlauncher.ui.components.PreloadedTrackMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class PlaybackUiState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val currentLyricsLine: String? = null,
    val isConnected: Boolean = false
)

class PlayerController private constructor(private val context: Context) {

    companion object {
        private const val TAG = "PlayerController"

        @Volatile
        private var INSTANCE: PlayerController? = null

        fun getInstance(context: Context): PlayerController {
            return INSTANCE ?: synchronized(this) {
                val instance = PlayerController(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var directPlayer: Player? = null
    private var directListener: Player.Listener? = null

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private var positionTickerJob: Job? = null
    private var nextPreloadJob: Job? = null
    private var lyricsLines: List<LrcLine>? = null
    private var lastSeekTimestamp = 0L
    private var lastSeekTargetMs = -1L
    private var anchorPositionMs = 0L
    private var anchorRealtimeMs = 0L
    private var lastDirectTransitionRealtimeMs = 0L
    private var lastPlaybackState: Int = Player.STATE_IDLE

    private fun getActivePlayer(): Player? {
        return directPlayer ?: PlaybackService.getMediaSessionInstance()?.player ?: mediaController
    }

    fun getDebugActivePlayerPositionMs(): Long? {
        if (!BuildConfig.DEBUG || Looper.myLooper() != Looper.getMainLooper()) return null
        return getActivePlayer()?.currentPosition
    }

    private fun isEffectivelyPlaying(player: Player): Boolean {
        return player.isPlaying || (
            player.playWhenReady &&
                player.playbackState != Player.STATE_ENDED &&
                player.playbackState != Player.STATE_IDLE &&
                player.mediaItemCount > 0
            )
    }

    private val repository = MusicRepository.getInstance(context)
    private val preferencesDataStore = PreferencesDataStore.getInstance(context)
    private var userLyricsOffsetMs: Long = 0L
    private var audioSyncOffsetMs: Long = 0L

    val lyricsOffsetMs: StateFlow<Int> = preferencesDataStore.lyricsOffsetMsFlow
        .stateIn(
            scope = controllerScope,
            started = SharingStarted.Eagerly,
            initialValue = 0
        )

    init {
        controllerScope.launch {
            preferencesDataStore.lyricsOffsetMsFlow.collect { offset: Int ->
                userLyricsOffsetMs = offset.toLong()
                updateLyricsLine(_currentPosition.value)
            }
        }
        controllerScope.launch {
            preferencesDataStore.audioSyncOffsetMsFlow.collect { offset: Int ->
                audioSyncOffsetMs = offset.toLong()
                getActivePlayer()?.let { reanchorAndSync(it) }
            }
        }
    }

    private fun toDisplayedPositionMs(rawPosMs: Long, durationMs: Long): Long {
        val shifted = (rawPosMs + audioSyncOffsetMs).coerceAtLeast(0L)
        return if (durationMs > 0L) shifted.coerceAtMost(durationMs) else shifted
    }

    fun setLyricsOffset(offsetMs: Int) {
        controllerScope.launch {
            preferencesDataStore.saveLyricsOffsetMs(offsetMs)
        }
    }

    fun initialize() {
        if (mediaController != null || controllerFuture != null) return

        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )

        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                val controller = controllerFuture?.get() ?: return@addListener
                mediaController = controller
                setupController(controller)
                _uiState.value = _uiState.value.copy(isConnected = true)
                updateStateFromController()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect MediaController", e)
            }
        }, MoreExecutors.directExecutor())
    }

    private var lastToastTime = 0L

    fun showCannotPlayToast() {
        val now = System.currentTimeMillis()
        if (now - lastToastTime < 1500L) return
        lastToastTime = now
        controllerScope.launch(Dispatchers.Main) {
            Toast.makeText(
                context,
                context.getString(R.string.error_cannot_play),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun attachDirectPlayer(player: Player) {
        if (directPlayer === player) return
        detachDirectPlayer(directPlayer)
        directPlayer = player
        val listener = createPlayerListener(isDirect = true)
        directListener = listener
        player.addListener(listener)
        lastPlaybackState = player.playbackState
        reanchorAndSync(player, mediaItem = player.currentMediaItem)
    }

    fun detachDirectPlayer(player: Player?) {
        if (player == null) return
        directListener?.let { player.removeListener(it) }
        if (directPlayer === player) {
            directPlayer = null
            directListener = null
        }
    }

    private fun createPlayerListener(isDirect: Boolean): Player.Listener {
        return object : Player.Listener {
            private fun shouldIgnore(): Boolean = !isDirect && directPlayer != null

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (shouldIgnore()) return
                val player = getActivePlayer() ?: return
                reanchorAndSync(player)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (shouldIgnore()) return
                val player = getActivePlayer() ?: return
                reanchorAndSync(player)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (shouldIgnore()) return
                val player = getActivePlayer() ?: return
                lastPlaybackState = playbackState
                reanchorAndSync(player)
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                if (shouldIgnore()) return
                val player = getActivePlayer() ?: return
                reanchorAndSync(player)
            }

            override fun onPlayerError(error: PlaybackException) {
                if (shouldIgnore()) return
                showCannotPlayToast()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (shouldIgnore()) return
                val player = getActivePlayer() ?: return
                val now = SystemClock.elapsedRealtime()
                if (isDirect) {
                    lastDirectTransitionRealtimeMs = now
                }
                lastSeekTimestamp = 0L
                lastSeekTargetMs = -1L
                reanchorAndSync(player, mediaItem = mediaItem ?: player.currentMediaItem)
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (shouldIgnore()) return
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION ||
                    reason == Player.DISCONTINUITY_REASON_SEEK ||
                    reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
                ) {
                    val player = getActivePlayer() ?: return
                    lastSeekTimestamp = 0L
                    lastSeekTargetMs = -1L
                    reanchorAndSync(player, mediaItem = player.currentMediaItem)
                }
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (shouldIgnore()) return
                val player = getActivePlayer() ?: return
                reanchorAndSync(player)
                scheduleNextTrackPreload(player)
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                if (shouldIgnore()) return
                val player = getActivePlayer() ?: return
                _uiState.value = _uiState.value.copy(isShuffleEnabled = player.shuffleModeEnabled)
                scheduleNextTrackPreload(player)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                if (shouldIgnore()) return
                val player = getActivePlayer() ?: return
                _uiState.value = _uiState.value.copy(repeatMode = player.repeatMode)
                scheduleNextTrackPreload(player)
            }
        }
    }

    private fun setupController(controller: MediaController) {
        controller.addListener(createPlayerListener(isDirect = false))
        if (directPlayer == null) {
            reanchorAndSync(controller, mediaItem = controller.currentMediaItem)
        }
    }

    private fun buildTrackFromMediaItem(mediaItem: MediaItem?): Track? {
        if (mediaItem == null) return null
        val mediaId = mediaItem.mediaId
        val extras = mediaItem.mediaMetadata.extras
        var filePath = extras?.getString("filePath") ?: ""
        val folderPath = extras?.getString("folderPath") ?: ""
        var title = mediaItem.mediaMetadata.title?.toString() ?: ""
        var artist = mediaItem.mediaMetadata.artist?.toString() ?: ""
        var album = mediaItem.mediaMetadata.albumTitle?.toString() ?: ""
        val durationMs = extras?.getLong("durationMs") ?: 0L

        if (filePath.isEmpty() && mediaId.isNotEmpty()) {
            if (mediaId.startsWith("file://")) {
                filePath = Uri.parse(mediaId).path ?: mediaId
            } else if (mediaId.startsWith("/")) {
                filePath = mediaId
            }
        }

        if (title.isBlank() || title == "Desconhecido") {
            title = if (filePath.isNotEmpty()) {
                File(filePath).nameWithoutExtension
            } else {
                "Desconhecido"
            }
        }
        if (artist.isBlank()) artist = "Artista Desconhecido"
        if (album.isBlank()) album = "Álbum Desconhecido"

        return Track(
            id = 0,
            uri = mediaId,
            path = filePath,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            folderPath = folderPath
        )
    }

    private fun startAsyncTrackExtras(track: Track) {
        val mediaId = track.uri
        val filePath = track.path
        val artPath = if (filePath.isNotEmpty()) filePath else mediaId

        if (artPath.isNotEmpty() && AudioArtExtractor.getCachedBitmap(artPath) == null) {
            controllerScope.launch(Dispatchers.IO) {
                AudioArtExtractor.preloadArtBitmap(context, artPath)
            }
        }

        val cachedMeta = AudioArtExtractor.getCachedMetadata(mediaId)
        if (cachedMeta != null && cachedMeta.lyricsLines != null) {
            lyricsLines = cachedMeta.lyricsLines
            updateLyricsLine(_currentPosition.value)
            return
        }

        controllerScope.launch {
            var targetPath = filePath
            if (targetPath.isEmpty() && mediaId.isNotEmpty() && mediaId.startsWith("content://")) {
                val dbTrack = repository.getTrackByUri(mediaId)
                if (dbTrack != null && dbTrack.path.isNotEmpty()) {
                    targetPath = dbTrack.path
                }
            }

            val loadedLines = if (targetPath.isNotEmpty()) {
                LrcParser.findAndParseLrc(targetPath)
            } else {
                null
            }

            if (_uiState.value.currentTrack?.uri == mediaId) {
                lyricsLines = loadedLines
                updateLyricsLine(_currentPosition.value)
            }
        }
    }

    private fun scheduleNextTrackPreload(activePlayer: Player) {
        nextPreloadJob?.cancel()
        nextPreloadJob = controllerScope.launch {
            delay(5000L)
            val nextIndex = activePlayer.nextMediaItemIndex
            if (nextIndex == C.INDEX_UNSET || nextIndex !in 0 until activePlayer.mediaItemCount) {
                return@launch
            }
            val nextItem = activePlayer.getMediaItemAt(nextIndex)
            val nextTrack = buildTrackFromMediaItem(nextItem) ?: return@launch
            val artPath = if (nextTrack.path.isNotEmpty()) nextTrack.path else nextTrack.uri

            kotlinx.coroutines.withContext(Dispatchers.IO) {
                try {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                } catch (_: Throwable) {}

                if (artPath.isNotEmpty() && AudioArtExtractor.getCachedBitmap(artPath) == null) {
                    AudioArtExtractor.preloadArtBitmap(context, artPath)
                }

                if (AudioArtExtractor.getCachedMetadata(nextTrack.uri) == null) {
                    var targetPath = nextTrack.path
                    if (targetPath.isEmpty() && nextTrack.uri.startsWith("content://")) {
                        val dbTrack = repository.getTrackByUri(nextTrack.uri)
                        if (dbTrack != null && dbTrack.path.isNotEmpty()) {
                            targetPath = dbTrack.path
                        }
                    }
                    val lines = if (targetPath.isNotEmpty()) {
                        LrcParser.findAndParseLrc(targetPath)
                    } else {
                        null
                    }
                    AudioArtExtractor.putCachedMetadata(
                        nextTrack.uri,
                        PreloadedTrackMetadata(
                            uri = nextTrack.uri,
                            filePath = targetPath,
                            title = nextTrack.title,
                            artist = nextTrack.artist,
                            album = nextTrack.album,
                            durationMs = nextTrack.durationMs,
                            lyricsLines = lines
                        )
                    )
                }
            }
        }
    }

    private fun reanchorAndSync(
        activePlayer: Player,
        mediaItem: MediaItem? = activePlayer.currentMediaItem,
        explicitPositionMs: Long? = null
    ) {
        val rawPos = (explicitPositionMs ?: activePlayer.currentPosition).coerceAtLeast(0L)
        val now = SystemClock.elapsedRealtime()

        val isSeekingDebounced = explicitPositionMs == null && (now - lastSeekTimestamp) < 800L
        val resolvedPos = if (isSeekingDebounced && lastSeekTargetMs >= 0L) {
            if (kotlin.math.abs(rawPos - lastSeekTargetMs) < 1500L) {
                lastSeekTimestamp = 0L
                lastSeekTargetMs = -1L
                rawPos
            } else {
                lastSeekTargetMs
            }
        } else {
            rawPos
        }

        anchorPositionMs = resolvedPos
        anchorRealtimeMs = now

        val prevTrack = _uiState.value.currentTrack
        val trackChanged = mediaItem?.mediaId != prevTrack?.uri
        val newTrack = if (trackChanged) buildTrackFromMediaItem(mediaItem) else prevTrack
        if (trackChanged) {
            lyricsLines = null
        }

        val controller = mediaController
        val playerDuration = activePlayer.duration.takeIf { it > 0 }
            ?: controller?.duration?.takeIf { it > 0 }
            ?: 0L
        val duration = if (playerDuration > 0) playerDuration else newTrack?.durationMs ?: 0L
        val effectivePlaying = isEffectivelyPlaying(activePlayer)

        val displayedPos = toDisplayedPositionMs(resolvedPos, duration)

        val nextLyricsLine = if (trackChanged) {
            null
        } else {
            val lines = lyricsLines
            if (!lines.isNullOrEmpty()) {
                val adjusted = (displayedPos + userLyricsOffsetMs).coerceAtLeast(0L)
                LrcParser.getActiveLine(lines, adjusted)
            } else {
                null
            }
        }

        _currentPosition.value = displayedPos
        _uiState.value = _uiState.value.copy(
            currentTrack = newTrack,
            isPlaying = effectivePlaying,
            currentPositionMs = displayedPos,
            durationMs = duration,
            isShuffleEnabled = activePlayer.shuffleModeEnabled,
            repeatMode = activePlayer.repeatMode,
            currentLyricsLine = nextLyricsLine
        )

        if (effectivePlaying) {
            if (positionTickerJob?.isActive != true) {
                startPositionTicker()
            }
        } else {
            stopPositionTicker()
        }

        if (trackChanged && newTrack != null) {
            startAsyncTrackExtras(newTrack)
            scheduleNextTrackPreload(activePlayer)
        }
    }

    private fun updateStateFromController() {
        val activePlayer = getActivePlayer() ?: return
        reanchorAndSync(activePlayer)
    }

    private fun updateLyricsLine(positionMs: Long) {
        val lines = lyricsLines
        if (!lines.isNullOrEmpty()) {
            val adjustedPosition = (positionMs + userLyricsOffsetMs).coerceAtLeast(0L)
            val active = LrcParser.getActiveLine(lines, adjustedPosition)
            if (active != _uiState.value.currentLyricsLine) {
                _uiState.value = _uiState.value.copy(currentLyricsLine = active)
            }
        } else if (_uiState.value.currentLyricsLine != null) {
            _uiState.value = _uiState.value.copy(currentLyricsLine = null)
        }
    }

    private fun startPositionTicker() {
        stopPositionTicker()
        positionTickerJob = controllerScope.launch {
            while (isActive) {
                delay(150L)
                val activePlayer = getActivePlayer() ?: continue
                val effectivePlaying = isEffectivelyPlaying(activePlayer)
                if (effectivePlaying != _uiState.value.isPlaying) {
                    reanchorAndSync(activePlayer)
                    if (!effectivePlaying) break
                }
                if (effectivePlaying) {
                    val currentItem = activePlayer.currentMediaItem
                    if (currentItem != null && currentItem.mediaId != _uiState.value.currentTrack?.uri) {
                        reanchorAndSync(activePlayer, mediaItem = currentItem)
                        continue
                    }

                    val now = SystemClock.elapsedRealtime()
                    val speed = activePlayer.playbackParameters.speed.coerceAtLeast(0.1f)
                    val projectedPos = anchorPositionMs + ((now - anchorRealtimeMs) * speed).toLong()
                    val playerPos = activePlayer.currentPosition.coerceAtLeast(0L)

                    val isSeekingDebounced = (now - lastSeekTimestamp) < 800L
                    if (!isSeekingDebounced && kotlin.math.abs(projectedPos - playerPos) > 45L) {
                        reanchorAndSync(activePlayer, explicitPositionMs = playerPos)
                    } else {
                        val controller = mediaController
                        val pDuration = activePlayer.duration.takeIf { it > 0 }
                            ?: controller?.duration?.takeIf { it > 0 }
                            ?: 0L
                        if (pDuration > 0 && pDuration != _uiState.value.durationMs) {
                            _uiState.value = _uiState.value.copy(durationMs = pDuration)
                        }
                        updateLyricsLine(toDisplayedPositionMs(projectedPos, pDuration))
                    }
                }
            }
        }
    }

    private fun stopPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = null
    }

    fun playPause() {
        val activePlayer = getActivePlayer() ?: return
        val currentlyPlaying = isEffectivelyPlaying(activePlayer)
        if (currentlyPlaying) {
            activePlayer.pause()
        } else {
            if (activePlayer.playbackState == Player.STATE_IDLE) {
                activePlayer.prepare()
            } else if (activePlayer.playbackState == Player.STATE_ENDED) {
                activePlayer.seekToDefaultPosition()
            }
            activePlayer.play()
        }
        updateStateFromController()
    }

    fun next() {
        val activePlayer = getActivePlayer() ?: return
        activePlayer.seekToNextMediaItem()
        if (activePlayer.playbackState == Player.STATE_IDLE) {
            activePlayer.prepare()
        }
        updateStateFromController()
    }

    fun previous() {
        val activePlayer = getActivePlayer() ?: return
        if (activePlayer.currentPosition > 3000L) {
            seekTo(0L)
        } else {
            activePlayer.seekToPreviousMediaItem()
            if (activePlayer.playbackState == Player.STATE_IDLE) {
                activePlayer.prepare()
            }
            updateStateFromController()
        }
    }

    fun seekTo(positionMs: Long) {
        val activePlayer = getActivePlayer() ?: return
        lastSeekTimestamp = SystemClock.elapsedRealtime()
        lastSeekTargetMs = positionMs
        activePlayer.seekTo(positionMs)
        reanchorAndSync(activePlayer, explicitPositionMs = positionMs)
        scheduleNextTrackPreload(activePlayer)
    }

    fun toggleShuffle() {
        val activePlayer = getActivePlayer() ?: return
        val nextMode = !activePlayer.shuffleModeEnabled
        activePlayer.shuffleModeEnabled = nextMode
        _uiState.value = _uiState.value.copy(isShuffleEnabled = nextMode)
    }

    fun setShuffleMode(enabled: Boolean) {
        val activePlayer = getActivePlayer() ?: return
        activePlayer.shuffleModeEnabled = enabled
        _uiState.value = _uiState.value.copy(isShuffleEnabled = enabled)
    }

    fun cycleRepeatMode() {
        val activePlayer = getActivePlayer() ?: return
        val nextMode = when (activePlayer.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        activePlayer.repeatMode = nextMode
        _uiState.value = _uiState.value.copy(repeatMode = nextMode)
    }

    fun playTrackFromQueue(track: Track, queueTracks: List<Track>, fallbackIndex: Int) {
        val activePlayer = getActivePlayer() ?: return
        if (!track.uri.startsWith("content://") && track.path.isNotEmpty() && !File(track.path).exists() && !File(track.path).canRead()) {
            showCannotPlayToast()
            return
        }

        val currentFolder = _uiState.value.currentTrack?.folderPath
        val isSameFolder = !track.folderPath.isEmpty() && currentFolder == track.folderPath
        var matchedIndex = -1

        if (isSameFolder && activePlayer.mediaItemCount > 0) {
            for (i in 0 until activePlayer.mediaItemCount) {
                if (activePlayer.getMediaItemAt(i).mediaId == track.uri) {
                    matchedIndex = i
                    break
                }
            }
        }

        if (matchedIndex >= 0 && activePlayer.mediaItemCount == queueTracks.size) {
            val item = activePlayer.getMediaItemAt(matchedIndex)
            val now = SystemClock.elapsedRealtime()
            lastSeekTimestamp = 0L
            lastSeekTargetMs = -1L
            anchorPositionMs = 0L
            anchorRealtimeMs = now
            activePlayer.seekTo(matchedIndex, 0L)
            if (activePlayer.playbackState == Player.STATE_IDLE) {
                activePlayer.prepare()
            }
            if (!activePlayer.isPlaying) {
                activePlayer.play()
            }
            reanchorAndSync(activePlayer, mediaItem = item, explicitPositionMs = 0L)
        } else if (queueTracks.isNotEmpty()) {
            playTracks(queueTracks, fallbackIndex)
        } else if (track.folderPath.isNotEmpty()) {
            playFolder(track.folderPath, fallbackIndex)
        } else {
            showCannotPlayToast()
        }
    }

    fun playFolder(folderPath: String, startIndex: Int = 0, shuffle: Boolean? = null) {
        controllerScope.launch {
            val tracks = repository.getTracksForFolderSync(folderPath)
            if (tracks.isEmpty()) {
                showCannotPlayToast()
                return@launch
            }

            val validTracks = tracks.filter { track ->
                track.uri.startsWith("content://") || track.path.isEmpty() || File(track.path).exists() || File(track.path).canRead()
            }

            if (validTracks.isEmpty()) {
                showCannotPlayToast()
                return@launch
            }

            if (shuffle != null) {
                setShuffleMode(shuffle)
            }

            val targetTrack = tracks.getOrNull(startIndex)
            val adjustedIndex = if (targetTrack != null) {
                validTracks.indexOfFirst { it.uri == targetTrack.uri }.takeIf { it >= 0 } ?: 0
            } else if (shuffle == true && validTracks.size > 1) {
                kotlin.random.Random.nextInt(validTracks.size)
            } else {
                0
            }

            playTracks(validTracks, adjustedIndex)
        }
    }

    fun playTracks(tracks: List<Track>, startIndex: Int = 0) {
        val activePlayer = getActivePlayer() ?: return
        if (tracks.isEmpty()) {
            showCannotPlayToast()
            return
        }

        val validTracks = tracks.filter { track ->
            track.uri.startsWith("content://") || track.path.isEmpty() || File(track.path).exists() || File(track.path).canRead()
        }
        if (validTracks.isEmpty()) {
            showCannotPlayToast()
            return
        }

        val targetTrack = tracks.getOrNull(startIndex)
        val adjustedIndex = if (targetTrack != null) {
            validTracks.indexOfFirst { it.uri == targetTrack.uri }.takeIf { it >= 0 } ?: 0
        } else {
            0
        }

        val mediaItems = validTracks.map { track ->
            val extras = Bundle().apply {
                putString("folderPath", track.folderPath)
                putString("filePath", track.path)
                putLong("durationMs", track.durationMs)
            }

            val metadata = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
                .setArtworkUri(Uri.parse(track.uri))
                .setExtras(extras)
                .build()

            MediaItem.Builder()
                .setMediaId(track.uri)
                .setUri(Uri.parse(track.uri))
                .setMediaMetadata(metadata)
                .build()
        }

        val safeIndex = adjustedIndex.coerceIn(0, mediaItems.size - 1)
        val now = SystemClock.elapsedRealtime()
        lastSeekTimestamp = 0L
        lastSeekTargetMs = -1L
        anchorPositionMs = 0L
        anchorRealtimeMs = now
        activePlayer.setMediaItems(mediaItems, safeIndex, 0L)
        activePlayer.prepare()
        activePlayer.play()
        reanchorAndSync(activePlayer, mediaItem = mediaItems[safeIndex], explicitPositionMs = 0L)
    }

    fun stop() {
        try {
            stopPositionTicker()
            nextPreloadJob?.cancel()
            nextPreloadJob = null
            mediaController?.stop()
            mediaController?.clearMediaItems()
            _uiState.value = _uiState.value.copy(isPlaying = false)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping player", e)
        }
    }

    fun release() {
        stopPositionTicker()
        mediaController?.release()
        mediaController = null
        controllerFuture = null
    }
}
