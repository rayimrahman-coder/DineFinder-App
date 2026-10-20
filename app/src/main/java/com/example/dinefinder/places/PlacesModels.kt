package com.example.dinefinder.places

/** Google content is used only by the current scan/profile, never inserted into Room. */
data class PlaceSummary(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val category: String,
    val googleMapsUri: String? = null,
    val attributions: List<PlaceAttribution> = emptyList()
)

data class PlaceAttribution(val provider: String, val providerUri: String?)

data class PlaceDetails(
    val summary: PlaceSummary,
    val rating: Float?,
    val ratingCount: Int?,
    val hours: List<String>,
    val websiteUri: String?,
    val photos: List<PlacePhoto>
)

/** Photo names expire and are obtained from a fresh Place Details response. */
data class PlacePhoto(
    val name: String,
    val authors: List<PhotoAuthor>,
    val googleMapsUri: String? = null
)

data class PhotoAuthor(
    val displayName: String,
    val uri: String?,
    val photoUri: String? = null
)

enum class PlacesFailure(val needsSettings: Boolean = false) {
    MISSING_KEY(true), CONFIG(true), QUOTA, NETWORK, NO_RESULTS
}

sealed interface PlacesSearchResult {
    data class Success(val places: List<PlaceSummary>) : PlacesSearchResult
    data class Failure(val reason: PlacesFailure) : PlacesSearchResult
}

sealed interface PlaceDetailsResult {
    data class Success(val details: PlaceDetails) : PlaceDetailsResult
    data class Failure(val reason: PlacesFailure) : PlaceDetailsResult
}

sealed interface PhotoResult {
    /** Public image URL. Fetch it with a separate client that sends no API or identity headers. */
    data class Success(val uri: String) : PhotoResult
    data class Failure(val reason: PlacesFailure) : PhotoResult
}
