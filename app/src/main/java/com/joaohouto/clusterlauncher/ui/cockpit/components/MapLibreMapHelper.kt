package com.joaohouto.clusterlauncher.ui.cockpit.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.Log
import com.joaohouto.clusterlauncher.data.location.GpsLocationData
import com.joaohouto.clusterlauncher.data.map.LocalMBTilesServer
import android.graphics.drawable.ColorDrawable
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Point
import java.io.File
import kotlin.math.abs

/**
 * Rastreador leve anexado ao MapView para evitar animações de câmera e updates JNI/OpenGL
 * redundantes durante recomposições da UI quando a telemetria GPS não mudou.
 */
class MapLibreRenderTracker(
    var lastLat: Double = Double.NaN,
    var lastLon: Double = Double.NaN,
    var lastBearing: Float = Float.NaN,
    var lastFollowHeading: Boolean = false,
    var last3dPerspective: Boolean = false,
    var lastPrimaryColor: Int = 0
) {
    fun shouldUpdate(
        location: GpsLocationData,
        followHeading: Boolean,
        is3dPerspective: Boolean,
        primaryColor: Int
    ): Boolean {
        val latChanged = lastLat.isNaN() || abs(location.latitude - lastLat) > 0.000003
        val lonChanged = lastLon.isNaN() || abs(location.longitude - lastLon) > 0.000003
        val bearingChanged = lastBearing.isNaN() || abs(location.bearing - lastBearing) > 0.8f
        val modeChanged = followHeading != lastFollowHeading ||
            is3dPerspective != last3dPerspective ||
            primaryColor != lastPrimaryColor

        if (latChanged || lonChanged || bearingChanged || modeChanged) {
            lastLat = location.latitude
            lastLon = location.longitude
            lastBearing = location.bearing
            lastFollowHeading = followHeading
            last3dPerspective = is3dPerspective
            lastPrimaryColor = primaryColor
            return true
        }
        return false
    }
}

/**
 * Utilitário para gerenciar instâncias do MapLibre Native para mapas vetoriais (.pbf).
 */
object MapLibreMapHelper {

    private const val TAG = "MapLibreMapHelper"
    const val VEHICLE_SOURCE_ID = "vehicle_location_source"
    const val VEHICLE_LAYER_ID = "vehicle_location_layer"
    const val VEHICLE_ICON_ID = "vehicle_arrow_icon"
    const val PITCH_3D_DEGREES = 50.0
    const val PITCH_2D_DEGREES = 0.0

    fun initMapLibre(context: Context) {
        try {
            MapLibre.getInstance(context)
            LocalMBTilesServer.init(context)
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao inicializar MapLibre", e)
        }
    }

    private val styleAccentCache = mutableMapOf<Int, Int>()

