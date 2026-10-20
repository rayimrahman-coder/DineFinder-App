package com.example.dinefinder.profile

import com.example.dinefinder.Restaurant
import com.example.dinefinder.places.PlaceAttribution
import com.example.dinefinder.places.PlaceDetails
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

data class ProfileContent(
    val name: String,
    val cuisine: String,
    val halal: Boolean?,
    val rating: Float?,
    val ratingCount: Int?,
    val attributions: List<PlaceAttribution>
)

fun mergeProfileContent(restaurant: Restaurant, details: PlaceDetails?): ProfileContent {
    val localRating = restaurant.rating.takeIf { it.isFinite() && it > 0f && it <= 5f }
    val googleRating = details?.rating?.takeIf { it.isFinite() && it > 0f && it <= 5f }
    return ProfileContent(
        name = restaurant.name.ifBlank { details?.summary?.name.orEmpty() },
        cuisine = restaurant.cuisine.ifBlank { details?.summary?.category.orEmpty() },
        halal = restaurant.halal.takeIf { restaurant.id > 0 },
        rating = localRating ?: googleRating,
        ratingCount = if (localRating == null && googleRating != null)
            details.ratingCount?.takeIf { it >= 0 } else null,
        attributions = details?.summary?.attributions.orEmpty()
            .filter { it.provider.isNotBlank() }
            .map { it.copy(providerUri = profileWebUri(it.providerUri)) }
    )
}

/** Provider credit links may open only ordinary HTTP(S) browser destinations. */
fun profileWebUri(value: String?, httpsOnly: Boolean = false): String? {
    if (value.isNullOrEmpty() || value.any { it.isWhitespace() || it.isISOControl() }) return null
    val uri = try {
        URI(value)
    } catch (_: URISyntaxException) {
        return null
    }
    val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
    if (scheme != "https" && (httpsOnly || scheme != "http")) return null
    if (uri.host.isNullOrBlank() || uri.rawUserInfo != null ||
        (uri.port != -1 && uri.port !in 1..65535)) return null
    // Encoded control characters are unsafe too, even though URI parsing accepts them.
    if (listOfNotNull(uri.path, uri.query, uri.fragment).any { component ->
            component.any { it.isISOControl() }
        }) return null
    return value
}
