package com.example.dinefinder.ui

import android.content.Intent
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Only Google's Maps website is accepted as an automatic app-intent fallback. */
internal fun isGoogleMapsWebUrl(link: String): Boolean {
    val url = link.toHttpUrlOrNull() ?: return false
    if (!isInAppReviewUrl(link)) return false
    return when (url.host) {
        "www.google.com", "google.com", "www.google.com.my", "google.com.my" ->
            url.encodedPath == "/maps" || url.encodedPath.startsWith("/maps/")
        "maps.google.com", "maps.google.com.my", "maps.app.goo.gl" -> true
        else -> false
    }
}

/** Parse data only: the Intent is never launched and no component/extras are executed. */
internal fun googleMapsBrowserFallback(link: String): String? {
    if (!link.startsWith("intent:", ignoreCase = true) || link.length > 16_384) return null
    return runCatching {
        val intent = Intent.parseUri(link, Intent.URI_INTENT_SCHEME)
        val explicit = intent.getStringExtra("browser_fallback_url")
        val candidate = explicit ?: intent.dataString
        candidate?.takeIf(::isGoogleMapsWebUrl)
    }.getOrNull()
}

internal sealed interface ReviewWebDecision {
    data object StayInWebView : ReviewWebDecision
    data class LoadFallback(val url: String) : ReviewWebDecision
    data object BlockSubframe : ReviewWebDecision
    data object ShowUnavailable : ReviewWebDecision
}

/** The same callback is used by modern and legacy WebView navigation paths. */
internal class ReviewWebNavigation {
    private val triedFallbacks = mutableSetOf<String>()
    fun decide(link: String, mainFrame: Boolean): ReviewWebDecision {
        if (isInAppReviewUrl(link)) return ReviewWebDecision.StayInWebView
        // Empty child frames are normal on Maps; they aren't external app navigation.
        if (link == "about:blank" && !mainFrame) return ReviewWebDecision.StayInWebView
        if (!mainFrame) return ReviewWebDecision.BlockSubframe
        val fallback = googleMapsBrowserFallback(link)
        if (fallback != null && triedFallbacks.size < 3 && triedFallbacks.add(fallback))
            return ReviewWebDecision.LoadFallback(fallback)
        return ReviewWebDecision.ShowUnavailable
    }
}
