package com.joaohouto.clusterlauncher.ui.cockpit.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Point
import android.util.Log
import com.joaohouto.clusterlauncher.data.location.GpsLocationData
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.modules.MBTilesFileArchive
import org.osmdroid.tileprovider.modules.OfflineTileProvider
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import java.io.File
import kotlin.math.abs

class OsmRenderTracker(
    var lastLat: Double = Double.NaN,
    var lastLon: Double = Double.NaN,
    var lastBearing: Float = Float.NaN,
    var lastFollowHeading: Boolean = false,
    var last3dPerspective: Boolean = false,
    var lastDarkMode: Boolean = false,
    var lastPrimaryColor: Int = 0
) {
    fun shouldUpdate(
        location: GpsLocationData,
        followHeading: Boolean,
        is3dPerspective: Boolean,
        isDarkMode: Boolean,
        primaryColor: Int
    ): Boolean {
        val latChanged = lastLat.isNaN() || abs(location.latitude - lastLat) > 0.000003
        val lonChanged = lastLon.isNaN() || abs(location.longitude - lastLon) > 0.000003
        val bearingChanged = lastBearing.isNaN() || abs(location.bearing - lastBearing) > 0.8f
        val modeChanged = followHeading != lastFollowHeading ||
            is3dPerspective != last3dPerspective ||
            isDarkMode != lastDarkMode ||
            primaryColor != lastPrimaryColor

        if (latChanged || lonChanged || bearingChanged || modeChanged) {
            lastLat = location.latitude
            lastLon = location.longitude
            lastBearing = location.bearing
            lastFollowHeading = followHeading
            last3dPerspective = is3dPerspective
            lastDarkMode = isDarkMode
            lastPrimaryColor = primaryColor
            return true
        }
        return false
    }
}

object OsmdroidMapHelper {

    private const val TAG = "OsmdroidMapHelper"

    val DarkCockpitColorFilter = ColorMatrixColorFilter(
        ColorMatrix(
            floatArrayOf(
                -0.75f,  0.00f,  0.00f, 0.00f, 220f,
                 0.00f, -0.75f,  0.00f, 0.00f, 220f,
                 0.00f,  0.00f, -0.70f, 0.00f, 230f,
                 0.00f,  0.00f,  0.00f, 1.00f,   0f
            )
        )
    )

    fun initOsmdroid(context: Context) {
        try {
            val cfg = Configuration.getInstance()
            cfg.userAgentValue = context.packageName
            val prefs = context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
            cfg.load(context, prefs)
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao inicializar osmdroid", e)
        }
    }

