package com.example.dinefinder.places

import com.example.dinefinder.Restaurant
import com.example.dinefinder.data.DiscoveryLocation
import com.example.dinefinder.data.RestaurantDiscovery
import java.text.Normalizer
import java.util.Locale

data class NearbyRestaurant(
    val restaurant: Restaurant,
    val placeId: String?,
    val attributions: List<PlaceAttribution> = emptyList()
)

/** Keep one merger per scan. Sorted allocation makes IDs independent of response order. */
class NearbyRestaurantMerger {
    private val googleIds = mutableMapOf<String, Int>()
    private var nextGoogleId = -1

    fun mergeNearbyRestaurants(local: List<Restaurant>, google: List<PlaceSummary>): List<NearbyRestaurant> {
        val result = local.filter { it.id > 0 && it.name.isNotBlank() &&
            DiscoveryLocation(it.latitude, it.longitude).isValid }
            .distinctBy { it.id }.map { NearbyRestaurant(it, null) }.toMutableList()
        val places = google.filter { validPlaceId(it.id) && it.name.isNotBlank() &&
            DiscoveryLocation(it.latitude, it.longitude).isValid }.distinctBy { it.id }.sortedBy { it.id }
        places.forEach { place ->
            val matchingIndex = result.indices
                .filter { normalizedName(result[it].restaurant.name) == normalizedName(place.name) }
                .filter { distance(result[it].restaurant, place) <= 30.0 }
                .minWithOrNull(compareBy<Int> { if (result[it].restaurant.id > 0) 0 else 1 }
                    .thenBy { distance(result[it].restaurant, place) }
                    .thenBy { result[it].restaurant.id })
            if (matchingIndex != null) {
                val known = result[matchingIndex]
                if (known.placeId == null) {
                    result[matchingIndex] = NearbyRestaurant(known.restaurant.copy(
                        address = known.restaurant.address.ifBlank { place.address },
                        cuisine = known.restaurant.cuisine.ifBlank { place.category }
                    ), place.id, place.attributions)
                }
            } else {
                val id = googleIds.getOrPut(place.id) {
                    check(nextGoogleId > Int.MIN_VALUE) { "Scan ID space exhausted" }
                    nextGoogleId--
                }
                result += NearbyRestaurant(Restaurant(
                    id = id, name = place.name, cuisine = place.category, halal = false,
                    rating = 0f, hours = "", priceRange = "", address = place.address,
                    latitude = place.latitude, longitude = place.longitude, distanceLabel = ""
                ), place.id, place.attributions)
            }
        }
        return result
    }

    private fun distance(local: Restaurant, google: PlaceSummary): Double =
        RestaurantDiscovery.distanceMeters(DiscoveryLocation(local.latitude, local.longitude),
            DiscoveryLocation(google.latitude, google.longitude)) ?: Double.POSITIVE_INFINITY
}

/** Deterministic pure merge for one set of inputs. Use a merger instance for stable session IDs. */
fun mergeNearbyRestaurants(local: List<Restaurant>, google: List<PlaceSummary>): List<NearbyRestaurant> =
    NearbyRestaurantMerger().mergeNearbyRestaurants(local, google)

private fun normalizedName(name: String): String = Normalizer
    .normalize(name, Normalizer.Form.NFKC)
    .lowercase(Locale.ROOT)
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
    .trim()
