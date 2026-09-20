package com.joaohouto.clusterlauncher.data.map

import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan

/**
 * Utilitários matemáticos para projeção Web Mercator (Slippy Map standard do OpenStreetMap).
 */
object SlippyMapUtils {

    const val TILE_SIZE = 256

    /**
     * Converte Longitude em coordenada X de tile contínua.
     */
    fun lonToTileX(lon: Double, zoom: Int): Double {
        val n = (1 shl zoom).toDouble()
        return (lon + 180.0) / 360.0 * n
    }

    /**
     * Converte Latitude em coordenada Y de tile contínua (Web Mercator).
     */
    fun latToTileY(lat: Double, zoom: Int): Double {
        val latClamped = lat.coerceIn(-85.05112878, 85.05112878)
        val latRad = Math.toRadians(latClamped)
        val n = (1 shl zoom).toDouble()
        return (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * n
    }
}
