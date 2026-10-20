package com.example.dinefinder.data

import com.example.dinefinder.Restaurant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestaurantDiscoveryTest {
    private val restaurants = listOf(
        fixture(1, "Cuisine A", true, 1.0),
        fixture(2, "Cuisine B", true, 2.0),
        fixture(3, "Cuisine C", false, 3.0),
        fixture(4, "Cuisine B", false, 4.0)
    )

    @Test
    fun computesKnownDistancesIncludingAcrossDateLine() {
        assertEquals(0.0, RestaurantDiscovery.distanceMeters(
            DiscoveryLocation(0.0, 0.0), DiscoveryLocation(0.0, 0.0))!!, 0.001)
        assertEquals(111_195.0, RestaurantDiscovery.distanceMeters(
            DiscoveryLocation(0.0, 0.0), DiscoveryLocation(0.0, 1.0))!!, 1.0)
        assertEquals(222_390.0, RestaurantDiscovery.distanceMeters(
            DiscoveryLocation(0.0, 179.0), DiscoveryLocation(0.0, -179.0))!!, 1.0)
    }

    @Test
    fun nearestRestaurantsComeFirstRegardlessOfDatabaseOrder() {
        val restaurant = restaurants.last()
        val matches = RestaurantDiscovery.find(restaurants,
            DiscoveryLocation(restaurant.latitude, restaurant.longitude))
        assertEquals(restaurant.id, matches.first().restaurant.id)
        assertEquals(0.0, matches.first().distanceMeters!!, 0.001)
        assertTrue(matches.zipWithNext().all { (a, b) -> a.distanceMeters!! <= b.distanceMeters!! })
    }

    @Test
    fun combinesCuisineAndHalalFiltersWithoutChangingRecords() {
        assertEquals(listOf(2), RestaurantDiscovery.find(restaurants, cuisine = "cuisine b", halalOnly = true)
            .map { it.restaurant.id })
        assertTrue(RestaurantDiscovery.find(restaurants, cuisine = "Cuisine C", halalOnly = true).isEmpty())
        assertEquals(4, restaurants.size)
    }

    @Test
    fun absentOrInvalidLocationNeverUsesStoredDistanceLabels() {
        assertTrue(RestaurantDiscovery.find(restaurants).all { it.distanceMeters == null })
        assertTrue(RestaurantDiscovery.find(restaurants, DiscoveryLocation(Double.NaN, 0.0))
            .all { it.distanceMeters == null })
        assertNull(RestaurantDiscovery.distanceMeters(DiscoveryLocation(91.0, 0.0), DiscoveryLocation(0.0, 0.0)))
        val invalid = restaurants.first().copy(id = 99, latitude = Double.NaN)
        val matches = RestaurantDiscovery.find(listOf(invalid, restaurants.last()), DiscoveryLocation(0.0, 0.0))
        assertEquals(99, matches.last().restaurant.id)
        assertNull(matches.last().distanceMeters)
    }

    private fun fixture(id: Int, cuisine: String, halal: Boolean, longitude: Double) = Restaurant(
        id = id,
        name = "Test location $id",
        cuisine = cuisine,
        halal = halal,
        rating = 1f,
        hours = "",
        priceRange = "",
        address = "",
        latitude = 0.0,
        longitude = longitude,
        distanceLabel = "Stored label for tests"
    )
}