    /**
     * Gera bitmap nítido do indicador de posição veicular estilo Android Auto:
     * círculo preenchido com a cor do tema (primaryColor), sombra difusa externa
     * e seta de navegação (chevron) branca centralizada.
     */
    fun createVehicleMarkerBitmap(
        density: Float = 2.0f,
        primaryColor: Int = Color.parseColor("#E61924"),
        darkColor: Int = Color.parseColor("#8B0000")
    ): Bitmap {
        val s = density.coerceAtLeast(1.0f)
        val size = (64 * s).toInt().coerceAtLeast(64)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = size / 2f
        val cy = size / 2f

        // 1. Halo de sombra externa suave em 2 camadas para destacar sobre qualquer rua
        val outerShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(45, 0, 0, 0)
            style = Paint.Style.FILL
        }
        val innerShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(105, 0, 0, 0)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy + 1.2f * s, 24.5f * s, outerShadowPaint)
        canvas.drawCircle(cx, cy + 0.8f * s, 22.0f * s, innerShadowPaint)

        // 2. Círculo principal preenchido com a cor do tema (estilo Android Auto)
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            style = Paint.Style.FILL
        }
        val circleRadius = 20.5f * s
        canvas.drawCircle(cx, cy, circleRadius, circlePaint)

        // Borda sutil translúcida para acabamento em alta definição
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(65, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * s
        }
        canvas.drawCircle(cx, cy, circleRadius - 0.6f * s, borderPaint)

        // 3. Seta de navegação (chevron) branca centralizada dentro do círculo
        val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            pathEffect = CornerPathEffect(1.6f * s)
        }

        val arrowPath = Path().apply {
            moveTo(cx, cy - 11.5f * s)
            lineTo(cx + 9.5f * s, cy + 9.0f * s)
            lineTo(cx, cy + 4.2f * s)
            lineTo(cx - 9.5f * s, cy + 9.0f * s)
            close()
        }

        canvas.drawPath(arrowPath, arrowPaint)

        return bitmap
    }

    /**
     * Adiciona ou atualiza camada nativa OpenGL com o marcador do veículo nas coordenadas geográficas.
     */
    fun setupVehicleMarker(
        context: Context,
        style: Style,
        location: GpsLocationData,
        primaryColor: Int = Color.parseColor("#E61924"),
        darkColor: Int = Color.parseColor("#8B0000"),
        followVehicleHeading: Boolean = false,
        is3dPerspective: Boolean = false
    ) {
        try {
            val density = context.resources.displayMetrics.density
            val bitmap = createVehicleMarkerBitmap(density, primaryColor, darkColor)
            style.addImage(VEHICLE_ICON_ID, bitmap)
            styleAccentCache[style.hashCode()] = primaryColor

            val lat = if (location.latitude != 0.0) location.latitude else -23.5505
            val lon = if (location.longitude != 0.0) location.longitude else -46.6333
            val hasValidLocation = (location.latitude != 0.0 || location.longitude != 0.0)

            if (style.getSource(VEHICLE_SOURCE_ID) == null) {
                val source = GeoJsonSource(
                    VEHICLE_SOURCE_ID,
                    Point.fromLngLat(lon, lat)
                )
                style.addSource(source)
            }

            val iconRotation = if (followVehicleHeading) 0f else location.bearing
            val rotationAlignment = if (followVehicleHeading) {
                Property.ICON_ROTATION_ALIGNMENT_VIEWPORT
            } else {
                Property.ICON_ROTATION_ALIGNMENT_MAP
            }
            val pitchAlignment = if (is3dPerspective) {
                Property.ICON_PITCH_ALIGNMENT_MAP
            } else {
                Property.ICON_PITCH_ALIGNMENT_VIEWPORT
            }

            if (style.getLayer(VEHICLE_LAYER_ID) == null) {
                val symbolLayer = SymbolLayer(VEHICLE_LAYER_ID, VEHICLE_SOURCE_ID).apply {
                    setProperties(
                        PropertyFactory.iconImage(VEHICLE_ICON_ID),
                        PropertyFactory.iconRotate(iconRotation),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconIgnorePlacement(true),
                        PropertyFactory.iconRotationAlignment(rotationAlignment),
                        PropertyFactory.iconPitchAlignment(pitchAlignment),
                        PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
                        PropertyFactory.iconOpacity(if (hasValidLocation) 1.0f else 0.7f)
                    )
                }
                style.addLayer(symbolLayer)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao configurar marcador veicular no MapLibre", e)
        }
    }

    /**
     * Atualiza a posição, o ângulo e a cor de accent do marcador veicular em tempo real.
     */
    fun updateVehicleLocation(
        context: Context,
        map: MapLibreMap?,
        location: GpsLocationData,
        primaryColor: Int = Color.parseColor("#E61924"),
        darkColor: Int = Color.parseColor("#8B0000"),
        followVehicleHeading: Boolean = false,
        is3dPerspective: Boolean = false
    ) {
        if (map == null) return
        val style = map.style ?: return
        if (!style.isFullyLoaded) return

        // Se a cor de destaque mudou, recria e atualiza imediatamente o bitmap no atlas de texturas
        if (styleAccentCache[style.hashCode()] != primaryColor) {
            styleAccentCache[style.hashCode()] = primaryColor
            val density = context.resources.displayMetrics.density
            val bitmap = createVehicleMarkerBitmap(density, primaryColor, darkColor)
            style.addImage(VEHICLE_ICON_ID, bitmap)
        }

        val targetLat = if (location.latitude != 0.0) location.latitude else -23.5505
        val targetLon = if (location.longitude != 0.0) location.longitude else -46.6333
        val hasValidLocation = (location.latitude != 0.0 || location.longitude != 0.0)

        val source = style.getSourceAs<GeoJsonSource>(VEHICLE_SOURCE_ID)
        val layer = style.getLayerAs<SymbolLayer>(VEHICLE_LAYER_ID)

        if (source == null || layer == null) {
            setupVehicleMarker(context, style, location, primaryColor, darkColor, followVehicleHeading, is3dPerspective)
            return
        }

        val iconRotation = if (followVehicleHeading) 0f else location.bearing
        val rotationAlignment = if (followVehicleHeading) {
            Property.ICON_ROTATION_ALIGNMENT_VIEWPORT
        } else {
            Property.ICON_ROTATION_ALIGNMENT_MAP
        }
        val pitchAlignment = if (is3dPerspective) {
            Property.ICON_PITCH_ALIGNMENT_MAP
        } else {
            Property.ICON_PITCH_ALIGNMENT_VIEWPORT
        }

        try {
            source.setGeoJson(Point.fromLngLat(targetLon, targetLat))
            layer.setProperties(
                PropertyFactory.iconRotate(iconRotation),
                PropertyFactory.iconRotationAlignment(rotationAlignment),
                PropertyFactory.iconPitchAlignment(pitchAlignment),
                PropertyFactory.iconOpacity(if (hasValidLocation) 1.0f else 0.7f)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao atualizar marcador do veículo no MapLibre: ${e.message}")
        }
    }

    /**
     * Cria e configura um MapView do MapLibre apontando para o servidor local de blocos PBF.
     */
    fun createMapView(
        context: Context,
        mbtilesFile: File?,
        initialLocation: GpsLocationData,
        isInteractive: Boolean,
        initialZoom: Double = 17.0,
        primaryColor: Int = Color.parseColor("#E61924"),
        darkColor: Int = Color.parseColor("#8B0000"),
        followVehicleHeading: Boolean = false,
        is3dPerspective: Boolean = false,
        onMapReady: ((MapLibreMap) -> Unit)? = null
    ): MapView {
        initMapLibre(context)

        // Prepara e inicia o servidor loopback com o arquivo selecionado
        LocalMBTilesServer.setSource(mbtilesFile)
        LocalMBTilesServer.start()

        val mapOptions = MapLibreMapOptions.createFromAttributes(context)
            .foregroundLoadColor(Color.parseColor("#0C0D10"))
            .textureMode(true)
            .translucentTextureSurface(false)

        val mapView = MapView(context, mapOptions)
        mapView.tag = MapLibreRenderTracker()
        mapView.setBackgroundColor(Color.parseColor("#0C0D10"))
        mapView.foreground = ColorDrawable(Color.parseColor("#0C0D10"))
        mapView.onCreate(null)
        mapView.onStart()
        mapView.onResume()

        mapView.getMapAsync { map ->
            // Configurações de interface para visual cockpit limpo
            map.uiSettings.isAttributionEnabled = false
            map.uiSettings.isLogoEnabled = false
            map.uiSettings.isCompassEnabled = false

            map.uiSettings.isRotateGesturesEnabled = isInteractive
            map.uiSettings.isTiltGesturesEnabled = isInteractive
            map.uiSettings.isZoomGesturesEnabled = isInteractive
            map.uiSettings.isScrollGesturesEnabled = isInteractive
            map.uiSettings.isDoubleTapGesturesEnabled = isInteractive
            map.uiSettings.isQuickZoomGesturesEnabled = isInteractive

            map.setMinZoomPreference(0.0)
            map.setMaxZoomPreference(22.0)

            val target = MapProjectionHelper.calculateRoadAheadTarget(
                location = initialLocation,
                followHeading = followVehicleHeading,
                zoom = initialZoom,
                forwardOffset = is3dPerspective
            )
            val pitch = if (is3dPerspective) PITCH_3D_DEGREES else PITCH_2D_DEGREES
            map.cameraPosition = CameraPosition.Builder()
                .target(LatLng(target.latitude, target.longitude))
                .zoom(initialZoom)
                .tilt(pitch)
                .bearing(target.bearing.toDouble())
                .build()

            val styleJson = LocalMBTilesServer.buildCockpitDarkStyle(LocalMBTilesServer.port)
            val styleBuilder = Style.Builder().fromJson(styleJson)

            val density = context.resources.displayMetrics.density
            val vehicleBitmap = createVehicleMarkerBitmap(density, primaryColor, darkColor)
            styleBuilder.withImage(VEHICLE_ICON_ID, vehicleBitmap)

            map.setStyle(styleBuilder) { style ->
                Log.i(TAG, "Estilo Cockpit Dark carregado com sucesso no MapLibre")
                setupVehicleMarker(context, style, initialLocation, primaryColor, darkColor, followVehicleHeading, is3dPerspective)
                onMapReady?.invoke(map)
            }
        }

        return mapView
    }
}
