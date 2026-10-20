package com.example.dinefinder.ar

internal class NavigationDestination private constructor(
    val name: String,
    val latitude: Double,
    val longitude: Double
) {
    companion object {
        fun create(name: String?, latitude: Double?, longitude: Double?): NavigationDestination? {
            val validName = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val validLatitude = latitude?.takeIf { it.isFinite() && it in -90.0..90.0 } ?: return null
            val validLongitude = longitude?.takeIf { it.isFinite() && it in -180.0..180.0 } ?: return null
            return NavigationDestination(validName, validLatitude, validLongitude)
        }
    }
}
