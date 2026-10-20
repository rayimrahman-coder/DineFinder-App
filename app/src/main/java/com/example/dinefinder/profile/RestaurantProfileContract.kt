package com.example.dinefinder.profile

import android.content.Intent
import android.os.Bundle
import com.example.dinefinder.Restaurant
import com.example.dinefinder.places.PlaceAttribution

data class ProfileSelection(
    val restaurant: Restaurant,
    val distanceMeters: Double = 0.0,
    val placeId: String?,
    val attributions: List<PlaceAttribution> = emptyList()
)

/** Only profile facts, the navigation destination, and provider credits cross this boundary. */
object RestaurantProfileContract {
    internal const val ID = "restaurant_profile.id"
    internal const val NAME = "restaurant_profile.name"
    internal const val CUISINE = "restaurant_profile.cuisine"
    internal const val HALAL = "restaurant_profile.halal"
    internal const val RATING = "restaurant_profile.rating"
    internal const val LATITUDE = "restaurant_profile.latitude"
    internal const val LONGITUDE = "restaurant_profile.longitude"
    internal const val DISTANCE = "restaurant_profile.distance"
    internal const val PLACE_ID = "restaurant_profile.place_id"
    internal const val ATTRIBUTION_NAMES = "restaurant_profile.attribution_names"
    internal const val ATTRIBUTION_URIS = "restaurant_profile.attribution_uris"
    private val validPlaceId = Regex("[A-Za-z0-9_-]{1,1024}")

    fun fill(
        intent: Intent,
        restaurant: Restaurant,
        distanceMeters: Double,
        placeId: String?,
        attributions: List<PlaceAttribution> = emptyList()
    ): Intent = intent.apply {
        // Reusing an Intent cannot carry credentials or stale extended profile fields.
        replaceExtras(Bundle())
        putExtra(ID, restaurant.id)
        putExtra(NAME, restaurant.name)
        putExtra(CUISINE, restaurant.cuisine)
        putExtra(HALAL, restaurant.halal)
        putExtra(RATING, restaurant.rating)
        putExtra(LATITUDE, restaurant.latitude)
        putExtra(LONGITUDE, restaurant.longitude)
        if (distanceMeters.isFinite() && distanceMeters >= 0.0) {
            putExtra(DISTANCE, distanceMeters)
        }
        placeId?.takeIf(validPlaceId::matches)?.let { putExtra(PLACE_ID, it) }
        val credits = attributions.filter { it.provider.isNotBlank() }
        if (credits.isNotEmpty()) {
            putStringArrayListExtra(ATTRIBUTION_NAMES, ArrayList(credits.map { it.provider }))
            putStringArrayListExtra(ATTRIBUTION_URIS,
                ArrayList(credits.map { profileWebUri(it.providerUri).orEmpty() }))
        }
    }

    @Suppress("DEPRECATION")
    fun read(intent: Intent): ProfileSelection? {
        return try {
            val extras = intent.extras ?: return null
            val name = extras.get(NAME) as? String ?: return null
            val latitude = extras.get(LATITUDE) as? Double ?: return null
            val longitude = extras.get(LONGITUDE) as? Double ?: return null
            if (name.isBlank() || !latitude.isFinite() || !longitude.isFinite() ||
                latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null

            val id = extras.get(ID) as? Int ?: 0
            val halal = extras.get(HALAL) as? Boolean
            // A saved row's Boolean is known data; a missing value cannot mean "not halal".
            if (id > 0 && halal == null) return null
            val rating = (extras.get(RATING) as? Float)
                ?.takeIf { it.isFinite() && it in 0f..5f } ?: 0f
            val distanceMeters = (extras.get(DISTANCE) as? Double)?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
            val restaurant = Restaurant(
                id = id,
                name = name,
                cuisine = extras.get(CUISINE) as? String ?: "",
                halal = halal ?: false,
                rating = rating,
                hours = "",
                priceRange = "",
                address = "",
                latitude = latitude,
                longitude = longitude,
                distanceLabel = ""
            )
            val placeId = (extras.get(PLACE_ID) as? String)?.takeIf(validPlaceId::matches)
            val providerNames = extras.get(ATTRIBUTION_NAMES) as? ArrayList<*>
            val providerUris = extras.get(ATTRIBUTION_URIS) as? ArrayList<*>
            val attributions = providerNames.orEmpty().mapIndexedNotNull { index, value ->
                val provider = (value as? String)?.takeIf { it.isNotBlank() }
                    ?: return@mapIndexedNotNull null
                val uri = profileWebUri(providerUris?.getOrNull(index) as? String)
                PlaceAttribution(provider, uri)
            }
            ProfileSelection(restaurant, distanceMeters, placeId, attributions)
        } catch (_: RuntimeException) {
            // Invalid/unreadable extras must not start location or Places requests.
            null
        }
    }
}
