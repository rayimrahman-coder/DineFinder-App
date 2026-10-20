package com.example.dinefinder.ar

import kotlin.math.abs
import kotlin.math.ceil

/** The instruction to follow now, with a separate preview of the next junction. */
internal data class RouteManeuverGuidance(
    val instruction: String?,
    val directionDegrees: Float?,
    val remainingMeters: Int,
    val isStraightPath: Boolean,
    val upcomingInstruction: String?,
    val metersToManeuver: Int?,
    val isApproachingTurn: Boolean = false
)

/**
 * Google maneuvers describe entry into their own step. Continue along the
 * current geometry until the next junction is close, then retain its maneuver
 * until progress along the outgoing leg confirms that it has been taken.
 * Once passed, GPS jitter cannot replay a completed turn.
 */
internal class RouteInstructionTracker {
    private var completedThroughStep = -1
    private var pendingTurnStep: Int? = null

    fun reset() {
        completedThroughStep = -1
        pendingTurnStep = null
    }

    fun guidance(
        state: RouteRequestGate.State,
        routePoints: List<RouteCoordinate>,
        steps: List<WalkingRouteStep>,
        guidance: WalkingStepGuidance?,
        location: RouteCoordinate?
    ): RouteManeuverGuidance? {
        if (state != RouteRequestGate.State.READY || guidance == null || !guidance.onRoute || guidance.arrived ||
            location?.maneuverCoordinateIsValid() != true || !routePoints.maneuverGeometryIsValid() ||
            !guidance.target.maneuverCoordinateIsValid()) return null
        val remaining = guidance.metersToStepEnd?.takeIf { it.isFinite() && it >= 0.0 && it <= Int.MAX_VALUE }
            ?: return null
        val remainingMeters = ceil(remaining).toInt()
        val unverified = RouteManeuverGuidance(null, null, remainingMeters, false, null, null)
        // A whole-path fallback cannot address provider step instructions.
        if (steps.isEmpty() || steps.any { !it.points.maneuverGeometryIsValid() } ||
            steps.zipWithNext().any { (a, b) -> routeDistanceMeters(a.points.last(), b.points.first()) > STEP_JOIN_METERS } ||
            routeDistanceMeters(steps.first().points.first(), routePoints.first()) > STEP_JOIN_METERS ||
            routeDistanceMeters(steps.last().points.last(), routePoints.last()) > STEP_JOIN_METERS ||
            guidance.stepIndex !in steps.indices) return unverified

        val activeIndex = guidance.stepIndex
        val active = steps[activeIndex]
        val activePoints = active.points.distinctManeuverPoints()
        val targetIndex = guidance.targetIndex
        if (targetIndex !in 1..activePoints.lastIndex ||
            routeDistanceMeters(guidance.target, activePoints[targetIndex]) > STEP_JOIN_METERS) return unverified
        val stepLength = activePoints.zipWithNext().sumOf { (a, b) -> routeDistanceMeters(a, b) }
        if (remaining > stepLength + DISTANCE_EPSILON_METERS) return unverified

        completedThroughStep = maxOf(completedThroughStep, activeIndex - 1)
        if (pendingTurnStep?.let { it < activeIndex || it <= completedThroughStep } == true) pendingTurnStep = null
        val covered = (stepLength - remaining).coerceAtLeast(0.0)

        val next = steps.getOrNull(activeIndex + 1)
        val upcoming = next?.instruction?.trim()?.takeIf { it.isNotEmpty() }
        val continuation = RouteManeuverGuidance(
            instruction = null,
            directionDegrees = null,
            remainingMeters = remainingMeters,
            isStraightPath = false,
            upcomingInstruction = upcoming,
            metersToManeuver = remainingMeters.takeIf { upcoming != null }
        )
        if (activeIndex > completedThroughStep && active.maneuver.isEntryManeuver(activeIndex)) {
            // A shorter leg must release its entry cue in time for its own
            // next junction. Long legs require eight metres of outgoing travel.
            val exitMeters = minOf(TURN_EXIT_METERS, stepLength / 2.0)
            val incoming = steps.getOrNull(activeIndex - 1)?.points?.distinctManeuverPoints()
            val outgoingDistance = maneuverSegmentDistance(activePoints[targetIndex - 1], activePoints[targetIndex], location)
            val incomingDistance = incoming?.let {
                maneuverSegmentDistance(it[it.lastIndex - 1], it.last(), location)
            }
            val outgoingMatched = incomingDistance == null ||
                outgoingDistance <= incomingDistance + DISTANCE_EPSILON_METERS
            val turnTaken = covered + DISTANCE_EPSILON_METERS >= exitMeters && outgoingMatched
            if (turnTaken) {
                completedThroughStep = maxOf(completedThroughStep, activeIndex)
                if (pendingTurnStep == activeIndex) pendingTurnStep = null
            } else {
                val atEntry = covered <= TURN_ENTRY_METERS + DISTANCE_EPSILON_METERS &&
                    routeDistanceMeters(location, activePoints.first()) <= TURN_ENTRY_METERS + DISTANCE_EPSILON_METERS
                if (pendingTurnStep == activeIndex || atEntry) {
                    pendingTurnStep = activeIndex
                    return continuation.withManeuver(active)
                }
                // A sideward fix is not evidence that the entry maneuver was
                // completed, and must not replace it with a straight command.
                return continuation
            }
        }

        if (next != null && activeIndex + 1 > completedThroughStep && next.maneuver.isEntryManeuver(activeIndex + 1)) {
            val junctionDistance = maxOf(routeDistanceMeters(location, activePoints.last()),
                routeDistanceMeters(location, next.points.first()))
            val alreadyPending = pendingTurnStep == activeIndex + 1
            val window = if (alreadyPending) TURN_RELEASE_METERS else TURN_ENTRY_METERS
            if (remaining <= window + DISTANCE_EPSILON_METERS && junctionDistance <= window + DISTANCE_EPSILON_METERS) {
                pendingTurnStep = activeIndex + 1
                return continuation.withManeuver(next).copy(
                    upcomingInstruction = null,
                    metersToManeuver = null,
                    isApproachingTurn = true
                )
            }
            if (alreadyPending) pendingTurnStep = null
        }

        // Do not direct a walker farther along a clamped segment after its end.
        val beyondEnd = remaining <= DISTANCE_EPSILON_METERS &&
            routeDistanceMeters(location, activePoints.last()) > TURN_ENTRY_METERS + DISTANCE_EPSILON_METERS
        val straight = !beyondEnd && activePoints.drop(targetIndex - 1).isStraightManeuverPath()
        return continuation.copy(directionDegrees = 0f.takeIf { straight }, isStraightPath = straight)
    }
}

