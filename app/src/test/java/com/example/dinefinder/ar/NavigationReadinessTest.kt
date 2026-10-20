package com.example.dinefinder.ar

import android.location.Location
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NavigationReadinessTest {
    @Test
    fun navigationRequiresRecentAccurateGpsCoordinates() {
        val fix = fix()
        assertNotNull(usableNavigationLocation(fix, 20_000_000_000L))
        assertNotNull(usableNavigationLocation(fix, 25_000_000_000L))
        assertNull(usableNavigationLocation(fix, 25_000_000_001L))
        assertNull(usableNavigationLocation(fix, 9_999_999_999L))
        fix.accuracy = 25.1f
        assertNull(usableNavigationLocation(fix, 20_000_000_000L))
        fix.accuracy = 10f
        fix.elapsedRealtimeNanos = 0
        assertNull(usableNavigationLocation(fix, 1L))
        fix.elapsedRealtimeNanos = 10_000_000_000L
        fix.latitude = Double.NaN
        assertNull(usableNavigationLocation(fix, 20_000_000_000L))
    }

    private fun fix() = Location("gps").apply {
        latitude = 5.28
        longitude = 115.17
        accuracy = 25f
        elapsedRealtimeNanos = 10_000_000_000L
    }
}
