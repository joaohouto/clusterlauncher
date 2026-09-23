package com.joaohouto.clusterlauncher.ui.cockpit.dialogs

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.joaohouto.clusterlauncher.R
import com.joaohouto.clusterlauncher.data.location.GpsLocationRepository
import com.joaohouto.clusterlauncher.data.map.MBTilesRepository
import com.joaohouto.clusterlauncher.ui.cockpit.components.MapLibreMapHelper
import com.joaohouto.clusterlauncher.ui.cockpit.components.OsmdroidMapHelper
import com.joaohouto.clusterlauncher.ui.cockpit.components.VehicleMarkerOverlay
import com.joaohouto.clusterlauncher.ui.theme.LocalClusterAccent
import com.joaohouto.clusterlauncher.ui.theme.SurfaceCardBorder
import com.joaohouto.clusterlauncher.ui.theme.TextDisabled
import com.joaohouto.clusterlauncher.ui.theme.TextPrimary
import com.joaohouto.clusterlauncher.ui.theme.TextSecondary
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import kotlin.math.roundToInt

/**
 * Modal de visualização de mapa offline em tela cheia powered by osmdroid.
 * Oferece renderização nativa de MBTiles com suporte a pan suave, inércia,
 * pinch-to-zoom multi-touch contínuo e botões táteis ampliados para uso veicular.
 */
