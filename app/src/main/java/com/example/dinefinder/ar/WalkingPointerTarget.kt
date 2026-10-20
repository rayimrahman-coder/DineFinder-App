package com.example.dinefinder.ar

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot

/**
 * A short camera-pointer target on the currently selected walking segment.
 *
 * Projecting onto that segment keeps lateral GPS noise from choosing a nearby
 * later section of the route. The lookahead can pass dense straight vertices,
 * but stops at a bend or a provider step boundary. Only walkingStepGuidance may
 * advance the active segment or step; this helper never shortcuts a corner.
 */
internal fun walkingPointerTarget(
    steps: List<WalkingRouteStep>,
    routePoints: List<RouteCoordinate>,
    guidance: WalkingStepGuidance?,
    location: RouteCoordinate
): RouteCoordinate? {
    if (guidance == null || !guidance.onRoute || guidance.arrived ||
        !location.pointerCoordinateIsValid() || !guidance.target.pointerCoordinateIsValid() ||
        !routePoints.pointerGeometryIsValid()) return null

    val points = if (steps.isEmpty()) {
        if (guidance.stepIndex != 0) return null
        routePoints.distinctPointerPoints()
    } else {
        // A malformed set of provider steps must not silently select unrelated
        // full-route indexes or point directly at the restaurant instead.
        if (steps.any { !it.points.pointerGeometryIsValid() } ||
            steps.zipWithNext().any { (a, b) ->
                routeDistanceMeters(a.points.last(), b.points.first()) > POINTER_STEP_JOIN_METERS
            } ||
            routeDistanceMeters(steps.first().points.first(), routePoints.first()) > POINTER_STEP_JOIN_METERS ||
            routeDistanceMeters(steps.last().points.last(), routePoints.last()) > POINTER_STEP_JOIN_METERS ||
            guidance.stepIndex !in steps.indices) return null
        steps[guidance.stepIndex].points.distinctPointerPoints()
    }
    val targetIndex = guidance.targetIndex
    if (targetIndex !in 1..points.lastIndex ||
        routeDistanceMeters(guidance.target, points[targetIndex]) > POINTER_STEP_JOIN_METERS) return null

    val start = points[targetIndex - 1]
    val end = points[targetIndex]
    val projection = pointerSegmentProjection(start, end, location)
    if (projection.distanceMeters > POINTER_ROUTE_CORRIDOR_METERS) return null

    var segmentEndIndex = targetIndex
    var segmentStart = pointerInterpolate(start, end, projection.fraction)
    var availableMeters = POINTER_LOOKAHEAD_METERS
    var previousBearing = routeBearingDegrees(start, end).toDouble()
    var cumulativeBendDegrees = 0.0
    while (true) {
        val segmentEnd = points[segmentEndIndex]
        val remainingMeters = routeDistanceMeters(segmentStart, segmentEnd)
        if (remainingMeters >= availableMeters) {
            return pointerInterpolate(segmentStart, segmentEnd, availableMeters / remainingMeters)
        }
        availableMeters -= remainingMeters
        if (segmentEndIndex == points.lastIndex) return segmentEnd

        val nextBearing = routeBearingDegrees(segmentEnd, points[segmentEndIndex + 1]).toDouble()
        cumulativeBendDegrees += abs(pointerLongitudeDelta(previousBearing, nextBearing))
        if (cumulativeBendDegrees > POINTER_BEND_DEGREES) return segmentEnd

        segmentStart = segmentEnd
        segmentEndIndex++
        previousBearing = nextBearing
    }
}

private data class PointerSegmentProjection(val fraction: Double, val distanceMeters: Double)

private fun pointerSegmentProjection(
    start: RouteCoordinate,
    end: RouteCoordinate,
    location: RouteCoordinate
): PointerSegmentProjection {
    val longitudeScale = cos(Math.toRadians(location.latitude))
    val endX = Math.toRadians(pointerLongitudeDelta(start.longitude, end.longitude)) *
        POINTER_EARTH_RADIUS_METERS * longitudeScale
    val endY = Math.toRadians(end.latitude - start.latitude) * POINTER_EARTH_RADIUS_METERS
    val locationX = Math.toRadians(pointerLongitudeDelta(start.longitude, location.longitude)) *
        POINTER_EARTH_RADIUS_METERS * longitudeScale
    val locationY = Math.toRadians(location.latitude - start.latitude) * POINTER_EARTH_RADIUS_METERS
    val lengthSquared = endX * endX + endY * endY
    val fraction = if (lengthSquared == 0.0) 0.0 else
        ((locationX * endX + locationY * endY) / lengthSquared).coerceIn(0.0, 1.0)
    return PointerSegmentProjection(
        fraction,
        hypot(locationX - fraction * endX, locationY - fraction * endY)
    )
}

private fun pointerInterpolate(start: RouteCoordinate, end: RouteCoordinate, fraction: Double): RouteCoordinate =
    RouteCoordinate(
        latitude = start.latitude + (end.latitude - start.latitude) * fraction,
        longitude = pointerLongitudeDelta(0.0,
            start.longitude + pointerLongitudeDelta(start.longitude, end.longitude) * fraction)
    )

private fun List<RouteCoordinate>.distinctPointerPoints(): List<RouteCoordinate> = buildList {
    for (point in this@distinctPointerPoints) {
        if (isEmpty() || routeDistanceMeters(last(), point) > POINTER_DUPLICATE_METERS) add(point)
    }
}

private fun RouteCoordinate.pointerCoordinateIsValid() = latitude.isFinite() && longitude.isFinite() &&
    latitude in -90.0..90.0 && longitude in -180.0..180.0

private fun List<RouteCoordinate>.pointerGeometryIsValid() = size >= 2 &&
    all { it.pointerCoordinateIsValid() } && zipWithNext().any { (a, b) -> routeDistanceMeters(a, b) > .1 }

private fun pointerLongitudeDelta(from: Double, to: Double) = ((to - from + 540.0) % 360.0) - 180.0

private const val POINTER_EARTH_RADIUS_METERS = 6_371_000.0
private const val POINTER_LOOKAHEAD_METERS = 6.0
private const val POINTER_STEP_JOIN_METERS = 2.0
private const val POINTER_DUPLICATE_METERS = .01
private const val POINTER_ROUTE_CORRIDOR_METERS = 20.0
private const val POINTER_BEND_DEGREES = 15.0
