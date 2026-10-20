package com.example.dinefinder.camera

import android.hardware.SensorManager
import android.location.Location
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NavigationOrientationTrackerTest {
    private val now = 20_000_000_000L
    private val north = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, -1.0, 0.0, 1.0, 0.0)
    private val identity = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
    private val projection = DoubleArray(16).apply {
        this[0] = 1.0; this[5] = 1.0; this[10] = -1.002; this[11] = -1.0; this[14] = -.2
    }

    @Test fun sensorFallbackRequiresFreshReliableHeadingAndLiveCalibratedCamera() {
        val tracker = tracker()
        tracker.acceptFrame(frame(now, source = NavigationCameraSource.SENSORS))
        val fix = fix()
        assertNotNull(tracker.pose(fix, 0, now))
        set(tracker, "sensorAccuracy", SensorManager.SENSOR_STATUS_UNRELIABLE)
        assertNull(tracker.pose(fix, 0, now))
        set(tracker, "sensorAccuracy", SensorManager.SENSOR_STATUS_ACCURACY_HIGH)
        assertNull(tracker.pose(fix, 0, now + 2_100_000_000L))
        tracker.acceptFrame(frame(now, source = NavigationCameraSource.SENSORS).copy(
            frame = CameraPreviewFrame(DoubleArray(0), 1L, now)))
        assertNull(tracker.pose(fix, 0, now))
    }

    @Test fun arCoreCannotInitializeNorthUsingASensorSampleFromBeforeAFastPan() {
        val tracker = tracker()
        set(tracker, "sensorTimestampNanos", now - 400_000_000L)
        tracker.acceptFrame(frame(now))
        assertNull(tracker.pose(fix(), 0, now))
        set(tracker, "sensorTimestampNanos", now - 100_000_000L)
        assertNotNull(tracker.pose(fix(), 0, now))
    }

    @Test fun arCoreTranslationUsesTheGpsTimeAnchorAndTrueNorthMapping() {
        val tracker = tracker()
        val fix = fix()
        tracker.acceptFrame(frame(now))
        assertNotNull(tracker.pose(fix, 0, now))
        tracker.acceptFrame(frame(now + 200_000_000L, doubleArrayOf(.5, 0.0, -1.0)))
        val pose = tracker.pose(fix, 0, now + 200_000_000L)!!
        assertEquals(.5, pose.motion.eastMeters, .001)
        assertEquals(1.0, pose.motion.northMeters, .001)
        assertEquals(0.0, pose.motion.upMeters, .001)
    }

    @Test fun trackingLossDropsOldNorthAndTranslationUntilHeadingCanBeCalibratedAgain() {
        val tracker = tracker()
        tracker.acceptFrame(frame(now))
        assertNotNull(tracker.pose(fix(), 0, now))
        tracker.acceptFrame(frame(now + 100_000_000L).copy(tracking = false, cameraToWorld = null, position = null))
        assertNull(tracker.pose(fix(), 0, now + 100_000_000L))
        set(tracker, "sensorAccuracy", SensorManager.SENSOR_STATUS_UNRELIABLE)
        tracker.acceptFrame(frame(now + 200_000_000L))
        assertNull(tracker.pose(fix(), 0, now + 200_000_000L))
        set(tracker, "sensorAccuracy", SensorManager.SENSOR_STATUS_ACCURACY_HIGH)
        set(tracker, "sensorTimestampNanos", now + 200_000_000L)
        assertNotNull(tracker.pose(fix(), 0, now + 200_000_000L))
    }

    @Test fun briefCompassNoiseDoesNotInterruptCalibratedArCoreButCannotLastForever() {
        val tracker = tracker()
        tracker.acceptFrame(frame(now))
        assertNotNull(tracker.pose(fix(), 0, now))
        set(tracker, "sensorAccuracy", SensorManager.SENSOR_STATUS_UNRELIABLE)
        tracker.acceptFrame(frame(now + 1_000_000_000L))
        assertNotNull(tracker.pose(fix(), 0, now + 1_000_000_000L))
        tracker.acceptFrame(frame(now + 61_000_000_000L))
        assertNull(tracker.pose(fix(), 0, now + 61_000_000_000L))
    }

    @Test fun relocalizationJumpCannotApplyThePreviousGpsAnchor() {
        val tracker = tracker()
        tracker.acceptFrame(frame(now))
        assertNotNull(tracker.pose(fix(), 0, now))
        tracker.acceptFrame(frame(now + 100_000_000L, doubleArrayOf(15.0, 0.0, 0.0)))
        val pose = tracker.pose(fix(), 0, now + 100_000_000L)!!
        assertEquals(0.0, pose.motion.eastMeters, .001)
        assertEquals(0.0, pose.motion.northMeters, .001)
    }

    @Test fun aLongCameraGapDiscardsArNorthEvenWhenTrackingStateNeverReportedPaused() {
        val tracker = tracker()
        tracker.acceptFrame(frame(now))
        assertNotNull(tracker.pose(fix(), 0, now))
        assertNull(tracker.pose(fix(), 0, now + 2_000_000_000L))
        set(tracker, "sensorAccuracy", SensorManager.SENSOR_STATUS_UNRELIABLE)
        tracker.acceptFrame(frame(now + 2_100_000_000L))
        assertNull(tracker.pose(fix(), 0, now + 2_100_000_000L))
        set(tracker, "sensorTimestampNanos", now + 2_100_000_000L)
        set(tracker, "sensorAccuracy", SensorManager.SENSOR_STATUS_ACCURACY_HIGH)
        set(tracker, "lastReliableSensorNanos", now + 2_100_000_000L)
        assertNotNull(tracker.pose(fix(), 0, now + 2_100_000_000L))
    }

    @Test fun noSynchronizedGpsAnchorUsesGpsOnlyRatherThanInventingArMovement() {
        val tracker = tracker()
        tracker.acceptFrame(frame(now, doubleArrayOf(10.0, 0.0, 5.0)))
        val fix = fix().apply { elapsedRealtimeNanos = now - 1_000_000_000L }
        val pose = tracker.pose(fix, 0, now)!!
        assertEquals(0.0, pose.motion.eastMeters, .001)
        assertEquals(0.0, pose.motion.northMeters, .001)
    }

    @Test fun stopAndMalformedFramesNeverExposeAStalePose() {
        val tracker = tracker()
        tracker.acceptFrame(frame(now))
        assertNotNull(tracker.pose(fix(), 0, now))
        tracker.acceptFrame(frame(now + 50_000_000L).copy(cameraToWorld = doubleArrayOf(1.0)))
        assertNull(tracker.pose(fix(), 0, now + 50_000_000L))
        tracker.stop()
        tracker.acceptFrame(frame(now + 100_000_000L))
        assertNull(tracker.pose(fix(), 0, now + 100_000_000L))
    }

    private fun tracker() = NavigationOrientationTracker(RuntimeEnvironment.getApplication()).also {
        set(it, "active", true)
        set(it, "deviceToMagneticEnu", north)
        set(it, "sensorTimestampNanos", now)
        set(it, "sensorAccuracy", SensorManager.SENSOR_STATUS_ACCURACY_HIGH)
        set(it, "lastReliableSensorNanos", now)
        set(it, "declinationFixNanos", now)
        set(it, "declinationDegrees", 0.0)
    }

    private fun fix() = Location("gps").apply {
        latitude = 5.28; longitude = 115.17; accuracy = 5f; elapsedRealtimeNanos = now
    }
    private fun frame(time: Long, position: DoubleArray = doubleArrayOf(0.0, 0.0, 0.0),
                      source: NavigationCameraSource = NavigationCameraSource.ARCORE) = NavigationCameraFrame(
        CameraPreviewFrame(projection, time, time), identity, position, true, source)
    private fun set(tracker: NavigationOrientationTracker, name: String, value: Any) {
        tracker.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(tracker, value)
    }
}
