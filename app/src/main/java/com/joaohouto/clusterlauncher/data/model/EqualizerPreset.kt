package com.joaohouto.clusterlauncher.data.model

import kotlin.math.roundToInt

/**
 * Presets automotivos do Equalizador de 5 bandas (60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz).
 * Os ganhos padrão são definidos em milibéis (mB), onde 100 mB = 1 dB.
 */
enum class EqualizerPreset(
    val id: String,
    val label: String,
    val defaultBandsMb: List<Int>
) {
    FLAT("flat", "Flat", listOf(0, 0, 0, 0, 0)),
    BASS_BOOST("bass_boost", "Graves", listOf(550, 350, 0, 100, 200)),
    ROCK("rock", "Rock", listOf(450, 200, -100, 300, 450)),
    POP("pop", "Pop", listOf(-100, 250, 400, 200, -100)),
    ELECTRONIC("electronic", "Eletrônica", listOf(500, 300, -150, 250, 450)),
    VOCAL("vocal", "Vocal", listOf(-200, 0, 450, 350, 100)),
    CUSTOM("custom", "Personalizado", listOf(0, 0, 0, 0, 0));

    companion object {
        val BAND_LABELS = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")
        const val DEFAULT_MIN_MB = -1200
        const val DEFAULT_MAX_MB = 1200

        fun fromId(id: String?): EqualizerPreset {
            return entries.firstOrNull { it.id == id } ?: FLAT
        }

        fun parseBandsCsv(csv: String?): List<Int> {
            if (csv.isNullOrBlank()) return listOf(0, 0, 0, 0, 0)
            val parsed = csv.split(",").mapNotNull { it.trim().toIntOrNull() }
            return List(5) { idx -> parsed.getOrElse(idx) { 0 }.coerceIn(DEFAULT_MIN_MB, DEFAULT_MAX_MB) }
        }

        fun formatBandsCsv(bandsMb: List<Int>): String {
            return List(5) { idx ->
                bandsMb.getOrElse(idx) { 0 }.coerceIn(DEFAULT_MIN_MB, DEFAULT_MAX_MB)
            }.joinToString(",")
        }

        /**
         * Mapeia os 5 valores lógicos do preset/usuário para o número real de bandas e limites do hardware Android.
         */
        fun computeBandLevelsForHardware(
            preset: EqualizerPreset,
            customBandsMb: List<Int>,
            hardwareBandCount: Int,
            minLevelMb: Int,
            maxLevelMb: Int
        ): List<Short> {
            if (hardwareBandCount <= 0) return emptyList()
            val safeMin = minOf(minLevelMb, maxLevelMb)
            val safeMax = maxOf(minLevelMb, maxLevelMb)
            val source = if (preset == CUSTOM) {
                List(5) { idx -> customBandsMb.getOrElse(idx) { 0 } }
            } else {
                preset.defaultBandsMb
            }

            return List(hardwareBandCount) { hwIdx ->
                val rawMb = if (hardwareBandCount == 1) {
                    source[2]
                } else {
                    val pos = hwIdx.toFloat() * (source.size - 1).toFloat() / (hardwareBandCount - 1).toFloat()
                    val low = pos.toInt().coerceIn(0, source.size - 1)
                    val high = (low + 1).coerceIn(0, source.size - 1)
                    val frac = pos - low
                    (source[low] * (1f - frac) + source[high] * frac).roundToInt()
                }
                rawMb.coerceIn(safeMin, safeMax).toShort()
            }
        }
    }
}
