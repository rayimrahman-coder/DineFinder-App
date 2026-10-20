package com.example.dinefinder.camera

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** A displacement in the local true east / north / up frame, in metres. */
internal data class NavigationVector(
    val eastMeters: Double,
    val northMeters: Double,
    val upMeters: Double = 0.0
)

/** Pixel bounds of the preview and the part left unobstructed by navigation controls. */
internal data class NavigationViewport(
    val widthPx: Double,
    val heightPx: Double,
    val safeLeftPx: Double = 0.0,
    val safeTopPx: Double = 0.0,
    val safeRightPx: Double = widthPx,
    val safeBottomPx: Double = heightPx,
    val arrowRadiusPx: Double = 0.0,
    val marginPx: Double = 0.0
)

internal data class NavigationPointer(
    /** Coordinates normalized to the complete preview, including its protected controls. */
    val screenX: Double,
    val screenY: Double,
    /** Clockwise from up; an off-screen cue points out toward its clamped edge. */
    val rotationDegrees: Float,
    /** Readable mesh pitch: target elevation adjusts a 30-degree bevel view. */
    val tiltDegrees: Float,
    val isOffScreen: Boolean
)

/**
 * Navigation's camera is display-oriented: +X right, +Y up, and forward -Z.
 * Rotations are row-major 3 x 3 camera-to-world matrices; projections are the
 * column-major OpenGL matrices returned by ARCore or [CameraProjection].
 */
internal object NavigationPointerGeometry {
    fun sensorCameraToTrueEnu(
        deviceToMagneticEnu: DoubleArray,
        displayRotationDegrees: Int,
        declinationDegrees: Double
    ): DoubleArray? {
        if (!isPointerRotation(deviceToMagneticEnu) || !declinationDegrees.isFinite() ||
            displayRotationDegrees !in listOf(0, 90, 180, 270)) return null

        // Equivalent to SensorManager.remapCoordinateSystem's usual display mappings:
        // (X,Y), (Y,-X), (-X,-Y), (-Y,X). At rotation 90 the displayed camera's
        // right axis is natural-device -Y and its up axis is natural-device +X.
        val angle = Math.toRadians(displayRotationDegrees.toDouble())
        val c = cos(angle)
        val s = sin(angle)
        val displayToDevice = doubleArrayOf(c, s, 0.0, -s, c, 0.0, 0.0, 0.0, 1.0)
        val cameraToMagnetic = multiplyPointerMatrices(deviceToMagneticEnu, displayToDevice)
        // A positive declination places magnetic north east of true north.
        val declination = Math.toRadians(declinationDegrees)
        val dc = cos(declination)
        val ds = sin(declination)
        val magneticToTrue = doubleArrayOf(dc, ds, 0.0, -ds, dc, 0.0, 0.0, 0.0, 1.0)
        return multiplyPointerMatrices(magneticToTrue, cameraToMagnetic)
    }

