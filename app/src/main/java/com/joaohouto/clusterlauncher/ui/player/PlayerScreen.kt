package com.joaohouto.clusterlauncher.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.joaohouto.clusterlauncher.R
import com.joaohouto.clusterlauncher.ui.components.MetallicButton
import com.joaohouto.clusterlauncher.ui.components.MetallicButtonStyle
import com.joaohouto.clusterlauncher.ui.components.ProgressBarSlider
import com.joaohouto.clusterlauncher.ui.components.SquareAlbumArt
import com.joaohouto.clusterlauncher.ui.theme.DeepMetallicBackground
import com.joaohouto.clusterlauncher.ui.theme.LocalClusterAccent
import com.joaohouto.clusterlauncher.ui.theme.LocalClusterColors
import com.joaohouto.clusterlauncher.ui.theme.TextPrimary
import com.joaohouto.clusterlauncher.ui.theme.TextSecondary
import java.io.File

@Composable
fun IntegratedPlayerScreen(
    homeViewModel: HomeViewModel,
    playerViewModel: PlayerViewModel,
    showLyrics: Boolean,
    onCloseToCockpit: () -> Unit,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showFolderBrowser by rememberSaveable { mutableStateOf(false) }

    BackHandler {
        if (showFolderBrowser) {
            showFolderBrowser = false
        } else {
            onCloseToCockpit()
        }
    }

    AnimatedContent(
        targetState = showFolderBrowser,
        transitionSpec = {
            fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(160))
        },
        modifier = modifier.fillMaxSize(),
        label = "IntegratedPlayerNav"
    ) { inFolders ->
        if (inFolders) {
            FolderBrowserScreen(
                viewModel = homeViewModel,
                onNavigateToPlayer = { showFolderBrowser = false }
            )
        } else {
            PlayerScreen(
                viewModel = playerViewModel,
                showLyrics = showLyrics,
                onNavigateToFolders = { showFolderBrowser = true }
            )
        }
    }
}

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    showLyrics: Boolean = true,
    onNavigateToFolders: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.playbackState.collectAsState()
    val currentTrack = state.currentTrack
    val accent = LocalClusterAccent.current
    val colors = LocalClusterColors.current

    var dragAmountX by remember { mutableFloatStateOf(0f) }

    val currentFolderName = remember(currentTrack?.folderPath) {
        val path = currentTrack?.folderPath.orEmpty()
        if (path.isNotEmpty()) File(path).name else ""
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragAmountX = 0f },
                    onDragEnd = {
                        val threshold = 120f
                        if (dragAmountX < -threshold) {
                            viewModel.next()
                        } else if (dragAmountX > threshold) {
                            viewModel.previous()
                        }
                        dragAmountX = 0f
                    },
                    onDragCancel = { dragAmountX = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        dragAmountX += dragAmount
                    }
                )
            }
    ) {
        // Subtle ambient radial glow behind the album art
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accent.glow.copy(alpha = 0.22f),
                            Color.Transparent
                        ),
                        radius = 750f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar: Folders (aligned right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val folderButtonLabel = if (currentFolderName.isNotBlank()) {
                    currentFolderName
                } else {
                    stringResource(R.string.btn_playlists_folders)
                }

                MetallicButton(
                    onClick = onNavigateToFolders,
                    icon = Icons.Rounded.Folder,
                    text = folderButtonLabel,
                    minSize = 76.dp,
                    iconSize = 34.dp,
                    textSize = 18.sp,
                    contentPadding = PaddingValues(horizontal = 26.dp, vertical = 14.dp),
                    contentDescription = stringResource(R.string.desc_back)
                )
            }

            // Center Area: Album Art + Lyrics, Track Metadata & Progress Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                        .heightIn(max = 220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    SquareAlbumArt(
                        uriOrPath = currentTrack?.path?.ifEmpty { currentTrack.uri },
                        modifier = Modifier.fillMaxSize(),
                        onDoubleTap = { viewModel.playPause() }
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) {
                    // Clean, unbadged slot for synchronized .LRC lyrics above the title
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        val lyricsLine = state.currentLyricsLine
                        val accentColor = accent.primary
                        AnimatedContent(
                            targetState = if (showLyrics) lyricsLine else null,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(150))
                            },
                            label = "LyricsTransition"
                        ) { activeText ->
                            if (!activeText.isNullOrBlank()) {
                                Text(
                                    text = activeText,
                                    color = accentColor,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Track Title (tapping when no track is playing opens Folder Browser)
                    Text(
                        text = currentTrack?.title ?: stringResource(R.string.no_track_playing),
                        color = TextPrimary,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (currentTrack == null) {
                            Modifier.clickable { onNavigateToFolders() }
                        } else {
                            Modifier
                        }
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val unknownAlbumStr = stringResource(R.string.unknown_album)
                    val artistAlbumText = buildString {
                        append(currentTrack?.artist ?: stringResource(R.string.select_folder_to_play))
                        if (!currentTrack?.album.isNullOrBlank() &&
                            currentTrack?.album != unknownAlbumStr &&
                            currentTrack?.album != "Álbum Desconhecido" &&
                            currentTrack?.album != "Unknown Album"
                        ) {
                            append(" • ")
                            append(currentTrack?.album)
                        }
                    }
                    Text(
                        text = artistAlbumText,
                        color = if (currentTrack == null) accent.primary else colors.textSecondary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (currentTrack == null) {
                            Modifier.clickable { onNavigateToFolders() }
                        } else {
                            Modifier
                        }
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    TrackProgressSection(
                        viewModel = viewModel,
                        durationMs = state.durationMs,
                        isPlaying = state.isPlaying,
                        trackKey = currentTrack?.uri
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom 7-button Transport Controls Dock
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(116.dp)
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetallicButton(
                    onClick = { viewModel.volumeDown() },
                    icon = Icons.AutoMirrored.Rounded.VolumeDown,
                    minSize = 64.dp,
                    iconSize = 36.dp,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentDescription = stringResource(R.string.desc_volume_down)
                )

                MetallicButton(
                    onClick = { viewModel.toggleShuffle() },
                    icon = Icons.Rounded.Shuffle,
                    isActive = state.isShuffleEnabled,
                    minSize = 64.dp,
                    iconSize = 36.dp,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentDescription = stringResource(R.string.desc_shuffle)
                )

                MetallicButton(
                    onClick = { viewModel.previous() },
                    icon = Icons.Rounded.SkipPrevious,
                    minSize = 64.dp,
                    iconSize = 40.dp,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentDescription = stringResource(R.string.desc_previous)
                )

                MetallicButton(
                    onClick = { viewModel.playPause() },
                    style = MetallicButtonStyle.Accent,
                    icon = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    minSize = 78.dp,
                    iconSize = 46.dp,
                    modifier = Modifier
                        .weight(1.35f)
                        .fillMaxHeight(),
                    contentDescription = if (state.isPlaying) stringResource(R.string.desc_pause) else stringResource(R.string.desc_play)
                )

                MetallicButton(
                    onClick = { viewModel.next() },
                    icon = Icons.Rounded.SkipNext,
                    minSize = 64.dp,
                    iconSize = 40.dp,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentDescription = stringResource(R.string.desc_next)
                )

                val isRepeatActive = state.repeatMode != Player.REPEAT_MODE_OFF
                val repeatIcon = if (state.repeatMode == Player.REPEAT_MODE_ONE) {
                    Icons.Rounded.RepeatOne
                } else {
                    Icons.Rounded.Repeat
                }
                MetallicButton(
                    onClick = { viewModel.cycleRepeatMode() },
                    icon = repeatIcon,
                    isActive = isRepeatActive,
                    minSize = 64.dp,
                    iconSize = 36.dp,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentDescription = stringResource(R.string.desc_repeat)
                )

                MetallicButton(
                    onClick = { viewModel.volumeUp() },
                    icon = Icons.AutoMirrored.Rounded.VolumeUp,
                    minSize = 64.dp,
                    iconSize = 36.dp,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentDescription = stringResource(R.string.desc_volume_up)
                )
            }
        }
    }
}

@Composable
private fun TrackProgressSection(
    viewModel: PlayerViewModel,
    durationMs: Long,
    isPlaying: Boolean,
    trackKey: String? = null,
    modifier: Modifier = Modifier
) {
    val currentPositionMs by viewModel.currentPosition.collectAsState()
    ProgressBarSlider(
        currentPositionMs = currentPositionMs,
        durationMs = durationMs,
        isPlaying = isPlaying,
        trackKey = trackKey,
        onSeek = { targetMs -> viewModel.seekTo(targetMs) },
        modifier = modifier
    )
}
