package com.example.dinefinder.data

import com.example.dinefinder.Restaurant
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class DiscoveryLocation(val latitude: Double, val longitude: Double) {
    val isValid: Boolean
        get() = latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0
}

data class RestaurantMatch(val restaurant: Restaurant, val distanceMeters: Double?)

object RestaurantDiscovery {
    fun find(
        restaurants: List<Restaurant>,
        location: DiscoveryLocation? = null,
        cuisine: String? = null,
        halalOnly: Boolean = false
    ): List<RestaurantMatch> = restaurants.asSequence()
        .filter { !halalOnly || it.halal }
        .filter { cuisine == null || it.cuisine.equals(cuisine, ignoreCase = true) }
        .map { restaurant ->
            RestaurantMatch(
                restaurant,
                location?.let {
                    distanceMeters(it, DiscoveryLocation(restaurant.latitude, restaurant.longitude))
                }
            )
        }
        .sortedWith(
            compareBy<RestaurantMatch> { it.distanceMeters ?: Double.POSITIVE_INFINITY }
                .thenBy { it.restaurant.name.lowercase(Locale.ROOT) }
                .thenBy { it.restaurant.id }
        )
        .toList()

    fun distanceMeters(start: DiscoveryLocation, end: DiscoveryLocation): Double? {
        if (!start.isValid || !end.isValid) return null
        val latitudeDelta = Math.toRadians(end.latitude - start.latitude)
        val longitudeDelta = Math.toRadians(end.longitude - start.longitude)
        val a = (sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
            cos(Math.toRadians(start.latitude)) * cos(Math.toRadians(end.latitude)) *
            sin(longitudeDelta / 2) * sin(longitudeDelta / 2)).coerceIn(0.0, 1.0)
        return 6_371_000.0 * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
