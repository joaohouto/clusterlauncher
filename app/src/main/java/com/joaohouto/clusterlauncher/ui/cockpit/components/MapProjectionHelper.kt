package com.joaohouto.clusterlauncher.ui.cockpit.components

import com.joaohouto.clusterlauncher.data.location.GpsLocationData
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Utilitário para cálculo e centralização do foco da câmera no indicador do veículo.
 * Suporta tanto o modo centralizado 2D (50% / 50%) quanto o modo 3D estilo Android Auto
 * (deslocando o foco da câmera à frente para posicionar o veículo na parte inferior da tela).
 */
object MapProjectionHelper {

    data class ProjectedTarget(
        val latitude: Double,
        val longitude: Double,
        val bearing: Float
    )

    /**
     * Retorna as coordenadas de foco da câmera.
     * Quando [forwardOffset] é true, desloca o centro da câmera na direção da visão
     * para posicionar o carro mais abaixo na tela (~68% da altura) e mostrar mais a pista à frente.
     */
    fun calculateRoadAheadTarget(
        location: GpsLocationData,
        followHeading: Boolean,
        zoom: Double,
        viewportHeightPx: Int = 400,
        forwardOffset: Boolean = false
    ): ProjectedTarget {
        val lat = if (location.latitude != 0.0) location.latitude else -23.5505
        val lon = if (location.longitude != 0.0) location.longitude else -46.6333
        val cameraBearing = if (followHeading) location.bearing else 0f

        if (!forwardOffset) {
            return ProjectedTarget(lat, lon, cameraBearing)
        }

        val safeZoom = zoom.coerceIn(4.0, 22.0)
        val latRad = Math.toRadians(lat)
        val cosLat = cos(latRad).coerceAtLeast(0.01)
        val metersPerPixel = (78271.516 * cosLat) / 2.0.pow(safeZoom)
        val offsetPixels = viewportHeightPx.coerceIn(240, 1200) * 0.20
        val offsetMeters = offsetPixels * metersPerPixel

        val headingRad = Math.toRadians(cameraBearing.toDouble())
        val deltaLat = (offsetMeters * cos(headingRad)) / 111320.0
        val deltaLon = (offsetMeters * sin(headingRad)) / (111320.0 * cosLat)

        return ProjectedTarget(
            latitude = (lat + deltaLat).coerceIn(-85.0, 85.0),
            longitude = lon + deltaLon,
            bearing = cameraBearing
        )
    }
}
