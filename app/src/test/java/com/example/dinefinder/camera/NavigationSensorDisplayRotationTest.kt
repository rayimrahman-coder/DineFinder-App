package com.example.dinefinder.camera

import android.hardware.SensorManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NavigationSensorDisplayRotationTest {
    @Test
    fun cameraDisplayAxesMatchAndroidSensorCoordinateRemapping() {
        val rotation = floatArrayOf(1f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f)
        val mappings = listOf(
            Triple(0, SensorManager.AXIS_X, SensorManager.AXIS_Y),
            Triple(90, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X),
            Triple(180, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y),
            Triple(270, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X)
        )
        for ((displayDegrees, axisX, axisY) in mappings) {
            val androidRemapped = FloatArray(9)
            assertTrue(SensorManager.remapCoordinateSystem(rotation, axisX, axisY, androidRemapped))
            val converted = requireNotNull(NavigationPointerGeometry.sensorCameraToTrueEnu(
                rotation.map { it.toDouble() }.toDoubleArray(), displayDegrees, 0.0))
            for (index in converted.indices) {
                assertEquals("display $displayDegrees matrix[$index]", androidRemapped[index].toDouble(),
                    converted[index], 1e-7)
            }
        }
    }
}
