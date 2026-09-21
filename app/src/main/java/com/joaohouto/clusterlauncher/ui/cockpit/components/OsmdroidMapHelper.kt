package com.joaohouto.clusterlauncher.ui.cockpit.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
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
        darkColor: Int = Color.parseColor("#8B0000")
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

        // Posição inicial
        val lat = if (initialLocation.latitude != 0.0) initialLocation.latitude else -23.5505
        val lon = if (initialLocation.longitude != 0.0) initialLocation.longitude else -46.6333
        mapView.controller.setZoom(initialZoom)
        mapView.controller.setCenter(GeoPoint(lat, lon))

        // Adiciona overlay do veículo
        val vehicleOverlay = VehicleMarkerOverlay(initialLocation, primaryColor, darkColor)
        mapView.overlays.add(vehicleOverlay)

        return mapView
    }
}

/**
 * Overlay de renderização da seta de posição e direção do veículo no osmdroid.
 * A borda branca foi substituída pelo tom escuro da accent color.
 */
class VehicleMarkerOverlay(
    var location: GpsLocationData,
    var primaryColor: Int = Color.parseColor("#E61924"),
    var darkColor: Int = Color.parseColor("#8B0000")
) : Overlay() {

    private val shadowPaint = Paint().apply {
        isAntiAlias = true
        color = Color.argb(160, 0, 0, 0)
        style = Paint.Style.STROKE
        strokeWidth = 7.5f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val arrowPaint = Paint().apply {
        isAntiAlias = true
        color = primaryColor
        style = Paint.Style.FILL
    }

    private val outlinePaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4.0f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val path = android.graphics.Path()

    fun updateColors(primary: Int, dark: Int) {
        if (primaryColor != primary || darkColor != dark) {
            primaryColor = primary
            darkColor = dark
            arrowPaint.color = primary
        }
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        if (location.latitude == 0.0 && location.longitude == 0.0) return

        val point = Point()
        mapView.projection.toPixels(GeoPoint(location.latitude, location.longitude), point)

        val cx = point.x.toFloat()
        val cy = point.y.toFloat()

        canvas.save()
        canvas.rotate(location.bearing, cx, cy)

        path.reset()
        path.moveTo(cx, cy - 26f)
        path.lineTo(cx + 17f, cy + 18f)
        path.lineTo(cx, cy + 9f)
        path.lineTo(cx - 17f, cy + 18f)
        path.close()

        canvas.drawPath(path, shadowPaint)
        canvas.drawPath(path, arrowPaint)
        canvas.drawPath(path, outlinePaint)

        canvas.restore()
    }
}
