package com.example.dinefinder.ui

import com.example.dinefinder.*
import com.example.dinefinder.core.GeoPoint
import com.example.dinefinder.device.SensorSnapshot
import org.junit.Assert.*
import org.junit.Test

class ScanChecklistTest {
    private val point = GeoPoint(5.280285448386224, 115.17640486697412)
    private val good = AppState(cameraAllowed = true,
        sensors = SensorSnapshot(position = point, heading = 90.0, accuracyMeters = 8f, lastFixElapsedMs = 999_000),
        lastSearchCenter = point, lastSearchCount = 2)

    private fun checks(state: AppState = good, ready: Boolean = true, active: Boolean = true, cameraError: String? = null) =
        scanChecklist(state, true, true, true, true, true, ready, cameraError, active, 1_000_000)
    private fun List<ScanCheck>.status(title: String) = single { it.title == title }.status

    @Test fun observedHealthySignalsPassAllChecks() {
        assertEquals(12, checks().size)
        assertTrue(checks().all { it.status == CheckStatus.PASS })
    }
    @Test fun zeroMatchesDoesNotMeanDatabaseOrInternetFailed() {
        val checks = checks(good.copy(lastSearchCount = 0))
        assertEquals(CheckStatus.PASS, checks.status("Nearby database search"))
        assertEquals(CheckStatus.PASS, checks.status("Internet"))
        assertEquals(CheckStatus.CHECK, checks.status("Restaurants in range"))
    }
    @Test fun failedRequestIdentifiesSearchRatherThanInventingAnEmptySuccess() {
        val checks = checks(good.copy(lastSearchCount = null, error = "Supabase request failed"))
        assertEquals(CheckStatus.FAIL, checks.status("Nearby database search"))
        assertEquals(CheckStatus.WAIT, checks.status("Restaurants in range"))
        assertTrue(checks.single { it.title == "Nearby database search" }.detail.contains("Supabase request failed"))
    }
    @Test fun pendingAndStaleGpsNeverPass() {
        val pending = checks(good.copy(loading = true, lastSearchCount = null))
        assertEquals(CheckStatus.WAIT, pending.status("Nearby database search"))
        val stale = checks(good.copy(sensors = good.sensors.copy(lastFixElapsedMs = 900_000)))
        assertEquals(CheckStatus.WAIT, stale.status("Fresh GPS position"))
        assertEquals(CheckStatus.WAIT, stale.status("GPS accuracy"))
    }
    @Test fun approximatePermissionAndPoorAccuracyNeedAttention() {
        val checks = scanChecklist(good.copy(sensors = good.sensors.copy(accuracyMeters = 1500f)),
            true, false, true, true, true, false, null, true, 1_000_000)
        assertEquals(CheckStatus.CHECK, checks.status("Precise location"))
        assertEquals(CheckStatus.CHECK, checks.status("GPS accuracy"))
    }
    @Test fun permissionDoesNotProveCameraFramesExist() {
        assertEquals(CheckStatus.WAIT, checks(ready = false).status("Camera preview"))
        assertEquals(CheckStatus.WAIT, checks(ready = true, active = false).status("Camera preview"))
        assertEquals(CheckStatus.FAIL, checks(cameraError = "Camera failed").status("Camera preview"))
    }
    @Test fun missingPermissionsAndDisabledLocationAreFailures() {
        val checks = scanChecklist(AppState(), false, false, false, null, false, false, null, true, 1_000_000)
        for (title in listOf("Location permission", "Location services", "Fresh GPS position", "Camera permission", "Supabase configuration")) {
            assertEquals(title, CheckStatus.FAIL, checks.status(title))
        }
    }
}
