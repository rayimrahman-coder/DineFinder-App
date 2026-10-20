package com.example.dinefinder.ar

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

internal data class WalkingStepGuidance(
    val stepIndex: Int,
    val targetIndex: Int,
    val target: RouteCoordinate,
    val instruction: String?,
    val maneuver: String?,
    val metersToStepEnd: Double?,
    val onRoute: Boolean,
    val arrived: Boolean,
    val bearingDegrees: Float? = null
)

/**
 * Follows ordered route geometry. A target index of zero means the route has not
 * been joined yet; carry both returned indexes into subsequent location fixes.
 */
internal fun walkingStepGuidance(
    steps: List<WalkingRouteStep>,
    routePoints: List<RouteCoordinate>,
    location: RouteCoordinate,
    currentStepIndex: Int = 0,
    currentTargetIndex: Int = 0
): WalkingStepGuidance? {
    if (!location.routeCoordinateIsValid()) return null
    val providerSteps = steps.takeIf { candidate ->
        candidate.isNotEmpty() && candidate.all { it.points.routeGeometryIsValid() &&
            it.points.routeDistinctPoints().size >= 2 }
    }
    val geometry = providerSteps?.map { it.points.routeDistinctPoints() }
        ?: listOf(routePoints.takeIf { it.routeGeometryIsValid() }?.routeDistinctPoints()
            ?.takeIf { it.size >= 2 } ?: return null)
    var stepIndex = currentStepIndex.coerceIn(0, geometry.lastIndex)
    var targetIndex = currentTargetIndex.coerceIn(0, geometry[stepIndex].lastIndex)

    while (true) {
        val points = geometry[stepIndex]
        val progress = routeStepProgress(points, location, targetIndex)
        targetIndex = progress.targetIndex
        val finalSegmentSelected = targetIndex == points.lastIndex
        val endpointReached = finalSegmentSelected &&
            routeDistanceMeters(location, points.last()) <= ROUTE_VERTEX_METERS &&
            progress.projection.rawFraction >= 1.0 - ROUTE_PROJECTION_EPSILON
        val nearJunction = progress.onRoute && progress.targetIndex > 0 &&
            routeStepDistanceRemaining(points, progress) <= ROUTE_JUNCTION_GPS_TOLERANCE_METERS
        if (stepIndex == geometry.lastIndex || (!finalSegmentSelected && !nearJunction)) break

        val nextPoints = geometry[stepIndex + 1]
        val nextProgress = routeStepProgress(nextPoints, location, 0)
        val nextFirst = routeSegmentProjection(nextPoints[0], nextPoints[1], location)
        val joinDistance = routeDistanceMeters(points.last(), nextPoints.first())
        // Encoded step endpoints can differ slightly. Use the actual next
        // start projected onto this segment, rather than trapping a walker
        // behind the preceding endpoint after they have entered the next step.
        val nextStart = routeSegmentProjection(points[points.lastIndex - 1], points.last(), nextPoints.first())
        val joinFraction = minOf(1.0, nextStart.rawFraction)
        // A biased fix can remain on an earlier dense incoming vertex. Check
        // its position against the actual junction tangent rather than treating
        // that internal vertex as the end of this provider step.
        val junctionProjection = if (finalSegmentSelected) progress.projection else
            routeSegmentProjection(points[points.lastIndex - 1], points.last(), location)
        // Ordinary GPS bias may keep the fix behind the previous endpoint.
        // The bounded junction tolerance only applies after entering and
        // better matching the immediately following step.
        val joinPositionTolerance = ROUTE_JUNCTION_GPS_TOLERANCE_METERS / maxOf(1.0, junctionProjection.lengthMeters)
        val strictPositionTolerance = ROUTE_VERTEX_METERS / maxOf(1.0, junctionProjection.lengthMeters)
        val passedStrictJunction = junctionProjection.rawFraction >=
            joinFraction - strictPositionTolerance - ROUTE_PROJECTION_EPSILON
        // Before the strict endpoint position, a nearby returning portion of
        // the next step is ambiguous. Require evidence in its initial direction
        // instead of joining whichever later segment happens to be closest.
        val enteredInitialDirection = nextFirst.rawFraction * nextFirst.lengthMeters + ROUTE_PROJECTION_EPSILON >=
            nextFirst.perpendicularDistanceMeters
        // A nearby later intersection cannot advance the active step. To pass a
        // missed corner, the fix must have passed this final segment and joined
        // the immediately following step in its expected initial direction.
        val enteredNextStep = nextProgress.onRoute &&
            joinDistance <= ROUTE_STEP_JOIN_METERS &&
            junctionProjection.rawFraction >= joinFraction - joinPositionTolerance - ROUTE_PROJECTION_EPSILON &&
            (passedStrictJunction || enteredInitialDirection) &&
            nextFirst.rawFraction > 0.0 &&
            routeStepDistanceCovered(nextPoints, nextProgress) >= ROUTE_VERTEX_METERS &&
            nextProgress.projection.distanceMeters <
                progress.projection.distanceMeters - ROUTE_PROJECTION_EPSILON
        if (!endpointReached && !enteredNextStep) break
        stepIndex++
        targetIndex = 0
    }

    val points = geometry[stepIndex]
    val progress = routeStepProgress(points, location, targetIndex)
    val activeStep = providerSteps?.get(stepIndex)
    val remainingMeters = if (progress.onRoute) {
        routeStepDistanceRemaining(points, progress)
    } else null
    return WalkingStepGuidance(
        stepIndex = stepIndex,
        targetIndex = progress.targetIndex,
        target = points[maxOf(1, progress.targetIndex)],
        instruction = activeStep?.instruction?.takeIf { progress.onRoute && it.isNotBlank() },
        maneuver = activeStep?.maneuver?.takeIf { progress.onRoute },
        metersToStepEnd = remainingMeters,
        onRoute = progress.onRoute,
        arrived = progress.onRoute && stepIndex == geometry.lastIndex &&
            progress.targetIndex == points.lastIndex &&
            routeDistanceMeters(location, points.last()) <= ROUTE_REACHED_METERS,
        // The route tangent remains stable when GPS drifts beside a dense
        // polyline. Bearing from a raw fix to a close vertex can point backward.
        bearingDegrees = if (progress.onRoute) routeBearingDegrees(
            points[progress.targetIndex - 1], points[progress.targetIndex]
        ) else null
    )
}

