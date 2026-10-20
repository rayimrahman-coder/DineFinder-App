package com.example.dinefinder.camera

import kotlin.math.abs
import kotlin.math.atan2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrowProjectionTest {
    private val mesh = ArrowMesh(
        positions = floatArrayOf(2f, 0f, .1f, -2f, -1f, .1f, -2f, 1f, .1f,
            2f, 0f, -.1f, -2f, -1f, -.1f, -2f, 1f, -.1f),
        normals = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f,
            0f, 0f, -1f, 0f, 0f, -1f, 0f, 0f, -1f),
        indices = intArrayOf(0, 1, 2, 3, 5, 4),
        baseColor = floatArrayOf(.069f, .33f, .93f, 1f)
    )

    @Test
    fun modelTipMatchesClockwiseRouteManeuverInsteadOfModelExportOrientation() {
        for (angle in listOf(-180f, -135f, -90f, -45f, 0f, 45f, 90f, 135f, 180f)) {
            val projected = projectArrowMesh(mesh, angle)
            // Average the front/back tip to remove the bevel thickness offset.
            val x = (projected.positions[0] + projected.positions[9]) / 2f
            val y = (projected.positions[1] + projected.positions[10]) / 2f
            val actual = Math.toDegrees(atan2(x.toDouble(), -y.toDouble())).toFloat()
            val diff = abs(((actual - angle + 540f) % 360f) - 180f)
            val delta = minOf(diff, 360f - diff)
            assertTrue("Maneuver $angle projected as $actual", delta < 5f)
        }
    }

    @Test
    fun projectionFitsWithoutClippingAndOpaqueFacesPaintFromBackToFront() {
        for (angle in -180..180 step 15) {
            val result = projectArrowMesh(mesh, angle.toFloat())
            assertEquals(mesh.positions.size, result.positions.size)
            assertTrue(result.positions.all { it.isFinite() && abs(it) < .96f })
            assertTrue(result.triangles.zipWithNext().all { (a, b) -> a.depth <= b.depth })
            assertTrue(result.triangles.all { it.color ushr 24 == 255 })
        }
    }

    @Test
    fun dynamicTiltChangesDepthAndHeightWhileCardinalTipsStayReadable() {
        val flat = projectArrowMesh(mesh, 0f, 15f)
        val tilted = projectArrowMesh(mesh, 0f, 65f)
        assertTrue(abs(flat.positions[1]) > abs(tilted.positions[1]) * 1.5f)
        assertTrue(abs(tilted.positions[2]) > abs(flat.positions[2]) * 2f)
        for (pitch in listOf(-65f, 0f, 15f, 65f)) {
            for (angle in listOf(-90f, 0f, 90f, 180f)) {
                val positions = projectArrowMesh(mesh, angle, pitch).positions
                val x = (positions[0] + positions[9]) / 2f
                val y = (positions[1] + positions[10]) / 2f
                val actual = Math.toDegrees(atan2(x.toDouble(), -y.toDouble())).toFloat()
                val delta = abs(((actual - angle + 540f) % 360f) - 180f)
                assertTrue("Tip $angle at pitch $pitch projected as $actual", delta < 5f)
            }
        }
    }

    @Test
    fun continuousProjectionReusesItsVertexAndFaceBuffers() {
        val projector = ArrowMeshProjector(mesh)
        val first = projector.project(0f, 30f)
        val positions = first.positions
        val faces = first.triangles.toList()
        val forwardTipX = positions[0]
        val next = projector.project(90f, 55f)
        assertSame(first, next)
        assertSame(positions, next.positions)
        assertTrue(faces.all { face -> next.triangles.any { it === face } })
        assertTrue(next.positions[0] > forwardTipX + .5f)
        assertTrue(next.triangles.zipWithNext().all { (a, b) -> a.depth <= b.depth })
    }

    @Test
    fun pitchIsClampedBeforeItCanMakeTheModelEdgeOn() {
        assertTrue(projectArrowMesh(mesh, 0f, 65f).positions.contentEquals(projectArrowMesh(mesh, 0f, 1000f).positions))
        assertTrue(projectArrowMesh(mesh, 0f, -65f).positions.contentEquals(projectArrowMesh(mesh, 0f, -1000f).positions))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidDirectionCannotProduceAnUncontrolledProjection() {
        projectArrowMesh(mesh, Float.NaN)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidPitchCannotProduceAnUncontrolledProjection() {
        projectArrowMesh(mesh, 0f, Float.NaN)
    }
}
