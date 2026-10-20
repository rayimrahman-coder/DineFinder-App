package com.example.dinefinder.ar

import android.location.Location

internal fun usableNavigationLocation(fix: Location?, nowNanos: Long): RouteCoordinate? {
    if (fix == null || !fix.hasAccuracy() || !fix.accuracy.isFinite() || fix.accuracy !in 0f..25f ||
        fix.elapsedRealtimeNanos <= 0 || nowNanos - fix.elapsedRealtimeNanos !in 0..15_000_000_000L) return null
    return RouteCoordinate(fix.latitude, fix.longitude).takeIf {
        it.latitude.isFinite() && it.longitude.isFinite() && it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0
    }
}
