package com.example.dinefinder.ar

import android.hardware.SensorManager
import android.location.Location
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CameraScanReadinessTest {
    @Test
    fun acceptsFreshAccurateFixIncludingZeroCoordinatesAndTheAgeBoundary() {
        val fix = location(50f)
        assertNotNull(usableCameraScanLocation(fix, fix.elapsedRealtimeNanos))
        assertNotNull(usableCameraScanLocation(fix, fix.elapsedRealtimeNanos + MAX_SCAN_LOCATION_AGE_NANOS))
    }

    @Test
    fun rejectsStaleAndFutureFixes() {
        val fix = location()
        assertNull(usableCameraScanLocation(fix, fix.elapsedRealtimeNanos + MAX_SCAN_LOCATION_AGE_NANOS + 1))
        assertNull(usableCameraScanLocation(fix, fix.elapsedRealtimeNanos - 1))
    }

    @Test
    fun rejectsFixesWithoutAnElapsedTimestampEvenSoonAfterBoot() {
        val fix = location().apply { elapsedRealtimeNanos = 0L }
        assertNull(usableCameraScanLocation(fix, 1_000_000_000L))
        fix.elapsedRealtimeNanos = -1L
        assertNull(usableCameraScanLocation(fix, 1_000_000_000L))
    }

    @Test
    fun rejectsMissingInaccurateAndNonfiniteAccuracy() {
        val fix = location()
        fix.removeAccuracy()
        assertNull(usableCameraScanLocation(fix, fix.elapsedRealtimeNanos))
        for (accuracy in listOf(50.01f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            fix.accuracy = accuracy
            assertNull(usableCameraScanLocation(fix, fix.elapsedRealtimeNanos))
        }
    }

    @Test
    fun rejectsMissingOrInvalidCoordinates() {
        assertNull(usableCameraScanLocation(null, 0L))
        for (latitude in listOf(90.1, Double.NaN)) {
            val fix = location().apply { this.latitude = latitude }
            assertNull(usableCameraScanLocation(fix, fix.elapsedRealtimeNanos))
        }
        val invalidLongitude = location().apply { longitude = 180.1 }
        assertNull(usableCameraScanLocation(invalidLongitude, invalidLongitude.elapsedRealtimeNanos))
    }

    @Test
    fun compassRequiresReliableAccuracyAndAtMostTwoSecondsOfAge() {
        val timestamp = 10_000_000_000L
        assertTrue(cameraScanOrientationUsable(timestamp, SensorManager.SENSOR_STATUS_ACCURACY_LOW, timestamp))
        assertTrue(cameraScanOrientationUsable(timestamp, SensorManager.SENSOR_STATUS_ACCURACY_HIGH,
            timestamp + MAX_SCAN_ORIENTATION_AGE_NANOS))
        assertFalse(cameraScanOrientationUsable(timestamp, SensorManager.SENSOR_STATUS_UNRELIABLE, timestamp))
        assertFalse(cameraScanOrientationUsable(timestamp, SensorManager.SENSOR_STATUS_ACCURACY_HIGH,
            timestamp + MAX_SCAN_ORIENTATION_AGE_NANOS + 1))
        assertFalse(cameraScanOrientationUsable(timestamp, SensorManager.SENSOR_STATUS_ACCURACY_HIGH, timestamp - 1))
        assertFalse(cameraScanOrientationUsable(0, SensorManager.SENSOR_STATUS_ACCURACY_HIGH, 0))
    }

    private fun location(accuracy: Float = 5f) = Location("test").apply {
        latitude = 0.0
        longitude = 0.0
        elapsedRealtimeNanos = 10_000_000_000L
        this.accuracy = accuracy
    }
}
