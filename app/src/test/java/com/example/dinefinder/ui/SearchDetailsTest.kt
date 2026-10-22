package com.example.dinefinder.ui

import com.example.dinefinder.AppState
import com.example.dinefinder.BuildConfig
import com.example.dinefinder.core.GeoPoint
import com.example.dinefinder.device.SensorSnapshot
import com.example.dinefinder.searchDetailsText
import org.junit.Assert.*
import org.junit.Test

class SearchDetailsTest {
    private val point = GeoPoint(5.280285448386224, 115.17640486697412)

    @Test fun zeroResultsAreDistinguishedFromAnUnfinishedSearch() {
        val initial = searchDetailsText(AppState(), precise = true)
        assertTrue(initial.contains("Search: Not completed"))
        val completed = searchDetailsText(AppState(lastSearchCenter = point, lastSearchCount = 0), true)
        assertTrue(completed.contains("Search: Completed: 0 results"))
        assertTrue(completed.contains("Search centre: 5.280285, 115.176405"))
    }

    @Test fun failedSearchDoesNotClaimZeroSuccessfulResults() {
        val details = searchDetailsText(AppState(error = "Unable to connect."), true)
        assertTrue(details.contains("Search: Failed: Unable to connect."))
        assertFalse(details.contains("Completed: 0 results"))
    }

    @Test fun gpsAgeIsReportedWithoutInferringThatEveryWaitingFixIsStale() {
        val state = AppState(sensors = SensorSnapshot(position = point, lastFixElapsedMs = 990_000))
        assertTrue(searchDetailsText(state, true, 1_000_000).contains("GPS fix age: 10 s"))
        assertFalse(searchDetailsText(state, true, 1_000_000).contains("(stale)"))
        assertTrue(searchDetailsText(state, true, 1_040_000).contains("GPS fix age: 50 s (stale)"))
    }

    @Test fun locationAccuracyAndPermissionAreVisibleWithoutPublishingCredentials() {
        val details = searchDetailsText(AppState(sensors = SensorSnapshot(position = point, accuracyMeters = 1500f)), false)
        assertTrue(details.contains("Phone GPS: 5.280285, 115.176405"))
        assertTrue(details.contains("Accuracy: ±1500 m"))
        assertTrue(details.contains("enable Precise location"))
        assertTrue(details.contains("Location accuracy is low"))
        assertFalse(details.contains(BuildConfig.SUPABASE_PUBLISHABLE_KEY))
    }
}
