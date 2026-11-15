package com.joaohouto.clusterlauncher.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.joaohouto.clusterlauncher.data.model.Folder
import com.joaohouto.clusterlauncher.data.model.Track
import com.joaohouto.clusterlauncher.data.repository.MusicRepository
import com.joaohouto.clusterlauncher.player.PlaybackUiState
import com.joaohouto.clusterlauncher.player.PlayerController
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class BottomBarUiState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: Int = 0
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository.getInstance(application)
    private val playerController = PlayerController.getInstance(application)

    val isScanning: StateFlow<Boolean> = repository.isScanning

    val folders: StateFlow<List<Folder>> = repository.allFolders.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _selectedFolderPath = MutableStateFlow<String?>(null)

    val selectedFolder: StateFlow<Folder?> = combine(
        folders,
        _selectedFolderPath,
        playerController.uiState.map { it.currentTrack?.folderPath }.distinctUntilChanged()
    ) { folderList, manualPath, playingFolderPath ->
        if (folderList.isEmpty()) {
            null
        } else {
            val byManual = manualPath?.let { path -> folderList.firstOrNull { it.path == path } }
            val byPlaying = playingFolderPath?.let { path -> folderList.firstOrNull { it.path == path } }
            byManual ?: byPlaying ?: folderList.first()
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val selectedFolderTracks: StateFlow<List<Track>> = selectedFolder
        .map { it?.path }
        .distinctUntilChanged()
        .flatMapLatest { path ->
            if (path.isNullOrEmpty()) {
                flowOf(emptyList())
            } else {
                repository.getTracksForFolder(path)
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val playbackState: StateFlow<PlaybackUiState> = playerController.uiState

    val bottomBarState: StateFlow<BottomBarUiState> = playerController.uiState
        .map {
            BottomBarUiState(
                currentTrack = it.currentTrack,
                isPlaying = it.isPlaying,
                isShuffleEnabled = it.isShuffleEnabled,
                repeatMode = it.repeatMode
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BottomBarUiState()
        )

    init {
        playerController.initialize()
    }

    fun selectFolder(folder: Folder) {
        _selectedFolderPath.value = folder.path
    }

    fun playFolder(folderPath: String, shuffle: Boolean? = null) {
        _selectedFolderPath.value = folderPath
        playerController.playFolder(folderPath, shuffle = shuffle)
    }

    fun playTrackInSelectedFolder(trackIndex: Int) {
        val tracks = selectedFolderTracks.value
        val track = tracks.getOrNull(trackIndex) ?: return
        if (!track.uri.startsWith("content://") && track.path.isNotEmpty() && !File(track.path).exists() && !File(track.path).canRead()) {
            playerController.showCannotPlayToast()
            return
        }
        playerController.playTrackFromQueue(track, tracks, trackIndex)
    }

    fun playPause() {
        playerController.playPause()
    }

    fun next() {
        playerController.next()
    }

    fun previous() {
        playerController.previous()
    }

    fun toggleShuffle() {
        playerController.toggleShuffle()
    }

    fun cycleRepeatMode() {
        playerController.cycleRepeatMode()
    }

    fun rescan(clearOld: Boolean = true) {
        repository.triggerScan(clearOld = clearOld)
    }
}
