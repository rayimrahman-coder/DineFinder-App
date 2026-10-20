package com.example.dinefinder.ar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteManeuverGuidanceTest {
    private val start = point(0.0, 0.0)
    private val firstCorner = point(0.0, 63.0)
    private val secondCorner = point(50.0, 63.0)
    private val end = point(50.0, 95.0)
    private val points = listOf(start, firstCorner, secondCorner, end)
    private val steps = listOf(
        WalkingRouteStep(listOf(start, firstCorner), "Head north", "DEPART", 63),
        WalkingRouteStep(listOf(firstCorner, secondCorner), "Turn right", "TURN_RIGHT", 50),
        WalkingRouteStep(listOf(secondCorner, end), "Turn left", "TURN_LEFT", 32)
    )

    @Test
    fun walkingSequenceContinuesFirstTurnsAtJunctionAndContinuesOnFinalLeg() {
        val sequence = Sequence()
        val initial = sequence.at(start)
        assertForward(initial)
        assertEquals("Turn right", initial.upcomingInstruction)
        assertEquals(63, initial.metersToManeuver)
        assertForward(sequence.at(point(0.0, 54.0)))
        for (metersBefore in listOf(8.0, 3.0, 1.0, .1)) {
            val approach = sequence.at(point(0.0, 63.0 - metersBefore))
            assertEquals("Turn right", approach.instruction)
            assertEquals(90f, approach.directionDegrees)
            assertTrue(approach.isApproachingTurn)
            assertNull(approach.upcomingInstruction)
        }
        val firstTurn = sequence.at(firstCorner)
        assertEquals("Turn right", firstTurn.instruction)
        assertEquals(90f, firstTurn.directionDegrees)
        assertFalse(firstTurn.isStraightPath)
        assertFalse(firstTurn.isApproachingTurn)
        assertEquals("Turn left", firstTurn.upcomingInstruction)
        assertForward(sequence.at(point(10.0, 63.0)))
        val secondTurn = sequence.at(secondCorner)
        assertEquals("Turn left", secondTurn.instruction)
        assertEquals(-90f, secondTurn.directionDegrees)
        assertNull(secondTurn.upcomingInstruction)
        val finalLeg = sequence.at(point(50.0, 75.0))
        assertForward(finalLeg)
        assertNull(finalLeg.upcomingInstruction)
        assertTrue(finalLeg.remainingMeters in 19..21)
    }

    @Test
    fun entryTurnWindowEndsAfterEightOutgoingMetersAndCannotReplayWithGpsJitter() {
        val sequence = Sequence()
        sequence.at(start)
        assertEquals(90f, sequence.at(firstCorner).directionDegrees)
        assertEquals(90f, sequence.at(point(2.0, 63.0)).directionDegrees)
        assertEquals(90f, sequence.at(point(4.0, 63.0)).directionDegrees)
        assertForward(sequence.at(point(8.0, 63.0)))
        assertForward(sequence.at(firstCorner))
        assertForward(sequence.at(point(1.0, 63.0)))
    }

    @Test
    fun incomingGpsDriftDoesNotConsumeATurnWithoutProgressAlongOutgoingStep() {
        val sequence = Sequence()
        sequence.at(start)
        assertEquals(90f, sequence.at(firstCorner).directionDegrees)
        val beforeJunction = sequence.at(point(0.0, 59.0))
        assertEquals("Turn right", beforeJunction.instruction)
        assertEquals(90f, beforeJunction.directionDegrees)
        assertFalse(beforeJunction.isStraightPath)
        assertEquals(90f, sequence.at(firstCorner).directionDegrees)
    }

    @Test
    fun lateralGpsDriftRetainsPendingTurnWithoutConsumingIt() {
        val sequence = Sequence()
        sequence.at(start)
        assertEquals(90f, sequence.at(firstCorner).directionDegrees)
        val lateral = sequence.at(point(0.0, 67.0))
        assertEquals("Turn right", lateral.instruction)
        assertEquals(90f, lateral.directionDegrees)
        assertFalse(lateral.isStraightPath)
        assertEquals(90f, sequence.at(firstCorner).directionDegrees)
    }

    @Test
    fun sparseFixBeyondCornerDoesNotReplayMissedTurn() {
        val sequence = Sequence()
        sequence.at(start)
        assertForward(sequence.at(point(20.0, 63.0)))
        assertForward(sequence.at(firstCorner))
    }

    @Test
    fun temporaryUnavailableStateDoesNotResetCompletedTurns() {
        val tracker = RouteInstructionTracker()
        val afterTurn = walking(point(10.0, 63.0))
        assertForward(resolve(tracker, afterTurn, point(10.0, 63.0))!!)
        assertNull(resolve(tracker, afterTurn, point(10.0, 63.0), state = RouteRequestGate.State.LOADING))
        assertNull(resolve(tracker, afterTurn.copy(onRoute = false), point(10.0, 63.0)))
        val junction = walking(firstCorner, afterTurn)
        assertForward(resolve(tracker, junction, firstCorner)!!)
        tracker.reset()
        assertEquals(90f, resolve(tracker, junction, firstCorner)!!.directionDegrees)
    }

    @Test
    fun routeCompassBearingCannotRotateInstructionSymbols() {
        val tracker = RouteInstructionTracker()
        val active = walking(start)
        val expected = resolve(tracker, active, start)
        assertEquals(expected, resolve(tracker, active.copy(bearingDegrees = 270f), start))
        assertEquals(expected, resolve(tracker, active.copy(bearingDegrees = null), start))
    }

    @Test
    fun unknownEntryManeuverKeepsProviderTextWithoutInventingArrowThenContinues() {
        for (maneuver in listOf(null, "UNKNOWN", "ROUNDABOUT_LEFT", "MERGE")) {
            val modified = steps.toMutableList().apply { this[1] = this[1].copy(maneuver = maneuver) }
            val tracker = RouteInstructionTracker()
            val junction = walking(firstCorner, candidates = modified)
            val turn = resolve(tracker, junction, firstCorner, modified)!!
            assertEquals("Turn right", turn.instruction)
            assertNull(turn.directionDegrees)
            assertFalse(turn.isStraightPath)
            val after = walking(point(10.0, 63.0), junction, modified)
            assertForward(resolve(tracker, after, point(10.0, 63.0), modified)!!)
        }
    }

    @Test
    fun blankNextInstructionNeverSkipsToLaterTurnUsingWrongDistance() {
        val modified = steps.toMutableList().apply { this[1] = this[1].copy(instruction = " ") }
        val result = resolve(RouteInstructionTracker(), walking(start, candidates = modified), start, modified)!!
        assertForward(result)
        assertNull(result.upcomingInstruction)
        assertNull(result.metersToManeuver)
        assertEquals(63, result.remainingMeters)
    }

    @Test
    fun blankEntryInstructionDoesNotInventAnImmediateDirection() {
        val modified = steps.toMutableList().apply { this[1] = this[1].copy(instruction = " ") }
        val result = resolve(RouteInstructionTracker(), walking(firstCorner, candidates = modified), firstCorner, modified)!!
        assertNull(result.instruction)
        assertNull(result.directionDegrees)
        assertFalse(result.isStraightPath)
    }

    @Test
    fun curvedRemainingPathGetsNeutralContinuationRatherThanInventedStraightArrow() {
        val curvedPoints = listOf(start, point(0.0, 20.0), point(20.0, 20.0), point(20.0, 60.0))
        val curved = listOf(WalkingRouteStep(curvedPoints, "Head north", "DEPART", 80))
        val tracker = RouteInstructionTracker()
        val initial = walkingStepGuidance(curved, curvedPoints, start)!!
        val neutral = tracker.guidance(RouteRequestGate.State.READY, curvedPoints, curved, initial, start)!!
        assertNull(neutral.instruction)
        assertNull(neutral.directionDegrees)
        assertFalse(neutral.isStraightPath)
        val afterCurveLocation = point(20.0, 30.0)
        val afterCurve = walkingStepGuidance(curved, curvedPoints, afterCurveLocation, initial.stepIndex, initial.targetIndex)!!
        assertForward(tracker.guidance(RouteRequestGate.State.READY, curvedPoints, curved, afterCurve, afterCurveLocation)!!)
    }

    @Test
    fun pathThatReturnsToItsStartCannotReplayTheEntryTurn() {
        val loop = listOf(firstCorner, point(10.0, 63.0), point(10.0, 73.0), point(.5, 73.0), point(.5, 63.0))
        val loopSteps = listOf(steps[0], steps[1].copy(points = loop))
        val route = listOf(start) + loop
        val guidance = WalkingStepGuidance(1, loop.lastIndex, loop.last(), "Turn right", "TURN_RIGHT", .5, true, false)
        val result = RouteInstructionTracker().guidance(RouteRequestGate.State.READY, route, loopSteps, guidance, point(.5, 63.5))!!
        assertNull(result.instruction)
        assertEquals(0f, result.directionDegrees)
    }

    @Test
    fun missingMalformedOrDisconnectedStepsProvideNeutralPathContinuation() {
        val malformed = steps.toMutableList().apply { this[1] = this[1].copy(points = listOf(firstCorner)) }
        val disconnected = steps.toMutableList().apply {
            this[1] = this[1].copy(points = listOf(point(500.0, 500.0), secondCorner))
        }
        for (candidate in listOf(emptyList(), malformed, disconnected)) {
            val result = resolve(RouteInstructionTracker(), walking(start), start, candidate)!!
            assertNull(result.instruction)
            assertNull(result.directionDegrees)
            assertNull(result.upcomingInstruction)
            assertFalse(result.isStraightPath)
        }
    }

    @Test
    fun unavailableRouteOffPathArrivalAndInvalidDistanceSuppressDirections() {
        val tracker = RouteInstructionTracker()
        val guidance = walking(start)
        for (state in RouteRequestGate.State.entries.filter { it != RouteRequestGate.State.READY }) {
            assertNull(resolve(tracker, guidance, start, state = state))
        }
        assertNull(resolve(tracker, guidance.copy(onRoute = false), start))
        assertNull(resolve(tracker, guidance.copy(arrived = true), start))
        assertNull(resolve(tracker, guidance.copy(metersToStepEnd = null), start))
        assertNull(resolve(tracker, guidance.copy(metersToStepEnd = Double.NaN), start))
        assertNull(resolve(tracker, guidance.copy(metersToStepEnd = -1.0), start))
        assertNull(tracker.guidance(RouteRequestGate.State.READY, emptyList(), steps, guidance, start))
        assertNull(resolve(tracker, guidance, null))
        assertNull(resolve(tracker, null, start))
    }

    @Test
    fun clampedOldStepShowsTurnOnlyAtTheJunctionAndNotForwardPastItsEnd() {
        val oldStep = walking(start).copy(metersToStepEnd = 0.0)
        val tracker = RouteInstructionTracker()
        val atJunction = resolve(tracker, oldStep, firstCorner)!!
        assertEquals("Turn right", atJunction.instruction)
        assertEquals(90f, atJunction.directionDegrees)
        assertNull(atJunction.upcomingInstruction)
        val jitter = resolve(tracker, oldStep, point(0.0, 78.0))!!
        assertEquals(90f, jitter.directionDegrees)
        val beyond = resolve(tracker, oldStep, point(0.0, 82.0))!!
        assertNull(beyond.instruction)
        assertNull(beyond.directionDegrees)
        assertFalse(beyond.isStraightPath)
    }

    @Test
    fun nearJunctionTurnIsClearlyApproachingRatherThanSixtyThreeMetersEarly() {
        val tracker = RouteInstructionTracker()
        assertForward(resolve(tracker, walking(start), start)!!)
        val result = resolve(tracker, walking(point(0.0, 62.9)), point(0.0, 62.9))!!
        assertEquals(90f, result.directionDegrees)
        assertTrue(result.isApproachingTurn)
        assertEquals(1, result.remainingMeters)
    }

    @Test
    fun sparseFixesCannotSkipTheApproachingAndOutgoingTurnWindow() {
        val sequence = Sequence()
        assertForward(sequence.at(start))
        val approach = sequence.at(point(0.0, 59.0))
        assertEquals(90f, approach.directionDegrees)
        assertTrue(approach.isApproachingTurn)
        val afterCorner = sequence.at(point(4.0, 62.5))
        assertEquals(90f, afterCorner.directionDegrees)
        assertFalse(afterCorner.isApproachingTurn)
        assertForward(sequence.at(point(10.0, 62.5)))
    }

    @Test
    fun fourMeterGpsBiasInEitherDirectionStillChangesTurnAndContinuesAlongTheOutgoingLeg() {
        for (bias in listOf(-4.0, 4.0)) {
            val sequence = Sequence()
            assertForward(sequence.at(point(0.0, 45.0)))
            assertEquals(90f, sequence.at(point(0.0, 63.0 + bias)).directionDegrees)
            assertEquals(90f, sequence.at(point(4.0, 63.0 + bias)).directionDegrees)
            assertForward(sequence.at(point(12.0, 63.0 + bias)))
            assertForward(sequence.at(firstCorner))
        }
    }

    @Test
    fun repeatedFrameCallsAndNearbyGpsJitterDoNotConsumeOrFlickerATurn() {
        val sequence = Sequence()
        sequence.at(start)
        val junctionApproach = point(0.0, 57.0)
        repeat(40) {
            assertEquals(90f, sequence.at(junctionApproach).directionDegrees)
        }
        // Retain an armed cue through a modest outward jitter, then release
        // only if the walker retreats well outside the junction area.
        assertEquals(90f, sequence.at(point(0.0, 52.0)).directionDegrees)
        assertForward(sequence.at(point(0.0, 40.0)))
        assertEquals(90f, sequence.at(junctionApproach).directionDegrees)
        repeat(40) { assertEquals(90f, sequence.at(point(4.0, 63.0)).directionDegrees) }
        assertForward(sequence.at(point(12.0, 63.0)))
        repeat(40) { assertForward(sequence.at(firstCorner)) }
    }

    @Test
    fun outgoingProjectionAloneCannotCompleteATurnWhenFixMatchesIncomingLegBetter() {
        val tracker = RouteInstructionTracker()
        val entered = walking(firstCorner)
        assertEquals(90f, resolve(tracker, entered, firstCorner)!!.directionDegrees)
        val ambiguous = entered.copy(metersToStepEnd = 40.0)
        val incomingLocation = point(10.0, 48.0)
        assertEquals(90f, resolve(tracker, ambiguous, incomingLocation)!!.directionDegrees)
    }

    @Test
    fun closeConsecutiveTurnsRetireTheFirstCueAndPrioritizeTheNextJunction() {
        val closeCorner = point(6.0, 63.0)
        val closeEnd = point(6.0, 95.0)
        val closeSteps = listOf(steps[0],
            WalkingRouteStep(listOf(firstCorner, closeCorner), "Turn right", "TURN_RIGHT", 6),
            WalkingRouteStep(listOf(closeCorner, closeEnd), "Turn left", "TURN_LEFT", 32))
        val closePoints = listOf(start, firstCorner, closeCorner, closeEnd)
        val tracker = RouteInstructionTracker()
        var previous: WalkingStepGuidance? = null
        fun at(location: RouteCoordinate): RouteManeuverGuidance {
            val next = walkingStepGuidance(closeSteps, closePoints, location,
                previous?.stepIndex ?: 0, previous?.targetIndex ?: 0)!!
            previous = next
            return tracker.guidance(RouteRequestGate.State.READY, closePoints, closeSteps, next, location)!!
        }
        assertForward(at(start))
        assertEquals(90f, at(firstCorner).directionDegrees)
        val secondApproach = at(point(3.0, 63.0))
        assertEquals(-90f, secondApproach.directionDegrees)
        assertTrue(secondApproach.isApproachingTurn)
        val secondTurn = at(closeCorner)
        assertEquals(-90f, secondTurn.directionDegrees)
        assertFalse(secondTurn.isApproachingTurn)
        assertForward(at(point(6.0, 75.0)))
    }

    @Test
    fun anUpcomingTurnAtALaterRouteCrossingCannotActivateEarly() {
        val crossingPoints = listOf(start, point(0.0, 100.0), point(100.0, 100.0), point(0.0, 0.0))
        val crossingSteps = listOf(
            WalkingRouteStep(crossingPoints.take(2), "Head north", "DEPART", 100),
            WalkingRouteStep(crossingPoints.drop(1), "Turn right", "TURN_RIGHT", 240))
        val current = walkingStepGuidance(crossingSteps, crossingPoints, start)!!
        val result = RouteInstructionTracker().guidance(RouteRequestGate.State.READY, crossingPoints,
            crossingSteps, current, start)!!
        assertForward(result)
        assertEquals(100, result.metersToManeuver)
    }

    @Test
    fun explicitProviderManeuversMapToFixedSymbols() {
        val expected = mapOf("STRAIGHT" to 0f, "TURN_SLIGHT_LEFT" to -45f, "TURN_LEFT" to -90f,
            "TURN_SHARP_LEFT" to -135f, "TURN_SLIGHT_RIGHT" to 45f, "TURN_RIGHT" to 90f,
            "TURN_SHARP_RIGHT" to 135f, "UTURN_LEFT" to -180f, "UTURN_RIGHT" to 180f,
            "RAMP_LEFT" to -45f, "RAMP_RIGHT" to 45f, "FORK_LEFT" to -45f, "FORK_RIGHT" to 45f)
        expected.forEach { (maneuver, angle) -> assertEquals(angle, maneuverPreviewDegrees(maneuver)) }
        assertNull(maneuverPreviewDegrees("UNKNOWN"))
    }

    private inner class Sequence {
        private val tracker = RouteInstructionTracker()
        private var previous: WalkingStepGuidance? = null

        fun at(location: RouteCoordinate): RouteManeuverGuidance {
            val guidance = walking(location, previous)
            previous = guidance
            return resolve(tracker, guidance, location)!!
        }
    }

    private fun walking(location: RouteCoordinate, previous: WalkingStepGuidance? = null, candidates: List<WalkingRouteStep> = steps) =
        walkingStepGuidance(candidates, points, location, previous?.stepIndex ?: 0, previous?.targetIndex ?: 0)!!

    private fun resolve(tracker: RouteInstructionTracker, guidance: WalkingStepGuidance?, location: RouteCoordinate?,
        candidates: List<WalkingRouteStep> = steps, state: RouteRequestGate.State = RouteRequestGate.State.READY) =
        tracker.guidance(state, points, candidates, guidance, location)

    private fun assertForward(guidance: RouteManeuverGuidance) {
        assertNull(guidance.instruction)
        assertEquals(0f, guidance.directionDegrees)
        assertTrue(guidance.isStraightPath)
    }

    private fun point(east: Double, north: Double) = RouteCoordinate(Math.toDegrees(north / 6_371_000.0),
        Math.toDegrees(east / 6_371_000.0))
}