@Composable
fun FullScreenMapModal(
    isDarkMode: Boolean = false,
    initialDarkMode: Boolean = isDarkMode,
    followHeading: Boolean = false,
    onToggleDarkMode: ((Boolean) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val mapState by MBTilesRepository.mapState.collectAsState()
    val locationState by GpsLocationRepository.locationState.collectAsState()

    var currentZoom by remember { mutableIntStateOf(17) }
    var isMapPanned by remember { mutableStateOf(false) }
    var osmdroidMapView by remember { mutableStateOf<MapView?>(null) }
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var mapLibreMapView by remember { mutableStateOf<org.maplibre.android.maps.MapView?>(null) }

    BackHandler { onDismiss() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0C0D10))
        ) {
            // 1. Motor de Renderização do Mapa (MapLibre Vetorial, Osmdroid Raster ou Fallback Radar)
            val activeFile = mapState.activeFile
            val accentTheme = LocalClusterAccent.current
            val primaryColor = accentTheme.primary.toArgb()
            val darkColor = accentTheme.dark.toArgb()

            if (mapState.isMapLoaded && activeFile != null && activeFile.exists()) {
                if (mapState.isVectorMap) {
                    // Mapa Vetorial PBF via MapLibre Native
                    AndroidView(
                        factory = { ctx ->
                            val mapView = MapLibreMapHelper.createMapView(
                                context = ctx,
                                mbtilesFile = activeFile,
                                initialLocation = locationState,
                                isInteractive = true,
                                initialZoom = 17.0,
                                primaryColor = primaryColor,
                                darkColor = darkColor,
                                followVehicleHeading = followHeading
                            ) { map ->
                                mapLibreMap = map
                                map.addOnCameraMoveListener {
                                    currentZoom = map.cameraPosition.zoom.roundToInt()
                                    val target = map.cameraPosition.target
                                    if (target != null) {
                                        val lat = if (locationState.latitude != 0.0) locationState.latitude else -23.5505
                                        val lon = if (locationState.longitude != 0.0) locationState.longitude else -46.6333
                                        val latDiff = Math.abs(target.latitude - lat)
                                        val lonDiff = Math.abs(target.longitude - lon)
                                        isMapPanned = (latDiff > 0.0008 || lonDiff > 0.0008)
                                    }
                                }
                            }
                            mapLibreMapView = mapView
                            mapView
                        },
                        update = { mapView ->
                            val map = mapLibreMap
                            if (map != null && !isMapPanned) {
                                val lat = if (locationState.latitude != 0.0) locationState.latitude else -23.5505
                                val lon = if (locationState.longitude != 0.0) locationState.longitude else -46.6333
                                val targetBearing = if (followHeading) locationState.bearing.toDouble() else 0.0
                                val currentZ = map.cameraPosition.zoom
                                val bottomPad = if (followHeading) (mapView.height * 0.20) else 0.0

                                val cameraPosition = CameraPosition.Builder()
                                    .target(LatLng(lat, lon))
                                    .zoom(currentZ)
                                    .bearing(targetBearing)
                                    .padding(0.0, 0.0, 0.0, bottomPad)
                                    .build()
                                map.easeCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 900)
                            }
                            MapLibreMapHelper.updateVehicleLocation(
                                mapView.context,
                                mapLibreMap,
                                locationState,
                                primaryColor,
                                darkColor,
                                followVehicleHeading = followHeading
                            )
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    DisposableEffect(Unit) {
                        onDispose {
                            mapLibreMapView?.onPause()
                            mapLibreMapView?.onStop()
                            mapLibreMapView?.onDestroy()
                            mapLibreMapView = null
                            mapLibreMap = null
                        }
                    }
                } else {
                    // Mapa Rasterizado via osmdroid
                    AndroidView(
                        factory = { ctx ->
                            val mapView = OsmdroidMapHelper.createMapView(
                                context = ctx,
                                mbtilesFile = activeFile,
                                initialLocation = locationState,
                                isInteractive = true,
                                isDarkMode = initialDarkMode,
                                initialZoom = 17.0,
                                primaryColor = primaryColor,
                                darkColor = darkColor,
                                followHeading = followHeading
                            )

                            mapView.addMapListener(object : MapListener {
                                override fun onScroll(event: ScrollEvent?): Boolean {
                                    val center = mapView.mapCenter
                                    val latDiff = Math.abs(center.latitude - locationState.latitude)
                                    val lonDiff = Math.abs(center.longitude - locationState.longitude)
                                    isMapPanned = (latDiff > 0.001 || lonDiff > 0.001)
                                    return false
                                }

                                override fun onZoom(event: ZoomEvent?): Boolean {
                                    currentZoom = mapView.zoomLevelDouble.roundToInt()
                                    return false
                                }
                            })

                            osmdroidMapView = mapView
                            mapView
                        },
                        update = { mapView ->
                            if (initialDarkMode) {
                                mapView.overlayManager.tilesOverlay.setColorFilter(OsmdroidMapHelper.DarkCockpitColorFilter)
                            } else {
                                mapView.overlayManager.tilesOverlay.setColorFilter(null)
                            }

                            if (!isMapPanned) {
                                val lat = if (locationState.latitude != 0.0) locationState.latitude else -23.5505
                                val lon = if (locationState.longitude != 0.0) locationState.longitude else -46.6333
                                mapView.controller.setCenter(GeoPoint(lat, lon))
                                mapView.mapOrientation = if (followHeading) -locationState.bearing else 0f
                            }

                            for (overlay in mapView.overlays) {
                                if (overlay is VehicleMarkerOverlay) {
                                    overlay.location = locationState
                                    overlay.followHeading = followHeading
                                    overlay.updateColors(primaryColor, darkColor)
                                    break
                                }
                            }
                            mapView.invalidate()
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    DisposableEffect(Unit) {
                        onDispose {
                            osmdroidMapView?.onPause()
                            osmdroidMapView?.onDetach()
                            osmdroidMapView = null
                        }
                    }
                }
            } else {
                val accent = accentTheme.primary
                val accentDark = accentTheme.dark
                val accentGlow = accentTheme.glow

                // Fallback: Radar HUD automotivo caso nenhum arquivo offline esteja ativo
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxR = size.minDimension / 2.2f
                    val radarBearing = if (followHeading) -locationState.bearing else 0f
                    val arrowRotation = if (followHeading) 0f else locationState.bearing

                    rotate(degrees = radarBearing, pivot = center) {
                        drawCircle(
                            color = Color(0x18FFFFFF),
                            radius = maxR,
                            center = center,
                            style = Stroke(width = 1.5f)
                        )
                        drawCircle(
                            color = Color(0x10FFFFFF),
                            radius = maxR * 0.65f,
                            center = center,
                            style = Stroke(width = 1f)
                        )
                        drawCircle(
                            color = accentGlow,
                            radius = maxR * 0.25f,
                            center = center
                        )
                        drawLine(
                            color = Color(0x20FFFFFF),
                            start = Offset(center.x - maxR, center.y),
                            end = Offset(center.x + maxR, center.y),
                            strokeWidth = 1f
                        )
                        drawLine(
                            color = Color(0x20FFFFFF),
                            start = Offset(center.x, center.y - maxR),
                            end = Offset(center.x, center.y + maxR),
                            strokeWidth = 1f
                        )
                    }

                    rotate(degrees = arrowRotation, pivot = center) {
                        val arrowPath = Path().apply {
                            moveTo(center.x, center.y - 20f)
                            lineTo(center.x + 14f, center.y + 15f)
                            lineTo(center.x, center.y + 7f)
                            lineTo(center.x - 14f, center.y + 15f)
                            close()
                        }
                        drawPath(path = arrowPath, color = Color(0x99000000), style = Stroke(width = 5.5f))
                        drawPath(path = arrowPath, color = accent)
                        drawPath(path = arrowPath, color = Color.White, style = Stroke(width = 2.5f))
                    }
                }
            }

            // 2. HUD Superior: Botão Voltar Ampliado + Status GPS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Lado Esquerdo: Botão Voltar (Grande) + Status GPS
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Botão Voltar Grande para uso automotivo com estilo neutro
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xEE181A1F),
                        border = BorderStroke(1.dp, SurfaceCardBorder),
                        modifier = Modifier
                            .height(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onDismiss() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Voltar",
                                tint = TextPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Voltar",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Status GPS (apenas GPS e contagem de satélites, sem texto cardinal / ângulo)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xDD181A1F),
                        border = BorderStroke(1.dp, SurfaceCardBorder),
                        modifier = Modifier.height(54.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (locationState.hasFix) Color(0xFF4CAF50) else Color(0xFFFFB300))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (locationState.hasFix) "GPS" else stringResource(R.string.gps_searching),
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (locationState.satellitesCount > 0) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "• ${locationState.satellitesCount} sat",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            // 3. HUD Inferior Direito: Controles Interativos Ampliados de Zoom (+/-) e Recentralizar
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val accent = LocalClusterAccent.current.primary

                // Botão de Recentralizar no Carro Ampliado
                AnimatedVisibility(
                    visible = isMapPanned,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xEE181A1F),
                        border = BorderStroke(1.5.dp, accent),
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .clickable {
                                if (mapState.isVectorMap) {
                                    mapLibreMap?.let { map ->
                                        val lat = if (locationState.latitude != 0.0) locationState.latitude else -23.5505
                                        val lon = if (locationState.longitude != 0.0) locationState.longitude else -46.6333
                                        val targetBearing = if (followHeading) locationState.bearing.toDouble() else 0.0
                                        val cameraPosition = CameraPosition.Builder()
                                            .target(LatLng(lat, lon))
                                            .bearing(targetBearing)
                                            .build()
                                        map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
                                    }
                                } else {
                                    osmdroidMapView?.let { mv ->
                                        mv.controller.animateTo(
                                            GeoPoint(locationState.latitude, locationState.longitude)
                                        )
                                        if (followHeading) {
                                            mv.mapOrientation = -locationState.bearing
                                        }
                                    }
                                }
                                isMapPanned = false
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.MyLocation,
                                contentDescription = "Centralizar no veículo",
                                tint = accent,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                // Bloco de Controle de Zoom Vertical Ampliado
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xEE181A1F),
                    border = BorderStroke(1.5.dp, SurfaceCardBorder)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 6.dp)
                    ) {
                        // Zoom In (+) Ampliado
                        Box(
                            modifier = Modifier
                                .size(62.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    if (mapState.isVectorMap) {
                                        mapLibreMap?.let { map ->
                                            map.animateCamera(CameraUpdateFactory.zoomIn())
                                        }
                                    } else {
                                        osmdroidMapView?.controller?.zoomIn()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Zoom In",
                                tint = TextPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Indicador de Nível de Zoom em Tempo Real
                        Text(
                            text = "${currentZoom}z",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        // Zoom Out (-) Ampliado
                        Box(
                            modifier = Modifier
                                .size(62.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    if (mapState.isVectorMap) {
                                        mapLibreMap?.let { map ->
                                            map.animateCamera(CameraUpdateFactory.zoomOut())
                                        }
                                    } else {
                                        osmdroidMapView?.controller?.zoomOut()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Remove,
                                contentDescription = "Zoom Out",
                                tint = TextPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
