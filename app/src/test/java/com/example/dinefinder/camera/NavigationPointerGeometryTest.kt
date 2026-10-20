package com.example.dinefinder.camera

import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationPointerGeometryTest {
    private val north = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, -1.0, 0.0, 1.0, 0.0)
    private val identity = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
    private val projection = doubleArrayOf(
        1.0, 0.0, 0.0, 0.0,
        0.0, 1.0, 0.0, 0.0,
        0.0, 0.0, -1.002, -1.0,
        0.0, 0.0, -0.2002, 0.0
    )
    private val viewport = NavigationViewport(1_000.0, 2_000.0)

    @Test
    fun northAheadIsCenteredAndPanningChangesTheSameWorldTarget() {
        val ahead = project(NavigationVector(0.0, 20.0))
        assertEquals(0.5, ahead.screenX, 1e-9)
        assertEquals(0.5, ahead.screenY, 1e-9)
        assertEquals(0f, ahead.rotationDegrees, 1e-5f)
        assertEquals(30f, ahead.tiltDegrees, 1e-5f)
        assertFalse(ahead.isOffScreen)

        val northEast = NavigationVector(10.0, 20.0)
        val beforePan = project(northEast)
        val facingEast = doubleArrayOf(0.0, 0.0, -1.0, -1.0, 0.0, 0.0, 0.0, 1.0, 0.0)
        val afterPan = project(northEast, facingEast)
        assertTrue(beforePan.screenX > 0.5)
        assertTrue(beforePan.rotationDegrees > 0)
        assertTrue(afterPan.screenX < 0.5)
        assertTrue(afterPan.rotationDegrees < 0)
        assertEquals(0.5, project(NavigationVector(20.0, 0.0), facingEast).screenX, 1e-9)
    }

    @Test
    fun tiltingThePhoneMovesTargetVerticallyAndChangesReadableMeshPitch() {
        val pitch = Math.toRadians(30.0)
        val lookingUp = doubleArrayOf(
            1.0, 0.0, 0.0,
            0.0, -sin(pitch), -cos(pitch),
            0.0, cos(pitch), -sin(pitch)
        )
        val target = project(NavigationVector(0.0, 20.0), lookingUp)
        assertTrue(target.screenY > 0.5)
        assertEquals(18f, target.tiltDegrees, 1e-4f)
        assertEquals(0f, target.rotationDegrees, 1e-5f)
        assertEquals(65f, project(NavigationVector(0.0, 0.1, 100.0)).tiltDegrees, 1e-5f)
        assertEquals(15f, project(NavigationVector(0.0, 0.1, -100.0)).tiltDegrees, 1e-5f)
    }

    @Test
    fun rollUsesAllCameraAxesInsteadOfOnlyACompassYaw() {
        val rolled = multiply(north, doubleArrayOf(0.0, -1.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0))
        val target = project(NavigationVector(10.0, 20.0), rolled)
        assertEquals(0.5, target.screenX, 1e-9)
        assertTrue(target.screenY > 0.5)
        assertTrue(target.tiltDegrees < 30f)
    }

    @Test
    fun displayNinetyAndTwoSeventyRotateTheCameraBasisInOppositeDirections() {
        val landscape90 = requireNotNull(NavigationPointerGeometry.sensorCameraToTrueEnu(north, 90, 0.0))
        val landscape270 = requireNotNull(NavigationPointerGeometry.sensorCameraToTrueEnu(north, 270, 0.0))
        val vector = NavigationVector(10.0, 20.0)
        val at90 = project(vector, landscape90)
        val at270 = project(vector, landscape270)
        assertEquals(0.5, at90.screenX, 1e-9)
        assertEquals(0.5, at270.screenX, 1e-9)
        assertTrue(at90.screenY < 0.5)
        assertTrue(at270.screenY > 0.5)
        val upsideDown = requireNotNull(NavigationPointerGeometry.sensorCameraToTrueEnu(north, 180, 0.0))
        assertTrue(project(vector, upsideDown).screenX < 0.5)
    }

    @Test
    fun positiveDeclinationPointsMagneticNorthEastOfTrueNorth() {
        val truePose = requireNotNull(NavigationPointerGeometry.sensorCameraToTrueEnu(north, 0, 10.0))
        val trueNorth = project(NavigationVector(0.0, 20.0), truePose)
        assertEquals(-10f, trueNorth.rotationDegrees, 1e-4f)
        assertTrue(trueNorth.screenX < 0.5)
        val trueBearingOfMagneticNorth = NavigationVector(20 * sin(Math.toRadians(10.0)),
            20 * cos(Math.toRadians(10.0)))
        assertEquals(0.5, project(trueBearingOfMagneticNorth, truePose).screenX, 1e-9)
    }

    @Test
    fun screenEdgesIncludeTheEntireArrowAndAvoidProtectedControls() {
        val safe = NavigationViewport(1_000.0, 2_000.0,
            safeLeftPx = 50.0, safeTopPx = 200.0, safeRightPx = 950.0,
            safeBottomPx = 1_800.0, arrowRadiusPx = 50.0, marginPx = 10.0)
        val right = project(NavigationVector(100.0, 10.0), view = safe)
        assertEquals(0.89, right.screenX, 1e-9)
        assertEquals(0.5, right.screenY, 1e-9)
        assertEquals(90f, right.rotationDegrees, 1e-5f)
        assertTrue(right.isOffScreen)
        val left = project(NavigationVector(-100.0, 10.0), view = safe)
        assertEquals(0.11, left.screenX, 1e-9)
        assertEquals(-90f, left.rotationDegrees, 1e-5f)
        val top = project(NavigationVector(0.0, 10.0, 100.0), view = safe)
        assertEquals(0.13, top.screenY, 1e-9)
        assertEquals(0f, top.rotationDegrees, 1e-5f)
        val bottom = project(NavigationVector(0.0, 10.0, -100.0), view = safe)
        assertEquals(0.87, bottom.screenY, 1e-9)
        assertEquals(-180f, bottom.rotationDegrees, 1e-5f)
    }

    @Test
    fun diagonalOffScreenDirectionIntersectsRectangleAtItsCorner() {
        val safe = NavigationViewport(1_000.0, 2_000.0, 50.0, 200.0, 950.0, 1_800.0, 50.0, 10.0)
        // The screen ray's horizontal / vertical components are 390 / 740.
        val corner = project(NavigationVector(780.0, 1.0, 740.0), view = safe)
        assertEquals(0.89, corner.screenX, 1e-9)
        assertEquals(0.13, corner.screenY, 1e-9)
        assertTrue(corner.isOffScreen)
        assertTrue(corner.rotationDegrees in 0f..90f)
    }

    @Test
    fun behindCameraKeepsLeftAndRightInsteadOfMirroringThePointer() {
        val right = project(NavigationVector(10.0, -20.0))
        val left = project(NavigationVector(-10.0, -20.0))
        assertEquals(1.0, right.screenX, 1e-9)
        assertEquals(0.0, left.screenX, 1e-9)
        assertEquals(90f, right.rotationDegrees, 1e-5f)
        assertEquals(-90f, left.rotationDegrees, 1e-5f)
        assertTrue(right.isOffScreen && left.isOffScreen)
        val directlyBehind = project(NavigationVector(0.0, -20.0))
        assertEquals(0.5, directlyBehind.screenX, 1e-9)
        assertEquals(1.0, directlyBehind.screenY, 1e-9)
        assertEquals(-180f, directlyBehind.rotationDegrees, 1e-5f)
    }

    @Test
    fun offCenterLensIsRespectedWithoutChangingTargetYaw() {
        val lens = projection.copyOf().also { it[8] = -0.2; it[9] = 0.1 }
        val target = requireNotNull(NavigationPointerGeometry.project(NavigationVector(0.0, 20.0),
            north, lens, viewport))
        assertEquals(0.6, target.screenX, 1e-9)
        assertEquals(0.55, target.screenY, 1e-9)
        assertEquals(0f, target.rotationDegrees, 1e-5f)
    }

    @Test
    fun invalidMatricesCoordinatesAndEmptySafeAreasDoNotInventAPointer() {
        val target = NavigationVector(0.0, 10.0)
        assertNull(NavigationPointerGeometry.project(target, identity.copyOf(8), projection, viewport))
        assertNull(NavigationPointerGeometry.project(target, identity.copyOf().also { it[0] = 2.0 }, projection, viewport))
        assertNull(NavigationPointerGeometry.project(target, north, DoubleArray(16), viewport))
        assertNull(NavigationPointerGeometry.project(target, north, projection, viewport.copy(widthPx = 0.0)))
        assertNull(NavigationPointerGeometry.project(target, north, projection, viewport.copy(arrowRadiusPx = 600.0)))
        assertNull(NavigationPointerGeometry.project(target, north, projection, viewport.copy(safeLeftPx = -1.0)))
        assertNull(NavigationPointerGeometry.project(NavigationVector(0.0, 0.0), north, projection, viewport))
        assertNull(NavigationPointerGeometry.project(NavigationVector(Double.NaN, 1.0), north, projection, viewport))
        assertNull(NavigationPointerGeometry.sensorCameraToTrueEnu(north, 45, 0.0))
        assertNull(NavigationPointerGeometry.sensorCameraToTrueEnu(north, 0, Double.NaN))
    }

    @Test
    fun arbitraryArCoreYawMustBeAlignedToReliableNorthBeforeItCanGuide() {
        val alignment = ArPoseAlignment()
        val arbitraryPose = rotateY(90.0)
        assertNull(alignment.align(arbitraryPose, null))
        assertFalse(alignment.isAligned)
        assertNull(alignment.arWorldDeltaToTrueEnu(doubleArrayOf(1.0, 2.0, 3.0)))
        val calibrated = requireNotNull(alignment.align(arbitraryPose, north))
        assertMatrixEquals(north, calibrated)
        assertTrue(alignment.isAligned)

        // A subsequent camera pan is read from ARCore even while magnetic samples
        // are absent. ARCore +Y rotation turns the camera left from its initial yaw.
        val panned = requireNotNull(alignment.align(rotateY(120.0), null))
        val arrow = project(NavigationVector(0.0, 20.0), panned)
        assertEquals(30f, arrow.rotationDegrees, 1e-4f)
        assertTrue(arrow.screenX > 0.5)
    }

    @Test
    fun magneticJitterCannotRecalibrateEveryTrackedFrameAndResetRequiresNewReference() {
        val alignment = ArPoseAlignment()
        assertMatrixEquals(north, requireNotNull(alignment.align(identity, north)))
        val wrongMagneticSample = requireNotNull(NavigationPointerGeometry.sensorCameraToTrueEnu(north, 0, 80.0))
        assertMatrixEquals(north, requireNotNull(alignment.align(identity, wrongMagneticSample)))
        alignment.reset()
        assertFalse(alignment.isAligned)
        assertNull(alignment.align(identity, null))
        assertMatrixEquals(wrongMagneticSample,
            requireNotNull(alignment.align(identity, wrongMagneticSample)))
    }

    @Test
    fun arTranslationUsesTheSameNorthAlignmentAsOrientation() {
        val alignment = ArPoseAlignment()
        alignment.align(identity, north)
        val delta = requireNotNull(alignment.arWorldDeltaToTrueEnu(doubleArrayOf(1.0, 2.0, 3.0)))
        assertEquals(1.0, delta.eastMeters, 1e-9)
        assertEquals(-3.0, delta.northMeters, 1e-9)
        assertEquals(2.0, delta.upMeters, 1e-9)
        assertNull(alignment.arWorldDeltaToTrueEnu(doubleArrayOf(Double.NaN, 0.0, 0.0)))
        assertNull(alignment.arWorldDeltaToTrueEnu(doubleArrayOf(0.0, 0.0)))
        alignment.reset()
        assertNull(alignment.arWorldDeltaToTrueEnu(doubleArrayOf(1.0, 2.0, 3.0)))
    }

    @Test
    fun calibrationAlsoWorksWhenTheCameraPointsVertically() {
        val alignment = ArPoseAlignment()
        // Both frames see the phone lying flat: sensor local Z points true up,
        // while ARCore local Z points its world +Y. Forward has no compass yaw.
        val flatAr = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, -1.0, 0.0)
        assertMatrixEquals(identity, requireNotNull(alignment.align(flatAr, identity)))
    }

    @Test
    fun smoothingTakesTheShortArcAcrossNorthAndRetainsTargetEdgeState() {
        val smoother = NavigationPointerSmoother()
        smoother.update(pointer(angle = 179f), 1_000_000_000)
        val next = smoother.update(pointer(x = 0.9, angle = -179f, offScreen = true), 1_050_000_000)
        assertTrue(next.rotationDegrees > 179f || next.rotationDegrees < -179f)
        assertTrue(next.screenX in 0.5..0.9)
        assertTrue(next.isOffScreen)
        assertEquals(next, smoother.update(pointer(angle = 0f), 1_040_000_000))
    }

    @Test
    fun anOffScreenCueReachesTheSafeBoundaryOnItsFirstFilteredFrame() {
        val safe = NavigationViewport(1_000.0, 2_000.0, 50.0, 200.0, 950.0, 1_800.0, 50.0, 10.0)
        val smoother = NavigationPointerSmoother()
        smoother.update(project(NavigationVector(0.0, 20.0), view = safe), 1_000_000_000, safe)
        val target = project(NavigationVector(100.0, 10.0), view = safe)
        val firstFrame = smoother.update(target, 1_016_000_000, safe)
        assertEquals(0.89, firstFrame.screenX, 1e-9)
        assertEquals(0.5, firstFrame.screenY, 1e-9)
        assertTrue(firstFrame.isOffScreen)
        assertTrue(firstFrame.rotationDegrees > 0 && firstFrame.rotationDegrees < 90)

        // Bounds-free compatibility also places the model at the target edge
        // immediately, while retaining the same smooth rotation.
        val withoutViewport = NavigationPointerSmoother()
        withoutViewport.update(pointer(), 1_000_000_000)
        val compatibleFrame = withoutViewport.update(target, 1_016_000_000)
        assertEquals(target.screenX, compatibleFrame.screenX, 1e-9)
        assertEquals(target.screenY, compatibleFrame.screenY, 1e-9)
        assertEquals(firstFrame.rotationDegrees, compatibleFrame.rotationDegrees, 1e-5f)
    }

    @Test
    fun smoothingBetweenRightAndTopEdgesAlwaysRemainsOnAnEdge() {
        val safe = NavigationViewport(1_000.0, 2_000.0, 50.0, 200.0, 950.0, 1_800.0, 50.0, 10.0)
        val smoother = NavigationPointerSmoother()
        val right = project(NavigationVector(100.0, 10.0), view = safe)
        val top = project(NavigationVector(0.0, 10.0, 100.0), view = safe)
        smoother.update(right, 1_000_000_000, safe)
        var lastFrame = right
        var touchedRight = false
        var touchedTop = false
        for (frame in 1..40) {
            val filtered = smoother.update(top, 1_000_000_000L + frame * 16_000_000L, safe)
            assertTrue(filtered.screenX in 0.11..0.89)
            assertTrue(filtered.screenY in 0.13..0.87)
            val onRight = kotlin.math.abs(filtered.screenX - 0.89) < 1e-9
            val onTop = kotlin.math.abs(filtered.screenY - 0.13) < 1e-9
            assertTrue("off-screen frame $frame must remain on a safe edge", onRight || onTop)
            touchedRight = touchedRight || onRight
            touchedTop = touchedTop || onTop
            assertTrue(filtered.screenX <= lastFrame.screenX + 1e-9)
            assertTrue(filtered.screenY <= lastFrame.screenY + 1e-9)
            lastFrame = filtered
        }
        assertTrue(touchedRight && touchedTop)
    }

    @Test
    fun oppositeHorizontalEdgesConvergeInBothDirectionsWithoutClampedFeedback() {
        val safe = NavigationViewport(1_000.0, 2_000.0, 50.0, 200.0, 950.0, 1_800.0, 50.0, 10.0)
        val right = project(NavigationVector(100.0, 10.0), view = safe)
        val left = project(NavigationVector(-100.0, 10.0), view = safe)
        for ((start, target) in listOf(right to left, left to right)) {
            val result = followEdgeForOneSecond(start, target, safe)
            assertEquals(target.screenX, result.screenX, 0.001)
            assertEquals(target.screenY, result.screenY, 0.001)
            assertEquals(target.rotationDegrees, result.rotationDegrees, 0.1f)
        }
    }

    @Test
    fun oppositeVerticalEdgesConvergeInBothDirectionsAlongTheBoundary() {
        val safe = NavigationViewport(1_000.0, 2_000.0, 50.0, 200.0, 950.0, 1_800.0, 50.0, 10.0)
        val top = project(NavigationVector(0.0, 10.0, 100.0), view = safe)
        val bottom = project(NavigationVector(0.0, 10.0, -100.0), view = safe)
        for ((start, target) in listOf(top to bottom, bottom to top)) {
            val result = followEdgeForOneSecond(start, target, safe)
            assertEquals(target.screenX, result.screenX, 0.001)
            assertEquals(target.screenY, result.screenY, 0.001)
        }
    }

    @Test
    fun behindCameraEdgeAnglesCrossTheOneEightyBoundaryOnTheShortArc() {
        val safe = NavigationViewport(1_000.0, 2_000.0, 50.0, 200.0, 950.0, 1_800.0, 50.0, 10.0)
        val behindRight = NavigationPointer(0.51, 0.87, 179f, 30f, true)
        val behindLeft = NavigationPointer(0.49, 0.87, -179f, 30f, true)
        for ((start, target) in listOf(behindRight to behindLeft, behindLeft to behindRight)) {
            val smoother = NavigationPointerSmoother()
            smoother.update(start, 1_000_000_000, safe)
            var result = start
            for (frame in 1..60) {
                result = smoother.update(target, 1_000_000_000L + frame * 16_000_000L, safe)
                assertEquals(0.87, result.screenY, 1e-9)
                assertTrue(result.screenX in 0.49..0.51)
                assertTrue(kotlin.math.abs(result.rotationDegrees) > 175f)
            }
            assertEquals(target.screenX, result.screenX, 0.001)
        }
    }

    @Test
    fun smoothingDependsOnElapsedTimeRatherThanNumberOfCameraFrames() {
        fun afterFrames(count: Int): NavigationPointer {
            val smoother = NavigationPointerSmoother()
            smoother.update(pointer(x = 0.1, angle = -90f), 1_000_000_000)
            var result = pointer()
            for (frame in 1..count) {
                result = smoother.update(pointer(x = 0.9, angle = 45f),
                    1_000_000_000L + frame * 300_000_000L / count)
            }
            return result
        }
        val tenFrames = afterFrames(10)
        val thirtyFrames = afterFrames(30)
        assertEquals(tenFrames.screenX, thirtyFrames.screenX, 1e-9)
        assertEquals(tenFrames.rotationDegrees, thirtyFrames.rotationDegrees, 0.001f)
    }

    @Test
    fun pausedOrResetSmoothingDoesNotDragTheArrowFromAnOldLocation() {
        val smoother = NavigationPointerSmoother()
        smoother.update(pointer(x = 0.1, angle = -90f), 1_000_000_000)
        val newPointer = pointer(x = 0.9, angle = 90f)
        assertEquals(newPointer, smoother.update(newPointer, 3_000_000_000))
        smoother.reset()
        val resetPointer = pointer(x = 0.2, angle = -45f)
        assertEquals(resetPointer, smoother.update(resetPointer, 3_010_000_000))
    }

    private fun pointer(x: Double = 0.5, angle: Float = 0f, offScreen: Boolean = false) =
        NavigationPointer(x, 0.5, angle, 30f, offScreen)

    private fun followEdgeForOneSecond(start: NavigationPointer, target: NavigationPointer,
        viewport: NavigationViewport): NavigationPointer {
        val smoother = NavigationPointerSmoother()
        var previous = smoother.update(start, 1_000_000_000, viewport)
        var visitedOtherEdge = false
        for (frame in 1..60) {
            val result = smoother.update(target, 1_000_000_000L + frame * 16_000_000L, viewport)
            assertTrue(result.screenX in 0.11..0.89)
            assertTrue(result.screenY in 0.13..0.87)
            val onHorizontal = kotlin.math.abs(result.screenY - 0.13) < 1e-9 ||
                kotlin.math.abs(result.screenY - 0.87) < 1e-9
            val onVertical = kotlin.math.abs(result.screenX - 0.11) < 1e-9 ||
                kotlin.math.abs(result.screenX - 0.89) < 1e-9
            assertTrue("off-screen frame $frame must remain on the boundary", onHorizontal || onVertical)
            fun edgeRayDegrees(pointer: NavigationPointer): Double = Math.toDegrees(kotlin.math.atan2(
                pointer.screenX * viewport.widthPx - (viewport.safeLeftPx + viewport.safeRightPx) / 2,
                (viewport.safeTopPx + viewport.safeBottomPx) / 2 - pointer.screenY * viewport.heightPx
            ))
            fun wrappedDegrees(angle: Double): Double = ((angle + 180) % 360 + 360) % 360 - 180
            // Pixel displacement depends on the rectangle's aspect ratio. Bound
            // angular movement instead: a reversal must travel around the edge,
            // with no single-frame switch to the opposite side.
            assertTrue("boundary interpolation must move continuously",
                kotlin.math.abs(wrappedDegrees(edgeRayDegrees(result) - edgeRayDegrees(previous))) < 30.0)
            assertEquals("edge position and model must take the same angular path", 0.0,
                wrappedDegrees(edgeRayDegrees(result) - result.rotationDegrees), 0.001)
            visitedOtherEdge = visitedOtherEdge ||
                kotlin.math.hypot(result.screenX - start.screenX, result.screenY - start.screenY) > 0.1
            previous = result
        }
        assertTrue("off-screen pointer must leave its original edge position", visitedOtherEdge)
        return previous
    }

    private fun project(target: NavigationVector, camera: DoubleArray = north,
        view: NavigationViewport = viewport): NavigationPointer =
        requireNotNull(NavigationPointerGeometry.project(target, camera, projection, view))

    private fun rotateY(degrees: Double): DoubleArray {
        val angle = Math.toRadians(degrees)
        return doubleArrayOf(cos(angle), 0.0, sin(angle), 0.0, 1.0, 0.0, -sin(angle), 0.0, cos(angle))
    }

    private fun multiply(left: DoubleArray, right: DoubleArray): DoubleArray = DoubleArray(9) { index ->
        (0..2).sumOf { left[index / 3 * 3 + it] * right[it * 3 + index % 3] }
    }

    private fun assertMatrixEquals(expected: DoubleArray, actual: DoubleArray) {
        assertNotNull(actual)
        for (index in expected.indices) assertEquals("matrix[$index]", expected[index], actual[index], 1e-8)
    }
}