    fun project(
        target: NavigationVector,
        cameraToTrueEnu: DoubleArray,
        projection: DoubleArray,
        viewport: NavigationViewport
    ): NavigationPointer? {
        if (!isPointerRotation(cameraToTrueEnu) || !validPointerProjection(projection)) return null
        val vector = doubleArrayOf(target.eastMeters, target.northMeters, target.upMeters)
        if (vector.any { !it.isFinite() } || vector.sumOf { it * it } <= 1e-12) return null
        val bounds = pointerBounds(viewport) ?: return null

        // Inverting a proper rotation is its transpose; retain full pitch and roll.
        val x = (0..2).sumOf { cameraToTrueEnu[it * 3] * vector[it] }
        val y = (0..2).sumOf { cameraToTrueEnu[it * 3 + 1] * vector[it] }
        val z = (0..2).sumOf { cameraToTrueEnu[it * 3 + 2] * vector[it] }
        val forward = -z
        val inFront = forward > 1e-6
        // Behind the phone, dividing by negative W would mirror left/right. Use
        // positive depth for the edge ray, then explicitly keep the cue off-screen.
        val depth = abs(forward).coerceAtLeast(1e-6)
        val ndcX = projection[0] * x / depth - projection[8]
        val ndcY = projection[5] * y / depth - projection[9]
        var screenX = (ndcX + 1) * viewport.widthPx / 2
        var screenY = (1 - ndcY) * viewport.heightPx / 2
        if (!screenX.isFinite() || !screenY.isFinite()) return null
        val offScreen = !inFront || screenX !in bounds.left..bounds.right ||
            screenY !in bounds.top..bounds.bottom
        var rotation = Math.toDegrees(atan2(x, forward)).toFloat()
        if (offScreen) {
            val originX = (bounds.left + bounds.right) / 2
            val originY = (bounds.top + bounds.bottom) / 2
            var dx = screenX - originX
            var dy = screenY - originY
            if (!inFront) {
                // Behind-camera direction must not depend on an off-centre lens
                // or on where controls move the safe rectangle's centre.
                dx = projection[0] * x * viewport.widthPx / 2
                dy = -projection[5] * y * viewport.heightPx / 2
                if (hypot(dx, dy) < 1e-6) {
                    dx = 0.0
                    dy = 1.0 // Exactly behind: consistently ask the user to turn around.
                }
            }
            val edge = edgeIntersection(dx, dy, bounds)
            screenX = edge.first
            screenY = edge.second
            rotation = Math.toDegrees(atan2(dx, -dy)).toFloat()
        }
        return NavigationPointer(
            screenX / viewport.widthPx,
            screenY / viewport.heightPx,
            normalizePointerAngle(rotation),
            (30 + Math.toDegrees(atan2(y, hypot(x, z))) * 0.4).coerceIn(15.0, 65.0).toFloat(),
            offScreen
        )
    }

    /** Keep a filtered off-screen position on the protected rectangle's boundary. */
    fun clampOffScreen(pointer: NavigationPointer, viewport: NavigationViewport): NavigationPointer? {
        if (!pointer.screenX.isFinite() || !pointer.screenY.isFinite()) return null
        val bounds = pointerBounds(viewport) ?: return null
        if (!pointer.isOffScreen) return pointer
        val edge = edgeIntersection(
            pointer.screenX * viewport.widthPx - (bounds.left + bounds.right) / 2,
            pointer.screenY * viewport.heightPx - (bounds.top + bounds.bottom) / 2,
            bounds
        )
        return pointer.copy(screenX = edge.first / viewport.widthPx,
            screenY = edge.second / viewport.heightPx)
    }

    /** Interpolate along the boundary, rather than feeding clamped XY back into a lerp. */
    fun smoothOffScreenPosition(
        previous: NavigationPointer?,
        target: NavigationPointer,
        alpha: Double,
        viewport: NavigationViewport
    ): NavigationPointer? {
        val bounds = pointerBounds(viewport) ?: return null
        if (previous?.isOffScreen != true) return clampOffScreen(target, viewport)
        val centerX = (bounds.left + bounds.right) / 2
        val centerY = (bounds.top + bounds.bottom) / 2
        fun edgeAngle(pointer: NavigationPointer): Double = atan2(
            pointer.screenX * viewport.widthPx - centerX,
            centerY - pointer.screenY * viewport.heightPx
        )
        val before = edgeAngle(previous)
        val after = edgeAngle(target)
        val fullTurn = 2 * Math.PI
        // Match the model's [-180, 180) convention at an exact half-turn.
        val delta = ((after - before + Math.PI) % fullTurn + fullTurn) % fullTurn - Math.PI
        val angle = before + delta * alpha
        val edge = edgeIntersection(sin(angle), -cos(angle), bounds)
        return target.copy(screenX = edge.first / viewport.widthPx,
            screenY = edge.second / viewport.heightPx)
    }

    private fun validPointerProjection(matrix: DoubleArray): Boolean =
        matrix.size == 16 && matrix.all { it.isFinite() } && matrix[0] > 0 && matrix[5] > 0 &&
            listOf(1, 2, 3, 4, 6, 7, 12, 13, 15).all { abs(matrix[it]) <= 1e-3 } &&
            abs(matrix[11] + 1) <= 1e-3 && matrix[10] <= -1 + 1e-3 && matrix[14] < 0

