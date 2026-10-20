package com.example.dinefinder.core

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Local SLAM yaw, with model forward -Z; no cloud/geospatial localization. */
fun localArrowYaw(cameraForwardX: Double, cameraForwardZ: Double, directionDelta: Double): Float {
    require(cameraForwardX.isFinite() && cameraForwardZ.isFinite() && directionDelta.isFinite())
    require(cameraForwardX * cameraForwardX + cameraForwardZ * cameraForwardZ > 1e-6)
    val turn = Math.toRadians(directionDelta)
    val x = cameraForwardX * cos(turn) - cameraForwardZ * sin(turn)
    val z = cameraForwardX * sin(turn) + cameraForwardZ * cos(turn)
    return Math.toDegrees(atan2(-x, -z)).toFloat()
}

/** OSRM may snap its endpoint onto a path away from the actual restaurant. */
fun restaurantRouteProgress(position: GeoPoint, route: RoutePlan, restaurant: GeoPoint?): RouteProgress {
    val along = nearestRouteProgress(position, route)
    if (restaurant == null) return along
    val distance = distanceMeters(position, restaurant)
    if (distance <= 25.0) return along.copy(hasArrived = true, remainingMeters = 0.0,
        instruction = RouteInstruction(restaurant, "Arrive at the restaurant", 0.0))
    if (!along.hasArrived) return along
    return along.copy(hasArrived = false, remainingMeters = distance,
        instruction = RouteInstruction(route.points.last(), "The mapped path ends nearby. Check the restaurant entrance.", 0.0))
}
