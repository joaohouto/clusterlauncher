package com.joaohouto.clusterlauncher.player

import com.joaohouto.clusterlauncher.data.model.EqualizerPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerPresetTest {

    @Test
    fun parseAndFormatBandsCsv_roundTripAndClamping() {
        val raw = "1500, -2000, 300, 0, 600"
        val parsed = EqualizerPreset.parseBandsCsv(raw)
        assertEquals(listOf(1200, -1200, 300, 0, 600), parsed)
        assertEquals("1200,-1200,300,0,600", EqualizerPreset.formatBandsCsv(parsed))
    }

    @Test
    fun parseBandsCsv_handlesNullOrShortInput() {
        assertEquals(listOf(0, 0, 0, 0, 0), EqualizerPreset.parseBandsCsv(null))
        assertEquals(listOf(200, -100, 0, 0, 0), EqualizerPreset.parseBandsCsv("200,-100"))
    }

    @Test
    fun computeBandLevelsForHardware_fiveBandsMatchesPresetExactly() {
        val levels = EqualizerPreset.computeBandLevelsForHardware(
            preset = EqualizerPreset.BASS_BOOST,
            customBandsMb = listOf(0, 0, 0, 0, 0),
            hardwareBandCount = 5,
            minLevelMb = -1500,
            maxLevelMb = 1500
        )
        assertEquals(EqualizerPreset.BASS_BOOST.defaultBandsMb.map { it.toShort() }, levels)
    }

    @Test
    fun computeBandLevelsForHardware_clampsToHardwareLimits() {
        val levels = EqualizerPreset.computeBandLevelsForHardware(
            preset = EqualizerPreset.CUSTOM,
            customBandsMb = listOf(1200, -1200, 500, -500, 800),
            hardwareBandCount = 5,
            minLevelMb = -600,
            maxLevelMb = 600
        )
        assertEquals(listOf<Short>(600, -600, 500, -500, 600), levels)
    }

    @Test
    fun computeBandLevelsForHardware_interpolatesForDifferentBandCount() {
        val levels = EqualizerPreset.computeBandLevelsForHardware(
            preset = EqualizerPreset.CUSTOM,
            customBandsMb = listOf(400, 200, 0, -200, -400),
            hardwareBandCount = 3,
            minLevelMb = -1200,
            maxLevelMb = 1200
        )
        assertEquals(3, levels.size)
        assertEquals(400.toShort(), levels[0])
        assertEquals(0.toShort(), levels[1])
        assertEquals((-400).toShort(), levels[2])
    }

    @Test
    fun fromId_fallsBackToFlatForUnknownId() {
        assertEquals(EqualizerPreset.ROCK, EqualizerPreset.fromId("rock"))
        assertEquals(EqualizerPreset.FLAT, EqualizerPreset.fromId("unknown_id"))
        assertTrue(EqualizerPreset.computeBandLevelsForHardware(EqualizerPreset.FLAT, emptyList(), 0, -1200, 1200).isEmpty())
    }
}
