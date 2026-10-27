package com.example.dinefinder.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.ByteString.Companion.decodeBase64
import com.example.dinefinder.core.GeoPoint
import com.example.dinefinder.core.Restaurant
import com.example.dinefinder.core.RestaurantProfileDetails
import com.example.dinefinder.core.RestaurantReview
import com.example.dinefinder.core.RouteInstruction
import com.example.dinefinder.core.RoutePlan
import com.example.dinefinder.core.distanceMeters
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class SupabaseConfig(val url: String, val publishableKey: String) {
    val isConfigured: Boolean
        get() {
            val parsed = url.trim().toHttpUrlOrNull() ?: return false
            val local = parsed.host in setOf("localhost", "127.0.0.1", "::1")
            if ((!parsed.isHttps && !local) || parsed.username.isNotEmpty() ||
                parsed.password.isNotEmpty() || parsed.query != null || parsed.fragment != null
            ) return false
            val key = publishableKey.trim()
            if (key.isEmpty() || key.contains("YOUR_", ignoreCase = true) ||
                parsed.host.contains("YOUR_PROJECT", ignoreCase = true) ||
                key.startsWith("sb_secret_", ignoreCase = true)
            ) return false
            if (key.startsWith("sb_publishable_")) return true
            // Legacy anonymous keys are JWTs. Reject privileged or malformed
            // credentials instead of ever placing them in the Android client.
            return jwtRole(key) == "anon"
        }

    override fun toString(): String = "SupabaseConfig(url=$url, publishableKey=[redacted])"
}

data class Account(val displayName: String, val email: String, val accessToken: String) {
    override fun toString(): String =
        "Account(displayName=$displayName, email=$email, accessToken=[redacted])"
}

class ApiException(message: String) : Exception(message)