    private data class PointerBounds(val left: Double, val top: Double, val right: Double, val bottom: Double)

    private fun edgeIntersection(rayX: Double, rayY: Double, bounds: PointerBounds): Pair<Double, Double> {
        val dx = if (hypot(rayX, rayY) < 1e-6) 0.0 else rayX
        val dy = if (hypot(rayX, rayY) < 1e-6) -1.0 else rayY
        val horizontal = if (abs(dx) < 1e-12) Double.POSITIVE_INFINITY
            else (bounds.right - bounds.left) / (2 * abs(dx))
        val vertical = if (abs(dy) < 1e-12) Double.POSITIVE_INFINITY
            else (bounds.bottom - bounds.top) / (2 * abs(dy))
        val scale = minOf(horizontal, vertical)
        return Pair(
            ((bounds.left + bounds.right) / 2 + dx * scale).coerceIn(bounds.left, bounds.right),
            ((bounds.top + bounds.bottom) / 2 + dy * scale).coerceIn(bounds.top, bounds.bottom)
        )
    }

    private fun pointerBounds(viewport: NavigationViewport): PointerBounds? {
        if (listOf(viewport.widthPx, viewport.heightPx, viewport.safeLeftPx, viewport.safeTopPx,
                viewport.safeRightPx, viewport.safeBottomPx, viewport.arrowRadiusPx,
                viewport.marginPx).any { !it.isFinite() } ||
            viewport.widthPx <= 0 || viewport.heightPx <= 0 ||
            viewport.arrowRadiusPx < 0 || viewport.marginPx < 0 ||
            viewport.safeLeftPx < 0 || viewport.safeTopPx < 0 ||
            viewport.safeRightPx > viewport.widthPx || viewport.safeBottomPx > viewport.heightPx) return null
        val inset = viewport.arrowRadiusPx + viewport.marginPx
        val bounds = PointerBounds(viewport.safeLeftPx + inset, viewport.safeTopPx + inset,
            viewport.safeRightPx - inset, viewport.safeBottomPx - inset)
        return bounds.takeIf { it.left < it.right && it.top < it.bottom }
    }
}

/**
 * ARCore's world yaw is arbitrary. Calibrate a single yaw against a fresh, reliable
 * true-north sensor camera matrix, then use ARCore's continuous pose without feeding
 * magnetometer jitter into every frame. The owner must reset on tracking loss,
 * relocalization, a new session, or pause. A missing sensor cannot initialize yaw.
 */
internal class ArPoseAlignment {
    private var worldToTrueEnu: DoubleArray? = null

    val isAligned: Boolean get() = worldToTrueEnu != null

    fun reset() { worldToTrueEnu = null }

    fun align(arCameraToWorld: DoubleArray, sensorCameraToTrueEnu: DoubleArray?): DoubleArray? {
        if (!isPointerRotation(arCameraToWorld)) return null
        var alignment = worldToTrueEnu
        if (alignment == null) {
            if (sensorCameraToTrueEnu == null || !isPointerRotation(sensorCameraToTrueEnu)) return null
            // AR world has gravity along +Y; provisional ENU is (X, -Z, Y).
            val provisional = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, -1.0, 0.0, 1.0, 0.0)
            val cameraToProvisional = multiplyPointerMatrices(provisional, arCameraToWorld)
            // All three camera basis vectors contribute, including when its
            // forward axis points vertically and has no usable compass heading.
            var cosine = 0.0
            var sine = 0.0
            for (column in 0..2) {
                val east = cameraToProvisional[column]
                val north = cameraToProvisional[3 + column]
                val trueEast = sensorCameraToTrueEnu[column]
                val trueNorth = sensorCameraToTrueEnu[3 + column]
                cosine += east * trueEast + north * trueNorth
                sine += east * trueNorth - north * trueEast
            }
            if (hypot(cosine, sine) < 1e-6) return null
            val yaw = atan2(sine, cosine)
            val c = cos(yaw)
            val s = sin(yaw)
            alignment = multiplyPointerMatrices(
                doubleArrayOf(c, -s, 0.0, s, c, 0.0, 0.0, 0.0, 1.0), provisional)
            worldToTrueEnu = alignment
        }
        return multiplyPointerMatrices(alignment, arCameraToWorld)
    }

    /** Transform a bounded ARCore translation delta; this is not a GPS location. */
    fun arWorldDeltaToTrueEnu(deltaXyz: DoubleArray): NavigationVector? {
        val alignment = worldToTrueEnu ?: return null
        if (deltaXyz.size != 3 || deltaXyz.any { !it.isFinite() }) return null
        val transformed = DoubleArray(3) { row ->
            (0..2).sumOf { alignment[row * 3 + it] * deltaXyz[it] }
        }
        return NavigationVector(transformed[0], transformed[1], transformed[2])
    }
}

