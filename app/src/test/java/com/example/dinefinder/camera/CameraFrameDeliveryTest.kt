package com.example.dinefinder.camera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraFrameDeliveryTest {
    private val delivery = CameraFrameDelivery()

    private fun frame(generation: Long, timestamp: Long = 10, observed: Long = 1_000,
        now: Long = 1_000, permission: Boolean = true, resumed: Boolean = true) =
        delivery.canDeliver(generation, timestamp, observed, now, permission, resumed)

    @Test
    fun requiresActiveGenerationAndLiveCamera() {
        assertFalse(frame(0))
        val generation = delivery.start()
        assertFalse(frame(generation, permission = false))
        assertFalse(frame(generation, resumed = false))
        assertTrue(frame(generation))
    }

    @Test
    fun stopAndRestartDiscardPreviousCallbacks() {
        val first = delivery.start()
        delivery.stop()
        assertFalse(frame(first))
        val second = delivery.start()
        assertFalse(frame(first))
        assertTrue(frame(second))
    }

    @Test
    fun frozenOrRegressingCaptureDoesNotRefreshAnOverlay() {
        val generation = delivery.start()
        assertTrue(frame(generation))
        assertFalse(frame(generation, timestamp = 10, now = 2_000, observed = 2_000))
        assertFalse(frame(generation, timestamp = 9))
        assertTrue(frame(generation, timestamp = 11))
    }

    @Test
    fun rejectsDelayedAndInvalidCaptureTimestamps() {
        val generation = delivery.start()
        assertFalse(frame(generation, timestamp = 0))
        assertFalse(frame(generation, observed = 1_000, now = 999))
        assertFalse(frame(generation, observed = 1_000, now = 1_000_001_001))
        assertTrue(frame(generation, observed = 1_000, now = 1_000_001_000))
    }
}
