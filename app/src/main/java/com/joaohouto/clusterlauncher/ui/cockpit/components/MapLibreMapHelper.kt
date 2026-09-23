package com.joaohouto.clusterlauncher.ui.cockpit.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.Log
import com.joaohouto.clusterlauncher.data.location.GpsLocationData
import com.joaohouto.clusterlauncher.data.map.LocalMBTilesServer
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Point
import java.io.File

/**
 * Utilitário para gerenciar instâncias do MapLibre Native para mapas vetoriais (.pbf).
 */
object MapLibreMapHelper {

    private const val TAG = "MapLibreMapHelper"
    const val VEHICLE_SOURCE_ID = "vehicle_location_source"
    const val VEHICLE_LAYER_ID = "vehicle_location_layer"
    const val VEHICLE_ICON_ID = "vehicle_arrow_icon"

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
     * Gera bitmap nítido de seta de navegação veicular com as cores do accent selecionado.
     * A borda branca foi substituída pelo tom escuro da accent color.
     */
    fun createVehicleMarkerBitmap(
        density: Float = 2.0f,
        primaryColor: Int = Color.parseColor("#E61924"),
        darkColor: Int = Color.parseColor("#8B0000")
    ): Bitmap {
        val size = (54 * density).toInt().coerceAtLeast(54)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = size / 2f
        val cy = size / 2f
        val s = density.coerceAtLeast(1.0f)

        // Sombra de contraste escuro para separar de qualquer fundo/estrada
        val shadowPaint = Paint().apply {
            isAntiAlias = true
            color = Color.argb(160, 0, 0, 0)
            style = Paint.Style.STROKE
            strokeWidth = 6.5f * s
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }

        // Borda externa branca para contraste clássico no mapa
        val outlinePaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3.5f * s
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }

        // Preenchimento com a cor principal de accent do veículo
        val arrowPaint = Paint().apply {
            isAntiAlias = true
            color = primaryColor
            style = Paint.Style.FILL
        }

        val path = Path().apply {
            moveTo(cx, cy - 18f * s)
            lineTo(cx + 13f * s, cy + 13f * s)
            lineTo(cx, cy + 6f * s)
            lineTo(cx - 13f * s, cy + 13f * s)
            close()
        }

        canvas.drawPath(path, shadowPaint)
        canvas.drawPath(path, arrowPaint)
        canvas.drawPath(path, outlinePaint)

        return bitmap
    }

    /**
     * Adiciona ou atualiza camada nativa OpenGL com a seta do veículo nas coordenadas geográficas.
     */
    fun setupVehicleMarker(
        context: Context,
        style: Style,
        location: GpsLocationData,
        primaryColor: Int = Color.parseColor("#E61924"),
        darkColor: Int = Color.parseColor("#8B0000"),
        followVehicleHeading: Boolean = false
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

            if (style.getLayer(VEHICLE_LAYER_ID) == null) {
                val symbolLayer = SymbolLayer(VEHICLE_LAYER_ID, VEHICLE_SOURCE_ID).apply {
                    setProperties(
                        PropertyFactory.iconImage(VEHICLE_ICON_ID),
                        PropertyFactory.iconRotate(iconRotation),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconIgnorePlacement(true),
                        PropertyFactory.iconRotationAlignment(rotationAlignment),
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
        followVehicleHeading: Boolean = false
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

        val lat = if (location.latitude != 0.0) location.latitude else -23.5505
        val lon = if (location.longitude != 0.0) location.longitude else -46.6333
        val hasValidLocation = (location.latitude != 0.0 || location.longitude != 0.0)

        val source = style.getSourceAs<GeoJsonSource>(VEHICLE_SOURCE_ID)
        val layer = style.getLayerAs<SymbolLayer>(VEHICLE_LAYER_ID)

        val iconRotation = if (followVehicleHeading) 0f else location.bearing
        val rotationAlignment = if (followVehicleHeading) {
            Property.ICON_ROTATION_ALIGNMENT_VIEWPORT
        } else {
            Property.ICON_ROTATION_ALIGNMENT_MAP
        }

        if (source != null && layer != null) {
            source.setGeoJson(Point.fromLngLat(lon, lat))
            layer.setProperties(
                PropertyFactory.iconRotate(iconRotation),
                PropertyFactory.iconRotationAlignment(rotationAlignment),
                PropertyFactory.iconOpacity(if (hasValidLocation) 1.0f else 0.7f)
            )
        } else {
            setupVehicleMarker(context, style, location, primaryColor, darkColor, followVehicleHeading)
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
        onMapReady: ((MapLibreMap) -> Unit)? = null
    ): MapView {
        initMapLibre(context)

        // Prepara e inicia o servidor loopback com o arquivo selecionado
        LocalMBTilesServer.setSource(mbtilesFile)
        LocalMBTilesServer.start()

        val mapView = MapView(context)
        mapView.onCreate(null)
        mapView.onStart()
        mapView.onResume()

        val lat = if (initialLocation.latitude != 0.0) initialLocation.latitude else -23.5505
        val lon = if (initialLocation.longitude != 0.0) initialLocation.longitude else -46.6333

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

            val targetBearing = if (followVehicleHeading) initialLocation.bearing.toDouble() else 0.0
            map.cameraPosition = CameraPosition.Builder()
                .target(LatLng(lat, lon))
                .zoom(initialZoom)
                .bearing(targetBearing)
                .build()

            val styleJson = LocalMBTilesServer.buildCockpitDarkStyle(LocalMBTilesServer.port)
            val styleBuilder = Style.Builder().fromJson(styleJson)

            val density = context.resources.displayMetrics.density
            val vehicleBitmap = createVehicleMarkerBitmap(density, primaryColor, darkColor)
            styleBuilder.withImage(VEHICLE_ICON_ID, vehicleBitmap)

            map.setStyle(styleBuilder) { style ->
                Log.i(TAG, "Estilo Cockpit Dark carregado com sucesso no MapLibre")
                setupVehicleMarker(context, style, initialLocation, primaryColor, darkColor, followVehicleHeading)
                onMapReady?.invoke(map)
            }
        }

        return mapView
    }
}
