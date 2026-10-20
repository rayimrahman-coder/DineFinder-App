package com.example.dinefinder.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraProjectionTest {
    private val identity = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
    private val sensor = CameraSensorIntrinsics(1_000.0, 900.0, 1_000.0, 600.0)

    @Test
    fun calibratesLandscapePerspectiveAndCenter() {
        val matrix = requireNotNull(CameraProjection.fromSensorToView(sensor, identity, 2_000, 1_200))
        assertEquals(1.0, matrix[0], 1e-9)
        assertEquals(1.5, matrix[5], 1e-9)
        assertEquals(0.0, matrix[8], 1e-9)
        assertEquals(0.0, matrix[9], 1e-9)
        assertEquals(-1.0, matrix[11], 1e-9)
        assertTrue(matrix[10] < -1)
        assertTrue(matrix[14] < 0)
    }

    @Test
    fun portraitRotationSwapsFocalAxesAndMapsPrincipalPoint() {
        val rotate90 = doubleArrayOf(0.0, -1.0, 1_200.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0)
        val matrix = requireNotNull(CameraProjection.fromSensorToView(sensor, rotate90, 1_200, 2_000))
        assertEquals(1.5, matrix[0], 1e-9)
        assertEquals(1.0, matrix[5], 1e-9)
        assertEquals(0.0, matrix[8], 1e-9)
        assertEquals(0.0, matrix[9], 1e-9)
    }

    @Test
    fun fillCenterCropNarrowsVisibleFieldWithoutInventingOffsets() {
        // 2000x1200 sensor is scaled to 1800x1080, then centered in a 1080x1080 view.
        val fillCenter = doubleArrayOf(0.9, 0.0, -360.0, 0.0, 0.9, 0.0, 0.0, 0.0, 1.0)
        val matrix = requireNotNull(CameraProjection.fromSensorToView(sensor, fillCenter, 1_080, 1_080))
        assertEquals(5.0 / 3, matrix[0], 1e-9)
        assertEquals(1.5, matrix[5], 1e-9)
        assertEquals(0.0, matrix[8], 1e-9)
        assertEquals(0.0, matrix[9], 1e-9)
    }

    @Test
    fun offCenterLensProjectsOpticalAxisToMeasuredPoint() {
        val offCenter = sensor.copy(centerX = 1_100.0, centerY = 550.0)
        val matrix = requireNotNull(CameraProjection.fromSensorToView(offCenter, identity, 2_000, 1_200))
        val screenX = (1 - matrix[8]) / 2
        val screenY = (1 + matrix[9]) / 2
        assertEquals(1_100.0 / 2_000, screenX, 1e-9)
        assertEquals(550.0 / 1_200, screenY, 1e-9)
    }

    @Test
    fun zoomCropUsesActualCropCenterAndFocalScale() {
        val crop = CameraSensorCrop(2.0, 2.0, 1_050.0, 600.0, 1_000.0, 600.0)
        val matrix = requireNotNull(CameraProjection.fromSensorToView(sensor, identity, 2_000, 1_200, crop))
        assertEquals(2.0, matrix[0], 1e-9)
        assertEquals(3.0, matrix[5], 1e-9)
        assertEquals(0.1, matrix[8], 1e-9)
        assertEquals(0.0, matrix[9], 1e-9)
    }

    @Test
    fun rejectsMissingCalibrationOrViewport() {
        assertNull(CameraProjection.fromSensorToView(sensor.copy(focalX = 0.0), identity, 2_000, 1_200))
        assertNull(CameraProjection.fromSensorToView(sensor.copy(focalY = Double.NaN), identity, 2_000, 1_200))
        assertNull(CameraProjection.fromSensorToView(sensor, identity, 0, 1_200))
        assertNull(CameraProjection.fromSensorToView(sensor, identity.copyOf(8), 2_000, 1_200))
    }

    @Test
    fun rejectsMalformedTransformAndCrop() {
        assertNull(CameraProjection.fromSensorToView(sensor,
            doubleArrayOf(1.0, 0.0, 0.0, 0.5, 1.0, 0.0, 0.0, 0.0, 1.0), 2_000, 1_200))
        assertNull(CameraProjection.fromSensorToView(sensor,
            identity.copyOf().also { it[6] = 0.2 }, 2_000, 1_200))
        assertNull(CameraProjection.fromSensorToView(sensor, identity, 2_000, 1_200,
            CameraSensorCrop(-1.0, 1.0, 0.0, 0.0, 0.0, 0.0)))
    }
}
