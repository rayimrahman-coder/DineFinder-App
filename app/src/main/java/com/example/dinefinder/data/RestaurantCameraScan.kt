package com.example.dinefinder.data

import com.example.dinefinder.Restaurant
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

data class CameraScanPose(
    /** Row-major 3 x 3 rotation from device axes to magnetic east/north/up. */
    val deviceToWorld: DoubleArray,
    /** Column-major calibrated, display-oriented OpenGL perspective projection. */
    val projection: DoubleArray,
    val displayRotationDegrees: Int = 0,
    val declinationDegrees: Double = 0.0
)

data class CameraRestaurantMatch(
    val restaurant: Restaurant,
    val distanceMeters: Double,
    val screenX: Double,
    /** A fixed compass overlay row; this does not estimate restaurant elevation. */
    val screenY: Double
)

object RestaurantCameraScan {
    const val RADIUS_METERS = 1000.0
    const val MARKER_SCREEN_Y = 0.5
    private const val MATRIX_TOLERANCE = 0.001
    private const val FORWARD_EPSILON = 0.000001
    private const val MIN_HORIZONTAL_CAMERA_FORWARD = 0.15
    private const val MIN_DIRECTION_DISTANCE_METERS = 0.000001
    // Trigonometric rounding can put an exact boundary point a few ulps above 1000 m.
    // 0.1 micrometre preserves inclusion without meaningfully extending the GPS radius.
    private const val RADIUS_ROUNDING_TOLERANCE_METERS = 0.0000001

    fun visibleRestaurants(
        restaurants: List<Restaurant>,
        location: DiscoveryLocation?,
        pose: CameraScanPose?
    ): List<CameraRestaurantMatch> {
        if (location == null || !location.isValid || pose == null) return emptyList()
        val rotation = pose.deviceToWorld.copyOf()
        val projection = pose.projection.copyOf()
        if (!isValidPose(pose, rotation, projection)) return emptyList()

        // Rear-camera forward is device -Z. Normalize its horizontal part so pitch and
        // optical-axis roll cannot move the compass markers around the screen.
        val cameraEast = -rotation[2]
        val cameraNorth = -rotation[5]
        val horizontalLength = hypot(cameraEast, cameraNorth)
        // Near vertical, small sensor changes make the horizontal heading unreliable.
        if (horizontalLength < MIN_HORIZONTAL_CAMERA_FORWARD) return emptyList()
        val forwardEast = cameraEast / horizontalLength
        val forwardNorth = cameraNorth / horizontalLength

        return restaurants.mapNotNull { restaurant ->
            val destination = DiscoveryLocation(restaurant.latitude, restaurant.longitude)
            val distance = RestaurantDiscovery.distanceMeters(location, destination)
                ?: return@mapNotNull null
            // Coincident coordinates have no direction to project into the camera.
            if (distance <= MIN_DIRECTION_DISTANCE_METERS ||
                distance > RADIUS_METERS + RADIUS_ROUNDING_TOLERANCE_METERS) return@mapNotNull null

            val bearing = trueBearing(location, destination) - Math.toRadians(pose.declinationDegrees)
            val east = sin(bearing)
            val north = cos(bearing)
            val forward = forwardEast * east + forwardNorth * north
            if (forward <= FORWARD_EPSILON) return@mapNotNull null
            val right = forwardNorth * east - forwardEast * north
            // Calibration still controls horizontal field of view and lens offset.
            // Vertical projection and display roll deliberately do not move the overlay row.
            val ndcX = projection[0] * right / forward - projection[8]
            if (!ndcX.isFinite() || ndcX !in -1.0..1.0) return@mapNotNull null

            CameraRestaurantMatch(restaurant, distance, (ndcX + 1) / 2, MARKER_SCREEN_Y)
        }.sortedWith(compareBy<CameraRestaurantMatch> { it.distanceMeters }.thenBy { it.restaurant.id })
    }

    private fun trueBearing(start: DiscoveryLocation, end: DiscoveryLocation): Double {
        val startLatitude = Math.toRadians(start.latitude)
        val endLatitude = Math.toRadians(end.latitude)
        val longitudeDelta = Math.toRadians(end.longitude - start.longitude)
        return atan2(
            sin(longitudeDelta) * cos(endLatitude),
            cos(startLatitude) * sin(endLatitude) -
                sin(startLatitude) * cos(endLatitude) * cos(longitudeDelta)
        )
    }

    private fun isValidPose(pose: CameraScanPose, rotation: DoubleArray, projection: DoubleArray): Boolean {
        if (rotation.size != 9 || projection.size != 16 ||
            rotation.any { !it.isFinite() } || projection.any { !it.isFinite() } ||
            pose.displayRotationDegrees !in listOf(0, 90, 180, 270) ||
            !pose.declinationDegrees.isFinite()) return false

        for (column in 0..2) {
            val norm = (0..2).sumOf { row -> rotation[row * 3 + column] * rotation[row * 3 + column] }
            if (abs(norm - 1) > MATRIX_TOLERANCE) return false
            for (other in column + 1..2) {
                val dot = (0..2).sumOf { row -> rotation[row * 3 + column] * rotation[row * 3 + other] }
                if (abs(dot) > MATRIX_TOLERANCE) return false
            }
        }
        val determinant = rotation[0] * (rotation[4] * rotation[8] - rotation[5] * rotation[7]) -
            rotation[1] * (rotation[3] * rotation[8] - rotation[5] * rotation[6]) +
            rotation[2] * (rotation[3] * rotation[7] - rotation[4] * rotation[6])
        if (abs(determinant - 1) > MATRIX_TOLERANCE * 3) return false

        // Require a usable, axis-aligned OpenGL perspective camera rather than an identity,
        // orthographic or malformed matrix. Lens offsets at indices 8 and 9 are supported.
        if (listOf(1, 2, 3, 4, 6, 7, 12, 13, 15).any { abs(projection[it]) > MATRIX_TOLERANCE } ||
            abs(projection[11] + 1) > MATRIX_TOLERANCE ||
            projection[10] > -1 + MATRIX_TOLERANCE || projection[14] >= 0) return false
        return listOf(projection[0], projection[5]).all { scale ->
            if (scale <= 0) false
            else (2 * atan(1 / scale)).let { fieldOfView -> fieldOfView > 0 && fieldOfView < Math.PI }
        }
    }
}