/** Supabase data/auth plus a separate pedestrian router that needs no key. */
class DineFinderApi(
    private val config: SupabaseConfig,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build(),
    private val routeEndpoint: String = "https://routing.openstreetmap.de/routed-foot/route/v1/foot/",
) {
    suspend fun nearby(center: GeoPoint): List<Restaurant> = withContext(Dispatchers.IO) {
        requirePoint(center)
        val body = JSONObject()
            .put("latitude", center.latitude)
            .put("longitude", center.longitude)
            .put("radius_meters", 1000)
        val request = publicRequest("rest/v1/rpc/nearby_restaurants")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val response = execute(request)
        if (response.status !in 200..299) {
            val code = runCatching { JSONObject(response.body).optString("code") }.getOrDefault("")
            if (response.status == 404 || code in setOf("PGRST202", "42P01")) {
                throw ApiException("Restaurant database is not set up yet. Run supabase/setup.sql in the Supabase SQL Editor.")
            }
            // Report only bounded protocol codes, never the server body, SQL details,
            // headers, keys or user data. A configured key is not proof of access.
            val safeCode = code.takeIf { it.matches(Regex("[A-Z0-9_]{1,24}")) }
            val diagnostic = "HTTP ${response.status}" + (safeCode?.let { ", code $it" } ?: "")
            val action = when {
                code == "PGRST203" -> "Multiple nearby_restaurants function signatures conflict. Check the function definitions in Supabase."
                code == "42501" || response.status == 403 -> "Supabase denied restaurant access. Check function/table grants and public read policies."
                response.status == 401 -> "Supabase rejected the public connection. Verify the project URL and publishable key belong to the same active project."
                response.status == 429 -> "Supabase is limiting requests. Wait before retrying."
                code in setOf("42804", "42702", "42883") -> "The nearby_restaurants database function failed. Run the diagnostic SQL and check its definition."
                response.status in setOf(502, 503, 504) -> "The Supabase service is temporarily unavailable. Check the project status and retry."
                else -> "The nearby restaurant request failed. Run the diagnostic SQL in Supabase."
            }
            throw ApiException("$action ($diagnostic)")
        }
        parseSafely("The restaurant service returned invalid data.") {
            val rows = JSONArray(response.body)
            if (rows.length() > 200) throw ApiException("The restaurant response is too large.")
            (0 until rows.length()).map { restaurant(rows.getJSONObject(it)) }
                .filter { distanceMeters(center, it.location) <= 1000.5 }
                .sortedBy { distanceMeters(center, it.location) }
        }
    }

    suspend fun profileDetails(restaurantId: String): RestaurantProfileDetails = withContext(Dispatchers.IO) {
        if (restaurantId.isBlank() || restaurantId.length > 128) throw ApiException("Choose a restaurant to view its profile.")
        val body = JSONObject().put("restaurant_id", restaurantId)
        val response = execute(publicRequest("rest/v1/rpc/restaurant_profile_details")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE)).build())
        if (response.status !in 200..299) throw ApiException("Photos and reviews couldn't be loaded. Please try again.")
        parseSafely("Photos and reviews couldn't be read. Please try again.") {
            if (response.body.trim() == "null") return@parseSafely RestaurantProfileDetails()
            val data = JSONObject(response.body)
            fun count(value: Any?): Int? {
                if (value == null || value == JSONObject.NULL) return null
                val number = (value as? Number)?.toDouble()
                    ?: throw ApiException("The review information is invalid.")
                if (!number.isFinite() || number !in 0.0..1_000_000_000.0 || number % 1.0 != 0.0)
                    throw ApiException("The review information is invalid.")
                return number.toInt()
            }
            val scores = data.optJSONObject("score_counts")
            val breakdown = (1..5).mapNotNull { stars -> count(scores?.opt(stars.toString()))?.let { stars to it } }.toMap()
            val rows = data.optJSONArray("reviews") ?: JSONArray()
            if (rows.length() > 10) throw ApiException("The review response is too large.")
            val reviews = (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                val rating = count(row.opt("rating"))
                if (rating != null && rating !in 1..5) throw ApiException("The review rating is invalid.")
                val text = nonempty(row, "text")
                if (text.length > 5000) throw ApiException("The review response is too large.")
                RestaurantReview(nonempty(row, "id"), string(row, "author") ?: "Diner", rating, text)
            }
            val photo = string(data, "photo_url")?.takeIf { it.length <= 4096 }?.toHttpUrlOrNull()
                ?.takeIf { it.isHttps && it.username.isEmpty() && it.password.isEmpty() }
            RestaurantProfileDetails(photo?.toString(), count(data.opt("review_count")), breakdown, reviews)
        }
    }

    suspend fun walkingRoute(from: GeoPoint, to: GeoPoint): RoutePlan = withContext(Dispatchers.IO) {
        requirePoint(from)
        requirePoint(to)
        if (distanceMeters(from, to) > 10_000) {
            throw ApiException("Choose a walking destination within 10 km.")
        }
        val base = routeEndpoint.toHttpUrlOrNull()
            ?: throw ApiException("Walking directions have not been configured.")
        val local = base.host in setOf("localhost", "127.0.0.1", "::1")
        if ((!base.isHttps && !local) || !base.encodedPath.endsWith("/") ||
            base.username.isNotEmpty() || base.password.isNotEmpty() ||
            base.query != null || base.fragment != null
        ) throw ApiException("Walking directions have not been configured.")
        val coordinates = "${from.longitude},${from.latitude};${to.longitude},${to.latitude}"
        val url = base.newBuilder()
            .addPathSegment(coordinates)
            .addQueryParameter("overview", "full")
            .addQueryParameter("geometries", "geojson")
            .addQueryParameter("steps", "true")
            .addQueryParameter("alternatives", "false")
            .addQueryParameter("radiuses", "100;100")
            .build()
        // No Supabase headers, tokens, or routing keys go to this service.
        val response = execute(Request.Builder().url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "DineFinder/0.1 (Android; github.com/rayimrahman-coder/DineFinder-App)")
            .get().build())
        if (response.status == 429) throw ApiException("Walking directions are busy. Wait before trying again.")
        if (response.status !in 200..299) {
            throw ApiException("Walking directions are temporarily unavailable.")
        }
        parseSafely("The walking service returned invalid directions.") {
            parseRoute(JSONObject(response.body), from, to)
        }
    }

    suspend fun signIn(email: String, password: String): Account = withContext(Dispatchers.IO) {
        requireCredentials(email, password)
        val request = publicRequest("auth/v1/token")
            .url(supabaseUrl("auth/v1/token").newBuilder().addQueryParameter("grant_type", "password").build())
            .post(JSONObject().put("email", email.trim()).put("password", password)
                .toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val response = execute(request)
        requireAuthSuccess(response, signingUp = false)
        val root = parseSafely("The account service returned an invalid session.") { JSONObject(response.body) }
        val account = parseSafely("The account service returned an invalid session.") {
            account(root) ?: throw ApiException("A signed-in session was not returned.")
        }
        withOwnProfile(account, user(root).getString("id"))
    }

    /** Null means Supabase requires email confirmation before creating a session. */
    suspend fun signUp(email: String, password: String): Account? = withContext(Dispatchers.IO) {
        requireCredentials(email, password)
        val request = publicRequest("auth/v1/signup")
            .post(JSONObject().put("email", email.trim()).put("password", password)
                .toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val response = execute(request)
        requireAuthSuccess(response, signingUp = true)
        val result = parseSafely("The account service returned an invalid response.") {
            val root = JSONObject(response.body)
            val session = account(root)
            if (session == null) {
                val user = root.optJSONObject("user") ?: root
                nonempty(user, "id")
                nonempty(user, "email")
            }
            session to root
        }
        result.first?.let { withOwnProfile(it, user(result.second).getString("id")) }
    }

    suspend fun signOut(account: Account): Unit = withContext(Dispatchers.IO) {
        if (account.accessToken.isBlank()) throw ApiException("Your account session has expired.")
        val request = publicRequest("auth/v1/logout")
            .url(supabaseUrl("auth/v1/logout").newBuilder().addQueryParameter("scope", "local").build())
            .header("Authorization", "Bearer ${account.accessToken}")
            .post("{}".toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val response = execute(request)
        if (response.status !in 200..299 && response.status != 401) {
            throw ApiException("Sign out could not be completed. Check your connection.")
        }
    }

    private fun publicRequest(path: String): Request.Builder {
        if (!config.isConfigured) throw ApiException("Connect your Supabase project to use live restaurants and accounts.")
        val key = config.publishableKey.trim()
        val request = Request.Builder().url(supabaseUrl(path))
            .header("apikey", key).header("Accept", "application/json")
        // New publishable keys are not JWTs and must not become bearer tokens.
        if (!key.startsWith("sb_publishable_")) request.header("Authorization", "Bearer $key")
        return request
    }

    private fun supabaseUrl(path: String): HttpUrl =
        "${config.url.trim().trimEnd('/')}/$path".toHttpUrlOrNull()
            ?: throw ApiException("The Supabase project URL is invalid.")

    private suspend fun execute(request: Request): WireResponse = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(
                    ApiException("Unable to connect. Check your internet connection and try again."),
                )
            }

            override fun onResponse(call: Call, response: Response) {
                val result = try {
                    response.use {
                        val source = it.body?.source()
                        source?.request(MAX_RESPONSE_BYTES + 1)
                        if (source != null && source.buffer.size > MAX_RESPONSE_BYTES) {
                            throw ApiException("The service returned too much data. Please try again.")
                        }
                        WireResponse(it.code, source?.readUtf8().orEmpty())
                    }
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(
                        if (error is ApiException) error else ApiException("The service response could not be read. Please try again."),
                    )
                    return
                }
                if (continuation.isActive) continuation.resume(result)
            }
        })
    }

    private fun requireAuthSuccess(response: WireResponse, signingUp: Boolean) {
        if (response.status in 200..299) return
        val code = runCatching { JSONObject(response.body).optString("error_code") }.getOrDefault("")
        val message = when {
            response.status == 429 -> "Too many attempts. Wait a moment before trying again."
            code == "email_not_confirmed" -> "Confirm your email address before signing in."
            code == "weak_password" -> "Choose a stronger password and try again."
            code in setOf("email_exists", "user_already_exists") -> "An account already exists for that email address."
            !signingUp && response.status in setOf(400, 401, 422) -> "The email or password is incorrect."
            signingUp && response.status in setOf(400, 422) -> "Check your email address and password, then try again."
            else -> "The account service is unavailable. Please try again later."
        }
        throw ApiException(message)
    }

    private fun account(root: JSONObject): Account? {
        val session = root.optJSONObject("session") ?: root
        val token = string(session, "access_token") ?: return null
        val user = user(root)
        nonempty(user, "id")
        val email = nonempty(user, "email")
        val metadata = user.optJSONObject("user_metadata")
        val name = metadata?.let { string(it, "display_name") ?: string(it, "full_name") }
            ?: email.substringBefore('@').ifBlank { "DineFinder user" }
        return Account(name, email, token)
    }

    private fun user(root: JSONObject): JSONObject =
        root.optJSONObject("user") ?: root.optJSONObject("session")?.optJSONObject("user")
            ?: throw ApiException("The account session is missing user information.")

    /** Profile RLS restricts this read to the signed-in user; old projects can
     *  still sign in using their Auth metadata before the profile migration. */
    private suspend fun withOwnProfile(account: Account, userId: String): Account = try {
        val endpoint = supabaseUrl("rest/v1/profiles").newBuilder()
            .addQueryParameter("select", "display_name")
            .addQueryParameter("id", "eq.$userId")
            .addQueryParameter("limit", "1")
            .build()
        val response = execute(publicRequest("rest/v1/profiles").url(endpoint)
            .header("Authorization", "Bearer ${account.accessToken}").get().build())
        if (response.status in 200..299) {
            val rows = JSONArray(response.body)
            val name = if (rows.length() > 0) string(rows.getJSONObject(0), "display_name") else null
            if (name == null) account else account.copy(displayName = name)
        } else account
    } catch (_: ApiException) {
        account
    } catch (_: JSONException) {
        account
    }

    private fun restaurant(row: JSONObject): Restaurant {
        val rating = number(row, "rating")
        val price = number(row, "price_level")
        if (rating !in 0.0..5.0 || price !in 1.0..4.0 || price % 1.0 != 0.0) {
            throw ApiException("The restaurant service returned invalid profile information.")
        }
        val rawTags = row.optJSONArray("tags")
        val tags = if (rawTags == null) emptyList() else
            (0 until rawTags.length()).mapNotNull { rawTags.opt(it) as? String }
        val profileTags = when (row.opt("is_halal")) {
            false -> tags.filterNot { it.equals("HALAL", ignoreCase = true) }
            true -> if (tags.none { it.equals("HALAL", ignoreCase = true) }) tags + "HALAL" else tags
            else -> tags
        }
        return Restaurant(
            id = nonempty(row, "id"), name = nonempty(row, "name"),
            cuisine = string(row, "cuisine") ?: "Restaurant",
            description = string(row, "description").orEmpty(),
            address = string(row, "address") ?: "Address not listed",
            location = point(row), rating = rating, priceLevel = price.toInt(),
            emoji = string(row, "emoji") ?: "🍽️",
            accentHex = (row.opt("accent_hex") as? Number)?.toLong() ?: 0xffdef1e7L,
            tags = profileTags,
            openingHours = string(row, "opening_hours") ?: "Check opening hours with the restaurant",
            isHalal = row.opt("is_halal") as? Boolean,
        )
    }

    private fun parseRoute(root: JSONObject, from: GeoPoint, to: GeoPoint): RoutePlan {
        if (root.optString("code") in setOf("NoRoute", "NoSegment")) {
            throw ApiException("No walking route was found for these locations.")
        }
        if (root.optString("code") != "Ok") throw ApiException("Walking directions are unavailable.")
        val route = root.getJSONArray("routes").getJSONObject(0)
        val distance = number(route, "distance")
        val duration = number(route, "duration")
        if (distance !in 0.0..10_000.0 || duration < 0 || (distance > 0 && duration == 0.0)) {
            throw ApiException("The service returned an invalid walking distance.")
        }
        val geometry = route.getJSONObject("geometry")
        if (geometry.optString("type") != "LineString") throw ApiException("The walking route has no valid path.")
        val rawPoints = geometry.getJSONArray("coordinates")
        if (rawPoints.length() !in 2..50_000) throw ApiException("The walking route has no valid path.")
        val points = (0 until rawPoints.length()).map { coordinates(rawPoints.getJSONArray(it)) }
        if (distanceMeters(from, points.first()) > 150 || distanceMeters(to, points.last()) > 150) {
            throw ApiException("A nearby walking path could not be found for these locations.")
        }
        val instructions = mutableListOf<RouteInstruction>()
        val legs = route.getJSONArray("legs")
        for (legIndex in 0 until legs.length()) {
            val steps = legs.getJSONObject(legIndex).getJSONArray("steps")
            for (stepIndex in 0 until steps.length()) {
                val step = steps.getJSONObject(stepIndex)
                val maneuver = step.getJSONObject("maneuver")
                val stepDistance = number(step, "distance")
                if (stepDistance !in 0.0..10_000.0) throw ApiException("A walking direction has an invalid distance.")
                instructions += RouteInstruction(
                    point = coordinates(maneuver.getJSONArray("location")),
                    text = instruction(maneuver, step), distanceMeters = stepDistance,
                )
            }
        }
        if (instructions.isEmpty()) throw ApiException("The walking route contains no directions.")
        return RoutePlan(points = points, instructions = instructions, distanceMeters = distance, durationSeconds = duration)
    }

    private fun instruction(maneuver: JSONObject, step: JSONObject): String {
        val type = nonempty(maneuver, "type")
        val modifier = string(maneuver, "modifier").orEmpty().replace('_', ' ')
        val name = string(step, "name").orEmpty().take(160)
        val road = if (name.isEmpty()) "" else " onto $name"
        return when (type) {
            "depart" -> {
                val bearing = (maneuver.opt("bearing_after") as? Number)?.toDouble()
                val direction = if (bearing != null && bearing.isFinite() && bearing in 0.0..360.0) {
                    val compass = listOf("north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest")
                    " ${compass[((bearing + 22.5) / 45).toInt() % 8]}"
                } else ""
                "Start walking$direction${if (name.isEmpty()) "" else " on $name"}"
            }
            "arrive" -> "Arrive at your destination${if (modifier in setOf("left", "right")) " on the $modifier" else ""}"
            "turn", "end of road" -> "Turn${if (modifier.isEmpty()) "" else " $modifier"}$road"
            "fork" -> "${if (modifier.isEmpty()) "Continue" else "Keep $modifier"} at the fork$road"
            "roundabout", "rotary" -> {
                val exit = (maneuver.opt("exit") as? Number)?.toInt()
                if (exit != null && exit > 0) "At the roundabout, take exit $exit$road" else "Continue around the roundabout$road"
            }
            "exit roundabout", "exit rotary" -> "Exit the roundabout$road"
            "continue", "new name" -> if (modifier == "uturn") "Turn back$road" else
                "Continue${if (modifier.isEmpty() || modifier == "straight") "" else " $modifier"}$road"
            "merge" -> "Join the path$road"
            else -> "Follow the walking path$road"
        }
    }

    private fun point(json: JSONObject): GeoPoint =
        GeoPoint(number(json, "latitude"), number(json, "longitude")).also(::requirePoint)

    private fun coordinates(array: JSONArray): GeoPoint {
        if (array.length() < 2) throw ApiException("A walking location is invalid.")
        val lon = array.get(0) as? Number ?: throw ApiException("A walking location is invalid.")
        val lat = array.get(1) as? Number ?: throw ApiException("A walking location is invalid.")
        return GeoPoint(lat.toDouble(), lon.toDouble()).also(::requirePoint)
    }

    private fun number(json: JSONObject, key: String): Double {
        val value = (json.opt(key) as? Number)?.toDouble()
        if (value == null || !value.isFinite()) throw ApiException("The service returned invalid numeric data.")
        return value
    }

    private fun nonempty(json: JSONObject, key: String): String =
        string(json, key) ?: throw ApiException("The service returned incomplete information.")

    private fun string(json: JSONObject, key: String): String? =
        (json.opt(key) as? String)?.trim()?.takeIf { it.isNotEmpty() }

    private fun requirePoint(point: GeoPoint) {
        if (!point.latitude.isFinite() || !point.longitude.isFinite() ||
            point.latitude !in -90.0..90.0 || point.longitude !in -180.0..180.0
        ) throw ApiException("A valid GPS location is required.")
    }

    private fun requireCredentials(email: String, password: String) {
        if (!EMAIL.matches(email.trim()) || password.isEmpty()) {
            throw ApiException("Enter a valid email address and password.")
        }
    }

    private inline fun <T> parseSafely(message: String, block: () -> T): T = try {
        block()
    } catch (error: JSONException) {
        throw ApiException(message)
    } catch (error: IllegalArgumentException) {
        throw ApiException(message)
    }

    private data class WireResponse(val status: Int, val body: String)

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
        private const val MAX_RESPONSE_BYTES = 2L * 1024 * 1024
    }
}

private fun jwtRole(key: String): String? = runCatching {
    val parts = key.split('.')
    if (parts.size != 3) return@runCatching null
    val payload = parts[1].replace('-', '+').replace('_', '/').decodeBase64()?.utf8() ?: return@runCatching null
    JSONObject(payload).optString("role").takeIf { it.isNotEmpty() }
}.getOrNull()