/** Time-based filtering, independent of camera frame rate, with shortest-arc angles. */
internal class NavigationPointerSmoother(private val timeConstantSeconds: Double = 0.10) {
    private var last: NavigationPointer? = null
    private var lastTimestampNanos = 0L

    init { require(timeConstantSeconds.isFinite() && timeConstantSeconds > 0) }

    fun reset() { last = null; lastTimestampNanos = 0L }

    fun update(
        pointer: NavigationPointer,
        timestampNanos: Long,
        viewport: NavigationViewport? = null
    ): NavigationPointer {
        val previous = last
        if (previous != null && timestampNanos <= lastTimestampNanos) return previous
        val elapsed = (timestampNanos - lastTimestampNanos) / 1_000_000_000.0
        val alpha = if (previous == null || elapsed >= 1.0) 1.0
            else 1 - exp(-elapsed / timeConstantSeconds)
        val filtered = if (previous == null) pointer else pointer.copy(
            screenX = previous.screenX + (pointer.screenX - previous.screenX) * alpha,
            screenY = previous.screenY + (pointer.screenY - previous.screenY) * alpha,
            rotationDegrees = normalizePointerAngle(previous.rotationDegrees +
                normalizePointerAngle(pointer.rotationDegrees - previous.rotationDegrees) * alpha.toFloat()),
            tiltDegrees = previous.tiltDegrees + (pointer.tiltDegrees - previous.tiltDegrees) * alpha.toFloat()
        )
        // Snap to the correct edge when the target first leaves view. Subsequent
        // off-screen frames interpolate the edge ray's angle. Repeatedly lerping
        // and clamping XY would get stuck on the old side for opposite-edge targets.
        // Callers without viewport bounds retain immediate target-edge placement.
        val edgePosition = if (pointer.isOffScreen) viewport?.let {
            NavigationPointerGeometry.smoothOffScreenPosition(previous, pointer, alpha, it)
        } else null
        val result = if (!pointer.isOffScreen) filtered else filtered.copy(
            screenX = edgePosition?.screenX ?: pointer.screenX,
            screenY = edgePosition?.screenY ?: pointer.screenY
        )
        last = result
        lastTimestampNanos = timestampNanos
        return result
    }
}

private fun normalizePointerAngle(angle: Float): Float = ((angle + 180f) % 360f + 360f) % 360f - 180f

private fun multiplyPointerMatrices(left: DoubleArray, right: DoubleArray): DoubleArray =
    DoubleArray(9) { index ->
        val row = index / 3
        val column = index % 3
        (0..2).sumOf { left[row * 3 + it] * right[it * 3 + column] }
    }

private fun isPointerRotation(matrix: DoubleArray): Boolean {
    if (matrix.size != 9 || matrix.any { !it.isFinite() }) return false
    for (column in 0..2) {
        val length = sqrt((0..2).sumOf { matrix[it * 3 + column] * matrix[it * 3 + column] })
        if (abs(length - 1) > 0.002) return false
        for (other in column + 1..2) {
            if (abs((0..2).sumOf { matrix[it * 3 + column] * matrix[it * 3 + other] }) > 0.002) return false
        }
    }
    val determinant = matrix[0] * (matrix[4] * matrix[8] - matrix[5] * matrix[7]) -
        matrix[1] * (matrix[3] * matrix[8] - matrix[5] * matrix[6]) +
        matrix[2] * (matrix[3] * matrix[7] - matrix[4] * matrix[6])
    return abs(determinant - 1) <= 0.006
}
