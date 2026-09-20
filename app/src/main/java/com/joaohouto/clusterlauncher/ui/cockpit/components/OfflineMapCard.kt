package com.joaohouto.clusterlauncher.ui.cockpit.components

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joaohouto.clusterlauncher.R
import com.joaohouto.clusterlauncher.data.location.GpsLocationData
import com.joaohouto.clusterlauncher.data.location.GpsLocationRepository
import com.joaohouto.clusterlauncher.data.map.MBTilesRepository
import com.joaohouto.clusterlauncher.data.map.RenderableTile
import com.joaohouto.clusterlauncher.ui.theme.NeedleRed
import com.joaohouto.clusterlauncher.ui.theme.NeedleRedGlow
import com.joaohouto.clusterlauncher.ui.theme.SurfaceCardBorder
import com.joaohouto.clusterlauncher.ui.theme.TextDisabled
import com.joaohouto.clusterlauncher.ui.theme.TextPrimary
import com.joaohouto.clusterlauncher.ui.theme.TextSecondary

private val CardShape = RoundedCornerShape(16.dp)

// Matriz de inversão para criar tema escuro automotivo de alto contraste em blocos de mapa
private val DarkCockpitColorMatrix = ColorMatrix(
    floatArrayOf(
        -0.75f,  0.00f,  0.00f, 0.00f, 220f,
         0.00f, -0.75f,  0.00f, 0.00f, 220f,
         0.00f,  0.00f, -0.70f, 0.00f, 230f,
         0.00f,  0.00f,  0.00f, 1.00f,   0f
    )
)

/**
 * Tile horizontal de mapa offline para o Cockpit.
 * Exibe a posição do carro e orientação sobre blocos MBTiles locais.
 * Suporta modo escuro automotivo invertido e toca para abrir o app de navegação do sistema.
 */
