package com.example.dinefinder.data

import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import com.example.dinefinder.core.GeoPoint
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DineFinderApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: DineFinderApi
    private val publicKey = "sb_publishable_test_public_value"
    private val from = GeoPoint(3.1478, 101.7100)
    private val to = GeoPoint(3.1491, 101.7113)

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        api = DineFinderApi(
            SupabaseConfig(server.url("/").toString(), publicKey),
            OkHttpClient.Builder().callTimeout(2, TimeUnit.SECONDS).build(),
            server.url("/routed-foot/route/v1/foot/").toString(),
        )
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun publicConfigurationRejectsPrivilegedKeysAndUnsafeRemoteUrls() {
        assertTrue(SupabaseConfig("https://project.supabase.co", publicKey).isConfigured)
        assertTrue(SupabaseConfig("https://project.supabase.co", jwt("anon")).isConfigured)
        assertFalse(SupabaseConfig("https://project.supabase.co", jwt("service_role")).isConfigured)
        assertFalse(SupabaseConfig("https://project.supabase.co", "sb_secret_do_not_use").isConfigured)
        assertFalse(SupabaseConfig("http://remote.example", publicKey).isConfigured)
        assertFalse(SupabaseConfig("https://user:password@project.supabase.co", publicKey).isConfigured)
        assertFalse(SupabaseConfig("https://YOUR_PROJECT.supabase.co", "YOUR_PUBLIC_PUBLISHABLE_KEY").isConfigured)
    }

    @Test fun nearbyUsesOneKilometreRpcAndMergesVerifiedHalalTag() = runBlocking {
        server.enqueue(json(JSONArray().put(restaurant()).toString()))
        val places = api.nearby(from)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/rest/v1/rpc/nearby_restaurants", request.path)
        assertEquals(publicKey, request.getHeader("apikey"))
        assertNull(request.getHeader("Authorization"))
        val body = JSONObject(request.body.readUtf8())
        assertEquals(1000, body.getInt("radius_meters"))
        assertEquals(from.latitude, body.getDouble("latitude"), 0.0)
        assertEquals(from.longitude, body.getDouble("longitude"), 0.0)
        assertEquals(1, places.size)
        assertEquals("UMSKAL Food Court", places.single().name)
        assertEquals(to, places.single().location)
        assertEquals(0.0, places.single().rating, 0.0)
        assertEquals(listOf("Food court", "HALAL"), places.single().tags)
    }

    @Test fun nearbyRejectsInvalidRowsRatherThanRenderingBrokenLocations() = runBlocking {
        server.enqueue(json(JSONArray().put(restaurant().put("latitude", 91)).toString()))
        val error = expectApiError { api.nearby(from) }
        assertTrue(error.message.orEmpty().contains("invalid"))
    }

    @Test fun missingRpcProvidesExactSetupActionWithoutEchoingServerBody() = runBlocking {
        server.enqueue(json("""{"code":"PGRST202","message":"server-internal-detail"}""", 404))
        val error = expectApiError { api.nearby(from) }
        assertEquals("Restaurant database is not set up yet. Run supabase/setup.sql in the Supabase SQL Editor.", error.message)
        assertFalse(error.message.orEmpty().contains("server-internal-detail"))
    }

    @Test fun nearbyCampusSearchKeepsBothOwnerSuppliedFoodCourts() = runBlocking {
        val cafe = restaurant().put("id", "cafe-umskal").put("name", "Cafe UMSKAL")
            .put("latitude", 5.280285448386224).put("longitude", 115.17640486697412)
        val anjung = restaurant().put("id", "anjung").put("name", "Anjung Selera Sungai Pagar")
            .put("latitude", 5.28381416215476).put("longitude", 115.17329350236913)
        server.enqueue(json(JSONArray().put(cafe).put(anjung).toString()))
        assertEquals(listOf("Cafe UMSKAL", "Anjung Selera Sungai Pagar"),
            api.nearby(GeoPoint(5.280285448386224, 115.17640486697412)).map { it.name })
    }

    @Test fun rejectedPublicKeyReportsHttpStatusWithoutEchoingResponseBody() = runBlocking {
        server.enqueue(json("""{"message":"private-internal-response"}""", 401))
        val message = expectApiError { api.nearby(from) }.message.orEmpty()
        assertTrue(message.contains("HTTP 401"))
        assertTrue(message.contains("publishable key"))
        assertFalse(message.contains("private-internal-response"))
    }

    @Test fun forbiddenPublicReadReportsPermissionFailure() = runBlocking {
        server.enqueue(json("""{"code":"42501","message":"private schema details"}""", 403))
        val message = expectApiError { api.nearby(from) }.message.orEmpty()
        assertTrue(message.contains("HTTP 403, code 42501"))
        assertTrue(message.contains("public read policies"))
        assertFalse(message.contains("private schema details"))
    }

    @Test fun databaseFunctionFailureReportsItsBoundedProtocolCode() = runBlocking {
        server.enqueue(json("""{"code":"42804","message":"private SQL details"}""", 500))
        val message = expectApiError { api.nearby(from) }.message.orEmpty()
        assertTrue(message.contains("HTTP 500, code 42804"))
        assertTrue(message.contains("database function failed"))
        assertFalse(message.contains("private SQL details"))
    }

    @Test fun overloadedNearbyFunctionHasSpecificAction() = runBlocking {
        server.enqueue(json("""{"code":"PGRST203"}""", 300))
        val message = expectApiError { api.nearby(from) }.message.orEmpty()
        assertTrue(message.contains("function signatures conflict"))
        assertTrue(message.contains("HTTP 300, code PGRST203"))
    }

    @Test fun arbitraryServerTextIsNotAcceptedAsAProtocolCode() = runBlocking {
        server.enqueue(json("""{"code":"secret-data-with-lowercase","message":"private"}""", 400))
        val message = expectApiError { api.nearby(from) }.message.orEmpty()
        assertTrue(message.contains("HTTP 400"))
        assertFalse(message.contains("secret-data-with-lowercase"))
        assertFalse(message.contains("private"))
    }

    @Test fun legacyAnonJwtUsesBearerOnlyOnSupabaseRequests() = runBlocking {
        val key = jwt("anon")
        val legacy = DineFinderApi(SupabaseConfig(server.url("/").toString(), key))
        server.enqueue(json("[]"))
        legacy.nearby(from)
        val request = server.takeRequest()
        assertEquals(key, request.getHeader("apikey"))
        assertEquals("Bearer $key", request.getHeader("Authorization"))
    }

    @Test fun walkingUsesActualPedestrianGraphAndNoAuthenticationHeaders() = runBlocking {
        server.enqueue(json(walking().toString()))
        val route = api.walkingRoute(from, to)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/routed-foot/route/v1/foot/101.71,3.1478;101.7113,3.1491", request.requestUrl?.encodedPath)
        assertEquals("geojson", request.requestUrl?.queryParameter("geometries"))
        assertEquals("true", request.requestUrl?.queryParameter("steps"))
        assertEquals("100;100", request.requestUrl?.queryParameter("radiuses"))
        assertNull(request.getHeader("apikey"))
        assertNull(request.getHeader("Authorization"))
        assertNull(request.getHeader("x-api-key"))
        assertFalse(route.isDemo)
        assertEquals(from, route.points.first())
        assertEquals(to, route.points.last())
        assertEquals("Start walking north on First Street", route.instructions.first().text)
        assertEquals("Turn right onto Second Street", route.instructions[1].text)
        assertEquals(289.0, route.distanceMeters, 0.0)
    }

    @Test fun noRouteAndRateLimitNeverBecomeSyntheticPaths() = runBlocking {
        server.enqueue(json("""{"code":"NoRoute"}"""))
        assertTrue(expectApiError { api.walkingRoute(from, to) }.message.orEmpty().contains("No walking route"))
        server.enqueue(json("upstream private detail", 429))
        val limited = expectApiError { api.walkingRoute(from, to) }
        assertTrue(limited.message.orEmpty().contains("Wait"))
        assertFalse(limited.message.orEmpty().contains("private detail"))
    }

    @Test fun walkingRejectsDistantTripsAndInvalidUpstreamGeometry() = runBlocking {
        val count = server.requestCount
        expectApiError { api.walkingRoute(from, GeoPoint(4.0, 102.0)) }
        assertEquals(count, server.requestCount)
        server.enqueue(json(walking().apply {
            getJSONArray("routes").getJSONObject(0).getJSONObject("geometry")
                .put("coordinates", JSONArray("[[0,0],[0,0.001]]"))
        }.toString()))
        assertTrue(expectApiError { api.walkingRoute(from, to) }.message.orEmpty().contains("nearby walking path"))
    }

    @Test fun signInUsesPasswordGrantAndReadsOnlyOwnProfileWithUserToken() = runBlocking {
        server.enqueue(json(session().toString()))
        server.enqueue(json("""[{"display_name":"Profile Name"}]"""))
        val account = api.signIn("person@example.test", "test-password")
        val auth = server.takeRequest()
        assertEquals("password", auth.requestUrl?.queryParameter("grant_type"))
        assertEquals("/auth/v1/token", auth.requestUrl?.encodedPath)
        assertEquals(publicKey, auth.getHeader("apikey"))
        assertNull(auth.getHeader("Authorization"))
        assertEquals("person@example.test", JSONObject(auth.body.readUtf8()).getString("email"))
        val profile = server.takeRequest()
        assertEquals("/rest/v1/profiles", profile.requestUrl?.encodedPath)
        assertEquals("eq.user-1", profile.requestUrl?.queryParameter("id"))
        assertEquals("display_name", profile.requestUrl?.queryParameter("select"))
        assertEquals("Bearer user-session-token", profile.getHeader("Authorization"))
        assertEquals("Profile Name", account.displayName)
        assertEquals("person@example.test", account.email)
        assertFalse(account.toString().contains("user-session-token"))
    }

    @Test fun missingProfilesDoNotPreventValidAccountSignIn() = runBlocking {
        server.enqueue(json(session().toString()))
        server.enqueue(json("""{"code":"42P01"}""", 404))
        assertEquals("Metadata Name", api.signIn("person@example.test", "test-password").displayName)
    }

    @Test fun signUpWithoutSessionRequiresEmailConfirmationAndDoesNotInventLogin() = runBlocking {
        server.enqueue(json("""{"id":"user-1","email":"person@example.test"}"""))
        assertNull(api.signUp("person@example.test", "test-password"))
        val request = server.takeRequest()
        assertEquals("/auth/v1/signup", request.path)
        assertEquals(1, server.requestCount)
    }

    @Test fun signUpWithSessionCanUseAuthenticatedOwnProfile() = runBlocking {
        server.enqueue(json(session().toString()))
        server.enqueue(json("""[{"display_name":"Registered Person"}]"""))
        assertEquals("Registered Person", api.signUp("person@example.test", "test-password")?.displayName)
    }

    @Test fun invalidSignInKeepsServerMessagesOutOfUi() = runBlocking {
        server.enqueue(json("""{"error_code":"invalid_credentials","message":"do-not-display-private-body"}""", 400))
        val error = expectApiError { api.signIn("person@example.test", "test-password") }
        assertEquals("The email or password is incorrect.", error.message)
    }

    @Test fun unconfirmedEmailExplainsNextStepWithoutSigningIn() = runBlocking {
        server.enqueue(json("""{"error_code":"email_not_confirmed"}""", 400))
        val error = expectApiError { api.signIn("person@example.test", "test-password") }
        assertEquals("Confirm your email address before signing in.", error.message)
        assertEquals(1, server.requestCount)
    }

    @Test fun logoutIsScopedToCurrentSessionRatherThanGlobalSessions() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        api.signOut(Account("Person", "person@example.test", "user-session-token"))
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/auth/v1/logout", request.requestUrl?.encodedPath)
        assertEquals("local", request.requestUrl?.queryParameter("scope"))
        assertEquals("Bearer user-session-token", request.getHeader("Authorization"))
        assertEquals(publicKey, request.getHeader("apikey"))
    }

    @Test fun profileMediaUsesSupabasePublicRpcAndParsesRealReviewFields() = runBlocking {
        server.enqueue(json("""{"photo_url":"https://images.example.test/cafe.jpg","review_count":67,
          "score_counts":{"5":42,"4":20,"3":5,"2":0,"1":0},
          "location_reviews_link":"https://www.google.com/maps/place/Cafe/data=!9m1!1b1",
          "reviews":[{"id":"review-1","author":"Test diner","rating":5,"text":"Fresh lunch and friendly service."}]}"""))
        val details = api.profileDetails("10000000-0000-4000-8000-000000000001")
        val request = server.takeRequest()
        assertEquals("/rest/v1/rpc/restaurant_profile_details", request.path)
        assertEquals(publicKey, request.getHeader("apikey"))
        assertNull(request.getHeader("Authorization"))
        assertEquals("10000000-0000-4000-8000-000000000001", JSONObject(request.body.readUtf8()).getString("restaurant_id"))
        assertEquals("https://images.example.test/cafe.jpg", details.photoUrl)
        assertEquals("https://www.google.com/maps/place/Cafe/data=!9m1!1b1", details.locationReviewsLink)
        assertEquals(67, details.reviewCount)
        assertEquals(42, details.scoreCounts[5])
        assertEquals("Fresh lunch and friendly service.", details.reviews.single().text)
    }
    @Test fun profileMediaDropsUnsafePhotoLinksAndDoesNotInventMissingReviews() = runBlocking {
        for (url in listOf("http://images.example.test/a.jpg", "javascript:alert(1)", "https://user:password@images.example.test/a.jpg")) {
            server.enqueue(json(JSONObject().put("photo_url", url).put("location_reviews_link", url).toString()))
            val details = api.profileDetails("cafe")
            assertNull(details.photoUrl); assertNull(details.locationReviewsLink); assertNull(details.reviewCount)
            assertTrue(details.reviews.isEmpty()); assertTrue(details.scoreCounts.isEmpty())
        }
    }
    @Test fun profileMediaErrorsDoNotExposeServerDetails() = runBlocking {
        server.enqueue(json("""{"code":"PGRST202","message":"private SQL data"}""", 404))
        val error = expectApiError { api.profileDetails("cafe") }
        assertEquals("Photos and reviews couldn't be loaded. Please try again.", error.message)
    }
    @Test fun profileMediaRejectsInvalidCountsAndReviewRatings() = runBlocking<Unit> {
        server.enqueue(json("""{"review_count":-1}"""))
        expectApiError { api.profileDetails("cafe") }
        server.enqueue(json("""{"reviews":[{"id":"bad","text":"A real review","rating":6}]}"""))
        expectApiError { api.profileDetails("cafe") }
    }
    @Test fun profileNotFoundIsAnEmptyOptionalResult() = runBlocking {
        server.enqueue(json("null"))
        val details = api.profileDetails("not-found")
        assertNull(details.photoUrl); assertTrue(details.reviews.isEmpty())
    }

    private suspend fun expectApiError(block: suspend () -> Any?): ApiException {
        try { block() } catch (error: ApiException) { return error }
        throw AssertionError("Expected an ApiException")
    }

    private fun json(body: String, status: Int = 200) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)

    private fun restaurant() = JSONObject().put("id", "food-court-1")
        .put("name", "UMSKAL Food Court").put("cuisine", "Food court")
        .put("description", "Campus food court").put("address", "UMS Labuan")
        .put("latitude", to.latitude).put("longitude", to.longitude)
        .put("rating", 0).put("price_level", 1).put("emoji", "🍽️")
        .put("accent_hex", 0xffdef1e7L).put("tags", JSONArray().put("Food court"))
        .put("is_halal", true).put("opening_hours", "Check with the venue")

    private fun session() = JSONObject().put("access_token", "user-session-token")
        .put("user", JSONObject().put("id", "user-1").put("email", "person@example.test")
            .put("user_metadata", JSONObject().put("display_name", "Metadata Name")))

    private fun walking() = JSONObject("""
        {"code":"Ok","routes":[{"distance":289,"duration":240,
        "geometry":{"type":"LineString","coordinates":[[101.71,3.1478],[101.71,3.1491],[101.7113,3.1491]]},
        "legs":[{"steps":[
        {"name":"First Street","distance":145,"maneuver":{"type":"depart","bearing_after":0,"location":[101.71,3.1478]}},
        {"name":"Second Street","distance":144,"maneuver":{"type":"turn","modifier":"right","location":[101.71,3.1491]}},
        {"name":"","distance":0,"maneuver":{"type":"arrive","location":[101.7113,3.1491]}}
        ]}]}]}
    """.trimIndent())

    private fun jwt(role: String): String {
        val encode = Base64.getUrlEncoder().withoutPadding()
        return "${encode.encodeToString("{}".toByteArray())}.${encode.encodeToString("{\"role\":\"$role\"}".toByteArray())}.signature"
    }
}