    /**
     * Cria e configura um MapView do osmdroid com suporte direto ao arquivo MBTiles.
     */
    fun createMapView(
        context: Context,
        mbtilesFile: File?,
        initialLocation: GpsLocationData,
        isInteractive: Boolean,
        isDarkMode: Boolean,
        initialZoom: Double = 17.0,
        primaryColor: Int = Color.parseColor("#E61924"),
        darkColor: Int = Color.parseColor("#8B0000"),
        followHeading: Boolean = false
    ): MapView {
        initOsmdroid(context)

        val tileProvider = if (mbtilesFile != null && mbtilesFile.exists()) {
            try {
                val offlineProvider = OfflineTileProvider(
                    SimpleRegisterReceiver(context),
                    arrayOf(mbtilesFile)
                )
                for (archive in offlineProvider.archives) {
                    if (archive is MBTilesFileArchive) {
                        archive.setIgnoreTileSource(true)
                    }
                }
                val tileSource = XYTileSource(
                    "mbtiles",
                    0,
                    22,
                    256,
                    ".png",
                    emptyArray()
                )
                offlineProvider.tileSource = tileSource
                offlineProvider
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao abrir MBTiles com osmdroid", e)
                null
            }
        } else {
            null
        }

        val mapView = if (tileProvider != null) {
            MapView(context, tileProvider)
        } else {
            MapView(context)
        }
        mapView.tag = OsmRenderTracker()

        mapView.setBackgroundColor(Color.parseColor("#0C0D10"))
        try {
            mapView.overlayManager.tilesOverlay.loadingBackgroundColor = Color.parseColor("#0C0D10")
            mapView.overlayManager.tilesOverlay.loadingLineColor = Color.TRANSPARENT
        } catch (_: Exception) {}

        mapView.setDestroyMode(false)
        mapView.isTilesScaledToDpi = true
        mapView.minZoomLevel = 4.0
        mapView.maxZoomLevel = 22.0
        mapView.setMultiTouchControls(isInteractive)
        mapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        mapView.setBuiltInZoomControls(false)

        if (!isInteractive) {
            mapView.setOnTouchListener { _, _ -> false }
            mapView.isClickable = false
            mapView.isFocusable = false
            mapView.isFocusableInTouchMode = false
        }

        // Aplica filtro noturno se ativado
        if (isDarkMode) {
            mapView.overlayManager.tilesOverlay.setColorFilter(DarkCockpitColorFilter)
        } else {
            mapView.overlayManager.tilesOverlay.setColorFilter(null)
        }

        // Posição inicial e orientação
        val target = MapProjectionHelper.calculateRoadAheadTarget(
            location = initialLocation,
            followHeading = followHeading,
            zoom = initialZoom
        )
        mapView.controller.setZoom(initialZoom)
        mapView.controller.setCenter(GeoPoint(target.latitude, target.longitude))
        mapView.mapOrientation = if (followHeading) -initialLocation.bearing else 0f

        // Adiciona overlay do veículo
        val vehicleOverlay = VehicleMarkerOverlay(initialLocation, primaryColor, darkColor, followHeading)
        mapView.overlays.add(vehicleOverlay)

        return mapView
    }
}

/**
 * Overlay de renderização do indicador circular do veículo (estilo Android Auto) no osmdroid:
 * círculo preenchido com a cor do tema (primaryColor), sombra difusa externa
 * e seta de navegação branca centralizada.
 */
class VehicleMarkerOverlay(
    var location: GpsLocationData,
    var primaryColor: Int = Color.parseColor("#E61924"),
    var darkColor: Int = Color.parseColor("#8B0000"),
    var followHeading: Boolean = false
) : Overlay() {

    private val outerShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(45, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val innerShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(105, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primaryColor
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(65, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2.2f
    }

    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        pathEffect = CornerPathEffect(3.0f)
    }

    private val path = android.graphics.Path()

    fun updateColors(primary: Int, dark: Int) {
        if (primaryColor != primary || darkColor != dark) {
            primaryColor = primary
            darkColor = dark
            circlePaint.color = primary
        }
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        if (location.latitude == 0.0 && location.longitude == 0.0) return

        val point = Point()
        mapView.projection.toPixels(GeoPoint(location.latitude, location.longitude), point)

        val cx = point.x.toFloat()
        val cy = point.y.toFloat()

        // 1. Sombra e círculo na cor do tema
        canvas.drawCircle(cx, cy + 2.0f, 38f, outerShadowPaint)
        canvas.drawCircle(cx, cy + 1.2f, 34f, innerShadowPaint)
        canvas.drawCircle(cx, cy, 31.5f, circlePaint)
        canvas.drawCircle(cx, cy, 30.5f, borderPaint)

        // 2. Seta branca interna rotacionada na direção do veículo
        canvas.save()
        val rotation = if (followHeading) 0f else location.bearing
        canvas.rotate(rotation, cx, cy)

        path.reset()
        path.moveTo(cx, cy - 17.5f)
        path.lineTo(cx + 14.5f, cy + 13.5f)
        path.lineTo(cx, cy + 6.5f)
        path.lineTo(cx - 14.5f, cy + 13.5f)
        path.close()

        canvas.drawPath(path, arrowPaint)

        canvas.restore()
    }
}
