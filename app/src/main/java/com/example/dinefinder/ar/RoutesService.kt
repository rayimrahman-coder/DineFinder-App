package com.example.dinefinder.ar

import com.example.dinefinder.settings.GoogleMapsAppIdentity
import com.google.gson.JsonParseException
import com.google.maps.android.PolyUtil
import kotlinx.coroutines.CancellationException
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import java.io.IOException

/** Routes API credentials stay in headers, never in a URL or request body. */
internal interface RoutesService {
    @Headers("X-Goog-FieldMask: " + ROUTE_RESPONSE_FIELD_MASK)
    @POST("directions/v2:computeRoutes")
    suspend fun computeWalkingRoute(
        @Header("X-Goog-Api-Key") apiKey: String,
        @Header("X-Android-Package") packageName: String,
        @Header("X-Android-Cert") certificateSha1: String,
        @Body request: WalkingRouteRequest
    ): Response<RoutesResponse>
}

internal data class WalkingRouteRequest(
    val origin: RouteWaypoint,
    val destination: RouteWaypoint,
    val travelMode: String = "WALK",
    val polylineEncoding: String = "ENCODED_POLYLINE",
    val polylineQuality: String = "HIGH_QUALITY",
    val computeAlternativeRoutes: Boolean = false
)

internal data class RouteWaypoint(val location: RouteWaypointLocation)
internal data class RouteWaypointLocation(val latLng: RouteCoordinate)
internal data class RoutesResponse(val routes: List<ComputedRoute>? = null)
internal data class ComputedRoute(val polyline: RoutePolyline? = null, val legs: List<ComputedRouteLeg>? = null)
internal data class RoutePolyline(val encodedPolyline: String? = null)
internal data class ComputedRouteLeg(val steps: List<ComputedRouteStep>? = null)
internal data class ComputedRouteStep(
    val polyline: RoutePolyline? = null,
    val startLocation: RouteStepLocation? = null,
    val endLocation: RouteStepLocation? = null,
    val navigationInstruction: RouteNavigationInstruction? = null,
    val distanceMeters: Int? = null
)
internal data class RouteStepLocation(val latLng: RouteStepLatLng? = null)
internal data class RouteStepLatLng(val latitude: Double? = null, val longitude: Double? = null)
internal data class RouteNavigationInstruction(val instructions: String? = null, val maneuver: String? = null)
internal data class WalkingRouteStep(
    val points: List<RouteCoordinate>,
    val instruction: String,
    val maneuver: String?,
    val distanceMeters: Int?
)

internal const val ROUTE_RESPONSE_FIELD_MASK = "routes.polyline.encodedPolyline," +
    "routes.legs.steps.polyline.encodedPolyline,routes.legs.steps.startLocation," +
    "routes.legs.steps.endLocation,routes.legs.steps.navigationInstruction," +
    "routes.legs.steps.distanceMeters"

internal sealed interface WalkingRouteResult {
    data class Success(val points: List<RouteCoordinate>, val steps: List<WalkingRouteStep> = emptyList()) : WalkingRouteResult
    data class Failure(val reason: WalkingRouteFailure) : WalkingRouteResult
}

internal enum class WalkingRouteFailure(val needsSettings: Boolean = false) {
    MISSING_KEY(true), MISSING_APP_IDENTITY(true), INVALID_REQUEST(true), KEY_REJECTED(true),
    QUOTA_EXCEEDED, NO_ROUTE, NETWORK, UNAVAILABLE, INVALID_RESPONSE
}

