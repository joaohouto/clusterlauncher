package com.joaohouto.clusterlauncher.ui.cockpit.components

import com.joaohouto.clusterlauncher.data.location.GpsLocationData
import org.junit.Assert.assertEquals
import org.junit.Test

class MapProjectionTopDownTest {

    @Test
    fun topDownMode_keepsVehicleCenteredWithoutForwardOffset() {
        val loc = GpsLocationData(
            latitude = -15.793889,
            longitude = -47.882778,
            bearing = 135f,
            speedKmh = 80f,
            hasFix = true
        )
        val target = MapProjectionHelper.calculateRoadAheadTarget(
            location = loc,
            followHeading = true,
            zoom = 17.0,
            viewportHeightPx = 720,
            forwardOffset = false
        )

        assertEquals(loc.latitude, target.latitude, 1e-7)
        assertEquals(loc.longitude, target.longitude, 1e-7)
        assertEquals(135f, target.bearing, 0.01f)
    }

    @Test
    fun resolveValidCoordinates_usesRestoredCoordinatesImmediately() {
        val restored = GpsLocationData(
            latitude = -16.6869,
            longitude = -49.2648,
            bearing = 42f,
            hasFix = false
        )
        assertEquals(-16.6869, MapProjectionHelper.resolveValidLat(restored), 1e-7)
        assertEquals(-49.2648, MapProjectionHelper.resolveValidLon(restored), 1e-7)
    }
}
