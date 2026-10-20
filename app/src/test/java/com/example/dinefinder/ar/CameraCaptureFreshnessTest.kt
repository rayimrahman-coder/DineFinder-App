package com.example.dinefinder.ar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraCaptureFreshnessTest {
    @Test
    fun requiresAPositiveCameraCaptureTimestamp() {
        val freshness = CameraCaptureFreshness()
        assertFalse(freshness.isFresh(0))
        freshness.observe(0, 1)
        freshness.observe(-1, 2)
        assertFalse(freshness.isFresh(2))
    }

    @Test
    fun repeatedRenderFramesDoNotKeepAFrozenCameraCaptureFresh() {
        val freshness = CameraCaptureFreshness()
        freshness.observe(42, 0)
        freshness.observe(42, 500_000_000)
        freshness.observe(42, 1_000_000_000)
        assertTrue(freshness.isFresh(1_000_000_000))
        freshness.observe(42, 1_000_000_001)
        assertFalse(freshness.isFresh(1_000_000_001))
    }

    @Test
    fun newCameraCaptureRestoresFreshnessWithoutAssumingCameraClockEpoch() {
        val freshness = CameraCaptureFreshness()
        freshness.observe(42, 10_000_000_000)
        assertFalse(freshness.isFresh(12_000_000_000))
        freshness.observe(43, 12_000_000_000)
        assertTrue(freshness.isFresh(12_000_000_000))
        assertFalse(freshness.isFresh(11_999_999_999))
    }

    @Test
    fun trackingLossOrPauseInvalidatesTheLastCapture() {
        val freshness = CameraCaptureFreshness()
        freshness.observe(42, 0)
        assertTrue(freshness.isFresh(1))
        freshness.reset()
        assertFalse(freshness.isFresh(1))
        freshness.observe(42, 2)
        assertTrue(freshness.isFresh(2))
    }
}
