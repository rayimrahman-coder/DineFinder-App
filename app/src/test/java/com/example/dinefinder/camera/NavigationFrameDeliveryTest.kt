package com.example.dinefinder.camera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationFrameDeliveryTest {
    private val delivery = NavigationFrameDelivery()

    private fun frame(timestamp: Long = 10, observed: Long = 1_000,
        tracking: Boolean = true, source: NavigationCameraSource = NavigationCameraSource.ARCORE) =
        NavigationCameraFrame(CameraPreviewFrame(DoubleArray(0), timestamp, observed),
            tracking = tracking, source = source)

    @Test
    fun predictedPoseAndTrackingLossArriveBetweenCameraCaptures() {
        val generation = delivery.start()
        assertTrue(delivery.canDeliver(generation, frame(), 1_000, true, true))
        assertTrue(delivery.canDeliver(generation, frame(), 2_000, true, true))
        assertTrue(delivery.canDeliver(generation, frame(tracking = false), 3_000, true, true))
    }

    @Test
    fun duplicateImageNeverRenewsCaptureLiveness() {
        val generation = delivery.start()
        assertTrue(delivery.canDeliver(generation, frame(), 1_000, true, true))
        // Keeping a timestamp while changing its observed time is not a fresh image.
        assertFalse(delivery.canDeliver(generation, frame(observed = 2_000), 2_000, true, true))
        assertFalse(delivery.canDeliver(generation, frame(), 1_000_001_001, true, true))
        assertFalse(delivery.canDeliver(generation, frame(), 999, true, true))
        assertTrue(delivery.canDeliver(generation, frame(timestamp = 11, observed = 2_000_000_000),
            2_000_000_000, true, true))
    }

    @Test
    fun stoppedOldOrUnauthorizedPoseCannotRevivePointer() {
        val generation = delivery.start()
        assertTrue(delivery.canDeliver(generation, frame(), 1_000, true, true))
        assertFalse(delivery.canDeliver(generation, frame(), 2_000, false, true))
        assertFalse(delivery.canDeliver(generation, frame(), 2_000, true, false))
        delivery.stop()
        assertFalse(delivery.canDeliver(generation, frame(), 2_000, true, true))
        val next = delivery.start()
        assertFalse(delivery.canDeliver(generation, frame(), 2_000, true, true))
        // A new backend can have a different timestamp epoch.
        assertTrue(delivery.canDeliver(next, frame(timestamp = 1), 2_000, true, true))
    }

    @Test
    fun cameraXHasNoPoseOnlyDuplicates() {
        val generation = delivery.start()
        val image = frame(source = NavigationCameraSource.SENSORS)
        assertTrue(delivery.canDeliver(generation, image, 1_000, true, true))
        assertFalse(delivery.canDeliver(generation, image, 2_000, true, true))
    }
}
