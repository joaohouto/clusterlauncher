package com.joaohouto.clusterlauncher.data.location

/**
 * Representa os dados de telemetria e posicionamento GPS do veículo.
 */
data class GpsLocationData(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val bearing: Float = 0f,
    val speedKmh: Float = 0f,
    val altitude: Double = 0.0,
    val hasFix: Boolean = false,
    val satellitesCount: Int = 0,
    val timestamp: Long = 0L
) {
    /**
     * Retorna a direção cardeal aproximada (N, NE, L, SE, S, SO, O, NO).
     */
    val cardinalDirection: String
        get() {
            val normalized = (bearing % 360 + 360) % 360
            return when {
                normalized >= 337.5 || normalized < 22.5 -> "N"
                normalized < 67.5 -> "NE"
                normalized < 112.5 -> "L"
                normalized < 157.5 -> "SE"
                normalized < 202.5 -> "S"
                normalized < 247.5 -> "SO"
                normalized < 292.5 -> "O"
                else -> "NO"
            }
        }
}
