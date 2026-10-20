package com.example.dinefinder.ar

import org.junit.Assert.assertEquals
import org.junit.Test

class WalkingRouteProgressTest {
    @Test
    fun selectsUpcomingEndpointWhenFirstFixHasAlreadyPassedTheOrigin() {
        val route = listOf(point(0.0, 0.0), point(20.0, 0.0), point(40.0, 0.0))
        assertEquals(1, forwardRouteTargetIndex(route, point(12.0, 0.0), 0))
    }

    @Test
    fun skipsPassedWaypointEvenWhenNoGpsFixWasWithinFiveMeters() {
        val route = listOf(point(0.0, 0.0), point(20.0, 0.0), point(40.0, 0.0))
        assertEquals(2, forwardRouteTargetIndex(route, point(29.0, 0.0), 1))
    }

    @Test
    fun advancesPastSeveralCloseWaypointsInOneUpdate() {
        val route = listOf(point(0.0, 0.0), point(2.0, 0.0), point(4.0, 0.0), point(30.0, 0.0))
        assertEquals(3, forwardRouteTargetIndex(route, point(1.0, 0.0), 0))
    }

    @Test
    fun keepsProgressWhenGpsDriftsBackTowardAnEarlierSegment() {
        val route = listOf(point(0.0, 0.0), point(20.0, 0.0), point(40.0, 0.0))
        assertEquals(2, forwardRouteTargetIndex(route, point(8.0, 0.0), 2))
    }

    @Test
    fun keepsEarlierSegmentAtRouteIntersection() {
        val route = listOf(
            point(-20.0, 0.0), point(20.0, 0.0), point(20.0, 20.0),
            point(0.0, 20.0), point(0.0, -20.0)
        )
        assertEquals(1, forwardRouteTargetIndex(route, point(0.0, 0.0), 0))
    }

    @Test
    fun handlesEmptySinglePointAndDuplicatePointRoutes() {
        assertEquals(0, forwardRouteTargetIndex(emptyList(), point(0.0, 0.0), 0))
        assertEquals(0, forwardRouteTargetIndex(listOf(point(0.0, 0.0)), point(0.0, 0.0), 0))
        val route = listOf(point(0.0, 0.0), point(0.0, 0.0), point(20.0, 0.0))
        assertEquals(2, forwardRouteTargetIndex(route, point(0.0, 0.0), 0))
    }

    private fun point(eastMeters: Double, northMeters: Double) = RouteCoordinate(
        latitude = Math.toDegrees(northMeters / 6_371_000.0),
        longitude = Math.toDegrees(eastMeters / 6_371_000.0)
    )
}
