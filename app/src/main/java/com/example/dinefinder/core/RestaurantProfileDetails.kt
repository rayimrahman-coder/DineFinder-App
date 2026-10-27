package com.example.dinefinder.core

/** Optional public profile media and real review data returned by Supabase. */
data class RestaurantProfileDetails(
    val photoUrl: String? = null,
    val reviewCount: Int? = null,
    val scoreCounts: Map<Int, Int> = emptyMap(),
    val reviews: List<RestaurantReview> = emptyList(),
)

data class RestaurantReview(
    val id: String,
    val author: String,
    val rating: Int?,
    val text: String,
)
