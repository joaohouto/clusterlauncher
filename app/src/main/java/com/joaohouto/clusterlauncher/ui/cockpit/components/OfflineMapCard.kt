package com.joaohouto.clusterlauncher.ui.cockpit.components

import android.Manifest
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.joaohouto.clusterlauncher.R
import com.joaohouto.clusterlauncher.data.location.GpsLocationData
import com.joaohouto.clusterlauncher.data.location.GpsLocationRepository
import com.joaohouto.clusterlauncher.data.map.MBTilesRepository
import com.joaohouto.clusterlauncher.ui.theme.LocalClusterAccent
import com.joaohouto.clusterlauncher.ui.theme.SurfaceCardBorder
import com.joaohouto.clusterlauncher.ui.theme.TextDisabled
import com.joaohouto.clusterlauncher.ui.theme.TextPrimary
import com.joaohouto.clusterlauncher.ui.theme.TextSecondary
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.osmdroid.util.GeoPoint
import java.io.File

private val CardShape = RoundedCornerShape(16.dp)

/**
 * Tile horizontal de mapa offline para o Cockpit.
 * Suporta motor duplo: MapLibre Native para mapas vetoriais (.pbf) e osmdroid para mapas rasterizados.
 * Utiliza o zoom padrão 15 para melhor contexto regional na tela inicial.
 */
@Composable
fun OfflineMapCard(
    modifier: Modifier = Modifier,
    heightDp: Int? = null,
    isDarkMode: Boolean = false,
    onMapClick: (() -> Unit)? = null
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
                    modifier = Modifier.fillMaxSize()
                ) {
                    val activeFile = mapState.activeFile
                    if (mapState.isMapLoaded && activeFile != null && activeFile.exists()) {
                        if (mapState.isVectorMap) {
                            MapLibreCardMapView(
                                location = locationState,
                                activeFile = activeFile
                            )
                        } else {
                            OsmCardMapView(
                                location = locationState,
                                activeFile = activeFile,
                                isDarkMode = isDarkMode
                            )
                        }
                    } else {
                        RadarFallbackView(location = locationState)
                    }

                    // Overlay HUD Superior
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.Top
                    ) {
                        GpsStatusBadge(location = locationState)
                    }

                    // Overlay Inferior: Exibido apenas se nenhum mapa offline estiver carregado
                    if (!mapState.isMapLoaded) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomStart)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.no_offline_map),
                                color = LocalClusterAccent.current.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Text(
                                text = stringResource(R.string.offline_map_instruction),
                                color = TextDisabled,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Camada de clique sobreposta a toda a área do card
                    if (onMapClick != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null
                                ) {
                                    onMapClick()
                                }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Visualização do mapa vetorial no card usando MapLibre Native com zoom 15.
 */
@Composable
private fun MapLibreCardMapView(
    location: GpsLocationData,
    activeFile: File?
) {
    val accent = LocalClusterAccent.current
    val primaryColor = accent.primary.toArgb()
    val darkColor = accent.dark.toArgb()

    if (activeFile != null && activeFile.exists()) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    MapLibreMapHelper.createMapView(
                        context = ctx,
                        mbtilesFile = activeFile,
                        initialLocation = location,
                        isInteractive = false,
                        initialZoom = 15.0,
                        primaryColor = primaryColor,
                        darkColor = darkColor
                    )
                },
                update = { mapView ->
                    mapView.getMapAsync { map ->
                        val lat = if (location.latitude != 0.0) location.latitude else -23.5505
                        val lon = if (location.longitude != 0.0) location.longitude else -46.6333
                        map.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(lat, lon))
                            .zoom(15.0)
                            .build()
                        MapLibreMapHelper.updateVehicleLocation(mapView.context, map, location, primaryColor, darkColor)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Sombra sutil nas bordas para integração com o cockpit
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color(0x66000000)),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.maxDimension / 1.6f
                    )
                )
            }
        }
    }
}

/**
 * Visualização do mapa rasterizado no card usando osmdroid com zoom 15.
 */
@Composable
private fun OsmCardMapView(
    location: GpsLocationData,
    activeFile: File?,
    isDarkMode: Boolean
) {
    val accent = LocalClusterAccent.current
    val primaryColor = accent.primary.toArgb()
    val darkColor = accent.dark.toArgb()

    if (activeFile != null && activeFile.exists()) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    OsmdroidMapHelper.createMapView(
                        context = ctx,
                        mbtilesFile = activeFile,
                        initialLocation = location,
                        isInteractive = false,
                        isDarkMode = isDarkMode,
                        initialZoom = 15.0,
                        primaryColor = primaryColor,
                        darkColor = darkColor
                    )
                },
                update = { mapView ->
                    if (isDarkMode) {
                        mapView.overlayManager.tilesOverlay.setColorFilter(OsmdroidMapHelper.DarkCockpitColorFilter)
                    } else {
                        mapView.overlayManager.tilesOverlay.setColorFilter(null)
                    }

                    val lat = if (location.latitude != 0.0) location.latitude else -23.5505
                    val lon = if (location.longitude != 0.0) location.longitude else -46.6333
                    mapView.controller.setCenter(GeoPoint(lat, lon))
                    mapView.controller.setZoom(15.0)

                    for (overlay in mapView.overlays) {
                        if (overlay is VehicleMarkerOverlay) {
                            overlay.location = location
                            overlay.updateColors(primaryColor, darkColor)
                            break
                        }
                    }
                    mapView.invalidate()
                },
                modifier = Modifier.fillMaxSize()
            )

            // Sombra sutil nas bordas para integração com o cockpit
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color(0x66000000)),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.maxDimension / 1.6f
                    )
                )
            }
        }
    }
}

/**
 * Radar automotivo de fallback quando nenhum mapa offline está carregado.
 */
@Composable
private fun RadarFallbackView(location: GpsLocationData) {
    val accentTheme = LocalClusterAccent.current
    val accentPrimary = accentTheme.primary
    val accentDark = accentTheme.dark
    val accentGlow = accentTheme.glow

    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
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
            color = accentGlow,
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

        // Seta do Carro centralizada com borda no tom escuro do accent (sem borda branca)
        rotate(degrees = location.bearing, pivot = center) {
            val arrowPath = Path().apply {
                moveTo(center.x, center.y - 13f)
                lineTo(center.x + 9f, center.y + 11f)
                lineTo(center.x, center.y + 6f)
                lineTo(center.x - 9f, center.y + 11f)
                close()
            }
            drawPath(path = arrowPath, color = Color(0x99000000), style = Stroke(width = 4f))
            drawPath(path = arrowPath, color = accentPrimary)
            drawPath(path = arrowPath, color = Color.White, style = Stroke(width = 2f))
        }
    }
}

/**
 * Badge com status de conexão do GPS e satélites.
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
                    "GPS"
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
        val accent = LocalClusterAccent.current
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(accent.primary.copy(alpha = 0.15f))
                .border(1.dp, accent.glow, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.LocationOn,
                contentDescription = null,
                tint = accent.primary,
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
