package org.dinefinder.app.core

/** WGS84 coordinates in decimal degrees. Invalid fixes never enter app state. */
data class GeoPoint(val latitude: Double, val longitude: Double) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0) {
            "Latitude must be finite and between -90 and 90 degrees."
        }
        require(longitude.isFinite() && longitude in -180.0..180.0) {
            "Longitude must be finite and between -180 and 180 degrees."
        }
    }

    val isValid: Boolean
        get() = latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0
}

data class Restaurant(
    val id: String,
    val name: String,
    val cuisine: String,
    val description: String,
    val address: String,
    val location: GeoPoint,
    val rating: Double,
    val priceLevel: Int,
    val emoji: String,
    val accentHex: Long,
    val tags: List<String> = emptyList(),
    val openingHours: String = "Check opening hours with the restaurant",
    val isHalal: Boolean? = null,
) {
    init {
        require(id.isNotBlank() && name.isNotBlank()) { "A restaurant needs an ID and name." }
        require(rating.isFinite() && rating in 0.0..5.0) { "Rating must be between 0 and 5." }
        require(priceLevel in 1..4) { "Price level must be between 1 and 4." }
    }

    val priceLabel: String get() = "\$".repeat(priceLevel)
    val halalStatus: String get() = when (isHalal) {
        true -> "Halal"
        false -> "Not halal"
        null -> if (tags.any { it.equals("HALAL", ignoreCase = true) }) "Halal" else "Not provided"
    }
}

data class RouteInstruction(
    val point: GeoPoint,
    val text: String,
    val distanceMeters: Double,
) {
    init {
        require(text.isNotBlank()) { "A walking instruction needs text." }
        require(distanceMeters.isFinite() && distanceMeters >= 0.0) {
            "Instruction distance must be finite and nonnegative."
        }
    }
}

/** A supplied route, with defensive copies of its path and instructions. */
class RoutePlan(
    points: List<GeoPoint>,
    instructions: List<RouteInstruction>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val isDemo: Boolean = false,
) {
    val points: List<GeoPoint> = points.toList()
    val instructions: List<RouteInstruction> = instructions.toList()

    init {
        require(this.points.size >= 2) { "A walking route needs at least two points." }
        require(distanceMeters.isFinite() && distanceMeters >= 0.0) {
            "Route distance must be finite and nonnegative."
        }
        require(durationSeconds.isFinite() && durationSeconds >= 0.0) {
            "Route duration must be finite and nonnegative."
        }
    }
}

data class MarkerProjection(
    /** -1 is the left viewport edge; +1 is its right edge. */
    val horizontal: Double,
    val isVisible: Boolean,
    val directionDelta: Double,
)

data class RouteProgress(
    val distanceFromRouteMeters: Double,
    val remainingMeters: Double,
    val segmentIndex: Int,
    val targetPoint: GeoPoint,
    val instruction: RouteInstruction?,
    val isOffRoute: Boolean,
    val hasArrived: Boolean,
)
