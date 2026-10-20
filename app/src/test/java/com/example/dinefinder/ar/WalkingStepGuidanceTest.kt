package com.example.dinefinder.ar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkingStepGuidanceTest {
    @Test
    fun lTurnTargetsTheCornerAndUsesProviderInstructionWithRouteDistance() {
        val steps = lTurn()
        val guidance = guidance(steps, point(0.0, 40.0))

        assertEquals(0, guidance.stepIndex)
        assertEquals(point(0.0, 100.0), guidance.target)
        assertEquals("Head north on Campus Road", guidance.instruction)
        assertEquals("DEPART", guidance.maneuver)
        assertEquals(60.0, guidance.metersToStepEnd!!, 0.01)
        assertEquals(0.0, routeBearingDegrees(point(0.0, 40.0), guidance.target).toDouble(), 0.01)
    }

    @Test
    fun approachingTheCornerKeepsTheCurrentSegmentAndInstruction() {
        val guidance = guidance(lTurn(), point(0.0, 95.0), targetIndex = 1)

        assertEquals(0, guidance.stepIndex)
        assertEquals("Head north on Campus Road", guidance.instruction)
        assertEquals(point(0.0, 100.0), guidance.target)
        assertEquals(5.0, guidance.metersToStepEnd!!, 0.01)
        assertEquals(0.0, guidance.bearingDegrees!!.toDouble(), 0.01)
    }

    @Test
    fun reachingTheActualCornerAdvancesToTheProviderTurnInstruction() {
        val guidance = guidance(lTurn(), point(0.0, 100.0), targetIndex = 1)

        assertEquals(1, guidance.stepIndex)
        assertEquals("Turn right onto Food Court Lane", guidance.instruction)
        assertEquals("TURN_RIGHT", guidance.maneuver)
        assertEquals(point(100.0, 100.0), guidance.target)
        assertEquals(100.0, guidance.metersToStepEnd!!, 0.01)
        assertEquals(90.0, guidance.bearingDegrees!!.toDouble(), 0.01)
    }

    @Test
    fun sparseFixBeyondTheCornerAdvancesOnlyToTheImmediateNextStep() {
        val guidance = guidance(lTurn(), point(30.0, 100.0), targetIndex = 1)

        assertEquals(1, guidance.stepIndex)
        assertEquals(70.0, guidance.metersToStepEnd!!, 0.01)
        assertEquals("Turn right onto Food Court Lane", guidance.instruction)
    }

    @Test
    fun firstFixCanJoinTheMiddleOfTheFirstStep() {
        val points = listOf(point(0.0, 0.0), point(100.0, 0.0), point(100.0, 100.0))
        val guidance = guidance(listOf(step(points)), point(100.0, 70.0))

        assertEquals(0, guidance.stepIndex)
        assertEquals(2, guidance.targetIndex)
        assertEquals(point(100.0, 100.0), guidance.target)
        assertEquals(30.0, guidance.metersToStepEnd!!, 0.01)
    }

    @Test
    fun sparseFixPassesSeveralWaypointsInOrder() {
        val points = listOf(point(0.0, 0.0), point(20.0, 0.0), point(40.0, 0.0), point(100.0, 0.0))
        val guidance = guidance(listOf(step(points)), point(75.0, 0.0), targetIndex = 1)

        assertEquals(3, guidance.targetIndex)
        assertEquals(point(100.0, 0.0), guidance.target)
        assertEquals(25.0, guidance.metersToStepEnd!!, 0.01)
    }

    @Test
    fun nearbyShortCornerDoesNotAdvanceBeforeTheProjectedEndpoint() {
        val points = listOf(point(0.0, 0.0), point(10.0, 0.0), point(10.0, 100.0))
        val guidance = guidance(listOf(step(points)), point(4.0, 0.0), targetIndex = 1)

        assertEquals(1, guidance.targetIndex)
        assertEquals(point(10.0, 0.0), guidance.target)
        assertEquals(106.0, guidance.metersToStepEnd!!, 0.01)
        assertEquals(90.0, guidance.bearingDegrees!!.toDouble(), 0.01)
    }

    @Test
    fun denseVerticesAndLateralGpsDriftUseTheRouteTangent() {
        val points = listOf(point(0.0, 0.0), point(0.0, 10.0), point(0.0, 11.0), point(0.0, 30.0))
        val guidance = guidance(listOf(step(points)), point(12.0, 10.1), targetIndex = 1)

        assertEquals(2, guidance.targetIndex)
        assertEquals(point(0.0, 11.0), guidance.target)
        assertEquals(0.0, guidance.bearingDegrees!!.toDouble(), 0.01)
        assertEquals(19.9, guidance.metersToStepEnd!!, 0.01)
    }

    @Test
    fun firstFixBesideDenseGeometryDoesNotPointSidewaysAtANearbyVertex() {
        val points = listOf(point(0.0, 0.0), point(0.0, 10.0), point(0.0, 11.0), point(0.0, 30.0))
        val guidance = guidance(listOf(step(points)), point(12.0, 10.1))

        assertTrue(guidance.onRoute)
        assertEquals(2, guidance.targetIndex)
        assertEquals(0.0, guidance.bearingDegrees!!.toDouble(), 0.01)
    }

    @Test
    fun consecutiveDuplicateVerticesCannotTrapProgressOrCreateAZeroBearing() {
        val points = listOf(
            point(0.0, 0.0), point(0.0, 0.0), point(0.0, 10.0),
            point(0.0, 10.0), point(0.0, 30.0), point(0.0, 30.0)
        )
        val guidance = guidance(listOf(step(points)), point(0.0, 15.0), targetIndex = 1)

        assertEquals(point(0.0, 30.0), guidance.target)
        assertEquals(15.0, guidance.metersToStepEnd!!, 0.01)
        assertEquals(0.0, guidance.bearingDegrees!!.toDouble(), 0.01)
    }

    @Test
    fun sparseFixCanEnterAStepWhoseFirstSegmentIsShortAndThenTurns() {
        val steps = listOf(
            step(listOf(point(0.0, -100.0), point(0.0, 0.0)), "Continue north"),
            step(listOf(point(0.0, 0.0), point(5.0, 0.0), point(5.0, 100.0)), "Turn right")
        )
        val guidance = guidance(steps, point(5.0, 40.0), targetIndex = 1)

        assertEquals(1, guidance.stepIndex)
        assertEquals(point(5.0, 100.0), guidance.target)
        assertEquals("Turn right", guidance.instruction)
        assertEquals(60.0, guidance.metersToStepEnd!!, 0.01)
        assertEquals(0.0, guidance.bearingDegrees!!.toDouble(), 0.01)
    }

    @Test
    fun smallEncodedBoundaryGapAdvancesOnlyAfterEnteringActualNextStep() {
        val steps = listOf(
            step(listOf(point(0.0, 0.0), point(0.0, 100.0)), "Continue north"),
            step(listOf(point(.5, 99.5), point(100.0, 99.5)), "Turn right")
        )
        val beforeCorner = guidance(steps, point(0.0, 94.0), targetIndex = 1)
        assertEquals(0, beforeCorner.stepIndex)
        assertEquals(0.0, beforeCorner.bearingDegrees!!.toDouble(), 0.01)

        val enteredNextStep = guidance(steps, point(10.0, 99.5), targetIndex = 1)
        assertEquals(1, enteredNextStep.stepIndex)
        assertEquals("Turn right", enteredNextStep.instruction)
        assertEquals(90.0, enteredNextStep.bearingDegrees!!.toDouble(), 0.01)
        assertEquals(90.0, enteredNextStep.metersToStepEnd!!, .01)
    }

    @Test
    fun halfMetreGpsBiasAfterTurningCannotKeepThePreviousDirectionVisible() {
        val enteredNextStep = guidance(lTurn(), point(10.0, 99.5), targetIndex = 1)
        assertEquals(1, enteredNextStep.stepIndex)
        assertEquals(90.0, enteredNextStep.bearingDegrees!!.toDouble(), .01)

        val beforeCorner = guidance(lTurn(), point(0.0, 99.5), targetIndex = 1)
        assertEquals(0, beforeCorner.stepIndex)
        assertEquals(0.0, beforeCorner.bearingDegrees!!.toDouble(), .01)
    }

    @Test
    fun fourMetreGpsBiasAtJunctionStillAdvancesAfterMatchingTheOutgoingLeg() {
        val steps = lTurn()
        val beforeCorner = guidance(steps, point(0.0, 96.0), targetIndex = 1)
        assertEquals(0, beforeCorner.stepIndex)
        val atBiasedJunction = guidance(steps, point(4.0, 96.0),
            beforeCorner.stepIndex, beforeCorner.targetIndex)
        assertEquals(0, atBiasedJunction.stepIndex)
        val afterTurning = guidance(steps, point(12.0, 96.0),
            atBiasedJunction.stepIndex, atBiasedJunction.targetIndex)
        assertEquals(1, afterTurning.stepIndex)
        assertEquals("Turn right onto Food Court Lane", afterTurning.instruction)
        assertEquals(88.0, afterTurning.metersToStepEnd!!, .01)
    }

    @Test
    fun junctionGpsToleranceCannotAdvanceAStillBetterMatchingIncomingFix() {
        val steps = lTurn()
        for (location in listOf(point(0.0, 94.0), point(2.0, 94.0), point(4.0, 96.0))) {
            assertEquals(0, guidance(steps, location, targetIndex = 1).stepIndex)
        }
        // A fix too far behind this junction cannot jump to the nearby leg.
        assertEquals(0, guidance(steps, point(15.0, 90.0), targetIndex = 1).stepIndex)
    }

    @Test
    fun denseIncomingVerticesCannotTrapAStepAfterTurningWithFourMetreGpsBias() {
        val corner = point(0.0, 63.0)
        val steps = listOf(
            step(listOf(0.0, 54.0, 56.0, 58.0, 60.0, 62.0, 63.0).map { point(0.0, it) },
                "Head north", "DEPART"),
            step(listOf(corner, point(50.0, 63.0)), "Turn right", "TURN_RIGHT"))
        val approaching = guidance(steps, point(0.0, 59.0))
        assertEquals(0, approaching.stepIndex)
        assertTrue(approaching.targetIndex < steps[0].points.lastIndex)
        val afterTurning = guidance(steps, point(12.0, 59.0), approaching.stepIndex, approaching.targetIndex)
        assertEquals(1, afterTurning.stepIndex)
        assertEquals("Turn right", afterTurning.instruction)
        assertEquals(38.0, afterTurning.metersToStepEnd!!, .01)
    }

    @Test
    fun nearbyReturningNextStepCannotAdvanceBeforeItsInitialDirectionWasEntered() {
        val steps = listOf(
            step(listOf(point(0.0, 0.0), point(0.0, 99.0), point(0.0, 100.0)),
                "Head north", "DEPART"),
            step(listOf(point(0.0, 100.0), point(.5, 100.0), point(.5, 94.0), point(50.0, 94.0)),
                "Turn right", "TURN_RIGHT"))
        val approaching = guidance(steps, point(0.0, 99.0))
        assertEquals(0, approaching.stepIndex)
        val ambiguousFix = guidance(steps, point(2.0, 94.0), approaching.stepIndex, approaching.targetIndex)
        assertEquals(0, ambiguousFix.stepIndex)
        assertEquals("Head north", ambiguousFix.instruction)
        val actualJunction = guidance(steps, point(0.0, 100.0), ambiguousFix.stepIndex, ambiguousFix.targetIndex)
        assertEquals(1, actualJunction.stepIndex)
        assertEquals("Turn right", actualJunction.instruction)
    }

    @Test
    fun laterStepCrossingDoesNotReplaceTheActiveStep() {
        val steps = listOf(
            step(listOf(point(-100.0, 0.0), point(100.0, 0.0)), "Continue east"),
            step(listOf(point(100.0, 0.0), point(100.0, 100.0), point(0.0, 100.0), point(0.0, -100.0)), "Turn left")
        )
        val guidance = guidance(steps, point(0.0, 0.0), targetIndex = 1)

        assertEquals(0, guidance.stepIndex)
        assertEquals("Continue east", guidance.instruction)
        assertEquals(point(100.0, 0.0), guidance.target)
        assertEquals(100.0, guidance.metersToStepEnd!!, 0.01)
    }

    @Test
    fun aSelfCrossingWithinTheFirstStepKeepsTheEarliestPlausibleSegment() {
        val points = listOf(
            point(-100.0, 0.0), point(100.0, 0.0), point(100.0, 100.0),
            point(0.0, 100.0), point(0.0, -100.0)
        )
        val guidance = guidance(listOf(step(points)), point(0.0, 0.0))

        assertEquals(1, guidance.targetIndex)
        assertEquals(point(100.0, 0.0), guidance.target)
        assertEquals(500.0, guidance.metersToStepEnd!!, 0.01)
    }

    @Test
    fun backwardGpsDriftCannotDecreaseEitherProgressIndex() {
        val guidance = guidance(lTurn(), point(0.0, 98.0), stepIndex = 1, targetIndex = 1)

        assertEquals(1, guidance.stepIndex)
        assertEquals(1, guidance.targetIndex)
        assertEquals(point(100.0, 100.0), guidance.target)
    }

    @Test
    fun gpsDeviationDoesNotInventATurnOrJumpToLaterCrossing() {
        val steps = listOf(
            step(listOf(point(0.0, 0.0), point(100.0, 0.0)), "Continue east"),
            step(listOf(point(100.0, 0.0), point(100.0, 60.0), point(-100.0, 60.0)), "Turn left")
        )
        val guidance = guidance(steps, point(0.0, 60.0), targetIndex = 1)

        assertEquals(0, guidance.stepIndex)
        assertEquals(1, guidance.targetIndex)
        assertFalse(guidance.onRoute)
        assertNull(guidance.instruction)
        assertNull(guidance.maneuver)
        assertNull(guidance.metersToStepEnd)
        assertNull(guidance.bearingDegrees)
        assertFalse(guidance.arrived)
    }

    @Test
    fun noProviderStepsStillFollowsGeometryWithoutFabricatedInstructions() {
        val points = listOf(point(0.0, 0.0), point(0.0, 100.0), point(100.0, 100.0))
        val guidance = walkingStepGuidance(emptyList(), points, point(0.0, 50.0))!!

        assertEquals(point(0.0, 100.0), guidance.target)
        assertNull(guidance.instruction)
        assertNull(guidance.maneuver)
        assertEquals(150.0, guidance.metersToStepEnd!!, 0.01)
    }

    @Test
    fun slightGpsBiasAfterACornerInsideOneStepFollowsTheNextSegment() {
        val points = listOf(point(0.0, 0.0), point(0.0, 100.0), point(100.0, 100.0))
        val result = guidance(listOf(step(points)), point(10.0, 99.5), targetIndex = 1)

        assertEquals(2, result.targetIndex)
        assertTrue(result.onRoute)
        assertEquals(90f, result.bearingDegrees!!, 0.01f)
    }

    @Test
    fun fullPolylineFollowsTheBendWithSlightGpsBiasWithoutCuttingAnEarlierCorner() {
        val points = listOf(point(0.0, 0.0), point(0.0, 100.0), point(100.0, 100.0))
        val after = walkingStepGuidance(emptyList(), points, point(10.0, 99.5),
            currentTargetIndex = 1)!!
        assertEquals(2, after.targetIndex)
        assertTrue(after.onRoute)
        assertEquals(90f, after.bearingDegrees!!, 0.01f)

        val before = walkingStepGuidance(emptyList(), points, point(0.0, 94.0),
            currentTargetIndex = 1)!!
        assertEquals(1, before.targetIndex)
        assertEquals(0f, before.bearingDegrees!!, 0.01f)
    }

    @Test
    fun arrivalRequiresTheFinalStepEndpointRatherThanTheRouteStart() {
        val points = listOf(point(0.0, 0.0), point(100.0, 0.0), point(100.0, 100.0), point(0.0, 0.0))
        assertFalse(guidance(listOf(step(points)), point(0.0, 0.0)).arrived)
        assertTrue(guidance(lTurn(), point(97.0, 100.0), stepIndex = 1, targetIndex = 1).arrived)
    }

    @Test
    fun invalidLocationOrMissingGeometryDoesNotProduceGuidance() {
        assertNull(walkingStepGuidance(lTurn(), emptyList(), RouteCoordinate(Double.NaN, 0.0)))
        assertNull(walkingStepGuidance(emptyList(), emptyList(), point(0.0, 0.0)))
        assertNull(walkingStepGuidance(emptyList(), listOf(point(0.0, 0.0)), point(0.0, 0.0)))
    }

    private fun guidance(
        steps: List<WalkingRouteStep>,
        location: RouteCoordinate,
        stepIndex: Int = 0,
        targetIndex: Int = 0
    ) = walkingStepGuidance(steps, steps.flatMap { it.points }, location, stepIndex, targetIndex)!!

    private fun lTurn() = listOf(
        step(listOf(point(0.0, 0.0), point(0.0, 100.0)), "Head north on Campus Road", "DEPART"),
        step(listOf(point(0.0, 100.0), point(100.0, 100.0)), "Turn right onto Food Court Lane", "TURN_RIGHT")
    )

    private fun step(points: List<RouteCoordinate>, instruction: String = "Continue along the route", maneuver: String? = null) =
        WalkingRouteStep(points, instruction, maneuver, null)

    private fun point(eastMeters: Double, northMeters: Double) = RouteCoordinate(
        latitude = Math.toDegrees(northMeters / 6_371_000.0),
        longitude = Math.toDegrees(eastMeters / 6_371_000.0)
    )
}
