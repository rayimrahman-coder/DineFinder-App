package com.example.dinefinder.profile

import android.content.Intent
import com.example.dinefinder.Restaurant
import com.example.dinefinder.places.PlaceAttribution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RestaurantProfileContractTest {
    private val restaurant = Restaurant(
        id = 7, name = "Selected Cafe", cuisine = "Food court", halal = true,
        rating = 4.2f, hours = "Monday: 9–5\nTuesday: Closed", priceRange = "RM 10–20",
        address = "Local address", latitude = 5.28, longitude = 115.17,
        distanceLabel = "Earlier distance"
    )

    @Test
    fun selectedProfileFactsAndNavigationRoundTripWithoutDatabaseLookup() {
        val intent = Intent("profile-action")
        assertSame(intent, RestaurantProfileContract.fill(intent, restaurant, 187.5, "place_123-ABC"))
        val selection = RestaurantProfileContract.read(intent)!!
        assertEquals(compactRestaurant, selection.restaurant)
        assertEquals(187.5, selection.distanceMeters, 0.0)
        assertEquals("place_123-ABC", selection.placeId)
        assertEquals("profile-action", intent.action)
    }

    @Suppress("DEPRECATION")
    @Test
    fun fillingAReusedIntentKeepsOnlyPrimitiveSelectionFields() {
        val intent = Intent().putExtra("api_key", "discard-me")
            .putExtra("photo_name", "discard-me").putExtra("image_url", "https://example.com/image")
        RestaurantProfileContract.fill(intent, restaurant, 20.0, "place-id")
        val extras = intent.extras!!
        assertFalse(extras.containsKey("api_key"))
        assertFalse(extras.containsKey("photo_name"))
        assertFalse(extras.containsKey("image_url"))
        assertEquals(setOf(RestaurantProfileContract.ID, RestaurantProfileContract.NAME,
            RestaurantProfileContract.CUISINE, RestaurantProfileContract.HALAL,
            RestaurantProfileContract.RATING, RestaurantProfileContract.LATITUDE,
            RestaurantProfileContract.LONGITUDE, RestaurantProfileContract.DISTANCE,
            RestaurantProfileContract.PLACE_ID), extras.keySet())
        assertTrue(extras.keySet().all { key ->
            val value = extras.get(key)
            value is String || value is Int || value is Boolean || value is Float || value is Double
        })
    }

    @Test
    fun missingOrBlankNameAndMissingCoordinatesRejectSelection() {
        assertNull(RestaurantProfileContract.read(Intent()))
        listOf(RestaurantProfileContract.NAME, RestaurantProfileContract.LATITUDE,
            RestaurantProfileContract.LONGITUDE).forEach { key ->
            val intent = validIntent().apply { removeExtra(key) }
            assertNull(RestaurantProfileContract.read(intent))
        }
        listOf("", " \n\t ").forEach { name ->
            assertNull(RestaurantProfileContract.read(validIntent().putExtra(RestaurantProfileContract.NAME, name)))
        }
        assertNull(RestaurantProfileContract.read(validIntent()
            .putExtra(RestaurantProfileContract.LATITUDE, "5.28")))
    }

    @Test
    fun invalidCoordinatesRejectSelectionWhileZeroCoordinatesRemainValid() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -91.0, 91.0).forEach { latitude ->
            assertNull(RestaurantProfileContract.read(validIntent()
                .putExtra(RestaurantProfileContract.LATITUDE, latitude)))
        }
        listOf(Double.NaN, Double.NEGATIVE_INFINITY, -181.0, 181.0).forEach { longitude ->
            assertNull(RestaurantProfileContract.read(validIntent()
                .putExtra(RestaurantProfileContract.LONGITUDE, longitude)))
        }
        val selection = RestaurantProfileContract.read(validIntent()
            .putExtra(RestaurantProfileContract.LATITUDE, 0.0)
            .putExtra(RestaurantProfileContract.LONGITUDE, 0.0))!!
        assertEquals(0.0, selection.restaurant.latitude, 0.0)
        assertEquals(0.0, selection.restaurant.longitude, 0.0)
    }

    @Test
    fun invalidOrWronglyTypedRatingsRemainUnknown() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 5.1f).forEach { rating ->
            val selection = RestaurantProfileContract.read(validIntent()
                .putExtra(RestaurantProfileContract.RATING, rating))!!
            assertNull(mergeProfileContent(selection.restaurant, null).rating)
        }
        val missing = validIntent().apply { removeExtra(RestaurantProfileContract.RATING) }
        assertNull(mergeProfileContent(RestaurantProfileContract.read(missing)!!.restaurant, null).rating)
        val wrongType = validIntent().putExtra(RestaurantProfileContract.RATING, "4.5")
        assertNull(mergeProfileContent(RestaurantProfileContract.read(wrongType)!!.restaurant, null).rating)
    }

    @Test
    fun placeIdsAreOptionalAndBoundedWithoutAllowingRequestPathInjection() {
        listOf("", "../other", "place?key=x", "place%2Fother", "a".repeat(1025)).forEach { placeId ->
            assertNull(RestaurantProfileContract.read(validIntent()
                .putExtra(RestaurantProfileContract.PLACE_ID, placeId))!!.placeId)
            assertNull(RestaurantProfileContract.read(RestaurantProfileContract.fill(
                Intent(), restaurant, 20.0, placeId))!!.placeId)
        }
        val longest = "a".repeat(1024)
        assertEquals(longest, RestaurantProfileContract.read(RestaurantProfileContract.fill(
            Intent(), restaurant, 20.0, longest))!!.placeId)
        assertNull(RestaurantProfileContract.read(RestaurantProfileContract.fill(
            Intent(), restaurant, 20.0, null))!!.placeId)
    }

    @Test
    fun requiredFieldsAloneDoNotInventOptionalMetadata() {
        val selection = RestaurantProfileContract.read(Intent()
            .putExtra(RestaurantProfileContract.NAME, "Selected place")
            .putExtra(RestaurantProfileContract.LATITUDE, 5.0)
            .putExtra(RestaurantProfileContract.LONGITUDE, 115.0))!!
        assertEquals(0, selection.restaurant.id)
        assertEquals(0f, selection.restaurant.rating, 0f)
        assertNull(selection.placeId)
    }

    @Test
    fun savedRecordsRequireTheirActualHalalBooleanRatherThanInventingFalse() {
        assertNull(RestaurantProfileContract.read(validIntent().apply {
            removeExtra(RestaurantProfileContract.HALAL)
        }))
        assertNull(RestaurantProfileContract.read(validIntent()
            .putExtra(RestaurantProfileContract.HALAL, "false")))
        val googleSelection = RestaurantProfileContract.read(validIntent()
            .putExtra(RestaurantProfileContract.ID, -1).apply {
                removeExtra(RestaurantProfileContract.HALAL)
            })!!
        assertNull(mergeProfileContent(googleSelection.restaurant, null).halal)
    }

    @Test
    fun nearbyProviderCreditsRoundTripWithOnlySafeLinksAndOriginalNames() {
        val credits = listOf(
            PlaceAttribution("Nearby provider", "https://provider.example/credit"),
            PlaceAttribution("Another provider", "http://another.example/"),
            PlaceAttribution("Required name without a safe link", "javascript:alert(1)")
        )
        val selection = RestaurantProfileContract.read(RestaurantProfileContract.fill(
            Intent(), restaurant, 20.0, "place-id", credits))!!
        assertEquals(credits.take(2) + PlaceAttribution("Required name without a safe link", null),
            selection.attributions)
        assertEquals(compactRestaurant, selection.restaurant)
        assertTrue(RestaurantProfileContract.read(validIntent())!!.attributions.isEmpty())
    }

    @Test
    fun blankOrMalformedCreditMetadataCannotInventProvidersOrUnsafeLinks() {
        val filtered = RestaurantProfileContract.read(RestaurantProfileContract.fill(
            Intent(), restaurant, 20.0, "place-id",
            listOf(PlaceAttribution(" \n ", "https://example.com"))))!!
        assertTrue(filtered.attributions.isEmpty())
        val malformedNames = validIntent().putExtra(RestaurantProfileContract.ATTRIBUTION_NAMES, "not a list")
        assertTrue(RestaurantProfileContract.read(malformedNames)!!.attributions.isEmpty())
        val credited = validIntent()
            .putStringArrayListExtra(RestaurantProfileContract.ATTRIBUTION_NAMES,
                arrayListOf("", "Provider one", "Provider two"))
            .putStringArrayListExtra(RestaurantProfileContract.ATTRIBUTION_URIS,
                arrayListOf("https://example.com", "https://user:password@example.com/"))
        assertEquals(listOf(PlaceAttribution("Provider one", null), PlaceAttribution("Provider two", null)),
            RestaurantProfileContract.read(credited)!!.attributions)
        credited.putExtra(RestaurantProfileContract.ATTRIBUTION_URIS, 12)
        assertEquals(listOf(PlaceAttribution("Provider one", null), PlaceAttribution("Provider two", null)),
            RestaurantProfileContract.read(credited)!!.attributions)
    }

    private val compactRestaurant get() = restaurant.copy(hours = "", priceRange = "", address = "", distanceLabel = "")

    private fun validIntent() = RestaurantProfileContract.fill(Intent(), restaurant, 20.0, "place-id")
}