private data class RouteSegmentProjection(
    val rawFraction: Double,
    val fraction: Double,
    val distanceMeters: Double,
    val lengthMeters: Double,
    val perpendicularDistanceMeters: Double
)

private data class RouteStepProgress(
    val targetIndex: Int,
    val projection: RouteSegmentProjection,
    val onRoute: Boolean
)

private fun routeStepProgress(
    points: List<RouteCoordinate>,
    location: RouteCoordinate,
    currentTargetIndex: Int
): RouteStepProgress {
    var targetIndex = maxOf(1, currentTargetIndex).coerceAtMost(points.lastIndex)
    val projections = (1..points.lastIndex).map { index ->
        routeSegmentProjection(points[index - 1], points[index], location)
    }
    val lastNearbySegment = projections.indexOfLast { it.distanceMeters <= ROUTE_CORRIDOR_METERS }
    if (currentTargetIndex == 0) {
        // Join the earliest plausible segment, preserving route order where a
        // route crosses itself. Never choose a globally nearest later crossing.
        val firstNearby = projections.indexOfFirst { it.distanceMeters <= ROUTE_CORRIDOR_METERS }
        if (firstNearby < 0) return RouteStepProgress(0, projections[0], false)
        targetIndex = firstNearby + 1
    }
    while (targetIndex < points.lastIndex) {
        val current = projections[targetIndex - 1]
        val next = projections[targetIndex]
        val endpointReached = routeDistanceMeters(location, points[targetIndex]) <= ROUTE_VERTEX_METERS &&
            current.rawFraction >= 1.0 - ROUTE_PROJECTION_EPSILON
        val remainingRouteNearby = lastNearbySegment >= targetIndex
        // Consecutive projection checks allow sparse fixes to pass several
        // vertices, but require evidence of forward travel at every segment.
        val passedEndpoint = current.rawFraction >= 1.0 - ROUTE_PROJECTION_EPSILON &&
            remainingRouteNearby && next.rawFraction > 0.0 &&
            next.distanceMeters <= current.distanceMeters + ROUTE_PROJECTION_EPSILON
        // The same small GPS bias can occur at a vertex inside a step, or
        // when following the full route polyline without provider steps.
        val vertexPositionTolerance = ROUTE_VERTEX_METERS / maxOf(1.0, current.lengthMeters)
        val enteredNextSegment = current.rawFraction >=
            1.0 - vertexPositionTolerance - ROUTE_PROJECTION_EPSILON &&
            remainingRouteNearby && next.rawFraction > 0.0 &&
            next.fraction * next.lengthMeters >= ROUTE_VERTEX_METERS &&
            next.distanceMeters < current.distanceMeters - ROUTE_PROJECTION_EPSILON
        if (!endpointReached && !passedEndpoint && !enteredNextSegment) break
        targetIndex++
    }
    val projection = projections[targetIndex - 1]
    return RouteStepProgress(targetIndex, projection, projection.distanceMeters <= ROUTE_CORRIDOR_METERS)
}

