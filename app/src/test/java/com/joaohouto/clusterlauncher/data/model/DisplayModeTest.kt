package com.joaohouto.clusterlauncher.data.model

import com.joaohouto.clusterlauncher.data.map.LocalMBTilesServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayModeTest {

    @Test
    fun fromId_parsesKnownValuesAndDefaultsToAuto() {
        assertEquals(DisplayMode.AUTO, DisplayMode.fromId("auto"))
        assertEquals(DisplayMode.DAY, DisplayMode.fromId("day"))
        assertEquals(DisplayMode.NIGHT, DisplayMode.fromId("NIGHT"))
        assertEquals(DisplayMode.AUTO, DisplayMode.fromId(null))
        assertEquals(DisplayMode.AUTO, DisplayMode.fromId("unknown"))
    }

    @Test
    fun resolveIsDayHighContrast_respectsForcedModes() {
        assertTrue(DisplayMode.resolveIsDayHighContrast(DisplayMode.DAY, isSystemNight = true, headlightOn = true))
        assertTrue(DisplayMode.resolveIsDayHighContrast(DisplayMode.DAY, isSystemNight = false, headlightOn = null))
        assertFalse(DisplayMode.resolveIsDayHighContrast(DisplayMode.NIGHT, isSystemNight = false, headlightOn = false))
        assertFalse(DisplayMode.resolveIsDayHighContrast(DisplayMode.NIGHT, isSystemNight = true, headlightOn = null))
    }

    @Test
    fun resolveIsDayHighContrast_autoPrioritizesHeadlightOverSystemUiMode() {
        // Farol ligado -> modo Noite mesmo se sistema estiver em Dia
        assertFalse(DisplayMode.resolveIsDayHighContrast(DisplayMode.AUTO, isSystemNight = false, headlightOn = true))
        // Farol desligado -> modo Dia mesmo se sistema estiver em Noite
        assertTrue(DisplayMode.resolveIsDayHighContrast(DisplayMode.AUTO, isSystemNight = true, headlightOn = false))
        // Sem sinal direto de farol -> segue uiMode do Android
        assertFalse(DisplayMode.resolveIsDayHighContrast(DisplayMode.AUTO, isSystemNight = true, headlightOn = null))
        assertTrue(DisplayMode.resolveIsDayHighContrast(DisplayMode.AUTO, isSystemNight = false, headlightOn = null))
    }

    @Test
    fun buildCockpitStyle_producesDistinctHighContrastDayPalette() {
        val nightJson = LocalMBTilesServer.buildCockpitStyle(serverPort = 8080, isDayHighContrast = false)
        val dayJson = LocalMBTilesServer.buildCockpitStyle(serverPort = 8080, isDayHighContrast = true)

        assertNotEquals(nightJson, dayJson)
        assertTrue(nightJson.contains("\"Cluster Cockpit Dark\""))
        assertTrue(dayJson.contains("\"Cluster Cockpit Day High Contrast\""))
        assertTrue(dayJson.contains("\"#181C24\""))
        assertTrue(dayJson.contains("\"#5E6B82\""))
        assertTrue(dayJson.contains("\"#E5EEFC\""))
    }
}
