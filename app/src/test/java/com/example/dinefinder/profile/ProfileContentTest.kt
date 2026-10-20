package com.example.dinefinder.profile

import com.example.dinefinder.Restaurant
import com.example.dinefinder.places.PlaceAttribution
import com.example.dinefinder.places.PlaceDetails
import com.example.dinefinder.places.PlaceSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileContentTest {
    private val restaurant = Restaurant(
        id = 7, name = "Local name", cuisine = "Food court", halal = true, rating = 3.5f,
        hours = "", priceRange = "", address = "", latitude = 5.28, longitude = 115.17
    )
    private val details = PlaceDetails(
        summary = PlaceSummary(
            id = "place-id", name = "Google name", latitude = 5.28, longitude = 115.17,
            address = "", category = "Cafe",
            attributions = listOf(PlaceAttribution("Provider", "https://provider.example"))
        ),
        rating = 4.7f, ratingCount = 42, hours = emptyList(),
        websiteUri = null, photos = emptyList()
    )

    @Test
    fun knownLocalTypeAndRatingSurviveGoogleEnrichmentAndKeepTheirRatingSource() {
        val content = mergeProfileContent(restaurant, details)
        assertEquals("Local name", content.name)
        assertEquals("Food court", content.cuisine)
        assertEquals(true, content.halal)
        assertEquals(3.5f, content.rating!!, 0f)
        assertNull(content.ratingCount)
        assertEquals(details.summary.attributions, content.attributions)
    }

    @Test
    fun missingTypeAndRatingUseActualGoogleDetailsWithoutInventingHalal() {
        val content = mergeProfileContent(restaurant.copy(id = -2, name = " ", cuisine = "",
            rating = 0f), details)
        assertEquals("Google name", content.name)
        assertEquals("Cafe", content.cuisine)
        assertNull(content.halal)
        assertEquals(4.7f, content.rating!!, 0f)
        assertEquals(42, content.ratingCount)
        assertNull(mergeProfileContent(restaurant.copy(id = 0), details).halal)
        assertFalse(mergeProfileContent(restaurant.copy(halal = false), details).halal!!)
    }

    @Test
    fun unavailableGoogleFieldsRemainUnknownAndNeverAppearAsSyntheticContent() {
        val content = mergeProfileContent(restaurant.copy(id = 0, cuisine = "", rating = 0f), null)
        assertEquals("Local name", content.name)
        assertEquals("", content.cuisine)
        assertNull(content.halal)
        assertNull(content.rating)
        assertNull(content.ratingCount)
        assertTrue(content.attributions.isEmpty())
    }

    @Test
    fun googleCountsAreShownOnlyWithAValidGoogleRating() {
        listOf(null, Float.NaN, Float.POSITIVE_INFINITY, -1f, 0f, 5.1f).forEach { rating ->
            val content = mergeProfileContent(restaurant.copy(rating = 0f), details.copy(rating = rating))
            assertNull(content.rating)
            assertNull(content.ratingCount)
        }
        assertNull(mergeProfileContent(restaurant.copy(rating = 0f),
            details.copy(ratingCount = -1)).ratingCount)
        assertEquals(0, mergeProfileContent(restaurant.copy(rating = 0f),
            details.copy(ratingCount = 0)).ratingCount)
    }

    @Test
    fun unsafeLinksAreRemovedWhileProviderNamesStayVisible() {
        val unsafe = details.copy(summary = details.summary.copy(
            attributions = listOf(PlaceAttribution("Required provider", "file:///tmp/page"),
                PlaceAttribution(" ", "https://example.com"))))
        val content = mergeProfileContent(restaurant, unsafe)
        assertEquals(listOf(PlaceAttribution("Required provider", null)), content.attributions)
    }

    @Test
    fun providerLinksRequireAnExplicitWebSchemeHostAndNoCredentialsOrWhitespace() {
        listOf(null, "", " https://example.com", "https://example.com ",
            "https://example.com/my photo", "https://example.com/\nheader", "https://example.com/\tpath",
            "https://example.com/\u00a0path", "https://example.com/%0aheader", "javascript:alert(1)",
            "file:///tmp/photo", "mailto:owner@example.com", "intent://app", "//example.com/path",
            "relative/path", "https:///missing-host", "https://", "https://user@example.com/",
            "https://user:password@example.com/", "https://example.com:0/",
            "https://example.com:65536/").forEach { uri ->
            assertNull("Should reject $uri", profileWebUri(uri))
        }
        listOf("http://provider.example/credit", "https://example.com/path?q=provider%20credit#author",
            "HTTPS://example.com/credit", "https://example.com:8443/path").forEach { uri ->
            assertEquals(uri, profileWebUri(uri))
        }
        assertNull(profileWebUri("http://example.com/credit", httpsOnly = true))
        assertEquals("https://example.com/credit", profileWebUri("https://example.com/credit", httpsOnly = true))
    }
}