private fun routeSegmentProjection(
    start: RouteCoordinate,
    end: RouteCoordinate,
    location: RouteCoordinate
): RouteSegmentProjection {
    val longitudeScale = cos(Math.toRadians(location.latitude))
    val startX = Math.toRadians(routeLongitudeDelta(location.longitude, start.longitude)) *
        ROUTE_EARTH_RADIUS_METERS * longitudeScale
    val startY = Math.toRadians(start.latitude - location.latitude) * ROUTE_EARTH_RADIUS_METERS
    val endX = Math.toRadians(routeLongitudeDelta(location.longitude, end.longitude)) *
        ROUTE_EARTH_RADIUS_METERS * longitudeScale
    val endY = Math.toRadians(end.latitude - location.latitude) * ROUTE_EARTH_RADIUS_METERS
    val dx = endX - startX
    val dy = endY - startY
    val squaredLength = dx * dx + dy * dy
    val rawFraction = if (squaredLength < 0.000001) 0.0 else -(startX * dx + startY * dy) / squaredLength
    val fraction = rawFraction.coerceIn(0.0, 1.0)
    return RouteSegmentProjection(
        rawFraction = rawFraction,
        fraction = fraction,
        distanceMeters = hypot(startX + fraction * dx, startY + fraction * dy),
        lengthMeters = routeDistanceMeters(start, end),
        perpendicularDistanceMeters = if (squaredLength < 0.000001) hypot(startX, startY) else
            kotlin.math.abs(startX * dy - startY * dx) / sqrt(squaredLength)
    )
}

internal fun routeDistanceMeters(from: RouteCoordinate, to: RouteCoordinate): Double {
    val fromLatitude = Math.toRadians(from.latitude)
    val toLatitude = Math.toRadians(to.latitude)
    val latitudeDelta = toLatitude - fromLatitude
    val longitudeDelta = Math.toRadians(routeLongitudeDelta(from.longitude, to.longitude))
    val a = sin(latitudeDelta / 2.0) * sin(latitudeDelta / 2.0) +
        cos(fromLatitude) * cos(toLatitude) * sin(longitudeDelta / 2.0) * sin(longitudeDelta / 2.0)
    return 2.0 * ROUTE_EARTH_RADIUS_METERS * asin(sqrt(a.coerceIn(0.0, 1.0)))
}

internal fun routeBearingDegrees(from: RouteCoordinate, to: RouteCoordinate): Float {
    val fromLatitude = Math.toRadians(from.latitude)
    val toLatitude = Math.toRadians(to.latitude)
    val longitudeDelta = Math.toRadians(routeLongitudeDelta(from.longitude, to.longitude))
    val y = sin(longitudeDelta) * cos(toLatitude)
    val x = cos(fromLatitude) * sin(toLatitude) - sin(fromLatitude) * cos(toLatitude) * cos(longitudeDelta)
    return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
}

private fun List<RouteCoordinate>.routeGeometryIsValid() = size >= 2 && all { it.routeCoordinateIsValid() }

private fun List<RouteCoordinate>.routeDistinctPoints(): List<RouteCoordinate> = buildList {
    for (point in this@routeDistinctPoints) {
        if (isEmpty() || routeDistanceMeters(last(), point) > ROUTE_DUPLICATE_METERS) add(point)
    }
}

private fun routeStepDistanceCovered(points: List<RouteCoordinate>, progress: RouteStepProgress): Double {
    if (progress.targetIndex <= 0) return 0.0
    return (0 until progress.targetIndex - 1).sumOf { routeDistanceMeters(points[it], points[it + 1]) } +
        progress.projection.fraction * progress.projection.lengthMeters
}

private fun routeStepDistanceRemaining(points: List<RouteCoordinate>, progress: RouteStepProgress): Double =
    (1.0 - progress.projection.fraction) * progress.projection.lengthMeters +
        (progress.targetIndex until points.lastIndex).sumOf { index ->
            routeDistanceMeters(points[index], points[index + 1])
        }

private fun RouteCoordinate.routeCoordinateIsValid() = latitude.isFinite() && longitude.isFinite() &&
    latitude in -90.0..90.0 && longitude in -180.0..180.0

internal fun routeLongitudeDelta(from: Double, to: Double) = ((to - from + 540.0) % 360.0) - 180.0

private const val ROUTE_EARTH_RADIUS_METERS = 6_371_000.0
private const val ROUTE_REACHED_METERS = 8.0
private const val ROUTE_VERTEX_METERS = 1.0
private const val ROUTE_CORRIDOR_METERS = 20.0
private const val ROUTE_PROJECTION_EPSILON = 0.000001
private const val ROUTE_DUPLICATE_METERS = 0.01
private const val ROUTE_STEP_JOIN_METERS = 2.0
// Step transitions need room for ordinary GPS bias at a junction. Internal
// polyline vertices still use their stricter one-metre progression tolerance.
private const val ROUTE_JUNCTION_GPS_TOLERANCE_METERS = 8.0
