package com.example.dinefinder.ar

import com.example.dinefinder.settings.GoogleMapsAppIdentity
import com.google.gson.JsonParser
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WalkingRouteClientTest {
    private val identity = GoogleMapsAppIdentity("com.example.dinefinder", "A".repeat(40))
    private val origin = RouteCoordinate(38.5, -120.2)
    private val destination = RouteCoordinate(43.252, -126.453)
    private val validResponse = RoutesResponse(listOf(ComputedRoute(RoutePolyline(ENCODED_ROUTE))))

    @Test
    fun routesRequestUsesPostWalkingBodyHeadersAndModernResponseWithoutRealNetwork() = runBlocking {
        var captured: Request? = null
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            captured = chain.request()
            okhttp3.Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"routes":[{"polyline":{"encodedPolyline":"$ENCODED_ROUTE"}}]}"""
                    .toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        val service = Retrofit.Builder()
            .baseUrl("https://routes.googleapis.com/")
            .client(http)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RoutesService::class.java)
        val result = WalkingRouteClient(service).fetch("test-api-key", identity, origin, destination)
        val request = captured!!
        assertEquals("POST", request.method)
        assertEquals("https://routes.googleapis.com/directions/v2:computeRoutes", request.url.toString())
        assertEquals("test-api-key", request.header("X-Goog-Api-Key"))
        assertEquals(ROUTE_RESPONSE_FIELD_MASK, request.header("X-Goog-FieldMask"))
        assertTrue(request.header("X-Goog-FieldMask")!!.contains("routes.legs.steps.navigationInstruction"))
        assertTrue(request.header("X-Goog-FieldMask")!!.contains("routes.legs.steps.polyline.encodedPolyline"))
        assertEquals(identity.packageName, request.header("X-Android-Package"))
        assertEquals(identity.certificateSha1, request.header("X-Android-Cert"))
        val buffer = Buffer()
        request.body!!.writeTo(buffer)
        val requestJson = buffer.readUtf8()
        assertFalse(requestJson.contains("test-api-key"))
        val body = JsonParser.parseString(requestJson).asJsonObject
        assertEquals("WALK", body["travelMode"].asString)
        assertEquals("ENCODED_POLYLINE", body["polylineEncoding"].asString)
        assertEquals("HIGH_QUALITY", body["polylineQuality"].asString)
        assertFalse(body["computeAlternativeRoutes"].asBoolean)
        val latLng = body["origin"].asJsonObject["location"].asJsonObject["latLng"].asJsonObject
        assertEquals(origin.latitude, latLng["latitude"].asDouble, 0.0)
        assertEquals(origin.longitude, latLng["longitude"].asDouble, 0.0)
        val points = (result as WalkingRouteResult.Success).points
        assertEquals(3, points.size)
        assertEquals(origin.latitude, points.first().latitude, 1e-6)
        assertEquals(destination.longitude, points.last().longitude, 1e-6)
    }

    @Test
    fun missingKeyNeverCallsServiceEvenWhenAppIdentityIsMissing() = runBlocking {
        val service = FakeRoutesService { error("No request should be made") }
        assertFailure(WalkingRouteFailure.MISSING_KEY,
            WalkingRouteClient(service).fetch(" \n ", null, origin, destination))
        assertEquals(0, service.calls)
    }

    @Test
    fun missingSigningIdentityAndHeaderInjectionNeverMakeNetworkRequests() = runBlocking {
        val service = FakeRoutesService { error("No request should be made") }
        val client = WalkingRouteClient(service)
        assertFailure(WalkingRouteFailure.MISSING_APP_IDENTITY,
            client.fetch("test-key", null, origin, destination))
        assertFailure(WalkingRouteFailure.MISSING_APP_IDENTITY,
            client.fetch("test-key", identity.copy(certificateSha1 = "AA:BB"), origin, destination))
        assertFailure(WalkingRouteFailure.KEY_REJECTED,
            client.fetch("test-key\nInjected: value", identity, origin, destination))
        assertEquals(0, service.calls)
    }

    @Test
    fun invalidCoordinatesAreRejectedBeforeNetwork() = runBlocking {
        val service = FakeRoutesService { error("No request should be made") }
        val client = WalkingRouteClient(service)
        assertFailure(WalkingRouteFailure.INVALID_REQUEST,
            client.fetch("test-key", identity, origin.copy(latitude = Double.NaN), destination))
        assertFailure(WalkingRouteFailure.INVALID_REQUEST,
            client.fetch("test-key", identity, origin, destination.copy(longitude = 181.0)))
        assertEquals(0, service.calls)
    }

    @Test
    fun googleHttpFailuresHaveSafeDistinctUserStates() = runBlocking {
        val expected = mapOf(
            400 to WalkingRouteFailure.INVALID_REQUEST,
            401 to WalkingRouteFailure.KEY_REJECTED,
            403 to WalkingRouteFailure.KEY_REJECTED,
            429 to WalkingRouteFailure.QUOTA_EXCEEDED,
            500 to WalkingRouteFailure.UNAVAILABLE
        )
        for ((status, reason) in expected) {
            val service = FakeRoutesService {
                Response.error(status, "{\"error\":\"credential-containing body must not reach UI\"}"
                    .toResponseBody("application/json".toMediaType()))
            }
            val result = WalkingRouteClient(service).fetch("test-key", identity, origin, destination)
            assertFailure(reason, result)
            assertFalse(result.toString().contains("credential-containing"))
        }
        assertTrue(WalkingRouteFailure.KEY_REJECTED.needsSettings)
        assertFalse(WalkingRouteFailure.QUOTA_EXCEEDED.needsSettings)
    }

    @Test
    fun emptyRouteIsNotReportedAsLoaded() = runBlocking {
        for (body in listOf(RoutesResponse(), RoutesResponse(emptyList()))) {
            val service = FakeRoutesService { Response.success(body) }
            assertFailure(WalkingRouteFailure.NO_ROUTE,
                WalkingRouteClient(service).fetch("test-key", identity, origin, destination))
        }
    }

    @Test
    fun absentEmptyMalformedOrSinglePointPolylineNeverCreatesFakeRoute() = runBlocking {
        val bodies = listOf(
            RoutesResponse(listOf(ComputedRoute())),
            RoutesResponse(listOf(ComputedRoute(RoutePolyline("")))),
            RoutesResponse(listOf(ComputedRoute(RoutePolyline("_")))),
            RoutesResponse(listOf(ComputedRoute(RoutePolyline("??")))),
            RoutesResponse(listOf(ComputedRoute(RoutePolyline("????"))))
        )
        for (body in bodies) {
            val service = FakeRoutesService { Response.success(body) }
            assertFailure(WalkingRouteFailure.INVALID_RESPONSE,
                WalkingRouteClient(service).fetch("test-key", identity, origin, destination))
        }
    }

    @Test
    fun offlineFailureDoesNotExposeExceptionAndAllowsRetry() = runBlocking {
        val service = FakeRoutesService { throw IOException("secret test-api-key") }
        assertFailure(WalkingRouteFailure.NETWORK,
            WalkingRouteClient(service).fetch("test-key", identity, origin, destination))
    }

    @Test
    fun cancellationPropagatesInsteadOfAppearingAsRouteFailure() = runBlocking {
        val service = FakeRoutesService { throw CancellationException("paused") }
        try {
            WalkingRouteClient(service).fetch("test-key", identity, origin, destination)
            error("Expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(1, service.calls)
        }
    }

    @Test
    fun successfulRouteUsesActualDecodedGooglePoints() = runBlocking {
        val service = FakeRoutesService { Response.success(validResponse) }
        val result = WalkingRouteClient(service).fetch("  test-key  ", identity, origin, destination)
        assertEquals("test-key", service.key)
        val points = (result as WalkingRouteResult.Success).points
        assertEquals(3, points.size)
        assertEquals(40.7, points[1].latitude, 1e-6)
        assertEquals(-120.95, points[1].longitude, 1e-6)
    }

    @Test
    fun providerStepsKeepInstructionsManeuversAndActualStepGeometry() = runBlocking {
        val middle = RouteCoordinate(40.7, -120.95)
        val first = providerStep(listOf(origin, middle), "Head north on First Street", "DEPART")
        val second = providerStep(listOf(middle, destination), "Turn left onto Second Street", "TURN_LEFT")
        val response = validResponse.copy(routes = listOf(ComputedRoute(RoutePolyline(ENCODED_ROUTE),
            listOf(ComputedRouteLeg(listOf(first, second))))))
        val service = FakeRoutesService { Response.success(response) }
        val result = WalkingRouteClient(service).fetch("test-key", identity, origin, destination) as WalkingRouteResult.Success
        assertEquals(2, result.steps.size)
        assertEquals("Head north on First Street", result.steps[0].instruction)
        assertEquals("TURN_LEFT", result.steps[1].maneuver)
        assertEquals(middle.latitude, result.steps[1].points.first().latitude, 1e-6)
        assertEquals(middle.longitude, result.steps[1].points.first().longitude, 1e-6)
        assertEquals(destination.latitude, result.steps[1].points.last().latitude, 1e-6)
        assertEquals(destination.longitude, result.steps[1].points.last().longitude, 1e-6)
    }

    @Test
    fun malformedOrDisconnectedStepDataFallsBackToActualRoutePolyline() = runBlocking {
        val step = providerStep(listOf(origin, destination), "Turn left", "TURN_LEFT")
        val malformedSteps = listOf(
            listOf(step.copy(startLocation = null)),
            listOf(step.copy(polyline = RoutePolyline("_"))),
            listOf(step.copy(endLocation = RouteStepLocation(RouteStepLatLng(0.0, 0.0)))),
            listOf(step.copy(distanceMeters = -1))
        )
        for (steps in malformedSteps) {
            val response = validResponse.copy(routes = listOf(ComputedRoute(RoutePolyline(ENCODED_ROUTE),
                listOf(ComputedRouteLeg(steps)))))
            val service = FakeRoutesService { Response.success(response) }
            val result = WalkingRouteClient(service).fetch("test-key", identity, origin, destination) as WalkingRouteResult.Success
            assertEquals(3, result.points.size)
            assertTrue(result.steps.isEmpty())
        }
    }

    @Test
    fun twentyMetreStepGapUsesCompleteProviderRouteInsteadOfUnwalkableStepJump() = runBlocking {
        fun point(eastMetres: Double) = RouteCoordinate(0.0, Math.toDegrees(eastMetres / 6_371_000.0))
        val points = listOf(point(0.0), point(100.0), point(120.0), point(200.0))
        val steps = listOf(providerStep(points.take(2), "Head east", "DEPART"),
            providerStep(points.drop(2), "Continue east", "STRAIGHT"))
        val polyline = PolyUtil.encode(points.map { LatLng(it.latitude, it.longitude) })
        val service = FakeRoutesService { Response.success(RoutesResponse(listOf(ComputedRoute(
            RoutePolyline(polyline), listOf(ComputedRouteLeg(steps)))))) }
        val result = WalkingRouteClient(service).fetch("test-key", identity, points.first(), points.last()) as WalkingRouteResult.Success
        assertEquals(4, result.points.size)
        assertTrue(result.steps.isEmpty())
    }

    private fun providerStep(points: List<RouteCoordinate>, instruction: String, maneuver: String) = ComputedRouteStep(
        polyline = RoutePolyline(PolyUtil.encode(points.map { LatLng(it.latitude, it.longitude) })),
        startLocation = RouteStepLocation(RouteStepLatLng(points.first().latitude, points.first().longitude)),
        endLocation = RouteStepLocation(RouteStepLatLng(points.last().latitude, points.last().longitude)),
        navigationInstruction = RouteNavigationInstruction(instruction, maneuver),
        distanceMeters = 100
    )

    private fun assertFailure(reason: WalkingRouteFailure, result: WalkingRouteResult) {
        assertEquals(WalkingRouteResult.Failure(reason), result)
    }

    private class FakeRoutesService(private val result: () -> Response<RoutesResponse>) : RoutesService {
        var calls = 0
        var key: String? = null
        override suspend fun computeWalkingRoute(
            apiKey: String, packageName: String, certificateSha1: String, request: WalkingRouteRequest
        ): Response<RoutesResponse> {
            calls++
            key = apiKey
            return result()
        }
    }

    companion object {
        private const val ENCODED_ROUTE = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"
    }
}
