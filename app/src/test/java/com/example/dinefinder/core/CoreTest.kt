package com.example.dinefinder.core

import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CoreTest {
    private val cornerRoute = RoutePlan(
        points = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.001), GeoPoint(0.001, 0.001)),
        instructions = listOf(
            RouteInstruction(GeoPoint(0.0, 0.0), "Head east", 111.195),
            RouteInstruction(GeoPoint(0.0, 0.001), "Turn left", 111.195),
            RouteInstruction(GeoPoint(0.001, 0.001), "Arrive", 0.0),
        ),
        distanceMeters = 222.39,
        durationSeconds = 180.0,
    )

    @Test fun cardinalDistancesAndBearings() {
        val origin = GeoPoint(0.0, 0.0)
        assertEquals(111.195, distanceMeters(origin, GeoPoint(0.001, 0.0)), 0.01)
        assertEquals(111.195, distanceMeters(origin, GeoPoint(0.0, 0.001)), 0.01)
        assertEquals(0.0, bearingDegrees(origin, GeoPoint(0.001, 0.0)), 0.0001)
        assertEquals(90.0, bearingDegrees(origin, GeoPoint(0.0, 0.001)), 0.0001)
        assertEquals(0.0, distanceMeters(origin, origin), 0.0)
    }

    @Test fun antimeridianUsesShortPath() {
        val west = GeoPoint(0.0, 179.999)
        val east = GeoPoint(0.0, -179.999)
        assertEquals(222.39, distanceMeters(west, east), 0.02)
        assertEquals(90.0, bearingDegrees(west, east), 0.001)
    }

    @Test fun headingsWrapNorth() {
        assertEquals(10.0, headingDelta(5.0, 355.0), 0.0)
        assertEquals(-10.0, headingDelta(355.0, 5.0), 0.0)
        assertEquals(-180.0, headingDelta(90.0, 270.0), 0.0)
        assertEquals(315.0, normalizeAngle(-45.0), 0.0)
        rejects { normalizeAngle(Double.NaN) }
    }

    @Test fun cameraMarkersOnlyAppearInFront() {
        val middle = projectMarker(2.0, 358.0)
        assertTrue(middle.isVisible)
        assertTrue(middle.horizontal > 0.0)
        assertFalse(projectMarker(180.0, 0.0).isVisible)
        assertEquals(-1.0, projectMarker(180.0, 0.0).horizontal, 0.0)
        rejects { projectMarker(0.0, 0.0, fieldOfView = 180.0) }
    }

    @Test fun oneKilometreSearchFiltersAndOrdersFixtures() {
        val nearby = DemoData.nearby(DemoData.origin)
        assertEquals(6, nearby.size)
        val distances = nearby.map { distanceMeters(DemoData.origin, it.location) }
        assertEquals(distances.sorted(), distances)
        assertTrue(distances.all { it <= 1000.0 })
        assertEquals(listOf("Bamboo & Bowl"), DemoData.nearby(DemoData.origin, 250.0).map { it.name })
        assertTrue(DemoData.nearby(GeoPoint(0.0, 0.0)).isEmpty())
    }

    @Test fun oneKilometreBoundaryDoesNotIncludeMoreDistantPlaces() {
        val place = DemoData.restaurants.first()
        fun northBy(meters: Double) = GeoPoint(
            place.location.latitude + meters / 6_371_008.8 * 180.0 / PI,
            place.location.longitude,
        )
        assertTrue(DemoData.nearby(northBy(999.0)).any { it.id == place.id })
        assertFalse(DemoData.nearby(northBy(1001.0)).any { it.id == place.id })
    }

    @Test fun invalidFixesAndSearchRadiiAreRejected() {
        rejects { GeoPoint(91.0, 0.0) }
        rejects { GeoPoint(0.0, -181.0) }
        rejects { GeoPoint(Double.NaN, 0.0) }
        rejects { GeoPoint(0.0, Double.POSITIVE_INFINITY) }
        rejects { DemoData.nearby(DemoData.origin, 0.0) }
        rejects { DemoData.nearby(DemoData.origin, 10_000.0) }
        rejects { DemoData.nearby(DemoData.origin, Double.NaN) }
    }

    @Test fun routeMetricsAndInstructionsMustBeValid() {
        rejects { RoutePlan(emptyList(), emptyList(), 0.0, 0.0) }
        rejects { RoutePlan(cornerRoute.points, emptyList(), -1.0, 1.0) }
        rejects { RoutePlan(cornerRoute.points, emptyList(), 1.0, Double.NaN) }
        rejects { RouteInstruction(GeoPoint(0.0, 0.0), "", 0.0) }
        rejects { RouteInstruction(GeoPoint(0.0, 0.0), "Continue", -1.0) }
    }

    @Test fun routeCopiesCallerOwnedLists() {
        val points = cornerRoute.points.toMutableList()
        val instructions = cornerRoute.instructions.toMutableList()
        val plan = RoutePlan(points, instructions, 222.39, 180.0)
        points.clear()
        instructions.clear()
        assertEquals(3, plan.points.size)
        assertEquals(3, plan.instructions.size)
    }

    @Test fun remainingDistanceFollowsTheBendInsteadOfTakingAShortcut() {
        val progress = nearestRouteProgress(GeoPoint(0.0, 0.0005), cornerRoute)
        assertEquals(166.793, progress.remainingMeters, 0.02)
        assertEquals(0.0, progress.distanceFromRouteMeters, 0.01)
        assertEquals(GeoPoint(0.0, 0.001), progress.targetPoint)
        assertEquals("Head east", progress.instruction?.text)
        assertFalse(progress.hasArrived)
    }

    @Test fun currentInstructionChangesAfterTheJunction() {
        val progress = nearestRouteProgress(GeoPoint(0.0005, 0.001), cornerRoute)
        assertEquals("Turn left", progress.instruction?.text)
        assertEquals("Arrive", nearestRouteProgress(cornerRoute.points.last(), cornerRoute).instruction?.text)
    }

    @Test fun deviationIsFlaggedWithoutInventingANewPath() {
        val progress = nearestRouteProgress(GeoPoint(-0.001, 0.0005), cornerRoute)
        assertTrue(progress.isOffRoute)
        assertEquals(111.195, progress.distanceFromRouteMeters, 0.02)
        assertEquals(3, cornerRoute.points.size)
        rejects { nearestRouteProgress(DemoData.origin, cornerRoute, offRouteThreshold = Double.NaN) }
    }

    @Test fun arrivalNeedsTheRouteEndpoint() {
        val arrived = nearestRouteProgress(GeoPoint(0.001, 0.001), cornerRoute)
        assertTrue(arrived.hasArrived)
        assertEquals(0.0, arrived.remainingMeters, 0.0)
        assertFalse(nearestRouteProgress(GeoPoint(0.0005, 0.001), cornerRoute).hasArrived)
    }

    @Test fun demoRoutesAreExplicitlySyntheticIncludingZeroDistance() {
        val route = DemoData.route(DemoData.origin, DemoData.restaurants.first().location)
        assertTrue(route.isDemo)
        assertTrue(route.points.size > 2)
        assertEquals(DemoData.origin, route.points.first())
        assertEquals(DemoData.restaurants.first().location, route.points.last())
        val zero = DemoData.route(DemoData.origin, DemoData.origin)
        assertEquals(0.0, zero.distanceMeters, 0.0)
        assertTrue(nearestRouteProgress(DemoData.origin, zero).hasArrived)
    }

    @Test fun demoMovementFollowsPathAndWrapsTheAntimeridian() {
        assertEquals(cornerRoute.points.first(), DemoData.pointOnRoute(cornerRoute, 0.0))
        assertEquals(cornerRoute.points.last(), DemoData.pointOnRoute(cornerRoute, 1.0))
        val halfway = DemoData.pointOnRoute(cornerRoute, 0.5)
        assertEquals(0.0, halfway.latitude, 0.00001)
        assertEquals(0.001, halfway.longitude, 0.00001)
        val crossing = RoutePlan(
            listOf(GeoPoint(0.0, 179.999), GeoPoint(0.0, -179.999)),
            emptyList(), 222.39, 180.0,
        )
        assertEquals(-180.0, DemoData.pointOnRoute(crossing, 0.5).longitude, 0.00001)
        rejects { DemoData.pointOnRoute(cornerRoute, Double.NaN) }
    }

    private fun rejects(block: () -> Unit) {
        try {
            block()
            fail("Expected an IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected invalid input rejection.
        }
    }
}