private fun RouteManeuverGuidance.withManeuver(step: WalkingRouteStep): RouteManeuverGuidance = copy(
    instruction = step.instruction.trim().takeIf { it.isNotEmpty() },
    directionDegrees = step.instruction.takeIf { it.isNotBlank() }?.let { maneuverPreviewDegrees(step.maneuver) }
)

/** Local metre coordinates are sufficient for comparing the two legs at a junction. */
private fun maneuverSegmentDistance(start: RouteCoordinate, end: RouteCoordinate, location: RouteCoordinate): Double {
    val longitudeScale = kotlin.math.cos(Math.toRadians(location.latitude))
    fun x(point: RouteCoordinate) = Math.toRadians(((point.longitude - location.longitude + 540.0) % 360.0) - 180.0) * longitudeScale
    fun y(point: RouteCoordinate) = Math.toRadians(point.latitude - location.latitude)
    val startX = x(start)
    val startY = y(start)
    val dx = x(end) - startX
    val dy = y(end) - startY
    val lengthSquared = dx * dx + dy * dy
    val fraction = if (lengthSquared <= 0.0) 0.0 else (-(startX * dx + startY * dy) / lengthSquared).coerceIn(0.0, 1.0)
    return kotlin.math.hypot(startX + fraction * dx, startY + fraction * dy) * 6_371_000.0
}

/** Fixed maneuver symbols; an unrecognised maneuver never invents a direction. */
internal fun maneuverPreviewDegrees(maneuver: String?): Float? = when (maneuver) {
    "STRAIGHT" -> 0f
    "TURN_SLIGHT_LEFT", "RAMP_LEFT", "FORK_LEFT" -> -45f
    "TURN_LEFT" -> -90f
    "TURN_SHARP_LEFT" -> -135f
    "TURN_SLIGHT_RIGHT", "RAMP_RIGHT", "FORK_RIGHT" -> 45f
    "TURN_RIGHT" -> 90f
    "TURN_SHARP_RIGHT" -> 135f
    "UTURN_LEFT" -> -180f
    "UTURN_RIGHT" -> 180f
    else -> null
}

private fun String?.isEntryManeuver(stepIndex: Int) = this != "DEPART" && this != "STRAIGHT" &&
    (this != null || stepIndex > 0)

private fun List<RouteCoordinate>.isStraightManeuverPath(): Boolean {
    val bearings = zipWithNext().map { (a, b) -> routeBearingDegrees(a, b).toDouble() }
    return bearings.isNotEmpty() && bearings.zipWithNext().sumOf { (a, b) ->
        abs(((b - a + 540.0) % 360.0) - 180.0)
    } <= STRAIGHT_PATH_DEGREES
}

private fun List<RouteCoordinate>.distinctManeuverPoints(): List<RouteCoordinate> = buildList {
    for (point in this@distinctManeuverPoints) {
        if (isEmpty() || routeDistanceMeters(last(), point) > DUPLICATE_METERS) add(point)
    }
}

private fun RouteCoordinate.maneuverCoordinateIsValid() = latitude.isFinite() && longitude.isFinite() &&
    latitude in -90.0..90.0 && longitude in -180.0..180.0

private fun List<RouteCoordinate>.maneuverGeometryIsValid() = size >= 2 &&
    all { it.maneuverCoordinateIsValid() } && zipWithNext().any { (a, b) -> routeDistanceMeters(a, b) > .1 }

private const val TURN_ENTRY_METERS = 8.0
private const val TURN_EXIT_METERS = 8.0
private const val TURN_RELEASE_METERS = 16.0
private const val STEP_JOIN_METERS = 2.0
private const val DUPLICATE_METERS = .01
private const val DISTANCE_EPSILON_METERS = .000001
private const val STRAIGHT_PATH_DEGREES = 15.0