/** Converts protocol failures into safe UI states without exposing Google error bodies or keys. */
internal class WalkingRouteClient(private val service: RoutesService) {
    suspend fun fetch(
        apiKey: String,
        identity: GoogleMapsAppIdentity?,
        origin: RouteCoordinate,
        destination: RouteCoordinate
    ): WalkingRouteResult {
        val key = apiKey.trim()
        if (key.isEmpty()) return failure(WalkingRouteFailure.MISSING_KEY)
        if (key.any { it.isWhitespace() || it.isISOControl() }) {
            return failure(WalkingRouteFailure.KEY_REJECTED)
        }
        if (identity == null || identity.packageName.isBlank() ||
            !identity.certificateSha1.matches(Regex("[0-9A-F]{40}"))) {
            return failure(WalkingRouteFailure.MISSING_APP_IDENTITY)
        }
        if (!origin.isValid() || !destination.isValid()) {
            return failure(WalkingRouteFailure.INVALID_REQUEST)
        }
        return try {
            val response = service.computeWalkingRoute(
                key, identity.packageName, identity.certificateSha1,
                WalkingRouteRequest(
                    RouteWaypoint(RouteWaypointLocation(origin)),
                    RouteWaypoint(RouteWaypointLocation(destination))
                )
            )
            if (!response.isSuccessful) {
                failure(when (response.code()) {
                    400 -> WalkingRouteFailure.INVALID_REQUEST
                    401, 403 -> WalkingRouteFailure.KEY_REJECTED
                    429 -> WalkingRouteFailure.QUOTA_EXCEEDED
                    else -> WalkingRouteFailure.UNAVAILABLE
                })
            } else {
                val body = response.body() ?: return failure(WalkingRouteFailure.INVALID_RESPONSE)
                val route = body.routes?.firstOrNull()
                    ?: return failure(WalkingRouteFailure.NO_ROUTE)
                val polyline = route.polyline?.encodedPolyline
                    ?.takeIf { it.isNotBlank() }
                    ?: return failure(WalkingRouteFailure.INVALID_RESPONSE)
                val points = PolyUtil.decode(polyline).map { RouteCoordinate(it.latitude, it.longitude) }
                if (points.size < 2 || points.any { !it.isValid() } ||
                    points.zipWithNext().none { (start, end) -> routeDistanceMeters(start, end) > .1 }) {
                    failure(WalkingRouteFailure.INVALID_RESPONSE)
                } else {
                    WalkingRouteResult.Success(points, readSteps(route, points))
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: JsonParseException) {
            failure(WalkingRouteFailure.INVALID_RESPONSE)
        } catch (_: IOException) {
            failure(WalkingRouteFailure.NETWORK)
        } catch (_: RuntimeException) {
            failure(WalkingRouteFailure.INVALID_RESPONSE)
        } catch (_: Exception) {
            failure(WalkingRouteFailure.UNAVAILABLE)
        }
    }

    private fun failure(reason: WalkingRouteFailure) = WalkingRouteResult.Failure(reason)

    private fun readSteps(route: ComputedRoute, routePoints: List<RouteCoordinate>): List<WalkingRouteStep> {
        val legs = route.legs?.takeIf { it.isNotEmpty() && it.all { leg -> !leg.steps.isNullOrEmpty() } }
            ?: return emptyList()
        val steps = legs.flatMap { it.steps.orEmpty() }
        val decoded = steps.map { step ->
            try {
                val start = step.startLocation?.coordinate() ?: return emptyList()
                val end = step.endLocation?.coordinate() ?: return emptyList()
                val polyline = step.polyline?.encodedPolyline?.takeIf { it.isNotBlank() }
                    ?: return emptyList()
                val points = PolyUtil.decode(polyline).map { RouteCoordinate(it.latitude, it.longitude) }
                if (points.size < 2 || points.any { !it.isValid() } ||
                    points.zipWithNext().none { (a, b) -> routeDistanceMeters(a, b) > .1 } ||
                    routeDistanceMeters(start, points.first()) > 2.0 ||
                    routeDistanceMeters(end, points.last()) > 2.0 ||
                    step.distanceMeters?.let { it < 0 } == true) return emptyList()
                WalkingRouteStep(points, step.navigationInstruction?.instructions?.trim().orEmpty(),
                    step.navigationInstruction?.maneuver, step.distanceMeters)
            } catch (_: RuntimeException) {
                return emptyList()
            }
        }
        if (decoded.zipWithNext().any { (a, b) -> routeDistanceMeters(a.points.last(), b.points.first()) > 2.0 }) {
            return emptyList()
        }
        if (routeDistanceMeters(decoded.first().points.first(), routePoints.first()) > 2.0 ||
            routeDistanceMeters(decoded.last().points.last(), routePoints.last()) > 2.0) return emptyList()
        return decoded
    }

    private fun RouteStepLocation.coordinate(): RouteCoordinate? {
        val coordinate = latLng ?: return null
        val latitude = coordinate.latitude ?: return null
        val longitude = coordinate.longitude ?: return null
        return RouteCoordinate(latitude, longitude).takeIf { it.isValid() }
    }
    private fun RouteCoordinate.isValid() = latitude.isFinite() && longitude.isFinite() &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0
}
