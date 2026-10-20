package com.example.dinefinder.places

import com.example.dinefinder.Restaurant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyRestaurantMergerTest {
    private val local = Restaurant(1, "Cafe UMSKAL", "Food court", true, 0f, "", "", "", 5.280285448386224,
        115.17640486697412, "")
    private val google = PlaceSummary("place-a", "Cafe UMSKAL", local.latitude, local.longitude,
        "UMS Labuan", "Cafe", "https://maps.google.com/?cid=1")

    @Test
    fun matchingPlaceKeepsKnownLocalDataAndFillsOnlyBlankFieldsInMemory() {
        val credits = listOf(PlaceAttribution("Place data provider", "https://provider.example"))
        val result = mergeNearbyRestaurants(listOf(local), listOf(google.copy(attributions = credits))).single()
        assertEquals(1, result.restaurant.id)
        assertEquals("place-a", result.placeId)
        assertTrue(result.restaurant.halal)
        assertEquals("Food court", result.restaurant.cuisine)
        assertEquals("UMS Labuan", result.restaurant.address)
        assertEquals(credits, result.attributions)
        assertEquals("", local.address)
        assertEquals(0f, result.restaurant.rating, 0f)
        assertEquals("", result.restaurant.hours)
        assertEquals("", result.restaurant.priceRange)
    }

    @Test
    fun knownLocalAddressAndUserCoordinatesAlwaysWinOverGoogle() {
        val known = local.copy(address = "User supplied address", rating = 4f, hours = "9–5", priceRange = "RM10")
        val result = mergeNearbyRestaurants(listOf(known), listOf(google.copy(latitude = local.latitude + .0001)))
            .single().restaurant
        assertEquals(known, result)
    }

    @Test
    fun normalizedCaseWhitespacePunctuationAndUnicodeWidthMatchNearbyPlace() {
        val near = google.copy(name = "  ＣＡＦＥ -- UMSKAL ", latitude = local.latitude + .0001)
        val result = mergeNearbyRestaurants(listOf(local), listOf(near))
        assertEquals(1, result.size)
        assertEquals(1, result.single().restaurant.id)
        assertEquals("place-a", result.single().placeId)
    }

    @Test
    fun sameNameMoreThan30MetersAwayIsASeparateRestaurant() {
        val away = google.copy(latitude = local.latitude + .0004)
        val result = mergeNearbyRestaurants(listOf(local), listOf(away))
        assertEquals(2, result.size)
        assertTrue(result[0].restaurant.id > 0)
        assertTrue(result[1].restaurant.id < 0)
    }

    @Test
    fun unrelatedNamesAtSameCoordinatesStaySeparate() {
        val result = mergeNearbyRestaurants(listOf(local), listOf(google.copy(name = "Neighbour cafe")))
        assertEquals(2, result.size)
    }

    @Test
    fun pureGoogleRestaurantHasUnknownHalalRatingHoursAndPrice() {
        val result = mergeNearbyRestaurants(emptyList(), listOf(google)).single()
        assertTrue(result.restaurant.id < 0)
        assertEquals("place-a", result.placeId)
        assertFalse(result.restaurant.halal)
        assertEquals(0f, result.restaurant.rating, 0f)
        assertEquals("", result.restaurant.hours)
        assertEquals("", result.restaurant.priceRange)
        assertEquals("", result.restaurant.distanceLabel)
    }

    @Test
    fun hashCollisionPlaceIdsHaveDistinctNegativeIdsAndRemainStableAcrossRefreshes() {
        assertEquals("Aa".hashCode(), "BB".hashCode())
        val merger = NearbyRestaurantMerger()
        val first = google.copy(id = "Aa", name = "Restaurant A")
        val second = google.copy(id = "BB", name = "Restaurant B")
        val initial = merger.mergeNearbyRestaurants(emptyList(), listOf(second, first))
            .associate { it.placeId to it.restaurant.id }
        assertNotEquals(initial["Aa"], initial["BB"])
        assertTrue(initial.values.all { it < 0 })
        val refresh = merger.mergeNearbyRestaurants(emptyList(), listOf(first))
        assertEquals(initial["Aa"], refresh.single().restaurant.id)
        val restored = merger.mergeNearbyRestaurants(emptyList(), listOf(second, first))
            .associate { it.placeId to it.restaurant.id }
        assertEquals(initial, restored)
    }

    @Test
    fun pureMergeIdsAreIndependentOfGoogleResponseOrder() {
        val places = listOf(google.copy(id = "z", name = "Z"), google.copy(id = "a", name = "A"))
        assertEquals(mergeNearbyRestaurants(emptyList(), places),
            mergeNearbyRestaurants(emptyList(), places.reversed()))
    }

    @Test
    fun repeatedGoogleIdAndSameNameNearbyGoogleRecordsDoNotDuplicateMarkers() {
        val result = mergeNearbyRestaurants(emptyList(), listOf(google, google,
            google.copy(id = "place-b", latitude = local.latitude + .0001)))
        assertEquals(1, result.size)
        assertEquals("place-a", result.single().placeId)
    }

    @Test
    fun closestMatchingLocalWinsAndAllLocalRecordsRemain() {
        val further = local.copy(id = 2, latitude = local.latitude + .0002)
        val result = mergeNearbyRestaurants(listOf(further, local), listOf(google))
        assertEquals(2, result.size)
        assertEquals("place-a", result.single { it.restaurant.id == 1 }.placeId)
        assertEquals(null, result.single { it.restaurant.id == 2 }.placeId)
    }

    @Test
    fun emptyGoogleResponseRetainsOnlySavedLocalRecordsWithoutRecreatingSeeds() {
        assertEquals(listOf(NearbyRestaurant(local, null)), mergeNearbyRestaurants(listOf(local), emptyList()))
        assertTrue(mergeNearbyRestaurants(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun invalidCoordinatesNamesAndLocalIdsAreNeverGivenSyntheticPositions() {
        val badLocal = listOf(local.copy(latitude = Double.NaN), local.copy(name = ""), local.copy(id = -10))
        val badGoogle = listOf(google.copy(latitude = 91.0), google.copy(name = " "), google.copy(id = "../path"))
        assertTrue(mergeNearbyRestaurants(badLocal, badGoogle).isEmpty())
    }
}
