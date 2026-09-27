package com.joaohouto.clusterlauncher.ui.cockpit.components

import com.joaohouto.clusterlauncher.data.location.GpsLocationData

/**
 * Utilitário para cálculo e centralização do foco da câmera no indicador do veículo.
 * Mantém o marcador de posição do veículo rigorosamente centralizado no mapa (50% / 50%),
 * tanto no card compacto quanto no modal expandido, respeitando a orientação (Head-Up ou North-Up).
 */
object MapProjectionHelper {

    data class ProjectedTarget(
        val latitude: Double,
        val longitude: Double,
        val bearing: Float
    )

    /**
     * Retorna as coordenadas de foco da câmera mantendo o indicador do veículo
     * sempre centralizado no mapa.
     */
    fun calculateRoadAheadTarget(
        location: GpsLocationData,
        followHeading: Boolean,
        zoom: Double,
        viewportHeightPx: Int = 400
    ): ProjectedTarget {
        val lat = if (location.latitude != 0.0) location.latitude else -23.5505
        val lon = if (location.longitude != 0.0) location.longitude else -46.6333
        val cameraBearing = if (followHeading) location.bearing else 0f
        return ProjectedTarget(lat, lon, cameraBearing)
    }
}
