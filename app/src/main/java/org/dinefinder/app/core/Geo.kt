package org.dinefinder.app.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_METERS = 6_371_008.8
private const val RADIANS_PER_DEGREE = PI / 180.0

/** Great-circle distance, including the short path across the antimeridian. */
fun distanceMeters(from: GeoPoint, to: GeoPoint): Double {
    val deltaLatitude = (to.latitude - from.latitude) * RADIANS_PER_DEGREE
    val deltaLongitude = (to.longitude - from.longitude) * RADIANS_PER_DEGREE
    val latitudeSin = sin(deltaLatitude / 2)
    val longitudeSin = sin(deltaLongitude / 2)
    val haversine = latitudeSin * latitudeSin +
        cos(from.latitude * RADIANS_PER_DEGREE) *
        cos(to.latitude * RADIANS_PER_DEGREE) * longitudeSin * longitudeSin
    return 2 * EARTH_RADIUS_METERS * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
}

fun normalizeAngle(degrees: Double): Double {
    require(degrees.isFinite()) { "Heading must be finite." }
    return ((degrees % 360.0) + 360.0) % 360.0
}

/** Signed shortest rotation from the device heading toward a target bearing. */
fun headingDelta(bearing: Double, heading: Double): Double =
    (normalizeAngle(bearing) - normalizeAngle(heading) + 540.0) % 360.0 - 180.0

fun bearingDegrees(from: GeoPoint, to: GeoPoint): Double {
    val fromLatitude = from.latitude * RADIANS_PER_DEGREE
    val toLatitude = to.latitude * RADIANS_PER_DEGREE
    val deltaLongitude = (to.longitude - from.longitude) * RADIANS_PER_DEGREE
    return normalizeAngle(
        atan2(
            sin(deltaLongitude) * cos(toLatitude),
            cos(fromLatitude) * sin(toLatitude) -
                sin(fromLatitude) * cos(toLatitude) * cos(deltaLongitude),
        ) / RADIANS_PER_DEGREE,
    )
}

/** Compass projection only: this does not estimate a world-anchored AR pose. */
fun projectMarker(
    bearing: Double,
    heading: Double,
    fieldOfView: Double = 70.0,
): MarkerProjection {
    require(fieldOfView.isFinite() && fieldOfView > 0.0 && fieldOfView < 180.0) {
        "Camera field of view must be between 0 and 180 degrees."
    }
    val delta = headingDelta(bearing, heading)
    return MarkerProjection(
        horizontal = (delta / (fieldOfView / 2)).coerceIn(-1.0, 1.0),
        isVisible = abs(delta) <= fieldOfView / 2,
        directionDelta = delta,
    )
}

private data class NearestSegment(val index: Int, val fraction: Double, val distance: Double)

private fun nearestSegment(position: GeoPoint, points: List<GeoPoint>): NearestSegment {
    val longitudeScale = RADIANS_PER_DEGREE * EARTH_RADIUS_METERS *
        cos(position.latitude * RADIANS_PER_DEGREE)
    val latitudeScale = RADIANS_PER_DEGREE * EARTH_RADIUS_METERS
    var best = NearestSegment(0, 0.0, Double.POSITIVE_INFINITY)
    for (index in 0 until points.lastIndex) {
        // A local tangent plane is appropriate for the app's short walking routes.
        val ax = headingDelta(points[index].longitude, position.longitude) * longitudeScale
        val ay = (points[index].latitude - position.latitude) * latitudeScale
        val bx = headingDelta(points[index + 1].longitude, position.longitude) * longitudeScale
        val by = (points[index + 1].latitude - position.latitude) * latitudeScale
        val dx = bx - ax
        val dy = by - ay
        val squaredLength = dx * dx + dy * dy
        val fraction = if (squaredLength == 0.0) 0.0 else
            (-(ax * dx + ay * dy) / squaredLength).coerceIn(0.0, 1.0)
        val x = ax + fraction * dx
        val y = ay + fraction * dy
        val distance = sqrt(x * x + y * y)
        if (distance < best.distance) best = NearestSegment(index, fraction, distance)
    }
    return best
}

private fun alongDistance(nearest: NearestSegment, segmentLengths: List<Double>): Double =
    segmentLengths.take(nearest.index).sum() +
        segmentLengths[nearest.index] * nearest.fraction

fun nearestRouteProgress(
    position: GeoPoint,
    route: RoutePlan,
    offRouteThreshold: Double = 35.0,
    arrivalThreshold: Double = 25.0,
): RouteProgress {
    require(offRouteThreshold.isFinite() && offRouteThreshold > 0.0 &&
        arrivalThreshold.isFinite() && arrivalThreshold > 0.0) {
        "Navigation thresholds must be finite and positive."
    }
    val lengths = route.points.zipWithNext(::distanceMeters)
    val nearest = nearestSegment(position, route.points)
    val travelled = alongDistance(nearest, lengths)
    val remaining = max(0.0, lengths.sum() - travelled)
    val arrived = distanceMeters(position, route.points.last()) <= arrivalThreshold &&
        remaining <= arrivalThreshold

    // Routing maneuvers identify the START of a walking segment. Announcing
    // the next future segment instead would tell the user to turn too early.
    var instruction = route.instructions.firstOrNull()
    for (candidate in route.instructions) {
        val along = alongDistance(nearestSegment(candidate.point, route.points), lengths)
        if (along > travelled + 3.0) break
        instruction = candidate
    }
    if (arrived && route.instructions.isNotEmpty()) instruction = route.instructions.last()

    var targetIndex = nearest.index + 1
    if (targetIndex < route.points.lastIndex &&
        distanceMeters(position, route.points[targetIndex]) < 8.0) targetIndex++
    return RouteProgress(
        distanceFromRouteMeters = nearest.distance,
        remainingMeters = if (arrived) 0.0 else remaining,
        segmentIndex = nearest.index,
        targetPoint = route.points[targetIndex],
        instruction = instruction,
        isOffRoute = nearest.distance > offRouteThreshold,
        hasArrived = arrived,
    )
}
