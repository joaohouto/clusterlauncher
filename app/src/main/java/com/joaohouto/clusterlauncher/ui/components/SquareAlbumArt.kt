package com.joaohouto.clusterlauncher.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.joaohouto.clusterlauncher.R
import com.joaohouto.clusterlauncher.ui.theme.LocalClusterAccent
import com.joaohouto.clusterlauncher.ui.theme.SurfaceCard
import com.joaohouto.clusterlauncher.ui.theme.SurfaceCardBorder
import com.joaohouto.clusterlauncher.ui.theme.TextPrimary

private val ArtShape = RoundedCornerShape(14.dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SquareAlbumArt(
    uriOrPath: String?,
    modifier: Modifier = Modifier,
    onDoubleTap: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val accent = LocalClusterAccent.current.primary
    var artBitmap by remember(uriOrPath) {
        mutableStateOf(
            if (!uriOrPath.isNullOrEmpty()) AudioArtExtractor.getCachedBitmap(uriOrPath) else null
        )
    }

    LaunchedEffect(uriOrPath) {
        if (!uriOrPath.isNullOrEmpty()) {
            if (artBitmap == null) {
                artBitmap = AudioArtExtractor.preloadArtBitmap(context, uriOrPath)
            }
        } else {
            artBitmap = null
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(ArtShape)
            .background(SurfaceCard)
            .border(1.dp, SurfaceCardBorder, ArtShape)
            .then(
                if (onDoubleTap != null) {
                    Modifier.combinedClickable(
                        onClick = {},
                        onDoubleClick = onDoubleTap
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        val readyBitmap = artBitmap
        if (readyBitmap != null && !readyBitmap.isRecycled) {
            Image(
                bitmap = readyBitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.desc_album_art),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Vinyl metallic fallback matching UniversalMediaCard
            Box(
                modifier = Modifier
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
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .border(1.dp, Color(0x18FFFFFF), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(82.dp)
                        .border(1.dp, Color(0x20FFFFFF), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1C1E24))
                        .border(1.5.dp, accent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = stringResource(R.string.desc_no_art),
                        tint = TextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}
