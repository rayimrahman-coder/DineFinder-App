package com.example.dinefinder.places

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

internal const val NEARBY_FIELD_MASK = "places.id,places.displayName,places.location," +
    "places.formattedAddress,places.primaryType,places.primaryTypeDisplayName,places.types," +
    "places.businessStatus,places.googleMapsUri,places.attributions"
internal const val DETAILS_FIELD_MASK = "id,displayName,location,formattedAddress,primaryType," +
    "primaryTypeDisplayName,types,businessStatus,googleMapsUri,attributions,rating,userRatingCount," +
    "regularOpeningHours.weekdayDescriptions,websiteUri,photos.name,photos.authorAttributions," +
    "photos.googleMapsUri"
internal const val RATING_DETAILS_FIELD_MASK = "id,displayName,location,primaryType," +
    "primaryTypeDisplayName,types,businessStatus,attributions,rating,userRatingCount"

/** API credentials are sent only as headers to the Places API host. */
internal interface PlacesService {
    @Headers("X-Goog-FieldMask: $NEARBY_FIELD_MASK")
    @POST("v1/places:searchNearby")
    suspend fun nearby(
        @Header("X-Goog-Api-Key") apiKey: String,
        @Header("X-Android-Package") packageName: String,
        @Header("X-Android-Cert") certificateSha1: String,
        @Body request: NearbyRequest
    ): Response<NearbyResponse>

    @Headers("X-Goog-FieldMask: $DETAILS_FIELD_MASK")
    @GET("v1/places/{placeId}")
    suspend fun details(
        @Header("X-Goog-Api-Key") apiKey: String,
        @Header("X-Android-Package") packageName: String,
        @Header("X-Android-Cert") certificateSha1: String,
        @Path("placeId") placeId: String
    ): Response<PlaceResponse>

    @Headers("X-Goog-FieldMask: $RATING_DETAILS_FIELD_MASK")
    @GET("v1/places/{placeId}")
    suspend fun ratingDetails(
        @Header("X-Goog-Api-Key") apiKey: String,
        @Header("X-Android-Package") packageName: String,
        @Header("X-Android-Cert") certificateSha1: String,
        @Path("placeId") placeId: String
    ): Response<PlaceResponse>

    @GET("v1/{photoName}/media")
    suspend fun photo(
        @Header("X-Goog-Api-Key") apiKey: String,
        @Header("X-Android-Package") packageName: String,
        @Header("X-Android-Cert") certificateSha1: String,
        @Path(value = "photoName", encoded = true) photoName: String,
        @Query("maxWidthPx") maxWidthPx: Int,
        @Query("maxHeightPx") maxHeightPx: Int,
        @Query("skipHttpRedirect") skipHttpRedirect: Boolean = true
    ): Response<PhotoResponse>
}

internal data class NearbyRequest(
    val locationRestriction: NearbyLocationRestriction,
    val includedTypes: List<String> = listOf("restaurant", "cafe", "food_court"),
    val maxResultCount: Int = 20,
    val rankPreference: String = "DISTANCE"
)
internal data class NearbyLocationRestriction(val circle: NearbyCircle)
internal data class NearbyCircle(val center: PlaceCoordinate, val radius: Double)
internal data class PlaceCoordinate(val latitude: Double?, val longitude: Double?)
internal data class NearbyResponse(val places: List<PlaceResponse?>? = null)
internal data class LocalizedPlaceText(val text: String? = null)
internal data class PlaceResponse(
    val id: String? = null,
    val displayName: LocalizedPlaceText? = null,
    val location: PlaceCoordinate? = null,
    val formattedAddress: String? = null,
    val primaryType: String? = null,
    val primaryTypeDisplayName: LocalizedPlaceText? = null,
    val types: List<String?>? = null,
    val businessStatus: String? = null,
    val googleMapsUri: String? = null,
    val attributions: List<PlaceAttributionResponse?>? = null,
    val rating: Float? = null,
    val userRatingCount: Int? = null,
    val regularOpeningHours: PlaceOpeningHours? = null,
    val websiteUri: String? = null,
    val photos: List<PlacePhotoResponse?>? = null
)
internal data class PlaceAttributionResponse(val provider: String?, val providerUri: String?)
internal data class PlaceOpeningHours(val weekdayDescriptions: List<String?>? = null)
internal data class PlacePhotoResponse(
    val name: String? = null,
    val authorAttributions: List<PhotoAuthorResponse?>? = null,
    val googleMapsUri: String? = null
)
internal data class PhotoAuthorResponse(
    val displayName: String? = null,
    val uri: String? = null,
    val photoUri: String? = null
)
internal data class PhotoResponse(val photoUri: String? = null)
