package com.example.dinefinder.places

import com.example.dinefinder.data.DiscoveryLocation
import com.example.dinefinder.data.RestaurantDiscovery
import com.example.dinefinder.settings.GoogleMapsAppIdentity
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Maps protocol failures to safe UI states, without returning Google error bodies or exception text. */
class PlacesClient internal constructor(private val service: PlacesService) {
    suspend fun searchNearby(
        apiKey: String,
        identity: GoogleMapsAppIdentity?,
        location: DiscoveryLocation,
        radiusMeters: Double
    ): PlacesSearchResult {
        val credentials = credentials(apiKey, identity)
        credentials.failure?.let { return PlacesSearchResult.Failure(it) }
        if (!location.isValid || !radiusMeters.isFinite() || radiusMeters <= 0.0 ||
            radiusMeters > 50_000.0) return PlacesSearchResult.Failure(PlacesFailure.CONFIG)
        return request(
            call = {
                service.nearby(credentials.key, credentials.packageName, credentials.certificate,
                    NearbyRequest(NearbyLocationRestriction(NearbyCircle(
                        PlaceCoordinate(location.latitude, location.longitude), radiusMeters))))
            },
            failure = { PlacesSearchResult.Failure(it) },
            success = { body ->
                val places = body.places.orEmpty().mapNotNull { it?.summary() }
                    .distinctBy { it.id }
                    .filter { place ->
                        RestaurantDiscovery.distanceMeters(location,
                            DiscoveryLocation(place.latitude, place.longitude))?.let {
                            // Compensate only for spherical-distance floating-point roundoff.
                            it <= radiusMeters + 1e-7
                        } == true
                    }
                    .sortedWith(compareBy<PlaceSummary> {
                        RestaurantDiscovery.distanceMeters(location,
                            DiscoveryLocation(it.latitude, it.longitude))
                    }.thenBy { it.id })
                    .take(20)
                if (places.isEmpty()) PlacesSearchResult.Failure(PlacesFailure.NO_RESULTS)
                else PlacesSearchResult.Success(places)
            }
        )
    }

    suspend fun details(
        apiKey: String,
        identity: GoogleMapsAppIdentity?,
        placeId: String
    ): PlaceDetailsResult = requestDetails(apiKey, identity, placeId, ratingsOnly = false)

    /** Requests only profile type/rating plus fields needed to validate and credit the place. */
    suspend fun ratingDetails(
        apiKey: String,
        identity: GoogleMapsAppIdentity?,
        placeId: String
    ): PlaceDetailsResult = requestDetails(apiKey, identity, placeId, ratingsOnly = true)

    private suspend fun requestDetails(
        apiKey: String,
        identity: GoogleMapsAppIdentity?,
        placeId: String,
        ratingsOnly: Boolean
    ): PlaceDetailsResult {
        val credentials = credentials(apiKey, identity)
        credentials.failure?.let { return PlaceDetailsResult.Failure(it) }
        if (!validPlaceId(placeId)) return PlaceDetailsResult.Failure(PlacesFailure.CONFIG)
        return request(
            call = {
                if (ratingsOnly) service.ratingDetails(credentials.key, credentials.packageName,
                    credentials.certificate, placeId)
                else service.details(credentials.key, credentials.packageName,
                    credentials.certificate, placeId)
            },
            failure = { PlaceDetailsResult.Failure(it) },
            success = { body ->
                val summary = body.summary()
                if (summary == null || summary.id != placeId) {
                    PlaceDetailsResult.Failure(PlacesFailure.NO_RESULTS)
                } else {
                    PlaceDetailsResult.Success(PlaceDetails(
                        summary = if (ratingsOnly) summary.copy(address = "", googleMapsUri = null) else summary,
                        rating = body.rating?.takeIf { it.isFinite() && it in 1f..5f },
                        ratingCount = body.userRatingCount?.takeIf { it >= 0 },
                        hours = if (ratingsOnly) emptyList() else body.regularOpeningHours?.weekdayDescriptions.orEmpty()
                            .mapNotNull { it?.takeIf(String::isNotBlank) }.take(7),
                        websiteUri = if (ratingsOnly) null else safeWebUri(body.websiteUri),
                        photos = if (ratingsOnly) emptyList() else body.photos.orEmpty()
                            .mapNotNull { it?.toPhoto(placeId) }.take(10)
                    ))
                }
            }
        )
    }

    /** Stops at the JSON response: credentials are never forwarded to an image CDN redirect. */
    suspend fun photoUri(
        apiKey: String,
        identity: GoogleMapsAppIdentity?,
        photoName: String,
        maxWidthPx: Int,
        maxHeightPx: Int
    ): PhotoResult {
        val credentials = credentials(apiKey, identity)
        credentials.failure?.let { return PhotoResult.Failure(it) }
        if (!validPhotoName(photoName) || maxWidthPx !in 1..4800 || maxHeightPx !in 1..4800) {
            return PhotoResult.Failure(PlacesFailure.CONFIG)
        }
        return request(
            call = { service.photo(credentials.key, credentials.packageName, credentials.certificate,
                photoName, maxWidthPx, maxHeightPx, skipHttpRedirect = true) },
            failure = { PhotoResult.Failure(it) },
            success = { body ->
                val uri = safeWebUri(body.photoUri, httpsOnly = true)
                    ?.takeUnless { it.contains(credentials.key) || hasCredentialParameter(it) }
                if (uri == null) PhotoResult.Failure(PlacesFailure.NO_RESULTS)
                else PhotoResult.Success(uri)
            }
        )
    }

