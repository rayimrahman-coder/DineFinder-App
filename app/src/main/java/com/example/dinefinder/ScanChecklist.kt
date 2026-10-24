package com.example.dinefinder

import java.util.Locale

enum class CheckStatus { PASS, WAIT, FAIL, CHECK }
data class ScanCheck(val title: String, val status: CheckStatus, val detail: String)

/** Every pass has an observed signal; zero matches is not a failed connection. */
internal fun scanChecklist(
    state: AppState,
    locationAllowed: Boolean,
    preciseAllowed: Boolean,
    locationEnabled: Boolean?,
    networkValidated: Boolean?,
    configured: Boolean,
    cameraReady: Boolean,
    cameraError: String?,
    cameraActive: Boolean,
    nowElapsedMs: Long,
): List<ScanCheck> {
    val age = state.sensors.lastFixElapsedMs?.let { nowElapsedMs - it }
    val fresh = state.position != null && age != null && age in 0..45_000
    val searched = state.lastSearchCount != null
    val failure = state.lastSearchCenter != null && !state.loading && !searched && state.error != null
    val accuracy = state.sensors.accuracyMeters
    return listOf(
        ScanCheck("Location permission", if (locationAllowed) CheckStatus.PASS else CheckStatus.FAIL,
            if (locationAllowed) "Foreground location allowed" else "Allow location in Android app settings"),
        ScanCheck("Precise location", if (preciseAllowed) CheckStatus.PASS else CheckStatus.CHECK,
            if (preciseAllowed) "Precise permission allowed" else "Enable Precise location; approximate fixes can miss nearby restaurants"),
        ScanCheck("Location services", when (locationEnabled) { true -> CheckStatus.PASS; false -> CheckStatus.FAIL; null -> CheckStatus.WAIT },
            when (locationEnabled) { true -> "Phone location services are enabled"; false -> "Turn on phone location services"; null -> "Location service state unavailable" }),
        ScanCheck("Fresh GPS position", when { fresh -> CheckStatus.PASS; !locationAllowed || locationEnabled == false -> CheckStatus.FAIL; else -> CheckStatus.WAIT },
            if (fresh) String.format(Locale.US, "%.6f, %.6f · %d seconds old", state.position!!.latitude, state.position!!.longitude, age!! / 1000)
            else state.sensors.locationError ?: "Waiting for a GPS fix less than 45 seconds old"),
        ScanCheck("GPS accuracy", when { !fresh || accuracy == null -> CheckStatus.WAIT; accuracy > 100f -> CheckStatus.CHECK; else -> CheckStatus.PASS },
            if (accuracy != null && fresh) "±${accuracy.toInt()} m${if (accuracy > 100f) "; retry outdoors with precise location" else ""}" else "Accuracy not available for a fresh fix"),
        ScanCheck("Internet", when { searched -> CheckStatus.PASS; networkValidated == true -> CheckStatus.PASS; else -> CheckStatus.CHECK },
            if (searched) "A Supabase response was received" else if (networkValidated == true) "Android reports validated internet access" else "Internet access not confirmed; check Wi-Fi or mobile data"),
        ScanCheck("Supabase configuration", if (configured) CheckStatus.PASS else CheckStatus.FAIL,
            if (configured) "Public project configuration present; access is tested below" else "Project URL or public client key is missing/invalid"),
        ScanCheck("Nearby database search", when { searched -> CheckStatus.PASS; failure -> CheckStatus.FAIL; !configured -> CheckStatus.FAIL; else -> CheckStatus.WAIT },
            when { searched -> "Search completed: ${state.lastSearchCount} result(s) within 1 km"; failure -> state.error!!; state.loading -> "Request in progress"; !fresh -> "Waiting for a fresh GPS position"; else -> "Search has not completed" }),
        ScanCheck("Restaurants in range", when { !searched -> CheckStatus.WAIT; state.lastSearchCount!! > 0 -> CheckStatus.PASS; else -> CheckStatus.CHECK },
            when { !searched -> "Wait for the database search"; state.lastSearchCount!! > 0 -> "${state.lastSearchCount} restaurant(s) returned"; else -> "No matches: compare search coordinates, stored restaurant coordinates, radius and public read policies" }),
        ScanCheck("Compass", when { state.heading != null -> CheckStatus.PASS; state.sensors.headingError != null -> CheckStatus.CHECK; else -> CheckStatus.WAIT },
            if (state.heading != null) "Heading available" else state.sensors.headingError ?: "Waiting for compass; the restaurant list works without it"),
        ScanCheck("Camera permission", if (state.cameraAllowed) CheckStatus.PASS else CheckStatus.FAIL,
            if (state.cameraAllowed) "Camera permission allowed" else "Allow camera access; the restaurant list still works"),
        ScanCheck("Camera preview", when { cameraError != null -> CheckStatus.FAIL; !state.cameraAllowed -> CheckStatus.FAIL; !cameraActive -> CheckStatus.WAIT; cameraReady -> CheckStatus.PASS; else -> CheckStatus.WAIT },
            when { cameraError != null -> cameraError; !state.cameraAllowed -> "Camera permission needed"; !cameraActive -> "Preview inactive while camera-free or local 3D navigation is active"; cameraReady -> "CameraX is delivering preview frames"; else -> "Waiting for live camera frames" }),
    )
}
