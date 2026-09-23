package com.joaohouto.clusterlauncher.ui.cockpit.components

import com.joaohouto.clusterlauncher.data.location.GpsLocationData
import kotlin.math.cos
import kotlin.math.sin

/**
 * Utilitário para cálculo e projeção de visão da estrada à frente (road ahead) nos mapas offline.
 *
 * Considera a movimentação real do veículo (velocidade e rumo):
 * - Carro parado (velocidade < 2.0 km/h): visão centralizada no veículo (50% / 50%).
 * - Carro em movimento: o centro da câmera é projetado suavemente à frente ao longo do vetor
 *   de deslocamento e rumo do veículo, garantindo que aproximadamente 65% do mapa exiba a estrada
 *   à frente e 35% a retaguarda, tanto no modo Head-Up (rumo para cima) quanto North-Up (Norte fixo),
 *   em MapLibre e osmdroid.
 */
object MapProjectionHelper {

    data class ProjectedTarget(
        val latitude: Double,
        val longitude: Double,
        val bearing: Float
    )

    /**
     * Calcula as coordenadas do centro de foco da câmera para garantir a visibilidade
     * prioritária da via à frente do veículo.
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

        // Se o carro estiver parado ou as coordenadas forem padrão, centraliza no veículo
        val speedKmh = location.speedKmh
        val movementFactor = ((speedKmh - 2.0f) / 18.0f).coerceIn(0.0f, 1.0f)

        if (movementFactor <= 0.001f || (location.latitude == 0.0 && location.longitude == 0.0)) {
            return ProjectedTarget(lat, lon, cameraBearing)
        }

        // Para obter ~65% de visão da estrada à frente (e 35% atrás),
        // deslocamos o centro da câmera em 15% da altura da tela na direção do rumo.
        val effectiveHeight = viewportHeightPx.coerceAtLeast(200)
        val offsetPixels = (effectiveHeight * 0.15f) * movementFactor

        // Escala da projeção Web Mercator: graus por pixel para a latitude e nível de zoom
        val degPerPixel = 1.40625 / Math.pow(2.0, zoom)
        val radLat = Math.toRadians(lat)
        val radBearing = Math.toRadians(location.bearing.toDouble())

        // Projeção vetorial do deslocamento à frente no elipsoide
        val deltaLat = offsetPixels * degPerPixel * cos(radLat) * cos(radBearing)
        val deltaLon = offsetPixels * degPerPixel * sin(radBearing)

        return ProjectedTarget(
            latitude = lat + deltaLat,
            longitude = lon + deltaLon,
            bearing = cameraBearing
        )
    }
}
