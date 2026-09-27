package com.joaohouto.clusterlauncher.ui.cockpit.components

import com.joaohouto.clusterlauncher.data.location.GpsLocationData
import org.junit.Assert.assertEquals
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
    fun `when vehicle is driving North with followHeading, target remains centered on vehicle`() {
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

        assertEquals(-23.5505, target.latitude, 0.000001)
        assertEquals(-46.6333, target.longitude, 0.000001)
        assertEquals(0f, target.bearing, 0.001f)
    }

    @Test
    fun `when vehicle is driving South in North-Up mode, target remains centered on vehicle`() {
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

        assertEquals(-23.5505, target.latitude, 0.000001)
        assertEquals(-46.6333, target.longitude, 0.000001)
        assertEquals(0f, target.bearing, 0.001f)
    }

    @Test
    fun `when vehicle is driving East, target remains centered on vehicle`() {
        val location = GpsLocationData(
            latitude = -23.5505,
            longitude = -46.6333,
            bearing = 90f, // East
            speedKmh = 60f,
            hasFix = true
        )

        val target = MapProjectionHelper.calculateRoadAheadTarget(
            location = location,
            followHeading = true,
            zoom = 15.0,
            viewportHeightPx = 400
        )

        assertEquals(-23.5505, target.latitude, 0.000001)
        assertEquals(-46.6333, target.longitude, 0.000001)
        assertEquals(90f, target.bearing, 0.001f)
    }

    @Test
    fun `when vehicle is driving West, target remains centered on vehicle`() {
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

        assertEquals(-23.5505, target.latitude, 0.000001)
        assertEquals(-46.6333, target.longitude, 0.000001)
        assertEquals(0f, target.bearing, 0.001f)
    }
}
