package com.example.dinefinder.places

import com.example.dinefinder.data.DiscoveryLocation
import com.example.dinefinder.settings.GoogleMapsAppIdentity
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PlacesClientTest {
    private val identity = GoogleMapsAppIdentity("com.example.dinefinder", "A".repeat(40))
    private val location = DiscoveryLocation(5.280285448386224, 115.17640486697412)
    private val cafe = PlaceResponse(
        id = "cafe-id", displayName = LocalizedPlaceText("Cafe UMSKAL"),
        location = PlaceCoordinate(location.latitude, location.longitude),
        formattedAddress = "UMS Labuan", primaryType = "cafe",
        primaryTypeDisplayName = LocalizedPlaceText("Cafe"), types = listOf("cafe", "food"),
        businessStatus = "OPERATIONAL", googleMapsUri = "https://maps.google.com/?cid=123"
    )

    @Test
    fun nearbyRequestUses500MeterCircleDistanceRankingAndBasicFieldMaskWithoutRealNetwork() = runBlocking {
        val captured = mutableListOf<Request>()
        val client = client(captured, """{"places":[$CAFE_JSON]}""")
        val result = client.searchNearby(" test-api-key ", identity, location, 500.0)
        val request = captured.single()
        assertEquals("POST", request.method)
        assertEquals("https://places.googleapis.com/v1/places:searchNearby", request.url.toString())
        assertCredentials(request)
        assertEquals(NEARBY_FIELD_MASK, request.header("X-Goog-FieldMask"))
        assertFalse(NEARBY_FIELD_MASK.contains("rating"))
        assertFalse(NEARBY_FIELD_MASK.contains("OpeningHours"))
        assertFalse(NEARBY_FIELD_MASK.contains("photos"))
        assertFalse(NEARBY_FIELD_MASK.contains("websiteUri"))
        val bodyText = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
        assertFalse(bodyText.contains("test-api-key"))
        val body = JsonParser.parseString(bodyText).asJsonObject
        assertEquals(listOf("restaurant", "cafe", "food_court"),
            body["includedTypes"].asJsonArray.map { it.asString })
        assertEquals(20, body["maxResultCount"].asInt)
        assertEquals("DISTANCE", body["rankPreference"].asString)
        val circle = body["locationRestriction"].asJsonObject["circle"].asJsonObject
        assertEquals(500.0, circle["radius"].asDouble, 0.0)
        assertEquals(location.latitude, circle["center"].asJsonObject["latitude"].asDouble, 0.0)
        assertEquals(location.longitude, circle["center"].asJsonObject["longitude"].asDouble, 0.0)
        assertEquals("Cafe UMSKAL", (result as PlacesSearchResult.Success).places.single().name)
    }

    @Test
    fun detailRequestAddsProfileOnlyFieldsAndReturnsActualHoursAndAuthorLinks() = runBlocking {
        val captured = mutableListOf<Request>()
        val json = CAFE_JSON.dropLast(1) + """,
            "rating":4.3,"userRatingCount":28,
            "regularOpeningHours":{"weekdayDescriptions":["Monday: 8 am–5 pm","Tuesday: Closed"]},
            "websiteUri":"https://cafe.example/menu",
            "attributions":[{"provider":"Example provider","providerUri":"https://provider.example"}],
            "photos":[{"name":"places/cafe-id/photos/photo1","googleMapsUri":"https://maps.google.com/photo/1",
             "authorAttributions":[{"displayName":"Photo author","uri":"//maps.google.com/contrib/1",
              "photoUri":"//lh3.googleusercontent.com/author"}]}]}"""
        val result = client(captured, json).details("test-api-key", identity, "cafe-id")
        val request = captured.single()
        assertEquals("GET", request.method)
        assertEquals("https://places.googleapis.com/v1/places/cafe-id", request.url.toString())
        assertCredentials(request)
        assertEquals(DETAILS_FIELD_MASK, request.header("X-Goog-FieldMask"))
        assertFalse(DETAILS_FIELD_MASK.contains("reviews"))
        val details = (result as PlaceDetailsResult.Success).details
        assertEquals(4.3f, details.rating!!, 0f)
        assertEquals(28, details.ratingCount)
        assertEquals(listOf("Monday: 8 am–5 pm", "Tuesday: Closed"), details.hours)
        assertEquals("https://cafe.example/menu", details.websiteUri)
        assertEquals("Example provider", details.summary.attributions.single().provider)
        val photo = details.photos.single()
        assertEquals("https://maps.google.com/photo/1", photo.googleMapsUri)
        assertEquals("Photo author", photo.authors.single().displayName)
        assertEquals("https://maps.google.com/contrib/1", photo.authors.single().uri)
        assertEquals("https://lh3.googleusercontent.com/author", photo.authors.single().photoUri)
    }

    @Test
    fun ratingProfileRequestUsesLeanFieldMaskAndKeepsOnlyTypeRatingAndRequiredCredits() = runBlocking {
        val captured = mutableListOf<Request>()
        val json = CAFE_JSON.dropLast(1) + """,
            "rating":4.3,"userRatingCount":28,
            "regularOpeningHours":{"weekdayDescriptions":["Monday: 8 am–5 pm"]},
            "websiteUri":"https://cafe.example/menu",
            "attributions":[{"provider":"Example provider","providerUri":"https://provider.example"}],
            "photos":[{"name":"places/cafe-id/photos/photo1","authorAttributions":[]}]}"""
        val result = client(captured, json).ratingDetails(" test-api-key ", identity, "cafe-id")
        val request = captured.single()
        assertEquals("GET", request.method)
        assertEquals("https://places.googleapis.com/v1/places/cafe-id", request.url.toString())
        assertCredentials(request)
        assertNull(request.url.queryParameter("key"))
        assertFalse(request.url.toString().contains("test-api-key"))
        assertEquals(RATING_DETAILS_FIELD_MASK, request.header("X-Goog-FieldMask"))
        assertEquals(setOf("id", "displayName", "location", "primaryType", "primaryTypeDisplayName",
            "types", "businessStatus", "attributions", "rating", "userRatingCount"),
            request.header("X-Goog-FieldMask")!!.split(',').toSet())
        val details = (result as PlaceDetailsResult.Success).details
        assertEquals("Cafe", details.summary.category)
        assertEquals(4.3f, details.rating!!, 0f)
        assertEquals(28, details.ratingCount)
        assertEquals("Example provider", details.summary.attributions.single().provider)
        assertNull(details.summary.googleMapsUri)
        // Even unexpected rich fields in a server response do not enter the simple profile result.
        assertEquals("", details.summary.address)
        assertTrue(details.hours.isEmpty())
        assertNull(details.websiteUri)
        assertTrue(details.photos.isEmpty())
    }

    @Test
    fun absentOrInvalidRatingProfileValuesRemainUnknown() = runBlocking {
        val service = FakePlacesService()
        listOf<Float?>(null, Float.NaN, Float.POSITIVE_INFINITY, 0f, 5.1f).forEach { rating ->
            service.detailsResult = { Response.success(cafe.copy(rating = rating, userRatingCount = -1)) }
            val result = PlacesClient(service).ratingDetails("test-api-key", identity, "cafe-id")
            val details = (result as PlaceDetailsResult.Success).details
            assertNull(details.rating)
            assertNull(details.ratingCount)
        }
        service.detailsResult = { Response.success(cafe) }
        val absent = (PlacesClient(service).ratingDetails("test-api-key", identity, "cafe-id")
            as PlaceDetailsResult.Success).details
        assertNull(absent.rating)
        assertNull(absent.ratingCount)
    }

    @Test
    fun ratingProfileMissingCredentialsInvalidIdentityAndPathNeverReachNetwork() = runBlocking {
        val service = FakePlacesService()
        val client = PlacesClient(service)
        assertEquals(PlaceDetailsResult.Failure(PlacesFailure.MISSING_KEY),
            client.ratingDetails(" ", null, "cafe-id"))
        assertEquals(PlaceDetailsResult.Failure(PlacesFailure.CONFIG),
            client.ratingDetails("test-api-key", null, "cafe-id"))
        assertEquals(PlaceDetailsResult.Failure(PlacesFailure.CONFIG),
            client.ratingDetails("test-api-key", identity.copy(certificateSha1 = "AA:BB"), "cafe-id"))
        assertEquals(PlaceDetailsResult.Failure(PlacesFailure.CONFIG),
            client.ratingDetails("key\nInjected: value", identity, "cafe-id"))
        listOf("", "../other", "place?key=secret", "https://example.com", "place%2Fother").forEach {
            assertEquals(PlaceDetailsResult.Failure(PlacesFailure.CONFIG),
                client.ratingDetails("test-api-key", identity, it))
        }
        assertEquals(0, service.calls)
    }

    @Test
    fun ratingProfileRejectsMismatchedClosedOrNonRestaurantPlaces() = runBlocking {
        val service = FakePlacesService()
        listOf(cafe.copy(id = "other"), cafe.copy(businessStatus = "CLOSED_PERMANENTLY"),
            cafe.copy(types = listOf("school")), cafe.copy(location = null)).forEach { body ->
            service.detailsResult = { Response.success(body) }
            assertEquals(PlaceDetailsResult.Failure(PlacesFailure.NO_RESULTS),
                PlacesClient(service).ratingDetails("test-api-key", identity, "cafe-id"))
        }
    }

    @Test
    fun photoRequestGetsJsonUrlWithKeyOnlyInHeadersAndMakesNoImageRequest() = runBlocking {
        val captured = mutableListOf<Request>()
        val result = client(captured,
            """{"photoUri":"https://lh3.googleusercontent.com/photo=s800"}""")
            .photoUri("test-api-key", identity, "places/cafe-id/photos/photo1", 800, 600)
        val request = captured.single()
        assertEquals("GET", request.method)
        assertEquals("/v1/places/cafe-id/photos/photo1/media", request.url.encodedPath)
        assertEquals("800", request.url.queryParameter("maxWidthPx"))
        assertEquals("600", request.url.queryParameter("maxHeightPx"))
        assertEquals("true", request.url.queryParameter("skipHttpRedirect"))
        assertNull(request.url.queryParameter("key"))
        assertFalse(request.url.toString().contains("test-api-key"))
        assertCredentials(request)
        assertEquals(PhotoResult.Success("https://lh3.googleusercontent.com/photo=s800"), result)
    }

    @Test
    fun unexpectedPhotoRedirectIsNotFollowedAndCannotSendCredentialsToImageHost() = runBlocking {
        val captured = mutableListOf<Request>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            captured += chain.request()
            okhttp3.Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(302).message("Found").header("Location", "https://lh3.googleusercontent.com/photo")
                .body("".toResponseBody("application/json".toMediaType())).build()
        }.build()
        assertEquals(PhotoResult.Failure(PlacesFailure.NETWORK), PlacesClient.create(http)
            .photoUri("test-api-key", identity, "places/cafe-id/photos/photo1", 800, 600))
        assertEquals(1, captured.size)
        assertEquals("places.googleapis.com", captured.single().url.host)
    }

    @Test
    fun missingKeyAndInvalidIdentityOrHeaderValuesNeverReachNetwork() = runBlocking {
        val service = FakePlacesService()
        val client = PlacesClient(service)
        assertEquals(PlacesSearchResult.Failure(PlacesFailure.MISSING_KEY),
            client.searchNearby(" \n ", null, location, 500.0))
        val invalid = listOf(null, identity.copy(packageName = "bad\nInjected: header"),
            identity.copy(certificateSha1 = "AA:BB"))
        invalid.forEach { candidate ->
            assertEquals(PlacesSearchResult.Failure(PlacesFailure.CONFIG),
                client.searchNearby("test-api-key", candidate, location, 500.0))
        }
        assertEquals(PlacesSearchResult.Failure(PlacesFailure.CONFIG),
            client.searchNearby("key\nInjected: value", identity, location, 500.0))
        assertEquals(0, service.calls)
    }

    @Test
    fun invalidSearchCoordinatesAndRadiusNeverReachNetwork() = runBlocking {
        val service = FakePlacesService()
        val client = PlacesClient(service)
        listOf(location.copy(latitude = Double.NaN), location.copy(longitude = 181.0)).forEach {
            assertEquals(PlacesSearchResult.Failure(PlacesFailure.CONFIG),
                client.searchNearby("test-api-key", identity, it, 500.0))
        }
        listOf(0.0, -1.0, 50_001.0, Double.POSITIVE_INFINITY).forEach {
            assertEquals(PlacesSearchResult.Failure(PlacesFailure.CONFIG),
                client.searchNearby("test-api-key", identity, location, it))
        }
        assertEquals(0, service.calls)
    }

    @Test
    fun pathInjectionAndInvalidPhotoSizeNeverReachNetwork() = runBlocking {
        val service = FakePlacesService()
        val client = PlacesClient(service)
        listOf("", "../other", "place?key=secret", "https://example.com", "place%2Fother").forEach {
            assertEquals(PlaceDetailsResult.Failure(PlacesFailure.CONFIG),
                client.details("test-api-key", identity, it))
        }
        listOf("places/cafe-id/photos/../secret", "places/cafe-id/photos/photo?key=secret",
            "https://example.com/photo", "places/cafe-id/photos/%2fsecret").forEach {
            assertEquals(PhotoResult.Failure(PlacesFailure.CONFIG),
                client.photoUri("test-api-key", identity, it, 800, 600))
        }
        listOf(0 to 600, 800 to 0, 4801 to 600, 800 to -1).forEach { (width, height) ->
            assertEquals(PhotoResult.Failure(PlacesFailure.CONFIG),
                client.photoUri("test-api-key", identity, "places/cafe-id/photos/photo1", width, height))
        }
        assertEquals(0, service.calls)
    }

    @Test
    fun absentResultsClosedBusinessesWrongTypesAndInvalidCoordinatesNeverCreateMarkers() = runBlocking {
        val invalid = listOf(cafe.copy(businessStatus = "CLOSED_PERMANENTLY"),
            cafe.copy(businessStatus = "CLOSED_TEMPORARILY"), cafe.copy(businessStatus = "FUTURE_OPENING"),
            cafe.copy(businessStatus = null), cafe.copy(types = listOf("school", "food")),
            cafe.copy(location = PlaceCoordinate(Double.NaN, 115.0)), cafe.copy(location = null),
            cafe.copy(displayName = LocalizedPlaceText(" ")), cafe.copy(id = "../other"))
        val responses = listOf(NearbyResponse(), NearbyResponse(emptyList()),
            NearbyResponse(invalid + null))
        responses.forEach { response ->
            val service = FakePlacesService().apply { nearbyResult = { Response.success(response) } }
            assertEquals(PlacesSearchResult.Failure(PlacesFailure.NO_RESULTS), PlacesClient(service)
                .searchNearby("test-api-key", identity, location, 500.0))
        }
    }

    @Test
    fun responseIsDistanceSortedDeduplicatedBoundedAndExcludedOutsideRadius() = runBlocking {
        val far = cafe.copy(id = "far", location = PlaceCoordinate(location.latitude + .02, location.longitude))
        val near = cafe.copy(id = "near", location = PlaceCoordinate(location.latitude + .001, location.longitude))
        val service = FakePlacesService().apply {
            nearbyResult = { Response.success(NearbyResponse(listOf(far, near, cafe, near))) }
        }
        val result = PlacesClient(service).searchNearby("test-api-key", identity, location, 1000.0)
        assertEquals(listOf("cafe-id", "near"), (result as PlacesSearchResult.Success).places.map { it.id })
        service.nearbyResult = { Response.success(NearbyResponse((0..29).map { cafe.copy(id = "place-$it") })) }
        val bounded = PlacesClient(service).searchNearby("test-api-key", identity, location, 1000.0)
        assertEquals(20, (bounded as PlacesSearchResult.Success).places.size)
    }

    @Test
    fun exact1000MeterBoundaryIsIncludedWhileOneMillimeterOutsideIsExcluded() = runBlocking {
        val center = DiscoveryLocation(0.0, 0.0)
        fun northAt(meters: Double, id: String) = cafe.copy(id = id,
            location = PlaceCoordinate(Math.toDegrees(meters / 6_371_000.0), 0.0))
        val service = FakePlacesService().apply {
            nearbyResult = { Response.success(NearbyResponse(listOf(
                northAt(1000.001, "outside"), northAt(1000.0, "boundary")
            ))) }
        }
        val result = PlacesClient(service).searchNearby("test-api-key", identity, center, 1000.0)
        assertEquals(listOf("boundary"), (result as PlacesSearchResult.Success).places.map { it.id })
    }

    @Test
    fun unavailableProfileFieldsRemainUnknownAndUnsafeUrlsAreNotExposed() = runBlocking {
        val service = FakePlacesService().apply {
            detailsResult = { Response.success(cafe.copy(rating = Float.NaN, userRatingCount = -1,
                websiteUri = "javascript:alert(1)", googleMapsUri = "https://user:secret@maps.google.com")) }
        }
        val result = PlacesClient(service).details("test-api-key", identity, "cafe-id")
        val details = (result as PlaceDetailsResult.Success).details
        assertNull(details.rating)
        assertNull(details.ratingCount)
        assertNull(details.websiteUri)
        assertNull(details.summary.googleMapsUri)
        assertTrue(details.hours.isEmpty())
        assertTrue(details.photos.isEmpty())
    }

    @Test
    fun wrongPlaceResponseAndClosedProfileDoNotShowAnotherPlace() = runBlocking {
        val service = FakePlacesService()
        listOf(cafe.copy(id = "other"), cafe.copy(businessStatus = "CLOSED_PERMANENTLY")).forEach { body ->
            service.detailsResult = { Response.success(body) }
            assertEquals(PlaceDetailsResult.Failure(PlacesFailure.NO_RESULTS),
                PlacesClient(service).details("test-api-key", identity, "cafe-id"))
        }
    }

    @Test
    fun photoResourcesCannotBelongToAnotherPlaceOrSilentlyDropRequiredAuthors() = runBlocking {
        val service = FakePlacesService().apply {
            detailsResult = { Response.success(cafe.copy(photos = listOf(
                PlacePhotoResponse("places/other/photos/photo1", emptyList()),
                PlacePhotoResponse("places/cafe-id/photos/photo1", null),
                PlacePhotoResponse("places/cafe-id/photos/photo2", listOf(PhotoAuthorResponse("", null, null))),
                PlacePhotoResponse("places/cafe-id/photos/photo3", listOf(null)),
                PlacePhotoResponse("places/cafe-id/photos/valid", emptyList())
            ))) }
        }
        val details = (PlacesClient(service).details("test-api-key", identity, "cafe-id")
            as PlaceDetailsResult.Success).details
        assertEquals(listOf("places/cafe-id/photos/valid"), details.photos.map { it.name })
    }

    @Test
    fun httpFailuresExposeOnlySafeEnumForSearchDetailsAndPhotos() = runBlocking {
        val expected = mapOf(400 to PlacesFailure.CONFIG, 401 to PlacesFailure.CONFIG,
            403 to PlacesFailure.CONFIG, 404 to PlacesFailure.NO_RESULTS,
            429 to PlacesFailure.QUOTA, 500 to PlacesFailure.NETWORK)
        expected.forEach { (status, failure) ->
            val service = FakePlacesService().apply {
                nearbyResult = { errorResponse(status) }
                detailsResult = { errorResponse(status) }
                photoResult = { errorResponse(status) }
            }
            val client = PlacesClient(service)
            val results = listOf(client.searchNearby("test-api-key", identity, location, 500.0),
                client.details("test-api-key", identity, "cafe-id"),
                client.photoUri("test-api-key", identity, "places/cafe-id/photos/photo1", 800, 600),
                client.ratingDetails("test-api-key", identity, "cafe-id"))
            assertEquals(PlacesSearchResult.Failure(failure), results[0])
            assertEquals(PlaceDetailsResult.Failure(failure), results[1])
            assertEquals(PhotoResult.Failure(failure), results[2])
            assertEquals(PlaceDetailsResult.Failure(failure), results[3])
            assertTrue(results.none { it.toString().contains("secret-error-body") })
        }
    }

    @Test
    fun offlineAndMalformedJsonFailuresAreSafeAndCancellationPropagates() = runBlocking {
        val service = FakePlacesService()
        val client = PlacesClient(service)
        service.nearbyResult = { throw IOException("secret test-api-key") }
        assertEquals(PlacesSearchResult.Failure(PlacesFailure.NETWORK),
            client.searchNearby("test-api-key", identity, location, 500.0))
        service.nearbyResult = { throw JsonParseException("secret-error-body") }
        assertEquals(PlacesSearchResult.Failure(PlacesFailure.CONFIG),
            client.searchNearby("test-api-key", identity, location, 500.0))
        service.nearbyResult = { throw CancellationException("paused") }
        try {
            client.searchNearby("test-api-key", identity, location, 500.0)
            error("Expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(3, service.calls)
        }
    }

    @Test
    fun forbiddenQuotaResponseUsesOnlyStatusAndDoesNotExposeServerMessages() = runBlocking {
        val service = FakePlacesService().apply {
            photoResult = { Response.error(403,
                """{"error":{"status":"RESOURCE_EXHAUSTED","message":"secret test-api-key"}}"""
                    .toResponseBody("application/json".toMediaType())) }
        }
        val result = PlacesClient(service)
            .photoUri("test-api-key", identity, "places/cafe-id/photos/photo1", 800, 600)
        assertEquals(PhotoResult.Failure(PlacesFailure.QUOTA), result)
        assertFalse(result.toString().contains("test-api-key"))
    }

    @Test
    fun photoUrlsRejectInsecureCredentialBearingAndScriptResponses() = runBlocking {
        val service = FakePlacesService()
        val client = PlacesClient(service)
        val invalid = listOf("http://lh3.googleusercontent.com/photo", "javascript:alert(1)",
            "https://lh3.googleusercontent.com/photo?key=some-key",
            "https://lh3.googleusercontent.com/photo?%6Bey=some-key",
            "https://lh3.googleusercontent.com/photo?apikey=some-key",
            "https://lh3.googleusercontent.com/test-api-key/photo", "https://user:password@example.com/p",
            "https://example.com/photo\nInjected: value")
        invalid.forEach { uri ->
            service.photoResult = { Response.success(PhotoResponse(uri)) }
            assertEquals(PhotoResult.Failure(PlacesFailure.NO_RESULTS),
                client.photoUri("test-api-key", identity, "places/cafe-id/photos/photo1", 800, 600))
        }
    }

    @Test
    fun allowedPublicWebsiteAndProtocolRelativeAuthorUrlsStayUsable() {
        assertEquals("http://cafe.example/menu", safeWebUri("http://cafe.example/menu"))
        assertEquals("https://maps.google.com/contrib/1", safeWebUri("//maps.google.com/contrib/1", true))
        assertNull(safeWebUri("intent://app"))
        assertNull(safeWebUri("file:///tmp/photo"))
        assertNull(safeWebUri("https://example.com/my photo"))
    }

    private fun client(captured: MutableList<Request>, json: String): PlacesClient = PlacesClient.create(
        OkHttpClient.Builder().addInterceptor { chain ->
            captured += chain.request()
            okhttp3.Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(json.toResponseBody("application/json".toMediaType())).build()
        }.build()
    )

    private fun assertCredentials(request: Request) {
        assertEquals("test-api-key", request.header("X-Goog-Api-Key"))
        assertEquals(identity.packageName, request.header("X-Android-Package"))
        assertEquals(identity.certificateSha1, request.header("X-Android-Cert"))
    }

    private fun <T> errorResponse(status: Int): Response<T> = Response.error(status,
        "secret-error-body test-api-key".toResponseBody("application/json".toMediaType()))

    private class FakePlacesService : PlacesService {
        var calls = 0
        var nearbyResult: () -> Response<NearbyResponse> = { Response.success(NearbyResponse()) }
        var detailsResult: () -> Response<PlaceResponse> = { Response.success(PlaceResponse()) }
        var photoResult: () -> Response<PhotoResponse> = { Response.success(PhotoResponse()) }
        override suspend fun nearby(apiKey: String, packageName: String, certificateSha1: String,
            request: NearbyRequest): Response<NearbyResponse> { calls++; return nearbyResult() }
        override suspend fun details(apiKey: String, packageName: String, certificateSha1: String,
            placeId: String): Response<PlaceResponse> { calls++; return detailsResult() }
        override suspend fun ratingDetails(apiKey: String, packageName: String, certificateSha1: String,
            placeId: String): Response<PlaceResponse> { calls++; return detailsResult() }
        override suspend fun photo(apiKey: String, packageName: String, certificateSha1: String,
            photoName: String, maxWidthPx: Int, maxHeightPx: Int,
            skipHttpRedirect: Boolean): Response<PhotoResponse> { calls++; return photoResult() }
    }

    companion object {
        private const val CAFE_JSON = """{"id":"cafe-id","displayName":{"text":"Cafe UMSKAL"},
            "location":{"latitude":5.280285448386224,"longitude":115.17640486697412},
            "formattedAddress":"UMS Labuan","primaryType":"cafe","primaryTypeDisplayName":{"text":"Cafe"},
            "types":["cafe","food"],"businessStatus":"OPERATIONAL",
            "googleMapsUri":"https://maps.google.com/?cid=123"}"""
    }
}
