package com.example.dinefinder.ar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkingPointerTargetTest {
    @Test
    fun longApproachTargetsSixMetresAlongTheCurrentPathRatherThanTheRestaurant() {
        val steps = rightTurn()
        val location = point(0.0, 0.0)
        val guidance = guidance(steps, location)

        assertPoint(point(0.0, 6.0), pointer(steps, guidance, location))
        assertEquals(63.0, guidance.metersToStepEnd!!, .01)
    }

    @Test
    fun denseAndDuplicateStraightVerticesDoNotMakeTheTargetTooClose() {
        val points = listOf(0.0, 0.0, 1.0, 2.0, 2.0, 4.0, 10.0, 63.0).map { point(0.0, it) }
        val steps = listOf(step(points), step(listOf(points.last(), point(50.0, 63.0))))
        val location = point(0.0, .2)
        val guidance = guidance(steps, location)

        assertEquals(1, guidance.targetIndex)
        assertPoint(point(0.0, 6.2), pointer(steps, guidance, location))
    }

    @Test
    fun oneMetreBeforeTheTurnStopsAtTheJunctionWithoutCuttingAcrossIt() {
        val steps = rightTurn()
        val location = point(0.0, 62.0)
        val guidance = guidance(steps, location)

        assertEquals(0, guidance.stepIndex)
        assertPoint(point(0.0, 63.0), pointer(steps, guidance, location))
    }

    @Test
    fun staleIncomingGuidanceAtTheCornerCannotSelectTheOutgoingStep() {
        val steps = rightTurn()
        val oldGuidance = guidance(steps, point(0.0, 62.0))
        val corner = point(0.0, 63.0)

        assertPoint(corner, pointer(steps, oldGuidance.copy(metersToStepEnd = 0.0), corner))
    }

    @Test
    fun reachingTheCornerUsesTheOutgoingSegmentAfterWalkingProgressAdvances() {
        val steps = rightTurn()
        val corner = point(0.0, 63.0)
        val guidance = guidance(steps, corner)

        assertEquals(1, guidance.stepIndex)
        assertPoint(point(6.0, 63.0), pointer(steps, guidance, corner))
    }

    @Test
    fun afterTurningContinuesSixMetresAheadOnTheOutgoingLeg() {
        val steps = rightTurn()
        val location = point(5.0, 63.0)
        val guidance = guidance(steps, location)

        assertEquals(1, guidance.stepIndex)
        assertPoint(point(11.0, 63.0), pointer(steps, guidance, location))
    }

    @Test
    fun lateralGpsNoiseProjectsOntoTheActivePathBeforeLookingAhead() {
        val steps = rightTurn()
        val location = point(12.0, 40.0)
        val guidance = guidance(steps, location)

        assertTrue(guidance.onRoute)
        assertPoint(point(0.0, 46.0), pointer(steps, guidance, location))
    }

    @Test
    fun aSharpBendInsideOneProviderStepStopsTheLookahead() {
        val steps = listOf(step(listOf(point(0.0, 0.0), point(0.0, 40.0), point(50.0, 40.0))))
        val location = point(0.0, 37.0)
        val guidance = guidance(steps, location)

        assertEquals(1, guidance.targetIndex)
        assertPoint(point(0.0, 40.0), pointer(steps, guidance, location))

        val after = point(5.0, 40.0)
        val progressed = guidance(steps, after, targetIndex = guidance.targetIndex)
        assertEquals(2, progressed.targetIndex)
        assertPoint(point(11.0, 40.0), pointer(steps, progressed, after))
    }

    @Test
    fun aStraightProviderBoundaryIsStillAnInstructionBoundary() {
        val steps = listOf(
            step(listOf(point(0.0, 0.0), point(0.0, 63.0))),
            step(listOf(point(0.0, 63.0), point(0.0, 100.0)))
        )
        val location = point(0.0, 61.0)
        assertPoint(point(0.0, 63.0), pointer(steps, guidance(steps, location), location))
    }

    @Test
    fun fullPolylineFallbackRetainsItsCornersWhenProviderStepsAreAbsent() {
        val points = listOf(point(0.0, 0.0), point(0.0, 40.0), point(50.0, 40.0))
        val location = point(0.0, 38.0)
        val guidance = walkingStepGuidance(emptyList(), points, location)!!

        assertPoint(point(0.0, 40.0), walkingPointerTarget(emptyList(), points, guidance, location))
    }

    @Test
    fun aSelfCrossingCannotChooseTheLaterNearbyRouteSection() {
        val points = listOf(
            point(-100.0, 0.0), point(100.0, 0.0), point(100.0, 100.0),
            point(0.0, 100.0), point(0.0, -100.0)
        )
        val steps = listOf(step(points))
        val location = point(0.0, 0.0)
        val guidance = guidance(steps, location)

        assertEquals(1, guidance.targetIndex)
        assertPoint(point(6.0, 0.0), pointer(steps, guidance, location))
    }

    @Test
    fun lookaheadInterpolatesAcrossTheDatelineAlongTheShortArc() {
        val start = RouteCoordinate(0.0, 179.9998)
        val end = RouteCoordinate(0.0, -179.9992)
        val steps = listOf(step(listOf(start, end)))
        val location = RouteCoordinate(0.0, 179.99999)
        val guidance = guidance(steps, location)
        val target = pointer(steps, guidance, location)!!

        assertEquals(6.0, routeDistanceMeters(location, target), .001)
        assertEquals(90f, routeBearingDegrees(location, target), .001f)
        assertTrue(target.longitude < -179.999)
        assertTrue(target.longitude >= -180.0)
    }

    @Test
    fun invalidOffRouteOrArrivedGuidanceNeverCreatesAPointer() {
        val steps = rightTurn()
        val location = point(0.0, 0.0)
        val guidance = guidance(steps, location)

        assertNull(pointer(steps, null, location))
        assertNull(pointer(steps, guidance.copy(onRoute = false), location))
        assertNull(pointer(steps, guidance.copy(arrived = true), location))
        assertNull(pointer(steps, guidance, RouteCoordinate(Double.NaN, 0.0)))
        assertNull(pointer(steps, guidance, RouteCoordinate(0.0, 181.0)))
        assertNull(pointer(steps, guidance.copy(target = RouteCoordinate(91.0, 0.0)), location))
        assertNull(pointer(steps, guidance.copy(targetIndex = 0), location))
        assertNull(pointer(steps, guidance.copy(targetIndex = 20), location))
        assertNull(pointer(steps, guidance.copy(target = point(0.0, 30.0)), location))
        assertNull(pointer(steps, guidance.copy(stepIndex = 5), location))
        // A stale onRoute flag cannot approve a newly distant fix.
        assertNull(pointer(steps, guidance, point(30.0, 0.0)))
    }

    @Test
    fun malformedProviderGeometryCannotFallBackToDifferentFullPolylineIndexes() {
        val steps = rightTurn()
        val points = steps.flatMap { it.points }
        val location = point(0.0, 20.0)
        val guidance = guidance(steps, location)
        val malformed = listOf(
            steps.mapIndexed { index, s -> if (index == 1) s.copy(points = emptyList()) else s },
            steps.mapIndexed { index, s -> if (index == 1) s.copy(points = listOf(point(0.0, 63.0), point(0.0, 63.0))) else s },
            steps.mapIndexed { index, s -> if (index == 1) s.copy(points = listOf(point(0.0, 68.0), point(50.0, 63.0))) else s },
            steps.mapIndexed { index, s -> if (index == 0) s.copy(points = listOf(point(0.0, 5.0), point(0.0, 63.0))) else s },
            steps.mapIndexed { index, s -> if (index == 1) s.copy(points = listOf(point(0.0, 63.0), point(55.0, 63.0))) else s },
            steps.mapIndexed { index, s -> if (index == 1) s.copy(points = listOf(point(0.0, 63.0), RouteCoordinate(0.0, Double.POSITIVE_INFINITY))) else s }
        )
        for (badSteps in malformed) {
            assertNull(walkingPointerTarget(badSteps, points, guidance, location))
        }
        assertNull(walkingPointerTarget(steps, emptyList(), guidance, location))
        assertNull(walkingPointerTarget(steps, listOf(location, RouteCoordinate(Double.NaN, 0.0)), guidance, location))
        assertNull(walkingPointerTarget(emptyList(), points, guidance.copy(stepIndex = 1), location))
    }

    @Test
    fun validNearJoinedProviderStepsRetainTheirOwnCurrentEndpoint() {
        val steps = rightTurn().let { listOf(it[0], it[1].copy(
            points = listOf(point(.5, 62.5), point(50.0, 63.0))
        )) }
        val location = point(0.0, 62.0)
        val guidance = guidance(steps, location)

        assertFalse(guidance.arrived)
        assertEquals(0, guidance.stepIndex)
        assertPoint(point(0.0, 63.0), pointer(steps, guidance, location))
    }

    private fun pointer(
        steps: List<WalkingRouteStep>,
        guidance: WalkingStepGuidance?,
        location: RouteCoordinate
    ) = walkingPointerTarget(steps, steps.flatMap { it.points }, guidance, location)

    private fun guidance(
        steps: List<WalkingRouteStep>,
        location: RouteCoordinate,
        targetIndex: Int = 0
    ) = walkingStepGuidance(steps, steps.flatMap { it.points }, location, currentTargetIndex = targetIndex)!!

    private fun rightTurn() = listOf(
        step(listOf(point(0.0, 0.0), point(0.0, 63.0))),
        step(listOf(point(0.0, 63.0), point(50.0, 63.0)))
    )

    private fun step(points: List<RouteCoordinate>) = WalkingRouteStep(points, "Follow the path", null, null)

    private fun assertPoint(expected: RouteCoordinate, actual: RouteCoordinate?) {
        assertTrue("Expected a route target", actual != null)
        assertEquals(0.0, routeDistanceMeters(expected, actual!!), .001)
    }

    private fun point(eastMeters: Double, northMeters: Double) = RouteCoordinate(
        latitude = Math.toDegrees(northMeters / 6_371_000.0),
        longitude = Math.toDegrees(eastMeters / 6_371_000.0)
    )
}
