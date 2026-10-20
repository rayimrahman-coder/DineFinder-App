package org.dinefinder.app.data

import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.dinefinder.app.core.GeoPoint
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
