package com.example.dinefinder.ar

import kotlin.math.cos

internal data class RouteCoordinate(val latitude: Double, val longitude: Double)

/** Selects the end of the closest remaining segment, never moving backward. */
internal fun forwardRouteTargetIndex(
    points: List<RouteCoordinate>,
    location: RouteCoordinate,
    currentTargetIndex: Int,
    reachedDistanceMeters: Double = 5.0
): Int {
    if (points.size < 2) return 0
    val currentTarget = currentTargetIndex.coerceIn(0, points.lastIndex)
    val longitudeScale = cos(Math.toRadians(location.latitude))
    val offsets = points.map { point ->
        Pair(
            Math.toRadians(point.longitude - location.longitude) * EARTH_RADIUS_METERS * longitudeScale,
            Math.toRadians(point.latitude - location.latitude) * EARTH_RADIUS_METERS
        )
    }
    var target = currentTarget
    var nearestDistanceSquared = Double.POSITIVE_INFINITY

    for (segmentStart in (currentTarget - 1).coerceAtLeast(0) until points.lastIndex) {
        val (startX, startY) = offsets[segmentStart]
        val (endX, endY) = offsets[segmentStart + 1]
        val dx = endX - startX
        val dy = endY - startY
        val lengthSquared = dx * dx + dy * dy
        val fraction = if (lengthSquared == 0.0) 0.0 else
            (-(startX * dx + startY * dy) / lengthSquared).coerceIn(0.0, 1.0)
        val closestX = startX + fraction * dx
        val closestY = startY + fraction * dy
        val distanceSquared = closestX * closestX + closestY * closestY
        // Prefer the earlier segment at intersections or shared endpoints.
        if (distanceSquared < nearestDistanceSquared - 0.01) {
            nearestDistanceSquared = distanceSquared
            target = maxOf(currentTarget, segmentStart + 1)
        }
    }

    val reachedDistanceSquared = reachedDistanceMeters * reachedDistanceMeters
    while (target < points.lastIndex) {
        val (x, y) = offsets[target]
        if (x * x + y * y > reachedDistanceSquared) break
        target++
    }
    return target
}

private const val EARTH_RADIUS_METERS = 6_371_000.0
