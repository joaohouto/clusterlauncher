package com.joaohouto.clusterlauncher.ui.cockpit.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.joaohouto.clusterlauncher.R
import com.joaohouto.clusterlauncher.media.MediaManager
import com.joaohouto.clusterlauncher.ui.theme.LocalClusterAccent
import com.joaohouto.clusterlauncher.ui.theme.SurfaceCardBorder
import com.joaohouto.clusterlauncher.ui.theme.TextPrimary
import com.joaohouto.clusterlauncher.ui.theme.TextSecondary

private val CardShape = RoundedCornerShape(16.dp)

/**
 * Card vertical de mídia em tela cheia (estilo Android Auto Coolwalk),
 * com capa do álbum preenchendo todo o fundo, gradiente de contraste,
 * botão "X" para fechar/encerrar no canto superior direito e controles na base.
 */
@Composable
fun FullBleedMediaCard(
    onOpenPlayer: () -> Unit = {},
    onCloseCard: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val mediaState by MediaManager.mediaState.collectAsState()
    val accentTheme = LocalClusterAccent.current
    val accent = accentTheme.primary

    Surface(
        modifier = modifier
            .clip(CardShape)
            .border(BorderStroke(1.dp, SurfaceCardBorder), CardShape),
        shape = CardShape,
        color = Color(0xFF101216)
    ) {
        val albumArtImage = remember(mediaState.albumArtBitmap) {
            mediaState.albumArtBitmap?.asImageBitmap()
        }

        var dragAmountX by remember { mutableFloatStateOf(0f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onOpenPlayer() },
                        onDoubleTap = { MediaManager.playPause() }
                    )
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragAmountX = 0f },
                        onDragEnd = {
                            val threshold = 90f
                            if (dragAmountX < -threshold) {
                                MediaManager.skipToNext()
                            } else if (dragAmountX > threshold) {
                                MediaManager.skipToPrevious()
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
            // Camada 1: Capa do álbum preenchendo 100% do card em alta qualidade
            when {
                albumArtImage != null -> {
                    Image(
                        bitmap = albumArtImage,
                        contentDescription = mediaState.title,
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.High,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                mediaState.albumArtUri != null -> {
                    SubcomposeAsyncImage(
                        model = mediaState.albumArtUri,
                        contentDescription = mediaState.title,
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.High,
                        modifier = Modifier.fillMaxSize(),
                        loading = { FullBleedCoverFallback() },
                        error = { FullBleedCoverFallback() }
                    )
                }
                else -> {
                    FullBleedCoverFallback()
                }
            }

            // Camada 2: Gradiente vertical estilo Android Auto para legibilidade perfeita
            val overlayGradient = remember {
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color(0x28000000),
                        0.42f to Color(0x660A0C10),
                        0.72f to Color(0xCC0D0F14),
                        1.0f to Color(0xF50A0C10)
                    )
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(overlayGradient)
            )

            // Botão "X" no canto superior direito para fechar/ocultar o card e encerrar reprodução
            if (onCloseCard != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0x880D0F14))
                        .border(1.dp, Color(0x40FFFFFF), CircleShape)
                        .clickable(onClick = onCloseCard),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.btn_close),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Camada 3: Informações da música + barra de progresso sólida na cor accent + controles na parte inferior
            val hasTrack = mediaState.title.isNotBlank()
            val displayTitle = if (hasTrack) mediaState.title else stringResource(R.string.no_track_playing)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    text = displayTitle,
                    color = if (hasTrack) Color.White else TextSecondary,
                    fontSize = if (hasTrack) 24.sp else 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (hasTrack && mediaState.artist.isNotEmpty()) mediaState.artist else stringResource(R.string.select_folder_to_play),
                    color = Color(0xFFD0D6E2),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Barra de progresso 100% na cor accent (isolada para evitar recomposição do card inteiro)
                MediaCardProgressBar(
                    hasTrack = hasTrack,
                    durationMs = mediaState.durationMs,
                    accent = accent
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Controles de reprodução ampliados na base
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(78.dp)
                            .clip(CircleShape)
                            .background(Color(0x28FFFFFF))
                            .border(1.dp, Color(0x38FFFFFF), CircleShape)
                            .clickable { MediaManager.skipToPrevious() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = stringResource(R.string.desc_previous),
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                    }

                    val playBtnBg = if (mediaState.isPlaying) Color.White else accent
                    val playBtnIconTint = if (mediaState.isPlaying) Color(0xFF0E1015) else accentTheme.onPrimary

                    Box(
                        modifier = Modifier
                            .width(112.dp)
                            .height(78.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(playBtnBg)
                            .clickable { MediaManager.playPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (mediaState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (mediaState.isPlaying) stringResource(R.string.desc_pause) else stringResource(R.string.desc_play),
                            tint = playBtnIconTint,
                            modifier = Modifier.size(50.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(78.dp)
                            .clip(CircleShape)
                            .background(Color(0x28FFFFFF))
                            .border(1.dp, Color(0x38FFFFFF), CircleShape)
                            .clickable { MediaManager.skipToNext() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = stringResource(R.string.desc_next),
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaCardProgressBar(
    hasTrack: Boolean,
    durationMs: Long,
    accent: Color
) {
    val positionMs by MediaManager.mediaPositionMs.collectAsState()
    val progress = if (hasTrack && durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color(0x38FFFFFF))
    ) {
        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(accent)
            )
        }
    }
}

@Composable
private fun FullBleedCoverFallback(modifier: Modifier = Modifier) {
    val accent = LocalClusterAccent.current.primary
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.22f),
                        Color(0xFF1A1D24),
                        Color(0xFF0D0F13)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = 120.dp)
                .size(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .border(1.dp, Color(0x18FFFFFF), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(134.dp)
                    .border(1.dp, Color(0x22FFFFFF), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF181B22))
                    .border(2.dp, accent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(38.dp)
                )
            }
        }
    }
}
