package com.example.dinefinder.camera

import kotlin.math.abs
import kotlin.math.sqrt

/** Intrinsics in Camera2's active sensor pixel coordinates. */
internal data class CameraSensorIntrinsics(
    val focalX: Double,
    val focalY: Double,
    val centerX: Double,
    val centerY: Double
)

/** A current crop/zoom, before CameraX's stream aspect crop and FILL_CENTER transform. */
internal data class CameraSensorCrop(
    val zoomX: Double,
    val zoomY: Double,
    val cropCenterX: Double,
    val cropCenterY: Double,
    val activeCenterX: Double,
    val activeCenterY: Double
)

internal object CameraProjection {
    /**
     * Uses PreviewView's actual sensor-to-view transform, including rotation, stream crop,
     * and FILL_CENTER scaling. Sensor pixels point down; the projected display Y points up.
     * The returned matrix is column-major OpenGL perspective, with back-camera forward -Z.
     */
    fun fromSensorToView(
        intrinsics: CameraSensorIntrinsics,
        sensorToView: DoubleArray,
        viewportWidth: Int,
        viewportHeight: Int,
        crop: CameraSensorCrop? = null
    ): DoubleArray? {
        if (viewportWidth <= 0 || viewportHeight <= 0 || sensorToView.size != 9 ||
            sensorToView.any { !it.isFinite() } ||
            listOf(intrinsics.focalX, intrinsics.focalY, intrinsics.centerX, intrinsics.centerY)
                .any { !it.isFinite() } || intrinsics.focalX <= 0 || intrinsics.focalY <= 0 ||
            abs(sensorToView[6]) > 1e-6 || abs(sensorToView[7]) > 1e-6 ||
            abs(sensorToView[8] - 1) > 1e-6) return null

        val zoomX = crop?.zoomX ?: 1.0
        val zoomY = crop?.zoomY ?: 1.0
        if (!zoomX.isFinite() || !zoomY.isFinite() || zoomX <= 0 || zoomY <= 0 ||
            crop?.let { listOf(it.cropCenterX, it.cropCenterY, it.activeCenterX, it.activeCenterY)
                .any { value -> !value.isFinite() } } == true) return null

        val sensorX = crop?.let {
            it.activeCenterX + (intrinsics.centerX - it.cropCenterX) * zoomX
        } ?: intrinsics.centerX
        val sensorY = crop?.let {
            it.activeCenterY + (intrinsics.centerY - it.cropCenterY) * zoomY
        } ?: intrinsics.centerY
        val centerX = sensorToView[0] * sensorX + sensorToView[1] * sensorY + sensorToView[2]
        val centerY = sensorToView[3] * sensorX + sensorToView[4] * sensorY + sensorToView[5]
        val x1 = sensorToView[0] * intrinsics.focalX * zoomX
        val x2 = sensorToView[1] * intrinsics.focalY * zoomY
        val y1 = sensorToView[3] * intrinsics.focalX * zoomX
        val y2 = sensorToView[4] * intrinsics.focalY * zoomY
        val focalX = sqrt(x1 * x1 + x2 * x2)
        val focalY = sqrt(y1 * y1 + y2 * y2)
        if (!focalX.isFinite() || !focalY.isFinite() || focalX <= 0 || focalY <= 0 ||
            !centerX.isFinite() || !centerY.isFinite() ||
            abs(x1 * y1 + x2 * y2) > focalX * focalY * 1e-5) return null

        val near = 0.1
        val far = 1_000.0
        return DoubleArray(16).also {
            it[0] = 2 * focalX / viewportWidth
            it[5] = 2 * focalY / viewportHeight
            it[8] = 1 - 2 * centerX / viewportWidth
            it[9] = 2 * centerY / viewportHeight - 1
            it[10] = -(far + near) / (far - near)
            it[11] = -1.0
            it[14] = -2 * far * near / (far - near)
        }
    }

    /** Fallback perspective projection matrix when camera lens calibration is unavailable. */
    fun defaultProjection(viewportWidth: Int, viewportHeight: Int): DoubleArray {
        val width = maxOf(1, viewportWidth).toDouble()
        val height = maxOf(1, viewportHeight).toDouble()
        val aspect = width / height
        val scaleY = 1.7320508 // ~60 degree FOV
        val scaleX = scaleY / aspect
        val near = 0.1
        val far = 1_000.0
        return DoubleArray(16).also {
            it[0] = scaleX
            it[5] = scaleY
            it[8] = 0.0
            it[9] = 0.0
            it[10] = -(far + near) / (far - near)
            it[11] = -1.0
            it[14] = -2 * far * near / (far - near)
        }
    }
}
