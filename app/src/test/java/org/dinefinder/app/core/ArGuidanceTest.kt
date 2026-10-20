package org.dinefinder.app.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class ArGuidanceTest {
    @Test fun straightAheadFollowsTheArCameraForward() {
        assertEquals(0f, localArrowYaw(0.0, -1.0, 0.0), .001f)
        assertEquals(-90f, localArrowYaw(1.0, 0.0, 0.0), .001f)
    }
    @Test fun compassRightAndLeftTurnsAreNotMirroredInArSpace() {
        assertEquals(-90f, localArrowYaw(0.0, -1.0, 90.0), .001f)
        assertEquals(90f, localArrowYaw(0.0, -1.0, -90.0), .001f)
        assertEquals(180f, abs(localArrowYaw(0.0, -1.0, 180.0)), .001f)
    }
    @Test fun cameraWorldRotationAndRouteTurnCombine() {
        assertEquals(-180f, localArrowYaw(1.0, 0.0, 90.0), .001f)
        assertEquals(0f, localArrowYaw(1.0, 0.0, -90.0), .001f)
    }
    @Test fun invalidTrackingCannotProduceAnArrowDirection() {
        assertThrows(IllegalArgumentException::class.java) { localArrowYaw(0.0, 0.0, 0.0) }
        assertThrows(IllegalArgumentException::class.java) { localArrowYaw(Double.NaN, -1.0, 0.0) }
        assertThrows(IllegalArgumentException::class.java) { localArrowYaw(0.0, -1.0, Double.POSITIVE_INFINITY) }
    }
    @Test fun missingHalalMetadataIsNotPresentedAsHalal() {
        val sample = DemoData.restaurants.first().copy(tags = emptyList(), isHalal = null)
        assertEquals("Not provided", sample.halalStatus)
        assertEquals("Halal", sample.copy(isHalal = true).halalStatus)
        assertEquals("Not halal", sample.copy(tags = listOf("HALAL"), isHalal = false).halalStatus)
        assertEquals("Halal", sample.copy(tags = listOf("halal")).halalStatus)
    }
    @Test fun snappedPathEndpointDoesNotConfirmRestaurantArrival() {
        val end = GeoPoint(5.28, 115.17)
        val restaurant = GeoPoint(5.281, 115.17)
        val route = RoutePlan(listOf(GeoPoint(5.279, 115.17), end), emptyList(), 111.0, 90.0)
        val progress = restaurantRouteProgress(end, route, restaurant)
        assertFalse(progress.hasArrived)
        assertTrue(progress.remainingMeters > 100)
        assertTrue(progress.instruction!!.text.contains("mapped path"))
    }
    @Test fun actualRestaurantCoordinatesConfirmArrivalEvenWhenPathIsSnappedAway() {
        val end = GeoPoint(5.28, 115.17)
        val restaurant = GeoPoint(5.281, 115.17)
        val route = RoutePlan(listOf(GeoPoint(5.279, 115.17), end), emptyList(), 111.0, 90.0)
        assertTrue(restaurantRouteProgress(restaurant, route, restaurant).hasArrived)
        assertEquals(0.0, restaurantRouteProgress(restaurant, route, restaurant).remainingMeters, .001)
    }
}
