package com.joaohouto.clusterlauncher.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.joaohouto.clusterlauncher.R
import com.joaohouto.clusterlauncher.data.model.Folder
import com.joaohouto.clusterlauncher.data.model.Track
import com.joaohouto.clusterlauncher.ui.components.MetallicButton
import com.joaohouto.clusterlauncher.ui.components.MetallicButtonStyle
import com.joaohouto.clusterlauncher.ui.components.SquareAlbumArt
import com.joaohouto.clusterlauncher.ui.theme.DeepMetallicBackground
import com.joaohouto.clusterlauncher.ui.theme.DeepMetallicBackgroundSecondary
import com.joaohouto.clusterlauncher.ui.theme.LocalClusterAccent
import com.joaohouto.clusterlauncher.ui.theme.LocalClusterColors
import com.joaohouto.clusterlauncher.ui.theme.MetallicIntermediate
import com.joaohouto.clusterlauncher.ui.theme.SurfaceCard
import com.joaohouto.clusterlauncher.ui.theme.SurfaceCardBorder
import com.joaohouto.clusterlauncher.ui.theme.TextPrimary
import com.joaohouto.clusterlauncher.ui.theme.TextSecondary

@Composable
fun FolderBrowserScreen(
    viewModel: HomeViewModel,
    onNavigateToPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val folders by viewModel.folders.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val selectedFolderTracks by viewModel.selectedFolderTracks.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val bottomBarState by viewModel.bottomBarState.collectAsState()
    val accent = LocalClusterAccent.current.primary
    val colors = LocalClusterColors.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Main Split-View Area
        if (folders.isEmpty() && !isScanning) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.empty_folders_title),
                        color = TextSecondary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.empty_folders_subtitle),
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    MetallicButton(
                        onClick = { viewModel.rescan() },
                        icon = Icons.Rounded.Refresh,
                        text = stringResource(R.string.btn_refresh),
                        minSize = 58.dp,
                        iconSize = 24.dp,
                        textSize = 15.sp,
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                        contentDescription = stringResource(R.string.desc_refresh)
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left Column: Refresh Button + Folders List (42%)
                Column(
                    modifier = Modifier
                        .weight(0.42f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetallicButton(
                        onClick = { viewModel.rescan() },
                        icon = Icons.Rounded.Refresh,
                        text = if (isScanning) stringResource(R.string.status_scanning) else stringResource(R.string.btn_refresh),
                        isLoading = isScanning,
                        minSize = 56.dp,
                        iconSize = 24.dp,
                        textSize = 15.sp,
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                        contentDescription = stringResource(R.string.desc_refresh),
                        modifier = Modifier.fillMaxWidth()
                    )

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        items(folders, key = { it.path }, contentType = { "folder_item" }) { folder ->
                            val isSelected = selectedFolder?.path == folder.path
                            val isPlayingFolder = bottomBarState.currentTrack?.folderPath == folder.path
                            SplitFolderItem(
                                folder = folder,
                                isSelected = isSelected,
                                isPlayingFolder = isPlayingFolder,
                                onSelect = { viewModel.selectFolder(folder) },
                                onPlayFolderNow = {
                                    viewModel.playFolder(folder.path, shuffle = false)
                                    onNavigateToPlayer()
                                }
                            )
                        }
                    }
                }

                // Right Column: Selected Folder Tracks & Actions (58%)
                Surface(
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxHeight()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surfaceCard,
                    border = BorderStroke(1.dp, colors.surfaceCardBorder)
                ) {
                    val activeFolder = selectedFolder
                    if (activeFolder == null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.select_folder_to_play),
                                color = TextSecondary,
                                fontSize = 16.sp
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(18.dp)
                        ) {
                            // Folder Detail Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (activeFolder.isUsbOrExternal) Icons.Rounded.Usb else Icons.Rounded.Folder,
                                            contentDescription = null,
                                            tint = accent,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Text(
                                            text = activeFolder.name,
                                            color = TextPrimary,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.tracks_count, selectedFolderTracks.size.takeIf { it > 0 } ?: activeFolder.trackCount),
                                        color = TextSecondary,
                                        fontSize = 14.sp
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    MetallicButton(
                                        onClick = {
                                            viewModel.playFolder(activeFolder.path, shuffle = true)
                                            onNavigateToPlayer()
                                        },
                                        icon = Icons.Rounded.Shuffle,
                                        text = stringResource(R.string.btn_shuffle_folder),
                                        minSize = 52.dp,
                                        iconSize = 22.dp,
                                        textSize = 14.sp,
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                        contentDescription = stringResource(R.string.btn_shuffle_folder)
                                    )

                                    MetallicButton(
                                        onClick = {
                                            viewModel.playFolder(activeFolder.path, shuffle = false)
                                            onNavigateToPlayer()
                                        },
                                        style = MetallicButtonStyle.Accent,
                                        icon = Icons.Rounded.PlayArrow,
                                        text = stringResource(R.string.btn_play_all),
                                        minSize = 52.dp,
                                        iconSize = 24.dp,
                                        textSize = 14.sp,
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                        contentDescription = stringResource(R.string.btn_play_all)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = SurfaceCardBorder, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(10.dp))

                            // Tracks List
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(
                                    count = selectedFolderTracks.size,
                                    key = { idx ->
                                        val t = selectedFolderTracks[idx]
                                        if (t.id != 0L) t.id else t.uri
                                    },
                                    contentType = { "folder_track_row" }
                                ) { index ->
                                    val track = selectedFolderTracks[index]
                                    val isCurrentTrack = bottomBarState.currentTrack?.uri == track.uri
                                    FolderTrackRow(
                                        index = index,
                                        track = track,
                                        isCurrent = isCurrentTrack,
                                        isPlaying = isCurrentTrack && bottomBarState.isPlaying,
                                        onClick = {
                                            viewModel.playTrackInSelectedFolder(index)
                                            onNavigateToPlayer()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Mini-Player Bar when a track is loaded
        val currentTrack = bottomBarState.currentTrack
        if (currentTrack != null) {
            FullControlsBottomPlaybackBar(
                track = currentTrack,
                isPlaying = bottomBarState.isPlaying,
                isShuffleEnabled = bottomBarState.isShuffleEnabled,
                repeatMode = bottomBarState.repeatMode,
                onPlayPause = { viewModel.playPause() },
                onNext = { viewModel.next() },
                onPrevious = { viewModel.previous() },
                onToggleShuffle = { viewModel.toggleShuffle() },
                onCycleRepeatMode = { viewModel.cycleRepeatMode() },
                onBarClick = onNavigateToPlayer
            )
        }
    }
}

@Composable
private fun SplitFolderItem(
    folder: Folder,
    isSelected: Boolean,
    isPlayingFolder: Boolean,
    onSelect: () -> Unit,
    onPlayFolderNow: () -> Unit
) {
    val accent = LocalClusterAccent.current.primary
    val colors = LocalClusterColors.current
    val shape = RoundedCornerShape(14.dp)
    val bgColor = if (isSelected) colors.metallicIntermediate else colors.surfaceCard
    val borderColor = when {
        isSelected -> accent
        isPlayingFolder -> accent.copy(alpha = 0.55f)
        else -> colors.surfaceCardBorder
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bgColor)
            .border(if (isSelected) 1.5.dp else 1.dp, borderColor, shape)
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) accent.copy(alpha = 0.2f) else DeepMetallicBackgroundSecondary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (folder.isUsbOrExternal) Icons.Rounded.Usb else Icons.Rounded.Folder,
                contentDescription = null,
                tint = if (isSelected || isPlayingFolder) accent else TextSecondary,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isPlayingFolder) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = folder.name,
                    color = if (isSelected || isPlayingFolder) TextPrimary else TextPrimary.copy(alpha = 0.92f),
                    fontSize = 18.sp,
                    fontWeight = if (isSelected || isPlayingFolder) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = stringResource(R.string.tracks_count, folder.trackCount),
                color = TextSecondary,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun FolderTrackRow(
    index: Int,
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val accent = LocalClusterAccent.current.primary
    val colors = LocalClusterColors.current
    val shape = RoundedCornerShape(10.dp)
    val formattedIndex = remember(index) {
        val num = index + 1
        if (num < 10) "0$num" else num.toString()
    }
    val formattedDuration = remember(track.durationMs) {
        val totalSeconds = (track.durationMs / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val minStr = if (minutes < 10) "0$minutes" else minutes.toString()
        val secStr = if (seconds < 10) "0$seconds" else seconds.toString()
        "$minStr:$secStr"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isCurrent) accent.copy(alpha = 0.14f) else colors.backgroundSecondary)
            .border(
                width = 1.dp,
                color = if (isCurrent) accent else colors.surfaceCardBorder,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isPlaying) {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
        } else {
            Text(
                text = formattedIndex,
                color = if (isCurrent) accent else TextSecondary,
                fontSize = 15.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                modifier = Modifier.width(32.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = if (isCurrent) accent else TextPrimary,
                fontSize = 17.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                color = TextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = formattedDuration,
            color = if (isCurrent) accent else TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun FullControlsBottomPlaybackBar(
    track: Track,
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onBarClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalClusterColors.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(104.dp),
        color = colors.surfaceCard,
        border = BorderStroke(1.dp, colors.surfaceCardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onBarClick),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SquareAlbumArt(
                    uriOrPath = track.path.ifEmpty { track.uri },
                    modifier = Modifier.size(80.dp)
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = track.title,
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = track.artist,
                        color = TextSecondary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetallicButton(
                    onClick = onToggleShuffle,
                    icon = Icons.Rounded.Shuffle,
                    isActive = isShuffleEnabled,
                    minSize = 58.dp,
                    iconSize = 28.dp,
                    contentDescription = stringResource(R.string.desc_shuffle)
                )

                MetallicButton(
                    onClick = onPrevious,
                    icon = Icons.Rounded.SkipPrevious,
                    minSize = 58.dp,
                    iconSize = 28.dp,
                    contentDescription = stringResource(R.string.desc_previous)
                )

                MetallicButton(
                    onClick = onPlayPause,
                    style = MetallicButtonStyle.Accent,
                    icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    minSize = 62.dp,
                    iconSize = 30.dp,
                    contentDescription = if (isPlaying) stringResource(R.string.desc_pause) else stringResource(R.string.desc_play)
                )

                MetallicButton(
                    onClick = onNext,
                    icon = Icons.Rounded.SkipNext,
                    minSize = 58.dp,
                    iconSize = 28.dp,
                    contentDescription = stringResource(R.string.desc_next)
                )

                val isRepeatActive = repeatMode != Player.REPEAT_MODE_OFF
                val repeatIcon = if (repeatMode == Player.REPEAT_MODE_ONE) {
                    Icons.Rounded.RepeatOne
                } else {
                    Icons.Rounded.Repeat
                }
                MetallicButton(
                    onClick = onCycleRepeatMode,
                    icon = repeatIcon,
                    isActive = isRepeatActive,
                    minSize = 58.dp,
                    iconSize = 28.dp,
                    contentDescription = stringResource(R.string.desc_repeat)
                )
            }
        }
    }
}