    private suspend fun <T, R> request(
        call: suspend () -> Response<T>,
        failure: (PlacesFailure) -> R,
        success: (T) -> R
    ): R = try {
        val response = call()
        if (!response.isSuccessful) {
            // Read only the protocol status, never expose Google's error text to callers or logs.
            val exhausted = response.errorBody()?.use { error ->
                if (response.code() != 403) false else try {
                    JsonParser.parseString(error.string()).asJsonObject["error"]?.asJsonObject
                        ?.get("status")?.asString == "RESOURCE_EXHAUSTED"
                } catch (_: Exception) {
                    false
                }
            } == true
            failure(when (response.code()) {
                403 -> if (exhausted) PlacesFailure.QUOTA else PlacesFailure.CONFIG
                400, 401 -> PlacesFailure.CONFIG
                404 -> PlacesFailure.NO_RESULTS
                429 -> PlacesFailure.QUOTA
                else -> PlacesFailure.NETWORK
            })
        } else {
            response.body()?.let(success) ?: failure(PlacesFailure.NO_RESULTS)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: JsonParseException) {
        failure(PlacesFailure.CONFIG)
    } catch (_: IOException) {
        failure(PlacesFailure.NETWORK)
    } catch (_: RuntimeException) {
        failure(PlacesFailure.CONFIG)
    } catch (_: Exception) {
        failure(PlacesFailure.NETWORK)
    }

    companion object {
        fun create(httpClient: OkHttpClient = OkHttpClient()): PlacesClient {
            val http = httpClient.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .cache(null)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
                .build()
            return PlacesClient(Retrofit.Builder()
                .baseUrl("https://places.googleapis.com/")
                .client(http)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(PlacesService::class.java))
        }
    }
}

private data class PlacesCredentials(
    val key: String = "", val packageName: String = "", val certificate: String = "",
    val failure: PlacesFailure? = null
)

private fun credentials(apiKey: String, identity: GoogleMapsAppIdentity?): PlacesCredentials {
    val key = apiKey.trim()
    if (key.isEmpty()) return PlacesCredentials(failure = PlacesFailure.MISSING_KEY)
    if (!key.matches(Regex("[A-Za-z0-9_-]+")) || identity == null ||
        !identity.packageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")) ||
        !identity.certificateSha1.matches(Regex("[0-9A-F]{40}"))) {
        return PlacesCredentials(failure = PlacesFailure.CONFIG)
    }
    return PlacesCredentials(key, identity.packageName, identity.certificateSha1)
}

internal fun validPlaceId(value: String): Boolean = value.matches(Regex("[A-Za-z0-9_-]{1,1024}"))
internal fun validPhotoName(value: String): Boolean =
    value.matches(Regex("places/[A-Za-z0-9_-]{1,1024}/photos/[A-Za-z0-9_-]{1,4096}"))

private fun PlaceResponse.summary(): PlaceSummary? {
    val placeId = id?.takeIf(::validPlaceId) ?: return null
    val name = displayName?.text?.takeIf(String::isNotBlank) ?: return null
    val coordinates = location ?: return null
    val latitude = coordinates.latitude ?: return null
    val longitude = coordinates.longitude ?: return null
    if (!DiscoveryLocation(latitude, longitude).isValid) return null
    if (businessStatus != "OPERATIONAL") return null
    val categories = types.orEmpty().filterNotNull()
    if (categories.none { it == "restaurant" || it == "cafe" || it == "food_court" ||
            it.endsWith("_restaurant") || it.endsWith("_cafe") }) return null
    val category = primaryTypeDisplayName?.text?.takeIf(String::isNotBlank)
        ?: primaryType?.takeIf(String::isNotBlank)?.replace('_', ' ')
            ?.replaceFirstChar { it.titlecase(Locale.ROOT) }
        ?: "Restaurant"
    return PlaceSummary(placeId, name, latitude, longitude, formattedAddress.orEmpty(), category,
        safeWebUri(googleMapsUri, httpsOnly = true), attributions.orEmpty().mapNotNull { item ->
            val provider = item?.provider?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            PlaceAttribution(provider, safeWebUri(item.providerUri))
        })
}

private fun PlacePhotoResponse.toPhoto(placeId: String): PlacePhoto? {
    val resourceName = name?.takeIf(::validPhotoName)
        ?.takeIf { it.startsWith("places/$placeId/photos/") } ?: return null
    // A malformed author cannot be silently omitted while the associated photo is displayed.
    val authors = authorAttributions ?: return null
    if (authors.any { it?.displayName.isNullOrBlank() }) return null
    return PlacePhoto(resourceName, authors.map { author ->
        PhotoAuthor(author!!.displayName!!, safeWebUri(author.uri, httpsOnly = true),
            safeWebUri(author.photoUri, httpsOnly = true))
    }, safeWebUri(googleMapsUri, httpsOnly = true))
}

internal fun safeWebUri(value: String?, httpsOnly: Boolean = false): String? {
    if (value.isNullOrBlank() || value.any { it.isISOControl() || it.isWhitespace() }) return null
    val normalized = if (value.startsWith("//")) "https:$value" else value
    return try {
        val uri = URI(normalized)
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if ((scheme != "https" && (httpsOnly || scheme != "http")) || uri.host.isNullOrBlank() ||
            uri.rawUserInfo != null) null else normalized
    } catch (_: Exception) {
        null
    }
}

private fun hasCredentialParameter(uri: String): Boolean = try {
    URI(uri).query.orEmpty().split('&').any { item ->
        item.substringBefore('=').lowercase(Locale.ROOT) in
            setOf("key", "api_key", "apikey", "x-goog-api-key")
    }
} catch (_: Exception) {
    true
}
