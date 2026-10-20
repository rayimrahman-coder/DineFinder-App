package com.example.dinefinder.ar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationDestinationTest {
    @Test
    fun requiresBothCoordinatesRatherThanDefaultingMissingValuesToZero() {
        assertNull(NavigationDestination.create("Example venue", null, 0.0))
        assertNull(NavigationDestination.create("Example venue", 0.0, null))
        assertNull(NavigationDestination.create("Example venue", null, null))
        val destination = NavigationDestination.create("Example venue", 0.0, 0.0)
        assertNotNull(destination)
        assertEquals(0.0, destination!!.latitude, 0.0)
        assertEquals(0.0, destination.longitude, 0.0)
    }

    @Test
    fun rejectsMissingBlankAndWhitespaceNames() {
        for (name in listOf(null, "", " ", "\t\n")) {
            assertNull(NavigationDestination.create(name, 0.0, 0.0))
        }
    }

    @Test
    fun trimsValidNames() {
        val destination = NavigationDestination.create("  Example venue  ", 0.0, 0.0)
        assertEquals("Example venue", destination!!.name)
    }

    @Test
    fun rejectsNonfiniteLatitudeAndLongitude() {
        for (coordinate in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertNull(NavigationDestination.create("Example venue", coordinate, 0.0))
            assertNull(NavigationDestination.create("Example venue", 0.0, coordinate))
        }
    }

    @Test
    fun rejectsCoordinatesOutsideGeographicRanges() {
        for (latitude in listOf(-90.001, 90.001)) {
            assertNull(NavigationDestination.create("Example venue", latitude, 0.0))
        }
        for (longitude in listOf(-180.001, 180.001)) {
            assertNull(NavigationDestination.create("Example venue", 0.0, longitude))
        }
    }

    @Test
    fun acceptsCoordinateRangeBoundaries() {
        assertNotNull(NavigationDestination.create("Example venue", -90.0, -180.0))
        assertNotNull(NavigationDestination.create("Example venue", 90.0, 180.0))
    }
}
