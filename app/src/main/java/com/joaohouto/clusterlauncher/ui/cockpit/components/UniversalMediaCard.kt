package com.joaohouto.clusterlauncher.ui.cockpit.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.joaohouto.clusterlauncher.R
import com.joaohouto.clusterlauncher.media.MediaManager
import com.joaohouto.clusterlauncher.ui.components.MetallicButton
import com.joaohouto.clusterlauncher.ui.components.MetallicButtonStyle
import com.joaohouto.clusterlauncher.ui.theme.LocalClusterAccent
import com.joaohouto.clusterlauncher.ui.theme.SurfaceCard
import com.joaohouto.clusterlauncher.ui.theme.SurfaceCardBorder
import com.joaohouto.clusterlauncher.ui.theme.TextDisabled
import com.joaohouto.clusterlauncher.ui.theme.TextPrimary
import com.joaohouto.clusterlauncher.ui.theme.TextSecondary

private val CardShape = RoundedCornerShape(16.dp)
private val CoverShape = RoundedCornerShape(12.dp)

@Composable
fun UniversalMediaCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mediaState by MediaManager.mediaState.collectAsState()

    val backgroundBrush = remember {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF181A1F), Color(0xFF121316))
        )
    }

    Surface(
        modifier = modifier
            .clip(CardShape)
            .border(BorderStroke(1.dp, SurfaceCardBorder), CardShape),
        shape = CardShape,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .background(backgroundBrush)
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            if (!mediaState.isPermissionGranted) {
                // Permission Fallback Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                        .padding(12.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = LocalClusterAccent.current.primary,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.media_permission_title),
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.media_permission_desc),
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val albumArtImage = remember(mediaState.albumArtBitmap) {
                        mediaState.albumArtBitmap?.asImageBitmap()
                    }

                    // Album Art (136dp x 136dp) with resilient fallback
                    Box(
                        modifier = Modifier
                            .size(136.dp)
                            .clip(CoverShape)
                            .background(SurfaceCard)
                            .border(1.dp, SurfaceCardBorder, CoverShape)
                            .clickable { MediaManager.openPlayer(context) },
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            albumArtImage != null -> {
                                Image(
                                    bitmap = albumArtImage,
                                    contentDescription = mediaState.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            mediaState.albumArtUri != null -> {
                                SubcomposeAsyncImage(
                                    model = mediaState.albumArtUri,
                                    contentDescription = mediaState.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                    loading = { MusicCoverFallback() },
                                    error = { MusicCoverFallback() }
                                )
                            }
                            else -> {
                                MusicCoverFallback()
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(18.dp))

                    // Track title and artist (fills middle space)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { MediaManager.openPlayer(context) },
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (mediaState.title.isNotBlank()) mediaState.title else stringResource(R.string.no_track_playing),
                            color = TextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (mediaState.artist.isNotEmpty()) mediaState.artist else "",
                            color = TextSecondary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Transport Buttons: placed side-by-side with title/artist
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MetallicButton(
                            onClick = { MediaManager.skipToPrevious() },
                            icon = Icons.Rounded.SkipPrevious,
                            modifier = Modifier.size(68.dp),
                            iconSize = 36.dp,
                            contentDescription = "Anterior"
                        )

                        MetallicButton(
                            onClick = { MediaManager.playPause() },
                            icon = if (mediaState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            style = if (mediaState.isPlaying) MetallicButtonStyle.Accent else MetallicButtonStyle.Standard,
                            modifier = Modifier.size(80.dp),
                            iconSize = 44.dp,
                            contentDescription = "Play/Pause"
                        )

                        MetallicButton(
                            onClick = { MediaManager.skipToNext() },
                            icon = Icons.Rounded.SkipNext,
                            modifier = Modifier.size(68.dp),
                            iconSize = 36.dp,
                            contentDescription = "Próximo"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicCoverFallback(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF282B33),
                        Color(0xFF16181C),
                        Color(0xFF0C0D0F)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Vinyl grooves effect
        Box(
            modifier = Modifier
                .size(96.dp)
                .border(1.dp, Color(0x18FFFFFF), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(74.dp)
                .border(1.dp, Color(0x20FFFFFF), CircleShape)
        )
        // Center label with accent
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color(0xFF1C1E24))
                .border(1.5.dp, LocalClusterAccent.current.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

/**
 * Card vertical de mídia em tela cheia (estilo Android Auto Coolwalk),
 * com capa do álbum preenchendo todo o fundo, gradiente de contraste e controles na base.
 */
@Composable
fun FullBleedMediaCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mediaState by MediaManager.mediaState.collectAsState()
    val accent = LocalClusterAccent.current.primary

    Surface(
        modifier = modifier
            .clip(CardShape)
            .border(BorderStroke(1.dp, SurfaceCardBorder), CardShape),
        shape = CardShape,
        color = Color(0xFF101216)
    ) {
        if (!mediaState.isPermissionGranted) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.media_permission_title),
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.media_permission_desc),
                        color = TextSecondary,
                        fontSize = 15.sp
                    )
                }
            }
        } else {
            val albumArtImage = remember(mediaState.albumArtBitmap) {
                mediaState.albumArtBitmap?.asImageBitmap()
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { MediaManager.openPlayer(context) }
            ) {
                // Camada 1: Capa do álbum preenchendo 100% do card
                when {
                    albumArtImage != null -> {
                        Image(
                            bitmap = albumArtImage,
                            contentDescription = mediaState.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    mediaState.albumArtUri != null -> {
                        SubcomposeAsyncImage(
                            model = mediaState.albumArtUri,
                            contentDescription = mediaState.title,
                            contentScale = ContentScale.Crop,
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color(0x28000000),
                                    0.42f to Color(0x660A0C10),
                                    0.72f to Color(0xCC0D0F14),
                                    1.0f to Color(0xF50A0C10)
                                )
                            )
                        )
                )

                // Camada 3: Informações da música + barra de progresso + controles na parte inferior
                val hasTrack = mediaState.title.isNotBlank()
                val displayTitle = if (hasTrack) mediaState.title else stringResource(R.string.no_track_playing)
                val progress = if (hasTrack && mediaState.durationMs > 0L) {
                    (mediaState.positionMs.toFloat() / mediaState.durationMs.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }

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

                    if (hasTrack && mediaState.artist.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = mediaState.artist,
                            color = Color(0xFFD0D6E2),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Barra de progresso real da faixa
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
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(accent, Color.White)
                                        )
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Controles de reprodução ampliados na base
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Botão Anterior
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
                                contentDescription = "Anterior",
                                tint = Color.White,
                                modifier = Modifier.size(44.dp)
                            )
                        }

                        // Botão Play/Pause em formato Pílula Destacada (estilo Android Auto)
                        Box(
                            modifier = Modifier
                                .width(112.dp)
                                .height(78.dp)
                                .clip(RoundedCornerShape(26.dp))
                                .background(
                                    if (mediaState.isPlaying) Color.White else accent
                                )
                                .clickable { MediaManager.playPause() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (mediaState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = if (mediaState.isPlaying) Color(0xFF0E1015) else Color.White,
                                modifier = Modifier.size(50.dp)
                            )
                        }

                        // Botão Próximo
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
                                contentDescription = "Próximo",
                                tint = Color.White,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }
                }
            }
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