@Composable
fun OfflineMapCard(
    modifier: Modifier = Modifier,
    heightDp: Int? = null,
    isDarkMode: Boolean = false
) {
    val context = LocalContext.current
    val hasPermission by GpsLocationRepository.hasPermission.collectAsState()
    val locationState by GpsLocationRepository.locationState.collectAsState()
    val mapState by MBTilesRepository.mapState.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            GpsLocationRepository.startListening(context)
        }
    }

    DisposableEffect(Unit) {
        GpsLocationRepository.checkPermission(context)
        if (GpsLocationRepository.hasPermission.value) {
            GpsLocationRepository.startListening(context)
        }
        onDispose {
            GpsLocationRepository.stopListening()
        }
    }

    LaunchedEffect(Unit) {
        MBTilesRepository.initialize(context)
    }

    val backgroundBrush = remember {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF181A1F), Color(0xFF101215))
        )
    }

    val cardModifier = if (heightDp != null && heightDp > 0) {
        modifier.height(heightDp.dp)
    } else {
        modifier.defaultMinSize(minHeight = 220.dp)
    }

    Surface(
        modifier = cardModifier
            .clip(CardShape)
            .border(BorderStroke(1.dp, SurfaceCardBorder), CardShape),
        shape = CardShape,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .background(backgroundBrush)
                .fillMaxSize()
        ) {
            if (!hasPermission) {
                LocationPermissionBanner(
                    onRequestPermission = {
                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            try {
                                val geoUri = Uri.parse("geo:${locationState.latitude},${locationState.longitude}?z=16")
                                val intent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                // Fallback silencioso
                            }
                        }
                ) {
                    MapCanvasView(
                        location = locationState,
                        isMapLoaded = mapState.isMapLoaded,
                        defaultZoom = mapState.defaultZoom,
                        isDarkMode = isDarkMode
                    )

                    // Overlay HUD Superior
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        GpsStatusBadge(location = locationState)
                        SpeedometerBadge(speedKmh = locationState.speedKmh)
                    }

                    // Overlay Inferior: Nome do arquivo ativo ou dica
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (mapState.isMapLoaded) {
                                mapState.mapDisplayName ?: mapState.mapFileName ?: stringResource(R.string.slot_gps)
                            } else {
                                stringResource(R.string.no_offline_map)
                            },
                            color = if (mapState.isMapLoaded) TextSecondary else NeedleRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )

                        if (!mapState.isMapLoaded) {
                            Text(
                                text = stringResource(R.string.offline_map_instruction),
                                color = TextDisabled,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Canvas que renderiza os blocos do mapa MBTiles com suporte a tema noturno e bússola/radar HUD.
 */
@Composable
private fun MapCanvasView(
    location: GpsLocationData,
    isMapLoaded: Boolean,
    defaultZoom: Int,
    isDarkMode: Boolean
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var visibleTiles by remember { mutableStateOf<List<RenderableTile>>(emptyList()) }

    val tileColorFilter = remember(isDarkMode) {
        if (isDarkMode) ColorFilter.colorMatrix(DarkCockpitColorMatrix) else null
    }

    LaunchedEffect(location.latitude, location.longitude, isMapLoaded, canvasSize) {
        if (isMapLoaded && canvasSize.width > 0 && canvasSize.height > 0) {
            visibleTiles = MBTilesRepository.getVisibleTiles(
                lat = location.latitude,
                lon = location.longitude,
                zoom = defaultZoom,
                viewWidth = canvasSize.width.toFloat(),
                viewHeight = canvasSize.height.toFloat()
            )
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)

        if (isMapLoaded && visibleTiles.isNotEmpty()) {
            for (tile in visibleTiles) {
                drawImage(
                    image = tile.bitmap.asImageBitmap(),
                    topLeft = Offset(tile.screenX, tile.screenY),
                    colorFilter = tileColorFilter
                )
            }
            // Sombra sutil nas bordas para integração com o cockpit
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color(0x66000000)),
                    center = center,
                    radius = size.maxDimension / 1.6f
                )
            )
        } else {
            // Fallback: Radar automotivo estéreo
            val maxR = size.minDimension / 1.8f
            drawCircle(
                color = Color(0x15FFFFFF),
                radius = maxR,
                center = center,
                style = Stroke(width = 1f)
            )
            drawCircle(
                color = Color(0x0FFFFFFF),
                radius = maxR * 0.6f,
                center = center,
                style = Stroke(width = 1f)
            )
            drawCircle(
                color = NeedleRedGlow,
                radius = maxR * 0.25f,
                center = center
            )

            // Linhas de mira (Crosshair)
            drawLine(
                color = Color(0x18FFFFFF),
                start = Offset(center.x - maxR, center.y),
                end = Offset(center.x + maxR, center.y),
                strokeWidth = 1f
            )
            drawLine(
                color = Color(0x18FFFFFF),
                start = Offset(center.x, center.y - maxR),
                end = Offset(center.x, center.y + maxR),
                strokeWidth = 1f
            )
        }

        // Desenha a Seta Indicadora do Carro (centralizada, apontando para o bearing)
        rotate(degrees = location.bearing, pivot = center) {
            val arrowPath = Path().apply {
                moveTo(center.x, center.y - 13f)
                lineTo(center.x + 9f, center.y + 11f)
                lineTo(center.x, center.y + 6f)
                lineTo(center.x - 9f, center.y + 11f)
                close()
            }
            drawPath(path = arrowPath, color = NeedleRed)
            drawPath(path = arrowPath, color = Color.White, style = Stroke(width = 2f))
        }
    }
}

/**
 * Badge com status de conexão do GPS, satélites e orientação da bússola.
 */
@Composable
private fun GpsStatusBadge(location: GpsLocationData) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xCC181A1F),
        border = BorderStroke(1.dp, SurfaceCardBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (location.hasFix) Color(0xFF4CAF50) else Color(0xFFFFB300))
            )
            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = if (location.hasFix) {
                    "${location.cardinalDirection} ${location.bearing.toInt()}°"
                } else {
                    stringResource(R.string.gps_searching)
                },
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )

            if (location.satellitesCount > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "• ${location.satellitesCount} sat",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * Velocímetro digital simplificado com leitura instantânea do GPS em km/h.
 */
@Composable
private fun SpeedometerBadge(speedKmh: Float) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xCC181A1F),
        border = BorderStroke(1.dp, SurfaceCardBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = speedKmh.toInt().toString(),
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "km/h",
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 1.dp)
            )
        }
    }
}

/**
 * Banner para solicitar a concessão de permissão de GPS no Cockpit.
 */
@Composable
private fun LocationPermissionBanner(
    onRequestPermission: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onRequestPermission() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0x22E61924))
                .border(1.dp, NeedleRedGlow, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.LocationOn,
                contentDescription = null,
                tint = NeedleRed,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.location_permission_title),
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.location_permission_desc),
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}
