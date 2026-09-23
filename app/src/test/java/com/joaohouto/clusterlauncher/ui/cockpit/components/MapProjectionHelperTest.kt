package com.joaohouto.clusterlauncher.ui.cockpit.components

import com.joaohouto.clusterlauncher.data.location.GpsLocationData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapProjectionHelperTest {

    @Test
    fun `when vehicle is stopped, target matches vehicle position`() {
        val location = GpsLocationData(
            latitude = -23.5505,
            longitude = -46.6333,
            bearing = 90f,
            speedKmh = 0f,
            hasFix = true
        )

        val target = MapProjectionHelper.calculateRoadAheadTarget(
            location = location,
            followHeading = false,
            zoom = 15.0,
            viewportHeightPx = 400
        )

        assertEquals(-23.5505, target.latitude, 0.000001)
        assertEquals(-46.6333, target.longitude, 0.000001)
        assertEquals(0f, target.bearing, 0.001f)
    }

    @Test
    fun `when vehicle is driving North, camera target is projected North`() {
        val location = GpsLocationData(
            latitude = -23.5505,
            longitude = -46.6333,
            bearing = 0f, // North
            speedKmh = 60f,
            hasFix = true
        )

        val target = MapProjectionHelper.calculateRoadAheadTarget(
            location = location,
            followHeading = true,
            zoom = 15.0,
            viewportHeightPx = 400
        )

        // North projection means target latitude is greater than vehicle latitude
        assertTrue("Target latitude (${target.latitude}) should be > vehicle latitude (${location.latitude})", target.latitude > location.latitude)
        assertEquals(location.longitude, target.longitude, 0.0001)
        assertEquals(0f, target.bearing, 0.001f)
    }

    @Test
    fun `when vehicle is driving South, camera target is projected South`() {
        val location = GpsLocationData(
            latitude = -23.5505,
            longitude = -46.6333,
            bearing = 180f, // South
            speedKmh = 60f,
            hasFix = true
        )

        val target = MapProjectionHelper.calculateRoadAheadTarget(
            location = location,
            followHeading = false, // North-Up mode
            zoom = 15.0,
            viewportHeightPx = 400
        )

        // South projection means target latitude is less than vehicle latitude
        assertTrue("Target latitude (${target.latitude}) should be < vehicle latitude (${location.latitude})", target.latitude < location.latitude)
        assertEquals(location.longitude, target.longitude, 0.0001)
        assertEquals(0f, target.bearing, 0.001f) // In North-Up, camera bearing is 0
    }

    @Test
    fun `when vehicle is driving East, camera target is projected East`() {
        val location = GpsLocationData(
            latitude = -23.5505,
            longitude = -46.6333,
            bearing = 90f, // East
            speedKmh = 60f,
            hasFix = true
        )

        val target = MapProjectionHelper.calculateRoadAheadTarget(
            location = location,
            followHeading = false,
            zoom = 15.0,
            viewportHeightPx = 400
        )

        assertEquals(location.latitude, target.latitude, 0.0001)
        // East projection means longitude increases (becomes less negative)
        assertTrue("Target longitude (${target.longitude}) should be > vehicle longitude (${location.longitude})", target.longitude > location.longitude)
    }

    @Test
    fun `when vehicle is driving West, camera target is projected West`() {
        val location = GpsLocationData(
            latitude = -23.5505,
            longitude = -46.6333,
            bearing = 270f, // West
            speedKmh = 60f,
            hasFix = true
        )

        val target = MapProjectionHelper.calculateRoadAheadTarget(
            location = location,
            followHeading = false,
            zoom = 15.0,
            viewportHeightPx = 400
        )

        assertEquals(location.latitude, target.latitude, 0.0001)
        // West projection means longitude decreases (becomes more negative)
        assertTrue("Target longitude (${target.longitude}) should be < vehicle longitude (${location.longitude})", target.longitude < location.longitude)
    }
}
